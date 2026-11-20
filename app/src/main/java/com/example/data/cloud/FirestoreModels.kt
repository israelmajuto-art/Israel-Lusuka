package com.example.data.cloud

import androidx.annotation.Keep
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.OpeningBalanceRecord
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.math.BigDecimal

@Keep
@IgnoreExtraProperties
data class UserBusinessProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessName: String = "Biashara Yangu",
    val primaryLanguage: String = AppLanguage.SWAHILI.code,
    val currencyCode: String = "TZS",
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "businessName" to businessName.trim().ifBlank { "Biashara Yangu" }.take(100),
            "primaryLanguage" to if (primaryLanguage == "en") "en" else "sw",
            "currencyCode" to currencyCode.trim().ifBlank { "TZS" }.uppercase().take(5),
            "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "businessName" to businessName.trim().ifBlank { "Biashara Yangu" }.take(100),
            "primaryLanguage" to if (primaryLanguage == "en") "en" else "sw",
            "currencyCode" to currencyCode.trim().ifBlank { "TZS" }.uppercase().take(5),
            "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class CloudTransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transactionId: String = "",
    val network: String = MobileMoneyNetwork.UNKNOWN.code,
    val type: String = TransactionType.MONEY_RECEIVED.code,
    val amount: String = "0.00",
    val fee: String = "0.00",
    val senderOrRecipientName: String = "Haijulikani",
    val senderOrRecipientPhone: String? = null,
    val transactionDateText: String = "01/01/2026",
    val transactionTimeText: String = "00:00",
    val timestampEpochMs: Long = 0L,
    val description: String = "",
    val providerReportedBalance: String? = null,
    val verificationStatus: String = VerificationStatus.NEEDS_REVIEW.code,
    val verificationNotes: String = "",
    val manualEntry: Boolean = false,
    val creditAdjustment: Boolean = true,
    val relatedTransactionId: String? = null,
    val formatVersion: String? = null,
    val reconciliationNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(localId: Long = 0L): TransactionRecord {
        val parsedAmount = MoneyUtils.parseAmountOrNull(amount) ?: BigDecimal.ZERO
        val parsedFee = MoneyUtils.parseAmountOrNull(fee) ?: BigDecimal.ZERO
        val parsedProviderBal = providerReportedBalance?.let { MoneyUtils.parseAmountOrNull(it) }
        return TransactionRecord(
            id = localId,
            transactionId = transactionId,
            network = MobileMoneyNetwork.fromCode(network),
            type = TransactionType.fromCode(type),
            amount = parsedAmount,
            fee = parsedFee,
            senderOrRecipientName = senderOrRecipientName.ifBlank { "Haijulikani" },
            senderOrRecipientPhone = senderOrRecipientPhone,
            transactionDateText = transactionDateText,
            transactionTimeText = transactionTimeText,
            timestampEpochMs = timestampEpochMs,
            description = description,
            providerReportedBalance = parsedProviderBal,
            verificationStatus = VerificationStatus.fromCode(verificationStatus),
            verificationNotes = verificationNotes,
            isManualEntry = manualEntry,
            isCreditAdjustment = creditAdjustment,
            relatedTransactionId = relatedTransactionId,
            smsArchiveId = null,
            formatVersion = formatVersion,
            reconciliationNote = reconciliationNote,
            createdAtEpochMs = createdAt?.toDate()?.time ?: timestampEpochMs,
            updatedAtEpochMs = updatedAt?.toDate()?.time ?: timestampEpochMs
        )
    }

    companion object {
        fun buildDocId(tx: TransactionRecord): String {
            val raw = "${tx.network.code}_${tx.transactionId}"
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .take(100)
            return raw.ifBlank { "tx_${tx.id.coerceAtLeast(0L)}" }
        }

        fun toCreateMap(uid: String, tx: TransactionRecord): Map<String, Any> {
            val safeTxId = tx.transactionId.trim().ifBlank { "TX-${tx.timestampEpochMs}" }.take(80)
            val safeName = tx.senderOrRecipientName.trim().ifBlank { "Haijulikani" }.take(120)
            val safeDate = tx.transactionDateText.trim().ifBlank { "01/01/2026" }.take(30)
            val safeTime = tx.transactionTimeText.trim().ifBlank { "00:00" }.take(20)
            val raw = mutableMapOf<String, Any?>(
                "userId" to uid,
                "transactionId" to safeTxId,
                "network" to tx.network.code,
                "type" to tx.type.code,
                "amount" to MoneyUtils.scale(tx.amount).toPlainString().take(32),
                "fee" to MoneyUtils.scale(tx.fee).toPlainString().take(32),
                "senderOrRecipientName" to safeName,
                "senderOrRecipientPhone" to tx.senderOrRecipientPhone?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                "transactionDateText" to safeDate,
                "transactionTimeText" to safeTime,
                "timestampEpochMs" to tx.timestampEpochMs.coerceAtLeast(0L),
                "description" to tx.description.trim().take(300),
                "providerReportedBalance" to tx.providerReportedBalance?.let { MoneyUtils.scale(it).toPlainString().take(32) },
                "verificationStatus" to tx.verificationStatus.code,
                "verificationNotes" to tx.verificationNotes.trim().take(400),
                "manualEntry" to tx.isManualEntry,
                "creditAdjustment" to tx.isCreditAdjustment,
                "relatedTransactionId" to tx.relatedTransactionId?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                "formatVersion" to tx.formatVersion?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                "reconciliationNote" to tx.reconciliationNote?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }

        fun toUpdateMap(tx: TransactionRecord): Map<String, Any> {
            val safeTxId = tx.transactionId.trim().ifBlank { "TX-${tx.timestampEpochMs}" }.take(80)
            val safeName = tx.senderOrRecipientName.trim().ifBlank { "Haijulikani" }.take(120)
            val safeDate = tx.transactionDateText.trim().ifBlank { "01/01/2026" }.take(30)
            val safeTime = tx.transactionTimeText.trim().ifBlank { "00:00" }.take(20)
            val raw = mutableMapOf<String, Any?>(
                "transactionId" to safeTxId,
                "network" to tx.network.code,
                "type" to tx.type.code,
                "amount" to MoneyUtils.scale(tx.amount).toPlainString().take(32),
                "fee" to MoneyUtils.scale(tx.fee).toPlainString().take(32),
                "senderOrRecipientName" to safeName,
                "senderOrRecipientPhone" to tx.senderOrRecipientPhone?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                "transactionDateText" to safeDate,
                "transactionTimeText" to safeTime,
                "timestampEpochMs" to tx.timestampEpochMs.coerceAtLeast(0L),
                "description" to tx.description.trim().take(300),
                "providerReportedBalance" to tx.providerReportedBalance?.let { MoneyUtils.scale(it).toPlainString().take(32) },
                "verificationStatus" to tx.verificationStatus.code,
                "verificationNotes" to tx.verificationNotes.trim().take(400),
                "manualEntry" to tx.isManualEntry,
                "creditAdjustment" to tx.isCreditAdjustment,
                "relatedTransactionId" to tx.relatedTransactionId?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                "formatVersion" to tx.formatVersion?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
                "reconciliationNote" to tx.reconciliationNote?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudOpeningBalance(
    @DocumentId val id: String = "",
    val userId: String = "",
    val network: String = MobileMoneyNetwork.MPESA.code,
    val amount: String = "0.00",
    val effectiveDateEpochMs: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): OpeningBalanceRecord = OpeningBalanceRecord(
        id = 0L,
        network = MobileMoneyNetwork.fromCode(network),
        amount = MoneyUtils.parseAmountOrNull(amount) ?: BigDecimal.ZERO,
        effectiveDateEpochMs = effectiveDateEpochMs,
        note = note,
        updatedAtEpochMs = updatedAt?.toDate()?.time ?: effectiveDateEpochMs
    )

    companion object {
        fun toCreateMap(uid: String, ob: OpeningBalanceRecord): Map<String, Any> = mapOf(
            "userId" to uid,
            "network" to ob.network.code,
            "amount" to MoneyUtils.scale(ob.amount).toPlainString().take(32),
            "effectiveDateEpochMs" to ob.effectiveDateEpochMs.coerceAtLeast(0L),
            "note" to ob.note.trim().take(200),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        fun toUpdateMap(ob: OpeningBalanceRecord): Map<String, Any> = mapOf(
            "network" to ob.network.code,
            "amount" to MoneyUtils.scale(ob.amount).toPlainString().take(32),
            "effectiveDateEpochMs" to ob.effectiveDateEpochMs.coerceAtLeast(0L),
            "note" to ob.note.trim().take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
