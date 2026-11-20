package com.example.sms_parser

import com.example.domain.MobileMoneyNetwork

/**
 * Routes incoming SMS through pre-filtering and dispatches to the independent network parser.
 */
class SmsParserOrchestrator(
    private val parsers: List<NetworkSmsParser> = listOf(
        MpesaSmsParser(),
        AirtelMoneySmsParser(),
        MixxByYasSmsParser()
    )
) {
    fun parseSms(sender: String, body: String, receivedAtEpochMs: Long): SmsParseResult {
        val matched = parsers.firstOrNull { it.canParse(sender, body) }
            ?: return SmsParseResult.Unrecognized(
                networkHint = SmsPreFilter.identifyNetworkFromSender(sender)
                    .takeIf { it != MobileMoneyNetwork.UNKNOWN }
                    ?: SmsPreFilter.inferNetworkFromContent(body),
                reason = "Mtandao wa SMS haujatambuliwa"
            )
        return matched.parse(sender, body, receivedAtEpochMs)
    }
}
