package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MauzoApplication
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MauzoApplication ?: return Result.failure()
        val settings = app.database.businessSettingsDao().getOnce()
        if (settings != null && !settings.autoBackupEnabled) {
            return Result.success()
        }
        val outcome = app.backupRestoreManager.writeLocalAutoBackup()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "mauzo_periodic_auto_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
