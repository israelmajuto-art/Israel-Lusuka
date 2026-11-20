package com.example.sms_reader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.BusinessSettings
import com.example.domain.MoneyUtils
import com.example.domain.SmsIngestionOutcome
import com.example.domain.SmsProcessingStatus
import com.example.domain.TransactionRecord
import com.example.domain.VerificationStatus

/**
 * Posts local notifications when mobile money transaction SMS messages are detected automatically.
 * Strictly respects PIN/privacy mode and POST_NOTIFICATIONS runtime permissions.
 */
object TransactionNotificationHelper {

    const val CHANNEL_ID = "mauzo_tx_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.sms_receiver_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Arifa za ndani za SMS za M-Pesa, Airtel Money, na Mixx by Yas"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    fun notifyTransactionDetected(
        context: Context,
        outcome: SmsIngestionOutcome,
        transaction: TransactionRecord?,
        settings: BusinessSettings
    ) {
        if (!settings.smsNotificationsEnabled) return
        val shouldNotify = outcome.processingStatus == SmsProcessingStatus.PARSED ||
            outcome.processingStatus == SmsProcessingStatus.NEEDS_REVIEW ||
            outcome.processingStatus == SmsProcessingStatus.FAILED_PARSE
        if (!shouldNotify) return
        if (!canPostNotifications(context)) return

        ensureChannel(context)

        val isEnglish = settings.primaryLanguage == AppLanguage.ENGLISH
        val isPrivate = settings.pinEnabled || settings.hideBalancesOnHome

        val title = if (isPrivate) {
            if (isEnglish) "New Mobile Money Alert" else "Muamala Mpya Umegunduliwa"
        } else {
            val netLabel = transaction?.network?.shortLabel ?: "Mobile Money"
            val statusLabel = when (outcome.verificationStatus) {
                VerificationStatus.FORMAT_VALIDATED ->
                    if (isEnglish) "SMS Format Validated" else "Umbizo Limethibitishwa (SMS)"
                VerificationStatus.SUSPICIOUS ->
                    if (isEnglish) "Suspicious — Review" else "Inatia Shaka — Kagua"
                else ->
                    if (isEnglish) "Needs Review" else "Inahitaji Uhakiki"
            }
            "$netLabel • $statusLabel"
        }

        val body = when {
            isPrivate -> {
                if (isEnglish) {
                    "Tap to unlock Mauzo App and review transaction details (amounts hidden for privacy)."
                } else {
                    "Gusa kufungua Mauzo App kuona maelezo (kiasi kimefichwa kwa faragha)."
                }
            }
            transaction != null && outcome.processingStatus != SmsProcessingStatus.FAILED_PARSE -> {
                val typeLabel = if (isEnglish) transaction.type.labelEn else transaction.type.labelSw
                val amtStr = MoneyUtils.formatCurrency(transaction.amount, settings.currencyCode)
                "$typeLabel: $amtStr • ${transaction.senderOrRecipientName}"
            }
            else -> outcome.message
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            outcome.smsArchiveId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = if (outcome.smsArchiveId > 0L) {
            outcome.smsArchiveId.toInt()
        } else {
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
