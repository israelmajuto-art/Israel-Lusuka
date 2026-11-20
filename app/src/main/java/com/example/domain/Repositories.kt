package com.example.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

data class SmsIngestionOutcome(
    val smsArchiveId: Long,
    val transactionLocalId: Long?,
    val processingStatus: SmsProcessingStatus,
    val verificationStatus: VerificationStatus?,
    val message: String
)

interface MauzoRepository {
    fun observeTransactions(): Flow<List<TransactionRecord>>
    fun observeSmsArchive(): Flow<List<SmsArchiveRecord>>
    fun observeWallets(): Flow<List<WalletSummary>>
    fun observeOpeningBalances(): Flow<List<OpeningBalanceRecord>>
    fun observeBusinessSettings(): Flow<BusinessSettings>
    fun observeNetworkFormats(): Flow<List<NetworkFormatConfig>>
    fun observeAuditLogs(): Flow<List<AuditLogRecord>>

    suspend fun getTransactionById(id: Long): TransactionRecord?
    suspend fun getSmsArchiveById(id: Long): SmsArchiveRecord?

    suspend fun ingestIncomingSms(
        sender: String,
        body: String,
        receivedAtEpochMs: Long,
        importSource: String = "SMS_RECEIVER",
        simSlot: Int? = null
    ): SmsIngestionOutcome

    suspend fun addManualTransaction(
        transaction: TransactionRecord,
        reason: String
    ): Result<Long>

    suspend fun updateTransactionDetails(
        updated: TransactionRecord,
        reason: String
    ): Result<Unit>

    suspend fun linkReversalOrRefund(
        originalTransactionId: String,
        newType: TransactionType,
        amount: BigDecimal,
        network: MobileMoneyNetwork,
        reason: String
    ): Result<Long>

    suspend fun setOpeningBalance(
        network: MobileMoneyNetwork,
        amount: BigDecimal,
        note: String
    ): Result<Unit>

    suspend fun reconcileWallet(
        network: MobileMoneyNetwork,
        providerReportedBalance: BigDecimal,
        adjustCalculatedBalance: Boolean,
        reason: String
    ): Result<Unit>

    suspend fun updateBusinessSettings(transform: (BusinessSettings) -> BusinessSettings)
    suspend fun toggleNetworkEnabled(network: MobileMoneyNetwork, enabled: Boolean)
    suspend fun updateNetworkSenders(network: MobileMoneyNetwork, senderIdsCsv: String): Result<Unit>

    suspend fun recalculateAllWallets()
    suspend fun deleteArchivedSmsRecords(): Int
    suspend fun deleteAllLocalData(): Result<Unit>

    suspend fun exportBackupJson(): String
    suspend fun restoreBackupJson(jsonPayload: String): Result<Unit>
}
