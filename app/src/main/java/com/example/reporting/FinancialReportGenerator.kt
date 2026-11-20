package com.example.reporting

import com.example.domain.ChartBucket
import com.example.domain.FinancialReport
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkComparisonRow
import com.example.domain.OpeningBalanceRecord
import com.example.domain.ReportPeriod
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.transaction_engine.FinancialTransactionEngine
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FinancialReportGenerator(
    private val txEngine: FinancialTransactionEngine = FinancialTransactionEngine()
) {

    fun generate(
        period: ReportPeriod,
        allTransactions: List<TransactionRecord>,
        openingBalances: List<OpeningBalanceRecord>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): FinancialReport {
        val (startMs, endMs) = resolveBounds(period, nowEpochMs)
        val periodTxs = allTransactions
            .filter { it.timestampEpochMs in startMs..endMs }
            .sortedByDescending { it.timestampEpochMs }

        val activeTxs = periodTxs.filter { txEngine.isFinanciallyActive(it) }

        val totalReceived = sumByType(activeTxs, TransactionType.MONEY_RECEIVED)
        val totalSent = sumByType(activeTxs, TransactionType.MONEY_SENT)
        val totalDeposits = sumByType(activeTxs, TransactionType.CASH_DEPOSIT)
        val totalWithdrawals = sumByType(activeTxs, TransactionType.CASH_WITHDRAWAL)
        val explicitFees = sumByType(activeTxs, TransactionType.TRANSACTION_FEE)
        val embeddedFees = activeTxs
            .filter { it.type != TransactionType.TRANSACTION_FEE }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.fee.max(BigDecimal.ZERO)) }
        val totalFees = explicitFees.add(embeddedFees)
        val totalRefunds = sumByType(activeTxs, TransactionType.REFUND)
        val totalReversals = sumByType(activeTxs, TransactionType.REVERSAL)

        val wallets = MobileMoneyNetwork.supportedNetworks.map { net ->
            txEngine.calculateWalletSummary(
                network = net,
                openingBalance = openingBalances.firstOrNull { it.network == net },
                allTransactions = allTransactions
            )
        }

        val networkComparisons = MobileMoneyNetwork.supportedNetworks.map { net ->
            val netTxs = activeTxs.filter { it.network == net }
            val netReceived = netTxs
                .filter { it.type == TransactionType.MONEY_RECEIVED || it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.REFUND }
                .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }
            val netSent = netTxs
                .filter { it.type == TransactionType.MONEY_SENT || it.type == TransactionType.CASH_WITHDRAWAL || it.type == TransactionType.REVERSAL }
                .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }
            val netFees = netTxs.fold(MoneyUtils.ZERO) { acc, t ->
                val f = if (t.type == TransactionType.TRANSACTION_FEE) t.amount else t.fee.max(BigDecimal.ZERO)
                acc.add(f)
            }
            NetworkComparisonRow(
                network = net,
                received = netReceived,
                sent = netSent,
                fees = netFees,
                transactionCount = netTxs.size,
                calculatedBalance = wallets.firstOrNull { it.network == net }?.calculatedBalance ?: MoneyUtils.ZERO
            )
        }

        val buckets = buildChartBuckets(period, startMs, endMs, activeTxs)

        return FinancialReport(
            period = period,
            startEpochMs = startMs,
            endEpochMs = endMs,
            totalReceived = totalReceived,
            totalSent = totalSent,
            totalDeposits = totalDeposits,
            totalWithdrawals = totalWithdrawals,
            totalFees = totalFees,
            totalRefunds = totalRefunds,
            totalReversals = totalReversals,
            transactionCount = activeTxs.size,
            networkComparisons = networkComparisons,
            walletSummaries = wallets,
            chartBuckets = buckets,
            transactions = periodTxs
        )
    }

    private fun sumByType(txs: List<TransactionRecord>, type: TransactionType): BigDecimal =
        txs.filter { it.type == type }.fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }

    private fun resolveBounds(period: ReportPeriod, nowMs: Long): Pair<Long, Long> {
        val endCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val startCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (period) {
                ReportPeriod.DAILY -> Unit
                ReportPeriod.WEEKLY -> add(Calendar.DAY_OF_YEAR, -6)
                ReportPeriod.MONTHLY -> set(Calendar.DAY_OF_MONTH, 1)
                ReportPeriod.ANNUAL -> {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    private fun buildChartBuckets(
        period: ReportPeriod,
        startMs: Long,
        endMs: Long,
        activeTxs: List<TransactionRecord>
    ): List<ChartBucket> {
        return when (period) {
            ReportPeriod.DAILY -> {
                val labels = listOf("00-06", "06-12", "12-18", "18-24")
                labels.mapIndexed { idx, label ->
                    val minHour = idx * 6
                    val maxHour = minHour + 6
                    val matching = activeTxs.filter { tx ->
                        val h = Calendar.getInstance().apply { timeInMillis = tx.timestampEpochMs }
                            .get(Calendar.HOUR_OF_DAY)
                        h in minHour until maxHour
                    }
                    toChartBucket(label, matching)
                }
            }
            ReportPeriod.WEEKLY -> {
                val fmt = SimpleDateFormat("EEE", Locale.US)
                (0..6).map { offset ->
                    val dayStart = Calendar.getInstance().apply {
                        timeInMillis = startMs
                        add(Calendar.DAY_OF_YEAR, offset)
                    }
                    val dayEnd = dayStart.timeInMillis + 24L * 60 * 60 * 1000 - 1
                    val matching = activeTxs.filter { it.timestampEpochMs in dayStart.timeInMillis..dayEnd }
                    toChartBucket(fmt.format(Date(dayStart.timeInMillis)), matching)
                }
            }
            ReportPeriod.MONTHLY -> {
                val weeks = listOf(1..7, 8..14, 15..21, 22..31)
                weeks.mapIndexed { idx, range ->
                    val matching = activeTxs.filter { tx ->
                        val day = Calendar.getInstance().apply { timeInMillis = tx.timestampEpochMs }
                            .get(Calendar.DAY_OF_MONTH)
                        day in range
                    }
                    toChartBucket("W${idx + 1}", matching)
                }
            }
            ReportPeriod.ANNUAL -> {
                val monthFmt = SimpleDateFormat("MMM", Locale.US)
                (0..11).map { m ->
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = startMs
                        set(Calendar.MONTH, m)
                    }
                    val matching = activeTxs.filter { tx ->
                        Calendar.getInstance().apply { timeInMillis = tx.timestampEpochMs }
                            .get(Calendar.MONTH) == m
                    }
                    toChartBucket(monthFmt.format(cal.time), matching)
                }
            }
        }
    }

    private fun toChartBucket(label: String, txs: List<TransactionRecord>): ChartBucket {
        val inflow = txs
            .filter { it.type == TransactionType.MONEY_RECEIVED || it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.REFUND }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }
        val outflow = txs
            .filter { it.type == TransactionType.MONEY_SENT || it.type == TransactionType.CASH_WITHDRAWAL || it.type == TransactionType.TRANSACTION_FEE || it.type == TransactionType.REVERSAL }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount).add(t.fee.max(BigDecimal.ZERO)) }
        return ChartBucket(label = label, inflow = inflow, outflow = outflow)
    }
}
