package com.example.reporting

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.domain.FinancialReport
import com.example.domain.MoneyUtils
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportExporter(private val context: Context) {

    fun exportCsv(uri: Uri, report: FinancialReport, businessName: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writeCsvToStream(out, report, businessName)
        } ?: error("Imeshindwa kufungua faili la CSV")
    }

    fun writeCsvToStream(out: OutputStream, report: FinancialReport, businessName: String) {
        val sb = StringBuilder()
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        sb.append('\uFEFF') // UTF-8 BOM for Excel
        sb.appendLine("MAUZO APP - RIPOTI YA FEDHA,${csvEsc(businessName)}")
        sb.appendLine("Kipindi,${report.period.labelSw},${df.format(Date(report.startEpochMs))} - ${df.format(Date(report.endEpochMs))}")
        sb.appendLine("Jumla Fedha Zilizopokelewa,${report.totalReceived.toPlainString()}")
        sb.appendLine("Jumla Fedha Zilizotumwa,${report.totalSent.toPlainString()}")
        sb.appendLine("Jumla Ada,${report.totalFees.toPlainString()}")
        sb.appendLine("Jumla Marejesho,${report.totalRefunds.toPlainString()}")
        sb.appendLine("Jumla Ubatilishaji,${report.totalReversals.toPlainString()}")
        sb.appendLine("Idadi ya Miamala,${report.transactionCount}")
        sb.appendLine()
        sb.appendLine("Transaction ID,Mtandao,Aina,Kiasi (TZS),Ada (TZS),Mhusika,Simu,Tarehe,Saa,Hali ya Uhakiki,Maelezo")
        report.transactions.forEach { tx ->
            sb.appendLine(
                listOf(
                    csvEsc(tx.transactionId),
                    csvEsc(tx.network.displayNameSw),
                    csvEsc(tx.type.labelSw),
                    tx.amount.toPlainString(),
                    tx.fee.toPlainString(),
                    csvEsc(tx.senderOrRecipientName),
                    csvEsc(tx.senderOrRecipientPhone ?: ""),
                    csvEsc(tx.transactionDateText),
                    csvEsc(tx.transactionTimeText),
                    csvEsc(tx.verificationStatus.labelSw),
                    csvEsc(tx.description)
                ).joinToString(",")
            )
        }
        out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    /**
     * Exports an Excel-compatible SpreadsheetML (.xls) workbook with Summary and Transactions worksheets.
     */
    fun exportExcelXml(uri: Uri, report: FinancialReport, businessName: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writeExcelXmlToStream(out, report, businessName)
        } ?: error("Imeshindwa kufungua faili la Excel")
    }

    fun writeExcelXmlToStream(out: OutputStream, report: FinancialReport, businessName: String) {
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
            appendLine("""  <Styles><Style ss:ID="Header"><Font ss:Bold="1"/></Style></Styles>""")

            // Worksheet 1: Summary
            appendLine("""  <Worksheet ss:Name="Muhtasari"><Table>""")
            appendRow("MAUZO APP - RIPOTI", businessName, header = true)
            appendRow("Kipindi", "${report.period.labelSw} (${df.format(Date(report.startEpochMs))} - ${df.format(Date(report.endEpochMs))})")
            appendRow("Fedha Zilizopokelewa (TZS)", report.totalReceived.toPlainString())
            appendRow("Fedha Zilizotumwa (TZS)", report.totalSent.toPlainString())
            appendRow("Kuweka Fedha (TZS)", report.totalDeposits.toPlainString())
            appendRow("Kutoa Fedha (TZS)", report.totalWithdrawals.toPlainString())
            appendRow("Ada Zote (TZS)", report.totalFees.toPlainString())
            appendRow("Marejesho (TZS)", report.totalRefunds.toPlainString())
            appendRow("Ubatilishaji (TZS)", report.totalReversals.toPlainString())
            appendRow("Idadi ya Miamala", report.transactionCount.toString())
            appendLine("""  </Table></Worksheet>""")

            // Worksheet 2: Transactions
            appendLine("""  <Worksheet ss:Name="Miamala"><Table>""")
            appendMultiRow(
                listOf("Transaction ID", "Mtandao", "Aina", "Kiasi", "Ada", "Mhusika", "Simu", "Tarehe", "Saa", "Uhakiki"),
                header = true
            )
            report.transactions.forEach { tx ->
                appendMultiRow(
                    listOf(
                        tx.transactionId,
                        tx.network.displayNameSw,
                        tx.type.labelSw,
                        tx.amount.toPlainString(),
                        tx.fee.toPlainString(),
                        tx.senderOrRecipientName,
                        tx.senderOrRecipientPhone ?: "-",
                        tx.transactionDateText,
                        tx.transactionTimeText,
                        tx.verificationStatus.labelSw
                    )
                )
            }
            appendLine("""  </Table></Worksheet>""")
            appendLine("""</Workbook>""")
        }
        out.write(xml.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    /**
     * Generates a local A4 PDF financial report using Android's PdfDocument API.
     */
    fun exportPdf(uri: Uri, report: FinancialReport, businessName: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writePdfToStream(out, report, businessName)
        } ?: error("Imeshindwa kufungua faili la PDF")
    }

    fun writePdfToStream(out: OutputStream, report: FinancialReport, businessName: String) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#064E3B")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        var y = 48f
        canvas.drawText("MAUZO APP — RIPOTI YA FEDHA", 36f, y, titlePaint)
        y += 18f
        canvas.drawText(
            "Biashara: $businessName | Kipindi: ${report.period.labelSw} (${df.format(Date(report.startEpochMs))} - ${df.format(Date(report.endEpochMs))})",
            36f,
            y,
            bodyPaint
        )
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        canvas.drawText("MUHTASARI WA FEDHA", 36f, y, subtitlePaint)
        y += 16f
        val summaryLines = listOf(
            "Fedha Zilizopokelewa: ${MoneyUtils.formatCurrency(report.totalReceived)}",
            "Fedha Zilizotumwa: ${MoneyUtils.formatCurrency(report.totalSent)}",
            "Ada za Miamala: ${MoneyUtils.formatCurrency(report.totalFees)}",
            "Marejesho / Ubatilishaji: ${MoneyUtils.formatCurrency(report.totalRefunds)} / ${MoneyUtils.formatCurrency(report.totalReversals)}",
            "Idadi ya Miamala: ${report.transactionCount}"
        )
        summaryLines.forEach { line ->
            canvas.drawText(line, 44f, y, bodyPaint)
            y += 14f
        }

        y += 10f
        canvas.drawText("LINGANISHO LA MITANDAO", 36f, y, subtitlePaint)
        y += 16f
        report.networkComparisons.forEach { row ->
            canvas.drawText(
                "${row.network.displayNameSw}: Pokea ${MoneyUtils.formatCurrency(row.received)} | Tuma ${MoneyUtils.formatCurrency(row.sent)} | Salio ${MoneyUtils.formatCurrency(row.calculatedBalance)}",
                44f,
                y,
                bodyPaint
            )
            y += 14f
        }

        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 18f
        canvas.drawText("ORODHA YA MIAMALA", 36f, y, subtitlePaint)
        y += 16f

        for (tx in report.transactions) {
            if (y > pageHeight - 50) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
            val rowText = "${tx.transactionDateText} | ${tx.network.shortLabel} | ${tx.transactionId} | ${MoneyUtils.formatCurrency(tx.amount)} | ${tx.senderOrRecipientName.take(18)}"
            canvas.drawText(rowText, 36f, y, bodyPaint)
            y += 14f
        }

        doc.finishPage(page)
        doc.writeTo(out)
        out.flush()
        doc.close()
    }

    private fun StringBuilder.appendRow(label: String, value: String, header: Boolean = false) {
        val styleAttr = if (header) """ ss:StyleID="Header"""" else ""
        appendLine("""    <Row$styleAttr><Cell><Data ss:Type="String">${xmlEsc(label)}</Data></Cell><Cell><Data ss:Type="String">${xmlEsc(value)}</Data></Cell></Row>""")
    }

    private fun StringBuilder.appendMultiRow(cells: List<String>, header: Boolean = false) {
        val styleAttr = if (header) """ ss:StyleID="Header"""" else ""
        append("    <Row$styleAttr>")
        cells.forEach { c ->
            append("""<Cell><Data ss:Type="String">${xmlEsc(c)}</Data></Cell>""")
        }
        appendLine("</Row>")
    }

    private fun csvEsc(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun xmlEsc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
