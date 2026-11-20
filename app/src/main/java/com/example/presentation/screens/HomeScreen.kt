package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.WalletSummary
import com.example.presentation.MauzoUiState
import com.example.presentation.components.FormatDisclaimerBanner
import com.example.presentation.components.NetworkBadge
import com.example.presentation.components.TransactionRowCard
import com.example.presentation.localization.LocalAppStrings
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.PureWhite

@Composable
fun HomeScreen(
    uiState: MauzoUiState,
    onTogglePrivacy: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenSmsImport: () -> Unit,
    onOpenManualTxDialog: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onReconcileWallet: (WalletSummary) -> Unit,
    onLockNow: () -> Unit
) {
    val strings = LocalAppStrings.current
    val settings = uiState.settings
    val dash = uiState.dashboard
    val hide = settings.hideBalancesOnHome
    val cur = settings.currencyCode
    val lang = settings.primaryLanguage

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Header row with Business Name, Offline Shield, and Lock Button
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.businessName,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = strings.offlineBadge,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.offlineBadge,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.pinConfigured) {
                        IconButton(
                            onClick = onLockNow,
                            modifier = Modifier.testTag("home_lock_button")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = "Funga")
                        }
                    }
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier.testTag("home_privacy_toggle")
                    ) {
                        Icon(
                            imageVector = if (hide) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (hide) strings.showAmounts else strings.hideAmounts
                        )
                    }
                }
            }
        }

        // Hero Total Calculated Balance Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(DarkNavy, DarkGreen)
                            )
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_fintech_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.22f)
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.totalCalculatedBalance,
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldSoft
                            )
                            Surface(
                                color = PureWhite.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${dash.todayTransactionCount} ${strings.todayTransactions}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = MoneyUtils.formatCurrency(dash.totalCalculatedBalance, cur, hide),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            ),
                            color = PureWhite,
                            modifier = Modifier.testTag("home_total_balance")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onOpenSmsImport,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreen,
                                    contentColor = DarkNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_import_sms_btn")
                            ) {
                                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.importSmsButton, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenManualTxDialog,
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_add_tx_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.addManualTx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // SMS Permission Rationale Card if SMS permissions are not yet granted
        if (!uiState.smsPermissions.allGranted) {
            val perms = uiState.smsPermissions
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = strings.smsPermissionTitle,
                                tint = DarkGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (perms.hasAttemptedRequest) {
                                    strings.smsPermissionDeniedTitle
                                } else {
                                    strings.smsPermissionTitle
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                perms.isPermanentlyDenied -> strings.smsPermissionPermanentDeniedDesc
                                perms.hasAttemptedRequest -> strings.smsPermissionDeniedDesc
                                else -> strings.smsPermissionDesc
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRequestSmsPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("grant_sms_permission_btn")
                            ) {
                                Text(
                                    if (perms.isPermanentlyDenied) {
                                        strings.openAppSettings
                                    } else {
                                        strings.grantSmsPermission
                                    }
                                )
                            }
                            TextButton(
                                onClick = onOpenSmsImport,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(strings.continueManualImport)
                            }
                        }
                    }
                }
            }
        }

        // Alert badge if there are NEEDS_REVIEW or SUSPICIOUS transactions
        if (dash.needsReviewCount > 0 || dash.suspiciousCount > 0) {
            item {
                Surface(
                    color = AmberSoft,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                    onClick = onViewAllTransactions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = AmberWarning
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (lang == AppLanguage.ENGLISH) {
                                "${dash.needsReviewCount} need review, ${dash.suspiciousCount} suspicious — Tap to inspect"
                            } else {
                                "Miamala ${dash.needsReviewCount} inahitaji uhakiki, ${dash.suspiciousCount} inatia shaka — Gusa kukagua"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = DarkNavy
                        )
                    }
                }
            }
        }

        // Today & Monthly Financial Summary Grid (2x2 cards)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KpiSummaryCard(
                        title = strings.todayReceived,
                        amount = MoneyUtils.formatCurrency(dash.todayReceived, cur, hide),
                        accent = EmeraldGreen,
                        isInflow = true,
                        modifier = Modifier.weight(1f)
                    )
                    KpiSummaryCard(
                        title = strings.todaySent,
                        amount = MoneyUtils.formatCurrency(dash.todaySent, cur, hide),
                        accent = CrimsonAlert,
                        isInflow = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KpiSummaryCard(
                        title = strings.monthlyIncome,
                        amount = MoneyUtils.formatCurrency(dash.monthlyIncome, cur, hide),
                        accent = DarkGreen,
                        isInflow = true,
                        modifier = Modifier.weight(1f)
                    )
                    KpiSummaryCard(
                        title = strings.monthlyExpenses,
                        amount = MoneyUtils.formatCurrency(dash.monthlyExpenses, cur, hide),
                        accent = AmberWarning,
                        isInflow = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Provider Wallet Balances (Calculated vs Provider SMS balance)
        item {
            Column {
                Text(
                    text = strings.walletsTitle,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dash.walletSummaries, key = { it.network.code }) { wallet ->
                        WalletBalanceCard(
                            wallet = wallet,
                            currencyCode = cur,
                            hideAmounts = hide,
                            calculatedLabel = strings.calculatedBalanceLabel,
                            providerLabel = strings.providerBalanceLabel,
                            reconcileLabel = strings.reconcileBalance,
                            onReconcile = { onReconcileWallet(wallet) }
                        )
                    }
                }
            }
        }

        // Explicit Format vs Provider Verification Disclaimer
        item {
            FormatDisclaimerBanner(text = strings.formatDisclaimerShort)
        }

        // Recent Transactions Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.recentTransactions,
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = onViewAllTransactions,
                    modifier = Modifier.testTag("home_view_all_tx_btn")
                ) {
                    Text(strings.viewAllTransactions)
                }
            }
        }

        if (dash.recentTransactions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.noTransactionsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(dash.recentTransactions, key = { it.id }) { tx ->
                TransactionRowCard(
                    tx = tx,
                    currencyCode = cur,
                    hideAmounts = hide,
                    language = lang,
                    onClick = { onTransactionClick(tx.id) }
                )
            }
        }
    }
}

@Composable
private fun KpiSummaryCard(
    title: String,
    amount: String,
    accent: Color,
    isInflow: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isInflow) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WalletBalanceCard(
    wallet: WalletSummary,
    currencyCode: String,
    hideAmounts: Boolean,
    calculatedLabel: String,
    providerLabel: String,
    reconcileLabel: String,
    onReconcile: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.width(240.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                NetworkBadge(network = wallet.network)
                Text(
                    text = "${wallet.transactionCount} tx",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = calculatedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = MoneyUtils.formatCurrency(wallet.calculatedBalance, currencyCode, hideAmounts),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = wallet.providerReportedBalance?.let {
                    MoneyUtils.formatCurrency(it, currencyCode, hideAmounts)
                } ?: "Haijapokelewa",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                color = if (wallet.discrepancy != null && wallet.discrepancy!!.compareTo(java.math.BigDecimal.ZERO) != 0) {
                    AmberWarning
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReconcile,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("reconcile_${wallet.network.code}")
            ) {
                Text(reconcileLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
