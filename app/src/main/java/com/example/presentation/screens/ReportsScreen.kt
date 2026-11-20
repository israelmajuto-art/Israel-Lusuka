package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage
import com.example.domain.ChartBucket
import com.example.domain.MoneyUtils
import com.example.domain.ReportPeriod
import com.example.presentation.ExportFormat
import com.example.presentation.MauzoUiState
import com.example.presentation.components.NetworkBadge
import com.example.presentation.localization.LocalAppStrings
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkGreen
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.JetBrainsMono
import java.math.BigDecimal

@Composable
fun ReportsScreen(
    uiState: MauzoUiState,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onExport: (ExportFormat) -> Unit
) {
    val strings = LocalAppStrings.current
    val report = uiState.currentReport
    val lang = uiState.settings.primaryLanguage
    val cur = uiState.settings.currencyCode
    val hide = uiState.settings.hideBalancesOnHome

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = strings.navReports,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Period selector
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = uiState.selectedReportPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = {
                            Text(
                                text = if (lang == AppLanguage.ENGLISH) period.labelEn else period.labelSw,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("report_period_${period.name}")
                    )
                }
            }
        }

        if (report != null) {
            // Summary Financial Metrics Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = if (lang == AppLanguage.ENGLISH) {
                                "${report.period.labelEn} Summary (${report.transactionCount} tx)"
                            } else {
                                "Muhtasari: ${report.period.labelSw} (Miamala ${report.transactionCount})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ReportStatCol(
                                label = "Zimepokelewa",
                                value = MoneyUtils.formatCurrency(report.totalReceived, cur, hide),
                                color = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                            ReportStatCol(
                                label = "Zimetumwa",
                                value = MoneyUtils.formatCurrency(report.totalSent, cur, hide),
                                color = Color(0xFFFCA5A5),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ReportStatCol(
                                label = "Ada / Makato",
                                value = MoneyUtils.formatCurrency(report.totalFees, cur, hide),
                                color = Color(0xFFFCD34D),
                                modifier = Modifier.weight(1f)
                            )
                            ReportStatCol(
                                label = "Marejesho / Batili",
                                value = "${MoneyUtils.formatCompactCurrency(report.totalRefunds, cur, hide)} / ${MoneyUtils.formatCompactCurrency(report.totalReversals, cur, hide)}",
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Cashflow Bar Chart
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Mtiririko wa Fedha",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ChartLegendDot(color = EmeraldGreen, label = "Ingia")
                                ChartLegendDot(color = CrimsonAlert, label = "Toka")
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        CashflowGroupedBarChart(
                            buckets = report.chartBuckets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }
            }

            // Network Comparison Cards
            item {
                Text(
                    text = "Linganisho la Mitandao",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(report.networkComparisons, key = { it.network.code }) { row ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NetworkBadge(network = row.network)
                            Text(
                                text = "${row.transactionCount} miamala",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Mapato", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MoneyUtils.formatCompactCurrency(row.received, cur, hide),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    color = EmeraldGreen
                                )
                            }
                            Column {
                                Text("Matumizi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MoneyUtils.formatCompactCurrency(row.sent, cur, hide),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    color = CrimsonAlert
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Salio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MoneyUtils.formatCompactCurrency(row.calculatedBalance, cur, hide),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Local Export Buttons (CSV, Excel, PDF)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Hamisha Ripoti (Nje ya Mtandao)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onExport(ExportFormat.CSV) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_csv_btn")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onExport(ExportFormat.EXCEL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_excel_btn")
                            ) {
                                Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Excel", maxLines = 1)
                            }
                            Button(
                                onClick = { onExport(ExportFormat.PDF) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_pdf_btn")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PDF", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatCol(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CashflowGroupedBarChart(
    buckets: List<ChartBucket>,
    modifier: Modifier = Modifier
) {
    val maxVal = buckets
        .flatMap { listOf(it.inflow, it.outflow) }
        .maxOfOrNull { it }
        ?.takeIf { it > BigDecimal.ZERO }
        ?: BigDecimal.ONE

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (buckets.isEmpty()) return@Canvas
            val groupWidth = size.width / buckets.size.toFloat()
            val barWidth = (groupWidth * 0.28f).coerceAtLeast(6f).coerceAtMost(28f)
            val gap = 4f
            val chartHeight = size.height - 8f

            buckets.forEachIndexed { idx, bucket ->
                val centerX = idx * groupWidth + groupWidth / 2f
                val inflowRatio = (bucket.inflow.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val outflowRatio = (bucket.outflow.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)

                val inH = (chartHeight * inflowRatio).coerceAtLeast(if (bucket.inflow > BigDecimal.ZERO) 6f else 2f)
                val outH = (chartHeight * outflowRatio).coerceAtLeast(if (bucket.outflow > BigDecimal.ZERO) 6f else 2f)

                drawRoundRect(
                    color = EmeraldGreen,
                    topLeft = Offset(centerX - barWidth - gap / 2f, size.height - inH),
                    size = Size(barWidth, inH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = CrimsonAlert,
                    topLeft = Offset(centerX + gap / 2f, size.height - outH),
                    size = Size(barWidth, outH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            buckets.forEach { bucket ->
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
