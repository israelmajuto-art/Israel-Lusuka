package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.TransactionType
import java.math.BigDecimal

/**
 * Independent parser for Vodacom M-Pesa Tanzania SMS formats (Swahili & English, v1/v2).
 */
class MpesaSmsParser : NetworkSmsParser {
    override val supportedNetwork: MobileMoneyNetwork = MobileMoneyNetwork.MPESA

    private val txIdPrefixRegex = Regex("""^([A-Z0-9]{8,14})\s+(?:Confirmed|Imethibitishwa)\b""", RegexOption.IGNORE_CASE)
    private val txIdLabelRegex = Regex("""(?:kumbukumbu|ref(?:erence)?|transaction\s*id|namba\s+ya\s+muamala)\s*(?:no\.?|namba|:)?\s*([A-Z0-9]{8,14})""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex("""(?:umepokea|umeshapokea|received|umetuma|sent|umetoa|withdrawn|umeweka|deposited|umelipa|paid|rejesho|refund(?:ed)?|imebatilishwa|reversed)\s+(?:kiasi\s+cha\s+)?(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val genericAmountRegex = Regex("""(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val partyFromRegex = Regex("""(?:kutoka\s+kwa|from)\s+([A-Za-z][A-Za-z\s.'-]{1,45}?)(?=\s+(?:\+?255|0\d|tarehe|on\b|kumbukumbu|wakala|agent|\(|-|\.|,))""", RegexOption.IGNORE_CASE)
    private val partyToRegex = Regex("""(?:kwenda\s+kwa|kwa|to|wakala|agent)\s+([A-Za-z0-9][A-Za-z0-9\s.'-]{1,45}?)(?=\s+(?:\+?255|0\d|tarehe|on\b|kumbukumbu|\(|-|\.|,))""", RegexOption.IGNORE_CASE)
    private val reversedRefRegex = Regex("""(?:reversal\s+of|ubatilishaji\s+wa|ya\s+muamala)\s+([A-Z0-9]{8,14})""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean {
        val senderNet = SmsPreFilter.identifyNetworkFromSender(sender)
        if (senderNet == MobileMoneyNetwork.MPESA) return true
        val lower = body.lowercase()
        return senderNet == MobileMoneyNetwork.UNKNOWN && (lower.contains("m-pesa") || lower.contains("mpesa"))
    }

    override fun parse(sender: String, body: String, fallbackEpochMs: Long): SmsParseResult {
        val cleaned = body.trim().replace("\\s+".toRegex(), " ")
        if (cleaned.isEmpty()) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.MPESA, "Ujumbe hauna maandishi")
        }

        val lower = cleaned.lowercase()
        val txId = txIdPrefixRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()
            ?: txIdLabelRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()

        val txType = detectType(lower)
        val amountStr = amountRegex.find(cleaned)?.groupValues?.getOrNull(1)
            ?: genericAmountRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val amount = MoneyUtils.parseAmountOrNull(amountStr)

        if (txType == null && amount == null && txId == null) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.MPESA, "Umbizo la M-Pesa halitambuliki")
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
        val relatedId = reversedRefRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()

        val formatVersion = when {
            txIdPrefixRegex.containsMatchIn(cleaned) && lower.contains("imethibitishwa") -> "MPESA_SW_V2"
            txIdPrefixRegex.containsMatchIn(cleaned) -> "MPESA_EN_V2"
            lower.contains("umeshapokea") || lower.contains("umepokea") -> "MPESA_SW_V1"
            else -> "MPESA_GENERIC_V1"
        }

        val missing = buildList {
            if (txId == null) add("transactionId")
            if (amount == null || amount <= BigDecimal.ZERO) add("amount")
            if (partyName == null) add("senderOrRecipientName")
            if (dateText == null) add("transactionDate")
            if (timeText == null) add("transactionTime")
        }

        val data = ParsedTransactionData(
            network = MobileMoneyNetwork.MPESA,
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
            relatedTransactionId = relatedId,
            formatVersion = formatVersion,
            missingFields = missing
        )

        return if (txId == null || amount == null || txType == null) {
            SmsParseResult.Partial(data, "Baadhi ya taarifa za muamala wa M-Pesa hazikupatikana")
        } else {
            SmsParseResult.Parsed(data)
        }
    }

    private fun detectType(lower: String): TransactionType? = when {
        lower.contains("imebatilishwa") || lower.contains("umebatilishwa") || lower.contains("reversed") || lower.contains("reversal") ->
            TransactionType.REVERSAL
        lower.contains("rejesho") || lower.contains("umerudishiwa") || lower.contains("refund") ->
            TransactionType.REFUND
        lower.contains("umeshapokea") || lower.contains("umepokea") || lower.contains("you have received") || lower.contains("received tsh") || lower.contains("received tzs") ->
            TransactionType.MONEY_RECEIVED
        lower.contains("umetoa") || lower.contains("withdrawn") || lower.contains("withdraw") || lower.contains("cash out") ->
            TransactionType.CASH_WITHDRAWAL
        lower.contains("umeweka") || lower.contains("deposited") || lower.contains("deposit") ->
            TransactionType.CASH_DEPOSIT
        lower.contains("umetuma") || lower.contains("umelipa") || lower.contains("you have sent") || lower.contains("sent to") || lower.contains("paid to") ->
            TransactionType.MONEY_SENT
        else -> null
    }

    private fun buildDescription(type: TransactionType?, party: String?, phone: String?): String {
        val partyPart = party ?: phone ?: "Haijulikani"
        return when (type) {
            TransactionType.MONEY_RECEIVED -> "Malipo kutoka $partyPart (M-Pesa)"
            TransactionType.MONEY_SENT -> "Malipo kwenda $partyPart (M-Pesa)"
            TransactionType.CASH_WITHDRAWAL -> "Kutoa fedha kwa Wakala $partyPart"
            TransactionType.CASH_DEPOSIT -> "Kuweka fedha kupitia $partyPart"
            TransactionType.REVERSAL -> "Ubatilishaji wa muamala M-Pesa"
            TransactionType.REFUND -> "Rejesho kutoka $partyPart"
            else -> "Muamala wa M-Pesa"
        }
    }
}
