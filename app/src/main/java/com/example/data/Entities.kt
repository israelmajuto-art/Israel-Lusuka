package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_archive",
    indices = [
        Index(value = ["extractedTransactionId"]),
        Index(value = ["network"]),
        Index(value = ["processingStatus"]),
        Index(value = ["receivedAtEpochMs"])
    ]
)
data class SmsArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val senderIdentifier: String,
    val originalContent: String,
    val network: String,
    val extractedTransactionId: String?,
    val extractedAmountMinor: Long?,
    val receivedAtEpochMs: Long,
    val processingStatus: String,
    val parseErrorReason: String?,
    val relatedTransactionLocalId: Long?,
    val importSource: String
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = SmsArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["smsArchiveId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["transactionId"], unique = true),
        Index(value = ["network"]),
        Index(value = ["type"]),
        Index(value = ["verificationStatus"]),
        Index(value = ["timestampEpochMs"]),
        Index(value = ["senderOrRecipientPhone"]),
        Index(value = ["smsArchiveId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: String,
    val network: String,
    val type: String,
    val amountMinor: Long,
    val feeMinor: Long,
    val senderOrRecipientName: String,
    val senderOrRecipientPhone: String?,
    val transactionDateText: String,
    val transactionTimeText: String,
    val timestampEpochMs: Long,
    val description: String,
    val providerReportedBalanceMinor: Long?,
    val verificationStatus: String,
    val verificationNotes: String,
    val isManualEntry: Boolean,
    val isCreditAdjustment: Boolean,
    val relatedTransactionId: String?,
    val smsArchiveId: Long?,
    val formatVersion: String?,
    val reconciliationNote: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val network: String,
    val calculatedBalanceMinor: Long,
    val providerReportedBalanceMinor: Long?,
    val providerBalanceUpdatedAtEpochMs: Long?,
    val lastReconciledAtEpochMs: Long?,
    val transactionCount: Int,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "opening_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["network"],
            childColumns = ["network"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["network"], unique = true)]
)
data class OpeningBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val network: String,
    val amountMinor: Long,
    val effectiveDateEpochMs: Long,
    val note: String,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "network_formats")
data class NetworkFormatEntity(
    @PrimaryKey val network: String,
    val displayName: String,
    val senderIdentifiersCsv: String,
    val formatVersion: String,
    val isEnabled: Boolean,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val businessName: String,
    val primaryLanguage: String,
    val currencyCode: String,
    val hideBalancesOnHome: Boolean,
    val pinEnabled: Boolean,
    val biometricEnabled: Boolean,
    val autoBackupEnabled: Boolean,
    val lastBackupEpochMs: Long?,
    val onboardingCompleted: Boolean,
    val lastAutoBackupStatus: String?,
    val smsNotificationsEnabled: Boolean = true,
    val simSlotFilter: Int = 0
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["timestampEpochMs"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String,
    val entityId: String,
    val action: String,
    val summary: String,
    val previousValue: String?,
    val newValue: String?,
    val reason: String?,
    val timestampEpochMs: Long
)
