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
import com.example.data.model.PartyAccount
import com.example.data.model.SaleTransaction
import com.example.data.model.StoreSettings
import java.io.File
import java.io.FileOutputStream

object InvoiceHelper {

    fun shareInvoiceText(context: Context, text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "مشاركة الفاتورة")
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

    fun generateReceiptText(
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ): String {
        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val phone = settings?.phone?.ifBlank { "770315173" } ?: "770315173"
        val address = settings?.address?.ifBlank { "صنعاء - التحرير" } ?: "صنعاء - التحرير"
        val currency = settings?.currency ?: "ريال"
        val footer = settings?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة الأصلية."

        val dateStr = FormatUtils.formatDate(sale.timestamp)
        val timeStr = FormatUtils.formatTime(sale.timestamp)

        val newDebtBalance = previousDebt + (if (sale.paymentType == "آجل (دين)" || sale.paymentType == "بالدين") sale.totalAmount else 0.0)

        return buildString {
            appendLine("════════════════════════════════")
            appendLine("        $shopName")
            appendLine("       $address")
            appendLine("        هاتف: $phone")
            appendLine("════════════════════════════════")
            appendLine("فاتورة مبيعات: #${sale.id}")
            appendLine("التاريخ: $dateStr  |  الوقت: $timeStr")
            appendLine("العميل: ${sale.customerName.ifBlank { "زبون نقدي" }}")
            if (sale.customerPhone.isNotBlank()) {
                appendLine("الهاتف: ${sale.customerPhone}")
            }
            appendLine("البائع: ${sale.employeeName.ifBlank { "صاحب المحل" }}")
            appendLine("طريقة الدفع: ${sale.paymentType}")
            if (sale.exchangeAccountName.isNotBlank()) {
                appendLine("الحساب المحول له: ${sale.exchangeAccountName}")
            }
            if (sale.transferRef.isNotBlank()) {
                appendLine("رقم الحوالة: ${sale.transferRef}")
            }
            appendLine("────────────────────────────────")
            appendLine("الصنف: ${sale.productName}")
            appendLine("المقاس: ${sale.size}  |  اللون: ${sale.color}")
            val unitStr = if (sale.unit.isBlank() || sale.unit == "حبه") "${sale.quantity} حبة" else "${sale.quantity} حبة (${sale.unit})"
            appendLine("الكمية: $unitStr")
            appendLine("سعر الوحدة: ${FormatUtils.formatCurrency(sale.unitSellPrice, currency)}")
            if (sale.discount > 0) {
                appendLine("الخصم الممنوح: - ${FormatUtils.formatCurrency(sale.discount, currency)}")
            }
            appendLine("────────────────────────────────")
            appendLine("الإجمالي المطلوب: ${FormatUtils.formatCurrency(sale.totalAmount, currency)}")
            if (sale.cashAmount > 0) {
                appendLine("المدفوع نقداً (كاش): ${FormatUtils.formatCurrency(sale.cashAmount, currency)}")
            }
            if (sale.transferAmount > 0) {
                appendLine("المدفوع تحويل: ${FormatUtils.formatCurrency(sale.transferAmount, currency)}")
            }
            if (sale.paymentType == "آجل (دين)" || sale.paymentType == "بالدين") {
                appendLine("المبلغ المقيد ديناً: ${FormatUtils.formatCurrency(sale.totalAmount, currency)}")
            }
            if (previousDebt > 0.01 || newDebtBalance > 0.01) {
                appendLine("────────────────────────────────")
                appendLine("الرصيد السابق للعميل: ${FormatUtils.formatCurrency(previousDebt, currency)}")
                appendLine("إجمالي الرصيد المستحق: ${FormatUtils.formatCurrency(newDebtBalance, currency)}")
            }
            appendLine("════════════════════════════════")
            appendLine(footer)
            appendLine("       شكراً لتسوقكم معنا!")
            appendLine("════════════════════════════════")
        }
    }

    fun generatePdfInvoiceFile(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(420, 680, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
        }

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42) // BrandNavy
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val mutedPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 10f
        }

        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val shopName = settings?.shopNameAr?.ifBlank { "محل الأناقة للملابس الرجالية" } ?: "محل الأناقة للملابس الرجالية"
        val phone = settings?.phone?.ifBlank { "770315173" } ?: "770315173"
        val address = settings?.address?.ifBlank { "صنعاء - التحرير" } ?: "صنعاء - التحرير"
        val currency = settings?.currency ?: "ريال"

        var y = 35f

        // Header
        canvas.drawText(shopName, 210f, y, titlePaint)
        y += 18f
        mutedPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(address, 210f, y, mutedPaint)
        y += 14f
        canvas.drawText("هاتف: $phone", 210f, y, mutedPaint)
        y += 15f

        canvas.drawLine(20f, y, 400f, y, linePaint)
        y += 18f

        // Invoice Meta
        mutedPaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("فاتورة رقم: #${sale.id}", 390f, y, boldPaint)
        canvas.drawText("${FormatUtils.formatDate(sale.timestamp)} - ${FormatUtils.formatTime(sale.timestamp)}", 30f, y, mutedPaint)
        y += 18f

        canvas.drawText("العميل: ${sale.customerName.ifBlank { "زبون نقدي" }}", 390f, y, boldPaint)
        canvas.drawText("البائع: ${sale.employeeName.ifBlank { "صاحب المحل" }}", 30f, y, mutedPaint)
        y += 18f

        canvas.drawText("طريقة الدفع: ${sale.paymentType}", 390f, y, boldPaint)
        if (sale.exchangeAccountName.isNotBlank()) {
            canvas.drawText("الحساب: ${sale.exchangeAccountName}", 30f, y, mutedPaint)
        }
        y += 15f

        canvas.drawLine(20f, y, 400f, y, linePaint)
        y += 20f

        // Item Details
        canvas.drawText(sale.productName, 390f, y, boldPaint)
        y += 16f
        canvas.drawText("المقاس: ${sale.size} | اللون: ${sale.color}", 390f, y, mutedPaint)
        y += 16f
        canvas.drawText("الكمية: ${sale.quantity} حبة × ${FormatUtils.formatCurrency(sale.unitSellPrice, currency)}", 390f, y, mutedPaint)
        y += 18f

        if (sale.discount > 0) {
            canvas.drawText("الخصم الممنوح: - ${FormatUtils.formatCurrency(sale.discount, currency)}", 390f, y, mutedPaint)
            y += 16f
        }

        canvas.drawLine(20f, y, 400f, y, linePaint)
        y += 22f

        // Total
        val totalPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(5, 150, 105) // FintechEmerald
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("الإجمالي المطلوب: ${FormatUtils.formatCurrency(sale.totalAmount, currency)}", 390f, y, totalPaint)
        y += 20f

        if (sale.cashAmount > 0) {
            canvas.drawText("المدفوع نقداً: ${FormatUtils.formatCurrency(sale.cashAmount, currency)}", 390f, y, mutedPaint)
            y += 14f
        }
        if (sale.transferAmount > 0) {
            canvas.drawText("المدفوع تحويل: ${FormatUtils.formatCurrency(sale.transferAmount, currency)}", 390f, y, mutedPaint)
            y += 14f
        }

        if (previousDebt > 0.01) {
            val newBal = previousDebt + if (sale.paymentType.contains("دين") || sale.paymentType.contains("آجل")) sale.totalAmount else 0.0
            y += 6f
            canvas.drawLine(20f, y, 400f, y, linePaint)
            y += 16f
            canvas.drawText("الرصيد السابق للعميل: ${FormatUtils.formatCurrency(previousDebt, currency)}", 390f, y, mutedPaint)
            y += 14f
            canvas.drawText("إجمالي الرصيد المستحق: ${FormatUtils.formatCurrency(newBal, currency)}", 390f, y, boldPaint)
            y += 14f
        }

        y += 15f
        canvas.drawLine(20f, y, 400f, y, linePaint)
        y += 20f

        // Footer
        mutedPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(settings?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بالوصل.", 210f, y, mutedPaint)
        y += 15f
        canvas.drawText("شكراً لتسوقكم معنا!", 210f, y, mutedPaint)

        pdfDocument.finishPage(page)

        val invoicesDir = File(context.cacheDir, "invoices")
        if (!invoicesDir.exists()) invoicesDir.mkdirs()
        val file = File(invoicesDir, "فاتورة_${sale.id}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    fun shareInvoice(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ) {
        try {
            val textReceipt = generateReceiptText(sale, settings, customerParty, previousDebt)
            val pdfFile = generatePdfInvoiceFile(context, sale, settings, customerParty, previousDebt)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "فاتورة مبيعات #${sale.id} - ${settings?.shopNameAr ?: "حسابي للمحل"}")
                putExtra(Intent.EXTRA_TEXT, textReceipt)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة أو طباعة الفاتورة"))
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في مشاركة الفاتورة: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun printInvoiceDirect(
        context: Context,
        sale: SaleTransaction,
        settings: StoreSettings?,
        customerParty: PartyAccount? = null,
        previousDebt: Double = 0.0
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                shareInvoice(context, sale, settings, customerParty, previousDebt)
                return
            }

            val pdfFile = generatePdfInvoiceFile(context, sale, settings, customerParty, previousDebt)

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
                    val info = android.print.PrintDocumentInfo.Builder("فاتورة_${sale.id}.pdf")
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
                        val input = java.io.FileInputStream(pdfFile)
                        val output = java.io.FileOutputStream(destination?.fileDescriptor)
                        input.copyTo(output)
                        input.close()
                        output.close()
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            printManager.print("فاتورة_${sale.id}", printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            shareInvoice(context, sale, settings, customerParty, previousDebt)
        }
    }
}
