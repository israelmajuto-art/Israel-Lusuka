package com.example.verification

import com.example.domain.MobileMoneyNetwork
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.sms_parser.ParsedTransactionData
import com.example.sms_parser.SmsParseResult
import com.example.sms_parser.SmsPreFilter
import java.math.BigDecimal

data class VerificationContext(
    val senderIdentifier: String,
    val existingTransactions: List<TransactionRecord>,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val customSendersByNetwork: Map<MobileMoneyNetwork, Set<String>> = emptyMap()
)

data class VerificationOutcome(
    val status: VerificationStatus,
    val reasons: List<String>,
    val isDuplicate: Boolean = false,
    val isSuspicious: Boolean = false,
    val disclaimerNote: String
)

/**
 * Rule-based verification engine.
 * IMPORTANT: Passing SMS format verification NEVER implies official provider confirmation.
 */
class TransactionVerificationEngine {

    companion object {
        const val DISCLAIMER_SW =
            "Umbizo limethibitishwa (SMS) — Hii haithibitishi rasmi kutoka kwa mtandao kuwa fedha zimeingia."
        const val DISCLAIMER_EN =
            "Format Validated (SMS) — Not independently confirmed by mobile money provider."
        private val PERSONAL_PHONE_SENDER_REGEX = Regex("""^(?:\+?255|0)[67]\d{8}$""")
        private val MAX_SINGLE_TX_TZS = BigDecimal("50000000.00")
    }

    fun verifyParsedSms(
        parseResult: SmsParseResult,
        context: VerificationContext
    ): VerificationOutcome {
        val reasons = mutableListOf<String>()
        val sender = context.senderIdentifier.trim()
        val isManualPaste = sender.equals("MANUAL_IMPORT", ignoreCase = true) ||
            sender.equals("MANUAL", ignoreCase = true)

        // 1. Sender check (Anti-spoofing: personal phone numbers must never spoof official SMS)
        if (PERSONAL_PHONE_SENDER_REGEX.matches(sender.replace(" ", ""))) {
            reasons.add("Mtumaji ni namba ya simu binafsi ($sender), sio kitambulisho rasmi cha mtandao.")
            return VerificationOutcome(
                status = VerificationStatus.SUSPICIOUS,
                reasons = reasons,
                isSuspicious = true,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        val senderRecognized = SmsPreFilter.isRecognizedSender(sender, context.customSendersByNetwork)
        if (!senderRecognized && !isManualPaste) {
            reasons.add("Kitambulisho cha mtumaji ($sender) hakiko kwenye orodha rasmi ya mitandao.")
        }

        // 2. Message format check
        val parsed: ParsedTransactionData? = when (parseResult) {
            is SmsParseResult.Parsed -> parseResult.data
            is SmsParseResult.Partial -> {
                reasons.add(parseResult.warning)
                parseResult.data
            }
            is SmsParseResult.Unrecognized -> {
                reasons.add(parseResult.reason)
                return VerificationOutcome(
                    status = VerificationStatus.NEEDS_REVIEW,
                    reasons = reasons,
                    disclaimerNote = DISCLAIMER_SW
                )
            }
        }

        // 3. Transaction ID check
        val txId = parsed!!.transactionId?.trim()
        if (txId.isNullOrBlank()) {
            reasons.add("Namba ya kumbukumbu (Transaction ID) haikupatikana.")
        }

        // 4. Amount check
        val amount = parsed.amount
        if (amount == null || amount <= BigDecimal.ZERO) {
            reasons.add("Kiasi cha muamala hakijapatikana au si sahihi.")
        } else if (amount > MAX_SINGLE_TX_TZS) {
            reasons.add("Kiasi kinazidi kiwango cha kawaida cha muamala mmoja (${amount.toPlainString()}).")
        }

        // 5. Fee & balance consistency checks
        val fee = parsed.fee
        if (fee != null && amount != null && (fee < BigDecimal.ZERO || fee > amount)) {
            reasons.add("Ada ya muamala ina utata ikilinganishwa na kiasi.")
            return VerificationOutcome(
                status = VerificationStatus.SUSPICIOUS,
                reasons = reasons,
                isSuspicious = true,
                disclaimerNote = DISCLAIMER_SW
            )
        }
        if (parsed.remainingBalance != null && parsed.remainingBalance < BigDecimal.ZERO) {
            reasons.add("Salio lililoripotiwa ni hasi, jambo linalotia shaka.")
            return VerificationOutcome(
                status = VerificationStatus.SUSPICIOUS,
                reasons = reasons,
                isSuspicious = true,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        // 6. Date/time check
        val parsedTs = parsed.parsedTimestampEpochMs
        if (parsedTs == null) {
            reasons.add("Tarehe au saa ya muamala haikupatikana kwenye SMS.")
        } else if (parsedTs > context.nowEpochMs + 24L * 60 * 60 * 1000) {
            reasons.add("Tarehe ya muamala iko mbele ya muda halisi wa kifaa.")
            return VerificationOutcome(
                status = VerificationStatus.SUSPICIOUS,
                reasons = reasons,
                isSuspicious = true,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        // 7. Duplicate Transaction ID detection
        if (!txId.isNullOrBlank()) {
            val duplicateById = context.existingTransactions.firstOrNull {
                it.transactionId.equals(txId, ignoreCase = true) &&
                    it.verificationStatus != VerificationStatus.DUPLICATE
            }
            if (duplicateById != null) {
                reasons.add("Muamala wenye kumbukumbu $txId tayari upo kwenye daftari.")
                return VerificationOutcome(
                    status = VerificationStatus.DUPLICATE,
                    reasons = reasons,
                    isDuplicate = true,
                    disclaimerNote = DISCLAIMER_SW
                )
            }
        } else if (amount != null && parsedTs != null) {
            val twoMinutesMs = 120_000L
            val nearDuplicate = context.existingTransactions.firstOrNull { existing ->
                existing.network == parsed.network &&
                    existing.amount.compareTo(amount) == 0 &&
                    existing.senderOrRecipientPhone == parsed.senderOrRecipientPhone &&
                    kotlin.math.abs(existing.timestampEpochMs - parsedTs) <= twoMinutesMs &&
                    existing.verificationStatus != VerificationStatus.DUPLICATE
            }
            if (nearDuplicate != null) {
                reasons.add("Muamala unaofanana ndani ya dakika 2 tayari umerekodiwa.")
                return VerificationOutcome(
                    status = VerificationStatus.DUPLICATE,
                    reasons = reasons,
                    isDuplicate = true,
                    disclaimerNote = DISCLAIMER_SW
                )
            }
        }

        // 8. Reversed / Refund status detection
        if (parsed.transactionType == TransactionType.REVERSAL) {
            if (reasons.isEmpty()) {
                reasons.add("Muamala wa ubatilishaji umebainika kupitia umbizo la SMS.")
            }
            return VerificationOutcome(
                status = VerificationStatus.REVERSED,
                reasons = reasons,
                disclaimerNote = DISCLAIMER_SW
            )
        }
        if (parsed.transactionType == TransactionType.REFUND) {
            if (reasons.isEmpty()) {
                reasons.add("Muamala wa rejesho umebainika kupitia umbizo la SMS.")
            }
            return VerificationOutcome(
                status = VerificationStatus.REFUNDED,
                reasons = reasons,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        // 9. Final routing: FORMAT_VALIDATED vs NEEDS_REVIEW vs SUSPICIOUS
        if (!senderRecognized && !isManualPaste) {
            return VerificationOutcome(
                status = VerificationStatus.SUSPICIOUS,
                reasons = reasons,
                isSuspicious = true,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        if (parseResult is SmsParseResult.Partial || txId.isNullOrBlank() || amount == null || parsed.transactionType == null) {
            return VerificationOutcome(
                status = VerificationStatus.NEEDS_REVIEW,
                reasons = reasons,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        if (reasons.isNotEmpty() && !isManualPaste) {
            return VerificationOutcome(
                status = VerificationStatus.NEEDS_REVIEW,
                reasons = reasons,
                disclaimerNote = DISCLAIMER_SW
            )
        }

        return VerificationOutcome(
            status = VerificationStatus.FORMAT_VALIDATED,
            reasons = listOf(DISCLAIMER_SW),
            disclaimerNote = DISCLAIMER_SW
        )
    }

    fun validateManualTransaction(
        tx: TransactionRecord,
        existingTransactions: List<TransactionRecord>
    ): VerificationOutcome {
        val reasons = mutableListOf<String>()
        if (tx.amount <= BigDecimal.ZERO) {
            reasons.add("Kiasi lazima kiwe kikubwa kuliko sifuri.")
            return VerificationOutcome(VerificationStatus.SUSPICIOUS, reasons, isSuspicious = true, disclaimerNote = DISCLAIMER_SW)
        }
        if (tx.fee < BigDecimal.ZERO) {
            reasons.add("Ada haiwezi kuwa hasi.")
            return VerificationOutcome(VerificationStatus.SUSPICIOUS, reasons, isSuspicious = true, disclaimerNote = DISCLAIMER_SW)
        }
        val dup = existingTransactions.firstOrNull {
            it.id != tx.id &&
                it.transactionId.equals(tx.transactionId, ignoreCase = true) &&
                it.verificationStatus != VerificationStatus.DUPLICATE
        }
        if (dup != null) {
            reasons.add("Namba ya kumbukumbu ${tx.transactionId} tayari ipo.")
            return VerificationOutcome(VerificationStatus.DUPLICATE, reasons, isDuplicate = true, disclaimerNote = DISCLAIMER_SW)
        }
        if (tx.network == MobileMoneyNetwork.UNKNOWN) {
            reasons.add("Mtandao haujachaguliwa.")
            return VerificationOutcome(VerificationStatus.NEEDS_REVIEW, reasons, disclaimerNote = DISCLAIMER_SW)
        }
        return VerificationOutcome(
            status = when (tx.type) {
                TransactionType.REVERSAL -> VerificationStatus.REVERSED
                TransactionType.REFUND -> VerificationStatus.REFUNDED
                else -> VerificationStatus.FORMAT_VALIDATED
            },
            reasons = listOf("Imeingizwa au kuhakikiwa na mmiliki wa biashara."),
            disclaimerNote = DISCLAIMER_SW
        )
    }
}
