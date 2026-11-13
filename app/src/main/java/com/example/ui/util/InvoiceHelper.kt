package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

    private fun drawStoreLogoIfPresent(
        canvas: Canvas,
        settings: StoreSettings?,
        left: Float = 24f,
        top: Float = 18f,
        size: Float = 44f
    ) {
        val logoPath = settings?.logoPath.orEmpty()
        val bmp = LogoHelper.loadLogoBitmap(logoPath) ?: return
        try {
            val dest = RectF(left, top, left + size, top + size)
            canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally {
            bmp.recycle()
        }
    }

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
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
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
        val total: Double,
        val unit: String = "حبه"
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

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val phone = settings?.phone?.ifBlank { "" } ?: ""
        val address = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency ?: "ريال"
        val headerNote = settings?.invoiceHeader ?: ""
        val footer = settings?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 44f)
        var y = 35f

        // Header Title
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        if (headerNote.isNotBlank()) {
            canvas.drawText(headerNote, 240f, y, subTitlePaint)
            y += 16f
        }
        val contactLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, 240f, y, subTitlePaint)
            y += 18f
        }

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
            canvas.drawText("${item.quantity} ${item.unit}", 260f, y, regularPaint)
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

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency ?: "ريال"

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 40f)
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

    fun generateMultiItemPurchaseInvoicePdf(
        context: Context,
        invoiceNumber: String,
        supplierName: String,
        paymentMethod: String,
        exchangeAccountName: String,
        transferRef: String,
        items: List<CartPurchaseItem>,
        totalCost: Double,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        timestamp: Long,
        settings: StoreSettings?
    ): File {
        val calculatedHeight = (520 + (items.size * 36)).coerceAtLeast(680)
        val pdfDocument = PdfDocument()
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
        val tableHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val leftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            textAlign = Paint.Align.LEFT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency ?: "ريال"
        val phone = settings?.phone ?: ""
        val address = settings?.address ?: ""

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 42f)
        var y = 35f
        canvas.drawText(shopName, 240f, y, titlePaint)
        y += 18f
        canvas.drawText("سند قيد فاتورة مشتريات واستلام بضاعة للمستودع", 240f, y, subTitlePaint)
        y += 16f
        val contactLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, 240f, y, subTitlePaint)
            y += 18f
        }
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فاتورة مشتريات: $invoiceNumber", 450f, y, boldPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}", 30f, y, leftPaint)
        y += 20f

        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("المورد: ${supplierName.ifBlank { "مورد عام" }}", 450f, y, boldPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("طريقة الدفع: $paymentMethod", 30f, y, leftPaint)
        y += 18f

        if (exchangeAccountName.isNotBlank() || transferRef.isNotBlank()) {
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("حساب الصراف: $exchangeAccountName", 450f, y, regularPaint)
            if (transferRef.isNotBlank()) {
                leftPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("رقم الحوالة: #$transferRef", 30f, y, leftPaint)
            }
            y += 18f
        }

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        // Table Header
        val bgPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(20f, y - 14f, 460f, y + 10f, bgPaint)

        tableHeaderPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف والمواصفات", 445f, y, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("الكمية / الوحدة", 260f, y, tableHeaderPaint)
        canvas.drawText("سعر الشراء", 165f, y, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", 35f, y, tableHeaderPaint)
        y += 20f

        // Table Rows
        items.forEachIndexed { index, item ->
            if (index % 2 == 1) {
                val rowBg = Paint().apply { color = Color.rgb(248, 250, 252) }
                canvas.drawRect(20f, y - 12f, 460f, y + 16f, rowBg)
            }

            regularPaint.textAlign = Paint.Align.RIGHT
            val itemTitle = "${item.modelName} (${item.category})"
            canvas.drawText(itemTitle, 445f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.CENTER
            val unitStr = if (item.unit == "حبه") "${item.totalPieces} حبة" else "${item.unitCount.toInt()} ${item.unit} (${item.totalPieces} حبة)"
            canvas.drawText(unitStr, 260f, y, regularPaint)
            canvas.drawText(FormatUtils.formatNumber(item.buyPricePerUnit), 165f, y, regularPaint)

            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${FormatUtils.formatNumber(item.lineCost)} $currency", 35f, y, regularPaint)

            y += 14f
            val specDetails = "المقاس: ${item.size} | اللون: ${item.color}"
            subTitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(specDetails, 445f, y, subTitlePaint)
            y += 14f
        }

        y += 6f
        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 22f

        // Totals Box
        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("إجمالي تكلفة المشتريات (${items.size} أصناف):", 280f, y, regularPaint)
        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(FormatUtils.formatCurrency(totalCost, currency), 35f, y, leftPaint)
        y += 20f

        if (cashPaid > 0) {
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("المدفوع نقداً من الصندوق:", 280f, y, regularPaint)
            leftPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(FormatUtils.formatCurrency(cashPaid, currency), 35f, y, leftPaint)
            y += 18f
        }

        if (transferPaid > 0) {
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("المدفوع تحويل عبر الصرافة:", 280f, y, regularPaint)
            leftPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(FormatUtils.formatCurrency(transferPaid, currency), 35f, y, leftPaint)
            y += 18f
        }

        if (debtAmount > 0) {
            val debtP = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(220, 38, 38)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("المبلغ المقيد ديناً للمورد (عليا زلط):", 280f, y, debtP)
            debtP.textAlign = Paint.Align.LEFT
            canvas.drawText(FormatUtils.formatCurrency(debtAmount, currency), 35f, y, debtP)
            y += 20f
        }

        canvas.drawLine(20f, y, 460f, y, linePaint)
        y += 20f

        subTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("تم استلام وإدخال الأصناف بنجاح لمخزون المستودع", 240f, y, subTitlePaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "فاتورة_شراء_${invoiceNumber.replace("/", "_")}.pdf")
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

        val shopName = settings?.shopNameAr?.ifBlank { "الجرادي للملابس الجاهزة" } ?: "الجرادي للملابس الجاهزة"
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
    // 5. Employee Statement PDF (كشف حساب الموظف - متعدد الصفحات)
    // ==========================================

    private fun clipPdfText(text: String, maxChars: Int): String {
        val clean = text.replace('\n', ' ').trim()
        return if (clean.length <= maxChars) clean else clean.take(maxChars - 1) + "…"
    }

    fun generateEmployeeStatementPdf(
        context: Context,
        employee: Employee,
        transactions: List<EmployeeTransaction>,
        salesCount: Int,
        totalSalesAmount: Double,
        settings: StoreSettings?
    ): File {
        val dummySales = if (salesCount > 0 && totalSalesAmount > 0.0) {
            listOf(
                SaleTransaction(
                    productId = 0L,
                    productName = "إجمالي المبيعات",
                    category = "",
                    color = "",
                    size = "",
                    quantity = salesCount,
                    unitBuyPrice = 0.0,
                    unitSellPrice = totalSalesAmount,
                    totalAmount = totalSalesAmount,
                    profit = 0.0,
                    employeeId = employee.id,
                    employeeName = employee.name
                )
            )
        } else emptyList()
        val summary = StatementHelper.buildEmployeeStatement(employee, transactions, dummySales).copy(
            salesCount = salesCount,
            totalSalesAmount = totalSalesAmount,
            commissionEarned = totalSalesAmount * (employee.commissionPercent / 100.0)
        )
        return generateEmployeeStatementPdf(context, summary, StatementMode.DETAILED, settings)
    }

    fun generateEmployeeStatementPdf(
        context: Context,
        summary: EmployeeStatementSummary,
        mode: StatementMode,
        settings: StoreSettings?
    ): File {
        val pageWidth = 540
        val pageHeight = 780
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val emp = summary.employee

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        val boldRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val regularRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val regularLeftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.LEFT
        }
        val smallMutedRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 34f

        fun drawEmpTableHeader() {
            canvas.drawRect(24f, y - 13f, 516f, y + 9f, Paint().apply { color = Color.rgb(15, 30, 54) })
            val thPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ", 508f, y, thPaint)
            canvas.drawText("البيان والحركة", 435f, y, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("له (+)", 215f, y, thPaint)
            canvas.drawText("عليه (-)", 135f, y, thPaint)
            thPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الرصيد", 32f, y, thPaint)
            y += 19f
        }

        fun nextEmpPage() {
            subPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("صفحة $pageNumber — يتبع...", pageWidth / 2f, pageHeight - 20f, subPaint)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 32f
            boldRightPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("تابع كشف حساب العامل: ${emp.name} (صفحة $pageNumber)", 516f, y, boldRightPaint)
            y += 14f
            canvas.drawLine(24f, y, 516f, y, linePaint)
            y += 20f
            drawEmpTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 40f)
        canvas.drawText(shopName, pageWidth / 2f, y, titlePaint)
        y += 17f
        val docTitle = if (mode == StatementMode.DETAILED) "كشف حساب عامل مفصل بجميع العمليات" else "تقرير نهائي لمستحقات وحساب عامل"
        canvas.drawText(docTitle, pageWidth / 2f, y, subPaint)
        y += 14f
        val contact = listOfNotNull(shopAddress.takeIf { it.isNotBlank() }, shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }).joinToString(" • ")
        if (contact.isNotBlank()) {
            canvas.drawText(contact, pageWidth / 2f, y, subPaint)
            y += 14f
        }
        canvas.drawLine(24f, y, 516f, y, linePaint)
        y += 18f

        // Employee Meta
        canvas.drawText("العامل: ${emp.name} (${emp.role})", 512f, y, boldRightPaint)
        canvas.drawText("تاريخ الكشف: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", 28f, y, regularLeftPaint)
        y += 16f
        canvas.drawText("الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)} (${emp.salaryType})", 512f, y, regularRightPaint)
        canvas.drawText("مبيعاته: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)", 28f, y, regularLeftPaint)
        y += 16f

        // 3 Summary Cards (له | عليه | الباقي)
        val boxTop = y
        val boxBottom = y + 48f
        val colWidth = (516f - 24f - 16f) / 3f
        val xBox3 = 24f // left-most: باقي له
        val xBox2 = xBox3 + colWidth + 8f // middle: عليه
        val xBox1 = xBox2 + colWidth + 8f // right-most: له

        fun drawSummaryBox(left: Float, bg: Int, border: Int, label: String, amount: String, amountColor: Int) {
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply { color = bg })
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            val lPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(71, 85, 105)
                textSize = 8.5f
                textAlign = Paint.Align.CENTER
            }
            val vPaint = Paint().apply {
                isAntiAlias = true
                color = amountColor
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(label, left + colWidth / 2f, boxTop + 17f, lPaint)
            canvas.drawText(amount, left + colWidth / 2f, boxTop + 37f, vPaint)
        }

        drawSummaryBox(
            xBox1,
            Color.rgb(236, 253, 245),
            Color.rgb(167, 243, 208),
            "كم له (مستحقات)",
            FormatUtils.formatCurrency(summary.totalEarned, currency),
            Color.rgb(4, 120, 87)
        )
        drawSummaryBox(
            xBox2,
            Color.rgb(254, 242, 242),
            Color.rgb(254, 202, 202),
            "كم عليه (مسحوب وخصم)",
            FormatUtils.formatCurrency(summary.totalDeductedAndPaid, currency),
            Color.rgb(185, 28, 28)
        )
        val remLabel = when {
            summary.remainingBalance > 0.01 -> "كم باقي له"
            summary.remainingBalance < -0.01 -> "عليه سحب زائد"
            else -> "الحساب خالص"
        }
        val remBg = if (summary.remainingBalance >= 0) Color.rgb(239, 246, 255) else Color.rgb(255, 247, 237)
        val remBorder = if (summary.remainingBalance >= 0) Color.rgb(191, 219, 254) else Color.rgb(254, 215, 170)
        val remColor = if (summary.remainingBalance >= 0) Color.rgb(29, 78, 216) else Color.rgb(194, 65, 12)
        drawSummaryBox(
            xBox3,
            remBg,
            remBorder,
            remLabel,
            FormatUtils.formatCurrency(kotlin.math.abs(summary.remainingBalance), currency),
            remColor
        )

        y = boxBottom + 18f

        if (mode == StatementMode.SUMMARY) {
            val rows = listOf(
                "الراتب الأساسي (${emp.salaryType})" to "+ ${FormatUtils.formatCurrency(emp.baseSalary, currency)}",
                "عمولة المبيعات (${emp.commissionPercent}%)" to "+ ${FormatUtils.formatCurrency(summary.commissionEarned, currency)}",
                "المكافآت المضافة" to "+ ${FormatUtils.formatCurrency(summary.bonuses, currency)}",
                "السلف المسحوبة" to "- ${FormatUtils.formatCurrency(summary.advances, currency)}",
                "الصرفيات اليومية" to "- ${FormatUtils.formatCurrency(summary.dailyStipends, currency)}",
                "خصومات الغياب والتأخير" to "- ${FormatUtils.formatCurrency(summary.deductions, currency)}",
                "الرواتب المصروفة" to "- ${FormatUtils.formatCurrency(summary.salaryPaid, currency)}"
            )
            rows.forEachIndexed { idx, (label, value) ->
                if (idx % 2 == 0) {
                    canvas.drawRect(24f, y - 12f, 516f, y + 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
                }
                canvas.drawText(label, 506f, y, regularRightPaint)
                canvas.drawText(value, 34f, y, regularLeftPaint)
                y += 22f
            }
            y += 8f
            canvas.drawLine(24f, y, 516f, y, linePaint)
            y += 20f
            canvas.drawText("صافي المستحق المتبقي للعامل:", 506f, y, boldRightPaint)
            val netLeft = Paint(regularLeftPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11.5f
                color = remColor
            }
            canvas.drawText(FormatUtils.formatCurrency(summary.remainingBalance, currency), 34f, y, netLeft)
            y += 30f
        } else {
            drawEmpTableHeader()
            if (summary.entries.isEmpty()) {
                canvas.drawText("لا توجد حركات مسجلة لهذا العامل حتى الآن", pageWidth / 2f, y + 10f, subPaint)
                y += 30f
            } else {
                summary.entries.forEachIndexed { index, entry ->
                    if (y > 700f) nextEmpPage()
                    if (index % 2 == 1) {
                        canvas.drawRect(24f, y - 11f, 516f, y + 11f, Paint().apply { color = Color.rgb(248, 250, 252) })
                    }
                    canvas.drawText(FormatUtils.formatDate(entry.timestamp), 508f, y, smallMutedRightPaint)
                    canvas.drawText(clipPdfText("${entry.type} • ${entry.title}", 34), 435f, y, regularRightPaint)

                    val centerPaint = Paint(regularRightPaint).apply { textAlign = Paint.Align.CENTER }
                    if (entry.creditEarned > 0.01) {
                        centerPaint.color = Color.rgb(4, 120, 87)
                        canvas.drawText(FormatUtils.formatNumber(entry.creditEarned), 215f, y, centerPaint)
                    } else {
                        centerPaint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 215f, y, centerPaint)
                    }
                    if (entry.debitTaken > 0.01) {
                        centerPaint.color = Color.rgb(185, 28, 28)
                        canvas.drawText(FormatUtils.formatNumber(entry.debitTaken), 135f, y, centerPaint)
                    } else {
                        centerPaint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 135f, y, centerPaint)
                    }
                    val balText = FormatUtils.formatNumber(entry.runningBalance)
                    canvas.drawText(balText, 32f, y, regularLeftPaint)
                    y += 21f
                }
            }
        }

        if (y > 690f) nextEmpPage()
        y += 12f
        canvas.drawLine(24f, y, 516f, y, linePaint)
        y += 22f
        canvas.drawText("توقيع العامل: ________________", 508f, y, regularRightPaint)
        canvas.drawText("اعتماد الإدارة: ________________", 32f, y, regularLeftPaint)
        subPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("صفحة $pageNumber • تم إصداره آلياً من نظام $shopName", pageWidth / 2f, pageHeight - 20f, subPaint)

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        val safeName = emp.name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        val file = File(dir, "كشف_عامل_${safeName}.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file
    }

    // ==========================================
    // 6. Party Ledger Statement PDF (كشف حساب عميل أو مورد - مفصل / نهائي متعدد الصفحات)
    // ==========================================

    fun generatePartyStatementPdf(
        context: Context,
        party: PartyAccount,
        debts: List<DebtRecord>,
        payments: List<DebtPayment>,
        settings: StoreSettings?
    ): File {
        val summary = StatementHelper.buildPartyStatement(
            party = party,
            sales = emptyList(),
            purchases = emptyList(),
            debts = debts,
            payments = payments
        )
        return generatePartyStatementPdf(context, summary, StatementMode.DETAILED, settings)
    }

    fun generatePartyStatementPdf(
        context: Context,
        summary: PartyStatementSummary,
        mode: StatementMode,
        settings: StoreSettings?
    ): File {
        val pageWidth = 540
        val pageHeight = 780
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        val boldRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val regularRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val regularLeftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.LEFT
        }
        val smallMutedRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 34f

        fun drawPartyTableHeader() {
            canvas.drawRect(24f, y - 13f, 516f, y + 9f, Paint().apply { color = Color.rgb(15, 30, 54) })
            val thPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ", 508f, y, thPaint)
            canvas.drawText("البيان والتفاصيل", 436f, y, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (summary.isCustomer) "عليه" else "مسدد له", 215f, y, thPaint)
            canvas.drawText(if (summary.isCustomer) "له (سداد)" else "له (توريد)", 140f, y, thPaint)
            thPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الرصيد", 32f, y, thPaint)
            y += 19f
        }

        fun nextPartyPage(withTableHeader: Boolean = true) {
            subPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("صفحة $pageNumber — يتبع...", pageWidth / 2f, pageHeight - 20f, subPaint)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 32f
            canvas.drawText(
                "تابع كشف حساب ${if (summary.isCustomer) "العميل" else "المورد"}: ${summary.party.name} (صفحة $pageNumber)",
                512f,
                y,
                boldRightPaint
            )
            y += 14f
            canvas.drawLine(24f, y, 516f, y, linePaint)
            y += 18f
            if (withTableHeader) drawPartyTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 40f)
        canvas.drawText(shopName, pageWidth / 2f, y, titlePaint)
        y += 17f
        val docTitle = when {
            summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب عميل تفصيلي بجميع العمليات"
            summary.isCustomer && mode == StatementMode.SUMMARY -> "تقرير نهائي لحساب العميل"
            !summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب مورد تفصيلي بجميع العمليات"
            else -> "تقرير نهائي لحساب المورد"
        }
        canvas.drawText(docTitle, pageWidth / 2f, y, subPaint)
        y += 14f
        val contact = listOfNotNull(shopAddress.takeIf { it.isNotBlank() }, shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }).joinToString(" • ")
        if (contact.isNotBlank()) {
            canvas.drawText(contact, pageWidth / 2f, y, subPaint)
            y += 14f
        }
        canvas.drawLine(24f, y, 516f, y, linePaint)
        y += 18f

        // Party Info
        val roleTitle = if (summary.isCustomer) "العميل" else "المورد"
        canvas.drawText("$roleTitle: ${summary.party.name}", 512f, y, boldRightPaint)
        canvas.drawText("تاريخ الإصدار: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", 28f, y, regularLeftPaint)
        y += 16f
        val partyPhoneStr = if (summary.party.phone.isNotBlank()) "هاتف $roleTitle: ${summary.party.phone}" else "إجمالي العمليات: ${summary.entries.size}"
        canvas.drawText(partyPhoneStr, 512f, y, regularRightPaint)
        canvas.drawText("فواتير: ${summary.invoicesCount} | سندات: ${summary.paymentsCount}", 28f, y, regularLeftPaint)
        y += 16f

        // 3 Summary Cards (عليه | له | باقي)
        val boxTop = y
        val boxBottom = y + 50f
        val colWidth = (516f - 24f - 16f) / 3f
        val xLeftBox = 24f
        val xMidBox = xLeftBox + colWidth + 8f
        val xRightBox = xMidBox + colWidth + 8f

        fun drawKpiCard(left: Float, bg: Int, border: Int, title: String, value: String, valueColor: Int) {
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply { color = bg })
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            val lP = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(71, 85, 105)
                textSize = 8.5f
                textAlign = Paint.Align.CENTER
            }
            val vP = Paint().apply {
                isAntiAlias = true
                color = valueColor
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(title, left + colWidth / 2f, boxTop + 17f, lP)
            canvas.drawText(value, left + colWidth / 2f, boxTop + 38f, vP)
        }

        if (summary.isCustomer) {
            drawKpiCard(
                xRightBox,
                Color.rgb(254, 242, 242),
                Color.rgb(254, 202, 202),
                "كم عليه (فواتير وديون)",
                FormatUtils.formatCurrency(summary.totalOnParty, currency),
                Color.rgb(185, 28, 28)
            )
            drawKpiCard(
                xMidBox,
                Color.rgb(236, 253, 245),
                Color.rgb(167, 243, 208),
                "كم له (المدفوع والمحصل)",
                FormatUtils.formatCurrency(summary.totalForParty, currency),
                Color.rgb(4, 120, 87)
            )
            val netLabel = when {
                summary.netBalance > 0.01 -> "كم باقي عليه"
                summary.netBalance < -0.01 -> "له رصيد زائد"
                else -> "الحساب خالص"
            }
            val netBg = when {
                summary.netBalance > 0.01 -> Color.rgb(255, 247, 237)
                summary.netBalance < -0.01 -> Color.rgb(239, 246, 255)
                else -> Color.rgb(240, 253, 244)
            }
            val netBorder = when {
                summary.netBalance > 0.01 -> Color.rgb(254, 215, 170)
                summary.netBalance < -0.01 -> Color.rgb(191, 219, 254)
                else -> Color.rgb(187, 247, 208)
            }
            val netColor = when {
                summary.netBalance > 0.01 -> Color.rgb(194, 65, 12)
                summary.netBalance < -0.01 -> Color.rgb(29, 78, 216)
                else -> Color.rgb(22, 101, 52)
            }
            drawKpiCard(
                xLeftBox,
                netBg,
                netBorder,
                netLabel,
                FormatUtils.formatCurrency(kotlin.math.abs(summary.netBalance), currency),
                netColor
            )
        } else {
            drawKpiCard(
                xRightBox,
                Color.rgb(239, 246, 255),
                Color.rgb(191, 219, 254),
                "كم له (توريدات وديون)",
                FormatUtils.formatCurrency(summary.totalForParty, currency),
                Color.rgb(29, 78, 216)
            )
            drawKpiCard(
                xMidBox,
                Color.rgb(236, 253, 245),
                Color.rgb(167, 243, 208),
                "كم عليه (المسدد له)",
                FormatUtils.formatCurrency(summary.totalOnParty, currency),
                Color.rgb(4, 120, 87)
            )
            val netLabel = when {
                summary.netBalance > 0.01 -> "كم باقي للمورد"
                summary.netBalance < -0.01 -> "رصيد مقدم لدى المورد"
                else -> "حساب المورد خالص"
            }
            val netBg = if (summary.netBalance > 0.01) Color.rgb(255, 247, 237) else Color.rgb(240, 253, 244)
            val netBorder = if (summary.netBalance > 0.01) Color.rgb(254, 215, 170) else Color.rgb(187, 247, 208)
            val netColor = if (summary.netBalance > 0.01) Color.rgb(194, 65, 12) else Color.rgb(22, 101, 52)
            drawKpiCard(
                xLeftBox,
                netBg,
                netBorder,
                netLabel,
                FormatUtils.formatCurrency(kotlin.math.abs(summary.netBalance), currency),
                netColor
            )
        }

        y = boxBottom + 18f

        if (mode == StatementMode.SUMMARY) {
            val summaryLines = if (summary.isCustomer) {
                listOf(
                    "عدد فواتير المبيعات للعميل:" to "${summary.invoicesCount} فاتورة",
                    "عدد سندات القبض والدفعات المستلمة:" to "${summary.paymentsCount} سند",
                    "سجلات الدين المفتوحة حالياً:" to "${summary.unsettledDebtsCount} سجل",
                    "إجمالي ما على العميل (فواتير وديون):" to FormatUtils.formatCurrency(summary.totalOnParty, currency),
                    "إجمالي ما للعميل (مبالغ مدفوعة ومحصلة):" to FormatUtils.formatCurrency(summary.totalForParty, currency),
                    "صافي الرصيد النهائي:" to when {
                        summary.netBalance > 0.01 -> "عليه ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
                        summary.netBalance < -0.01 -> "له فائض ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
                        else -> "الحساب خالص (0 $currency)"
                    }
                )
            } else {
                listOf(
                    "عدد فواتير المشتريات من المورد:" to "${summary.invoicesCount} فاتورة",
                    "عدد سندات الصرف والدفعات المسددة:" to "${summary.paymentsCount} سند",
                    "سجلات الدين المفتوحة للمورد:" to "${summary.unsettledDebtsCount} سجل",
                    "إجمالي ما للمورد (بضائع موردة وديون):" to FormatUtils.formatCurrency(summary.totalForParty, currency),
                    "إجمالي ما تم سداده للمورد:" to FormatUtils.formatCurrency(summary.totalOnParty, currency),
                    "صافي الرصيد النهائي:" to when {
                        summary.netBalance > 0.01 -> "باقي له بذمتنا ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
                        summary.netBalance < -0.01 -> "رصيد مدفوع مقدماً ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
                        else -> "حساب المورد خالص (0 $currency)"
                    }
                )
            }
            summaryLines.forEachIndexed { idx, (k, v) ->
                if (idx % 2 == 0) {
                    canvas.drawRect(24f, y - 13f, 516f, y + 9f, Paint().apply { color = Color.rgb(248, 250, 252) })
                }
                canvas.drawText(k, 506f, y, regularRightPaint)
                canvas.drawText(v, 34f, y, Paint(regularLeftPaint).apply {
                    typeface = if (idx == summaryLines.lastIndex) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                })
                y += 24f
            }
        } else {
            drawPartyTableHeader()
            if (summary.entries.isEmpty()) {
                canvas.drawText("لا توجد حركات مسجلة في هذا الحساب حتى الآن", pageWidth / 2f, y + 14f, subPaint)
                y += 32f
            } else {
                summary.entries.forEachIndexed { index, entry ->
                    val rowHeight = if (entry.subDetails.isNotBlank()) 28f else 20f
                    if (y + rowHeight > 715f) nextPartyPage(withTableHeader = true)

                    if (index % 2 == 1) {
                        canvas.drawRect(24f, y - 11f, 516f, y + rowHeight - 11f, Paint().apply { color = Color.rgb(248, 250, 252) })
                    }

                    canvas.drawText(FormatUtils.formatDate(entry.timestamp), 508f, y, smallMutedRightPaint)
                    canvas.drawText(clipPdfText(entry.title, 34), 436f, y, boldRightPaint.apply { textSize = 9.2f })
                    if (entry.subDetails.isNotBlank()) {
                        canvas.drawText(clipPdfText(entry.subDetails, 40), 436f, y + 11f, smallMutedRightPaint)
                    }

                    val cPaint = Paint(regularRightPaint).apply { textAlign = Paint.Align.CENTER; textSize = 9.2f }
                    if (entry.debit > 0.01) {
                        cPaint.color = if (summary.isCustomer) Color.rgb(185, 28, 28) else Color.rgb(4, 120, 87)
                        canvas.drawText(FormatUtils.formatNumber(entry.debit), 215f, y, cPaint)
                    } else {
                        cPaint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 215f, y, cPaint)
                    }
                    if (entry.credit > 0.01) {
                        cPaint.color = if (summary.isCustomer) Color.rgb(4, 120, 87) else Color.rgb(29, 78, 216)
                        canvas.drawText(FormatUtils.formatNumber(entry.credit), 140f, y, cPaint)
                    } else {
                        cPaint.color = Color.rgb(148, 163, 184)
                        canvas.drawText("-", 140f, y, cPaint)
                    }

                    val balLabel = when {
                        entry.runningBalance > 0.01 -> FormatUtils.formatNumber(entry.runningBalance)
                        entry.runningBalance < -0.01 -> "فائض ${FormatUtils.formatNumber(-entry.runningBalance)}"
                        else -> "0"
                    }
                    val bPaint = Paint(regularLeftPaint).apply {
                        textSize = 9.2f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(balLabel, 32f, y, bPaint)
                    y += rowHeight
                }
            }
        }

        if (y > 670f) nextPartyPage(withTableHeader = false)
        y += 8f
        canvas.drawRect(24f, y - 10f, 516f, y + 22f, Paint().apply { color = Color.rgb(241, 245, 249) })
        val finalRight = Paint(boldRightPaint).apply { textSize = 10f }
        val finalLeft = Paint(regularLeftPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10.5f
        }
        val finalText = if (summary.isCustomer) {
            "إجمالي عليه: ${FormatUtils.formatNumber(summary.totalOnParty)} | إجمالي له: ${FormatUtils.formatNumber(summary.totalForParty)}"
        } else {
            "إجمالي له: ${FormatUtils.formatNumber(summary.totalForParty)} | إجمالي المسدد: ${FormatUtils.formatNumber(summary.totalOnParty)}"
        }
        canvas.drawText(finalText, 506f, y + 9f, finalRight)
        val remainingText = when {
            summary.netBalance > 0.01 -> "الباقي: ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
            summary.netBalance < -0.01 -> "فائض: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
            else -> "خالص: 0 $currency"
        }
        canvas.drawText(remainingText, 34f, y + 9f, finalLeft)
        y += 40f

        canvas.drawText("توقيع الطرف الآخر: ________________", 508f, y, regularRightPaint)
        canvas.drawText("ختم وتوقيع المحل: ________________", 32f, y, regularLeftPaint)
        subPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("صفحة $pageNumber • تم استخراج الكشف آلياً من نظام $shopName", pageWidth / 2f, pageHeight - 20f, subPaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val safeParty = summary.party.name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        val prefix = if (mode == StatementMode.DETAILED) "كشف_حساب" else "تقرير_نهائي"
        val file = File(invoicesDir, "${prefix}_${safeParty}.pdf")
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
        val summary = StatementHelper.buildDailyReportSummary(
            periodTitle = periodTitle,
            filteredSales = sales,
            filteredPurchases = purchases,
            filteredExpenses = expenses,
            filteredEmployeeTx = employeeTx,
            filteredPayments = emptyList(),
            allDebts = emptyList(),
            allProducts = emptyList(),
            currentCashBalance = 0.0,
            currentExchangeBalance = 0.0
        )
        val topItems = sales.groupBy { it.productName }.map { (name, list) ->
            Triple(name, list.sumOf { it.quantity }, list.sumOf { it.profit })
        }.sortedByDescending { it.third }.take(10)
        val empSales = sales.filter { it.employeeName.isNotBlank() }.groupBy { it.employeeName }.map { (name, list) ->
            Triple(name, list.sumOf { it.totalAmount }, list.sumOf { it.profit })
        }.sortedByDescending { it.second }
        return generateFinancialReportPdf(context, summary, topItems, empSales, settings)
    }

    fun generateFinancialReportPdf(
        context: Context,
        report: DailyReportSummary,
        topItems: List<Triple<String, Int, Double>>,
        employeeSales: List<Triple<String, Double, Double>>,
        settings: StoreSettings?
    ): File {
        val pageWidth = 540
        val pageHeight = 780
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        val boldRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val regularRightPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val regularLeftPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
            textAlign = Paint.Align.LEFT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 34f

        fun nextReportPage() {
            subPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("صفحة $pageNumber — يتبع...", pageWidth / 2f, pageHeight - 20f, subPaint)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 32f
            canvas.drawText("تابع التقرير المالي الشامل — ${report.periodTitle} (صفحة $pageNumber)", 512f, y, boldRightPaint)
            y += 14f
            canvas.drawLine(24f, y, 516f, y, linePaint)
            y += 20f
        }

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 16f, size = 40f)
        canvas.drawText(shopName, pageWidth / 2f, y, titlePaint)
        y += 17f
        canvas.drawText("التقرير المالي والتشغيلي الشامل (${report.periodTitle})", pageWidth / 2f, y, subPaint)
        y += 14f
        canvas.drawText("تاريخ الاستخراج: ${FormatUtils.formatDateTime(report.generatedAt)}", pageWidth / 2f, y, subPaint)
        y += 14f
        canvas.drawLine(24f, y, 516f, y, linePaint)
        y += 18f

        val eqRows = listOf(
            "إجمالي المبيعات (${report.salesCount} فاتورة)" to FormatUtils.formatCurrency(report.totalSales, currency),
            "  • مبيعات نقدية (كاش)" to FormatUtils.formatCurrency(report.cashSales, currency),
            "  • مبيعات تحويل صرافة" to FormatUtils.formatCurrency(report.transferSales, currency),
            "  • مبيعات آجلة (بالدين)" to FormatUtils.formatCurrency(report.debtSales, currency),
            "تكلفة البضاعة المباعة" to "- ${FormatUtils.formatCurrency(report.cogs, currency)}",
            "مجمل الربح التجاري" to FormatUtils.formatCurrency(report.grossProfit, currency),
            "إجمالي المشتريات (${report.purchasesCount} فاتورة)" to FormatUtils.formatCurrency(report.totalPurchases, currency),
            "التحصيلات من العملاء (سندات قبض)" to "+ ${FormatUtils.formatCurrency(report.collectionsTotal, currency)}",
            "المدفوعات للموردين (سندات صرف)" to "- ${FormatUtils.formatCurrency(report.supplierPaymentsTotal, currency)}",
            "المصروفات التشغيلية" to "- ${FormatUtils.formatCurrency(report.expensesTotal, currency)}",
            "الرواتب والسلف والمكافآت" to "- ${FormatUtils.formatCurrency(report.salariesAndAdvancesTotal, currency)}"
        )

        eqRows.forEachIndexed { idx, (label, value) ->
            if (y > 700f) nextReportPage()
            if (idx % 2 == 0) {
                canvas.drawRect(24f, y - 11f, 516f, y + 9f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            canvas.drawText(label, 506f, y, regularRightPaint)
            canvas.drawText(value, 34f, y, regularLeftPaint)
            y += 20f
        }

        if (y > 670f) nextReportPage()
        y += 4f
        val netBg = if (report.netProfit >= 0) Color.rgb(240, 253, 244) else Color.rgb(254, 242, 242)
        val netColor = if (report.netProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(220, 38, 38)
        canvas.drawRoundRect(RectF(24f, y - 10f, 516f, y + 24f), 8f, 8f, Paint().apply { color = netBg })
        val nRight = Paint(boldRightPaint).apply { color = netColor; textSize = 12f }
        val nLeft = Paint(regularLeftPaint).apply { color = netColor; textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("صافي الربح الفعلي للفترة:", 506f, y + 11f, nRight)
        canvas.drawText(FormatUtils.formatCurrency(report.netProfit, currency), 34f, y + 11f, nLeft)
        y += 40f

        if (y > 640f) nextReportPage()
        canvas.drawText("المركز المالي والأرصدة الحالية:", 512f, y, boldRightPaint)
        y += 18f
        val posRows = listOf(
            "رصيد كاش الصندوق:" to FormatUtils.formatCurrency(report.currentCashBalance, currency),
            "أرصدة الصرافين والمحافظ:" to FormatUtils.formatCurrency(report.currentExchangeBalance, currency),
            "قيمة المخزون الحالي (بالتكلفة):" to FormatUtils.formatCurrency(report.inventoryCapital, currency),
            "لنا عند العملاء (إجمالي الديون):" to FormatUtils.formatCurrency(report.totalCustomerDebts, currency),
            "علينا للموردين (إجمالي الديون):" to FormatUtils.formatCurrency(report.totalSupplierDebts, currency)
        )
        posRows.forEachIndexed { idx, (label, value) ->
            if (idx % 2 == 0) {
                canvas.drawRect(24f, y - 11f, 516f, y + 9f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            canvas.drawText(label, 506f, y, regularRightPaint)
            canvas.drawText(value, 34f, y, regularLeftPaint)
            y += 19f
        }

        if (topItems.isNotEmpty()) {
            if (y > 670f) nextReportPage()
            y += 10f
            canvas.drawText("الأصناف الأكثر مبيعاً خلال الفترة:", 512f, y, boldRightPaint)
            y += 18f
            topItems.forEach { (name, qty, profit) ->
                if (y > 710f) nextReportPage()
                canvas.drawText("• ${clipPdfText(name, 36)} ($qty حبة)", 502f, y, regularRightPaint)
                canvas.drawText("ربح: ${FormatUtils.formatCurrency(profit, currency)}", 34f, y, regularLeftPaint)
                y += 18f
            }
        }

        if (employeeSales.isNotEmpty()) {
            if (y > 670f) nextReportPage()
            y += 10f
            canvas.drawText("أداء البائعين والموظفين:", 512f, y, boldRightPaint)
            y += 18f
            employeeSales.forEach { (empName, total, profit) ->
                if (y > 710f) nextReportPage()
                canvas.drawText("• ${clipPdfText(empName, 34)}", 502f, y, regularRightPaint)
                canvas.drawText("${FormatUtils.formatCurrency(total, currency)} (ربح: ${FormatUtils.formatNumber(profit)})", 34f, y, regularLeftPaint)
                y += 18f
            }
        }

        subPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("صفحة $pageNumber • تم استخراج التقرير آلياً من نظام $shopName", pageWidth / 2f, pageHeight - 20f, subPaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "reports")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val safePeriod = report.periodTitle.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        val file = File(invoicesDir, "تقرير_${safePeriod}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    fun generateCollectionVoucherPdf(
        context: Context,
        receipt: CollectionReceiptResult,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(440, 560, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(71, 85, 105)
            textSize = 11.5f
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency ?: "ريال"

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 18f, size = 40f)
        var y = 36f
        canvas.drawText(shopName, 220f, y, titlePaint)
        y += 18f
        val contactLine = listOfNotNull(
            shopAddress.takeIf { it.isNotBlank() },
            shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString(" • ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, 220f, y, subPaint)
            y += 18f
        } else {
            y += 6f
        }
        canvas.drawRect(24f, y - 16f, 416f, y + 12f, Paint().apply { color = Color.rgb(236, 253, 245) })
        val voucherHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(4, 120, 87)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("سند قبض وتحصيل زلط  •  #${receipt.voucherNumber}", 220f, y + 3f, voucherHeaderPaint)
        y += 32f

        canvas.drawLine(24f, y, 416f, y, linePaint)
        y += 24f

        fun drawRow(label: String, value: String) {
            canvas.drawText(label, 408f, y, labelPaint)
            canvas.drawText(value, 32f, y, valuePaint)
            y += 24f
        }

        drawRow("رقم السند:", receipt.voucherNumber)
        drawRow("التاريخ والوقت:", FormatUtils.formatDateTime(receipt.timestamp))
        drawRow("استلمنا من:", receipt.payerName)
        drawRow("طريقة التحصيل:", if (receipt.paymentMethod == "نقداً") "نقداً إلى الصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})")
        if (receipt.appliedToDebtAmount > 0.0) {
            drawRow("مسدد من حساب الدين:", FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency))
        }
        if (receipt.directCollectionAmount > 0.0) {
            drawRow("تحصيل مباشر / فائض:", FormatUtils.formatCurrency(receipt.directCollectionAmount, currency))
        }
        if (receipt.remainingCustomerDebt > 0.0) {
            drawRow("المتبقي على العميل:", FormatUtils.formatCurrency(receipt.remainingCustomerDebt, currency))
        }
        if (receipt.notes.isNotBlank()) {
            drawRow("البيان / الملاحظات:", receipt.notes)
        }

        y += 8f
        canvas.drawRect(24f, y - 14f, 416f, y + 26f, Paint().apply { color = Color.rgb(240, 253, 244) })
        val totalPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(22, 101, 52)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("المبلغ المستلم:", 404f, y + 10f, totalPaint)
        totalPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(FormatUtils.formatCurrency(receipt.amount, currency), 36f, y + 10f, totalPaint)
        y += 52f

        canvas.drawLine(24f, y, 416f, y, linePaint)
        y += 22f
        canvas.drawText("توقيع المستلم: ________________", 408f, y, labelPaint)
        y += 26f
        canvas.drawText("تم إصدار السند آلياً • $shopName", 220f, y, subPaint)

        pdfDocument.finishPage(page)

        val vouchersDir = File(context.cacheDir, "vouchers")
        if (!vouchersDir.exists()) vouchersDir.mkdirs()
        val safeName = receipt.voucherNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(vouchersDir, "سند_قبض_$safeName.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    fun generateSupplierPaymentVoucherPdf(
        context: Context,
        receipt: SupplierPaymentReceiptResult,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(440, 560, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 30, 54)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(71, 85, 105)
            textSize = 11.5f
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency ?: "ريال"

        drawStoreLogoIfPresent(canvas, settings, left = 24f, top = 18f, size = 40f)
        var y = 36f
        canvas.drawText(shopName, 220f, y, titlePaint)
        y += 18f
        val contactLine = listOfNotNull(
            shopAddress.takeIf { it.isNotBlank() },
            shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString(" • ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, 220f, y, subPaint)
            y += 18f
        } else {
            y += 6f
        }
        canvas.drawRect(24f, y - 16f, 416f, y + 12f, Paint().apply { color = Color.rgb(254, 242, 242) })
        val voucherHeaderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(185, 28, 28)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("سند صرف وتسديد لمورد  •  #${receipt.voucherNumber}", 220f, y + 3f, voucherHeaderPaint)
        y += 32f

        canvas.drawLine(24f, y, 416f, y, linePaint)
        y += 24f

        fun drawRow(label: String, value: String) {
            canvas.drawText(label, 408f, y, labelPaint)
            canvas.drawText(value, 32f, y, valuePaint)
            y += 24f
        }

        drawRow("رقم السند:", receipt.voucherNumber)
        drawRow("التاريخ والوقت:", FormatUtils.formatDateTime(receipt.timestamp))
        drawRow("صرفنا إلى المورد:", receipt.supplierName)
        drawRow("طريقة الصرف:", if (receipt.paymentMethod == "نقداً") "نقداً من الصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})")
        if (receipt.appliedToDebtAmount > 0.0) {
            drawRow("مسدد من الحساب الآجل:", FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency))
        }
        if (receipt.directPaymentAmount > 0.0) {
            drawRow("دفعة مباشرة / مقدمة:", FormatUtils.formatCurrency(receipt.directPaymentAmount, currency))
        }
        drawRow("المتبقي للمورد بذمتنا:", FormatUtils.formatCurrency(receipt.remainingSupplierDebt, currency))
        if (receipt.notes.isNotBlank()) {
            drawRow("البيان / الملاحظات:", receipt.notes)
        }

        y += 8f
        canvas.drawRect(24f, y - 14f, 416f, y + 26f, Paint().apply { color = Color.rgb(254, 242, 242) })
        val totalPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(185, 28, 28)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("المبلغ المسدد:", 404f, y + 10f, totalPaint)
        totalPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(FormatUtils.formatCurrency(receipt.amount, currency), 36f, y + 10f, totalPaint)
        y += 52f

        canvas.drawLine(24f, y, 416f, y, linePaint)
        y += 22f
        canvas.drawText("توقيع المستلم (المورد): ____________", 408f, y, labelPaint)
        y += 26f
        canvas.drawText("تم إصدار السند آلياً • $shopName", 220f, y, subPaint)

        pdfDocument.finishPage(page)

        val vouchersDir = File(context.cacheDir, "vouchers")
        if (!vouchersDir.exists()) vouchersDir.mkdirs()
        val safeName = receipt.voucherNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(vouchersDir, "سند_صرف_$safeName.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }
}
