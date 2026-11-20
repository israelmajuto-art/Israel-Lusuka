package com.example.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.AuditLogRecord
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkFormatConfig
import com.example.domain.TransactionType
import com.example.domain.WalletSummary
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.EmeraldGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManualSmsImportDialog(
    onDismiss: () -> Unit,
    onImport: (sender: String, body: String) -> Unit
) {
    var sender by remember { mutableStateOf("MPESA") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ingiza SMS ya Muamala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Chagua mtumaji au bandika ujumbe kamili wa M-Pesa, Airtel Money au Mixx by Yas.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MPESA", "AIRTELMONEY", "MIXX BY YAS").forEach { s ->
                        FilterChip(
                            selected = sender == s,
                            onClick = { sender = s },
                            label = { Text(s.take(10)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Kitambulisho cha Mtumaji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Nakili & Bandika SMS hapa") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_sms_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(sender, body) },
                enabled = body.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("manual_sms_submit_btn")
            ) {
                Text("Chakata SMS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
fun AddManualTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (
        txId: String,
        network: MobileMoneyNetwork,
        type: TransactionType,
        amountText: String,
        feeText: String,
        partyName: String,
        partyPhone: String,
        description: String,
        isCreditAdjustment: Boolean,
        reason: String
    ) -> Unit
) {
    var txId by remember { mutableStateOf("") }
    var network by remember { mutableStateOf(MobileMoneyNetwork.MPESA) }
    var type by remember { mutableStateOf(TransactionType.MONEY_RECEIVED) }
    var amountText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("0") }
    var partyName by remember { mutableStateOf("") }
    var partyPhone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isCreditAdjustment by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ongeza Muamala wa Mkono") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MobileMoneyNetwork.supportedNetworks) { net ->
                        FilterChip(
                            selected = network == net,
                            onClick = { network = net },
                            label = { Text(net.shortLabel) }
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TransactionType.entries) { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.labelSw) }
                        )
                    }
                }
                if (type == TransactionType.MANUAL_ADJUSTMENT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCreditAdjustment,
                            onCheckedChange = { isCreditAdjustment = it }
                        )
                        Text("Ongeza salio (+) badala ya kupunguza (-)")
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Kiasi (TZS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_tx_amount_input")
                )
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Ada (TZS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = txId,
                    onValueChange = { txId = it },
                    label = { Text("Kumbukumbu / ID (hiari)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = partyName,
                    onValueChange = { partyName = it },
                    label = { Text("Jina la Mteja / Wakala") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_tx_party_input")
                )
                OutlinedTextField(
                    value = partyPhone,
                    onValueChange = { partyPhone = it },
                    label = { Text("Namba ya Simu") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Maelezo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Sababu (Audit Log)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        txId,
                        network,
                        type,
                        amountText,
                        feeText,
                        partyName,
                        partyPhone,
                        description,
                        isCreditAdjustment,
                        reason
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                modifier = Modifier.testTag("manual_tx_save_btn")
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
fun ReconcileWalletDialog(
    wallet: WalletSummary,
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (providerBalanceText: String, adjustCalculated: Boolean, reason: String) -> Unit
) {
    var providerText by remember {
        mutableStateOf(wallet.providerReportedBalance?.toPlainString() ?: "")
    }
    var adjustCalculated by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sawazisha ${wallet.network.displayNameSw}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Salio la Hesabu: ${MoneyUtils.formatCurrency(wallet.calculatedBalance, currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = providerText,
                    onValueChange = { providerText = it },
                    label = { Text("Salio Halisi Kwenye Simu/Mtandao") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = adjustCalculated,
                        onCheckedChange = { adjustCalculated = it }
                    )
                    Text(
                        text = "Rekebisha salio la hesabu lilingane na la mtandao (itaingizwa kwenye Audit Log)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Maelezo / Sababu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(providerText, adjustCalculated, reason) },
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Text("Sawazisha")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
fun OpeningBalanceDialog(
    network: MobileMoneyNetwork,
    currentAmountText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(currentAmountText) }
    var note by remember { mutableStateOf("Salio la awali") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salio la Awali — ${network.displayNameSw}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Kiasi (TZS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Maelezo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(amountText, note) }) { Text("Hifadhi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val mismatch = pin2.isNotEmpty() && pin1 != pin2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weka PIN ya Usalama") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin1 = it },
                    label = { Text("PIN Mpya (tarakimu 4-6)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin2 = it },
                    label = { Text("Rudia PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = mismatch,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin1) },
                enabled = pin1.length in 4..6 && pin1 == pin2
            ) {
                Text("Hifadhi PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
fun BackupPassphraseDialog(
    isRestore: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRestore) "Rejesha Chelezo" else "Simba Faili la Chelezo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Weka neno la siri (angalau herufi 4) la kufunga au kufungua faili la chelezo (AES-256).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Neno la Siri la Chelezo") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = passphrase.length >= 4
            ) {
                Text(if (isRestore) "Chagua Faili & Rejesha" else "Chagua Mahali & Hifadhi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ghairi") }
        }
    )
}

@Composable
fun AuditLogDialog(
    logs: List<AuditLogRecord>,
    onDismiss: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kumbukumbu ya Ukaguzi (Audit Log)") },
        text = {
            if (logs.isEmpty()) {
                Text("Bado hakuna mabadiliko yaliyorekodiwa.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        Column {
                            Text(
                                text = "${log.action} • ${df.format(Date(log.timestampEpochMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkGreen
                            )
                            Text(log.summary, style = MaterialTheme.typography.bodySmall)
                            if (!log.reason.isNullOrBlank()) {
                                Text(
                                    text = "Sababu: ${log.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Funga") }
        }
    )
}

@Composable
fun EditNetworkSendersDialog(
    networkFormat: NetworkFormatConfig,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val isEnglish = language == AppLanguage.ENGLISH
    var csv by remember(networkFormat.senderIdentifiersCsv) {
        mutableStateOf(networkFormat.senderIdentifiersCsv)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEnglish) {
                    "Sender IDs — ${networkFormat.displayName}"
                } else {
                    "Watumaji wa SMS — ${networkFormat.displayName}"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEnglish) {
                        "Enter comma-separated SMS sender IDs or shortcodes recognized for ${networkFormat.network.shortLabel}."
                    } else {
                        "Weka vitambulisho au namba za watumaji wa SMS (tenganisha kwa koma) kwa ajili ya ${networkFormat.network.shortLabel}."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = csv,
                    onValueChange = { csv = it },
                    label = {
                        Text(if (isEnglish) "Sender IDs (comma-separated)" else "Watumaji (tenganisha kwa koma)")
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_senders_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(csv) },
                enabled = csv.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                modifier = Modifier.testTag("edit_senders_save_btn")
            ) {
                Text(if (isEnglish) "Save" else "Hifadhi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "Ghairi")
            }
        }
    )
}
