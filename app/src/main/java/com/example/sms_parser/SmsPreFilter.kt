package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork

enum class PreFilterDecision {
    PROCESS_MOBILE_MONEY,
    IGNORE_OTP,
    IGNORE_NON_TRANSACTION
}

/**
 * Filters incoming SMS so OTPs, authentication codes, and personal/promotional SMS
 * are never stored or treated as financial transactions.
 */
object SmsPreFilter {

    private val mpesaSenders = setOf(
        "MPESA", "M-PESA", "VODACOM", "MPESATZ", "15000", "15300"
    )
    private val airtelSenders = setOf(
        "AIRTELMONEY", "AIRTEL MONEY", "AIRTEL", "AIRTELTZ", "15050", "15002"
    )
    private val mixxSenders = setOf(
        "MIXX BY YAS", "MIXXBYYAS", "MIXX", "YAS", "TIGOPESA", "TIGO PESA", "TIGO", "15001"
    )

    private val otpKeywords = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "security code", "pin reset", "namba ya siri", "msimbo wa siri",
        "usishirikishe", "do not share this code", "token yako ni", "auth code"
    )

    private val transactionSignals = listOf(
        "tsh", "tzs", "umepokea", "umeshapokea", "received", "umetuma", "sent",
        "umetoa", "withdrawn", "umeweka", "deposited", "umelipa", "paid",
        "kumbukumbu", "transaction id", "ref", "salio", "balance", "imebatilishwa",
        "reversed", "rejesho", "refund", "confirmed", "imethibitishwa", "muamala"
    )

    fun identifyNetworkFromSender(
        sender: String,
        customSendersByNetwork: Map<MobileMoneyNetwork, Set<String>> = emptyMap()
    ): MobileMoneyNetwork {
        val normalized = sender.trim().uppercase().replace("\\s+".toRegex(), " ")
        if (normalized.isEmpty()) return MobileMoneyNetwork.UNKNOWN
        val compact = normalized.replace(" ", "")

        for ((net, customIds) in customSendersByNetwork) {
            if (customIds.any { id ->
                    val clean = id.trim().uppercase()
                    clean.isNotEmpty() && (normalized == clean || compact == clean.replace(" ", ""))
                }
            ) {
                return net
            }
        }

        return when {
            mpesaSenders.any { normalized == it || compact == it.replace(" ", "") || normalized.contains("M-PESA") || normalized.contains("MPESA") } ->
                MobileMoneyNetwork.MPESA
            airtelSenders.any { normalized == it || compact == it.replace(" ", "") || normalized.contains("AIRTEL") } ->
                MobileMoneyNetwork.AIRTEL_MONEY
            mixxSenders.any { normalized == it || compact == it.replace(" ", "") || normalized.contains("MIXX") || normalized.contains("TIGO") || normalized == "YAS" } ->
                MobileMoneyNetwork.MIXX_BY_YAS
            else -> MobileMoneyNetwork.UNKNOWN
        }
    }

    fun isRecognizedSender(
        sender: String,
        customSendersByNetwork: Map<MobileMoneyNetwork, Set<String>> = emptyMap()
    ): Boolean =
        identifyNetworkFromSender(sender, customSendersByNetwork) != MobileMoneyNetwork.UNKNOWN

    fun inferNetworkFromContent(body: String): MobileMoneyNetwork {
        val lower = body.lowercase()
        return when {
            lower.contains("m-pesa") || lower.contains("mpesa") || lower.contains("vodacom") ->
                MobileMoneyNetwork.MPESA
            lower.contains("airtel money") || lower.contains("airtelmoney") || Regex("""\bam\d{6}\.""", RegexOption.IGNORE_CASE).containsMatchIn(body) ->
                MobileMoneyNetwork.AIRTEL_MONEY
            lower.contains("mixx by yas") || lower.contains("mixx") || lower.contains("tigo pesa") || lower.contains("tigopesa") ->
                MobileMoneyNetwork.MIXX_BY_YAS
            else -> MobileMoneyNetwork.UNKNOWN
        }
    }

    fun evaluate(
        sender: String,
        body: String,
        customSendersByNetwork: Map<MobileMoneyNetwork, Set<String>> = emptyMap()
    ): PreFilterDecision {
        val trimmedBody = body.trim()
        if (trimmedBody.length < 15) return PreFilterDecision.IGNORE_NON_TRANSACTION

        val lower = trimmedBody.lowercase()
        val hasCurrency = lower.contains("tsh") || lower.contains("tzs")
        val hasOtpWord = otpKeywords.any { lower.contains(it) }

        // Reject OTPs strictly when no currency receipt is present or when OTP terms dominate
        if (hasOtpWord && !hasCurrency) {
            return PreFilterDecision.IGNORE_OTP
        }
        if (hasOtpWord && (lower.contains("msimbo") || lower.contains("code is") || lower.contains("otp is"))) {
            return PreFilterDecision.IGNORE_OTP
        }

        val senderNetwork = identifyNetworkFromSender(sender, customSendersByNetwork)
        val contentNetwork = inferNetworkFromContent(trimmedBody)
        val signalHits = transactionSignals.count { lower.contains(it) }

        return when {
            senderNetwork != MobileMoneyNetwork.UNKNOWN && signalHits >= 2 ->
                PreFilterDecision.PROCESS_MOBILE_MONEY
            contentNetwork != MobileMoneyNetwork.UNKNOWN && hasCurrency && signalHits >= 2 ->
                PreFilterDecision.PROCESS_MOBILE_MONEY
            hasCurrency && signalHits >= 3 ->
                PreFilterDecision.PROCESS_MOBILE_MONEY
            else -> PreFilterDecision.IGNORE_NON_TRANSACTION
        }
    }
}
