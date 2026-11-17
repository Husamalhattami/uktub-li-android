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

            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            printManager.print(jobName, printAdapter, attrs)
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
    // 2. Multi-Item Professional Sale Invoice PDF (Framed, Multi-Page, Aligned RTL Table)
    // ==========================================

    data class PdfSaleLineItem(
        val name: String,
        val details: String,
        val quantity: Int,
        val unitPrice: Double,
        val total: Double,
        val unit: String = "حبه"
    )

    private fun drawFramedInvoiceHeader(
        canvas: Canvas,
        settings: StoreSettings?,
        documentTitle: String,
        documentNumber: String,
        accentColor: Int,
        bannerBg: Int
    ): Float {
        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val phone = settings?.phone?.trim().orEmpty()
        val address = settings?.address?.trim().orEmpty()
        val headerNote = settings?.invoiceHeader?.trim().orEmpty()
        val taxNo = settings?.taxNumber?.trim().orEmpty()

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 17f, bold = true)
        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 9.5f, bold = false)

        // Top Navy Accent Bar
        canvas.drawRoundRect(
            RectF(MARGIN_X, 14f, PAGE_WIDTH - MARGIN_X, 20f),
            3f, 3f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X + 4f, top = 26f, size = 44f)

        val headerWidth = CONTENT_WIDTH - 108f
        val headerLeft = MARGIN_X + 54f
        var y = 26f
        y += drawWrappedText(canvas, shopName, headerLeft, y, headerWidth, titleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        if (headerNote.isNotBlank()) {
            y += drawWrappedText(canvas, headerNote, headerLeft, y, headerWidth, subTp, Layout.Alignment.ALIGN_CENTER) + 2f
        }
        val contactLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" },
            taxNo.takeIf { it.isNotBlank() }?.let { "الرقم الضريبي: $it" }
        ).joinToString("  •  ")
        if (contactLine.isNotBlank()) {
            y += drawWrappedText(canvas, contactLine, headerLeft, y, headerWidth, subTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        y = y.coerceAtLeast(76f)

        // Framed Document Banner
        val bannerRect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 30f)
        canvas.drawRoundRect(bannerRect, 6f, 6f, Paint().apply { color = bannerBg })
        canvas.drawRoundRect(
            bannerRect, 6f, 6f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = accentColor
                alpha = 90
            }
        )
        val bannerTp = makeTextPaint(accentColor, 12.5f, bold = true)
        drawWrappedText(
            canvas,
            "$documentTitle  •  $documentNumber",
            MARGIN_X + 10f,
            y + 7f,
            CONTENT_WIDTH - 20f,
            bannerTp,
            Layout.Alignment.ALIGN_CENTER
        )
        return y + 38f
    }

    private fun drawMetaCard(
        canvas: Canvas,
        startY: Float,
        rows: List<Pair<String, String>>
    ): Float {
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.8f, bold = false)

        val rowHeights = rows.map { (rightTxt, leftTxt) ->
            val rH = measureWrappedHeight(rightTxt, boldTp, 234f, Layout.Alignment.ALIGN_NORMAL)
            val lH = if (leftTxt.isNotBlank()) measureWrappedHeight(leftTxt, regularTp, 224f, Layout.Alignment.ALIGN_OPPOSITE) else 0f
            maxOf(rH, lH) + 6f
        }
        val boxHeight = rowHeights.sum() + 12f
        val rect = RectF(MARGIN_X, startY, PAGE_WIDTH - MARGIN_X, startY + boxHeight)
        canvas.drawRoundRect(rect, 7f, 7f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            rect, 7f, 7f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        var cy = startY + 8f
        rows.forEachIndexed { i, (rightTxt, leftTxt) ->
            drawWrappedText(canvas, rightTxt, 272f, cy, 234f, boldTp, Layout.Alignment.ALIGN_NORMAL)
            if (leftTxt.isNotBlank()) {
                drawWrappedText(canvas, leftTxt, MARGIN_X + 10f, cy, 224f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            }
            cy += rowHeights[i]
        }
        return startY + boxHeight + 10f
    }

    private fun drawAlignedFiveColHeader(
        canvas: Canvas,
        y: Float,
        col3Title: String,
        col4Title: String
    ): Float {
        canvas.drawRoundRect(
            RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f),
            4f, 4f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        thPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("#", 502f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف والمواصفات", 482f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(col3Title, 221f, y + 15.5f, thPaint)
        canvas.drawText(col4Title, 149f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", 32f, y + 15.5f, thPaint)
        val hdrDividerPaint = Paint().apply {
            color = Color.argb(65, 255, 255, 255)
            strokeWidth = 0.8f
        }
        listOf(112f, 186f, 256f, 488f).forEach { dx ->
            canvas.drawLine(dx, y + 3f, dx, y + 21f, hdrDividerPaint)
        }
        return y + 26f
    }

    private fun drawGridRowBorders(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        dividersX: List<Float>,
        isOdd: Boolean
    ) {
        if (isOdd) {
            canvas.drawRect(MARGIN_X, top, PAGE_WIDTH - MARGIN_X, bottom, Paint().apply { color = Color.rgb(248, 250, 252) })
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_X, top, PAGE_WIDTH - MARGIN_X, top, borderPaint)
        canvas.drawLine(MARGIN_X, top, MARGIN_X, bottom, borderPaint)
        dividersX.forEach { dx ->
            canvas.drawLine(dx, top, dx, bottom, borderPaint)
        }
        canvas.drawLine(PAGE_WIDTH - MARGIN_X, top, PAGE_WIDTH - MARGIN_X, bottom, borderPaint)
        canvas.drawLine(MARGIN_X, bottom, PAGE_WIDTH - MARGIN_X, bottom, borderPaint)
    }

    private fun drawRowGridBorders(canvas: Canvas, top: Float, bottom: Float, isOdd: Boolean) {
        drawGridRowBorders(
            canvas = canvas,
            top = top,
            bottom = bottom,
            dividersX = listOf(112f, 186f, 256f, 488f),
            isOdd = isOdd
        )
    }

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

        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 9.5f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.2f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val footer = settings?.invoiceFooter?.ifBlank {
            "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."
        } ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y: Float

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
            ) + 8f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            if (withTableHeader) {
                y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الوحدة")
            }
        }

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "فاتورة مبيعات",
            documentNumber = invoiceNumber,
            accentColor = Color.rgb(15, 30, 54),
            bannerBg = Color.rgb(241, 245, 249)
        )

        val metaRows = buildList {
            add(
                "العميل: ${customerName.ifBlank { "زبون نقدي" }}${if (customerPhone.isNotBlank()) " ($customerPhone)" else ""}" to
                    "التاريخ: ${FormatUtils.formatDateTime(timestamp)}"
            )
            add(
                "طريقة الدفع: $paymentType" to
                    "البائع: ${employeeName.ifBlank { "صاحب المحل" }}"
            )
            if (exchangeAccountName.isNotBlank() || transferRef.isNotBlank()) {
                val exLabel = if (exchangeAccountName.isNotBlank()) "الصراف: $exchangeAccountName" else ""
                val refLabel = if (transferRef.isNotBlank()) "رقم الحوالة: #$transferRef" else ""
                add(exLabel to refLabel)
            }
        }
        y = drawMetaCard(canvas, y, metaRows)
        y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الوحدة")

        items.forEachIndexed { index, item ->
            val idxLayout = buildRtlStaticLayout("${index + 1}", regularTp, 22f, Layout.Alignment.ALIGN_CENTER)
            val nameLayout = buildRtlStaticLayout(item.name, boldTp, 220f, Layout.Alignment.ALIGN_NORMAL)
            val detailsLayout = if (item.details.isNotBlank()) {
                buildRtlStaticLayout(item.details, mutedSmallTp, 220f, Layout.Alignment.ALIGN_NORMAL)
            } else null
            val qtyLayout = buildRtlStaticLayout("${item.quantity} ${item.unit}", regularTp, 62f, Layout.Alignment.ALIGN_CENTER)
            val unitPriceLayout = buildRtlStaticLayout(FormatUtils.formatNumber(item.unitPrice), regularTp, 66f, Layout.Alignment.ALIGN_CENTER)
            val totalLayout = buildRtlStaticLayout("${FormatUtils.formatNumber(item.total)} $currency", boldTp, 80f, Layout.Alignment.ALIGN_OPPOSITE)

            val textBlockH = nameLayout.height + (if (detailsLayout != null) detailsLayout.height + 2 else 0)
            val rowContentH = maxOf(idxLayout.height, textBlockH, qtyLayout.height, unitPriceLayout.height, totalLayout.height).toFloat()
            val rowHeight = rowContentH + 12f

            if (y + rowHeight > CONTENT_BOTTOM) {
                nextSalePage(withTableHeader = true)
            }

            drawRowGridBorders(canvas, y, y + rowHeight, isOdd = index % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxLayout, 491f, cellY)
            drawWrappedLayout(canvas, nameLayout, 262f, cellY)
            if (detailsLayout != null) {
                drawWrappedLayout(canvas, detailsLayout, 262f, cellY + nameLayout.height + 2f)
            }
            drawWrappedLayout(canvas, qtyLayout, 190f, cellY)
            drawWrappedLayout(canvas, unitPriceLayout, 116f, cellY)
            drawWrappedLayout(canvas, totalLayout, 28f, cellY)

            y += rowHeight
        }

        val summaryNeeded = 175f + (if (discount > 0) 22f else 0f) + (if (previousDebt > 0.01) 48f else 0f)
        if (y + summaryNeeded > CONTENT_BOTTOM) {
            nextSalePage(withTableHeader = false)
        }

        y += 12f
        val summaryStart = y
        val summaryCardRows = mutableListOf<Triple<String, String, Int>>()
        summaryCardRows.add(Triple("إجمالي الأصناف:", FormatUtils.formatCurrency(subtotal, currency), Color.rgb(15, 23, 42)))
        if (discount > 0) {
            summaryCardRows.add(Triple("الخصم الممنوح (-):", "- ${FormatUtils.formatCurrency(discount, currency)}", Color.rgb(217, 119, 6)))
        }
        if (cashPaid > 0.01) {
            summaryCardRows.add(Triple("المدفوع نقداً (كاش):", FormatUtils.formatCurrency(cashPaid, currency), Color.rgb(22, 101, 52)))
        }
        if (transferPaid > 0.01) {
            summaryCardRows.add(Triple("المدفوع تحويل صرافة:", FormatUtils.formatCurrency(transferPaid, currency), Color.rgb(15, 118, 110)))
        }
        if (debtAmount > 0.01) {
            summaryCardRows.add(Triple("المبلغ المقيد ديناً على العميل:", FormatUtils.formatCurrency(debtAmount, currency), Color.rgb(220, 38, 38)))
        }
        if (previousDebt > 0.01) {
            summaryCardRows.add(Triple("الرصيد السابق للعميل:", FormatUtils.formatCurrency(previousDebt, currency), Color.rgb(100, 116, 139)))
            summaryCardRows.add(Triple("إجمالي الرصيد المستحق بذمة العميل:", FormatUtils.formatCurrency(previousDebt + debtAmount, currency), Color.rgb(15, 30, 54)))
        }

        val cardH = 44f + (summaryCardRows.size * 22f) + 12f
        val sumRect = RectF(MARGIN_X, summaryStart, PAGE_WIDTH - MARGIN_X, summaryStart + cardH)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        // Highlighted Net Total Pill inside Summary Box
        var sy = summaryStart + 10f
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sy, PAGE_WIDTH - MARGIN_X - 10f, sy + 30f),
            6f, 6f,
            Paint().apply { color = Color.rgb(220, 252, 231) }
        )
        val netTp = makeTextPaint(Color.rgb(22, 101, 52), 12.5f, bold = true)
        drawWrappedText(canvas, "الصافي المطلوب للفاتورة:", 260f, sy + 7f, 240f, netTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(netTotal, currency), MARGIN_X + 18f, sy + 7f, 210f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 38f

        summaryCardRows.forEach { (k, v, col) ->
            val kTp = makeTextPaint(Color.rgb(51, 65, 85), 9.8f, bold = false)
            val vTp = makeTextPaint(col, 10f, bold = true)
            drawWrappedText(canvas, k, 250f, sy, 250f, kTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 200f, vTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += 22f
        }
        y = summaryStart + cardH + 12f

        if (y + 56f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
        y += drawWrappedText(canvas, footer, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 6f
        drawWrappedText(canvas, "توقيع العميل: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 8f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)

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
    // 3. Purchase Invoice PDF (Single & Multi-Item — Unified Framed Layout)
    // ==========================================

    fun generatePurchaseInvoicePdf(
        context: Context,
        purchase: PurchaseRecord,
        settings: StoreSettings?
    ): File {
        val invNo = purchase.invoiceNumber.ifBlank { "PUR-${purchase.id}" }
        val mult = ClothingUnits.getMultiplier(purchase.unit).coerceAtLeast(1)
        val count = purchase.unitCount.takeIf { it > 0.0 } ?: (purchase.quantity.toDouble() / mult)
        val item = CartPurchaseItem(
            category = purchase.category,
            modelName = purchase.modelName,
            color = purchase.color,
            size = purchase.size,
            unit = purchase.unit,
            unitCount = count,
            buyPricePerUnit = purchase.unitBuyPrice * mult,
            sellPricePerUnit = purchase.unitSellPrice * mult,
            colorsCount = purchase.colorsCount,
            notes = purchase.notes
        )
        return generateMultiItemPurchaseInvoicePdf(
            context = context,
            invoiceNumber = invNo,
            supplierName = purchase.supplierName,
            paymentMethod = purchase.paymentMethod,
            exchangeAccountName = purchase.exchangeAccountName,
            transferRef = "",
            items = listOf(item),
            totalCost = purchase.totalCost,
            cashPaid = purchase.cashPaid,
            transferPaid = purchase.transferPaid,
            debtAmount = purchase.remainingDebt,
            timestamp = purchase.timestamp,
            settings = settings,
            notes = purchase.notes
        )
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
        settings: StoreSettings?,
        notes: String = ""
    ): File {
        val pdfDocument = PdfDocument()

        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 9.5f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.2f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y: Float

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
            y += 10f
            if (withTableHeader) {
                y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الشراء")
            }
        }

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "فاتورة مشتريات واستلام بضاعة",
            documentNumber = invoiceNumber,
            accentColor = Color.rgb(29, 78, 216),
            bannerBg = Color.rgb(239, 246, 255)
        )

        val metaRows = buildList {
            add(
                "المورد / المعمل: ${supplierName.ifBlank { "مورد عام" }}" to
                    "التاريخ: ${FormatUtils.formatDateTime(timestamp)}"
            )
            add(
                "طريقة السداد: $paymentMethod" to
                    "عدد الأصناف: ${items.size} صنف"
            )
            if (exchangeAccountName.isNotBlank() || transferRef.isNotBlank()) {
                val exLabel = if (exchangeAccountName.isNotBlank()) "حساب الصراف: $exchangeAccountName" else ""
                val refLabel = if (transferRef.isNotBlank()) "رقم الحوالة: #$transferRef" else ""
                add(exLabel to refLabel)
            }
        }
        y = drawMetaCard(canvas, y, metaRows)
        y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الشراء")

        items.forEachIndexed { index, item ->
            val idxLayout = buildRtlStaticLayout("${index + 1}", regularTp, 22f, Layout.Alignment.ALIGN_CENTER)
            val titleLayout = buildRtlStaticLayout("${item.modelName} (${item.category})", boldTp, 220f, Layout.Alignment.ALIGN_NORMAL)
            val specText = buildString {
                append("المقاس: ${item.size} | اللون: ${item.color}")
                if (item.sellPricePerUnit > 0) {
                    append(" • بيع مقترح: ${FormatUtils.formatNumber(item.sellPricePerUnit)}")
                }
            }
            val specLayout = buildRtlStaticLayout(specText, mutedSmallTp, 220f, Layout.Alignment.ALIGN_NORMAL)
            val unitStr = if (item.unit == "حبه") "${item.totalPieces} حبة" else "${item.unitCount.toInt()} ${item.unit} (${item.totalPieces} حبة)"
            val unitLayout = buildRtlStaticLayout(unitStr, regularTp, 62f, Layout.Alignment.ALIGN_CENTER)
            val buyLayout = buildRtlStaticLayout(FormatUtils.formatNumber(item.buyPricePerUnit), regularTp, 66f, Layout.Alignment.ALIGN_CENTER)
            val totalLayout = buildRtlStaticLayout("${FormatUtils.formatNumber(item.lineCost)} $currency", boldTp, 80f, Layout.Alignment.ALIGN_OPPOSITE)

            val centerBlockH = titleLayout.height + 2 + specLayout.height
            val rowH = maxOf(idxLayout.height, centerBlockH, unitLayout.height, buyLayout.height, totalLayout.height).toFloat() + 12f

            if (y + rowH > CONTENT_BOTTOM) {
                nextPurchasePage(withTableHeader = true)
            }

            drawRowGridBorders(canvas, y, y + rowH, isOdd = index % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxLayout, 491f, cellY)
            drawWrappedLayout(canvas, titleLayout, 262f, cellY)
            drawWrappedLayout(canvas, specLayout, 262f, cellY + titleLayout.height + 2f)
            drawWrappedLayout(canvas, unitLayout, 190f, cellY)
            drawWrappedLayout(canvas, buyLayout, 116f, cellY)
            drawWrappedLayout(canvas, totalLayout, 28f, cellY)
            y += rowH
        }

        val summaryRows = mutableListOf<Triple<String, String, Int>>()
        if (cashPaid > 0.01) {
            summaryRows.add(Triple("المدفوع نقداً من الصندوق:", FormatUtils.formatCurrency(cashPaid, currency), Color.rgb(22, 101, 52)))
        }
        if (transferPaid > 0.01) {
            summaryRows.add(Triple("المدفوع تحويل عبر الصرافة:", FormatUtils.formatCurrency(transferPaid, currency), Color.rgb(15, 118, 110)))
        }
        if (debtAmount > 0.01) {
            summaryRows.add(Triple("المتبقي دين مستحق للمورد:", FormatUtils.formatCurrency(debtAmount, currency), Color.rgb(220, 38, 38)))
        }
        if (notes.isNotBlank()) {
            summaryRows.add(Triple("ملاحظات الفاتورة:", notes, Color.rgb(51, 65, 85)))
        }

        val kTp = makeTextPaint(Color.rgb(51, 65, 85), 9.8f, bold = false)
        val measuredSummaryRows = summaryRows.map { (k, v, col) ->
            val vTp = makeTextPaint(col, 10f, bold = true)
            val kh = measureWrappedHeight(k, kTp, 245f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(v, vTp, 200f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(kh, vh) + 8f
            Triple(k, v to vTp, rowH)
        }
        val dynamicRowsHeight = measuredSummaryRows.sumOf { it.third.toDouble() }.toFloat()
        val neededSummaryHeight = 44f + dynamicRowsHeight + 70f
        if (y + neededSummaryHeight > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += 12f

        val sumStart = y
        val sumH = 44f + dynamicRowsHeight + 12f
        val sumRect = RectF(MARGIN_X, sumStart, PAGE_WIDTH - MARGIN_X, sumStart + sumH)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        var sy = sumStart + 10f
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sy, PAGE_WIDTH - MARGIN_X - 10f, sy + 30f),
            6f, 6f,
            Paint().apply { color = Color.rgb(239, 246, 255) }
        )
        val totalPurchaseTp = makeTextPaint(Color.rgb(29, 78, 216), 12.5f, bold = true)
        drawWrappedText(canvas, "إجمالي تكلفة المشتريات (${items.sumOf { it.totalPieces }} حبة):", 245f, sy + 7f, 255f, totalPurchaseTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(totalCost, currency), MARGIN_X + 18f, sy + 7f, 195f, totalPurchaseTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 38f

        measuredSummaryRows.forEach { (k, vPair, rowH) ->
            val (v, vTp) = vPair
            drawWrappedText(canvas, k, 255f, sy, 245f, kTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 200f, vTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += rowH
        }
        y = sumStart + sumH + 12f

        if (y + 52f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += drawWrappedText(canvas, "تم استلام ومطابقة البضاعة وإدخالها للمستودع بنجاح", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        drawWrappedText(canvas, "توقيع المورد: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "أمين المستودع / المدير: ________________", MARGIN_X + 8f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)

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
                val isIn = t.isCredit
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
        var y = 0f

        val movementDividers = listOf(114f, 182f, 260f)

        fun drawMovementHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            headerCellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("التاريخ والبيان", 508f, y + 15.5f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الحساب", 221f, y + 15.5f, headerCellPaint)
            canvas.drawText("النوع", 148f, y + 15.5f, headerCellPaint)
            canvas.drawText("المبلغ", 69f, y + 15.5f, headerCellPaint)
            val hdrDivider = Paint().apply {
                color = Color.argb(65, 255, 255, 255)
                strokeWidth = 0.8f
            }
            movementDividers.forEach { dx -> canvas.drawLine(dx, y + 3f, dx, y + 21f, hdrDivider) }
            y += 26f
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

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "كشف حركة الصندوق والحسابات",
            documentNumber = periodTitle,
            accentColor = Color.rgb(15, 30, 54),
            bannerBg = Color.rgb(241, 245, 249)
        )
        y = drawMetaCard(
            canvas = canvas,
            startY = y,
            rows = listOf(
                "تاريخ التقرير: ${FormatUtils.formatDateTime(System.currentTimeMillis())}" to "إجمالي الحركات: ${ledgerRows.size} حركة",
                "رصيد كاش الصندوق: ${FormatUtils.formatCurrency(currentCash, currency)}" to "أرصدة الصرافة: ${FormatUtils.formatCurrency(currentExchange, currency)}"
            )
        )

        drawMovementHeader()

        if (ledgerRows.isEmpty()) {
            y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذه الفترة", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
        } else {
            ledgerRows.forEachIndexed { index, row ->
                val descText = "${FormatUtils.formatDateTime(row.timestamp)} • ${row.title}"
                val descLayout = buildRtlStaticLayout(descText, regularTp, 244f, Layout.Alignment.ALIGN_NORMAL)
                val accLayout = buildRtlStaticLayout(row.accountLabel, regularTp, 68f, Layout.Alignment.ALIGN_CENTER)
                val typePaint = if (row.isIncome) incomeTp else expenseTp
                val typeLayout = buildRtlStaticLayout(if (row.isIncome) "مقبوضات" else "مدفوعات", typePaint, 58f, Layout.Alignment.ALIGN_CENTER)
                val amountText = (if (row.isIncome) "+ " else "- ") + FormatUtils.formatNumber(row.amount) + " $currency"
                val amtLayout = buildRtlStaticLayout(amountText, typePaint, 82f, Layout.Alignment.ALIGN_OPPOSITE)

                val rowH = maxOf(descLayout.height, accLayout.height, typeLayout.height, amtLayout.height).toFloat() + 12f
                if (y + rowH > CONTENT_BOTTOM) {
                    nextMovementPage(withHeader = true)
                }

                drawGridRowBorders(canvas, y, y + rowH, movementDividers, isOdd = index % 2 == 1)

                val cellY = y + 6f
                drawWrappedLayout(canvas, descLayout, 266f, cellY)
                drawWrappedLayout(canvas, accLayout, 187f, cellY)
                drawWrappedLayout(canvas, typeLayout, 119f, cellY)
                drawWrappedLayout(canvas, amtLayout, 28f, cellY)
                y += rowH
            }
        }

        if (y + 34f > CONTENT_BOTTOM) nextMovementPage(withHeader = false)
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
        var y = 0f
        val empDividers = listOf(102f, 176f, 250f, 438f)

        fun drawEmpTableHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("التاريخ", 477f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("البيان والحركة", 432f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("له (+)", 213f, y + 15.5f, thPaint)
            canvas.drawText("عليه (-)", 139f, y + 15.5f, thPaint)
            canvas.drawText("الرصيد", 63f, y + 15.5f, thPaint)
            val hdrDivider = Paint().apply {
                color = Color.argb(65, 255, 255, 255)
                strokeWidth = 0.8f
            }
            empDividers.forEach { dx -> canvas.drawLine(dx, y + 3f, dx, y + 21f, hdrDivider) }
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

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = if (mode == StatementMode.DETAILED) "كشف حساب عامل تفصيلي" else "تقرير نهائي لمستحقات عامل",
            documentNumber = emp.name,
            accentColor = Color.rgb(15, 30, 54),
            bannerBg = Color.rgb(241, 245, 249)
        )
        y = drawMetaCard(
            canvas = canvas,
            startY = y,
            rows = listOf(
                "العامل: ${emp.name} (${emp.role})" to "تاريخ الكشف: ${FormatUtils.formatDateTime(System.currentTimeMillis())}",
                "الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)} (${emp.salaryType})" to "مبيعاته: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)"
            )
        )

        // 3 Summary Cards
        val boxTop = y
        val boxBottom = y + 52f
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
            val lTp = makeTextPaint(Color.rgb(71, 85, 105), 8.5f, bold = false)
            val vTp = makeTextPaint(amountColor, 10.5f, bold = true)
            drawWrappedText(canvas, label, left + 6f, boxTop + 9f, colWidth - 12f, lTp, Layout.Alignment.ALIGN_CENTER)
            drawWrappedText(canvas, amount, left + 6f, boxTop + 27f, colWidth - 12f, vTp, Layout.Alignment.ALIGN_CENTER)
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
            val rows = buildList {
                add("الراتب الأساسي (${emp.salaryType})" to "+ ${FormatUtils.formatCurrency(emp.baseSalary, currency)}")
                add("عمولة المبيعات (${emp.commissionPercent}%)" to "+ ${FormatUtils.formatCurrency(summary.commissionEarned, currency)}")
                add("المكافآت المضافة" to "+ ${FormatUtils.formatCurrency(summary.bonuses, currency)}")
                if (summary.openingCredit > 0.01) {
                    add("مستحقات سابقة (رصيد افتتاحي له)" to "+ ${FormatUtils.formatCurrency(summary.openingCredit, currency)}")
                }
                add("السلف المسحوبة" to "- ${FormatUtils.formatCurrency(summary.advances, currency)}")
                if (summary.openingDebit > 0.01) {
                    add("سلف سابقة (رصيد افتتاحي عليه)" to "- ${FormatUtils.formatCurrency(summary.openingDebit, currency)}")
                }
                add("الصرفيات اليومية" to "- ${FormatUtils.formatCurrency(summary.dailyStipends, currency)}")
                add("خصومات الغياب والتأخير" to "- ${FormatUtils.formatCurrency(summary.deductions, currency)}")
                add("الرواتب المصروفة" to "- ${FormatUtils.formatCurrency(summary.salaryPaid, currency)}")
            }
            rows.forEachIndexed { idx, (label, value) ->
                val lh = measureWrappedHeight(label, regularTp, 270f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(value, regularTp, 200f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 12f
                if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
                drawGridRowBorders(canvas, y, y + rowH, listOf(238f), isOdd = idx % 2 == 0)
                drawWrappedText(canvas, label, 246f, y + 6f, 262f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, value, 32f, y + 6f, 198f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
            y += 10f
            canvas.drawRoundRect(RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 32f), 6f, 6f, Paint().apply { color = remBg })
            val netTp = makeTextPaint(remColor, 11.5f, bold = true)
            drawWrappedText(canvas, "صافي المستحق المتبقي للعامل:", 250f, y + 8f, 256f, netTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, FormatUtils.formatCurrency(summary.remainingBalance, currency), 32f, y + 8f, 200f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += 40f
        } else {
            drawEmpTableHeader()
            if (summary.entries.isEmpty()) {
                y += drawWrappedText(canvas, "لا توجد حركات مسجلة لهذا العامل حتى الآن", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
            } else {
                val creditTp = makeTextPaint(Color.rgb(4, 120, 87), 9.2f, bold = true)
                val debitTp = makeTextPaint(Color.rgb(185, 28, 28), 9.2f, bold = true)
                val mutedCenterTp = makeTextPaint(Color.rgb(148, 163, 184), 9.2f, bold = false)

                summary.entries.forEachIndexed { index, entry ->
                    val dateLayout = buildRtlStaticLayout(FormatUtils.formatDate(entry.timestamp), mutedSmallTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    val descLayout = buildRtlStaticLayout("${entry.type} • ${entry.title}", regularTp, 176f, Layout.Alignment.ALIGN_NORMAL)
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
                    val balLayout = buildRtlStaticLayout(FormatUtils.formatNumber(entry.runningBalance), boldTp, 70f, Layout.Alignment.ALIGN_OPPOSITE)

                    val rowH = maxOf(dateLayout.height, descLayout.height, crLayout.height, dbLayout.height, balLayout.height).toFloat() + 12f
                    if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = true)

                    drawGridRowBorders(canvas, y, y + rowH, empDividers, isOdd = index % 2 == 1)

                    val cellY = y + 6f
                    drawWrappedLayout(canvas, dateLayout, 442f, cellY)
                    drawWrappedLayout(canvas, descLayout, 256f, cellY)
                    drawWrappedLayout(canvas, crLayout, 180f, cellY)
                    drawWrappedLayout(canvas, dbLayout, 106f, cellY)
                    drawWrappedLayout(canvas, balLayout, 28f, cellY)
                    y += rowH
                }
            }
        }

        if (y + 52f > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
        y += 12f
        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 14f
        drawWrappedText(canvas, "توقيع العامل: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "اعتماد الإدارة: ________________", MARGIN_X + 8f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 9.5f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val mutedSmallTp = makeTextPaint(Color.rgb(100, 116, 139), 8.2f, bold = false)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val partyDividers = listOf(104f, 178f, 252f, 438f)

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 0f

        fun drawPartyTableHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 24f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("التاريخ", 477f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("البيان والتفاصيل", 432f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (summary.isCustomer) "عليه" else "مسدد له", 215f, y + 15.5f, thPaint)
            canvas.drawText(if (summary.isCustomer) "له (سداد)" else "له (توريد)", 141f, y + 15.5f, thPaint)
            canvas.drawText("الرصيد", 64f, y + 15.5f, thPaint)
            val hdrDivider = Paint().apply {
                color = Color.argb(65, 255, 255, 255)
                strokeWidth = 0.8f
            }
            partyDividers.forEach { dx -> canvas.drawLine(dx, y + 3f, dx, y + 21f, hdrDivider) }
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

        val docTitle = when {
            summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب عميل تفصيلي"
            summary.isCustomer && mode == StatementMode.SUMMARY -> "تقرير نهائي لحساب العميل"
            !summary.isCustomer && mode == StatementMode.DETAILED -> "كشف حساب مورد تفصيلي"
            else -> "تقرير نهائي لحساب المورد"
        }
        val docBannerBg = if (summary.isCustomer) Color.rgb(255, 251, 235) else Color.rgb(239, 246, 255)
        val docAccent = if (summary.isCustomer) Color.rgb(180, 83, 9) else Color.rgb(29, 78, 216)

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = docTitle,
            documentNumber = summary.party.name,
            accentColor = docAccent,
            bannerBg = docBannerBg
        )

        val roleTitle = if (summary.isCustomer) "العميل" else "المورد"
        val partyPhoneStr = if (summary.party.phone.isNotBlank()) "هاتف $roleTitle: ${summary.party.phone}" else "إجمالي العمليات: ${summary.entries.size} حركة"
        y = drawMetaCard(
            canvas = canvas,
            startY = y,
            rows = listOf(
                "$roleTitle: ${summary.party.name}" to "تاريخ الإصدار: ${FormatUtils.formatDateTime(System.currentTimeMillis())}",
                partyPhoneStr to "فواتير: ${summary.invoicesCount}  •  سندات: ${summary.paymentsCount}"
            )
        )

        // 3 Summary KPI Cards
        val boxTop = y
        val boxBottom = y + 52f
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
            val lTp = makeTextPaint(Color.rgb(71, 85, 105), 8.5f, bold = false)
            val vTp = makeTextPaint(valueColor, 10.5f, bold = true)
            drawWrappedText(canvas, title, left + 6f, boxTop + 9f, colWidth - 12f, lTp, Layout.Alignment.ALIGN_CENTER)
            drawWrappedText(canvas, value, left + 6f, boxTop + 27f, colWidth - 12f, vTp, Layout.Alignment.ALIGN_CENTER)
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
                val lh = measureWrappedHeight(k, regularTp, 265f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(v, valTp, 195f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 12f
                if (y + rowH > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
                drawGridRowBorders(canvas, y, y + rowH, listOf(238f), isOdd = idx % 2 == 0)
                drawWrappedText(canvas, k, 245f, y + 6f, 265f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, v, MARGIN_X + 8f, y + 6f, 195f, valTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
        } else {
            drawPartyTableHeader()
            if (summary.entries.isEmpty()) {
                y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذا الحساب حتى الآن", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
            } else {
                val entryTitleTp = makeTextPaint(Color.rgb(15, 23, 42), 9.4f, bold = true)
                val debitTp = makeTextPaint(if (summary.isCustomer) Color.rgb(185, 28, 28) else Color.rgb(4, 120, 87), 9.4f, bold = true)
                val creditTp = makeTextPaint(if (summary.isCustomer) Color.rgb(4, 120, 87) else Color.rgb(29, 78, 216), 9.4f, bold = true)
                val dashTp = makeTextPaint(Color.rgb(148, 163, 184), 9.4f, bold = false)
                val balTp = makeTextPaint(Color.rgb(15, 23, 42), 9.4f, bold = true)

                summary.entries.forEachIndexed { index, entry ->
                    val dateLayout = buildRtlStaticLayout(FormatUtils.formatDate(entry.timestamp), mutedSmallTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    val titleLayout = buildRtlStaticLayout(entry.title, entryTitleTp, 174f, Layout.Alignment.ALIGN_NORMAL)
                    val subLayout = if (entry.subDetails.isNotBlank()) {
                        buildRtlStaticLayout(entry.subDetails, mutedSmallTp, 174f, Layout.Alignment.ALIGN_NORMAL)
                    } else null
                    val debLayout = if (entry.debit > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.debit), debitTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", dashTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val credLayout = if (entry.credit > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.credit), creditTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", dashTp, 66f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val balLabel = when {
                        entry.runningBalance > 0.01 -> FormatUtils.formatNumber(entry.runningBalance)
                        entry.runningBalance < -0.01 -> "فائض ${FormatUtils.formatNumber(-entry.runningBalance)}"
                        else -> "0"
                    }
                    val balLayout = buildRtlStaticLayout(balLabel, balTp, 72f, Layout.Alignment.ALIGN_OPPOSITE)

                    val centerBlockH = titleLayout.height + (if (subLayout != null) subLayout.height + 2 else 0)
                    val rowHeight = maxOf(dateLayout.height, centerBlockH, debLayout.height, credLayout.height, balLayout.height).toFloat() + 14f

                    if (y + rowHeight > CONTENT_BOTTOM) nextPartyPage(withTableHeader = true)

                    drawGridRowBorders(canvas, y, y + rowHeight, partyDividers, isOdd = index % 2 == 1)

                    val cellY = y + 7f
                    drawWrappedLayout(canvas, dateLayout, 442f, cellY)
                    drawWrappedLayout(canvas, titleLayout, 258f, cellY)
                    if (subLayout != null) {
                        drawWrappedLayout(canvas, subLayout, 258f, cellY + titleLayout.height + 2f)
                    }
                    drawWrappedLayout(canvas, debLayout, 182f, cellY)
                    drawWrappedLayout(canvas, credLayout, 108f, cellY)
                    drawWrappedLayout(canvas, balLayout, 28f, cellY)

                    y += rowHeight
                }
            }
        }

        if (y + 135f > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
        y += 14f

        // Framed Summary Card with Highlighted Remaining Pill
        val sumStart = y
        val sumHeight = 86f
        val sumRect = RectF(MARGIN_X, sumStart, PAGE_WIDTH - MARGIN_X, sumStart + sumHeight)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        val pillBg = when {
            summary.netBalance > 0.01 -> Color.rgb(255, 247, 237)
            summary.netBalance < -0.01 -> Color.rgb(239, 246, 255)
            else -> Color.rgb(240, 253, 244)
        }
        val pillFg = when {
            summary.netBalance > 0.01 -> Color.rgb(194, 65, 12)
            summary.netBalance < -0.01 -> Color.rgb(29, 78, 216)
            else -> Color.rgb(22, 101, 52)
        }
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sumStart + 10f, PAGE_WIDTH - MARGIN_X - 10f, sumStart + 40f),
            6f, 6f,
            Paint().apply { color = pillBg }
        )
        val remainingTitle = when {
            summary.isCustomer && summary.netBalance > 0.01 -> "صافي الباقي على العميل للمحل:"
            summary.isCustomer && summary.netBalance < -0.01 -> "رصيد فائض للعميل لدى المحل:"
            !summary.isCustomer && summary.netBalance > 0.01 -> "صافي المتبقي للمورد بذمتنا:"
            !summary.isCustomer && summary.netBalance < -0.01 -> "رصيد مدفوع مقدماً لدى المورد:"
            else -> "صافي الحساب النهائي:"
        }
        val remainingVal = if (kotlin.math.abs(summary.netBalance) <= 0.01) {
            "الحساب خالص (0 $currency)"
        } else {
            FormatUtils.formatCurrency(kotlin.math.abs(summary.netBalance), currency)
        }
        val netTitleTp = makeTextPaint(pillFg, 11.5f, bold = true)
        drawWrappedText(canvas, remainingTitle, 245f, sumStart + 17f, 255f, netTitleTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, remainingVal, MARGIN_X + 18f, sumStart + 17f, 200f, netTitleTp, Layout.Alignment.ALIGN_OPPOSITE)

        val leftLabel = if (summary.isCustomer) "إجمالي ما عليه: ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}" else "إجمالي التوريدات: ${FormatUtils.formatCurrency(summary.totalForParty, currency)}"
        val rightLabel = if (summary.isCustomer) "إجمالي ما له (سداد): ${FormatUtils.formatCurrency(summary.totalForParty, currency)}" else "إجمالي المسدد له: ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}"
        drawWrappedText(canvas, leftLabel, 268f, sumStart + 54f, 232f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, rightLabel, MARGIN_X + 14f, sumStart + 54f, 232f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)

        y = sumStart + sumHeight + 18f

        drawWrappedText(canvas, "توقيع الطرف الآخر: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 8f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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

        val eqRows = buildList {
            add("صافي المبيعات (${report.salesCount} فاتورة)" to FormatUtils.formatCurrency(report.totalSales, currency))
            if (report.salesReturnsTotal > 0.01) {
                add("  • مرتجعات المبيعات (${report.salesReturnsCount} مستند)" to "- ${FormatUtils.formatCurrency(report.salesReturnsTotal, currency)}")
            }
            add("  • مبيعات نقدية (كاش)" to FormatUtils.formatCurrency(report.cashSales, currency))
            add("  • مبيعات تحويل صرافة" to FormatUtils.formatCurrency(report.transferSales, currency))
            add("  • مبيعات آجلة (بالدين)" to FormatUtils.formatCurrency(report.debtSales, currency))
            add("تكلفة البضاعة المباعة" to "- ${FormatUtils.formatCurrency(report.cogs, currency)}")
            add("مجمل الربح التجاري" to FormatUtils.formatCurrency(report.grossProfit, currency))
            add("صافي المشتريات (${report.purchasesCount} فاتورة)" to FormatUtils.formatCurrency(report.totalPurchases, currency))
            if (report.purchaseReturnsTotal > 0.01) {
                add("  • مرتجعات المشتريات (${report.purchaseReturnsCount} مستند)" to "- ${FormatUtils.formatCurrency(report.purchaseReturnsTotal, currency)}")
            }
            add("التحصيلات من العملاء (سندات قبض)" to "+ ${FormatUtils.formatCurrency(report.collectionsTotal, currency)}")
            add("المدفوعات للموردين (سندات صرف)" to "- ${FormatUtils.formatCurrency(report.supplierPaymentsTotal, currency)}")
            add("المصروفات التشغيلية" to "- ${FormatUtils.formatCurrency(report.expensesTotal, currency)}")
            add("الرواتب والسلف والمكافآت" to "- ${FormatUtils.formatCurrency(report.salariesAndAdvancesTotal, currency)}")
        }

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
        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val labelTp = makeTextPaint(Color.rgb(71, 85, 105), 10.5f, bold = false)
        val valueTp = makeTextPaint(Color.rgb(15, 23, 42), 11f, bold = true)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        var y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "سند قبض وتحصيل",
            documentNumber = "#${receipt.voucherNumber}",
            accentColor = Color.rgb(4, 120, 87),
            bannerBg = Color.rgb(236, 253, 245)
        ) + 4f

        val voucherRows = buildList {
            add("رقم السند:" to receipt.voucherNumber)
            add("التاريخ والوقت:" to FormatUtils.formatDateTime(receipt.timestamp))
            add("استلمنا من:" to receipt.payerName)
            add("طريقة التحصيل:" to if (receipt.paymentMethod == "نقداً") "نقداً إلى الصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})")
            if (receipt.appliedToDebtAmount > 0.0) {
                add("مسدد من حساب الدين:" to FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency))
            }
            if (receipt.directCollectionAmount > 0.0) {
                add("تحصيل مباشر / فائض:" to FormatUtils.formatCurrency(receipt.directCollectionAmount, currency))
            }
            if (receipt.remainingCustomerDebt > 0.0) {
                add("المتبقي على العميل:" to FormatUtils.formatCurrency(receipt.remainingCustomerDebt, currency))
            }
            if (receipt.notes.isNotBlank()) {
                add("البيان / الملاحظات:" to receipt.notes)
            }
        }

        voucherRows.forEachIndexed { idx, (label, value) ->
            val lh = measureWrappedHeight(label, labelTp, 165f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, valueTp, 295f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 14f
            if (y + rowH > CONTENT_BOTTOM) {
                drawPageFooter(canvas, pageNumber, shopName, continued = true)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 32f
            }
            drawGridRowBorders(canvas, y, y + rowH, listOf(332f), isOdd = idx % 2 == 0)
            drawWrappedText(canvas, label, 340f, y + 7f, 168f, labelTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, MARGIN_X + 8f, y + 7f, 294f, valueTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (y + 100f > CONTENT_BOTTOM) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 32f
        }

        y += 14f
        val totalBox = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 40f)
        canvas.drawRoundRect(totalBox, 8f, 8f, Paint().apply { color = Color.rgb(240, 253, 244) })
        canvas.drawRoundRect(totalBox, 8f, 8f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(167, 243, 208)
        })
        val totalTp = makeTextPaint(Color.rgb(22, 101, 52), 14f, bold = true)
        drawWrappedText(canvas, "المبلغ المستلم:", 260f, y + 11f, 244f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(receipt.amount, currency), MARGIN_X + 12f, y + 11f, 210f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 56f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 16f
        drawWrappedText(canvas, "توقيع المستلم: ________________", 280f, y, 228f, labelTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم المحل: ________________", MARGIN_X + 8f, y, 228f, labelTp, Layout.Alignment.ALIGN_OPPOSITE)
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
        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val labelTp = makeTextPaint(Color.rgb(71, 85, 105), 10.5f, bold = false)
        val valueTp = makeTextPaint(Color.rgb(15, 23, 42), 11f, bold = true)
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        var y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "سند صرف وتسديد لمورد",
            documentNumber = "#${receipt.voucherNumber}",
            accentColor = Color.rgb(185, 28, 28),
            bannerBg = Color.rgb(254, 242, 242)
        ) + 4f

        val voucherRows = buildList {
            add("رقم السند:" to receipt.voucherNumber)
            add("التاريخ والوقت:" to FormatUtils.formatDateTime(receipt.timestamp))
            add("صرفنا إلى المورد:" to receipt.supplierName)
            add("طريقة الصرف:" to if (receipt.paymentMethod == "نقداً") "نقداً من الصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})")
            if (receipt.appliedToDebtAmount > 0.0) {
                add("مسدد من الحساب الآجل:" to FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency))
            }
            if (receipt.directPaymentAmount > 0.0) {
                add("دفعة مباشرة / مقدمة:" to FormatUtils.formatCurrency(receipt.directPaymentAmount, currency))
            }
            add("المتبقي للمورد بذمتنا:" to FormatUtils.formatCurrency(receipt.remainingSupplierDebt, currency))
            if (receipt.notes.isNotBlank()) {
                add("البيان / الملاحظات:" to receipt.notes)
            }
        }

        voucherRows.forEachIndexed { idx, (label, value) ->
            val lh = measureWrappedHeight(label, labelTp, 165f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, valueTp, 295f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 14f
            if (y + rowH > CONTENT_BOTTOM) {
                drawPageFooter(canvas, pageNumber, shopName, continued = true)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 32f
            }
            drawGridRowBorders(canvas, y, y + rowH, listOf(332f), isOdd = idx % 2 == 0)
            drawWrappedText(canvas, label, 340f, y + 7f, 168f, labelTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, MARGIN_X + 8f, y + 7f, 294f, valueTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (y + 100f > CONTENT_BOTTOM) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 32f
        }

        y += 14f
        val totalBox = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 40f)
        canvas.drawRoundRect(totalBox, 8f, 8f, Paint().apply { color = Color.rgb(254, 242, 242) })
        canvas.drawRoundRect(totalBox, 8f, 8f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(254, 202, 202)
        })
        val totalTp = makeTextPaint(Color.rgb(185, 28, 28), 14f, bold = true)
        drawWrappedText(canvas, "المبلغ المسدد:", 260f, y + 11f, 244f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(receipt.amount, currency), MARGIN_X + 12f, y + 11f, 210f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        y += 56f

        canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
        y += 16f
        drawWrappedText(canvas, "توقيع المستلم (المورد): ____________", 260f, y, 248f, labelTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم المحل: ________________", MARGIN_X + 8f, y, 228f, labelTp, Layout.Alignment.ALIGN_OPPOSITE)
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

    fun generateReturnReceiptPdf(
        context: Context,
        records: List<ReturnRecord>,
        settings: StoreSettings?
    ): File {
        require(records.isNotEmpty()) { "لا توجد بنود مرتجع لطباعتها" }
        val first = records.first()
        val isSaleReturn = first.returnType == ReturnTypes.SALE_RETURN
        val pdfDocument = PdfDocument()

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

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
        var y: Float

        fun nextReturnPage(withTableHeader: Boolean = true) {
            drawPageFooter(canvas, pageNumber, shopName, continued = true)
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = 26f
            y += drawWrappedText(
                canvas,
                "تابع إشعار المرتجع #${first.returnNumber} (صفحة $pageNumber)",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 8f
            canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, linePaint)
            y += 10f
            if (withTableHeader) {
                y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الفاتورة")
            }
        }

        val bannerBg = if (isSaleReturn) Color.rgb(255, 247, 237) else Color.rgb(239, 246, 255)
        val bannerFg = if (isSaleReturn) Color.rgb(194, 65, 12) else Color.rgb(29, 78, 216)

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = if (isSaleReturn) "إشعار مرتجع مبيعات (من عميل)" else "إشعار مرتجع مشتريات (إلى مورد)",
            documentNumber = "#${first.returnNumber}",
            accentColor = bannerFg,
            bannerBg = bannerBg
        )

        val partyLabel = if (isSaleReturn) "العميل: ${first.partyName}" else "المورد: ${first.partyName}"
        y = drawMetaCard(
            canvas = canvas,
            startY = y,
            rows = listOf(
                partyLabel to "التاريخ: ${FormatUtils.formatDateTime(first.timestamp)}",
                "الفاتورة الأصلية: #${first.originalInvoiceNumber}" to "التسوية: ${first.settlementMethod}"
            )
        )
        y = drawAlignedFiveColHeader(canvas, y, "الكمية", "سعر الفاتورة")

        records.forEachIndexed { idx, line ->
            val unitPrice = if (isSaleReturn) line.unitSellPrice else line.unitBuyPrice
            val idxL = buildRtlStaticLayout("${idx + 1}", regularTp, 22f, Layout.Alignment.ALIGN_CENTER)
            val nameL = buildRtlStaticLayout(line.productName, boldTp, 220f, Layout.Alignment.ALIGN_NORMAL)
            val descText = listOfNotNull(
                line.category.takeIf { it.isNotBlank() },
                line.size.takeIf { it.isNotBlank() }?.let { "مقاس: $it" },
                line.color.takeIf { it.isNotBlank() }?.let { "لون: $it" }
            ).joinToString(" | ")
            val descL = if (descText.isNotBlank()) buildRtlStaticLayout(descText, mutedSmallTp, 220f, Layout.Alignment.ALIGN_NORMAL) else null
            val qtyL = buildRtlStaticLayout("${line.quantity} حبة", regularTp, 62f, Layout.Alignment.ALIGN_CENTER)
            val priceL = buildRtlStaticLayout(FormatUtils.formatNumber(unitPrice), regularTp, 66f, Layout.Alignment.ALIGN_CENTER)
            val totL = buildRtlStaticLayout("${FormatUtils.formatNumber(line.totalAmount)} $currency", boldTp, 80f, Layout.Alignment.ALIGN_OPPOSITE)

            val centerH = nameL.height + (if (descL != null) descL.height + 2 else 0)
            val rowH = maxOf(idxL.height, centerH, qtyL.height, priceL.height, totL.height).toFloat() + 12f
            if (y + rowH > CONTENT_BOTTOM) nextReturnPage(withTableHeader = true)

            drawRowGridBorders(canvas, y, y + rowH, isOdd = idx % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxL, 491f, cellY)
            drawWrappedLayout(canvas, nameL, 262f, cellY)
            if (descL != null) drawWrappedLayout(canvas, descL, 262f, cellY + nameL.height + 2f)
            drawWrappedLayout(canvas, qtyL, 190f, cellY)
            drawWrappedLayout(canvas, priceL, 116f, cellY)
            drawWrappedLayout(canvas, totL, 28f, cellY)
            y += rowH
        }

        val totalReturn = records.sumOf { it.totalAmount }
        val totalCash = records.sumOf { it.cashAmount }
        val totalTransfer = records.sumOf { it.transferAmount }
        val totalDebtReduced = records.sumOf { it.debtReducedAmount }
        val totalSurplus = records.sumOf { it.surplusCreditAmount }

        val breakdownRows = buildList {
            if (totalDebtReduced > 0.01) add("مخصوم من حساب الدين:" to FormatUtils.formatCurrency(totalDebtReduced, currency))
            if (totalCash > 0.01) add((if (isSaleReturn) "مبلغ نقدي مُرجع للعميل:" else "مبلغ نقدي مستلم من المورد:") to FormatUtils.formatCurrency(totalCash, currency))
            if (totalTransfer > 0.01) add("تسوية عبر الصراف (${first.exchangeAccountName}):" to FormatUtils.formatCurrency(totalTransfer, currency))
            if (totalSurplus > 0.01) add("رصيد دائن متروك بالحساب:" to FormatUtils.formatCurrency(totalSurplus, currency))
            if (first.notes.isNotBlank()) add("ملاحظات:" to first.notes)
        }
        val measuredBreakdown = breakdownRows.map { (k, v) ->
            val h = maxOf(
                measureWrappedHeight(k, regularTp, 230f, Layout.Alignment.ALIGN_NORMAL),
                measureWrappedHeight(v, boldTp, 215f, Layout.Alignment.ALIGN_OPPOSITE)
            ) + 8f
            Triple(k, v, h)
        }
        val sumBoxH = 44f + measuredBreakdown.sumOf { it.third.toDouble() }.toFloat() + 12f
        if (y + sumBoxH + 56f > CONTENT_BOTTOM) nextReturnPage(withTableHeader = false)
        y += 12f

        val sumRect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + sumBoxH)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(203, 213, 225)
        })

        var sy = y + 10f
        canvas.drawRoundRect(RectF(MARGIN_X + 10f, sy, PAGE_WIDTH - MARGIN_X - 10f, sy + 30f), 6f, 6f, Paint().apply { color = bannerBg })
        val totalTp = makeTextPaint(bannerFg, 12.5f, bold = true)
        drawWrappedText(canvas, "إجمالي قيمة المرتجع (${records.sumOf { it.quantity }} حبة):", 245f, sy + 7f, 255f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(totalReturn, currency), MARGIN_X + 18f, sy + 7f, 195f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 38f

        measuredBreakdown.forEach { (k, v, h) ->
            drawWrappedText(canvas, k, 270f, sy, 230f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 215f, boldTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += h
        }
        y += sumBoxH + 14f

        drawWrappedText(canvas, "توقيع الطرف الآخر: ________________", 280f, y, 228f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 8f, y, 228f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawPageFooter(canvas, pageNumber, shopName, continued = false)
        pdfDocument.finishPage(page)

        val returnsDir = File(context.cacheDir, "vouchers")
        if (!returnsDir.exists()) returnsDir.mkdirs()
        val safeName = first.returnNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(returnsDir, "مرتجع_$safeName.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }
}
