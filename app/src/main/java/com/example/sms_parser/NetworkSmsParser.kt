package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedTransactionData(
    val network: MobileMoneyNetwork,
    val transactionId: String?,
    val amount: BigDecimal?,
    val senderOrRecipientName: String?,
    val senderOrRecipientPhone: String?,
    val transactionDateText: String?,
    val transactionTimeText: String?,
    val parsedTimestampEpochMs: Long?,
    val transactionType: TransactionType?,
    val description: String,
    val fee: BigDecimal?,
    val remainingBalance: BigDecimal?,
    val relatedTransactionId: String?,
    val formatVersion: String,
    val missingFields: List<String> = emptyList()
)

sealed class SmsParseResult {
    data class Parsed(val data: ParsedTransactionData) : SmsParseResult()
    data class Partial(val data: ParsedTransactionData, val warning: String) : SmsParseResult()
    data class Unrecognized(val networkHint: MobileMoneyNetwork, val reason: String) : SmsParseResult()
}

interface NetworkSmsParser {
    val supportedNetwork: MobileMoneyNetwork
    fun canParse(sender: String, body: String): Boolean
    fun parse(sender: String, body: String, fallbackEpochMs: Long): SmsParseResult
}

internal object SmsRegexCommon {
    private val phoneRegex = Regex("""(?:\+?255|0)(?:6\d|7\d)\d{7}""")
    private val dateNumericRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
    private val timeRegex = Regex("""\b(\d{1,2}:\d{2}(?:\s*[APap][Mm])?)\b""")
    private val feeRegex = Regex(
        """(?:makato|ada|fee|transaction\s*cost|charges?|gharama)\s*(?:ni|:|ya)?\s*(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val balanceRegex = Regex(
        """(?:salio(?:\s+jipya|\s+lako)?(?:\s+la\s+[^:]+)?|new\s+(?:m-?pesa|airtel\s+money|mixx|tigo\s*pesa)?\s*balance|balance)\s*(?:ni|is|:)?\s*(?:tsh|tzs)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    fun extractPhone(text: String): String? {
        val raw = phoneRegex.find(text)?.value ?: return null
        return when {
            raw.startsWith("+") -> raw
            raw.startsWith("255") -> "+$raw"
            raw.startsWith("0") -> "+255${raw.substring(1)}"
            else -> raw
        }
    }

    fun extractDate(text: String): String? = dateNumericRegex.find(text)?.groupValues?.getOrNull(1)

    fun extractTime(text: String): String? = timeRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase(Locale.US)

    fun extractFee(text: String): BigDecimal? {
        val raw = feeRegex.find(text)?.groupValues?.getOrNull(1) ?: return null
        return MoneyUtils.parseAmountOrNull(raw)
    }

    fun extractBalance(text: String): BigDecimal? {
        val raw = balanceRegex.find(text)?.groupValues?.getOrNull(1) ?: return null
        return MoneyUtils.parseAmountOrNull(raw)
    }

    fun parseTimestamp(dateText: String?, timeText: String?): Long? {
        if (dateText.isNullOrBlank()) return null
        val normalizedDate = dateText.replace('-', '/')
        val datePatterns = listOf("dd/MM/yyyy", "dd/MM/yy")
        val timePatterns = if (!timeText.isNullOrBlank()) {
            listOf("h:mm a", "hh:mm a", "HH:mm", "H:mm")
        } else {
            emptyList()
        }

        for (dp in datePatterns) {
            if (timePatterns.isNotEmpty()) {
                for (tp in timePatterns) {
                    val sdf = SimpleDateFormat("$dp $tp", Locale.US).apply { isLenient = false }
                    val parsed = runCatching { sdf.parse("$normalizedDate ${timeText!!.trim()}") }.getOrNull()
                    if (parsed != null) return parsed.time
                }
            }
            val sdf = SimpleDateFormat(dp, Locale.US).apply { isLenient = false }
            val parsed = runCatching { sdf.parse(normalizedDate) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }
}
