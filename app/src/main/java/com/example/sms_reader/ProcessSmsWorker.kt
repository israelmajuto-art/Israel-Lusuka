package com.example.sms_reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MauzoApplication
import com.example.domain.SmsProcessingStatus
import kotlinx.coroutines.flow.first

class ProcessSmsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis())
        val rawSimSlot = inputData.getInt(KEY_SIM_SLOT, -1)
        val simSlot = if (rawSimSlot >= 0) rawSimSlot else null

        val app = applicationContext as? MauzoApplication ?: return Result.failure()
        return runCatching {
            val outcome = app.repository.ingestIncomingSms(
                sender = sender,
                body = body,
                receivedAtEpochMs = receivedAt,
                importSource = "SMS_RECEIVER",
                simSlot = simSlot
            )
            if (outcome.processingStatus == SmsProcessingStatus.PARSED ||
                outcome.processingStatus == SmsProcessingStatus.NEEDS_REVIEW ||
                outcome.processingStatus == SmsProcessingStatus.FAILED_PARSE
            ) {
                val settings = app.repository.observeBusinessSettings().first()
                val tx = outcome.transactionLocalId?.let { app.repository.getTransactionById(it) }
                TransactionNotificationHelper.notifyTransactionDetected(
                    context = applicationContext,
                    outcome = outcome,
                    transaction = tx,
                    settings = settings
                )
            }
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_SENDER = "key_sender"
        const val KEY_BODY = "key_body"
        const val KEY_RECEIVED_AT = "key_received_at"
        const val KEY_SIM_SLOT = "key_sim_slot"
    }
}
