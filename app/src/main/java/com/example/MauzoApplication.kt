package com.example

import android.app.Application
import com.example.data.AutoBackupWorker
import com.example.data.BackupRestoreManager
import com.example.data.MauzoDatabase
import com.example.data.MauzoRepositoryImpl
import com.example.data.cloud.FirestoreSyncRepository
import com.example.reporting.FinancialReportGenerator
import com.example.reporting.ReportExporter
import com.example.security.KeystoreManager
import com.example.security.PinManager
import com.example.sms_reader.SenderDirectoryCache
import com.example.sms_reader.SmsInboxImporter
import com.example.sms_reader.TransactionNotificationHelper
import com.example.transaction_engine.FinancialTransactionEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MauzoApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val keystoreManager: KeystoreManager by lazy { KeystoreManager(this) }
    val pinManager: PinManager by lazy { PinManager(this) }
    val transactionEngine: FinancialTransactionEngine by lazy { FinancialTransactionEngine() }
    val reportGenerator: FinancialReportGenerator by lazy { FinancialReportGenerator(transactionEngine) }
    val reportExporter: ReportExporter by lazy { ReportExporter(this) }

    val database: MauzoDatabase by lazy {
        val passphrase = runBlocking(Dispatchers.IO) {
            keystoreManager.getOrCreateDatabasePassphrase()
        }
        MauzoDatabase.createEncrypted(this, passphrase)
    }

    val repository: MauzoRepositoryImpl by lazy {
        MauzoRepositoryImpl(
            db = database,
            txEngine = transactionEngine
        )
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    val firestoreSyncRepository: FirestoreSyncRepository by lazy {
        FirestoreSyncRepository(db = firestore)
    }

    val backupRestoreManager: BackupRestoreManager by lazy {
        BackupRestoreManager(
            context = this,
            repository = repository,
            keystoreManager = keystoreManager
        )
    }

    val smsInboxImporter: SmsInboxImporter by lazy {
        SmsInboxImporter(
            context = this,
            repository = repository
        )
    }

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized()
        TransactionNotificationHelper.ensureChannel(this)
        appScope.launch {
            runCatching {
                repository.ensureSeeded()
                val formats = repository.observeNetworkFormats().first()
                SenderDirectoryCache.save(this@MauzoApplication, formats)
                val settings = database.businessSettingsDao().getOnce()
                if (settings?.autoBackupEnabled != false) {
                    AutoBackupWorker.schedule(this@MauzoApplication)
                }
            }
        }
    }

    private fun ensureFirebaseInitialized() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        if (FirebaseApp.initializeApp(this) != null) return
        val appId = runCatching { getString(R.string.google_app_id) }.getOrNull().orEmpty()
        val apiKey = runCatching { getString(R.string.google_api_key) }.getOrNull().orEmpty()
        if (appId.isNotBlank() && apiKey.isNotBlank()) {
            val builder = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
            runCatching { getString(R.string.project_id) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { builder.setProjectId(it) }
            runCatching { getString(R.string.google_storage_bucket) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { builder.setStorageBucket(it) }
            runCatching { getString(R.string.gcm_defaultSenderId) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { builder.setGcmSenderId(it) }
            FirebaseApp.initializeApp(this, builder.build())
        }
    }
}
