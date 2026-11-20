package com.example.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.MobileMoneyNetwork
import com.example.domain.TransactionType
import com.example.domain.VerificationStatus
import com.example.presentation.DateFilterOption
import com.example.presentation.MauzoUiState
import com.example.presentation.TransactionFilters
import com.example.presentation.components.FormatDisclaimerBanner
import com.example.presentation.components.TransactionRowCard
import com.example.presentation.localization.LocalAppStrings
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PureWhite

@Composable
fun TransactionsScreen(
    uiState: MauzoUiState,
    onUpdateFilters: ((TransactionFilters) -> TransactionFilters) -> Unit,
    onOpenAddManualDialog: () -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    val strings = LocalAppStrings.current
    val filters = uiState.filters
    val lang = uiState.settings.primaryLanguage
    val cur = uiState.settings.currencyCode
    val hide = uiState.settings.hideBalancesOnHome

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddManualDialog,
                containerColor = EmeraldGreen,
                contentColor = PureWhite,
                modifier = Modifier.testTag("fab_add_manual_tx")
            ) {
                Icon(Icons.Filled.Add, contentDescription = strings.addManualTx)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.navTransactions,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = filters.query,
                onValueChange = { q -> onUpdateFilters { it.copy(query = q) } },
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { onUpdateFilters { it.copy(query = "") } }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Futa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_search_input")
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Network filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filters.network == null,
                        onClick = { onUpdateFilters { it.copy(network = null) } },
                        label = { Text(strings.allLabel) }
                    )
                }
                items(MobileMoneyNetwork.supportedNetworks) { net ->
                    FilterChip(
                        selected = filters.network == net,
                        onClick = {
                            onUpdateFilters {
                                it.copy(network = if (it.network == net) null else net)
                            }
                        },
                        label = { Text(net.shortLabel) }
                    )
                }
                items(DateFilterOption.entries) { opt ->
                    if (opt != DateFilterOption.ALL) {
                        FilterChip(
                            selected = filters.dateFilter == opt,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(dateFilter = if (it.dateFilter == opt) DateFilterOption.ALL else opt)
                                }
                            },
                            label = { Text(if (lang == AppLanguage.ENGLISH) opt.labelEn else opt.labelSw) }
                        )
                    }
                }
            }

            // Verification status & Transaction type filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VerificationStatus.entries) { status ->
                    FilterChip(
                        selected = filters.status == status,
                        onClick = {
                            onUpdateFilters {
                                it.copy(status = if (it.status == status) null else status)
                            }
                        },
                        label = { Text(if (lang == AppLanguage.ENGLISH) status.labelEn else status.labelSw) }
                    )
                }
                items(TransactionType.entries) { type ->
                    FilterChip(
                        selected = filters.type == type,
                        onClick = {
                            onUpdateFilters {
                                it.copy(type = if (it.type == type) null else type)
                            }
                        },
                        label = { Text(if (lang == AppLanguage.ENGLISH) type.labelEn else type.labelSw) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FormatDisclaimerBanner(text = strings.formatDisclaimerShort)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.filteredTransactions.isEmpty()) {
                    item {
                        Text(
                            text = strings.noTransactionsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(uiState.filteredTransactions, key = { it.id }) { tx ->
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
    }
}
