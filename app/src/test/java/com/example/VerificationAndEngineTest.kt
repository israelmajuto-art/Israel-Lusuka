package com.example

import com.example.domain.MobileMoneyNetwork
import com.example.domain.OpeningBalanceRecord
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.sms_parser.SmsParserOrchestrator
import com.example.transaction_engine.FinancialTransactionEngine
import com.example.verification.TransactionVerificationEngine
import com.example.verification.VerificationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class VerificationAndEngineTest {

    private val parser = SmsParserOrchestrator()
    private val verifier = TransactionVerificationEngine()
    private val engine = FinancialTransactionEngine()

    private fun sampleTx(
        id: Long = 1L,
        txId: String = "TX1001",
        network: MobileMoneyNetwork = MobileMoneyNetwork.MPESA,
        type: TransactionType = TransactionType.MONEY_RECEIVED,
        amount: String = "50000.00",
        fee: String = "0.00",
        status: VerificationStatus = VerificationStatus.FORMAT_VALIDATED,
        relatedTxId: String? = null,
        ts: Long = 1_700_000_000_000L
    ) = TransactionRecord(
        id = id,
        transactionId = txId,
        network = network,
        type = type,
        amount = BigDecimal(amount),
        fee = BigDecimal(fee),
        senderOrRecipientName = "Mteja",
        senderOrRecipientPhone = "+255754111222",
        transactionDateText = "20/11/2026",
        transactionTimeText = "10:00",
        timestampEpochMs = ts,
        description = "Test",
        providerReportedBalance = null,
        verificationStatus = status,
        verificationNotes = "",
        relatedTransactionId = relatedTxId
    )

    @Test
    fun `verification flags duplicate transaction id and personal phone spoofing`() {
        val sms = "ABC12XYZ89 Confirmed. You have received Tsh50,000.00 from JOHN DOE 0754123456 on 20/11/26 at 2:15 PM."
        val parsed = parser.parseSms("MPESA", sms, 1_700_000_000_000L)

        // Duplicate check
        val dupOutcome = verifier.verifyParsedSms(
            parsed,
            VerificationContext(
                senderIdentifier = "MPESA",
                existingTransactions = listOf(sampleTx(txId = "ABC12XYZ89")),
                nowEpochMs = 1_800_000_000_000L
            )
        )
        assertEquals(VerificationStatus.DUPLICATE, dupOutcome.status)
        assertTrue(dupOutcome.isDuplicate)

        // Spoofed sender check (personal 10-digit phone sending M-Pesa text)
        val spoofOutcome = verifier.verifyParsedSms(
            parsed,
            VerificationContext(
                senderIdentifier = "+255754999888",
                existingTransactions = emptyList(),
                nowEpochMs = 1_800_000_000_000L
            )
        )
        assertEquals(VerificationStatus.SUSPICIOUS, spoofOutcome.status)
        assertTrue(spoofOutcome.isSuspicious)
    }

    @Test
    fun `wallet balance calculation prevents duplicate and double reversal counting`() {
        val opening = OpeningBalanceRecord(
            network = MobileMoneyNetwork.MPESA,
            amount = BigDecimal("100000.00"),
            effectiveDateEpochMs = 1000L,
            note = "Opening",
            updatedAtEpochMs = 1000L
        )
        val txs = listOf(
            sampleTx(id = 1, txId = "TX1", type = TransactionType.MONEY_RECEIVED, amount = "50000.00", ts = 2000L),
            // Duplicate TX1 must be ignored
            sampleTx(id = 2, txId = "TX1", type = TransactionType.MONEY_RECEIVED, amount = "50000.00", status = VerificationStatus.DUPLICATE, ts = 2100L),
            sampleTx(id = 3, txId = "TX2", type = TransactionType.MONEY_SENT, amount = "20000.00", fee = "500.00", ts = 3000L),
            sampleTx(id = 4, txId = "TX3", type = TransactionType.CASH_WITHDRAWAL, amount = "10000.00", fee = "1200.00", ts = 4000L),
            sampleTx(id = 5, txId = "TX4", type = TransactionType.REFUND, amount = "5000.00", relatedTxId = "TX2", ts = 5000L),
            // Reversal of TX1
            sampleTx(id = 6, txId = "REV1", type = TransactionType.REVERSAL, amount = "50000.00", relatedTxId = "TX1", status = VerificationStatus.REVERSED, ts = 6000L),
            // Second duplicate reversal targeting TX1 must NOT double-subtract
            sampleTx(id = 7, txId = "REV2", type = TransactionType.REVERSAL, amount = "50000.00", relatedTxId = "TX1", status = VerificationStatus.REVERSED, ts = 7000L)
        )

        val wallet = engine.calculateWalletSummary(MobileMoneyNetwork.MPESA, opening, txs)
        // Expected: 100000 + 50000 - (20000 + 500) - (10000 + 1200) + 5000 - 50000 = 73300.00
        assertEquals(BigDecimal("73300.00"), wallet.calculatedBalance)
    }

    @Test
    fun `reversal and refund validation blocks double reversal and over-refund`() {
        val original = sampleTx(id = 1, txId = "TX100", amount = "25000.00")
        val existingRev = sampleTx(
            id = 2,
            txId = "REV-TX100",
            type = TransactionType.REVERSAL,
            amount = "25000.00",
            relatedTxId = "TX100",
            status = VerificationStatus.REVERSED
        )
        val doubleRev = engine.validateReversalOrRefundLink(
            originalTx = original,
            newType = TransactionType.REVERSAL,
            requestedAmount = BigDecimal("25000.00"),
            existingTransactions = listOf(original, existingRev)
        )
        assertTrue(doubleRev.isFailure)

        val overRefund = engine.validateReversalOrRefundLink(
            originalTx = original,
            newType = TransactionType.REFUND,
            requestedAmount = BigDecimal("30000.00"),
            existingTransactions = listOf(original)
        )
        assertTrue(overRefund.isFailure)
    }
}
