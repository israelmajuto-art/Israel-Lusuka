package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Supported Tanzanian mobile money networks.
 */
enum class MobileMoneyNetwork(
    val code: String,
    val displayNameSw: String,
    val displayNameEn: String,
    val shortLabel: String
) {
    MPESA("MPESA", "Vodacom M-Pesa", "Vodacom M-Pesa", "M-Pesa"),
    AIRTEL_MONEY("AIRTEL_MONEY", "Airtel Money", "Airtel Money", "Airtel"),
    MIXX_BY_YAS("MIXX_BY_YAS", "Mixx by Yas", "Mixx by Yas", "Mixx"),
    UNKNOWN("UNKNOWN", "Haijulikani", "Unknown", "N/A");

    companion object {
        fun fromCode(code: String?): MobileMoneyNetwork =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN

        val supportedNetworks: List<MobileMoneyNetwork>
            get() = listOf(MPESA, AIRTEL_MONEY, MIXX_BY_YAS)
    }
}

enum class TransactionType(
    val code: String,
    val labelSw: String,
    val labelEn: String,
    val isInflowByDefault: Boolean
) {
    MONEY_RECEIVED("MONEY_RECEIVED", "Fedha Zimepokelewa", "Money Received", true),
    MONEY_SENT("MONEY_SENT", "Fedha Zimetumwa", "Money Sent", false),
    CASH_WITHDRAWAL("CASH_WITHDRAWAL", "Kutoa Fedha", "Cash Withdrawal", false),
    CASH_DEPOSIT("CASH_DEPOSIT", "Kuweka Fedha", "Cash Deposit", true),
    TRANSACTION_FEE("TRANSACTION_FEE", "Ada ya Muamala", "Transaction Fee", false),
    REFUND("REFUND", "Rejesho", "Refund", true),
    REVERSAL("REVERSAL", "Ubatilishaji", "Reversal", false),
    MANUAL_ADJUSTMENT("MANUAL_ADJUSTMENT", "Marekebisho ya Mkono", "Manual Adjustment", true);

    companion object {
        fun fromCode(code: String?): TransactionType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MANUAL_ADJUSTMENT
    }
}

enum class VerificationStatus(
    val code: String,
    val labelSw: String,
    val labelEn: String
) {
    FORMAT_VALIDATED("FORMAT_VALIDATED", "Umbizo Limethibitishwa (SMS)", "Format Validated (SMS)"),
    NEEDS_REVIEW("NEEDS_REVIEW", "Inahitaji Uhakiki", "Needs Review"),
    DUPLICATE("DUPLICATE", "Nakala / Rudufu", "Duplicate"),
    SUSPICIOUS("SUSPICIOUS", "Inatia Shaka", "Suspicious"),
    REVERSED("REVERSED", "Imebatilishwa", "Reversed"),
    REFUNDED("REFUNDED", "Imerejeshwa", "Refunded");

    companion object {
        fun fromCode(code: String?): VerificationStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NEEDS_REVIEW
    }
}

enum class SmsProcessingStatus(
    val code: String,
    val labelSw: String,
    val labelEn: String
) {
    PARSED("PARSED", "Imesomwa", "Parsed"),
    NEEDS_REVIEW("NEEDS_REVIEW", "Inahitaji Uhakiki", "Needs Review"),
    DUPLICATE("DUPLICATE", "Nakala", "Duplicate"),
    IGNORED_NON_TRANSACTION("IGNORED_NON_TRANSACTION", "Sio Muamala", "Ignored (Non-Tx)"),
    IGNORED_OTP("IGNORED_OTP", "OTP Imepuuzwa", "Ignored (OTP)"),
    FAILED_PARSE("FAILED_PARSE", "Imeshindwa Kusomwa", "Parse Failed"),
    CORRUPTED("CORRUPTED", "Imeharibika", "Corrupted");

    companion object {
        fun fromCode(code: String?): SmsProcessingStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NEEDS_REVIEW
    }
}

enum class ReportPeriod(
    val labelSw: String,
    val labelEn: String
) {
    DAILY("Leo / Siku", "Daily"),
    WEEKLY("Wiki Hii", "Weekly"),
    MONTHLY("Mwezi Huu", "Monthly"),
    ANNUAL("Mwaka Huu", "Annual")
}

enum class AppLanguage(val code: String, val displayName: String) {
    SWAHILI("sw", "Kiswahili"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            if (code.equals("en", ignoreCase = true)) ENGLISH else SWAHILI
    }
}

data class TransactionRecord(
    val id: Long = 0L,
    val transactionId: String,
    val network: MobileMoneyNetwork,
    val type: TransactionType,
    val amount: BigDecimal,
    val fee: BigDecimal = BigDecimal.ZERO,
    val senderOrRecipientName: String,
    val senderOrRecipientPhone: String?,
    val transactionDateText: String,
    val transactionTimeText: String,
    val timestampEpochMs: Long,
    val description: String,
    val providerReportedBalance: BigDecimal?,
    val verificationStatus: VerificationStatus,
    val verificationNotes: String,
    val isManualEntry: Boolean = false,
    val isCreditAdjustment: Boolean = true,
    val relatedTransactionId: String? = null,
    val smsArchiveId: Long? = null,
    val formatVersion: String? = null,
    val reconciliationNote: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class SmsArchiveRecord(
    val id: Long = 0L,
    val senderIdentifier: String,
    val originalContent: String,
    val network: MobileMoneyNetwork,
    val extractedTransactionId: String?,
    val extractedAmount: BigDecimal?,
    val receivedAtEpochMs: Long,
    val processingStatus: SmsProcessingStatus,
    val parseErrorReason: String? = null,
    val relatedTransactionLocalId: Long? = null,
    val importSource: String = "SMS_RECEIVER"
)

data class WalletSummary(
    val network: MobileMoneyNetwork,
    val openingBalance: BigDecimal,
    val calculatedBalance: BigDecimal,
    val providerReportedBalance: BigDecimal?,
    val providerBalanceUpdatedAtEpochMs: Long?,
    val lastReconciledAtEpochMs: Long?,
    val transactionCount: Int
) {
    val discrepancy: BigDecimal?
        get() = providerReportedBalance?.subtract(calculatedBalance)
            ?.setScale(2, RoundingMode.HALF_UP)
}

data class OpeningBalanceRecord(
    val id: Long = 0L,
    val network: MobileMoneyNetwork,
    val amount: BigDecimal,
    val effectiveDateEpochMs: Long,
    val note: String,
    val updatedAtEpochMs: Long
)

data class NetworkFormatConfig(
    val network: MobileMoneyNetwork,
    val displayName: String,
    val senderIdentifiersCsv: String,
    val formatVersion: String,
    val isEnabled: Boolean
)

data class BusinessSettings(
    val businessName: String = "Biashara Yangu",
    val primaryLanguage: AppLanguage = AppLanguage.SWAHILI,
    val currencyCode: String = "TZS",
    val hideBalancesOnHome: Boolean = false,
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val lastBackupEpochMs: Long? = null,
    val enabledNetworks: Set<MobileMoneyNetwork> = MobileMoneyNetwork.supportedNetworks.toSet(),
    val onboardingCompleted: Boolean = false,
    val smsNotificationsEnabled: Boolean = true,
    val simSlotFilter: Int = 0
)

data class AuditLogRecord(
    val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: String,
    val summary: String,
    val previousValue: String?,
    val newValue: String?,
    val reason: String?,
    val timestampEpochMs: Long
)

data class DashboardMetrics(
    val totalCalculatedBalance: BigDecimal,
    val todayReceived: BigDecimal,
    val todaySent: BigDecimal,
    val todayTransactionCount: Int,
    val monthlyIncome: BigDecimal,
    val monthlyExpenses: BigDecimal,
    val walletSummaries: List<WalletSummary>,
    val recentTransactions: List<TransactionRecord>,
    val needsReviewCount: Int,
    val suspiciousCount: Int
)

data class NetworkComparisonRow(
    val network: MobileMoneyNetwork,
    val received: BigDecimal,
    val sent: BigDecimal,
    val fees: BigDecimal,
    val transactionCount: Int,
    val calculatedBalance: BigDecimal
)

data class ChartBucket(
    val label: String,
    val inflow: BigDecimal,
    val outflow: BigDecimal
)

data class FinancialReport(
    val period: ReportPeriod,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val totalReceived: BigDecimal,
    val totalSent: BigDecimal,
    val totalDeposits: BigDecimal,
    val totalWithdrawals: BigDecimal,
    val totalFees: BigDecimal,
    val totalRefunds: BigDecimal,
    val totalReversals: BigDecimal,
    val transactionCount: Int,
    val networkComparisons: List<NetworkComparisonRow>,
    val walletSummaries: List<WalletSummary>,
    val chartBuckets: List<ChartBucket>,
    val transactions: List<TransactionRecord>
)
