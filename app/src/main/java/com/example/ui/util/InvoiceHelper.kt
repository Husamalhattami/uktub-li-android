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

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 28f
    private const val RIGHT_X = PAGE_WIDTH - MARGIN_X // 567f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f) // 539f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - 44f // 798f

    private fun drawStoreLogoIfPresent(
        canvas: Canvas,
        settings: StoreSettings?,
        left: Float = MARGIN_X,
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
            "صفحة $pageNumber • تم إصداره آلياً من نظام $shopName (A4)"
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
    // 2. Multi-Item Professional Sale Invoice PDF (A4 595x842, 4-Column RTL + Tafqeet)
    // ==========================================

    data class PdfSaleLineItem(
        val name: String,
        val details: String,
        val quantity: Int,
        val unitPrice: Double,
        val total: Double,
        val unit: String = "حبه",
        val piecePrice: Double? = null,
        val productPrice: Double? = null
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
        val shopEn = settings?.shopNameEn?.trim().orEmpty()
        val phone = settings?.phone?.trim().orEmpty()
        val address = settings?.address?.trim().orEmpty()
        val headerNote = settings?.invoiceHeader?.trim().orEmpty()
        val taxNo = settings?.taxNumber?.trim().orEmpty()

        val titleTp = makeTextPaint(Color.rgb(15, 30, 54), 18f, bold = true)
        val subTp = makeTextPaint(Color.rgb(71, 85, 105), 9.5f, bold = false)

        // Top Navy Accent Bar
        canvas.drawRoundRect(
            RectF(MARGIN_X, 14f, RIGHT_X, 20f),
            3f, 3f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X + 4f, top = 26f, size = 46f)

        val headerWidth = CONTENT_WIDTH - 112f
        val headerLeft = MARGIN_X + 56f
        var y = 25f
        val fullTitle = if (shopEn.isNotBlank()) "$shopName  •  $shopEn" else shopName
        y += drawWrappedText(canvas, fullTitle, headerLeft, y, headerWidth, titleTp, Layout.Alignment.ALIGN_CENTER) + 2f
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
        y = y.coerceAtLeast(78f)

        // Framed Document Banner
        val bannerRect = RectF(MARGIN_X, y, RIGHT_X, y + 30f)
        canvas.drawRoundRect(bannerRect, 6f, 6f, Paint().apply { color = bannerBg })
        canvas.drawRoundRect(
            bannerRect, 6f, 6f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = accentColor
                alpha = 95
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

        val rightColWidth = (CONTENT_WIDTH - 24f) * 0.52f
        val leftColWidth = (CONTENT_WIDTH - 24f) * 0.46f
        val rightColLeft = RIGHT_X - 10f - rightColWidth
        val leftColLeft = MARGIN_X + 10f

        val rowHeights = rows.map { (rightTxt, leftTxt) ->
            val rH = measureWrappedHeight(rightTxt, boldTp, rightColWidth, Layout.Alignment.ALIGN_NORMAL)
            val lH = if (leftTxt.isNotBlank()) measureWrappedHeight(leftTxt, regularTp, leftColWidth, Layout.Alignment.ALIGN_OPPOSITE) else 0f
            maxOf(rH, lH) + 6f
        }
        val boxHeight = rowHeights.sum() + 12f
        val rect = RectF(MARGIN_X, startY, RIGHT_X, startY + boxHeight)
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
            drawWrappedText(canvas, rightTxt, rightColLeft, cy, rightColWidth, boldTp, Layout.Alignment.ALIGN_NORMAL)
            if (leftTxt.isNotBlank()) {
                drawWrappedText(canvas, leftTxt, leftColLeft, cy, leftColWidth, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            }
            cy += rowHeights[i]
        }
        return startY + boxHeight + 10f
    }

    private val invoiceFourColDividers = listOf(345f, 433f, 529f)

    // 4-Column RTL Header: # | السعر للمنتج | السعر للحبه | النوع و التفاصيل
    private fun drawInvoiceFourColHeader(canvas: Canvas, y: Float): Float {
        canvas.drawRoundRect(
            RectF(MARGIN_X, y, RIGHT_X, y + 25f),
            4f, 4f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        thPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("#", 548f, y + 16f, thPaint)
        canvas.drawText("السعر للمنتج", 481f, y + 16f, thPaint)
        canvas.drawText("السعر للحبه", 389f, y + 16f, thPaint)
        thPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("النوع و التفاصيل", 337f, y + 16f, thPaint)
        val hdrDividerPaint = Paint().apply {
            color = Color.argb(65, 255, 255, 255)
            strokeWidth = 0.8f
        }
        invoiceFourColDividers.forEach { dx ->
            canvas.drawLine(dx, y + 3f, dx, y + 22f, hdrDividerPaint)
        }
        return y + 26f
    }

    private fun drawAlignedFiveColHeader(
        canvas: Canvas,
        y: Float,
        col3Title: String,
        col4Title: String
    ): Float {
        canvas.drawRoundRect(
            RectF(MARGIN_X, y, RIGHT_X, y + 24f),
            4f, 4f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        thPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("#", 549f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف والمواصفات", 525f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(col3Title, 240f, y + 15.5f, thPaint)
        canvas.drawText(col4Title, 160f, y + 15.5f, thPaint)
        thPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("الإجمالي", MARGIN_X + 8f, y + 15.5f, thPaint)
        val hdrDividerPaint = Paint().apply {
            color = Color.argb(65, 255, 255, 255)
            strokeWidth = 0.8f
        }
        listOf(118f, 200f, 280f, 533f).forEach { dx ->
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
            canvas.drawRect(MARGIN_X, top, RIGHT_X, bottom, Paint().apply { color = Color.rgb(248, 250, 252) })
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_X, top, RIGHT_X, top, borderPaint)
        canvas.drawLine(MARGIN_X, top, MARGIN_X, bottom, borderPaint)
        dividersX.forEach { dx ->
            canvas.drawLine(dx, top, dx, bottom, borderPaint)
        }
        canvas.drawLine(RIGHT_X, top, RIGHT_X, bottom, borderPaint)
        canvas.drawLine(MARGIN_X, bottom, RIGHT_X, bottom, borderPaint)
    }

    private fun drawRowGridBorders(canvas: Canvas, top: Float, bottom: Float, isOdd: Boolean) {
        drawGridRowBorders(
            canvas = canvas,
            top = top,
            bottom = bottom,
            dividersX = listOf(118f, 200f, 280f, 533f),
            isOdd = isOdd
        )
    }

    private fun drawEndOfItemsRow(canvas: Canvas, y: Float, itemsCount: Int): Float {
        val rowH = 22f
        val rect = RectF(MARGIN_X, y, RIGHT_X, y + rowH)
        canvas.drawRect(rect, Paint().apply { color = Color.rgb(241, 245, 249) })
        val borderPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            strokeWidth = 0.9f
        }
        canvas.drawRect(rect, borderPaint.apply { style = Paint.Style.STROKE })
        val endTp = makeTextPaint(Color.rgb(51, 65, 85), 9.2f, bold = true)
        drawWrappedText(
            canvas,
            "— نهاية الأصناف ($itemsCount صنف) —",
            MARGIN_X + 8f,
            y + 5f,
            CONTENT_WIDTH - 16f,
            endTp,
            Layout.Alignment.ALIGN_CENTER
        )
        return y + rowH
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
        val mutedSmallTp = makeTextPaint(Color.rgb(71, 85, 105), 8.5f, bold = false)
        val priceTp = makeTextPaint(Color.rgb(15, 30, 54), 10f, bold = true)
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
            y += 10f
            if (withTableHeader) {
                y = drawInvoiceFourColHeader(canvas, y)
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
        y = drawInvoiceFourColHeader(canvas, y)

        items.forEachIndexed { index, item ->
            val mult = ClothingUnits.getMultiplier(item.unit).coerceAtLeast(1)
            val resolvedProductPrice = item.productPrice ?: item.total
            val resolvedPiecePrice = item.piecePrice ?: run {
                if (mult > 1 && kotlin.math.abs(item.unitPrice - resolvedProductPrice) <= 0.5) {
                    item.unitPrice / mult
                } else {
                    item.unitPrice
                }
            }

            val qtyText = if (item.piecePrice != null) {
                if (mult > 1 && item.quantity >= mult && item.quantity % mult == 0) {
                    "${item.quantity / mult} ${item.unit} (${item.quantity} حبة)"
                } else {
                    "${item.quantity} حبة"
                }
            } else {
                ClothingUnits.formatUnitDescription(item.unit, item.quantity.toDouble())
            }

            val fullDetails = buildString {
                append("الكمية: $qtyText")
                if (item.details.isNotBlank()) {
                    append(" • ${item.details}")
                }
            }

            val idxLayout = buildRtlStaticLayout("${index + 1}", regularTp, 32f, Layout.Alignment.ALIGN_CENTER)
            val prodPriceLayout = buildRtlStaticLayout(FormatUtils.formatNumber(resolvedProductPrice), priceTp, 88f, Layout.Alignment.ALIGN_CENTER)
            val piecePriceLayout = buildRtlStaticLayout(FormatUtils.formatNumber(resolvedPiecePrice), regularTp, 80f, Layout.Alignment.ALIGN_CENTER)
            val nameLayout = buildRtlStaticLayout(item.name, boldTp, 305f, Layout.Alignment.ALIGN_NORMAL)
            val detailsLayout = buildRtlStaticLayout(fullDetails, mutedSmallTp, 305f, Layout.Alignment.ALIGN_NORMAL)

            val descBlockH = nameLayout.height + 2 + detailsLayout.height
            val rowContentH = maxOf(idxLayout.height, prodPriceLayout.height, piecePriceLayout.height, descBlockH).toFloat()
            val rowHeight = rowContentH + 12f

            if (y + rowHeight > CONTENT_BOTTOM) {
                nextSalePage(withTableHeader = true)
            }

            drawGridRowBorders(canvas, y, y + rowHeight, invoiceFourColDividers, isOdd = index % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxLayout, 532f, cellY)
            drawWrappedLayout(canvas, prodPriceLayout, 437f, cellY)
            drawWrappedLayout(canvas, piecePriceLayout, 349f, cellY)
            drawWrappedLayout(canvas, nameLayout, MARGIN_X + 6f, cellY)
            drawWrappedLayout(canvas, detailsLayout, MARGIN_X + 6f, cellY + nameLayout.height + 2f)

            y += rowHeight
        }

        if (y + 26f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
        y = drawEndOfItemsRow(canvas, y, items.size)

        val amountInWords = FormatUtils.amountToArabicWords(netTotal, currency)
        val extraSummaryRows = mutableListOf<Triple<String, String, Int>>()
        if (discount > 0.01) {
            extraSummaryRows.add(Triple("الإجمالي قبل الخصم:", FormatUtils.formatCurrency(subtotal, currency), Color.rgb(15, 23, 42)))
            extraSummaryRows.add(Triple("الخصم الممنوح (-):", "- ${FormatUtils.formatCurrency(discount, currency)}", Color.rgb(217, 119, 6)))
        } else {
            extraSummaryRows.add(Triple("الإجمالي:", FormatUtils.formatCurrency(subtotal, currency), Color.rgb(15, 23, 42)))
        }
        if (cashPaid > 0.01) {
            extraSummaryRows.add(Triple("المدفوع نقداً (كاش):", FormatUtils.formatCurrency(cashPaid, currency), Color.rgb(22, 101, 52)))
        }
        if (transferPaid > 0.01) {
            extraSummaryRows.add(Triple("المدفوع تحويل صرافة:", FormatUtils.formatCurrency(transferPaid, currency), Color.rgb(15, 118, 110)))
        }
        if (debtAmount > 0.01) {
            extraSummaryRows.add(Triple("المبلغ المقيد ديناً على العميل:", FormatUtils.formatCurrency(debtAmount, currency), Color.rgb(220, 38, 38)))
        }
        if (previousDebt > 0.01) {
            extraSummaryRows.add(Triple("الرصيد السابق للعميل:", FormatUtils.formatCurrency(previousDebt, currency), Color.rgb(100, 116, 139)))
            extraSummaryRows.add(Triple("إجمالي الرصيد المستحق بذمة العميل:", FormatUtils.formatCurrency(previousDebt + debtAmount, currency), Color.rgb(15, 30, 54)))
        }

        val wordsTp = makeTextPaint(Color.rgb(15, 30, 54), 10.5f, bold = true)
        val wordsH = measureWrappedHeight("كتابة المبلغ بالنص: $amountInWords", wordsTp, CONTENT_WIDTH - 36f, Layout.Alignment.ALIGN_NORMAL)
        val wordsBoxH = maxOf(28f, wordsH + 14f)
        val cardH = 12f + 34f + wordsBoxH + 10f + (extraSummaryRows.size * 22f) + 10f

        if (y + cardH + 64f > CONTENT_BOTTOM) {
            nextSalePage(withTableHeader = false)
        }

        y += 12f
        val summaryStart = y
        val sumRect = RectF(MARGIN_X, summaryStart, RIGHT_X, summaryStart + cardH)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        var sy = summaryStart + 10f
        // Highlighted Net Total Row: المبلغ / الإجمالي
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + 30f),
            6f, 6f,
            Paint().apply { color = Color.rgb(220, 252, 231) }
        )
        val netTp = makeTextPaint(Color.rgb(22, 101, 52), 13f, bold = true)
        drawWrappedText(canvas, "المبلغ (الإجمالي النهائي):", RIGHT_X - 240f, sy + 7f, 224f, netTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(netTotal, currency), MARGIN_X + 18f, sy + 7f, 240f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 36f

        // Tafqeet Box: كتابة المبلغ بالنص
        val wordsRect = RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + wordsBoxH)
        canvas.drawRoundRect(wordsRect, 6f, 6f, Paint().apply { color = Color.WHITE })
        canvas.drawRoundRect(wordsRect, 6f, 6f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            color = Color.rgb(203, 213, 225)
        })
        drawWrappedText(
            canvas,
            "كتابة المبلغ بالنص: $amountInWords",
            MARGIN_X + 18f,
            sy + 7f,
            CONTENT_WIDTH - 36f,
            wordsTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        sy += wordsBoxH + 8f

        extraSummaryRows.forEach { (k, v, col) ->
            val kTp = makeTextPaint(Color.rgb(51, 65, 85), 9.8f, bold = false)
            val vTp = makeTextPaint(col, 10f, bold = true)
            drawWrappedText(canvas, k, RIGHT_X - 260f, sy, 246f, kTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 235f, vTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += 22f
        }
        y = summaryStart + cardH + 12f

        if (y + 56f > CONTENT_BOTTOM) nextSalePage(withTableHeader = false)
        y += drawWrappedText(canvas, footer, MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        drawWrappedText(canvas, "توقيع العميل: ________________", RIGHT_X - 240f, y, 230f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 8f, y, 230f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)

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
    // 3. Purchase Invoice PDF (A4 595x842, 4-Column RTL + Tafqeet)
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
        val mutedSmallTp = makeTextPaint(Color.rgb(71, 85, 105), 8.5f, bold = false)
        val priceTp = makeTextPaint(Color.rgb(29, 78, 216), 10f, bold = true)
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
            y += 10f
            if (withTableHeader) {
                y = drawInvoiceFourColHeader(canvas, y)
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
        y = drawInvoiceFourColHeader(canvas, y)

        items.forEachIndexed { index, item ->
            val mult = item.multiplier.coerceAtLeast(1)
            val pricePerPiece = item.buyPricePerUnit / mult
            val unitStr = if (item.unit == "حبه") {
                "${item.totalPieces} حبة"
            } else {
                "${item.unitCount.toInt()} ${item.unit} (${item.totalPieces} حبة)"
            }
            val specText = buildString {
                append("الكمية: $unitStr • المقاس: ${item.size} | اللون: ${item.color}")
                if (item.sellPricePerUnit > 0) {
                    append(" • بيع مقترح: ${FormatUtils.formatNumber(item.sellPricePerUnit)}")
                }
            }

            val idxLayout = buildRtlStaticLayout("${index + 1}", regularTp, 32f, Layout.Alignment.ALIGN_CENTER)
            val lineCostLayout = buildRtlStaticLayout(FormatUtils.formatNumber(item.lineCost), priceTp, 88f, Layout.Alignment.ALIGN_CENTER)
            val piecePriceLayout = buildRtlStaticLayout(FormatUtils.formatNumber(pricePerPiece), regularTp, 80f, Layout.Alignment.ALIGN_CENTER)
            val titleLayout = buildRtlStaticLayout("${item.modelName} (${item.category})", boldTp, 305f, Layout.Alignment.ALIGN_NORMAL)
            val specLayout = buildRtlStaticLayout(specText, mutedSmallTp, 305f, Layout.Alignment.ALIGN_NORMAL)

            val centerBlockH = titleLayout.height + 2 + specLayout.height
            val rowH = maxOf(idxLayout.height, lineCostLayout.height, piecePriceLayout.height, centerBlockH).toFloat() + 12f

            if (y + rowH > CONTENT_BOTTOM) {
                nextPurchasePage(withTableHeader = true)
            }

            drawGridRowBorders(canvas, y, y + rowH, invoiceFourColDividers, isOdd = index % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxLayout, 532f, cellY)
            drawWrappedLayout(canvas, lineCostLayout, 437f, cellY)
            drawWrappedLayout(canvas, piecePriceLayout, 349f, cellY)
            drawWrappedLayout(canvas, titleLayout, MARGIN_X + 6f, cellY)
            drawWrappedLayout(canvas, specLayout, MARGIN_X + 6f, cellY + titleLayout.height + 2f)
            y += rowH
        }

        if (y + 26f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y = drawEndOfItemsRow(canvas, y, items.size)

        val amountInWords = FormatUtils.amountToArabicWords(totalCost, currency)
        val summaryRows = mutableListOf<Triple<String, String, Int>>()
        summaryRows.add(Triple("الإجمالي (${items.sumOf { it.totalPieces }} حبة):", FormatUtils.formatCurrency(totalCost, currency), Color.rgb(15, 23, 42)))
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
            val kh = measureWrappedHeight(k, kTp, 250f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(v, vTp, 230f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(kh, vh) + 8f
            Triple(k, v to vTp, rowH)
        }
        val wordsTp = makeTextPaint(Color.rgb(15, 30, 54), 10.5f, bold = true)
        val wordsH = measureWrappedHeight("كتابة المبلغ بالنص: $amountInWords", wordsTp, CONTENT_WIDTH - 36f, Layout.Alignment.ALIGN_NORMAL)
        val wordsBoxH = maxOf(28f, wordsH + 14f)

        val dynamicRowsHeight = measuredSummaryRows.sumOf { it.third.toDouble() }.toFloat()
        val sumH = 12f + 34f + wordsBoxH + 10f + dynamicRowsHeight + 10f
        if (y + sumH + 64f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += 12f

        val sumStart = y
        val sumRect = RectF(MARGIN_X, sumStart, RIGHT_X, sumStart + sumH)
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
            RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + 30f),
            6f, 6f,
            Paint().apply { color = Color.rgb(239, 246, 255) }
        )
        val totalPurchaseTp = makeTextPaint(Color.rgb(29, 78, 216), 13f, bold = true)
        drawWrappedText(canvas, "المبلغ (إجمالي المشتريات):", RIGHT_X - 245f, sy + 7f, 229f, totalPurchaseTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(totalCost, currency), MARGIN_X + 18f, sy + 7f, 235f, totalPurchaseTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 36f

        val wordsRect = RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + wordsBoxH)
        canvas.drawRoundRect(wordsRect, 6f, 6f, Paint().apply { color = Color.WHITE })
        canvas.drawRoundRect(wordsRect, 6f, 6f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            color = Color.rgb(203, 213, 225)
        })
        drawWrappedText(
            canvas,
            "كتابة المبلغ بالنص: $amountInWords",
            MARGIN_X + 18f,
            sy + 7f,
            CONTENT_WIDTH - 36f,
            wordsTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        sy += wordsBoxH + 8f

        measuredSummaryRows.forEach { (k, vPair, rowH) ->
            val (v, vTp) = vPair
            drawWrappedText(canvas, k, RIGHT_X - 264f, sy, 250f, kTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 230f, vTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += rowH
        }
        y = sumStart + sumH + 12f

        if (y + 52f > CONTENT_BOTTOM) nextPurchasePage(withTableHeader = false)
        y += drawWrappedText(canvas, "تم استلام ومطابقة البضاعة وإدخالها للمستودع بنجاح", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        drawWrappedText(canvas, "توقيع المورد: ________________", RIGHT_X - 240f, y, 230f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "أمين المستودع / المدير: ________________", MARGIN_X + 8f, y, 230f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)

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
    // 4. Cash Drawer & Exchange Movement Statement PDF (A4, 8-Column RTL + ملخص الحساب)
    // ==========================================

    fun generateDailyMovementPdf(
        context: Context,
        movements: List<CashDrawerMovement>,
        transfers: List<ExchangeTransfer>,
        currentCash: Double,
        currentExchange: Double,
        periodTitle: String,
        settings: StoreSettings?,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): File {
        val ledgerSummary = StatementHelper.buildCashDrawerLedger(
            movements = movements,
            transfers = transfers,
            currentCashBalance = currentCash,
            currentExchangeBalance = currentExchange,
            startMillis = startMillis,
            endMillis = endMillis,
            periodTitle = periodTitle
        )
        return generateDailyMovementPdf(context, ledgerSummary, settings)
    }

    fun generateDailyMovementPdf(
        context: Context,
        summary: CashDrawerLedgerSummary,
        settings: StoreSettings?
    ): File {
        val pdfDocument = PdfDocument()

        val subTp = makeTextPaint(Color.rgb(100, 116, 139), 9.5f, bold = false)
        val boldTp = makeTextPaint(Color.rgb(15, 23, 42), 10f, bold = true)
        val regularTp = makeTextPaint(Color.rgb(51, 65, 85), 8.6f, bold = false)
        val dateTp = makeTextPaint(Color.rgb(71, 85, 105), 8.0f, bold = false)
        val incomeTp = makeTextPaint(Color.rgb(4, 120, 87), 8.8f, bold = true)
        val expenseTp = makeTextPaint(Color.rgb(185, 28, 28), 8.8f, bold = true)
        val mutedCenterTp = makeTextPaint(Color.rgb(148, 163, 184), 8.8f, bold = false)
        val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val ledgerRows = summary.rows

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 0f

        // 8 columns RTL inside [28f .. 567f] (width = 539f):
        // م (24) | التاريخ (62) | البيان (146) | الحساب (66) | النوع (52) | مدين دفع (62) | دائن قبض (62) | الرصيد (65)
        val movementDividers = listOf(93f, 155f, 217f, 269f, 335f, 481f, 543f)

        fun drawMovementHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, RIGHT_X, y + 25f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("م", 555f, y + 16f, headerCellPaint)
            canvas.drawText("التاريخ", 512f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("البيان", 476f, y + 16f, headerCellPaint)
            headerCellPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("الحساب", 302f, y + 16f, headerCellPaint)
            canvas.drawText("النوع", 243f, y + 16f, headerCellPaint)
            canvas.drawText("مدين دفع", 186f, y + 16f, headerCellPaint)
            canvas.drawText("دائن قبض", 124f, y + 16f, headerCellPaint)
            canvas.drawText("الرصيد", 60.5f, y + 16f, headerCellPaint)

            val hdrDivider = Paint().apply {
                color = Color.argb(65, 255, 255, 255)
                strokeWidth = 0.8f
            }
            movementDividers.forEach { dx -> canvas.drawLine(dx, y + 3f, dx, y + 22f, hdrDivider) }
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
                "تابع كشف حركة الصندوق والحسابات (${summary.periodLabel}) — صفحة $pageNumber",
                MARGIN_X,
                y,
                CONTENT_WIDTH,
                boldTp,
                Layout.Alignment.ALIGN_NORMAL
            ) + 8f
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
            y += 10f
            if (withHeader) drawMovementHeader()
        }

        y = drawFramedInvoiceHeader(
            canvas = canvas,
            settings = settings,
            documentTitle = "كشف حركة الصندوق والحسابات",
            documentNumber = summary.periodLabel,
            accentColor = Color.rgb(15, 30, 54),
            bannerBg = Color.rgb(241, 245, 249)
        )
        y = drawMetaCard(
            canvas = canvas,
            startY = y,
            rows = listOf(
                "الفترة: ${summary.periodLabel}" to "تاريخ التقرير: ${FormatUtils.formatDateTime(System.currentTimeMillis())}",
                "رصيد كاش الصندوق الحالي: ${FormatUtils.formatCurrency(summary.currentCashBalance, currency)}" to
                    "أرصدة الصرافة والمحافظ: ${FormatUtils.formatCurrency(summary.currentExchangeBalance, currency)}",
                "عدد الحركات بالفترة: ${ledgerRows.size} حركة" to
                    "الرصيد قبل الفترة: ${FormatUtils.formatCurrency(summary.openingBalance, currency)}"
            )
        )

        drawMovementHeader()

        if (ledgerRows.isEmpty()) {
            y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذه الفترة", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
        } else {
            ledgerRows.forEachIndexed { index, row ->
                val idxLayout = buildRtlStaticLayout("${index + 1}", dateTp, 20f, Layout.Alignment.ALIGN_CENTER)
                val dateStr = "${FormatUtils.formatDate(row.timestamp)}\n${FormatUtils.formatTime(row.timestamp)}"
                val dateLayout = buildRtlStaticLayout(dateStr, dateTp, 56f, Layout.Alignment.ALIGN_CENTER)
                val descLayout = buildRtlStaticLayout(row.title, regularTp, 138f, Layout.Alignment.ALIGN_NORMAL)
                val accLayout = buildRtlStaticLayout(row.accountLabel, regularTp, 60f, Layout.Alignment.ALIGN_CENTER)
                val typePaint = if (row.isIncome) incomeTp else expenseTp
                val typeLayout = buildRtlStaticLayout(row.kindLabel, typePaint, 46f, Layout.Alignment.ALIGN_CENTER)
                val debitLayout = if (row.debitPaid > 0.01) {
                    buildRtlStaticLayout(FormatUtils.formatNumber(row.debitPaid), expenseTp, 56f, Layout.Alignment.ALIGN_CENTER)
                } else {
                    buildRtlStaticLayout("-", mutedCenterTp, 56f, Layout.Alignment.ALIGN_CENTER)
                }
                val creditLayout = if (row.creditReceived > 0.01) {
                    buildRtlStaticLayout(FormatUtils.formatNumber(row.creditReceived), incomeTp, 56f, Layout.Alignment.ALIGN_CENTER)
                } else {
                    buildRtlStaticLayout("-", mutedCenterTp, 56f, Layout.Alignment.ALIGN_CENTER)
                }
                val balLayout = buildRtlStaticLayout(FormatUtils.formatNumber(row.runningBalance), boldTp, 59f, Layout.Alignment.ALIGN_CENTER)

                val rowH = maxOf(
                    idxLayout.height,
                    dateLayout.height,
                    descLayout.height,
                    accLayout.height,
                    typeLayout.height,
                    debitLayout.height,
                    creditLayout.height,
                    balLayout.height
                ).toFloat() + 10f

                if (y + rowH > CONTENT_BOTTOM) {
                    nextMovementPage(withHeader = true)
                }

                drawGridRowBorders(canvas, y, y + rowH, movementDividers, isOdd = index % 2 == 1)

                val cellY = y + 5f
                drawWrappedLayout(canvas, idxLayout, 545f, cellY)
                drawWrappedLayout(canvas, dateLayout, 484f, cellY)
                drawWrappedLayout(canvas, descLayout, 339f, cellY)
                drawWrappedLayout(canvas, accLayout, 272f, cellY)
                drawWrappedLayout(canvas, typeLayout, 220f, cellY)
                drawWrappedLayout(canvas, debitLayout, 158f, cellY)
                drawWrappedLayout(canvas, creditLayout, 96f, cellY)
                drawWrappedLayout(canvas, balLayout, MARGIN_X + 3f, cellY)
                y += rowH
            }
        }

        // Bottom Summary Card: ملخص الحساب
        val summaryCardHeight = 148f
        if (y + summaryCardHeight + 24f > CONTENT_BOTTOM) nextMovementPage(withHeader = false)
        y += 14f

        val sumStart = y
        val sumRect = RectF(MARGIN_X, sumStart, RIGHT_X, sumStart + summaryCardHeight)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        // Header bar inside summary card
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sumStart + 10f, RIGHT_X - 10f, sumStart + 34f),
            6f, 6f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )
        val sumHeaderTp = makeTextPaint(Color.WHITE, 11f, bold = true)
        drawWrappedText(canvas, "ملخص الحساب (${summary.periodLabel})", MARGIN_X + 16f, sumStart + 16f, CONTENT_WIDTH - 32f, sumHeaderTp, Layout.Alignment.ALIGN_CENTER)

        var sy = sumStart + 44f
        val kTp = makeTextPaint(Color.rgb(51, 65, 85), 9.5f, bold = false)
        val rightColLeft = RIGHT_X - 255f
        val leftColLeft = MARGIN_X + 14f

        drawWrappedText(canvas, "الرصيد قبل الفترة: ${FormatUtils.formatCurrency(summary.openingBalance, currency)}", rightColLeft, sy, 240f, kTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "صافي الحركة: ${FormatUtils.formatCurrency(summary.netChange, currency)}", leftColLeft, sy, 240f, kTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 22f

        drawWrappedText(canvas, "إجمالي دائن قبض (+): ${FormatUtils.formatCurrency(summary.totalCreditReceived, currency)}", rightColLeft, sy, 240f, incomeTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "إجمالي مدين دفع (-): ${FormatUtils.formatCurrency(summary.totalDebitPaid, currency)}", leftColLeft, sy, 240f, expenseTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 26f

        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + 44f),
            6f, 6f,
            Paint().apply { color = Color.rgb(236, 253, 245) }
        )
        val closingTp = makeTextPaint(Color.rgb(4, 120, 87), 11f, bold = true)
        val closingWordsTp = makeTextPaint(Color.rgb(51, 65, 85), 8.8f, bold = false)
        drawWrappedText(canvas, "الرصيد الختامي نهاية الفترة:", RIGHT_X - 245f, sy + 6f, 230f, closingTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(summary.closingBalance, currency), MARGIN_X + 18f, sy + 6f, 230f, closingTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "كتابة الرصيد بالنص: ${FormatUtils.amountToArabicWords(summary.closingBalance, currency)}", MARGIN_X + 18f, sy + 24f, CONTENT_WIDTH - 36f, closingWordsTp, Layout.Alignment.ALIGN_NORMAL)

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
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val emp = summary.employee

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
        val empDividers = listOf(106f, 184f, 262f, 472f)

        fun drawEmpTableHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, RIGHT_X, y + 24f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("التاريخ", 519f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("البيان والحركة", 464f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("له (+)", 223f, y + 15.5f, thPaint)
            canvas.drawText("عليه (-)", 145f, y + 15.5f, thPaint)
            canvas.drawText("الرصيد", 67f, y + 15.5f, thPaint)
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
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
        val metaRows = buildList {
            add("العامل: ${emp.name} (${emp.role})" to "تاريخ الكشف: ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
            add("الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)} (${emp.salaryType})" to "مبيعاته: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)")
            if (summary.periodLabel.isNotBlank()) {
                add("نطاق الكشف: ${summary.periodLabel}" to "عدد الحركات: ${summary.entries.size}")
            }
        }
        y = drawMetaCard(canvas = canvas, startY = y, rows = metaRows)

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
                val lh = measureWrappedHeight(label, regularTp, 300f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(value, regularTp, 206f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 12f
                if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
                drawGridRowBorders(canvas, y, y + rowH, listOf(248f), isOdd = idx % 2 == 0)
                drawWrappedText(canvas, label, 256f, y + 6f, 300f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, value, MARGIN_X + 6f, y + 6f, 206f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
            y += 10f
            canvas.drawRoundRect(RectF(MARGIN_X, y, RIGHT_X, y + 32f), 6f, 6f, Paint().apply { color = remBg })
            val netTp = makeTextPaint(remColor, 11.5f, bold = true)
            drawWrappedText(canvas, "صافي المستحق المتبقي للعامل:", 256f, y + 8f, 300f, netTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, FormatUtils.formatCurrency(summary.remainingBalance, currency), MARGIN_X + 8f, y + 8f, 206f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
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
                    val dateLayout = buildRtlStaticLayout(FormatUtils.formatDate(entry.timestamp), mutedSmallTp, 87f, Layout.Alignment.ALIGN_CENTER)
                    val descLayout = buildRtlStaticLayout("${entry.type} • ${entry.title}", regularTp, 200f, Layout.Alignment.ALIGN_NORMAL)
                    val crLayout = if (entry.creditEarned > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.creditEarned), creditTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", mutedCenterTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val dbLayout = if (entry.debitTaken > 0.01) {
                        buildRtlStaticLayout(FormatUtils.formatNumber(entry.debitTaken), debitTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    } else {
                        buildRtlStaticLayout("-", mutedCenterTp, 70f, Layout.Alignment.ALIGN_CENTER)
                    }
                    val balLayout = buildRtlStaticLayout(FormatUtils.formatNumber(entry.runningBalance), boldTp, 70f, Layout.Alignment.ALIGN_CENTER)

                    val rowH = maxOf(dateLayout.height, descLayout.height, crLayout.height, dbLayout.height, balLayout.height).toFloat() + 12f
                    if (y + rowH > CONTENT_BOTTOM) nextEmpPage(withTableHeader = true)

                    drawGridRowBorders(canvas, y, y + rowH, empDividers, isOdd = index % 2 == 1)

                    val cellY = y + 6f
                    drawWrappedLayout(canvas, dateLayout, 476f, cellY)
                    drawWrappedLayout(canvas, descLayout, 267f, cellY)
                    drawWrappedLayout(canvas, crLayout, 188f, cellY)
                    drawWrappedLayout(canvas, dbLayout, 110f, cellY)
                    drawWrappedLayout(canvas, balLayout, 32f, cellY)
                    y += rowH
                }
            }
        }

        if (y + 52f > CONTENT_BOTTOM) nextEmpPage(withTableHeader = false)
        y += 12f
        canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
        y += 14f
        drawWrappedText(canvas, "توقيع العامل: ________________", 300f, y, 258f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "اعتماد الإدارة: ________________", MARGIN_X + 6f, y, 258f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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
    // 6. Party Ledger Statement PDF (كشف حساب عميل أو مورد A4 - 6 أعمدة RTL + ملخص كشف الحساب)
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

        // 6-Column RTL Layout across [28 .. 567] = 539f:
        // م (30f: 537..567) | رقم السند (72f: 465..537) | البيانات (221f: 244..465) |
        // مدين (72f: 172..244) | دائن (72f: 100..172) | الرصيد (72f: 28..100)
        val partyDividers = listOf(100f, 172f, 244f, 465f, 537f)

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = 0f

        fun drawPartyTableHeader() {
            canvas.drawRoundRect(
                RectF(MARGIN_X, y, RIGHT_X, y + 24f),
                4f, 4f,
                Paint().apply { color = Color.rgb(15, 30, 54) }
            )
            val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("م", 552f, y + 15.5f, thPaint)
            canvas.drawText("رقم السند", 501f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("البيانات", 458f, y + 15.5f, thPaint)
            thPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("مدين", 208f, y + 15.5f, thPaint)
            canvas.drawText("دائن", 136f, y + 15.5f, thPaint)
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
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
        val metaRows = buildList {
            add("$roleTitle: ${summary.party.name}" to "تاريخ الإصدار: ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
            add(partyPhoneStr to "فواتير: ${summary.invoicesCount}  •  سندات: ${summary.paymentsCount}")
            if (summary.periodLabel.isNotBlank()) {
                add("الفترة المحددة: ${summary.periodLabel}" to "عدد الحركات بالكشف: ${summary.entries.size}")
            }
        }
        y = drawMetaCard(canvas = canvas, startY = y, rows = metaRows)

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
                "مدين / عليه (فواتير وديون)",
                FormatUtils.formatCurrency(summary.totalOnParty, currency),
                Color.rgb(185, 28, 28)
            )
            drawKpiCard(
                xMidBox,
                Color.rgb(236, 253, 245),
                Color.rgb(167, 243, 208),
                "دائن / له (المدفوع والمحصل)",
                FormatUtils.formatCurrency(summary.totalForParty, currency),
                Color.rgb(4, 120, 87)
            )
            val netLabel = when {
                summary.netBalance > 0.01 -> "صافي الباقي عليه"
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
                "دائن / له (توريدات وديون)",
                FormatUtils.formatCurrency(summary.totalForParty, currency),
                Color.rgb(29, 78, 216)
            )
            drawKpiCard(
                xMidBox,
                Color.rgb(236, 253, 245),
                Color.rgb(167, 243, 208),
                "مدين / مسدد له",
                FormatUtils.formatCurrency(summary.totalOnParty, currency),
                Color.rgb(4, 120, 87)
            )
            val netLabel = when {
                summary.netBalance > 0.01 -> "صافي المتبقي للمورد"
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
                    "إجمالي مدين (فواتير وديون عليه):" to FormatUtils.formatCurrency(summary.totalOnParty, currency),
                    "إجمالي دائن (مبالغ مدفوعة ومرتجعات):" to FormatUtils.formatCurrency(summary.totalForParty, currency),
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
                    "إجمالي دائن (بضائع موردة وديون له):" to FormatUtils.formatCurrency(summary.totalForParty, currency),
                    "إجمالي مدين (مسدد ومرتجعات للمورد):" to FormatUtils.formatCurrency(summary.totalOnParty, currency),
                    "صافي الرصيد النهائي:" to when {
                        summary.netBalance > 0.01 -> "باقي له بذمتنا ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
                        summary.netBalance < -0.01 -> "رصيد مدفوع مقدماً ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
                        else -> "حساب المورد خالص (0 $currency)"
                    }
                )
            }
            summaryLines.forEachIndexed { idx, (k, v) ->
                val valTp = if (idx == summaryLines.lastIndex) boldTp else regularTp
                val lh = measureWrappedHeight(k, regularTp, 290f, Layout.Alignment.ALIGN_NORMAL)
                val vh = measureWrappedHeight(v, valTp, 210f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, vh) + 12f
                if (y + rowH > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
                drawGridRowBorders(canvas, y, y + rowH, listOf(252f), isOdd = idx % 2 == 0)
                drawWrappedText(canvas, k, 260f, y + 6f, 298f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, v, MARGIN_X + 8f, y + 6f, 210f, valTp, Layout.Alignment.ALIGN_OPPOSITE)
                y += rowH
            }
        } else {
            drawPartyTableHeader()
            if (summary.entries.isEmpty()) {
                y += drawWrappedText(canvas, "لا توجد حركات مسجلة في هذه الفترة", MARGIN_X, y + 8f, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 20f
            } else {
                val entryTitleTp = makeTextPaint(Color.rgb(15, 23, 42), 9.2f, bold = true)
                val debitTp = makeTextPaint(if (summary.isCustomer) Color.rgb(185, 28, 28) else Color.rgb(4, 120, 87), 9.2f, bold = true)
                val creditTp = makeTextPaint(if (summary.isCustomer) Color.rgb(4, 120, 87) else Color.rgb(29, 78, 216), 9.2f, bold = true)
                val dashTp = makeTextPaint(Color.rgb(148, 163, 184), 9.2f, bold = false)
                val balTp = makeTextPaint(Color.rgb(15, 23, 42), 9.2f, bold = true)
                val refTp = makeTextPaint(Color.rgb(30, 41, 59), 8.6f, bold = true)

                summary.entries.forEachIndexed { index, entry ->
                    val idxLayout = buildRtlStaticLayout("${index + 1}", mutedSmallTp, 24f, Layout.Alignment.ALIGN_CENTER)
                    val voucherRef = StatementHelper.formatPartyVoucherRef(entry)
                    val refLayout = buildRtlStaticLayout(voucherRef, refTp, 66f, Layout.Alignment.ALIGN_CENTER)

                    val dateStr = if (entry.timestamp > 0L) FormatUtils.formatDate(entry.timestamp) else ""
                    val dataTitle = if (dateStr.isNotBlank()) "${entry.title}  •  $dateStr" else entry.title
                    val titleLayout = buildRtlStaticLayout(dataTitle, entryTitleTp, 213f, Layout.Alignment.ALIGN_NORMAL)
                    val subLayout = if (entry.subDetails.isNotBlank()) {
                        buildRtlStaticLayout(entry.subDetails, mutedSmallTp, 213f, Layout.Alignment.ALIGN_NORMAL)
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
                    val balLayout = buildRtlStaticLayout(balLabel, balTp, 66f, Layout.Alignment.ALIGN_CENTER)

                    val centerBlockH = titleLayout.height + (if (subLayout != null) subLayout.height + 2 else 0)
                    val rowHeight = maxOf(
                        idxLayout.height,
                        refLayout.height,
                        centerBlockH,
                        debLayout.height,
                        credLayout.height,
                        balLayout.height
                    ).toFloat() + 12f

                    if (y + rowHeight > CONTENT_BOTTOM) nextPartyPage(withTableHeader = true)

                    drawGridRowBorders(canvas, y, y + rowHeight, partyDividers, isOdd = index % 2 == 1)

                    val cellY = y + 6f
                    drawWrappedLayout(canvas, idxLayout, 540f, cellY)
                    drawWrappedLayout(canvas, refLayout, 468f, cellY)
                    drawWrappedLayout(canvas, titleLayout, 248f, cellY)
                    if (subLayout != null) {
                        drawWrappedLayout(canvas, subLayout, 248f, cellY + titleLayout.height + 2f)
                    }
                    drawWrappedLayout(canvas, debLayout, 175f, cellY)
                    drawWrappedLayout(canvas, credLayout, 103f, cellY)
                    drawWrappedLayout(canvas, balLayout, 31f, cellY)

                    y += rowHeight
                }
            }
        }

        // Bottom Framed Summary Card: ملخص كشف الحساب
        val sumCardHeight = 148f
        if (y + sumCardHeight + 48f > CONTENT_BOTTOM) nextPartyPage(withTableHeader = false)
        y += 14f

        val sumStart = y
        val sumRect = RectF(MARGIN_X, sumStart, RIGHT_X, sumStart + sumCardHeight)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(
            sumRect, 8f, 8f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.rgb(203, 213, 225)
            }
        )

        // Header Pill: ملخص كشف الحساب
        canvas.drawRoundRect(
            RectF(MARGIN_X + 10f, sumStart + 10f, RIGHT_X - 10f, sumStart + 34f),
            6f, 6f,
            Paint().apply { color = Color.rgb(15, 30, 54) }
        )
        val sumHdrTp = makeTextPaint(Color.WHITE, 11f, bold = true)
        val periodSuffix = if (summary.periodLabel.isNotBlank()) " (${summary.periodLabel})" else ""
        drawWrappedText(
            canvas,
            "ملخص كشف الحساب$periodSuffix",
            MARGIN_X + 16f,
            sumStart + 16f,
            CONTENT_WIDTH - 32f,
            sumHdrTp,
            Layout.Alignment.ALIGN_CENTER
        )

        var sy = sumStart + 44f
        val rightColLeft = RIGHT_X - 255f
        val leftColLeft = MARGIN_X + 14f
        val debitSumTp = makeTextPaint(if (summary.isCustomer) Color.rgb(185, 28, 28) else Color.rgb(4, 120, 87), 9.8f, bold = true)
        val creditSumTp = makeTextPaint(if (summary.isCustomer) Color.rgb(4, 120, 87) else Color.rgb(29, 78, 216), 9.8f, bold = true)

        val debitLabel = if (summary.isCustomer) {
            "إجمالي مدين (فواتير وديون عليه): ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}"
        } else {
            "إجمالي مدين (مسدد ومرتجعات للمورد): ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}"
        }
        val creditLabel = if (summary.isCustomer) {
            "إجمالي دائن (محصل ومرتجعات له): ${FormatUtils.formatCurrency(summary.totalForParty, currency)}"
        } else {
            "إجمالي دائن (بضائع موردة وديون له): ${FormatUtils.formatCurrency(summary.totalForParty, currency)}"
        }
        drawWrappedText(canvas, debitLabel, rightColLeft, sy, 245f, debitSumTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, creditLabel, leftColLeft, sy, 245f, creditSumTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 24f

        val countRight = "عدد الحركات بالكشف: ${summary.entries.size}  •  فواتير: ${summary.invoicesCount}"
        val countLeft = "سندات: ${summary.paymentsCount}  •  سجلات مفتوحة: ${summary.unsettledDebtsCount}"
        drawWrappedText(canvas, countRight, rightColLeft, sy, 245f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, countLeft, leftColLeft, sy, 245f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
        sy += 24f

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
            RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + 44f),
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
        val netTitleTp = makeTextPaint(pillFg, 11f, bold = true)
        val netWordsTp = makeTextPaint(Color.rgb(51, 65, 85), 8.8f, bold = false)
        drawWrappedText(canvas, remainingTitle, RIGHT_X - 265f, sy + 6f, 250f, netTitleTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, remainingVal, MARGIN_X + 18f, sy + 6f, 225f, netTitleTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(
            canvas,
            "كتابة الرصيد بالنص: ${FormatUtils.amountToArabicWords(kotlin.math.abs(summary.netBalance), currency)}",
            MARGIN_X + 18f,
            sy + 24f,
            CONTENT_WIDTH - 36f,
            netWordsTp,
            Layout.Alignment.ALIGN_NORMAL
        )

        y = sumStart + sumCardHeight + 18f

        drawWrappedText(canvas, "توقيع الطرف الآخر: ________________", 300f, y, 258f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 6f, y, 258f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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
                    details = listOfNotNull(
                        sale.category.takeIf { it.isNotBlank() },
                        sale.size.takeIf { it.isNotBlank() }?.let { "مقاس: $it" },
                        sale.color.takeIf { it.isNotBlank() }?.let { "لون: $it" }
                    ).joinToString(" | "),
                    quantity = sale.quantity,
                    unitPrice = sale.unitSellPrice,
                    total = sale.totalAmount,
                    unit = sale.unit,
                    piecePrice = sale.unitSellPrice,
                    productPrice = sale.totalAmount
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
            y += 12f
        }

        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X, top = 16f, size = 40f)
        y += drawWrappedText(canvas, shopName, MARGIN_X + 50f, y, CONTENT_WIDTH - 100f, titleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        y += drawWrappedText(canvas, "التقرير المالي والتشغيلي الشامل (${report.periodTitle})", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 3f
        y += drawWrappedText(canvas, "تاريخ الاستخراج: ${FormatUtils.formatDateTime(report.generatedAt)}", MARGIN_X, y, CONTENT_WIDTH, subTp, Layout.Alignment.ALIGN_CENTER) + 8f
        y = y.coerceAtLeast(64f)
        canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
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
            val lh = measureWrappedHeight(label, regularTp, 320f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, regularTp, 196f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 9f
            if (y + rowH > CONTENT_BOTTOM) nextReportPage()
            if (idx % 2 == 0) {
                canvas.drawRect(MARGIN_X, y - 3f, RIGHT_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            drawWrappedText(canvas, label, 238f, y, 320f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 34f, y, 196f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (y + 52f > CONTENT_BOTTOM) nextReportPage()
        y += 6f
        val netBg = if (report.netProfit >= 0) Color.rgb(240, 253, 244) else Color.rgb(254, 242, 242)
        val netColor = if (report.netProfit >= 0) Color.rgb(22, 101, 52) else Color.rgb(220, 38, 38)
        canvas.drawRoundRect(RectF(MARGIN_X, y, RIGHT_X, y + 32f), 8f, 8f, Paint().apply { color = netBg })
        val netTp = makeTextPaint(netColor, 12f, bold = true)
        drawWrappedText(canvas, "صافي الربح الفعلي للفترة:", 248f, y + 8f, 308f, netTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(report.netProfit, currency), 34f, y + 8f, 206f, netTp, Layout.Alignment.ALIGN_OPPOSITE)
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
            val lh = measureWrappedHeight(label, regularTp, 320f, Layout.Alignment.ALIGN_NORMAL)
            val vh = measureWrappedHeight(value, regularTp, 196f, Layout.Alignment.ALIGN_OPPOSITE)
            val rowH = maxOf(lh, vh) + 9f
            if (y + rowH > CONTENT_BOTTOM) nextReportPage()
            if (idx % 2 == 0) {
                canvas.drawRect(MARGIN_X, y - 3f, RIGHT_X, y + rowH - 3f, Paint().apply { color = Color.rgb(248, 250, 252) })
            }
            drawWrappedText(canvas, label, 238f, y, 320f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, value, 34f, y, 196f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
        }

        if (topItems.isNotEmpty()) {
            if (y + 44f > CONTENT_BOTTOM) nextReportPage()
            y += 10f
            y += drawWrappedText(canvas, "الأصناف الأكثر مبيعاً خلال الفترة:", MARGIN_X, y, CONTENT_WIDTH, boldTp, Layout.Alignment.ALIGN_NORMAL) + 6f
            topItems.forEach { (name, qty, profit) ->
                val lText = "• $name ($qty حبة)"
                val rText = "ربح: ${FormatUtils.formatCurrency(profit, currency)}"
                val lh = measureWrappedHeight(lText, regularTp, 330f, Layout.Alignment.ALIGN_NORMAL)
                val rh = measureWrappedHeight(rText, regularTp, 186f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, rh) + 8f
                if (y + rowH > CONTENT_BOTTOM) nextReportPage()
                drawWrappedText(canvas, lText, 228f, y, 330f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, rText, 34f, y, 186f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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
                val lh = measureWrappedHeight(lText, regularTp, 285f, Layout.Alignment.ALIGN_NORMAL)
                val rh = measureWrappedHeight(rText, regularTp, 230f, Layout.Alignment.ALIGN_OPPOSITE)
                val rowH = maxOf(lh, rh) + 8f
                if (y + rowH > CONTENT_BOTTOM) nextReportPage()
                drawWrappedText(canvas, lText, 272f, y, 285f, regularTp, Layout.Alignment.ALIGN_NORMAL)
                drawWrappedText(canvas, rText, 34f, y, 230f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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

    // ==========================================
    // 7. Vouchers PDF (سند قبض / سند صرف A4 بنموذج البطاقة الموحد + ترويسة المحل + كتابة المبلغ)
    // ==========================================

    private fun drawStandardVoucherPdf(
        context: Context,
        settings: StoreSettings?,
        isReceiptVoucher: Boolean,
        voucherNumber: String,
        timestamp: Long,
        partyName: String,
        paymentMethodLabel: String,
        amount: Double,
        purposeDetails: String,
        filePrefix: String
    ): File {
        val pdfDocument = PdfDocument()
        val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val shopEn = settings?.shopNameEn?.trim().orEmpty()
        val phone = settings?.phone?.trim().orEmpty()
        val address = settings?.address?.trim().orEmpty()
        val headerNote = settings?.invoiceHeader?.trim().orEmpty()
        val taxNo = settings?.taxNumber?.trim().orEmpty()
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        val navyColor = Color.rgb(15, 30, 54)
        val accentColor = if (isReceiptVoucher) Color.rgb(4, 120, 87) else Color.rgb(154, 52, 18)
        val accentSoftBg = if (isReceiptVoucher) Color.rgb(240, 253, 244) else Color.rgb(255, 247, 237)
        val borderColor = Color.rgb(148, 163, 184)
        val subBorderColor = Color.rgb(203, 213, 225)

        val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas: Canvas = page.canvas

        // 1) Store Header Above Voucher Card
        val shopTitleTp = makeTextPaint(navyColor, 19f, bold = true)
        val shopSubTp = makeTextPaint(Color.rgb(71, 85, 105), 9.8f, bold = false)
        drawStoreLogoIfPresent(canvas, settings, left = MARGIN_X + 6f, top = 26f, size = 50f)

        val hdrLeft = MARGIN_X + 64f
        val hdrW = CONTENT_WIDTH - 128f
        var y = 26f
        val fullTitle = if (shopEn.isNotBlank()) "$shopName  •  $shopEn" else shopName
        y += drawWrappedText(canvas, fullTitle, hdrLeft, y, hdrW, shopTitleTp, Layout.Alignment.ALIGN_CENTER) + 3f
        if (headerNote.isNotBlank()) {
            y += drawWrappedText(canvas, headerNote, hdrLeft, y, hdrW, shopSubTp, Layout.Alignment.ALIGN_CENTER) + 2f
        }
        val infoLine = listOfNotNull(
            address.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" },
            taxNo.takeIf { it.isNotBlank() }?.let { "الرقم الضريبي: $it" }
        ).joinToString("  •  ")
        if (infoLine.isNotBlank()) {
            y += drawWrappedText(canvas, infoLine, hdrLeft, y, hdrW, shopSubTp, Layout.Alignment.ALIGN_CENTER) + 4f
        }
        val voucherKindSub = if (isReceiptVoucher) "سند قبض مالي معتمد — مقاس A4" else "سند صرف مالي معتمد — مقاس A4"
        y += drawWrappedText(canvas, voucherKindSub, hdrLeft, y, hdrW, makeTextPaint(Color.rgb(100, 116, 139), 9f, bold = false), Layout.Alignment.ALIGN_CENTER)
        y = y.coerceAtLeast(86f) + 12f

        // 2) Voucher Card Dimensions & Background Calculation
        val cardLeft = MARGIN_X
        val cardRight = RIGHT_X
        val cardTop = y
        val innerLeft = cardLeft + 16f
        val innerRight = cardRight - 16f
        val innerW = innerRight - innerLeft // 507f
        val halfGap = 10f
        val rightBoxW = (innerW - halfGap) * 0.48f
        val leftBoxW = innerW - halfGap - rightBoxW

        val boxLabelTp = makeTextPaint(Color.rgb(71, 85, 105), 9.5f, bold = true)
        val boxValTp = makeTextPaint(Color.rgb(15, 23, 42), 10.8f, bold = true)
        val numAmtTp = makeTextPaint(accentColor, 13.5f, bold = true)
        val wordsAmtTp = makeTextPaint(Color.rgb(15, 23, 42), 10.2f, bold = true)

        val dateBoxH = 38f
        val partyText = partyName.ifBlank { if (isReceiptVoucher) "عميل نقدي" else "مورد عام" }
        val partyPrefix = if (isReceiptVoucher) "استلمنا من السيد / الجهة:" else "صُرف للسيد / الجهة:"
        val partyTextH = measureWrappedHeight("$partyPrefix $partyText", boxValTp, innerW - 24f, Layout.Alignment.ALIGN_NORMAL)
        val partyBoxH = (partyTextH + 20f).coerceAtLeast(40f)

        val amountWords = FormatUtils.amountToArabicWords(amount, currency)
        val amountWordsH = measureWrappedHeight(amountWords, wordsAmtTp, leftBoxW - 20f, Layout.Alignment.ALIGN_NORMAL)
        val amountRowH = (amountWordsH + 34f).coerceAtLeast(62f)

        val purposePrefix = "وذلك مقابل:"
        val finalPurpose = purposeDetails.ifBlank { if (isReceiptVoucher) "تحصيل مالي وارد" else "دفعة مالية صادرة" }
        val purposeTextH = measureWrappedHeight("$purposePrefix $finalPurpose", boxValTp, innerW - 24f, Layout.Alignment.ALIGN_NORMAL)
        val purposeBoxH = (purposeTextH + 22f).coerceAtLeast(46f)

        val cardHeaderH = 54f
        val sigAreaH = 62f
        val cardTotalH = 8f + cardHeaderH + 12f + dateBoxH + 10f + partyBoxH + 10f + amountRowH + 10f + purposeBoxH + 14f + sigAreaH
        val cardBottom = cardTop + cardTotalH

        // Outer Card Body + Rounded Frame
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, 10f, 10f, Paint().apply { color = Color.WHITE })
        canvas.drawRoundRect(
            cardRect, 10f, 10f,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = borderColor
            }
        )
        // Top Navy Strip inside card
        canvas.drawRoundRect(
            RectF(cardLeft, cardTop, cardRight, cardTop + 8f),
            10f, 10f,
            Paint().apply { color = navyColor }
        )
        canvas.drawRect(cardLeft, cardTop + 4f, cardRight, cardTop + 9f, Paint().apply { color = navyColor })

        // Card Title & Voucher No Row
        var cy = cardTop + 18f
        val voucherTitle = if (isReceiptVoucher) "سند قبض" else "سند صرف"
        val titleBigTp = makeTextPaint(accentColor, 20f, bold = true)
        drawWrappedText(canvas, voucherTitle, innerRight - 180f, cy, 180f, titleBigTp, Layout.Alignment.ALIGN_NORMAL)

        val refNoTp = makeTextPaint(Color.rgb(51, 65, 85), 10.5f, bold = false)
        val origTp = makeTextPaint(accentColor, 10.5f, bold = true)
        val refStr = "رقم السند: #$voucherNumber"
        drawWrappedText(canvas, "أصل", innerLeft, cy + 6f, 40f, origTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "|", innerLeft + 36f, cy + 6f, 12f, refNoTp, Layout.Alignment.ALIGN_CENTER)
        drawWrappedText(canvas, refStr, innerLeft + 48f, cy + 6f, 210f, refNoTp, Layout.Alignment.ALIGN_NORMAL)

        cy += 36f
        canvas.drawLine(innerLeft, cy, innerRight, cy, Paint().apply { color = subBorderColor; strokeWidth = 1f })
        cy += 12f

        fun drawSubFieldBox(left: Float, top: Float, width: Float, height: Float, bg: Int = Color.WHITE) {
            val r = RectF(left, top, left + width, top + height)
            canvas.drawRoundRect(r, 6f, 6f, Paint().apply { color = bg })
            canvas.drawRoundRect(
                r, 6f, 6f,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.9f
                    color = subBorderColor
                }
            )
        }

        // Row 1: التاريخ (right box) | طريقة القبض/الصرف (left box)
        val rightBoxLeft = innerRight - rightBoxW
        val leftBoxLeft = innerLeft
        drawSubFieldBox(rightBoxLeft, cy, rightBoxW, dateBoxH)
        drawSubFieldBox(leftBoxLeft, cy, leftBoxW, dateBoxH)

        drawWrappedText(
            canvas,
            "التاريخ: ${FormatUtils.formatDateTime(timestamp)}",
            rightBoxLeft + 10f,
            cy + 12f,
            rightBoxW - 20f,
            boxValTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        val methodTitle = if (isReceiptVoucher) "طريقة القبض" else "طريقة الصرف"
        drawWrappedText(
            canvas,
            "$methodTitle: $paymentMethodLabel",
            leftBoxLeft + 10f,
            cy + 12f,
            leftBoxW - 20f,
            boxValTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        cy += dateBoxH + 10f

        // Row 2: استلمنا من / صرف للسيد أو الجهة
        drawSubFieldBox(innerLeft, cy, innerW, partyBoxH)
        drawWrappedText(
            canvas,
            "$partyPrefix  $partyText",
            innerLeft + 12f,
            cy + 11f,
            innerW - 24f,
            boxValTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        cy += partyBoxH + 10f

        // Row 3: المبلغ رقمًا (right box) | المبلغ كتابةً (left box)
        drawSubFieldBox(rightBoxLeft, cy, rightBoxW, amountRowH, bg = accentSoftBg)
        drawSubFieldBox(leftBoxLeft, cy, leftBoxW, amountRowH)

        drawWrappedText(canvas, "المبلغ رقمًا", rightBoxLeft + 10f, cy + 8f, rightBoxW - 20f, boxLabelTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(
            canvas,
            FormatUtils.formatCurrency(amount, currency),
            rightBoxLeft + 10f,
            cy + 27f,
            rightBoxW - 20f,
            numAmtTp,
            Layout.Alignment.ALIGN_NORMAL
        )

        drawWrappedText(canvas, "المبلغ كتابةً", leftBoxLeft + 10f, cy + 8f, leftBoxW - 20f, boxLabelTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, amountWords, leftBoxLeft + 10f, cy + 25f, leftBoxW - 20f, wordsAmtTp, Layout.Alignment.ALIGN_NORMAL)
        cy += amountRowH + 10f

        // Row 4: وذلك مقابل
        drawSubFieldBox(innerLeft, cy, innerW, purposeBoxH)
        drawWrappedText(
            canvas,
            "$purposePrefix  $finalPurpose",
            innerLeft + 12f,
            cy + 11f,
            innerW - 24f,
            boxValTp,
            Layout.Alignment.ALIGN_NORMAL
        )
        cy += purposeBoxH + 22f

        // Row 5: 3-Column Signatures (المستلم | المحاسب | توقيع وختم المنشأة)
        val sigGap = 14f
        val sigColW = (innerW - (sigGap * 2f)) / 3f
        val sigLeftX = innerLeft
        val sigMidX = sigLeftX + sigColW + sigGap
        val sigRightX = sigMidX + sigColW + sigGap
        val sigLinePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            strokeWidth = 1f
        }
        val sigTp = makeTextPaint(Color.rgb(71, 85, 105), 9.5f, bold = true)

        listOf(
            sigRightX to "المستلم",
            sigMidX to "المحاسب",
            sigLeftX to "توقيع وختم المنشأة"
        ).forEach { (sx, label) ->
            canvas.drawLine(sx + 6f, cy + 12f, sx + sigColW - 6f, cy + 12f, sigLinePaint)
            drawWrappedText(canvas, label, sx, cy + 18f, sigColW, sigTp, Layout.Alignment.ALIGN_CENTER)
        }

        drawPageFooter(canvas, 1, shopName, continued = false)
        pdfDocument.finishPage(page)

        val vouchersDir = File(context.cacheDir, "vouchers")
        if (!vouchersDir.exists()) vouchersDir.mkdirs()
        val safeName = voucherNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(vouchersDir, "${filePrefix}_$safeName.pdf")
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
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val methodLabel = if (receipt.paymentMethod == "نقداً") {
            "نقداً إلى الصندوق"
        } else {
            "تحويل (${receipt.exchangeAccountName.ifBlank { "صرافة / محفظة" }})"
        }
        val details = buildList {
            if (receipt.notes.isNotBlank()) add(receipt.notes.trim())
            if (receipt.appliedToDebtAmount > 0.01) {
                add("مسدد من حساب الدين: ${FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency)}")
            }
            if (receipt.directCollectionAmount > 0.01) {
                add("تحصيل مباشر / دفعة مقدمة: ${FormatUtils.formatCurrency(receipt.directCollectionAmount, currency)}")
            }
            if (receipt.remainingCustomerDebt > 0.01) {
                add("المتبقي على العميل بعد السند: ${FormatUtils.formatCurrency(receipt.remainingCustomerDebt, currency)}")
            } else {
                add("الحساب مسدد بالكامل")
            }
        }.joinToString("  •  ")

        return drawStandardVoucherPdf(
            context = context,
            settings = settings,
            isReceiptVoucher = true,
            voucherNumber = receipt.voucherNumber,
            timestamp = receipt.timestamp,
            partyName = receipt.payerName,
            paymentMethodLabel = methodLabel,
            amount = receipt.amount,
            purposeDetails = details,
            filePrefix = "سند_قبض"
        )
    }

    fun generateSupplierPaymentVoucherPdf(
        context: Context,
        receipt: SupplierPaymentReceiptResult,
        settings: StoreSettings?
    ): File {
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val methodLabel = if (receipt.paymentMethod == "نقداً") {
            "نقداً من الصندوق"
        } else {
            "تحويل (${receipt.exchangeAccountName.ifBlank { "صرافة / محفظة" }})"
        }
        val details = buildList {
            if (receipt.notes.isNotBlank()) add(receipt.notes.trim())
            if (receipt.appliedToDebtAmount > 0.01) {
                add("مسدد من الحساب الآجل: ${FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency)}")
            }
            if (receipt.directPaymentAmount > 0.01) {
                add("دفعة مباشرة / مقدمة: ${FormatUtils.formatCurrency(receipt.directPaymentAmount, currency)}")
            }
            add("المتبقي للمورد بذمتنا: ${FormatUtils.formatCurrency(receipt.remainingSupplierDebt, currency)}")
        }.joinToString("  •  ")

        return drawStandardVoucherPdf(
            context = context,
            settings = settings,
            isReceiptVoucher = false,
            voucherNumber = receipt.voucherNumber,
            timestamp = receipt.timestamp,
            partyName = receipt.supplierName,
            paymentMethodLabel = methodLabel,
            amount = receipt.amount,
            purposeDetails = details,
            filePrefix = "سند_صرف"
        )
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
            canvas.drawLine(MARGIN_X, y, RIGHT_X, y, linePaint)
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
            val idxL = buildRtlStaticLayout("${idx + 1}", regularTp, 28f, Layout.Alignment.ALIGN_CENTER)
            val nameL = buildRtlStaticLayout(line.productName, boldTp, 244f, Layout.Alignment.ALIGN_NORMAL)
            val descText = listOfNotNull(
                line.category.takeIf { it.isNotBlank() },
                line.size.takeIf { it.isNotBlank() }?.let { "مقاس: $it" },
                line.color.takeIf { it.isNotBlank() }?.let { "لون: $it" }
            ).joinToString(" | ")
            val descL = if (descText.isNotBlank()) buildRtlStaticLayout(descText, mutedSmallTp, 244f, Layout.Alignment.ALIGN_NORMAL) else null
            val qtyL = buildRtlStaticLayout("${line.quantity} حبة", regularTp, 72f, Layout.Alignment.ALIGN_CENTER)
            val priceL = buildRtlStaticLayout(FormatUtils.formatNumber(unitPrice), regularTp, 74f, Layout.Alignment.ALIGN_CENTER)
            val totL = buildRtlStaticLayout("${FormatUtils.formatNumber(line.totalAmount)} $currency", boldTp, 82f, Layout.Alignment.ALIGN_OPPOSITE)

            val centerH = nameL.height + (if (descL != null) descL.height + 2 else 0)
            val rowH = maxOf(idxL.height, centerH, qtyL.height, priceL.height, totL.height).toFloat() + 12f
            if (y + rowH > CONTENT_BOTTOM) nextReturnPage(withTableHeader = true)

            drawRowGridBorders(canvas, y, y + rowH, isOdd = idx % 2 == 1)

            val cellY = y + 6f
            drawWrappedLayout(canvas, idxL, 536f, cellY)
            drawWrappedLayout(canvas, nameL, 284f, cellY)
            if (descL != null) drawWrappedLayout(canvas, descL, 284f, cellY + nameL.height + 2f)
            drawWrappedLayout(canvas, qtyL, 204f, cellY)
            drawWrappedLayout(canvas, priceL, 122f, cellY)
            drawWrappedLayout(canvas, totL, 32f, cellY)
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
                measureWrappedHeight(k, regularTp, 260f, Layout.Alignment.ALIGN_NORMAL),
                measureWrappedHeight(v, boldTp, 225f, Layout.Alignment.ALIGN_OPPOSITE)
            ) + 8f
            Triple(k, v, h)
        }
        val sumBoxH = 62f + measuredBreakdown.sumOf { it.third.toDouble() }.toFloat() + 12f
        if (y + sumBoxH + 56f > CONTENT_BOTTOM) nextReturnPage(withTableHeader = false)
        y += 12f

        val sumRect = RectF(MARGIN_X, y, RIGHT_X, y + sumBoxH)
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply { color = Color.rgb(248, 250, 252) })
        canvas.drawRoundRect(sumRect, 8f, 8f, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(203, 213, 225)
        })

        var sy = y + 10f
        canvas.drawRoundRect(RectF(MARGIN_X + 10f, sy, RIGHT_X - 10f, sy + 44f), 6f, 6f, Paint().apply { color = bannerBg })
        val totalTp = makeTextPaint(bannerFg, 12f, bold = true)
        val totalWordsTp = makeTextPaint(Color.rgb(51, 65, 85), 8.8f, bold = false)
        drawWrappedText(canvas, "إجمالي قيمة المرتجع (${records.sumOf { it.quantity }} حبة):", 260f, sy + 6f, 290f, totalTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, FormatUtils.formatCurrency(totalReturn, currency), MARGIN_X + 18f, sy + 6f, 215f, totalTp, Layout.Alignment.ALIGN_OPPOSITE)
        drawWrappedText(canvas, "كتابة المبلغ بالنص: ${FormatUtils.amountToArabicWords(totalReturn, currency)}", MARGIN_X + 18f, sy + 24f, CONTENT_WIDTH - 36f, totalWordsTp, Layout.Alignment.ALIGN_NORMAL)
        sy += 52f

        measuredBreakdown.forEach { (k, v, h) ->
            drawWrappedText(canvas, k, 295f, sy, 260f, regularTp, Layout.Alignment.ALIGN_NORMAL)
            drawWrappedText(canvas, v, MARGIN_X + 14f, sy, 225f, boldTp, Layout.Alignment.ALIGN_OPPOSITE)
            sy += h
        }
        y += sumBoxH + 14f

        drawWrappedText(canvas, "توقيع الطرف الآخر: ________________", 300f, y, 258f, regularTp, Layout.Alignment.ALIGN_NORMAL)
        drawWrappedText(canvas, "ختم وتوقيع المحل: ________________", MARGIN_X + 6f, y, 258f, regularTp, Layout.Alignment.ALIGN_OPPOSITE)
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
