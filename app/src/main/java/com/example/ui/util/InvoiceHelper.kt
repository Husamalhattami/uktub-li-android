package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object InvoiceHelper {

    // ==========================================
    // 1. Generic PDF Print & Share Utilities
    // ==========================================

    fun sharePdfFile(context: Context, file: File, title: String = "مشاركة المستند") {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "فشل مشاركة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun printPdfFile(context: Context, file: File, jobName: String = "طباعة مستند") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                sharePdfFile(context, file, jobName)
                return
            }

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder("${jobName}.pdf")
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val input = FileInputStream(file)
                        val output = FileOutputStream(destination?.fileDescriptor)
                        input.copyTo(output)
                        input.close()
                        output.close()
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            sharePdfFile(context, file, jobName)
        }
    }

    fun shareInvoiceText(context: Context, text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "مشاركة الفاتورة نصياً")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "فشل في مشاركة الفاتورة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateThermalReceiptText(
        storeName: String,
        phoneNumber: String,
        address: String,
        invoiceId: Long,
        timestamp: Long,
        customerName: String,
        cashierName: String,
        items: List<Pair<String, Triple<Int, Double, Double>>>,
        totalAmount: Double,
        discount: Double,
        paymentType: String,
        headerNote: String,
        footerNote: String
    ): String {
        return buildString {
            appendLine("════════════════════════════════")
            appendLine("        $storeName")
            if (headerNote.isNotBlank()) appendLine("       $headerNote")
            if (address.isNotBlank()) appendLine("       $address")
            if (phoneNumber.isNotBlank()) appendLine("        هاتف: $phoneNumber")
            appendLine("════════════════════════════════")
            appendLine("فاتورة مبيعات: #$invoiceId")
            appendLine("التاريخ: ${FormatUtils.formatDate(timestamp)}  |  الوقت: ${FormatUtils.formatTime(timestamp)}")
            appendLine("العميل: ${customerName.ifBlank { "زبون نقدي" }}")
            appendLine("البائع: ${cashierName.ifBlank { "صاحب المحل" }}")
            appendLine("طريقة الدفع: $paymentType")
            appendLine("────────────────────────────────")
            items.forEach { (name, info) ->
                val (qty, price, subtotal) = info
                appendLine("$name")
                appendLine("  $qty × ${FormatUtils.formatCurrency(price)} = ${FormatUtils.formatCurrency(subtotal)}")
            }
            appendLine("────────────────────────────────")
            if (discount > 0) {
                appendLine("الخصم الممنوح: - ${FormatUtils.formatCurrency(discount)}")
            }
            appendLine("الإجمالي الصافي: ${FormatUtils.formatCurrency(totalAmount)}")
            appendLine("════════════════════════════════")
            if (footerNote.isNotBlank()) appendLine(footerNote)
            appendLine("       شكراً لزيارتكم!")
            appendLine("════════════════════════════════")
        }
    }

    // ==========================================
    // 2. Multi-Item Professional Sale Invoice PDF
    // ==========================================

    data class PdfSaleLineItem(
        val name: String,
        val details: String,
        val quantity: Int,
        val unitPrice: Double,
        val total: Double
    )

    fun generateSaleInvoicePdf(
        context: Context,
        invoiceNumber: String,
        customerName: String,
        customerPhone: String,
        employeeName: String,
        paymentType: String,
        items: List<PdfSaleLineItem>,
        subtotal: Double,
        discount: Double,
        netTotal: Double,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccountName: String,
        transferRef: String,
        timestamp: Long,
        settings: StoreSettings?,
        previousDebt: Double = 0.0
    ): File {
        val pdfDocument = PdfDocument()

        val itemHeight = 32
        val calculatedHeight = (520 + (items.size * itemHeight) + (if (previousDebt > 0) 50 else 0)).coerceAtLeast(680)

        val pageInfo = PdfDocument.PageInfo.Builder(480, calculatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54) // PdfHeaderNavy
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }

        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }

        val leftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 11f
            textAlign = Paint.Align.LEFT
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val tableHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val phone = settings?.phone?.ifBlank { "770315173" } ?: "770315173"
        val address = settings?.address?.ifBlank { "صنعاء - التحرير" } ?: "صنعاء - التحرير"
        val currency = settings?.currency ?: "ريال"
        val headerNote = settings?.invoiceHeader ?: "جودة .. تراث .. أناقة"
        val footer = settings?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."

        var y = 35f

        // Header Title
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        if (headerNote.isNotBlank()) {
            canvas.drawText(headerNote, 240f, y, subTitlePaint)
            y += 16f
        }
        canvas.drawText("$address  •  هاتف: $phone", 240f, y, subTitlePaint)
        y += 18f

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 18f

        // Invoice Meta Box
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فاتورة مبيعات: $invoiceNumber", 450f, y, boldPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}", 30f, y, leftPaint)
        y += 18f

        canvas.drawText("العميل: ${customerName.ifBlank { "زبون نقدي" }}${if (customerPhone.isNotBlank()) " ($customerPhone)" else ""}", 450f, y, regularPaint)
        canvas.drawText("البائع: ${employeeName.ifBlank { "صاحب المحل" }}", 30f, y, leftPaint)
        y += 18f

        canvas.drawText("طريقة الدفع: $paymentType", 450f, y, regularPaint)
        if (exchangeAccountName.isNotBlank()) {
            canvas.drawText("الصراف: $exchangeAccountName${if (transferRef.isNotBlank()) " (#$transferRef)" else ""}", 30f, y, leftPaint)
        }
        y += 16f

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        // Table Header
        val bgPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(20f, y - 14f, 460f, y + 10f, bgPaint)

        tableHeaderPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف والمواصفات", 445f, y, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية", 260f, y, tableHeaderPaint)
        canvas.drawText("سعر الوحدة", 170f, y, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", 35f, y, tableHeaderPaint)
        y += 20f

        // Table Rows
        items.forEachIndexed { index, item ->
            if (index % 2 == 1) {
                val rowBg = Paint().apply { color = Color.rgb(248, 250, 252) }
                canvas.drawRect(20f, y - 12f, 460f, y + 14f, rowBg)
            }

            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(item.name, 445f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", 260f, y, regularPaint)
            canvas.drawText(FormatUtils.formatNumber(item.unitPrice), 170f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${FormatUtils.formatNumber(item.total)} $currency", 35f, y, regularPaint)

            y += 14f
            if (item.details.isNotBlank()) {
                subTitlePaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(item.details, 445f, y, subTitlePaint)
                y += 14f
            } else {
                y += 8f
            }
        }

        y += 4f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        // Totals Box
        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("إجمالي الأصناف:", 280f, y, regularPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${FormatUtils.formatCurrency(subtotal, currency)}", 35f, y, leftPaint)
        y += 18f

        if (discount > 0) {
            val discPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(217, 119, 6)
                textSize = 11f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("الخصم الممنوح (-):", 280f, y, discPaint)
            discPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("- ${FormatUtils.formatCurrency(discount, currency)}", 35f, y, discPaint)
            y += 18f
        }

        // Net Total Highlight
        val netBg = Paint().apply { color = Color.rgb(240, 253, 244) }
        canvas.drawRect(20f, y - 14f, 460f, y + 14f, netBg)

        val netPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(22, 101, 52)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الصافي المطلوب:", 280f, y + 2f, netPaint)
        netPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(FormatUtils.formatCurrency(netTotal, currency), 35f, y + 2f, netPaint)
        y += 28f

        // Payment Breakdown Line
        regularPaint.textAlign = Paint.Align.RIGHT
        if (cashPaid > 0) {
            canvas.drawText("المدفوع نقداً (كاش): ${FormatUtils.formatCurrency(cashPaid, currency)}", 450f, y, regularPaint)
            y += 16f
        }
        if (transferPaid > 0) {
            canvas.drawText("المدفوع تحويل صرافة: ${FormatUtils.formatCurrency(transferPaid, currency)}", 450f, y, regularPaint)
            y += 16f
        }
        if (debtAmount > 0) {
            val debtP = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(220, 38, 38)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("المبلغ المقيد ديناً على العميل: ${FormatUtils.formatCurrency(debtAmount, currency)}", 450f, y, debtP)
            y += 16f
        }

        // Customer Debt Ledger Balance
        if (previousDebt > 0.01) {
            val totalRemaining = previousDebt + debtAmount
            canvas.drawLine(20f, y, 460f, y, linePaint)
            y += 16f
            subTitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("الرصيد السابق للعميل: ${FormatUtils.formatCurrency(previousDebt, currency)}", 450f, y, subTitlePaint)
            y += 14f
            boldPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("إجمالي الرصيد المستحق بذمة العميل: ${FormatUtils.formatCurrency(totalRemaining, currency)}", 450f, y, boldPaint)
            y += 16f
        }

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 22f

        // Footer Note
        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(footer, 240f, y, subTitlePaint)
        y += 16f
        canvas.drawText("شكراً لتسوقكم معنا  •  نسعد بخدمتكم دائماً", 240f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "فاتورة_${invoiceNumber.replace("/", "_")}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    // ==========================================
    // 3. Purchase Invoice PDF
    // ==========================================

    fun generatePurchaseInvoicePdf(
        context: Context,
        purchase: PurchaseRecord,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(480, 680, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 11f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val currency = settings?.currency ?: "ريال"

        var y = 35f
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        canvas.drawText("سند قيد فاتورة مشتريات بضاعة للمستودع", 240f, y, subTitlePaint)
        y += 18f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        canvas.drawText("فاتورة رقم: #${purchase.id}${if (purchase.invoiceNumber.isNotBlank()) " | المرجع: ${purchase.invoiceNumber}" else ""}", 450f, y, boldPaint)
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("التاريخ: ${FormatUtils.formatDate(purchase.timestamp)}", 30f, y, regularPaint)
        y += 20f

        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المورد: ${purchase.supplierName}", 450f, y, boldPaint)
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("طريقة الدفع: ${purchase.paymentMethod}", 30f, y, regularPaint)
        y += 20f

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 22f

        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف: ${purchase.modelName} (${purchase.category})", 450f, y, boldPaint)
        y += 18f
        canvas.drawText("المقاس واللون: ${purchase.size} | ${purchase.color}", 450f, y, regularPaint)
        y += 18f
        val unitLabel = if (purchase.unit == "حبه") "${purchase.quantity} حبة" else "${purchase.unitCount.toInt()} ${purchase.unit} (${purchase.quantity} حبة)"
        canvas.drawText("الكمية المستلمة: $unitLabel", 450f, y, regularPaint)
        y += 18f
        canvas.drawText("تكلفة شراء الحبة: ${FormatUtils.formatCurrency(purchase.unitBuyPrice, currency)}", 450f, y, regularPaint)
        y += 18f
        canvas.drawText("سعر البيع المقترح: ${FormatUtils.formatCurrency(purchase.unitSellPrice, currency)}", 450f, y, regularPaint)
        y += 20f

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 24f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("إجمالي تكلفة الفاتورة: ${FormatUtils.formatCurrency(purchase.totalCost, currency)}", 450f, y, boldPaint)
        y += 20f
        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المبلغ المدفوع للمورد: ${FormatUtils.formatCurrency(purchase.paidAmount, currency)}", 450f, y, regularPaint)
        y += 18f

        if (purchase.remainingDebt > 0) {
            val debtP = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(220, 38, 38)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("المتبقي دين مستحق للمورد: ${FormatUtils.formatCurrency(purchase.remainingDebt, currency)}", 450f, y, debtP)
            y += 20f
        }

        if (purchase.notes.isNotBlank()) {
            y += 10f
            subTitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("ملاحظات: ${purchase.notes}", 450f, y, subTitlePaint)
            y += 16f
        }

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "مشتريات_${purchase.id}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    // ==========================================
    // 4. Daily Cash Drawer & Movement Statement PDF
    // ==========================================

    fun generateDailyMovementPdf(
        context: Context,
        movements: List<CashDrawerMovement>,
        transfers: List<ExchangeTransfer>,
        currentCash: Double,
        currentExchange: Double,
        periodTitle: String,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val totalRows = (movements.size + transfers.size).coerceAtMost(30)
        val calculatedHeight = (500 + (totalRows * 26)).coerceAtLeast(700)

        val pageInfo = PdfDocument.PageInfo.Builder(500, calculatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val currency = settings?.currency ?: "ريال"

        var y = 35f
        canvas.drawText(shopName, 250f, y, titlePaint)
        y += 18f
        canvas.drawText("كشف حركة الصندوق والحسابات ($periodTitle)", 250f, y, subTitlePaint)
        y += 16f
        canvas.drawText("تاريخ التقرير: ${FormatUtils.formatDate(System.currentTimeMillis())}", 250f, y, subTitlePaint)
        y += 18f
        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 20f

        // Balances Strip
        val stripBg = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(20f, y - 14f, 480f, y + 26f, stripBg)

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("رصيد كاش الصندوق: ${FormatUtils.formatCurrency(currentCash, currency)}", 460f, y + 4f, boldPaint)
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("أرصدة الصرافة: ${FormatUtils.formatCurrency(currentExchange, currency)}", 40f, y + 4f, boldPaint)
        y += 42f

        // Movements Table Header
        canvas.drawRect(20f, y - 12f, 480f, y + 10f, Paint().apply { color = Color.rgb(226, 232, 240) })
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الوقت والبيان", 460f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الحساب", 260f, y, boldPaint)
        canvas.drawText("النوع", 180f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", 40f, y, boldPaint)
        y += 20f

        val combinedMovements = movements.take(25)
        combinedMovements.forEachIndexed { index, m ->
            if (index % 2 == 1) {
                canvas.drawRect(20f, y - 10f, 480f, y + 12f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${FormatUtils.formatTime(m.timestamp)} • ${m.title}", 460f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الصندوق", 260f, y, regularPaint)

            val typeColor = if (m.isIncome) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
            val typeP = Paint().apply {
                isAntiAlias = true
                color = typeColor
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(if (m.isIncome) "مقبوضات" else "مدفوعات", 180f, y, typeP)

            typeP.textAlign = Paint.Align.LEFT
            canvas.drawText((if (m.isIncome) "+ " else "- ") + FormatUtils.formatNumber(m.amount) + " $currency", 40f, y, typeP)

            y += 20f
        }

        y += 10f
        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 20f
        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("نهاية كشف الحركة  •  تم استخراجه آلياً من نظام حسابي للمحل", 250f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "كشف_حركة_الصندوق.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    // ==========================================
    // 5. Employee Statement PDF (كشف حساب الموظف)
    // ==========================================

    fun generateEmployeeStatementPdf(
        context: Context,
        employee: Employee,
        transactions: List<EmployeeTransaction>,
        salesCount: Int,
        totalSalesAmount: Double,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val calculatedHeight = (500 + (transactions.size * 26)).coerceAtLeast(650)
        val pageInfo = PdfDocument.PageInfo.Builder(480, calculatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val currency = settings?.currency ?: "ريال"

        var y = 35f
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        canvas.drawText("كشف حساب ومسير مستحقات الموظف / العامل", 240f, y, subTitlePaint)
        y += 18f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("اسم الموظف: ${employee.name} (${employee.role})", 450f, y, boldPaint)
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الراتب الأساسي: ${FormatUtils.formatCurrency(employee.baseSalary, currency)} (${employee.salaryType})", 30f, y, regularPaint)
        y += 18f

        if (employee.phone.isNotBlank()) {
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("رقم الهاتف: ${employee.phone}", 450f, y, regularPaint)
        }
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("إجمالي المبيعات المنفذة: ${FormatUtils.formatCurrency(totalSalesAmount, currency)} ($salesCount عملية)", 30f, y, regularPaint)
        y += 20f

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        // Table Header
        canvas.drawRect(20f, y - 12f, 460f, y + 10f, Paint().apply { color = Color.rgb(241, 245, 249) })
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ والبيان", 445f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("نوع العملية", 240f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", 35f, y, boldPaint)
        y += 20f

        var totalAdvances = 0.0
        var totalSalariesPaid = 0.0

        transactions.forEachIndexed { index, tx ->
            if (index % 2 == 1) {
                canvas.drawRect(20f, y - 10f, 460f, y + 12f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            if (tx.type.contains("سلفة")) totalAdvances += tx.amount
            if (tx.type.contains("راتب")) totalSalariesPaid += tx.amount

            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${FormatUtils.formatDate(tx.timestamp)}: ${tx.notes.ifBlank { tx.type }}", 445f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(tx.type, 240f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${FormatUtils.formatCurrency(tx.amount, currency)}", 35f, y, regularPaint)

            y += 20f
        }

        y += 10f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("إجمالي السلف المسحوبة: ${FormatUtils.formatCurrency(totalAdvances, currency)}", 450f, y, boldPaint)
        y += 18f
        canvas.drawText("إجمالي الرواتب المصروفة: ${FormatUtils.formatCurrency(totalSalariesPaid, currency)}", 450f, y, boldPaint)
        y += 24f

        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("تم استخراج الكشف آلياً  •  توقيع الموظف: ...................", 240f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "كشف_موظف_${employee.name}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    // ==========================================
    // 6. Party Ledger Statement PDF (كشف حساب عميل أو مورد)
    // ==========================================

    fun generatePartyStatementPdf(
        context: Context,
        party: PartyAccount,
        debts: List<DebtRecord>,
        payments: List<DebtPayment>,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val totalRows = (debts.size + payments.size).coerceAtMost(30)
        val calculatedHeight = (500 + (totalRows * 26)).coerceAtLeast(650)
        val pageInfo = PdfDocument.PageInfo.Builder(480, calculatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val currency = settings?.currency ?: "ريال"
        val partyTypeTitle = if (party.type == "CUSTOMER") "عميل / زبون" else "مورد / شركة"

        var y = 35f
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        canvas.drawText("كشف حساب مديونية وسداد ($partyTypeTitle)", 240f, y, subTitlePaint)
        y += 18f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الطرف: ${party.name}", 450f, y, boldPaint)
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("التاريخ: ${FormatUtils.formatDate(System.currentTimeMillis())}", 30f, y, regularPaint)
        y += 18f

        if (party.phone.isNotBlank()) {
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("رقم الهاتف: ${party.phone}", 450f, y, regularPaint)
            y += 18f
        }

        val totalDebts = debts.sumOf { it.remainingAmount }
        val stripBg = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(20f, y - 10f, 460f, y + 20f, stripBg)

        val balanceColor = if (totalDebts > 0) Color.rgb(220, 38, 38) else Color.rgb(22, 163, 74)
        val balPaint = Paint().apply {
            isAntiAlias = true
            color = balanceColor
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الرصيد المتبقي المستحق: ${FormatUtils.formatCurrency(totalDebts, currency)}", 445f, y + 6f, balPaint)
        y += 34f

        // Table Header
        canvas.drawRect(20f, y - 12f, 460f, y + 10f, Paint().apply { color = Color.rgb(226, 232, 240) })
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("التاريخ والبيان", 445f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("النوع", 240f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("المبلغ", 35f, y, boldPaint)
        y += 20f

        debts.forEachIndexed { index, d ->
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${FormatUtils.formatDate(d.createdAt)}: ${d.details.ifBlank { "فاتورة دين" }}", 445f, y, regularPaint)
            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("دين مسجل", 240f, y, regularPaint)
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("+ ${FormatUtils.formatCurrency(d.originalAmount, currency)}", 35f, y, regularPaint)
            y += 20f
        }

        payments.forEachIndexed { index, p ->
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${FormatUtils.formatDate(p.timestamp)}: ${p.notes.ifBlank { "دفعة سداد" }}", 445f, y, regularPaint)
            regularPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("سداد واصل", 240f, y, regularPaint)
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("- ${FormatUtils.formatCurrency(p.amount, currency)}", 35f, y, regularPaint)
            y += 20f
        }

        y += 10f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f
        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("نهاية كشف الحساب  •  حسابي للمحل", 240f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "كشف_حساب_${party.name}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    // Legacy single-item helper
    fun generatePdfInvoiceFile(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ): File {
        return generateSaleInvoicePdf(
            context = context,
            invoiceNumber = if (sale.invoiceNumber.isNotBlank()) sale.invoiceNumber else "INV-${sale.id}",
            customerName = sale.customerName,
            customerPhone = sale.customerPhone,
            employeeName = sale.employeeName,
            paymentType = sale.paymentType,
            items = listOf(
                PdfSaleLineItem(
                    name = sale.productName,
                    details = "المقاس: ${sale.size} | اللون: ${sale.color}",
                    quantity = sale.quantity,
                    unitPrice = sale.unitSellPrice,
                    total = sale.totalAmount
                )
            ),
            subtotal = (sale.unitSellPrice * sale.quantity),
            discount = sale.discount,
            netTotal = sale.totalAmount,
            cashPaid = sale.cashAmount,
            transferPaid = sale.transferAmount,
            debtAmount = sale.debtAmount,
            exchangeAccountName = sale.exchangeAccountName,
            transferRef = sale.transferRef,
            timestamp = sale.timestamp,
            settings = settings,
            previousDebt = previousDebt
        )
    }

    fun shareInvoice(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ) {
        val file = generatePdfInvoiceFile(context, sale, settings, customerParty, previousDebt)
        sharePdfFile(context, file, "مشاركة فاتورة #${sale.id}")
    }

    fun printInvoiceDirect(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ) {
        val file = generatePdfInvoiceFile(context, sale, settings, customerParty, previousDebt)
        printPdfFile(context, file, "فاتورة_${sale.id}")
    }

    fun generateFinancialReportPdf(
        context: Context,
        periodTitle: String,
        sales: List<SaleTransaction>,
        expenses: List<ExpenseRecord>,
        employeeTx: List<EmployeeTransaction>,
        purchases: List<PurchaseRecord>,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(500, 720, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val currency = settings?.currency ?: "ريال"

        val totalSales = sales.sumOf { it.totalAmount }
        val totalCostOfGoods = sales.sumOf { it.unitBuyPrice * it.quantity }
        val grossProfit = totalSales - totalCostOfGoods
        val totalExpenses = expenses.sumOf { it.amount }
        val totalSalaries = employeeTx.filter { it.type == "صرف راتب" || it.type == "سلفة" }.sumOf { it.amount }
        val netProfit = grossProfit - totalExpenses - totalSalaries

        var y = 35f
        canvas.drawText(shopName, 250f, y, titlePaint)
        y += 18f
        canvas.drawText("التقرير المالي والأرباح ($periodTitle)", 250f, y, subTitlePaint)
        y += 16f
        canvas.drawText("تاريخ التقرير: ${FormatUtils.formatDate(System.currentTimeMillis())}", 250f, y, subTitlePaint)
        y += 18f
        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 24f

        // Financial Equation Summary Cards
        boldPaint.textAlign = Paint.Align.RIGHT
        regularPaint.textAlign = Paint.Align.LEFT

        canvas.drawText("إجمالي المبيعات المحققة (${sales.size} فاتورة):", 450f, y, boldPaint)
        canvas.drawText(FormatUtils.formatCurrency(totalSales, currency), 40f, y, regularPaint)
        y += 20f

        canvas.drawText("تكلفة البضاعة المباعة (سعر الشراء):", 450f, y, boldPaint)
        canvas.drawText("- ${FormatUtils.formatCurrency(totalCostOfGoods, currency)}", 40f, y, regularPaint)
        y += 20f

        val grossPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(37, 99, 235)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("إجمالي الربح التجاري (الهامش):", 450f, y, grossPaint)
        grossPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("+ ${FormatUtils.formatCurrency(grossProfit, currency)}", 40f, y, grossPaint)
        y += 22f

        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المصاريف التشغيلية (${expenses.size} سند):", 450f, y, boldPaint)
        canvas.drawText("- ${FormatUtils.formatCurrency(totalExpenses, currency)}", 40f, y, regularPaint)
        y += 20f

        canvas.drawText("الرواتب والسلف المصروفة:", 450f, y, boldPaint)
        canvas.drawText("- ${FormatUtils.formatCurrency(totalSalaries, currency)}", 40f, y, regularPaint)
        y += 24f

        // Net Profit Box
        val netColor = if (netProfit >= 0) Color.rgb(240, 253, 244) else Color.rgb(254, 242, 242)
        val netTextColor = if (netProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(220, 38, 38)
        canvas.drawRect(20f, y - 14f, 480f, y + 26f, Paint().apply { color = netColor })

        val netPaint = Paint().apply {
            isAntiAlias = true
            color = netTextColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("صافي الربح الفعلي بعد المصاريف:", 450f, y + 6f, netPaint)
        netPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(FormatUtils.formatCurrency(netProfit, currency), 40f, y + 6f, netPaint)
        y += 46f

        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 22f

        // Top Sold Items
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("أكثر الأصناف مبيعاً خلال الفترة:", 450f, y, boldPaint)
        y += 20f

        val topSales = sales.groupBy { it.productName }.map { (name, list) ->
            name to Pair(list.sumOf { it.quantity }, list.sumOf { it.profit })
        }.sortedByDescending { it.second.second }.take(8)

        topSales.forEach { (name, pair) ->
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("• $name (${pair.first} حبة)", 440f, y, regularPaint)
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("ربح: ${FormatUtils.formatCurrency(pair.second, currency)}", 40f, y, regularPaint)
            y += 18f
        }

        y += 10f
        canvas.drawLine(20f, y, 480f, y, linePaint)
        y += 20f
        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("تم استخراج التقرير آلياً  •  حسابي للمحل", 250f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "تقرير_${periodTitle}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }
}
