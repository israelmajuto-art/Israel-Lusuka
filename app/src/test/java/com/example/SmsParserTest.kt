package com.example

import com.example.domain.MobileMoneyNetwork
import com.example.domain.TransactionType
import com.example.sms_parser.AirtelMoneySmsParser
import com.example.sms_parser.MixxByYasSmsParser
import com.example.sms_parser.MpesaSmsParser
import com.example.sms_parser.PreFilterDecision
import com.example.sms_parser.SmsParseResult
import com.example.sms_parser.SmsParserOrchestrator
import com.example.sms_parser.SmsPreFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SmsParserTest {

    private val orchestrator = SmsParserOrchestrator()
    private val mpesaParser = MpesaSmsParser()
    private val airtelParser = AirtelMoneySmsParser()
    private val mixxParser = MixxByYasSmsParser()

    @Test
    fun `mpesa swahili received sms parses accurately`() {
        val sms = "ABC12XYZ89 Imethibitishwa. Umepokea Tsh 45,500.50 kutoka kwa JUMA HASSAN 0754123456 tarehe 20/11/2026 saa 10:35 AM. Salio jipya la M-Pesa ni Tsh 195,500.50."
        val result = mpesaParser.parse("MPESA", sms, 1000L)
        assertTrue(result is SmsParseResult.Parsed)
        val data = (result as SmsParseResult.Parsed).data
        assertEquals(MobileMoneyNetwork.MPESA, data.network)
        assertEquals("ABC12XYZ89", data.transactionId)
        assertEquals(BigDecimal("45500.50"), data.amount)
        assertEquals(TransactionType.MONEY_RECEIVED, data.transactionType)
        assertEquals("JUMA HASSAN", data.senderOrRecipientName)
        assertEquals("+255754123456", data.senderOrRecipientPhone)
        assertEquals(BigDecimal("195500.50"), data.remainingBalance)
    }

    @Test
    fun `mpesa english sent sms parses fee and amount`() {
        val sms = "DEF99KLM11 Confirmed. You have sent Tsh20,000.00 to MARIA PAUL 255755998877 on 20/11/26 at 2:15 PM. Transaction cost Tsh 450.00. New M-Pesa balance is Tsh175,050.00."
        val result = orchestrator.parseSms("M-PESA", sms, 1000L)
        assertTrue(result is SmsParseResult.Parsed)
        val data = (result as SmsParseResult.Parsed).data
        assertEquals("DEF99KLM11", data.transactionId)
        assertEquals(TransactionType.MONEY_SENT, data.transactionType)
        assertEquals(BigDecimal("20000.00"), data.amount)
        assertEquals(BigDecimal("450.00"), data.fee)
        assertEquals(BigDecimal("175050.00"), data.remainingBalance)
    }

    @Test
    fun `airtel money sms parses dotted reference id and balance`() {
        val sms = "Muamala umekamilika. Umepokea TZS 75,000 kutoka kwa AMINA BAKARI (0784112233). Kumbukumbu: AM261120.1432.A09123. Ada: TZS 0. Salio lako ni TZS 125,000. Tarehe: 20/11/2026 14:32."
        val result = airtelParser.parse("AIRTELMONEY", sms, 1000L)
        assertTrue(result is SmsParseResult.Parsed)
        val data = (result as SmsParseResult.Parsed).data
        assertEquals(MobileMoneyNetwork.AIRTEL_MONEY, data.network)
        assertEquals("AM261120.1432.A09123", data.transactionId)
        assertEquals(BigDecimal("75000.00"), data.amount)
        assertEquals(BigDecimal("0.00"), data.fee)
        assertEquals(BigDecimal("125000.00"), data.remainingBalance)
    }

    @Test
    fun `mixx by yas sms parses received transaction and missing fields stay null`() {
        val sms = "Umepokea TSh 30,500 kutoka kwa NEEMA KILONGO - 0713998877. Kumbukumbu namba: YAS99887766. Tarehe 20/11/2026 09:15. Makato TSh 0. Salio jipya ni TSh 80,500."
        val result = mixxParser.parse("MIXX BY YAS", sms, 1000L)
        assertTrue(result is SmsParseResult.Parsed)
        val data = (result as SmsParseResult.Parsed).data
        assertEquals(MobileMoneyNetwork.MIXX_BY_YAS, data.network)
        assertEquals("YAS99887766", data.transactionId)
        assertEquals(BigDecimal("30500.00"), data.amount)
        assertEquals("NEEMA KILONGO", data.senderOrRecipientName)
    }

    @Test
    fun `partial corrupted sms does not invent missing transaction id or party name`() {
        val corrupted = "M-Pesa: Umepokea Tsh 12,000 ### ujumbe umekatika"
        val result = orchestrator.parseSms("MPESA", corrupted, 1000L)
        assertTrue(result is SmsParseResult.Partial)
        val data = (result as SmsParseResult.Partial).data
        assertNull(data.transactionId)
        assertNull(data.senderOrRecipientName)
        assertEquals(BigDecimal("12000.00"), data.amount)
    }

    @Test
    fun `otp and personal messages are rejected by prefilter`() {
        val otpSms = "Namba yako ya siri (OTP) ya M-Pesa ni 482910. Usishirikishe mtu yeyote."
        val personalSms = "Habari kaka, tutaonana dukani saa kumi jioni leo."
        assertEquals(PreFilterDecision.IGNORE_OTP, SmsPreFilter.evaluate("MPESA", otpSms))
        assertEquals(PreFilterDecision.IGNORE_NON_TRANSACTION, SmsPreFilter.evaluate("+255754000111", personalSms))
    }
}
