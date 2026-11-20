package com.example.data

import com.example.domain.AppLanguage
import com.example.domain.AuditLogRecord
import com.example.domain.BusinessSettings
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkFormatConfig
import com.example.domain.OpeningBalanceRecord
import com.example.domain.SmsArchiveRecord
import com.example.domain.SmsProcessingStatus
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.domain.WalletSummary
import java.math.BigDecimal

internal fun TransactionEntity.toDomain(): TransactionRecord = TransactionRecord(
    id = id,
    transactionId = transactionId,
    network = MobileMoneyNetwork.fromCode(network),
    type = TransactionType.fromCode(type),
    amount = MoneyUtils.fromMinorUnits(amountMinor),
    fee = MoneyUtils.fromMinorUnits(feeMinor),
    senderOrRecipientName = senderOrRecipientName,
    senderOrRecipientPhone = senderOrRecipientPhone,
    transactionDateText = transactionDateText,
    transactionTimeText = transactionTimeText,
    timestampEpochMs = timestampEpochMs,
    description = description,
    providerReportedBalance = providerReportedBalanceMinor?.let { MoneyUtils.fromMinorUnits(it) },
    verificationStatus = VerificationStatus.fromCode(verificationStatus),
    verificationNotes = verificationNotes,
    isManualEntry = isManualEntry,
    isCreditAdjustment = isCreditAdjustment,
    relatedTransactionId = relatedTransactionId,
    smsArchiveId = smsArchiveId,
    formatVersion = formatVersion,
    reconciliationNote = reconciliationNote,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

internal fun TransactionRecord.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    transactionId = transactionId,
    network = network.code,
    type = type.code,
    amountMinor = MoneyUtils.toMinorUnits(amount),
    feeMinor = MoneyUtils.toMinorUnits(fee),
    senderOrRecipientName = senderOrRecipientName,
    senderOrRecipientPhone = senderOrRecipientPhone,
    transactionDateText = transactionDateText,
    transactionTimeText = transactionTimeText,
    timestampEpochMs = timestampEpochMs,
    description = description,
    providerReportedBalanceMinor = providerReportedBalance?.let { MoneyUtils.toMinorUnits(it) },
    verificationStatus = verificationStatus.code,
    verificationNotes = verificationNotes,
    isManualEntry = isManualEntry,
    isCreditAdjustment = isCreditAdjustment,
    relatedTransactionId = relatedTransactionId,
    smsArchiveId = smsArchiveId,
    formatVersion = formatVersion,
    reconciliationNote = reconciliationNote,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

internal fun SmsArchiveEntity.toDomain(): SmsArchiveRecord = SmsArchiveRecord(
    id = id,
    senderIdentifier = senderIdentifier,
    originalContent = originalContent,
    network = MobileMoneyNetwork.fromCode(network),
    extractedTransactionId = extractedTransactionId,
    extractedAmount = extractedAmountMinor?.let { MoneyUtils.fromMinorUnits(it) },
    receivedAtEpochMs = receivedAtEpochMs,
    processingStatus = SmsProcessingStatus.fromCode(processingStatus),
    parseErrorReason = parseErrorReason,
    relatedTransactionLocalId = relatedTransactionLocalId,
    importSource = importSource
)

internal fun OpeningBalanceEntity.toDomain(): OpeningBalanceRecord = OpeningBalanceRecord(
    id = id,
    network = MobileMoneyNetwork.fromCode(network),
    amount = MoneyUtils.fromMinorUnits(amountMinor),
    effectiveDateEpochMs = effectiveDateEpochMs,
    note = note,
    updatedAtEpochMs = updatedAtEpochMs
)

internal fun WalletSummary.toEntity(nowMs: Long): WalletEntity = WalletEntity(
    network = network.code,
    calculatedBalanceMinor = MoneyUtils.toMinorUnits(calculatedBalance),
    providerReportedBalanceMinor = providerReportedBalance?.let { MoneyUtils.toMinorUnits(it) },
    providerBalanceUpdatedAtEpochMs = providerBalanceUpdatedAtEpochMs,
    lastReconciledAtEpochMs = lastReconciledAtEpochMs,
    transactionCount = transactionCount,
    updatedAtEpochMs = nowMs
)

internal fun WalletEntity.toDomain(openingBalance: BigDecimal): WalletSummary = WalletSummary(
    network = MobileMoneyNetwork.fromCode(network),
    openingBalance = MoneyUtils.scale(openingBalance),
    calculatedBalance = MoneyUtils.fromMinorUnits(calculatedBalanceMinor),
    providerReportedBalance = providerReportedBalanceMinor?.let { MoneyUtils.fromMinorUnits(it) },
    providerBalanceUpdatedAtEpochMs = providerBalanceUpdatedAtEpochMs,
    lastReconciledAtEpochMs = lastReconciledAtEpochMs,
    transactionCount = transactionCount
)

internal fun NetworkFormatEntity.toDomain(): NetworkFormatConfig = NetworkFormatConfig(
    network = MobileMoneyNetwork.fromCode(network),
    displayName = displayName,
    senderIdentifiersCsv = senderIdentifiersCsv,
    formatVersion = formatVersion,
    isEnabled = isEnabled
)

internal fun BusinessSettingsEntity.toDomain(enabledNetworks: Set<MobileMoneyNetwork>): BusinessSettings =
    BusinessSettings(
        businessName = businessName,
        primaryLanguage = AppLanguage.fromCode(primaryLanguage),
        currencyCode = currencyCode,
        hideBalancesOnHome = hideBalancesOnHome,
        pinEnabled = pinEnabled,
        biometricEnabled = biometricEnabled,
        autoBackupEnabled = autoBackupEnabled,
        lastBackupEpochMs = lastBackupEpochMs,
        enabledNetworks = enabledNetworks,
        onboardingCompleted = onboardingCompleted,
        smsNotificationsEnabled = smsNotificationsEnabled,
        simSlotFilter = simSlotFilter
    )

internal fun AuditLogEntity.toDomain(): AuditLogRecord = AuditLogRecord(
    id = id,
    entityType = entityType,
    entityId = entityId,
    action = action,
    summary = summary,
    previousValue = previousValue,
    newValue = newValue,
    reason = reason,
    timestampEpochMs = timestampEpochMs
)
