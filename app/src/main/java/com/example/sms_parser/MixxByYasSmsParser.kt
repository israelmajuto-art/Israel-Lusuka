package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.TransactionType
import java.math.BigDecimal

/**
 * Independent parser for Mixx by Yas (formerly Tigo Pesa) Tanzania SMS formats (Swahili & English).
 */
class MixxByYasSmsParser : NetworkSmsParser {
    override val supportedNetwork: MobileMoneyNetwork = MobileMoneyNetwork.MIXX_BY_YAS

    private val txIdRegex = Regex(
        """(?:kumbukumbu(?:\s+namba)?|ref(?:erence)?|namba\s+ya\s+muamala|transaction\s*id|id)\s*[:\-]?\s*([A-Z0-9]{7,18})""",
        RegexOption.IGNORE_CASE
    )
    private val amountRegex = Regex(
        """(?:umepokea|received|umetuma|sent|umetoa|withdrawn|umeweka|deposited|umelipa|paid|rejesho|refund|imebatilishwa|reversed)\s+(?:kiasi\s+cha\s+)?(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val genericAmountRegex = Regex("""(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val partyFromRegex = Regex("""(?:kutoka\s+kwa|from)\s+([A-Za-z][A-Za-z\s.'-]{1,45}?)(?=\s*(?:-|\(|0\d|\+?255|tarehe|on\b|kumbukumbu|ref|\.|,|$))""", RegexOption.IGNORE_CASE)
    private val partyToRegex = Regex("""(?:kwenda\s+kwa|kwa|to|wakala)\s+([A-Za-z0-9][A-Za-z0-9\s.'-]{1,45}?)(?=\s*(?:-|\(|0\d|\+?255|tarehe|on\b|kumbukumbu|ref|\.|,|$))""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean {
        val senderNet = SmsPreFilter.identifyNetworkFromSender(sender)
        if (senderNet == MobileMoneyNetwork.MIXX_BY_YAS) return true
        val lower = body.lowercase()
        return senderNet == MobileMoneyNetwork.UNKNOWN &&
            (lower.contains("mixx by yas") || lower.contains("mixx") || lower.contains("tigo pesa") || lower.contains("tigopesa"))
    }

    override fun parse(sender: String, body: String, fallbackEpochMs: Long): SmsParseResult {
        val cleaned = body.trim().replace("\\s+".toRegex(), " ")
        if (cleaned.isEmpty()) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.MIXX_BY_YAS, "Ujumbe hauna maandishi")
        }

        val lower = cleaned.lowercase()
        val txId = txIdRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()
        val txType = detectType(lower)
        val amountStr = amountRegex.find(cleaned)?.groupValues?.getOrNull(1)
            ?: genericAmountRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val amount = MoneyUtils.parseAmountOrNull(amountStr)

        if (txType == null && amount == null && txId == null) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.MIXX_BY_YAS, "Umbizo la Mixx by Yas halitambuliki")
        }

        val phone = SmsRegexCommon.extractPhone(cleaned)
        val partyName = when (txType) {
            TransactionType.MONEY_RECEIVED, TransactionType.REFUND ->
                partyFromRegex.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
            else ->
                partyToRegex.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
                    ?: partyFromRegex.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        }

        val dateText = SmsRegexCommon.extractDate(cleaned)
        val timeText = SmsRegexCommon.extractTime(cleaned)
        val parsedTs = SmsRegexCommon.parseTimestamp(dateText, timeText)
        val fee = SmsRegexCommon.extractFee(cleaned)
        val balance = SmsRegexCommon.extractBalance(cleaned)

        val formatVersion = when {
            lower.contains("mixx by yas") -> "MIXX_YAS_V2"
            lower.contains("tigo pesa") || lower.contains("tigopesa") -> "TIGO_PESA_LEGACY_V1"
            else -> "MIXX_GENERIC_V1"
        }

        val missing = buildList {
            if (txId == null) add("transactionId")
            if (amount == null || amount <= BigDecimal.ZERO) add("amount")
            if (partyName == null) add("senderOrRecipientName")
            if (dateText == null) add("transactionDate")
            if (timeText == null) add("transactionTime")
        }

        val data = ParsedTransactionData(
            network = MobileMoneyNetwork.MIXX_BY_YAS,
            transactionId = txId,
            amount = amount,
            senderOrRecipientName = partyName,
            senderOrRecipientPhone = phone,
            transactionDateText = dateText,
            transactionTimeText = timeText,
            parsedTimestampEpochMs = parsedTs,
            transactionType = txType,
            description = buildDescription(txType, partyName, phone),
            fee = fee,
            remainingBalance = balance,
            relatedTransactionId = null,
            formatVersion = formatVersion,
            missingFields = missing
        )

        return if (txId == null || amount == null || txType == null) {
            SmsParseResult.Partial(data, "Baadhi ya taarifa za muamala wa Mixx by Yas hazikupatikana")
        } else {
            SmsParseResult.Parsed(data)
        }
    }

    private fun detectType(lower: String): TransactionType? = when {
        lower.contains("imebatilishwa") || lower.contains("reversed") || lower.contains("reversal") ->
            TransactionType.REVERSAL
        lower.contains("rejesho") || lower.contains("umerudishiwa") || lower.contains("refund") ->
            TransactionType.REFUND
        lower.contains("umepokea") || lower.contains("received") ->
            TransactionType.MONEY_RECEIVED
        lower.contains("umetoa") || lower.contains("withdrawn") || lower.contains("cash out") ->
            TransactionType.CASH_WITHDRAWAL
        lower.contains("umeweka") || lower.contains("deposited") || lower.contains("deposit") ->
            TransactionType.CASH_DEPOSIT
        lower.contains("umetuma") || lower.contains("umelipa") || lower.contains("sent") || lower.contains("paid") ->
            TransactionType.MONEY_SENT
        else -> null
    }

    private fun buildDescription(type: TransactionType?, party: String?, phone: String?): String {
        val partyPart = party ?: phone ?: "Haijulikani"
        return when (type) {
            TransactionType.MONEY_RECEIVED -> "Malipo kutoka $partyPart (Mixx by Yas)"
            TransactionType.MONEY_SENT -> "Malipo kwenda $partyPart (Mixx by Yas)"
            TransactionType.CASH_WITHDRAWAL -> "Kutoa fedha Wakala $partyPart (Mixx)"
            TransactionType.CASH_DEPOSIT -> "Kuweka fedha $partyPart (Mixx)"
            TransactionType.REVERSAL -> "Ubatilishaji wa muamala Mixx by Yas"
            TransactionType.REFUND -> "Rejesho kutoka $partyPart (Mixx)"
            else -> "Muamala wa Mixx by Yas"
        }
    }
}
