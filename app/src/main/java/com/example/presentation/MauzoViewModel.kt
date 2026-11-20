package com.example.presentation

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AutoBackupWorker
import com.example.data.BackupRestoreManager
import com.example.data.cloud.CloudTransactionRecord
import com.example.data.cloud.FirestoreSyncRepository
import com.example.domain.AppLanguage
import com.example.domain.AuditLogRecord
import com.example.domain.BusinessSettings
import com.example.domain.DashboardMetrics
import com.example.domain.FinancialReport
import com.example.domain.MauzoRepository
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkFormatConfig
import com.example.domain.OpeningBalanceRecord
import com.example.domain.ReportPeriod
import com.example.domain.SmsArchiveRecord
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.domain.WalletSummary
import com.example.reporting.FinancialReportGenerator
import com.example.reporting.ReportExporter
import com.example.security.PinManager
import com.example.sms_reader.SenderDirectoryCache
import com.example.sms_reader.SmsInboxImporter
import com.example.sms_reader.SmsPermissionStatus
import com.example.transaction_engine.FinancialTransactionEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DateFilterOption(val labelSw: String, val labelEn: String) {
    ALL("Muda Wote", "All Time"),
    TODAY("Leo", "Today"),
    LAST_7_DAYS("Siku 7", "Last 7 Days"),
    THIS_MONTH("Mwezi Huu", "This Month")
}

data class TransactionFilters(
    val query: String = "",
    val network: MobileMoneyNetwork? = null,
    val type: TransactionType? = null,
    val status: VerificationStatus? = null,
    val dateFilter: DateFilterOption = DateFilterOption.ALL
)

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

data class MauzoUiState(
    val settings: BusinessSettings = BusinessSettings(),
    val dashboard: DashboardMetrics = DashboardMetrics(
        totalCalculatedBalance = MoneyUtils.ZERO,
        todayReceived = MoneyUtils.ZERO,
        todaySent = MoneyUtils.ZERO,
        todayTransactionCount = 0,
        monthlyIncome = MoneyUtils.ZERO,
        monthlyExpenses = MoneyUtils.ZERO,
        walletSummaries = emptyList(),
        recentTransactions = emptyList(),
        needsReviewCount = 0,
        suspiciousCount = 0
    ),
    val allTransactions: List<TransactionRecord> = emptyList(),
    val filteredTransactions: List<TransactionRecord> = emptyList(),
    val filters: TransactionFilters = TransactionFilters(),
    val smsArchive: List<SmsArchiveRecord> = emptyList(),
    val filteredSmsArchive: List<SmsArchiveRecord> = emptyList(),
    val smsSearchQuery: String = "",
    val wallets: List<WalletSummary> = emptyList(),
    val openingBalances: List<OpeningBalanceRecord> = emptyList(),
    val networkFormats: List<NetworkFormatConfig> = emptyList(),
    val auditLogs: List<AuditLogRecord> = emptyList(),
    val selectedReportPeriod: ReportPeriod = ReportPeriod.MONTHLY,
    val currentReport: FinancialReport? = null,
    val isLocked: Boolean = false,
    val pinConfigured: Boolean = false,
    val pinError: String? = null,
    val smsPermissions: SmsPermissionStatus = SmsPermissionStatus(false, false),
    val currentUserId: String = "",
    val currentUserEmail: String? = null,
    val cloudTransactionsState: CloudUiState<List<CloudTransactionRecord>> = CloudUiState.Loading,
    val snackbarMessage: String? = null,
    val isBusy: Boolean = false
)

class MauzoViewModel(
    private val repository: MauzoRepository,
    private val txEngine: FinancialTransactionEngine,
    private val reportGenerator: FinancialReportGenerator,
    private val reportExporter: ReportExporter,
    private val backupRestoreManager: BackupRestoreManager,
    private val smsInboxImporter: SmsInboxImporter,
    private val pinManager: PinManager,
    private val appContext: android.content.Context,
    private val firestoreSyncRepository: FirestoreSyncRepository? = null,
    private val currentUserId: String = "",
    private val currentUserEmail: String? = null
) : ViewModel() {

    private val filtersFlow = MutableStateFlow(TransactionFilters())
    private val smsQueryFlow = MutableStateFlow("")
    private val reportPeriodFlow = MutableStateFlow(ReportPeriod.MONTHLY)
    private val isLockedFlow = MutableStateFlow(false)
    private val pinConfiguredFlow = MutableStateFlow(false)
    private val pinErrorFlow = MutableStateFlow<String?>(null)
    private val smsPermissionsFlow = MutableStateFlow(smsInboxImporter.getPermissionStatus())
    private val snackbarFlow = MutableStateFlow<String?>(null)
    private val isBusyFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val pinReady = pinManager.isPinConfigured()
            pinConfiguredFlow.value = pinReady
            if (pinReady) {
                isLockedFlow.value = true
            }
        }
        viewModelScope.launch {
            repository.observeNetworkFormats().collect { formats ->
                if (formats.isNotEmpty()) {
                    SenderDirectoryCache.save(appContext, formats)
                }
            }
        }
    }

    private data class CoreDataBundle(
        val settings: BusinessSettings,
        val transactions: List<TransactionRecord>,
        val sms: List<SmsArchiveRecord>,
        val wallets: List<WalletSummary>,
        val openings: List<OpeningBalanceRecord>,
        val audits: List<AuditLogRecord>,
        val networkFormats: List<NetworkFormatConfig>
    )

    private val coreDataFlow = combine(
        repository.observeBusinessSettings(),
        repository.observeTransactions(),
        repository.observeSmsArchive(),
        repository.observeWallets(),
        repository.observeOpeningBalances(),
        repository.observeAuditLogs(),
        repository.observeNetworkFormats()
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        CoreDataBundle(
            settings = arr[0] as BusinessSettings,
            transactions = arr[1] as List<TransactionRecord>,
            sms = arr[2] as List<SmsArchiveRecord>,
            wallets = arr[3] as List<WalletSummary>,
            openings = arr[4] as List<OpeningBalanceRecord>,
            audits = arr[5] as List<AuditLogRecord>,
            networkFormats = arr[6] as List<NetworkFormatConfig>
        )
    }

    private val uiControlsFlow = combine(
        filtersFlow,
        smsQueryFlow,
        reportPeriodFlow,
        isLockedFlow,
        pinConfiguredFlow,
        pinErrorFlow,
        smsPermissionsFlow,
        snackbarFlow,
        isBusyFlow
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        ControlsBundle(
            filters = arr[0] as TransactionFilters,
            smsQuery = arr[1] as String,
            reportPeriod = arr[2] as ReportPeriod,
            isLocked = arr[3] as Boolean,
            pinConfigured = arr[4] as Boolean,
            pinError = arr[5] as String?,
            smsPermissions = arr[6] as SmsPermissionStatus,
            snackbar = arr[7] as String?,
            isBusy = arr[8] as Boolean
        )
    }

    private data class ControlsBundle(
        val filters: TransactionFilters,
        val smsQuery: String,
        val reportPeriod: ReportPeriod,
        val isLocked: Boolean,
        val pinConfigured: Boolean,
        val pinError: String?,
        val smsPermissions: SmsPermissionStatus,
        val snackbar: String?,
        val isBusy: Boolean
    )

    private val cloudTransactionsState: StateFlow<CloudUiState<List<CloudTransactionRecord>>> =
        if (firestoreSyncRepository != null && currentUserId.isNotBlank()) {
            firestoreSyncRepository.observeCloudTransactions(currentUserId)
                .map<List<CloudTransactionRecord>, CloudUiState<List<CloudTransactionRecord>>> {
                    CloudUiState.Success(it)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w("MauzoVM", "Error observing cloud transactions", rootError)
                    emit(CloudUiState.Error(rootError.message ?: "Failed to sync cloud records"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = CloudUiState.Loading
                )
        } else {
            flowOf(CloudUiState.Success(emptyList<CloudTransactionRecord>()))
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = CloudUiState.Loading
                )
        }

    val uiState: StateFlow<MauzoUiState> = combine(coreDataFlow, uiControlsFlow, cloudTransactionsState) { core, ctrl, cloudState ->
        val reconciliationMap = core.wallets.associate { it.network to it.lastReconciledAtEpochMs }
        val dashboard = txEngine.calculateDashboardMetrics(
            transactions = core.transactions,
            openingBalances = core.openings,
            reconciliationTimestamps = reconciliationMap
        )
        // Prefer persisted provider balances when newer/manually reconciled
        val mergedWallets = dashboard.walletSummaries.map { calcWallet ->
            val persisted = core.wallets.firstOrNull { it.network == calcWallet.network }
            if (calcWallet.providerReportedBalance == null && persisted?.providerReportedBalance != null) {
                calcWallet.copy(
                    providerReportedBalance = persisted.providerReportedBalance,
                    providerBalanceUpdatedAtEpochMs = persisted.providerBalanceUpdatedAtEpochMs,
                    lastReconciledAtEpochMs = persisted.lastReconciledAtEpochMs
                )
            } else {
                calcWallet.copy(lastReconciledAtEpochMs = persisted?.lastReconciledAtEpochMs)
            }
        }

        val filteredTxs = applyTransactionFilters(core.transactions, ctrl.filters)
        val filteredSms = applySmsSearch(core.sms, ctrl.smsQuery)
        val report = reportGenerator.generate(
            period = ctrl.reportPeriod,
            allTransactions = core.transactions,
            openingBalances = core.openings
        )

        MauzoUiState(
            settings = core.settings,
            dashboard = dashboard.copy(walletSummaries = mergedWallets),
            allTransactions = core.transactions,
            filteredTransactions = filteredTxs,
            filters = ctrl.filters,
            smsArchive = core.sms,
            filteredSmsArchive = filteredSms,
            smsSearchQuery = ctrl.smsQuery,
            wallets = mergedWallets,
            openingBalances = core.openings,
            networkFormats = core.networkFormats,
            auditLogs = core.audits,
            selectedReportPeriod = ctrl.reportPeriod,
            currentReport = report,
            isLocked = ctrl.isLocked && (core.settings.pinEnabled || core.settings.biometricEnabled),
            pinConfigured = ctrl.pinConfigured,
            pinError = ctrl.pinError,
            smsPermissions = ctrl.smsPermissions,
            currentUserId = currentUserId,
            currentUserEmail = currentUserEmail,
            cloudTransactionsState = cloudState,
            snackbarMessage = ctrl.snackbar,
            isBusy = ctrl.isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MauzoUiState(
            currentUserId = currentUserId,
            currentUserEmail = currentUserEmail
        )
    )

    fun refreshSmsPermissions(activity: Activity? = null) {
        smsPermissionsFlow.value = smsInboxImporter.getPermissionStatus(activity)
    }

    fun onSmsPermissionRequestResult(
        activity: Activity?,
        grantedByPermission: Map<String, Boolean>
    ): SmsPermissionStatus {
        smsInboxImporter.markPermissionRequested()
        val status = smsInboxImporter.getPermissionStatus(activity)
        smsPermissionsFlow.value = status
        val lang = uiState.value.settings.primaryLanguage
        if (status.allGranted) {
            snackbarFlow.value = if (lang == AppLanguage.ENGLISH) {
                "SMS permissions granted — automatic transaction monitoring is active."
            } else {
                "Ruhusa za SMS zimekubaliwa — ufuatiliaji wa miamala kiotomatiki umewashwa."
            }
        } else if (grantedByPermission.isNotEmpty()) {
            snackbarFlow.value = if (lang == AppLanguage.ENGLISH) {
                "SMS permission denied. Automatic SMS monitoring is paused."
            } else {
                "Ruhusa ya SMS imekataliwa. Ufuatiliaji wa SMS kiotomatiki umesimamishwa."
            }
        }
        return status
    }

    fun clearSnackbar() {
        snackbarFlow.value = null
    }

    fun updateFilters(transform: (TransactionFilters) -> TransactionFilters) {
        filtersFlow.update(transform)
    }

    fun updateSmsSearchQuery(query: String) {
        smsQueryFlow.value = query
    }

    fun selectReportPeriod(period: ReportPeriod) {
        reportPeriodFlow.value = period
    }

    fun toggleBalancePrivacy() {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(hideBalancesOnHome = !it.hideBalancesOnHome) }
        }
    }

    fun importManualSms(sender: String, body: String) {
        viewModelScope.launch {
            if (body.isBlank()) {
                snackbarFlow.value = "Tafadhali weka maandishi ya SMS."
                return@launch
            }
            isBusyFlow.value = true
            val effectiveSender = sender.trim().ifBlank { "MANUAL_IMPORT" }
            val outcome = repository.ingestIncomingSms(
                sender = effectiveSender,
                body = body.trim(),
                receivedAtEpochMs = System.currentTimeMillis(),
                importSource = "MANUAL_PASTE"
            )
            isBusyFlow.value = false
            snackbarFlow.value = outcome.message
            val txLocalId = outcome.transactionLocalId
            if (txLocalId != null) {
                val tx = repository.getTransactionById(txLocalId)
                if (tx != null && firestoreSyncRepository != null && currentUserId.isNotBlank()) {
                    firestoreSyncRepository.syncTransaction(tx)
                }
            }
        }
    }

    fun scanDeviceSmsInbox() {
        viewModelScope.launch {
            val lang = uiState.value.settings.primaryLanguage
            isBusyFlow.value = true
            val result = smsInboxImporter.scanInboxForMobileMoneySms(language = lang)
            isBusyFlow.value = false
            result.onSuccess { summary ->
                snackbarFlow.value = if (lang == AppLanguage.ENGLISH) {
                    "Scanned ${summary.scannedCount} SMS: ${summary.importedCount} new, ${summary.duplicateCount} duplicate, ${summary.needsReviewCount} needs review."
                } else {
                    "SMS ${summary.scannedCount} zimechanganuliwa: ${summary.importedCount} mpya, ${summary.duplicateCount} nakala, ${summary.needsReviewCount} uhakiki."
                }
                if (summary.importedCount > 0) {
                    syncToCloudSilently()
                }
            }.onFailure { err ->
                snackbarFlow.value = err.message ?: if (lang == AppLanguage.ENGLISH) {
                    "Unable to scan SMS inbox."
                } else {
                    "Imeshindwa kuchanganua SMS."
                }
            }
        }
    }

    fun addManualTransaction(
        txId: String,
        network: MobileMoneyNetwork,
        type: TransactionType,
        amountText: String,
        feeText: String,
        partyName: String,
        partyPhone: String,
        description: String,
        isCreditAdjustment: Boolean,
        reason: String
    ) {
        viewModelScope.launch {
            val amount = MoneyUtils.parseAmountOrNull(amountText)
            if (amount == null || amount <= BigDecimal.ZERO) {
                snackbarFlow.value = "Weka kiasi sahihi kikubwa kuliko 0."
                return@launch
            }
            val fee = MoneyUtils.parseAmountOrNull(feeText) ?: BigDecimal.ZERO
            val now = System.currentTimeMillis()
            val record = TransactionRecord(
                transactionId = txId.trim(),
                network = network,
                type = type,
                amount = amount,
                fee = fee,
                senderOrRecipientName = partyName.trim().ifBlank { "Haijulikani" },
                senderOrRecipientPhone = partyPhone.trim().takeIf { it.isNotEmpty() },
                transactionDateText = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(now)),
                transactionTimeText = SimpleDateFormat("HH:mm", Locale.US).format(Date(now)),
                timestampEpochMs = now,
                description = description.trim().ifBlank { type.labelSw },
                providerReportedBalance = null,
                verificationStatus = VerificationStatus.FORMAT_VALIDATED,
                verificationNotes = "Imeingizwa kwa mkono na mmiliki",
                isManualEntry = true,
                isCreditAdjustment = isCreditAdjustment
            )
            val res = repository.addManualTransaction(record, reason)
            snackbarFlow.value = if (res.isSuccess) {
                res.getOrNull()?.let { insertedId ->
                    repository.getTransactionById(insertedId)?.let { savedTx ->
                        if (firestoreSyncRepository != null && currentUserId.isNotBlank()) {
                            firestoreSyncRepository.syncTransaction(savedTx)
                        }
                    }
                }
                "Muamala umehifadhiwa kikamilifu."
            } else {
                res.exceptionOrNull()?.message ?: "Hitilafu wakati wa kuhifadhi muamala."
            }
        }
    }

    fun updateTransaction(updated: TransactionRecord, reason: String) {
        viewModelScope.launch {
            val res = repository.updateTransactionDetails(updated, reason)
            snackbarFlow.value = if (res.isSuccess) {
                if (firestoreSyncRepository != null && currentUserId.isNotBlank()) {
                    firestoreSyncRepository.syncTransaction(updated)
                }
                "Marekebisho yamehifadhiwa kwenye Audit Log."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kusasisha muamala."
            }
        }
    }

    fun recordReversalOrRefund(
        originalTransactionId: String,
        type: TransactionType,
        amountText: String,
        network: MobileMoneyNetwork,
        reason: String
    ) {
        viewModelScope.launch {
            val amount = MoneyUtils.parseAmountOrNull(amountText)
            if (amount == null || amount <= BigDecimal.ZERO) {
                snackbarFlow.value = "Weka kiasi sahihi."
                return@launch
            }
            val res = repository.linkReversalOrRefund(
                originalTransactionId = originalTransactionId,
                newType = type,
                amount = amount,
                network = network,
                reason = reason
            )
            snackbarFlow.value = if (res.isSuccess) {
                syncToCloudSilently()
                "${type.labelSw} imerekodiwa."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kurekodi uhusiano."
            }
        }
    }

    fun setOpeningBalance(network: MobileMoneyNetwork, amountText: String, note: String) {
        viewModelScope.launch {
            val amount = MoneyUtils.parseAmountOrNull(amountText)
            if (amount == null || amount < BigDecimal.ZERO) {
                snackbarFlow.value = "Weka kiasi sahihi (0 au zaidi)."
                return@launch
            }
            val res = repository.setOpeningBalance(network, amount, note)
            snackbarFlow.value = if (res.isSuccess) {
                if (firestoreSyncRepository != null && currentUserId.isNotBlank()) {
                    val ob = uiState.value.openingBalances.firstOrNull { it.network == network }
                        ?: OpeningBalanceRecord(
                            network = network,
                            amount = amount,
                            effectiveDateEpochMs = System.currentTimeMillis(),
                            note = note.ifBlank { "Salio la awali" },
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                    firestoreSyncRepository.syncOpeningBalance(ob.copy(amount = amount, note = note.ifBlank { ob.note }))
                }
                "Salio la awali la ${network.shortLabel} limehifadhiwa."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kuweka salio la awali."
            }
        }
    }

    fun reconcileWallet(
        network: MobileMoneyNetwork,
        providerBalanceText: String,
        adjustCalculated: Boolean,
        reason: String
    ) {
        viewModelScope.launch {
            val balance = MoneyUtils.parseAmountOrNull(providerBalanceText)
            if (balance == null || balance < BigDecimal.ZERO) {
                snackbarFlow.value = "Weka salio halisi la mtandao."
                return@launch
            }
            val res = repository.reconcileWallet(network, balance, adjustCalculated, reason)
            snackbarFlow.value = if (res.isSuccess) {
                syncToCloudSilently()
                "Usawazishaji wa ${network.shortLabel} umekamilika."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kusawazisha."
            }
        }
    }

    fun updateBusinessName(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isEmpty()) return@launch
            repository.updateBusinessSettings { it.copy(businessName = clean) }
            syncProfileSilently()
            snackbarFlow.value = "Jina la biashara limehifadhiwa."
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(primaryLanguage = language) }
            syncProfileSilently()
        }
    }

    fun updateCurrency(currencyCode: String) {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(currencyCode = currencyCode.uppercase().take(5)) }
            syncProfileSilently()
        }
    }

    fun syncToCloudNow() {
        val syncRepo = firestoreSyncRepository ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val lang = uiState.value.settings.primaryLanguage
            isBusyFlow.value = true
            val snapshot = uiState.value
            val res = syncRepo.syncAllFromLocal(
                userId = currentUserId,
                settings = snapshot.settings,
                email = currentUserEmail,
                transactions = snapshot.allTransactions,
                openingBalances = snapshot.openingBalances
            )
            isBusyFlow.value = false
            snackbarFlow.value = res.fold(
                onSuccess = { count ->
                    if (lang == AppLanguage.ENGLISH) {
                        "Synced $count transactions to Cloud Firestore."
                    } else {
                        "Miamala $count imesawazishwa na Cloud Firestore."
                    }
                },
                onFailure = { err ->
                    err.localizedMessage ?: if (lang == AppLanguage.ENGLISH) {
                        "Cloud sync failed."
                    } else {
                        "Usawazishaji wa wingu umeshindwa."
                    }
                }
            )
        }
    }

    fun restoreFromCloud() {
        val syncRepo = firestoreSyncRepository ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val lang = uiState.value.settings.primaryLanguage
            isBusyFlow.value = true
            val res = syncRepo.restoreFromCloud(currentUserId)
            res.onSuccess { payload ->
                payload.profile?.let { profile ->
                    repository.updateBusinessSettings { current ->
                        current.copy(
                            businessName = profile.businessName.ifBlank { current.businessName },
                            primaryLanguage = AppLanguage.fromCode(profile.primaryLanguage),
                            currencyCode = profile.currencyCode.ifBlank { current.currencyCode }
                        )
                    }
                }
                payload.openingBalances.forEach { ob ->
                    repository.setOpeningBalance(ob.network, ob.amount, ob.note)
                }
                var restoredCount = 0
                payload.transactions.forEach { cloudTx ->
                    val added = repository.addManualTransaction(cloudTx, "Rejesho kutoka Cloud Firestore")
                    if (added.isSuccess) restoredCount++
                }
                isBusyFlow.value = false
                snackbarFlow.value = if (lang == AppLanguage.ENGLISH) {
                    "Restored $restoredCount transactions from Cloud Firestore."
                } else {
                    "Miamala $restoredCount imepakuliwa kutoka Cloud Firestore."
                }
            }.onFailure { err ->
                isBusyFlow.value = false
                snackbarFlow.value = err.localizedMessage ?: if (lang == AppLanguage.ENGLISH) {
                    "Cloud restore failed."
                } else {
                    "Imeshindwa kupakua kutoka wingu."
                }
            }
        }
    }

    private suspend fun syncToCloudSilently() {
        val syncRepo = firestoreSyncRepository ?: return
        if (currentUserId.isBlank()) return
        val snapshot = uiState.value
        syncRepo.syncAllFromLocal(
            userId = currentUserId,
            settings = snapshot.settings,
            email = currentUserEmail,
            transactions = snapshot.allTransactions,
            openingBalances = snapshot.openingBalances
        )
    }

    private suspend fun syncProfileSilently() {
        val syncRepo = firestoreSyncRepository ?: return
        if (currentUserId.isBlank()) return
        syncRepo.upsertUserProfile(uiState.value.settings, currentUserEmail)
    }

    fun toggleNetworkEnabled(network: MobileMoneyNetwork, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleNetworkEnabled(network, enabled)
        }
    }

    fun toggleSmsNotifications(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(smsNotificationsEnabled = enabled) }
        }
    }

    fun setSimSlotFilter(slot: Int) {
        val clamped = slot.coerceIn(0, 2)
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(simSlotFilter = clamped) }
        }
    }

    fun updateNetworkSenders(network: MobileMoneyNetwork, csv: String) {
        viewModelScope.launch {
            val res = repository.updateNetworkSenders(network, csv)
            snackbarFlow.value = if (res.isSuccess) {
                "Vitambulisho vya SMS vya ${network.shortLabel} vimehifadhiwa."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kuhifadhi vitambulisho."
            }
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(autoBackupEnabled = enabled) }
            if (enabled) AutoBackupWorker.schedule(appContext) else AutoBackupWorker.cancel(appContext)
        }
    }

    fun configurePin(newPin: String) {
        viewModelScope.launch {
            val res = pinManager.setPin(newPin)
            if (res.isSuccess) {
                pinConfiguredFlow.value = true
                repository.updateBusinessSettings { it.copy(pinEnabled = true) }
                snackbarFlow.value = "Ulinzi wa PIN umewashwa."
            } else {
                snackbarFlow.value = res.exceptionOrNull()?.message ?: "PIN si sahihi."
            }
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            pinManager.clearPin()
            pinConfiguredFlow.value = false
            isLockedFlow.value = false
            repository.updateBusinessSettings { it.copy(pinEnabled = false, biometricEnabled = false) }
            snackbarFlow.value = "Ulinzi wa PIN umezimwa."
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBusinessSettings { it.copy(biometricEnabled = enabled) }
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val result = pinManager.verifyPin(pin)
            if (result.success) {
                pinErrorFlow.value = null
                isLockedFlow.value = false
            } else {
                pinErrorFlow.value = if (result.remainingCooldownSeconds > 0) {
                    "Subiri sekunde ${result.remainingCooldownSeconds} kabla ya kujaribu tena."
                } else {
                    "PIN si sahihi."
                }
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        pinErrorFlow.value = null
        isLockedFlow.value = false
    }

    fun lockAppNow() {
        if (pinConfiguredFlow.value) {
            isLockedFlow.value = true
        }
    }

    fun exportReport(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            val report = uiState.value.currentReport ?: return@launch
            val bizName = uiState.value.settings.businessName
            isBusyFlow.value = true
            val result = when (format) {
                ExportFormat.CSV -> reportExporter.exportCsv(uri, report, bizName)
                ExportFormat.EXCEL -> reportExporter.exportExcelXml(uri, report, bizName)
                ExportFormat.PDF -> reportExporter.exportPdf(uri, report, bizName)
            }
            isBusyFlow.value = false
            snackbarFlow.value = if (result.isSuccess) {
                "Ripoti ya ${format.label} imehifadhiwa kikamilifu."
            } else {
                result.exceptionOrNull()?.message ?: "Imeshindwa kuhamisha ripoti."
            }
        }
    }

    fun exportEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = backupRestoreManager.exportEncryptedBackupToUri(uri, passphrase)
            isBusyFlow.value = false
            snackbarFlow.value = if (res.isSuccess) {
                "Chelezo kilichosimbwa kimehifadhiwa."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kuhifadhi chelezo."
            }
        }
    }

    fun restoreEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = backupRestoreManager.restoreEncryptedBackupFromUri(uri, passphrase)
            isBusyFlow.value = false
            snackbarFlow.value = if (res.isSuccess) {
                "Data imerejeshwa kikamilifu kutoka chelezo."
            } else {
                res.exceptionOrNull()?.message ?: "Imeshindwa kurejesha chelezo (angalia neno la siri)."
            }
        }
    }

    fun runManualAutoBackupNow() {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = backupRestoreManager.writeLocalAutoBackup()
            isBusyFlow.value = false
            snackbarFlow.value = if (res.isSuccess) {
                "Chelezo cha ndani kimehifadhiwa kwa usalama."
            } else {
                res.exceptionOrNull()?.message ?: "Hitilafu ya chelezo."
            }
        }
    }

    fun deleteArchivedSms() {
        viewModelScope.launch {
            val count = repository.deleteArchivedSmsRecords()
            snackbarFlow.value = "Rekodi $count za SMS zimefutwa."
        }
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            val res = repository.deleteAllLocalData()
            snackbarFlow.value = if (res.isSuccess) {
                "Data zote za ndani zimefutwa."
            } else {
                "Imeshindwa kufuta data."
            }
        }
    }

    private fun applyTransactionFilters(
        list: List<TransactionRecord>,
        f: TransactionFilters
    ): List<TransactionRecord> {
        val now = System.currentTimeMillis()
        val minTs = when (f.dateFilter) {
            DateFilterOption.ALL -> 0L
            DateFilterOption.TODAY -> Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            DateFilterOption.LAST_7_DAYS -> now - 7L * 24 * 60 * 60 * 1000
            DateFilterOption.THIS_MONTH -> Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val q = f.query.trim().lowercase()
        return list.filter { tx ->
            val matchesQuery = q.isEmpty() ||
                tx.transactionId.lowercase().contains(q) ||
                (tx.senderOrRecipientPhone?.lowercase()?.contains(q) == true) ||
                tx.senderOrRecipientName.lowercase().contains(q) ||
                tx.description.lowercase().contains(q)
            val matchesNet = f.network == null || tx.network == f.network
            val matchesType = f.type == null || tx.type == f.type
            val matchesStatus = f.status == null || tx.verificationStatus == f.status
            val matchesDate = tx.timestampEpochMs >= minTs
            matchesQuery && matchesNet && matchesType && matchesStatus && matchesDate
        }
    }

    private fun applySmsSearch(list: List<SmsArchiveRecord>, query: String): List<SmsArchiveRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { sms ->
            sms.senderIdentifier.lowercase().contains(q) ||
                sms.originalContent.lowercase().contains(q) ||
                (sms.extractedTransactionId?.lowercase()?.contains(q) == true)
        }
    }

    class Factory(
        private val repository: MauzoRepository,
        private val txEngine: FinancialTransactionEngine,
        private val reportGenerator: FinancialReportGenerator,
        private val reportExporter: ReportExporter,
        private val backupRestoreManager: BackupRestoreManager,
        private val smsInboxImporter: SmsInboxImporter,
        private val pinManager: PinManager,
        private val appContext: android.content.Context,
        private val firestoreSyncRepository: FirestoreSyncRepository? = null,
        private val currentUserId: String = "",
        private val currentUserEmail: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MauzoViewModel(
                repository = repository,
                txEngine = txEngine,
                reportGenerator = reportGenerator,
                reportExporter = reportExporter,
                backupRestoreManager = backupRestoreManager,
                smsInboxImporter = smsInboxImporter,
                pinManager = pinManager,
                appContext = appContext,
                firestoreSyncRepository = firestoreSyncRepository,
                currentUserId = currentUserId,
                currentUserEmail = currentUserEmail
            ) as T
        }
    }
}

enum class ExportFormat(val label: String, val mimeType: String, val ext: String) {
    CSV("CSV", "text/csv", "csv"),
    EXCEL("Excel", "application/vnd.ms-excel", "xls"),
    PDF("PDF", "application/pdf", "pdf")
}
