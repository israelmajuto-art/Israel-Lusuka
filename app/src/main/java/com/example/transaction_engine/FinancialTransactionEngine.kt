package com.example.transaction_engine

import com.example.domain.DashboardMetrics
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.OpeningBalanceRecord
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.domain.WalletSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

/**
 * Pure decimal-safe (BigDecimal) financial transaction and balance engine.
 * Prevents duplicate calculations, double-counting reversals/refunds, and invalid amounts.
 */
class FinancialTransactionEngine {

    /**
     * Determines whether a transaction contributes to wallet balances.
     * DUPLICATE and SUSPICIOUS records are excluded so unverified/duplicate SMS never corrupt balances.
     */
    fun isFinanciallyActive(tx: TransactionRecord): Boolean {
        if (tx.amount <= BigDecimal.ZERO) return false
        return tx.verificationStatus != VerificationStatus.DUPLICATE &&
            tx.verificationStatus != VerificationStatus.SUSPICIOUS
    }

    /**
     * Validates that adding a reversal or refund will not double-count against an original transaction.
     */
    fun validateReversalOrRefundLink(
        originalTx: TransactionRecord?,
        newType: TransactionType,
        requestedAmount: BigDecimal,
        existingTransactions: List<TransactionRecord>
    ): Result<Unit> {
        if (requestedAmount <= BigDecimal.ZERO) {
            return Result.failure(IllegalArgumentException("Kiasi lazima kiwe kikubwa kuliko 0"))
        }
        if (originalTx == null) {
            return Result.failure(IllegalArgumentException("Muamala asili haujapatikana"))
        }
        val activeRelated = existingTransactions.filter {
            isFinanciallyActive(it) &&
                it.relatedTransactionId.equals(originalTx.transactionId, ignoreCase = true)
        }

        return when (newType) {
            TransactionType.REVERSAL -> {
                val alreadyReversed = activeRelated.any { it.type == TransactionType.REVERSAL } ||
                    originalTx.verificationStatus == VerificationStatus.REVERSED
                if (alreadyReversed) {
                    Result.failure(IllegalStateException("Muamala huu tayari umebatilishwa (zuia kuhesabu mara mbili)"))
                } else if (requestedAmount > originalTx.amount) {
                    Result.failure(IllegalArgumentException("Kiasi cha ubatilishaji hakiwezi kuzidi kiasi cha awali"))
                } else {
                    Result.success(Unit)
                }
            }
            TransactionType.REFUND -> {
                val refundedSoFar = activeRelated
                    .filter { it.type == TransactionType.REFUND }
                    .fold(MoneyUtils.ZERO) { acc, r -> acc.add(r.amount) }
                if (refundedSoFar.add(requestedAmount) > originalTx.amount) {
                    Result.failure(IllegalStateException("Jumla ya marejesho haiwezi kuzidi kiasi cha muamala asili"))
                } else {
                    Result.success(Unit)
                }
            }
            else -> Result.failure(IllegalArgumentException("Aina batili ya muamala wa uhusiano"))
        }
    }

    /**
     * Calculates per-network wallet balance using exact BigDecimal arithmetic:
     * Closing = Opening + Received + Deposits + Refunds - Sent - Withdrawals - Fees - Reversals (+/- Manual Adjustments)
     *
     * Deduplicates by transactionId and prevents double-counting reversals.
     */
    fun calculateWalletSummary(
        network: MobileMoneyNetwork,
        openingBalance: OpeningBalanceRecord?,
        allTransactions: List<TransactionRecord>,
        lastReconciledAtEpochMs: Long? = null
    ): WalletSummary {
        val opening = MoneyUtils.scale(openingBalance?.amount ?: BigDecimal.ZERO)
        val seenTxIds = HashSet<String>()
        val seenReversalTargets = HashSet<String>()

        val networkTxs = allTransactions
            .filter { it.network == network }
            .sortedBy { it.timestampEpochMs }

        var balance = opening
        var latestProviderBalance: BigDecimal? = null
        var latestProviderBalanceTs: Long? = null
        var countedTx = 0

        for (tx in networkTxs) {
            if (!isFinanciallyActive(tx)) continue

            // Guard against duplicate Transaction IDs even if verification status was manually altered
            val normalizedId = tx.transactionId.trim().uppercase()
            if (normalizedId.isNotEmpty() && !normalizedId.startsWith("MAN-") && !seenTxIds.add(normalizedId)) {
                continue
            }

            countedTx++
            val safeFee = tx.fee.max(BigDecimal.ZERO)

            when (tx.type) {
                TransactionType.MONEY_RECEIVED -> {
                    balance = balance.add(tx.amount).subtract(safeFee)
                }
                TransactionType.CASH_DEPOSIT -> {
                    balance = balance.add(tx.amount).subtract(safeFee)
                }
                TransactionType.REFUND -> {
                    balance = balance.add(tx.amount).subtract(safeFee)
                }
                TransactionType.MONEY_SENT -> {
                    balance = balance.subtract(tx.amount).subtract(safeFee)
                }
                TransactionType.CASH_WITHDRAWAL -> {
                    balance = balance.subtract(tx.amount).subtract(safeFee)
                }
                TransactionType.TRANSACTION_FEE -> {
                    balance = balance.subtract(tx.amount)
                }
                TransactionType.REVERSAL -> {
                    // Prevent double-counting a reversal for the same original transaction ID
                    val target = tx.relatedTransactionId?.trim()?.uppercase()
                    if (target == null || seenReversalTargets.add(target)) {
                        balance = balance.subtract(tx.amount).subtract(safeFee)
                    }
                }
                TransactionType.MANUAL_ADJUSTMENT -> {
                    balance = if (tx.isCreditAdjustment) {
                        balance.add(tx.amount)
                    } else {
                        balance.subtract(tx.amount)
                    }
                }
            }

            if (tx.providerReportedBalance != null &&
                (latestProviderBalanceTs == null || tx.timestampEpochMs >= latestProviderBalanceTs)
            ) {
                latestProviderBalance = MoneyUtils.scale(tx.providerReportedBalance)
                latestProviderBalanceTs = tx.timestampEpochMs
            }
        }

        return WalletSummary(
            network = network,
            openingBalance = opening,
            calculatedBalance = balance.setScale(2, RoundingMode.HALF_UP),
            providerReportedBalance = latestProviderBalance,
            providerBalanceUpdatedAtEpochMs = latestProviderBalanceTs,
            lastReconciledAtEpochMs = lastReconciledAtEpochMs,
            transactionCount = countedTx
        )
    }

    fun calculateDashboardMetrics(
        transactions: List<TransactionRecord>,
        openingBalances: List<OpeningBalanceRecord>,
        reconciliationTimestamps: Map<MobileMoneyNetwork, Long?> = emptyMap(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): DashboardMetrics {
        val wallets = MobileMoneyNetwork.supportedNetworks.map { net ->
            calculateWalletSummary(
                network = net,
                openingBalance = openingBalances.firstOrNull { it.network == net },
                allTransactions = transactions,
                lastReconciledAtEpochMs = reconciliationTimestamps[net]
            )
        }

        val totalBalance = wallets.fold(MoneyUtils.ZERO) { acc, w -> acc.add(w.calculatedBalance) }

        val cal = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDayMs = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonthMs = cal.timeInMillis

        val active = transactions.filter { isFinanciallyActive(it) }
        val todayTxs = active.filter { it.timestampEpochMs >= startOfDayMs }
        val monthTxs = active.filter { it.timestampEpochMs >= startOfMonthMs }

        val todayReceived = todayTxs
            .filter { it.type == TransactionType.MONEY_RECEIVED || it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.REFUND }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }

        val todaySent = todayTxs
            .filter { it.type == TransactionType.MONEY_SENT || it.type == TransactionType.CASH_WITHDRAWAL || it.type == TransactionType.TRANSACTION_FEE || it.type == TransactionType.REVERSAL }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount).add(t.fee.max(BigDecimal.ZERO)) }

        val monthlyIncome = monthTxs
            .filter { it.type == TransactionType.MONEY_RECEIVED || it.type == TransactionType.CASH_DEPOSIT || it.type == TransactionType.REFUND || (it.type == TransactionType.MANUAL_ADJUSTMENT && it.isCreditAdjustment) }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount) }

        val monthlyExpenses = monthTxs
            .filter { it.type == TransactionType.MONEY_SENT || it.type == TransactionType.CASH_WITHDRAWAL || it.type == TransactionType.TRANSACTION_FEE || it.type == TransactionType.REVERSAL || (it.type == TransactionType.MANUAL_ADJUSTMENT && !it.isCreditAdjustment) }
            .fold(MoneyUtils.ZERO) { acc, t -> acc.add(t.amount).add(t.fee.max(BigDecimal.ZERO)) }

        val needsReviewCount = transactions.count { it.verificationStatus == VerificationStatus.NEEDS_REVIEW }
        val suspiciousCount = transactions.count { it.verificationStatus == VerificationStatus.SUSPICIOUS }

        return DashboardMetrics(
            totalCalculatedBalance = totalBalance.setScale(2, RoundingMode.HALF_UP),
            todayReceived = todayReceived.setScale(2, RoundingMode.HALF_UP),
            todaySent = todaySent.setScale(2, RoundingMode.HALF_UP),
            todayTransactionCount = todayTxs.size,
            monthlyIncome = monthlyIncome.setScale(2, RoundingMode.HALF_UP),
            monthlyExpenses = monthlyExpenses.setScale(2, RoundingMode.HALF_UP),
            walletSummaries = wallets,
            recentTransactions = transactions.sortedByDescending { it.timestampEpochMs }.take(8),
            needsReviewCount = needsReviewCount,
            suspiciousCount = suspiciousCount
        )
    }
}
