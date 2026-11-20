package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.TransactionType
import java.math.BigDecimal

/**
 * Independent parser for Airtel Money Tanzania SMS formats (Swahili & English).
 */
class AirtelMoneySmsParser : NetworkSmsParser {
    override val supportedNetwork: MobileMoneyNetwork = MobileMoneyNetwork.AIRTEL_MONEY

    private val airtelTxIdRegex = Regex(
        """(?:kumbukumbu|transaction\s*id|txn\s*id|ref(?:erence)?)\s*(?:namba|no\.?)?\s*[:\-]?\s*([A-Z]{1,4}[0-9]{6}\.[0-9]{4}\.[A-Z0-9]{4,10}|[A-Z0-9]{8,20})""",
        RegexOption.IGNORE_CASE
    )
    private val dottedIdFallbackRegex = Regex("""\b([A-Z]{2}\d{6}\.\d{4}\.[A-Z0-9]{4,8})\b""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex(
        """(?:umepokea|received|umetuma|sent|umetoa|withdrawn|umeweka|deposited|umelipa|paid|rejesho|refund|imebatilishwa|reversed)\s+(?:kiasi\s+cha\s+)?(?:tzs|tsh)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val genericAmountRegex = Regex("""(?:tzs|tsh)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val partyFromRegex = Regex("""(?:kutoka\s+kwa|from)\s+(?:\+?255\d{9}|0\d{9})?\s*([A-Za-z][A-Za-z\s.'-]{1,45}?)(?=\s*(?:\(|tarehe|on\b|kumbukumbu|ada|fee|salio|\.|,|$))""", RegexOption.IGNORE_CASE)
    private val partyToRegex = Regex("""(?:kwenda\s+kwa|kwa|to|wakala)\s+(?:\+?255\d{9}|0\d{9})?\s*([A-Za-z0-9][A-Za-z0-9\s.'-]{1,45}?)(?=\s*(?:\(|tarehe|on\b|kumbukumbu|ada|fee|salio|\.|,|$))""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean {
        val senderNet = SmsPreFilter.identifyNetworkFromSender(sender)
        if (senderNet == MobileMoneyNetwork.AIRTEL_MONEY) return true
        val lower = body.lowercase()
        return senderNet == MobileMoneyNetwork.UNKNOWN &&
            (lower.contains("airtel money") || lower.contains("airtelmoney") || dottedIdFallbackRegex.containsMatchIn(body))
    }

    override fun parse(sender: String, body: String, fallbackEpochMs: Long): SmsParseResult {
        val cleaned = body.trim().replace("\\s+".toRegex(), " ")
        if (cleaned.isEmpty()) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.AIRTEL_MONEY, "Ujumbe hauna maandishi")
        }

        val lower = cleaned.lowercase()
        val txId = dottedIdFallbackRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()
            ?: airtelTxIdRegex.find(cleaned)?.groupValues?.getOrNull(1)?.uppercase()

        val txType = detectType(lower)
        val amountStr = amountRegex.find(cleaned)?.groupValues?.getOrNull(1)
            ?: genericAmountRegex.find(cleaned)?.groupValues?.getOrNull(1)
        val amount = MoneyUtils.parseAmountOrNull(amountStr)

        if (txType == null && amount == null && txId == null) {
            return SmsParseResult.Unrecognized(MobileMoneyNetwork.AIRTEL_MONEY, "Umbizo la Airtel Money halitambuliki")
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
            lower.contains("muamala umekamilika") -> "AIRTEL_SW_V2"
            lower.contains("transaction id:") -> "AIRTEL_EN_V2"
            else -> "AIRTEL_GENERIC_V1"
        }

        val missing = buildList {
            if (txId == null) add("transactionId")
            if (amount == null || amount <= BigDecimal.ZERO) add("amount")
            if (partyName == null) add("senderOrRecipientName")
            if (dateText == null) add("transactionDate")
            if (timeText == null) add("transactionTime")
        }

        val data = ParsedTransactionData(
            network = MobileMoneyNetwork.AIRTEL_MONEY,
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
            SmsParseResult.Partial(data, "Baadhi ya taarifa za muamala wa Airtel Money hazikupatikana")
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
            TransactionType.MONEY_RECEIVED -> "Malipo kutoka $partyPart (Airtel Money)"
            TransactionType.MONEY_SENT -> "Malipo kwenda $partyPart (Airtel Money)"
            TransactionType.CASH_WITHDRAWAL -> "Kutoa fedha Wakala $partyPart (Airtel)"
            TransactionType.CASH_DEPOSIT -> "Kuweka fedha $partyPart (Airtel)"
            TransactionType.REVERSAL -> "Ubatilishaji wa muamala Airtel Money"
            TransactionType.REFUND -> "Rejesho kutoka $partyPart (Airtel)"
            else -> "Muamala wa Airtel Money"
        }
    }
}
