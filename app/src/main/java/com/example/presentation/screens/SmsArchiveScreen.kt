package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.MoneyUtils
import com.example.domain.SmsArchiveRecord
import com.example.domain.SmsProcessingStatus
import com.example.presentation.MauzoUiState
import com.example.presentation.components.NetworkBadge
import com.example.presentation.localization.LocalAppStrings
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsArchiveScreen(
    uiState: MauzoUiState,
    onSearchQueryChange: (String) -> Unit,
    onOpenManualSmsImport: () -> Unit,
    onScanInbox: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLinkedTransaction: (Long) -> Unit,
    onRequestScanPermission: () -> Unit = onRequestPermissions
) {
    val strings = LocalAppStrings.current
    val lang = uiState.settings.primaryLanguage
    val isEnglish = lang == AppLanguage.ENGLISH
    val perms = uiState.smsPermissions
    val autoDetectActive = perms.receiveSmsGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = strings.navSms,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Live Auto-Detection Status Card
        Surface(
            color = if (autoDetectActive) EmeraldSoft else AmberSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (autoDetectActive) EmeraldGreen.copy(alpha = 0.45f) else AmberWarning.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sms_auto_detect_status_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (autoDetectActive) Icons.Filled.CheckCircle else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = if (autoDetectActive) DarkGreen else AmberWarning
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when {
                                perms.allGranted -> {
                                    if (isEnglish) "Auto-Detect Active (Offline)" else "Utambuzi wa SMS Umewashwa"
                                }
                                autoDetectActive && !perms.readSmsGranted -> {
                                    if (isEnglish) "Live SMS Active (Inbox Scan Off)" else "SMS za Papo Hapo Zimewashwa"
                                }
                                perms.isPermanentlyDenied -> {
                                    if (isEnglish) "SMS Permission Blocked" else "Ruhusa ya SMS Imezuiwa"
                                }
                                perms.hasAttemptedRequest -> {
                                    if (isEnglish) "SMS Permission Denied" else "Ruhusa ya SMS Imekataliwa"
                                }
                                else -> {
                                    if (isEnglish) "Manual Mode Only" else "Hali ya Mkono Pekee"
                                }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (autoDetectActive) DarkGreen else AmberWarning
                        )
                        val simText = when (uiState.settings.simSlotFilter) {
                            1 -> "SIM 1"
                            2 -> "SIM 2"
                            else -> if (isEnglish) "All SIMs" else "SIM Zote"
                        }
                        val notifyText = if (uiState.settings.smsNotificationsEnabled && perms.postNotificationsGranted) {
                            if (isEnglish) "Alerts ON" else "Arifa ON"
                        } else {
                            if (isEnglish) "Alerts OFF" else "Arifa OFF"
                        }
                        Text(
                            text = when {
                                perms.allGranted -> "$simText • $notifyText"
                                autoDetectActive && !perms.readSmsGranted -> {
                                    if (isEnglish) "$simText • Grant READ_SMS to scan past inbox SMS" else "$simText • Ruhusu READ_SMS kuchanganua kikasha"
                                }
                                perms.isPermanentlyDenied -> {
                                    if (isEnglish) "Open App Settings to enable SMS, or use Manual Import" else "Fungua Mipangilio kuwasha SMS, au tumia Ingiza SMS"
                                }
                                else -> {
                                    if (isEnglish) "Grant RECEIVE_SMS & READ_SMS for automatic capture" else "Ruhusu RECEIVE_SMS na READ_SMS kupokea miamala kiotomatiki"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!perms.allGranted) {
                    TextButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("sms_status_grant_btn")
                    ) {
                        Text(if (perms.isPermanentlyDenied) strings.openAppSettings else strings.grantSmsPermission)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onOpenManualSmsImport,
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("sms_manual_import_btn")
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.importSmsButton, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    if (perms.readSmsGranted) onScanInbox() else onRequestScanPermission()
                },
                enabled = !uiState.isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("sms_scan_inbox_btn")
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.scanInboxButton, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (uiState.isBusy) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms_scan_progress")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = uiState.smsSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Tafuta SMS, mtumaji au ID...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.smsSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Futa")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sms_search_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.filteredSmsArchive.isEmpty()) {
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
                            Icon(Icons.Filled.Inbox, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.noSmsYet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.filteredSmsArchive, key = { it.id }) { sms ->
                    SmsArchiveItemCard(
                        sms = sms,
                        currencyCode = uiState.settings.currencyCode,
                        hideAmounts = uiState.settings.hideBalancesOnHome,
                        language = lang,
                        onOpenTx = { txId -> onOpenLinkedTransaction(txId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsArchiveItemCard(
    sms: SmsArchiveRecord,
    currencyCode: String,
    hideAmounts: Boolean,
    language: AppLanguage,
    onOpenTx: (Long) -> Unit
) {
    val (bg, fg) = when (sms.processingStatus) {
        SmsProcessingStatus.PARSED -> EmeraldSoft to DarkGreen
        SmsProcessingStatus.DUPLICATE -> InfoBlueSoft to InfoBlue
        SmsProcessingStatus.NEEDS_REVIEW -> AmberSoft to AmberWarning
        else -> CrimsonSoft to CrimsonAlert
    }
    val statusText = if (language == AppLanguage.ENGLISH) {
        sms.processingStatus.labelEn
    } else {
        sms.processingStatus.labelSw
    }
    val timeText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sms.receivedAtEpochMs))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (sms.relatedTransactionLocalId != null) {
                    mod.clickable { onOpenTx(sms.relatedTransactionLocalId) }
                } else {
                    mod
                }
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkBadge(network = sms.network)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sms.senderIdentifier,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusText,
                        color = fg,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sms.originalContent,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ID: ${sms.extractedTransactionId ?: "Haijulikani"} • $timeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sms.extractedAmount?.let { amt ->
                    Text(
                        text = MoneyUtils.formatCurrency(amt, currencyCode, hideAmounts),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                }
            }
            if (!sms.parseErrorReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sms.parseErrorReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
