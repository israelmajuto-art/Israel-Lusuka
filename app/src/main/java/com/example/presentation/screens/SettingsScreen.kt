package com.example.presentation.screens

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.MoneyUtils
import com.example.domain.NetworkFormatConfig
import com.example.presentation.CloudUiState
import com.example.presentation.MauzoUiState
import com.example.presentation.localization.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.JetBrainsMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: MauzoUiState,
    biometricAvailable: Boolean,
    onUpdateBusinessName: (String) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateCurrency: (String) -> Unit,
    onToggleNetwork: (MobileMoneyNetwork, Boolean) -> Unit,
    onOpenEditSendersDialog: (NetworkFormatConfig) -> Unit,
    onToggleSmsNotifications: (Boolean) -> Unit,
    onSetSimSlotFilter: (Int) -> Unit,
    onOpenOpeningBalanceDialog: (MobileMoneyNetwork) -> Unit,
    onRequestSmsPermissions: () -> Unit,
    onOpenSetPinDialog: () -> Unit,
    onDisablePin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onRunLocalBackupNow: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onOpenExportReports: () -> Unit,
    onOpenAuditLogDialog: () -> Unit,
    onDeleteArchivedSms: () -> Unit,
    onDeleteAllData: () -> Unit,
    onSyncToCloud: () -> Unit = {},
    onRestoreFromCloud: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val settings = uiState.settings
    var bizNameInput by remember(settings.businessName) { mutableStateOf(settings.businessName) }
    var currencyInput by remember(settings.currencyCode) { mutableStateOf(settings.currencyCode) }
    var confirmDeleteSms by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = strings.navSettings,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // 1. Business Name & Currency & Language
        item {
            SettingsCard(title = "Taarifa za Biashara & Lugha", icon = Icons.Filled.Store) {
                OutlinedTextField(
                    value = bizNameInput,
                    onValueChange = { bizNameInput = it },
                    label = { Text("Jina la Biashara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currencyInput,
                        onValueChange = { currencyInput = it },
                        label = { Text("Sarafu") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onUpdateBusinessName(bizNameInput)
                            onUpdateCurrency(currencyInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("settings_save_business_btn")
                    ) {
                        Text(strings.save)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = DarkGreen)
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.primaryLanguage == lang,
                            onClick = { onUpdateLanguage(lang) },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("lang_chip_${lang.code}")
                        )
                    }
                }
            }
        }

        // 2. Opening Balances
        item {
            SettingsCard(title = "Salio la Awali (Opening Balances)", icon = Icons.Filled.AccountBalance) {
                MobileMoneyNetwork.supportedNetworks.forEach { net ->
                    val existing = uiState.openingBalances.firstOrNull { it.network == net }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(net.displayNameSw, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = existing?.let {
                                    MoneyUtils.formatCurrency(it.amount, settings.currencyCode)
                                } ?: "${settings.currencyCode} 0",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = EmeraldGreen
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenOpeningBalanceDialog(net) },
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("opening_balance_${net.code}")
                        ) {
                            Text("Weka")
                        }
                    }
                }
            }
        }

        // 3. Supported Mobile Money Networks, Custom Sender IDs, SIM Filter & SMS Permission
        item {
            val isEnglish = settings.primaryLanguage == AppLanguage.ENGLISH
            SettingsCard(title = "Mitandao & Ruhusa za SMS", icon = Icons.Filled.Sms) {
                MobileMoneyNetwork.supportedNetworks.forEach { net ->
                    val enabled = settings.enabledNetworks.contains(net)
                    val fmt = uiState.networkFormats.firstOrNull { it.network == net }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(net.displayNameSw, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = enabled,
                                onCheckedChange = { onToggleNetwork(net, it) },
                                modifier = Modifier.testTag("toggle_network_${net.code}")
                            )
                        }
                        if (fmt != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = fmt.senderIdentifiersCsv,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onOpenEditSendersDialog(fmt) },
                                    modifier = Modifier.testTag("edit_senders_${net.code}")
                                ) {
                                    Text(if (isEnglish) "Senders" else "Watumaji")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnglish) "Transaction Notifications" else "Arifa za SMS za Miamala",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (isEnglish) {
                                "Show local alert when an M-Pesa/Airtel/Mixx SMS arrives"
                            } else {
                                "Onyesha arifa ya ndani SMS ya muamala inapoingia"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.smsNotificationsEnabled,
                        onCheckedChange = onToggleSmsNotifications,
                        modifier = Modifier.testTag("toggle_sms_notifications")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnglish) "Dual-SIM Filter (Auto-Detect)" else "Kichujio cha SIM (Utambuzi Kiotomatiki)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0 to (if (isEnglish) "All SIMs" else "SIM Zote"),
                        1 to "SIM 1",
                        2 to "SIM 2"
                    ).forEach { (slot, label) ->
                        FilterChip(
                            selected = settings.simSlotFilter == slot,
                            onClick = { onSetSimSlotFilter(slot) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("sim_filter_$slot")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val perms = uiState.smsPermissions
                val grantedLabel = if (isEnglish) "Granted" else "Imewashwa"
                val deniedLabel = if (isEnglish) "Not granted" else "Haijatolewa"
                Text(
                    text = "RECEIVE_SMS: ${if (perms.receiveSmsGranted) grantedLabel else deniedLabel} • " +
                        "READ_SMS: ${if (perms.readSmsGranted) grantedLabel else deniedLabel} • " +
                        "POST_NOTIFICATIONS: ${if (perms.postNotificationsGranted) grantedLabel else deniedLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestSmsPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_sms_perm_btn")
                ) {
                    Text(if (perms.isPermanentlyDenied) strings.openAppSettings else strings.grantSmsPermission)
                }
            }
        }

        // 4. Security (PIN & Biometric & Keystore SQLCipher badge)
        item {
            SettingsCard(title = "Usalama & Faragha (Android Keystore)", icon = Icons.Filled.Security) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ulinzi wa PIN", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (uiState.pinConfigured) "PIN imewashwa (PBKDF2)" else "Haijawekwa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.pinConfigured) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenSetPinDialog) { Text("Badili") }
                            TextButton(onClick = onDisablePin) { Text("Zima", color = CrimsonAlert) }
                        }
                    } else {
                        Button(
                            onClick = onOpenSetPinDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                            modifier = Modifier.testTag("settings_enable_pin_btn")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Weka PIN")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = DarkGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Alama ya Kidole / Uso", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (biometricAvailable) "Inapatikana" else "Haipatikani kwenye kifaa hiki",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.biometricEnabled,
                        enabled = biometricAvailable && uiState.pinConfigured,
                        onCheckedChange = onToggleBiometric
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hifadhidata imesimbwa kwa SQLCipher AES-256 na Android Keystore. Hakuna ruhusa ya INTERNET kwenye App.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldGreen
                )
            }
        }

        // 5. Backup, Restore, Audit Log & Export
        item {
            SettingsCard(title = "Chelezo (Backup), Rejesho & Ukaguzi", icon = Icons.Filled.Backup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chelezo Kiotomatiki (WorkManager)", style = MaterialTheme.typography.bodyLarge)
                        val lastStr = settings.lastBackupEpochMs?.let {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(it))
                        } ?: "Bado"
                        Text(
                            text = "Ya mwisho: $lastStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = onToggleAutoBackup
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onExportEncryptedBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_export_backup_btn")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hamisha")
                    }
                    OutlinedButton(
                        onClick = onRestoreEncryptedBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_restore_backup_btn")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rejesha")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onRunLocalBackupNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Chelezo Sasa")
                    }
                    OutlinedButton(
                        onClick = onOpenExportReports,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Ripoti")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenAuditLogDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_audit_log_btn")
                ) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${strings.auditLogTitle} (${uiState.auditLogs.size})")
                }
            }
        }

        // 6. Data Deletion Options
        item {
            SettingsCard(title = "Futa Kumbukumbu", icon = Icons.Filled.DeleteSweep) {
                OutlinedButton(
                    onClick = { confirmDeleteSms = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_delete_sms_btn")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = CrimsonAlert)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Futa Kumbukumbu za SMS Pekee", color = CrimsonAlert)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { confirmDeleteAll = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_delete_all_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Futa Data Zote za Kifaa")
                }
            }
        }

        // 7. Firebase Cloud Firestore Sync & Account
        if (uiState.currentUserId.isNotBlank()) {
            item {
                val isEnglish = settings.primaryLanguage == AppLanguage.ENGLISH
                SettingsCard(title = strings.cloudSyncTitle, icon = Icons.Filled.CloudSync) {
                    Text(
                        text = uiState.currentUserEmail ?: uiState.currentUserId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val cloudStatusText = when (val c = uiState.cloudTransactionsState) {
                        is CloudUiState.Loading -> if (isEnglish) "Connecting to Cloud Firestore..." else "Inaunganisha na Cloud Firestore..."
                        is CloudUiState.Success -> if (isEnglish) "${c.data.size} transactions backed up in Cloud Firestore" else "Miamala ${c.data.size} imehifadhiwa kwenye Cloud Firestore"
                        is CloudUiState.Error -> c.message
                    }
                    Text(
                        text = cloudStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.cloudTransactionsState is CloudUiState.Error) CrimsonAlert else EmeraldGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onSyncToCloud,
                            enabled = !uiState.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_cloud_sync_btn")
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.syncNow)
                        }
                        OutlinedButton(
                            onClick = onRestoreFromCloud,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_cloud_restore_btn")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.restoreFromCloud)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_sign_out_btn")
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.signOutLabel, color = CrimsonAlert)
                    }
                }
            }
        }

        // 8. Application Information
        item {
            SettingsCard(title = "Taarifa za Programu", icon = Icons.Filled.Info) {
                Text("MAUZO APP v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Programu ya ufuatiliaji wa miamala ya fedha za simu Tanzania (Vodacom M-Pesa, Airtel Money, Mixx by Yas). SMS zinachakatwa ndani ya simu pamoja na chelezo salama ya Cloud Firestore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDeleteSms) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSms = false },
            title = { Text("Futa Kumbukumbu za SMS?") },
            text = { Text("Hatua hii itafuta nakala za SMS zilizohifadhiwa. Miamala iliyo kwenye daftari haitafutwa.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteArchivedSms()
                        confirmDeleteSms = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) { Text("Futa SMS") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSms = false }) { Text(strings.cancel) }
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Futa Data Zote?") },
            text = { Text("Miamala yote, SMS, salio la awali na kumbukumbu za ukaguzi zitafutwa kabisa kwenye simu hii.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllData()
                        confirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) { Text("Futa Yote") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = DarkGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
