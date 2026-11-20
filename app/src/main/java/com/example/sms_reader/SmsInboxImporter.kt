package com.example.sms_reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.domain.AppLanguage
import com.example.domain.MauzoRepository
import com.example.domain.MobileMoneyNetwork
import com.example.domain.SmsProcessingStatus
import com.example.sms_parser.PreFilterDecision
import com.example.sms_parser.SmsPreFilter
import kotlinx.coroutines.flow.first

data class InboxImportSummary(
    val scannedCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val needsReviewCount: Int
)

data class SmsPermissionStatus(
    val receiveSmsGranted: Boolean,
    val readSmsGranted: Boolean,
    val postNotificationsGranted: Boolean = true,
    val hasAttemptedRequest: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val isPermanentlyDenied: Boolean = false
) {
    val allGranted: Boolean get() = receiveSmsGranted && readSmsGranted
}

class SmsInboxImporter(
    private val context: Context,
    private val repository: MauzoRepository
) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun markPermissionRequested() {
        prefs.edit().putBoolean(KEY_SMS_PERMISSION_REQUESTED, true).apply()
    }

    fun getPermissionStatus(activity: Activity? = null): SmsPermissionStatus {
        val receive = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val notifyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val allSmsGranted = receive && read
        val attempted = prefs.getBoolean(KEY_SMS_PERMISSION_REQUESTED, false)
        val shouldShowRationale = if (activity != null && !allSmsGranted) {
            (!receive && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)) ||
                (!read && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS))
        } else {
            false
        }
        val permanentlyDenied = activity != null && !allSmsGranted && attempted && !shouldShowRationale

        return SmsPermissionStatus(
            receiveSmsGranted = receive,
            readSmsGranted = read,
            postNotificationsGranted = notifyGranted,
            hasAttemptedRequest = attempted,
            shouldShowRationale = shouldShowRationale,
            isPermanentlyDenied = permanentlyDenied
        )
    }

    /**
     * Scans the device SMS inbox only when user explicitly grants READ_SMS and triggers sync.
     * Filters strictly so personal chats and OTPs are never stored.
     */
    suspend fun scanInboxForMobileMoneySms(
        maxMessages: Int = 250,
        language: AppLanguage = AppLanguage.SWAHILI
    ): Result<InboxImportSummary> {
        if (!getPermissionStatus().readSmsGranted) {
            val msg = if (language == AppLanguage.ENGLISH) {
                "READ_SMS permission is required before scanning the SMS inbox."
            } else {
                "Ruhusa ya READ_SMS inahitajika kabla ya kuchanganua kikasha cha SMS."
            }
            return Result.failure(SecurityException(msg))
        }

        return runCatching {
            val formats = repository.observeNetworkFormats().first()
            val customSenders: Map<MobileMoneyNetwork, Set<String>> = formats.associate { fmt ->
                val ids = fmt.senderIdentifiersCsv
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                fmt.network to ids
            }

            var scanned = 0
            var imported = 0
            var duplicates = 0
            var needsReview = 0

            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext() && scanned < maxMessages) {
                    scanned++
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                    if (SmsPreFilter.evaluate(sender, body, customSenders) == PreFilterDecision.PROCESS_MOBILE_MONEY) {
                        val outcome = repository.ingestIncomingSms(
                            sender = sender,
                            body = body,
                            receivedAtEpochMs = dateMs,
                            importSource = "INBOX_SYNC"
                        )
                        when (outcome.processingStatus) {
                            SmsProcessingStatus.PARSED -> imported++
                            SmsProcessingStatus.DUPLICATE -> duplicates++
                            SmsProcessingStatus.NEEDS_REVIEW, SmsProcessingStatus.FAILED_PARSE -> needsReview++
                            else -> Unit
                        }
                    }
                }
            }

            InboxImportSummary(
                scannedCount = scanned,
                importedCount = imported,
                duplicateCount = duplicates,
                needsReviewCount = needsReview
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "mauzo_permission_prefs"
        private const val KEY_SMS_PERMISSION_REQUESTED = "sms_permission_requested"
    }
}
