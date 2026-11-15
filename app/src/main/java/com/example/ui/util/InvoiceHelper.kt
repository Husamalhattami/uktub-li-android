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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object InvoiceHelper {

    private const val PAGE_WIDTH = 540
    private const val PAGE_HEIGHT = 780
    private const val MARGIN_X = 24f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f) // 492f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - 44f // 736f

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

    private fun makeTextPaint(
        color: Int,
        textSize: Float,
        bold: Boolean = false
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        this.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun buildRtlStaticLayout(
        text: String,
        paint: TextPaint,
        maxWidth: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        val safeText = text.ifEmpty { " " }
        val safeWidth = maxWidth.toInt().coerceAtLeast(8)
        return StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, paint, safeWidth)
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(2f, 1.0f)
            .setIncludePad(false)
            .build()
    }

    private fun measureWrappedHeight(
        text: String,
        paint: TextPaint,
        maxWidth: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        if (text.isEmpty()) return 0f
        return buildRtlStaticLayout(text, paint, maxWidth, alignment).height.toFloat()
    }

    private fun drawWrappedLayout(
        canvas: Canvas,
        layout: StaticLayout,
        left: Float,
        top: Float
    ): Float {
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        maxWidth: Float,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): Float {
        if (text.isBlank()) return 0f
        val layout = buildRtlStaticLayout(text, paint, maxWidth, alignment)
        return drawWrappedLayout(canvas, layout, left, top)
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, shopName: String, continued: Boolean) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        val text = if (continued) {
            "صفحة $pageNumber — يتبع..."
        } else {
            "صفحة $pageNumber • تم إصداره آلياً من نظام $shopName"
        }
        canvas.drawText(text, PAGE_WIDTH / 2f, PAGE_HEIGHT - 18f, footerPaint)
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
                appendLine(name)
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
    // 2. Multi-Item Professional Sale Invoice PDF (Multi-Page + Wrapped)
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

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 11f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 10f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.8f, bold = false)
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val phone = settings?.phone?.ifBlank { "" } ?: ""
        val address = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val headerNote = settings?.invoiceHeader ?: ""
        val footer = settings?.invoiceFooter?.ifBlank {
            "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."
        } ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 26f

        fun drawSaleTableHeader() {
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f, Paint().apply { color = Color.rgb(15, 30, 54) })
            headerCellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("الصنف والمواصفات", 508f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الكمية", 232f, y + 16f, headerCellPaint)
            canvas.drawText("سعر الوحدة", 160f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الإجمالي", 32f, y + 16f, headerCellPaint)
            y += 28f
        }

        fun nextSalePage(withTableHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع فاتورة مبيعات: $invoiceNumber (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            )
            y += 8f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 12f
            if (withTableHeader) drawSaleTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 18f, size = 44f)
        val headerWidth = CONTENT_WIDTH - 104f
        val headerLeft = MARGIN_X + 52f
        y += drawWrappedText(canvas, shopName, headerLeft, y, headerWidth, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        if (headerNote.isNotBlank()) {
            y += drawWrappedText(canvas, headerNote, headerLeft, y, headerWidth, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        }
        val contactLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            y += drawWrappedText(canvas, contactLine, headerLeft, y, headerWidth, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        y = y.coerceAtLeast(68f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f

        // Meta box
        val rightMetaH = measureWrappedHeight(
            "فاتورة مبيعات: $invoiceNumber",
            boldTp,
            250f,
            Layout.Alignment.ALIGN_NORMAL
        )
        val leftMetaH = measureWrappedHeight(
            "${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}",
            regularTp,
            220f,
            Layout.Alignment.ALIGN_OPPOSITE
        )
        drawWrappedText(canvas, "فاتورة مبيعات: $invoiceNumber", 266f, y, 250f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(
            canvas,
            "${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}",
            MARGIN_X,
            y,
            225f,
            regularTp,
            Layout.Alignment.ALIGN_OPPOSITE
        )
        y += maxOf(rightMetaH, leftMetaH) + 6f

        val custText = "العميل: ${customerName.ifBlank { "زبون نقدي" }}${if (customerPhone.isNotBlank()) " ($customerPhone)" else ""}"
        val empText = "البائع: ${employeeName.ifBlank { "صاحب المحل" }}"
        val custH = measureWrappedHeight(custText, regularTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val empH = measureWrappedHeight(empText, regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, custText, 266f, y, 250f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, empText, MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(custH, empH) + 6f

        val payText = "طريقة الدفع: $paymentType"
        val exText = if (exchangeAccountName.isNotBlank()) {
            "الصراف: $exchangeAccountName${if (transferRef.isNotBlank()) " (#$transferRef)" else ""}"
        } else ""
        val payH = measureWrappedHeight(payText, regularTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val exH = if (exText.isNotBlank()) measureWrappedHeight(exText, regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE) else 0f
        drawWrappedText(canvas, payText, 266f, y, 250f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        if (exText.isNotBlank()) {
            drawWrappedText(canvas, exText, MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        }
        y += maxOf(payH, exH) + 10f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

        drawSaleTableHeader()

        items.forEachIndexed { index, item ->
            val nameLayout = buildRtlStaticLayout(item.name, regularTp, 244f, Layout.Alignment.ALIGN_NORMAL)
            val detailsLayout = if (item.details.isNotBlank()) {
                buildRtlStaticLayout(item.details, mutedSmallTp, 244f, Layout.Alignment.ALIGN_NORMAL)
            } else null
            val qtyLayout = buildRtlStaticLayout("${item.quantity} ${item.unit}", regularTp, 54f, Layout.Alignment.ALIGN_CENTER)
            val unitPriceLayout = buildRtlStaticLayout(FormatUtils.formatNumber(item.unitPrice), regularTp, 72f, Layout.Alignment.ALIGN_CENTER)
            val totalLayout = buildRtlStaticLayout("${FormatUtils.formatNumber(item.total)} $currency", boldTp, 92f, Layout.Alignment.ALIGN_OPPOSITE)

            val textBlockH = nameLayout.height + (if (detailsLayout != null) detailsLayout.height + 3 else 0)
            val rowContentH = maxOf(textBlockH, qtyLayout.height, unitPriceLayout.height, totalLayout.height).toFloat()
            val rowHeight = rowContentH + 10f

            if (y + rowHeight > CONTENT_BOTTOM) {
                nextSalePage(withTableHeader = true)
            }

            if (index % 2 == 1) {
                canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowHeight - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }

            drawWrappedLayout(canvas, nameLayout, 264f, y)
            if (detailsLayout != null) {
                drawWrappedLayout(canvas, detailsLayout, 264f, y + nameLayout.height + 3f)
            }
            drawWrappedLayout(canvas, qtyLayout, 205f, y)
            drawWrappedLayout(canvas, unitPriceLayout, 124f, y)
            drawWrappedLayout(canvas, totalLayout, 30f, y)

            y += rowHeight
            canvas.drawLine(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y - 3f, Paint().apply {
                color = Color.rgb(241, 245, 249)
                strokeWidth = 0.8f
            })
        }

        val summaryNeeded = 155f + (if (discount > 0) 20f else 0f) + (if (previousDebt > 0.01) 46f else 0f)
        if (y + summaryNeeded > CONTENT_BOTTOM) {
            nextSalePage(withTableHeader = false)
        }

        y += 8f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

        fun drawSummaryRow(label: String, value: String, labelPaint: TextPaint = regularTp, valPaint: TextPaint = boldTp) {
            val lH = measureWrappedHeight(label, labelPaint, 280f, Layout.Alignment.ALIGN_NORMAL)
            val vH = measureWrappedHeight(value, valPaint, 180f, Layout.Alignment.ALIGN_OPPOSITE)
            if (y + maxOf(lH, vH) + 8f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
            drawWrappedText(canvas, label, 230f, y, 280f, labelPaint, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 32f, y, 180f, valPaint, Layout.Alignment.ALIGN_OPPOSITE)
            y += maxOf(lH, vH) + 7f
        }

        drawSummaryRow("إجمالي الأصناف:", FormatUtils.formatCurrency(subtotal, currency))
        if (discount > 0) {
            val discTp = makeTextPaint(Color.rgb(217, 119, 6), 10f, bold = true)
            drawSummaryRow("الخصم الممنوح (-):", "- ${FormatUtils.formatCurrency(discount, currency)}", discTp, discTp)
        }

        if (y + 34f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 30f), 6f, 6f, Paint().apply { color = Color.rgb(240, 253, 244) })
        val netTp = makeTextPaint(Color.rgb(22, 101, 52), 13f, bold = true)
        drawWrappedText(canvas, "الصافي المطلوب:", 250f, y + 7f, 258f, netTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(netTotal, currency), 32f, y + 7f, 200f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 38f

        if (cashPaid > 0) {
            drawSummaryRow("المدفوع نقداً (كاش):", FormatUtils.formatCurrency(cashPaid, currency))
        }
        if (transferPaid > 0) {
            drawSummaryRow("المدفوع تحويل صرافة:", FormatUtils.formatCurrency(transferPaid, currency))
        }
        if (debtAmount > 0) {
            val debtTp = makeTextPaint(Color.rgb(220, 38, 38), 10.5f, bold = true)
            drawSummaryRow("المبلغ المقيد ديناً على العميل:", FormatUtils.formatCurrency(debtAmount, currency), debtTp, debtTp)
        }
        if (previousDebt > 0.01) {
            val totalRemaining = previousDebt + debtAmount
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            drawSummaryRow("الرصيد السابق للعميل:", FormatUtils.formatCurrency(previousDebt, currency), subTp, regularTp)
            drawSummaryRow("إجمالي الرصيد المستحق بذمة العميل:", FormatUtils.formatCurrency(totalRemaining, currency), boldTp, boldTp)
        }

        if (y + 52f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
        y += 6f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f
        y += drawWrappedText(canvas, footer, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        drawWrappedText(canvas, "شكراً لتسوقكم معنا  •  نسعد بخدمتكم دائماً", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER)

        drawPageFooter(canvas, pageNumber, shopName, continued = false)
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
    // 3. Purchase Invoice PDF (Multi-Page + Wrapped)
    // ==========================================

    fun generatePurchaseInvoicePdf(
        context: Context,
        purchase: PurchaseRecord,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10.5f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 11f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 10.5f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        fun nextPage() {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 18f, size = 40f)
        var y = 26f
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        y += drawWrappedText(canvas, "سند قيد فاتورة مشتريات بضاعة للمستودع", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        y = y.coerceAtLeast(66f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 14f

        fun drawKeyValueRow(label: String, value: String, valueTp: TextPaint = regularTp) {
            val lH = measureWrappedHeight(label, boldTp, 170f, Layout.Alignment.ALIGN_NORMAL)
            val vH = measureWrappedHeight(value, valueTp, 300f, Layout.Alignment.ALIGN_OPPOSITE)
            val h = maxOf(lH, vH) + 10f
            if (y + h > CONTENT_BOTTOM) {
                nextPage()
                y = 32f
            }
            drawWrappedText(canvas, label, 344f, y, 168f, boldTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, MARGIN_X, y, 300f, valueTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += h
        }

        val refStr = if (purchase.invoiceNumber.isNotBlank()) " | المرجع: ${purchase.invoiceNumber}" else ""
        drawKeyValueRow("رقم الفاتورة:", "#${purchase.id}$refStr", boldTp)
        drawKeyValueRow("تاريخ الفاتورة:", FormatUtils.formatDateTime(purchase.timestamp))
        drawKeyValueRow("المورد:", purchase.supplierName, boldTp)
        drawKeyValueRow("طريقة الدفع:", purchase.paymentMethod)

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

        drawKeyValueRow("الصنف والقسم:", "${purchase.modelName} (${purchase.category})", boldTp)
        drawKeyValueRow("المقاس واللون:", "${purchase.size} | ${purchase.color}")
        val unitLabel = if (purchase.unit == "حبه") "${purchase.quantity} حبة" else "${purchase.unitCount.toInt()} ${purchase.unit} (${purchase.quantity} حبة)"
        drawKeyValueRow("الكمية المستلمة:", unitLabel)
        drawKeyValueRow("تكلفة شراء الحبة:", FormatUtils.formatCurrency(purchase.unitBuyPrice, currency))
        drawKeyValueRow("سعر البيع المقترح:", FormatUtils.formatCurrency(purchase.unitSellPrice, currency))

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

        drawKeyValueRow("إجمالي تكلفة الفاتورة:", FormatUtils.formatCurrency(purchase.totalCost, currency), boldTp)
        drawKeyValueRow("المبلغ المدفوع للمورد:", FormatUtils.formatCurrency(purchase.paidAmount, currency))
        if (purchase.remainingDebt > 0) {
            val debtTp = makeTextPaint(Color.rgb(220, 38, 38), 11f, bold = true)
            drawKeyValueRow("المتبقي دين مستحق للمورد:", FormatUtils.formatCurrency(purchase.remainingDebt, currency), debtTp)
        }
        if (purchase.notes.isNotBlank()) {
            y += 6f
            val notesH = measureWrappedHeight("ملاحظات: ${purchase.notes}", subTp, CONTENT_WIDTH, Layout.Alignment.ALIGN_NORMAL)
            if (y + notesH > CONTENT_BOTTOM) {
                nextPage()
                y = 32f
            }
            y += drawWrappedText(canvas, "ملاحظات: ${purchase.notes}", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_NORMAL) + 8f
        }

        drawPageFooter(canvas, pageNumber, shopName, continued = false)
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
        val pdfDocument = PdfDocument()

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 11f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 10f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.8f, bold = false)
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val phone = settings?.phone ?: ""
        val address = settings?.address ?: ""

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 26f

        fun drawPurchaseTableHeader() {
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f, Paint().apply { color = Color.rgb(15, 30, 54) })
            headerCellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("الصنف والمواصفات", 508f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الكمية / الوحدة", 232f, y + 16f, headerCellPaint)
            canvas.drawText("سعر الشراء", 154f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الإجمالي", 32f, y + 16f, headerCellPaint)
            y += 28f
        }

        fun nextPurchasePage(withTableHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع فاتورة مشتريات: $invoiceNumber (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 8f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 12f
            if (withTableHeader) drawPurchaseTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 18f, size = 42f)
        val headerLeft = MARGIN_X + 52f
        val headerW = CONTENT_WIDTH - 104f
        y += drawWrappedText(canvas, shopName, headerLeft, y, headerW, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        y += drawWrappedText(canvas, "سند قيد فاتورة مشتريات واستلام بضاعة للمستودع", headerLeft, y, headerW, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        val contactLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            y += drawWrappedText(canvas, contactLine, headerLeft, y, headerW, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        y = y.coerceAtLeast(66f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f

        val h1 = measureWrappedHeight("فاتورة مشتريات: $invoiceNumber", boldTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val h2 = measureWrappedHeight("${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "فاتورة مشتريات: $invoiceNumber", 266f, y, 250f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "${FormatUtils.formatDate(timestamp)} - ${FormatUtils.formatTime(timestamp)}", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(h1, h2) + 6f

        val sH1 = measureWrappedHeight("المورد: ${supplierName.ifBlank { "مورد عام" }}", boldTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val sH2 = measureWrappedHeight("طريقة الدفع: $paymentMethod", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "المورد: ${supplierName.ifBlank { "مورد عام" }}", 266f, y, 250f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "طريقة الدفع: $paymentMethod", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(sH1, sH2) + 6f

        if (exchangeAccountName.isNotBlank() || transferRef.isNotBlank()) {
            val eH1 = if (exchangeAccountName.isNotBlank()) measureWrappedHeight("حساب الصراف: $exchangeAccountName", regularTp, 250f, Layout.Alignment.ALIGN_NORMAL) else 0f
            val eH2 = if (transferRef.isNotBlank()) measureWrappedHeight("رقم الحوالة: #$transferRef", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE) else 0f
            if (exchangeAccountName.isNotBlank()) {
                drawWrappedText(canvas, "حساب الصراف: $exchangeAccountName", 266f, y, 250f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            }
            if (transferRef.isNotBlank()) {
                drawWrappedText(canvas, "رقم الحوالة: #$transferRef", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            }
            y += maxOf(eH1, eH2) + 8f
        }

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f
        drawPurchaseTableHeader()

        items.forEachIndexed { index, item ->
            val titleLayout = buildRtlStaticLayout("${item.modelName} (${item.category})", regularTp, 232f, Layout.Alignment.ALIGN_NORMAL)
            val specLayout = buildRtlStaticLayout("المقاس: ${item.size} | اللون: ${item.color}", mutedSmallTp, 232f, Layout.Alignment.ALIGN_NORMAL)
            val unitStr = if (item.unit == "حبه") "${item.totalPieces} حبة" else "${item.unitCount.toInt()} ${item.unit} (${item.totalPieces} حبة)"
            val unitLayout = buildRtlStaticLayout(unitStr, regularTp, 72f, Layout.Alignment.ALIGN_CENTER)
            val buyLayout = buildRtlStaticLayout(FormatUtils.formatNumber(item.buyPricePerUnit), regularTp, 68f, Layout.Alignment.ALIGN_CENTER)
            val totalLayout = buildRtlStaticLayout("${FormatUtils.formatNumber(item.lineCost)} $currency", boldTp, 88f, Layout.Alignment.ALIGN_OPPOSITE)

            val leftBlockH = titleLayout.height + 3 + specLayout.height
            val rowH = maxOf(leftBlockH, unitLayout.height, buyLayout.height, totalLayout.height).toFloat() + 10f

            if (y + rowH > CONTENT_BOTTOM) {
                nextPurchasePage(withTableHeader = true)
            }

            if (index % 2 == 1) {
                canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }

            drawWrappedLayout(canvas, titleLayout, 276f, y)
            drawWrappedLayout(canvas, specLayout, 276f, y + titleLayout.height + 3f)
            drawWrappedLayout(canvas, unitLayout, 198f, y)
            drawWrappedLayout(canvas, buyLayout, 122f, y)
            drawWrappedLayout(canvas, totalLayout, 28f, y)
            y += rowH
            canvas.drawLine(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y - 3f, Paint().apply {
                color = Color.rgb(241, 245, 249)
                strokeWidth = 0.8f
            })
        }

        if (y + 130f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += 8f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

        fun drawTotalLine(label: String, value: String, tp: TextPaint = regularTp, valTp: TextPaint = boldTp) {
            val lh = measureWrappedHeight(label, tp, 280f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, valTp, 180f, Layout.Alignment.ALIGN_OPPOSITE)
            if (y + maxOf(lh, vh) + 8f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
            drawWrappedText(canvas, label, 230f, y, 280f, tp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 32f, y, 180f, valTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += maxOf(lh, vh) + 7f
        }

        drawTotalLine("إجمالي تكلفة المشتريات (${items.size} أصناف):", FormatUtils.formatCurrency(totalCost, currency), boldTp, boldTp)
        if (cashPaid > 0) {
            drawTotalLine("المدفوع نقداً من الصندوق:", FormatUtils.formatCurrency(cashPaid, currency))
        }
        if (transferPaid > 0) {
            drawTotalLine("المدفوع تحويل عبر الصرافة:", FormatUtils.formatCurrency(transferPaid, currency))
        }
        if (debtAmount > 0) {
            val debtTp = makeTextPaint(Color.rgb(220, 38, 38), 11f, bold = true)
            drawTotalLine("المبلغ المقيد ديناً للمورد (عليا زلط):", FormatUtils.formatCurrency(debtAmount, currency), debtTp, debtTp)
        }

        if (y + 42f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += 6f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f
        drawWrappedText(canvas, "تم استلام وإدخال الأصناف بنجاح لمخزون المستودع", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER)

        drawPageFooter(canvas, pageNumber, shopName, continued = false)
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
    // 4. Daily Cash Drawer & Exchange Movement Statement PDF (Multi-Page, No Truncation)
    // ==========================================

    private data class DailyLedgerLine(
        val timestamp: Long,
        val title: String,
        val accountLabel: String,
        val isIncome: Boolean,
        val amount: Double
    )

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

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10.5f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val incomeTp = makeTextPaint(Color.rgb(22, 163, 74), 9.5f, bold = true)
        val expenseTp = makeTextPaint(Color.rgb(220, 38, 38), 9.5f, bold = true)
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val activeTransfers = transfers.filter { !it.isReversed && !it.isReversalEntry }
        val ledgerRows = buildList {
            movements.forEach { m ->
                add(
                    DailyLedgerLine(
                        timestamp = m.timestamp,
                        title = m.title + (if (m.details.isNotBlank()) " — ${m.details}" else ""),
                        accountLabel = "الصندوق",
                        isIncome = m.isIncome,
                        amount = m.amount
                    )
                )
            }
            activeTransfers.forEach { t ->
                val isIn = t.type == "إيداع"
                add(
                    DailyLedgerLine(
                        timestamp = t.timestamp,
                        title = "${t.type}: ${t.notes.ifBlank { t.accountName }}" + (if (t.referenceNumber.isNotBlank()) " (#${t.referenceNumber})" else ""),
                        accountLabel = t.accountName.ifBlank { "صرافة" },
                        isIncome = isIn,
                        amount = t.amount
                    )
                )
            }
        }.sortedByDescending { it.timestamp }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 26f

        fun drawMovementHeader() {
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f, Paint().apply { color = Color.rgb(15, 30, 54) })
            headerCellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ والبيان", 508f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الحساب", 225f, y + 16f, headerCellPaint)
            canvas.drawText("النوع", 150f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("المبلغ", 32f, y + 16f, headerCellPaint)
            y += 28f
        }

        fun nextMovementPage(withHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع كشف حركة الصندوق والحسابات ($periodTitle) — صفحة $pageNumber",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 8f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            if (withHeader) drawMovementHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 16f, size = 40f)
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        y += drawWrappedText(canvas, "كشف حركة الصندوق والحسابات ($periodTitle)", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        y += drawWrappedText(canvas, "تاريخ التقرير: ${FormatUtils.formatDate(System.currentTimeMillis())} • إجمالي الحركات: ${ledgerRows.size}", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f

        // Balances strip
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 36f), 6f, 6f, Paint().apply { color = Color.rgb(241, 245, 249) })
        drawWrappedText(canvas, "رصيد كاش الصندوق: ${FormatUtils.formatCurrency(currentCash, currency)}", 275f, y + 10f, 232f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "أرصدة الصرافة: ${FormatUtils.formatCurrency(currentExchange, currency)}", 32f, y + 10f, 232f, boldTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 46f

        drawMovementHeader()

        if (ledgerRows.isEmpty()) {
            y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذه الفترة", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
        } else {
            ledgerRows.forEachIndexed { index, row ->
                val descText = "${FormatUtils.formatDateTime(row.timestamp)} • ${row.title}"
                val descLayout = buildRtlStaticLayout(descText, regularTp, 242f, Layout.Alignment.ALIGN_NORMAL)
                val accLayout = buildRtlStaticLayout(row.accountLabel, regularTp, 68f, Layout.Alignment.ALIGN_CENTER)
                val typePaint = if (row.isIncome) incomeTp else expenseTp
                val typeLayout = buildRtlStaticLayout(if (row.isIncome) "مقبوضات" else "مدفوعات", typePaint, 56f, Layout.Alignment.ALIGN_CENTER)
                val amountText = (if (row.isIncome) "+ " else "- ") + FormatUtils.formatNumber(row.amount) + " $currency"
                val amtLayout = buildRtlStaticLayout(amountText, typePaint, 86f, Layout.Alignment.ALIGN_OPPOSITE)

                val rowH = maxOf(descLayout.height, accLayout.height, typeLayout.height, amtLayout.height).toFloat() + 10f
                if (y + rowH > CONTENT_BOTTOM) {
                    nextMovementPage(withHeader = true)
                }

                if (index % 2 == 1) {
                    canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
                }

                drawWrappedLayout(canvas, descLayout, 266f, y)
                drawWrappedLayout(canvas, accLayout, 191f, y)
                drawWrappedLayout(canvas, typeLayout, 122f, y)
                drawWrappedLayout(canvas, amtLayout, 30f, y)
                y += rowH
            }
        }

        if (y + 32f > CONTENT_BOTTOM) nextMovementPage(withHeader = false)
        y += 8f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f
        drawWrappedText(canvas, "نهاية كشف الحركة  •  تم استخراجه آلياً من نظام $shopName", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER)

        drawPageFooter(canvas, pageNumber, shopName, continued = false)
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
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val emp = summary.employee

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10.5f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.5f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 26f

        fun drawEmpTableHeader() {
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 22f, Paint().apply { color = Color.rgb(15, 30, 54) })
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ", 508f, y + 15f, thPaint)
            canvas.drawText("البيان والحركة", 435f, y + 15f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("له (+)", 215f, y + 15f, thPaint)
            canvas.drawText("عليه (-)", 135f, y + 15f, thPaint)
            thPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الرصيد", 32f, y + 15f, thPaint)
            y += 26f
        }

        fun nextEmpPage(withTableHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع كشف حساب العامل: ${emp.name} (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 6f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            if (withTableHeader) drawEmpTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 16f, size = 40f)
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        val docTitle = if (mode == StatementMode.DETAILED) "كشف حساب عامل مفصل بجميع العمليات" else "تقرير نهائي لمستحقات وحساب عامل"
        y += drawWrappedText(canvas, docTitle, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        val contact = listOfNotNull(shopAddress.takeIf { it.isNotBlank() }, shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }).joinToString(" • ")
        if (contact.isNotBlank()) {
            y += drawWrappedText(canvas, contact, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        y = y.coerceAtLeast(64f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f

        val hEmp1 = measureWrappedHeight("العامل: ${emp.name} (${emp.role})", boldTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val hEmp2 = measureWrappedHeight("تاريخ الكشف: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "العامل: ${emp.name} (${emp.role})", 266f, y, 250f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "تاريخ الكشف: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(hEmp1, hEmp2) + 5f

        val hSal1 = measureWrappedHeight("الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)} (${emp.salaryType})", regularTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val hSal2 = measureWrappedHeight("مبيعاته: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)} (${emp.salaryType})", 266f, y, 250f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "مبيعاته: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(hSal1, hSal2) + 10f

        // 3 Summary Cards
        val boxTop = y
        val boxBottom = y + 48f
        val colWidth = (CONTENT_WIDTH - 16f) / 3f
        val xBox3 = MARGIN_X
        val xBox2 = xBox3 + colWidth + 8f
        val xBox1 = xBox2 + colWidth + 8f

        fun drawSummaryBox(left: Float, bg: Int, border: Int, label: String, amount: String, amountColor: Int) {
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply { color = bg })
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 8.5f
                textAlign = Paint.Align.CENTER
            }
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amountColor
                textSize = 11f
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

        y = boxBottom + 14f

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
                val lh = measureWrappedHeight(label, regularTp, 270f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(value, regularTp, 200f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 10f
                if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
                if (idx % 2 == 0) {
                    canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
                }
                drawWrappedText(canvas, label, 240f, y, 270f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, value, 32f, y, 200f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
            y += 6f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 12f
            val netTp = makeTextPaint(remColor, 11.5f, bold = true)
            drawWrappedText(canvas, "صافي المستحق المتبقي للعامل:", 250f, y, 260f, boldTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, FormatUtils.formatCurrency(summary.remainingBalance, currency), 32f, y, 200f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += 26f
        } else {
            drawEmpTableHeader()
            if (summary.entries.isEmpty()) {
                y += drawWrappedText(canvas, "لا توجد حركات مسجلة لهذا العامل حتى الآن", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
            } else {
                val creditTp = makeTextPaint(Color.rgb(4, 120, 87), 9.2f, bold = true)
                val debitTp = makeTextPaint(Color.rgb(185, 28, 28), 9.2f, bold = true)
                val mutedCenterTp = makeTextPaint(Color.rgb(148, 163, 184), 9.2f, bold = false)

                summary.entries.forEachIndexed { index, entry ->
                    val dateLayout = buildRtlStaticLayout(FormatUtils.formatDate(entry.timestamp), mutedSmallTp, 68f, Layout.Alignment.ALIGN_NORMAL)
                    val descLayout = buildRtlStaticLayout("${entry.type} • ${entry.title}", regularTp, 185f, Layout.Alignment.ALIGN_NORMAL)
                    val crLayout = if (entry.creditEarned > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.creditEarned), creditTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", mutedCenterTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val dbLayout = if (entry.debitTaken > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.debitTaken), debitTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", mutedCenterTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val balLayout = buildRtlStaticLayout(FormatUtils.formatNumber(entry.runningBalance), boldTp, 72f, Layout.Alignment.ALIGN_OPPOSITE)

                    val rowH = maxOf(dateLayout.height, descLayout.height, crLayout.height, dbLayout.height, balLayout.height).toFloat() + 10f
                    if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = true)

                    if (index % 2 == 1) {
                        canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
                    }

                    drawWrappedLayout(canvas, dateLayout, 444f, y)
                    drawWrappedLayout(canvas, descLayout, 252f, y)
                    drawWrappedLayout(canvas, crLayout, 182f, y)
                    drawWrappedLayout(canvas, dbLayout, 102f, y)
                    drawWrappedLayout(canvas, balLayout, 28f, y)
                    y += rowH
                }
            }
        }

        if (y + 52f > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
        y += 10f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 16f
        drawWrappedText(canvas, "توقيع العامل: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "اعتماد الإدارة: ________________", 32f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawPageFooter(canvas, pageNumber, shopName, continued = false)

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
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.2f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 26f

        fun drawPartyTableHeader() {
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 22f, Paint().apply { color = Color.rgb(15, 30, 54) })
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ", 508f, y + 15f, thPaint)
            canvas.drawText("البيان والتفاصيل", 436f, y + 15f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (summary.isCustomer) "عليه" else "مسدد له", 215f, y + 15f, thPaint)
            canvas.drawText(if (summary.isCustomer) "له (سداد)" else "له (توريد)", 140f, y + 15f, thPaint)
            thPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("الرصيد", 32f, y + 15f, thPaint)
            y += 26f
        }

        fun nextPartyPage(withTableHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع كشف حساب ${if (summary.isCustomer) "العميل" else "المورد"}: ${summary.party.name} (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 6f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            if (withTableHeader) drawPartyTableHeader()
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 16f, size = 40f)
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        val docTitle = when {
            summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب عميل تفصيلي بجميع العمليات"
            summary.isCustomer && mode == StatementMode.SUMMARY -> "تقرير نهائي لحساب العميل"
            !summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب مورد تفصيلي بجميع العمليات"
            else -> "تقرير نهائي لحساب المورد"
        }
        y += drawWrappedText(canvas, docTitle, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        val contact = listOfNotNull(shopAddress.takeIf { it.isNotBlank() }, shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }).joinToString(" • ")
        if (contact.isNotBlank()) {
            y += drawWrappedText(canvas, contact, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        y = y.coerceAtLeast(64f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 10f

        val roleTitle = if (summary.isCustomer) "العميل" else "المورد"
        val pH1 = measureWrappedHeight("$roleTitle: ${summary.party.name}", boldTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val pH2 = measureWrappedHeight("تاريخ الإصدار: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "$roleTitle: ${summary.party.name}", 266f, y, 250f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "تاريخ الإصدار: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(pH1, pH2) + 5f

        val partyPhoneStr = if (summary.party.phone.isNotBlank()) "هاتف $roleTitle: ${summary.party.phone}" else "إجمالي العمليات: ${summary.entries.size}"
        val sH1 = measureWrappedHeight(partyPhoneStr, regularTp, 250f, Layout.Alignment.ALIGN_NORMAL)
        val sH2 = measureWrappedHeight("فواتير: ${summary.invoicesCount} | سندات: ${summary.paymentsCount}", regularTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, partyPhoneStr, 266f, y, 250f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "فواتير: ${summary.invoicesCount} | سندات: ${summary.paymentsCount}", MARGIN_X, y, 225f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(sH1, sH2) + 10f

        // 3 Summary Cards
        val boxTop = y
        val boxBottom = y + 50f
        val colWidth = (CONTENT_WIDTH - 16f) / 3f
        val xLeftBox = MARGIN_X
        val xMidBox = xLeftBox + colWidth + 8f
        val xRightBox = xMidBox + colWidth + 8f

        fun drawKpiCard(left: Float, bg: Int, border: Int, title: String, value: String, valueColor: Int) {
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply { color = bg })
            canvas.drawRoundRect(RectF(left, boxTop, left + colWidth, boxBottom), 8f, 8f, Paint().apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            val lP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 8.5f
                textAlign = Paint.Align.CENTER
            }
            val vP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                textSize = 11f
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

        y = boxBottom + 14f

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
                val valTp = if (idx == summaryLines.lastIndex) boldTp else regularTp
                val lh = measureWrappedHeight(k, regularTp, 270f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(v, valTp, 200f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 10f
                if (y + rowH > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
                if (idx % 2 == 0) {
                    canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
                }
                drawWrappedText(canvas, k, 240f, y, 270f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, v, 32f, y, 200f, valTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
        } else {
            drawPartyTableHeader()
            if (summary.entries.isEmpty()) {
                y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذا الحساب حتى الآن", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
            } else {
                val entryTitleTp = makeTextPaint(Color.rgb(15, 23, 42), 9.2f, bold = true)
                val debitTp = makeTextPaint(if (summary.isCustomer) Color.rgb(185, 28, 28) else Color.rgb(4, 120, 87), 9.2f, bold = true)
                val creditTp = makeTextPaint(if (summary.isCustomer) Color.rgb(4, 120, 87) else Color.rgb(29, 78, 216), 9.2f, bold = true)
                val dashTp = makeTextPaint(Color.rgb(148, 163, 184), 9.2f, bold = false)
                val balTp = makeTextPaint(Color.rgb(15, 23, 42), 9.2f, bold = true)

                summary.entries.forEachIndexed { index, entry ->
                    val dateLayout = buildRtlStaticLayout(FormatUtils.formatDate(entry.timestamp), mutedSmallTp, 68f, Layout.Alignment.ALIGN_NORMAL)
                    val titleLayout = buildRtlStaticLayout(entry.title, entryTitleTp, 184f, Layout.Alignment.ALIGN_NORMAL)
                    val subLayout = if (entry.subDetails.isNotBlank()) {
                        buildRtlStaticLayout(entry.subDetails, mutedSmallTp, 184f, Layout.Alignment.ALIGN_NORMAL)
                    } else null
                    val debLayout = if (entry.debit > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.debit), debitTp, 68f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", dashTp, 68f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val credLayout = if (entry.credit > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.credit), creditTp, 68f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", dashTp, 68f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val balLabel = when {
                        entry.runningBalance > 0.01 -> FormatUtils.formatNumber(entry.runningBalance)
                        entry.runningBalance < -0.01 -> "فائض ${FormatUtils.formatNumber(-entry.runningBalance)}"
                        else -> "0"
                    }
                    val balLayout = buildRtlStaticLayout(balLabel, balTp, 72f, Layout.Alignment.ALIGN_OPPOSITE)

                    val centerBlockH = titleLayout.height + (if (subLayout != null) subLayout.height + 2 else 0)
                    val rowHeight = maxOf(dateLayout.height, centerBlockH, debLayout.height, credLayout.height, balLayout.height).toFloat() + 10f

                    if (y + rowHeight > CONTENT_BOTTOM) nextPartyPage(withTableHeader = true)

                    if (index % 2 == 1) {
                        canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowHeight - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
                    }

                    drawWrappedLayout(canvas, dateLayout, 444f, y)
                    drawWrappedLayout(canvas, titleLayout, 252f, y)
                    if (subLayout != null) {
                        drawWrappedLayout(canvas, subLayout, 252f, y + titleLayout.height + 2f)
                    }
                    drawWrappedLayout(canvas, debLayout, 181f, y)
                    drawWrappedLayout(canvas, credLayout, 106f, y)
                    drawWrappedLayout(canvas, balLayout, 28f, y)

                    y += rowHeight
                }
            }
        }

        if (y + 88f > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
        y += 8f
        canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 32f, Paint().apply { color = Color.rgb(241, 245, 249) })
        val finalText = if (summary.isCustomer) {
            "إجمالي عليه: ${FormatUtils.formatNumber(summary.totalOnParty)} | إجمالي له: ${FormatUtils.formatNumber(summary.totalForParty)}"
        } else {
            "إجمالي له: ${FormatUtils.formatNumber(summary.totalForParty)} | إجمالي المسدد: ${FormatUtils.formatNumber(summary.totalOnParty)}"
        }
        val remainingText = when {
            summary.netBalance > 0.01 -> "الباقي: ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
            summary.netBalance < -0.01 -> "فائض: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
            else -> "خالص: 0 $currency"
        }
        drawWrappedText(canvas, finalText, 210f, y + 8f, 298f, boldTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, remainingText, 32f, y + 8f, 175f, boldTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 46f

        drawWrappedText(canvas, "توقيع الطرف الآخر: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", 32f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawPageFooter(canvas, pageNumber, shopName, continued = false)

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
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10.5f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 26f

        fun nextReportPage() {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع التقرير المالي الشامل — ${report.periodTitle} (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 6f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 12f
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 16f, size = 40f)
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        y += drawWrappedText(canvas, "التقرير المالي والتشغيلي الشامل (${report.periodTitle})", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        y += drawWrappedText(canvas, "تاريخ الاستخراج: ${FormatUtils.formatDateTime(report.generatedAt)}", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        y = y.coerceAtLeast(64f)
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 12f

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
            val lh = measureWrappedHeight(label, regularTp, 280f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, regularTp, 180f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 9f
            if (y + rowH > CONTENT_BOTTOM) nextReportPage()
            if (idx % 2 == 0) {
                canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            drawWrappedText(canvas, label, 228f, y, 280f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 32f, y, 180f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (y + 52f > CONTENT_BOTTOM) nextReportPage()
        y += 6f
        val netBg = if (report.netProfit >= 0) Color.rgb(240, 253, 244) else Color.rgb(254, 242, 242)
        val netColor = if (report.netProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(220, 38, 38)
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 32f), 8f, 8f, Paint().apply { color = netBg })
        val netTp = makeTextPaint(netColor, 12f, bold = true)
        drawWrappedText(canvas, "صافي الربح الفعلي للفترة:", 236f, y + 8f, 270f, netTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(report.netProfit, currency), 32f, y + 8f, 190f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 44f

        if (y + 120f > CONTENT_BOTTOM) nextReportPage()
        y += drawWrappedText(canvas, "المركز المالي والأرصدة الحالية:", MARGIN_X, y, CONTENT_WIDTH, boldTp, Layout.Alignment.ALIGN_NORMAL) + 8f
        val posRows = listOf(
            "رصيد كاش الصندوق:" to FormatUtils.formatCurrency(report.currentCashBalance, currency),
            "أرصدة الصرافين والمحافظ:" to FormatUtils.formatCurrency(report.currentExchangeBalance, currency),
            "قيمة المخزون الحالي (بالتكلفة):" to FormatUtils.formatCurrency(report.inventoryCapital, currency),
            "لنا عند العملاء (إجمالي الديون):" to FormatUtils.formatCurrency(report.totalCustomerDebts, currency),
            "علينا للموردين (إجمالي الديون):" to FormatUtils.formatCurrency(report.totalSupplierDebts, currency)
        )
        posRows.forEachIndexed { idx, (label, value) ->
            val lh = measureWrappedHeight(label, regularTp, 280f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, regularTp, 180f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 9f
            if (y + rowH > CONTENT_BOTTOM) nextReportPage()
            if (idx % 2 == 0) {
                canvas.drawRect(MARGIN_X, y - 3f, PAGE_WIDTH - MARGIN_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            drawWrappedText(canvas, label, 228f, y, 280f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 32f, y, 180f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (topItems.isNotEmpty()) {
            if (y + 44f > CONTENT_BOTTOM) nextReportPage()
            y += 10f
            y += drawWrappedText(canvas, "الأصناف الأكثر مبيعاً خلال الفترة:", MARGIN_X, y, CONTENT_WIDTH, boldTp, Layout.Alignment.ALIGN_NORMAL) + 6f
            topItems.forEach { (name, qty, profit) ->
                val lText = "• $name ($qty حبة)"
                val rText = "ربح: ${FormatUtils.formatCurrency(profit, currency)}"
                val lh = measureWrappedHeight(lText, regularTp, 300f, Layout.Alignment.ALIGN_NORMAL)
                val rh = measureWrappedHeight(rText, regularTp, 168f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, rh) + 8f
                if (y + rowH > CONTENT_BOTTOM) nextReportPage()
                drawWrappedText(canvas, lText, 206f, y, 300f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, rText, 32f, y, 168f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
        }

        if (employeeSales.isNotEmpty()) {
            if (y + 44f > CONTENT_BOTTOM) nextReportPage()
            y += 10f
            y += drawWrappedText(canvas, "أداء البائعين والموظفين:", MARGIN_X, y, CONTENT_WIDTH, boldTp, Layout.Alignment.ALIGN_NORMAL) + 6f
            employeeSales.forEach { (empName, total, profit) ->
                val lText = "• $empName"
                val rText = "${FormatUtils.formatCurrency(total, currency)} (ربح: ${FormatUtils.formatNumber(profit)})"
                val lh = measureWrappedHeight(lText, regularTp, 260f, Layout.Alignment.ALIGN_NORMAL)
                val rh = measureWrappedHeight(rText, regularTp, 208f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, rh) + 8f
                if (y + rowH > CONTENT_BOTTOM) nextReportPage()
                drawWrappedText(canvas, lText, 246f, y, 260f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, rText, 32f, y, 208f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
        }

        drawPageFooter(canvas, pageNumber, shopName, continued = false)
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
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 18f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10.5f, bold = false)
        val labelTp = makeTextPaint(Color.rgb(71, 85, 105), 11f, bold = false)
        val valueTp = makeTextPaint(Color.rgb(15, 23, 42), 11.5f, bold = true)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 18f, size = 40f)
        var y = 26f
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        val contactLine = listOfNotNull(
            shopAddress.takeIf { it.isNotBlank() },
            shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString(" • ")
        if (contactLine.isNotBlank()) {
            y += drawWrappedText(canvas, contactLine, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 6f
        }
        y = y.coerceAtLeast(66f)
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 30f), 6f, 6f, Paint().apply { color = Color.rgb(236, 253, 245) })
        val voucherHeaderTp = makeTextPaint(Color.rgb(4, 120, 87), 13.5f, bold = true)
        drawWrappedText(canvas, "سند قبض وتحصيل زلط  •  #${receipt.voucherNumber}", MARGIN_X, y + 7f, CONTENT_WIDTH, voucherHeaderTp, Layout.Alignment.ALIGN_CENTER)
        y += 40f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 14f

        fun drawRow(label: String, value: String) {
            val lh = measureWrappedHeight(label, labelTp, 175f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, valueTp, 300f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 12f
            if (y + rowH > CONTENT_BOTTOM) {
                drawPageFooter(canvas, pageNumber, shopName, continued = true)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 32f
            }
            drawWrappedText(canvas, label, 336f, y, 175f, labelTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, MARGIN_X, y, 300f, valueTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
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

        if (y + 96f > CONTENT_BOTTOM) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 32f
        }

        y += 8f
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 36f), 6f, 6f, Paint().apply { color = Color.rgb(240, 253, 244) })
        val totalTp = makeTextPaint(Color.rgb(22, 101, 52), 14.5f, bold = true)
        drawWrappedText(canvas, "المبلغ المستلم:", 260f, y + 9f, 246f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(receipt.amount, currency), 34f, y + 9f, 210f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 50f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 16f
        drawWrappedText(canvas, "توقيع المستلم: ________________", 280f, y, 228f, labelTp, Layout.Alignment.ALIGN_NORMAL)
        drawPageFooter(canvas, pageNumber, shopName, continued = false)

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
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 18f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 10.5f, bold = false)
        val labelTp = makeTextPaint(Color.rgb(71, 85, 105), 11f, bold = false)
        val valueTp = makeTextPaint(Color.rgb(15, 23, 42), 11.5f, bold = true)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopPhone = settings?.phone?.ifBlank { "" } ?: ""
        val shopAddress = settings?.address?.ifBlank { "" } ?: ""
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 18f, size = 40f)
        var y = 26f
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 4f
        val contactLine = listOfNotNull(
            shopAddress.takeIf { it.isNotBlank() },
            shopPhone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" }
        ).joinToString(" • ")
        if (contactLine.isNotBlank()) {
            y += drawWrappedText(canvas, contactLine, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 6f
        }
        y = y.coerceAtLeast(66f)
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 30f), 6f, 6f, Paint().apply { color = Color.rgb(254, 242, 242) })
        val voucherHeaderTp = makeTextPaint(Color.rgb(185, 28, 28), 13.5f, bold = true)
        drawWrappedText(canvas, "سند صرف وتسديد لمورد  •  #${receipt.voucherNumber}", MARGIN_X, y + 7f, CONTENT_WIDTH, voucherHeaderTp, Layout.Alignment.ALIGN_CENTER)
        y += 40f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 14f

        fun drawRow(label: String, value: String) {
            val lh = measureWrappedHeight(label, labelTp, 175f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, valueTp, 300f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 12f
            if (y + rowH > CONTENT_BOTTOM) {
                drawPageFooter(canvas, pageNumber, shopName, continued = true)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 32f
            }
            drawWrappedText(canvas, label, 336f, y, 175f, labelTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, MARGIN_X, y, 300f, valueTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
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

        if (y + 96f > CONTENT_BOTTOM) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 32f
        }

        y += 8f
        canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 36f), 6f, 6f, Paint().apply { color = Color.rgb(254, 242, 242) })
        val totalTp = makeTextPaint(Color.rgb(185, 28, 28), 14.5f, bold = true)
        drawWrappedText(canvas, "المبلغ المسدد:", 260f, y + 9f, 246f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(receipt.amount, currency), 34f, y + 9f, 210f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 50f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 16f
        drawWrappedText(canvas, "توقيع المستلم (المورد): ____________", 260f, y, 248f, labelTp, Layout.Alignment.ALIGN_NORMAL)
        drawPageFooter(canvas, pageNumber, shopName, continued = false)

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
