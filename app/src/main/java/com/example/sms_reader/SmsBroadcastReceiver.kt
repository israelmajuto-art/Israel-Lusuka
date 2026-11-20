package com.example.sms_reader

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sms_parser.PreFilterDecision
import com.example.sms_parser.SmsPreFilter

/**
 * Receives incoming SMS broadcasts, checks RECEIVE_SMS permission, immediately discards OTPs/personal chats,
 * and dispatches mobile money notifications to WorkManager for reliable local processing.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val hasReceivePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReceivePermission) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val simSlot = extractSimSlot(intent)
        val customSenders = SenderDirectoryCache.load(context)

        // Group multipart SMS chunks by originating address while preserving per-sender timestamps
        val grouped = linkedMapOf<String, Pair<StringBuilder, Long>>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: ""
            val body = sms.displayMessageBody ?: sms.messageBody ?: ""
            val ts = if (sms.timestampMillis > 0L) sms.timestampMillis else System.currentTimeMillis()
            val entry = grouped[sender]
            if (entry == null) {
                grouped[sender] = StringBuilder(body) to ts
            } else {
                entry.first.append(body)
                if (ts > entry.second) {
                    grouped[sender] = entry.first to ts
                }
            }
        }

        val workManager = WorkManager.getInstance(context.applicationContext)
        grouped.forEach { (sender, pair) ->
            val body = pair.first.toString()
            val receivedAt = pair.second
            val decision = SmsPreFilter.evaluate(sender, body, customSenders)
            if (decision == PreFilterDecision.PROCESS_MOBILE_MONEY) {
                val input = Data.Builder()
                    .putString(ProcessSmsWorker.KEY_SENDER, sender)
                    .putString(ProcessSmsWorker.KEY_BODY, body)
                    .putLong(ProcessSmsWorker.KEY_RECEIVED_AT, receivedAt)
                    .putInt(ProcessSmsWorker.KEY_SIM_SLOT, simSlot)
                    .build()
                val uniqueKey = "sms_${sender}_${receivedAt}_${body.hashCode()}"
                val request = OneTimeWorkRequestBuilder<ProcessSmsWorker>()
                    .setInputData(input)
                    .build()
                workManager.enqueueUniqueWork(uniqueKey, ExistingWorkPolicy.KEEP, request)
            }
        }
    }

    private fun extractSimSlot(intent: Intent): Int {
        val candidateKeys = listOf(
            "android.telephony.extra.SLOT_INDEX",
            "slot",
            "simSlot",
            "simId"
        )
        for (key in candidateKeys) {
            if (intent.hasExtra(key)) {
                val slot = intent.getIntExtra(key, -1)
                if (slot >= 0) return slot
            }
        }
        return -1
    }
}
