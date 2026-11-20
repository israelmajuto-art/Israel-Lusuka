package com.example.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.SmsArchiveRecord
import com.example.domain.TransactionRecord
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.presentation.components.FormatDisclaimerBanner
import com.example.presentation.components.NetworkBadge
import com.example.presentation.components.VerificationStatusPill
import com.example.presentation.localization.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.JetBrainsMono
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    tx: TransactionRecord?,
    linkedSms: SmsArchiveRecord?,
    currencyCode: String,
    language: AppLanguage,
    onBack: () -> Unit,
    onSaveEdit: (TransactionRecord, String) -> Unit,
    onRecordReversalOrRefund: (String, TransactionType, String, MobileMoneyNetwork, String) -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalAppStrings.current

    var showEditDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf<TransactionType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tx?.transactionId ?: "Muamala") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Rudi")
                    }
                },
                actions = {
                    if (tx != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("detail_edit_btn")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Hariri")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tx == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("Muamala haujapatikana.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                FormatDisclaimerBanner(text = strings.formatDisclaimerLong)
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NetworkBadge(network = tx.network)
                            VerificationStatusPill(status = tx.verificationStatus, language = language)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = MoneyUtils.formatCurrency(tx.amount, currencyCode),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (language == AppLanguage.ENGLISH) tx.type.labelEn else tx.type.labelSw,
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldGreen
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        DetailRow("Kumbukumbu / ID", tx.transactionId)
                        DetailRow("Mhusika", tx.senderOrRecipientName)
                        DetailRow("Namba ya Simu", tx.senderOrRecipientPhone ?: "Haijulikani")
                        DetailRow("Tarehe & Saa", "${tx.transactionDateText} ${tx.transactionTimeText}")
                        DetailRow("Ada", MoneyUtils.formatCurrency(tx.fee, currencyCode))
                        DetailRow(
                            "Salio Kwenye SMS",
                            tx.providerReportedBalance?.let { MoneyUtils.formatCurrency(it, currencyCode) } ?: "Haijatajwa"
                        )
                        DetailRow("Maelezo", tx.description)
                        if (!tx.relatedTransactionId.isNullOrBlank()) {
                            DetailRow("Muamala Husika", tx.relatedTransactionId)
                        }
                        if (!tx.formatVersion.isNullOrBlank()) {
                            DetailRow("Toleo la Umbizo", tx.formatVersion)
                        }
                        if (tx.verificationNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Maelezo ya Uhakiki:\n${tx.verificationNotes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Reversal & Refund link actions
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showLinkDialog = TransactionType.REVERSAL },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("record_reversal_btn")
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batilisha", color = CrimsonAlert)
                    }
                    OutlinedButton(
                        onClick = { showLinkDialog = TransactionType.REFUND },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("record_refund_btn")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = DarkGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rejesho", color = DarkGreen)
                    }
                }
            }

            // Original SMS Archive Card
            if (linkedSms != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Icon(Icons.Filled.Sms, contentDescription = null, tint = DarkGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SMS Asili (${linkedSms.senderIdentifier})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = linkedSms.originalContent,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && tx != null) {
        EditTransactionDialog(
            tx = tx,
            onDismiss = { showEditDialog = false },
            onSave = { updated, reason ->
                onSaveEdit(updated, reason)
                showEditDialog = false
            }
        )
    }

    if (showLinkDialog != null && tx != null) {
        val targetType = showLinkDialog!!
        LinkReversalRefundDialog(
            originalTx = tx,
            type = targetType,
            onDismiss = { showLinkDialog = null },
            onConfirm = { amountText, reason ->
                onRecordReversalOrRefund(tx.transactionId, targetType, amountText, tx.network, reason)
                showLinkDialog = null
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun EditTransactionDialog(
    tx: TransactionRecord,
    onDismiss: () -> Unit,
    onSave: (TransactionRecord, String) -> Unit
) {
    var name by remember { mutableStateOf(tx.senderOrRecipientName) }
    var phone by remember { mutableStateOf(tx.senderOrRecipientPhone ?: "") }
    var amountText by remember { mutableStateOf(tx.amount.toPlainString()) }
    var feeText by remember { mutableStateOf(tx.fee.toPlainString()) }
    var description by remember { mutableStateOf(tx.description) }
    var selectedStatus by remember { mutableStateOf(tx.verificationStatus) }
    var selectedNetwork by remember { mutableStateOf(tx.network) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sahihisha Taarifa za Muamala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jina la Mhusika") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Namba ya Simu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Kiasi (TZS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Ada (TZS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Maelezo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MobileMoneyNetwork.supportedNetworks.forEach { net ->
                        FilterChip(
                            selected = selectedNetwork == net,
                            onClick = { selectedNetwork = net },
                            label = { Text(net.shortLabel) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        VerificationStatus.FORMAT_VALIDATED,
                        VerificationStatus.NEEDS_REVIEW,
                        VerificationStatus.SUSPICIOUS
                    ).forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.name.take(8)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Sababu ya Marekebisho (Audit Log)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = MoneyUtils.parseAmountOrNull(amountText) ?: tx.amount
                    val parsedFee = MoneyUtils.parseAmountOrNull(feeText) ?: BigDecimal.ZERO
                    onSave(
                        tx.copy(
                            network = selectedNetwork,
                            senderOrRecipientName = name.trim().ifBlank { "Haijulikani" },
                            senderOrRecipientPhone = phone.trim().takeIf { it.isNotEmpty() },
                            amount = parsedAmount,
                            fee = parsedFee,
                            description = description.trim(),
                            verificationStatus = selectedStatus
                        ),
                        reason.ifBlank { "Sahihisho la mmiliki" }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Hifadhi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
private fun LinkReversalRefundDialog(
    originalTx: TransactionRecord,
    type: TransactionType,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(originalTx.amount.toPlainString()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rekodi ${type.labelSw}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Muamala asili: ${originalTx.transactionId}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Kiasi (TZS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Sababu") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amountText, reason) }) {
                Text("Thibitisha")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}
