package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.InfoBlueSoft
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.MixxBlue
import com.example.ui.theme.MpesaRed
import com.example.ui.theme.SlateMuted

@Composable
fun NetworkBadge(network: MobileMoneyNetwork, modifier: Modifier = Modifier) {
    val (bg, fg) = when (network) {
        MobileMoneyNetwork.MPESA -> MpesaRed.copy(alpha = 0.12f) to MpesaRed
        MobileMoneyNetwork.AIRTEL_MONEY -> AirtelRed.copy(alpha = 0.12f) to AirtelRed
        MobileMoneyNetwork.MIXX_BY_YAS -> MixxBlue.copy(alpha = 0.12f) to MixxBlue
        MobileMoneyNetwork.UNKNOWN -> SlateMuted.copy(alpha = 0.12f) to SlateMuted
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = network.shortLabel,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun VerificationStatusPill(
    status: VerificationStatus,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (status) {
        VerificationStatus.FORMAT_VALIDATED -> Triple(EmeraldSoft, DarkGreen, Icons.Filled.VerifiedUser)
        VerificationStatus.NEEDS_REVIEW -> Triple(AmberSoft, AmberWarning, Icons.Filled.WarningAmber)
        VerificationStatus.DUPLICATE -> Triple(InfoBlueSoft, InfoBlue, Icons.Filled.ContentCopy)
        VerificationStatus.SUSPICIOUS -> Triple(CrimsonSoft, CrimsonAlert, Icons.Filled.ErrorOutline)
        VerificationStatus.REVERSED -> Triple(CrimsonSoft, CrimsonAlert, Icons.Filled.Replay)
        VerificationStatus.REFUNDED -> Triple(InfoBlueSoft, InfoBlue, Icons.Filled.SwapHoriz)
    }
    val text = if (language == AppLanguage.ENGLISH) status.labelEn else status.labelSw
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FormatDisclaimerBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = AmberSoft.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = text,
                tint = AmberWarning,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TransactionRowCard(
    tx: TransactionRecord,
    currencyCode: String,
    hideAmounts: Boolean,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInflow = when (tx.type) {
        TransactionType.MONEY_RECEIVED, TransactionType.CASH_DEPOSIT, TransactionType.REFUND -> true
        TransactionType.MANUAL_ADJUSTMENT -> tx.isCreditAdjustment
        else -> false
    }
    val amountColor = when {
        tx.verificationStatus == VerificationStatus.DUPLICATE || tx.verificationStatus == VerificationStatus.SUSPICIOUS ->
            SlateMuted
        isInflow -> EmeraldGreen
        else -> CrimsonAlert
    }
    val sign = if (isInflow) "+" else "-"
    val typeLabel = if (language == AppLanguage.ENGLISH) tx.type.labelEn else tx.type.labelSw

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tx_card_${tx.id}")
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = if (isInflow) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = typeLabel,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.senderOrRecipientName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    NetworkBadge(network = tx.network)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${tx.transactionId} • ${tx.transactionDateText} ${tx.transactionTimeText}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                VerificationStatusPill(status = tx.verificationStatus, language = language)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hideAmounts) "$currencyCode ••••" else "$sign${MoneyUtils.formatCurrency(tx.amount, currencyCode)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
                if (tx.fee > java.math.BigDecimal.ZERO) {
                    Text(
                        text = "Ada: ${MoneyUtils.formatCurrency(tx.fee, currencyCode, hideAmounts)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
