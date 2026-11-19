package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.PartyLedgerEntry
import com.example.ui.util.PartyLedgerRefType
import com.example.ui.util.StatementHelper

@Composable
fun StatementEntryDetailDialog(
    entry: PartyLedgerEntry,
    party: PartyAccount,
    isCustomer: Boolean,
    sales: List<SaleTransaction>,
    purchases: List<PurchaseRecord>,
    debts: List<DebtRecord>,
    payments: List<DebtPayment>,
    returns: List<ReturnRecord>,
    exchangeAccounts: List<ExchangeAccount> = emptyList(),
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

    val effectiveRefType = remember(entry, isCustomer) {
        entry.refType ?: when (entry.kind) {
            "INVOICE" -> if (isCustomer) PartyLedgerRefType.SALE_INVOICE else PartyLedgerRefType.PURCHASE_INVOICE
            "RETURN" -> if (isCustomer) PartyLedgerRefType.SALE_RETURN else PartyLedgerRefType.PURCHASE_RETURN
            "PAYMENT" -> PartyLedgerRefType.PAYMENT_VOUCHER
            else -> PartyLedgerRefType.DIRECT_DEBT
        }
    }

    when (effectiveRefType) {
        PartyLedgerRefType.SALE_INVOICE -> {
            val items = remember(entry, party, sales) {
                StatementHelper.resolveSaleInvoiceItems(entry, party, sales)
            }
            if (items.isNotEmpty()) {
                val first = items.first()
                val invNo = first.invoiceNumber.ifBlank { "INV-${first.id}" }
                val subtotal = items.sumOf { it.unitSellPrice * it.quantity }
                val discount = items.sumOf { it.discount }
                val netTotal = items.sumOf { it.totalAmount }
                val cashPaid = items.sumOf { it.cashAmount }
                val transferPaid = items.sumOf { it.transferAmount }
                val debtAmount = items.sumOf { it.debtAmount }
                val saleIdSet = remember(items) { items.map { it.id }.toSet() }
                val linkedReturns = remember(returns, saleIdSet) {
                    returns.filter {
                        it.returnType == ReturnTypes.SALE_RETURN && it.originalLineId in saleIdSet
                    }
                }

                fun buildSalePdf(): java.io.File = InvoiceHelper.generateSaleInvoicePdf(
                    context = context,
                    invoiceNumber = invNo,
                    customerName = first.customerName,
                    customerPhone = first.customerPhone,
                    employeeName = first.employeeName,
                    paymentType = first.paymentType,
                    items = items.map { line ->
                        InvoiceHelper.PdfSaleLineItem(
                            name = line.productName,
                            details = listOfNotNull(
                                line.category.takeIf { it.isNotBlank() },
                                line.color.takeIf { it.isNotBlank() }?.let { "لون: $it" },
                                line.size.takeIf { it.isNotBlank() }?.let { "مقاس: $it" }
                            ).joinToString(" | "),
                            quantity = line.quantity,
                            unitPrice = line.unitSellPrice,
                            total = line.totalAmount,
                            unit = line.unit,
                            piecePrice = line.unitSellPrice,
                            productPrice = line.totalAmount
                        )
                    },
                    subtotal = subtotal,
                    discount = discount,
                    netTotal = netTotal,
                    cashPaid = cashPaid,
                    transferPaid = transferPaid,
                    debtAmount = debtAmount,
                    exchangeAccountName = first.exchangeAccountName,
                    transferRef = first.transferRef,
                    timestamp = first.timestamp,
                    settings = settings
                )

                fun buildSaleText(): String = if (items.size == 1) {
                    StatementHelper.formatSaleRecordText(first, settings)
                } else {
                    StatementHelper.formatMultiItemSaleText(
                        invoiceNumber = invNo,
                        customerName = first.customerName,
                        paymentType = first.paymentType,
                        items = items.map { line ->
                            CartSaleItem(
                                product = ProductItem(
                                    id = line.productId,
                                    category = line.category,
                                    modelName = line.productName,
                                    color = line.color,
                                    size = line.size,
                                    quantity = line.quantity,
                                    buyPrice = line.unitBuyPrice,
                                    sellPrice = line.unitSellPrice,
                                    unit = line.unit
                                ),
                                quantity = line.quantity,
                                sellPrice = line.unitSellPrice,
                                unit = line.unit
                            )
                        },
                        discount = discount,
                        netTotal = netTotal,
                        cashPaid = cashPaid,
                        transferPaid = transferPaid,
                        debtAmount = debtAmount,
                        exchangeAccountName = first.exchangeAccountName,
                        timestamp = first.timestamp,
                        settings = settings
                    )
                }

                StoreOperationDialog(
                    onDismissRequest = onDismiss,
                    title = "تفاصيل فاتورة البيع #$invNo",
                    subtitle = "${first.customerName.ifBlank { party.name }} • ${FormatUtils.formatDateTime(first.timestamp)}",
                    icon = Icons.Default.ReceiptLong,
                    accentColor = BrandBlue,
                    maxWidth = 540.dp,
                    footer = {
                        InvoiceDetailFooter(
                            onPrintPdf = { InvoiceHelper.printPdfFile(context, buildSalePdf(), "فاتورة_$invNo") },
                            onSharePdf = { InvoiceHelper.sharePdfFile(context, buildSalePdf(), "مشاركة فاتورة $invNo") },
                            onShareWhatsApp = { StatementHelper.shareText(context, buildSaleText(), "مشاركة فاتورة بيع $invNo") },
                            onClose = onDismiss
                        )
                    }
                ) {
                    DetailHeaderInfoCard(
                        rows = buildList {
                            add("العميل" to first.customerName.ifBlank { party.name })
                            if (first.customerPhone.isNotBlank() || party.phone.isNotBlank()) {
                                add("الهاتف" to first.customerPhone.ifBlank { party.phone })
                            }
                            add("البائع / الكاشير" to first.employeeName.ifBlank { "صاحب المحل" })
                            add("التاريخ والوقت" to FormatUtils.formatDateTime(first.timestamp))
                            add("طريقة الدفع" to first.paymentType)
                            if (first.exchangeAccountName.isNotBlank()) {
                                add("حساب الصرافة" to first.exchangeAccountName)
                            }
                            if (first.transferRef.isNotBlank()) {
                                add("مرجع الحوالة" to "#${first.transferRef}")
                            }
                        }
                    )

                    DialogSectionHeader(
                        title = "الأصناف والمشتريات (${items.size} صنف • ${items.sumOf { it.quantity }} قطعة)",
                        icon = Icons.Default.ShoppingBag,
                        accentColor = PdfHeaderNavy
                    )

                    items.forEachIndexed { idx, line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SlateBg,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${line.productName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SlateTextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(line.totalAmount, currency),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = BrandBlue
                                    )
                                }
                                val meta = listOfNotNull(
                                    line.category.takeIf { it.isNotBlank() },
                                    line.color.takeIf { it.isNotBlank() }?.let { "اللون: $it" },
                                    line.size.takeIf { it.isNotBlank() }?.let { "المقاس: $it" }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(meta, fontSize = 11.sp, color = SlateTextSecondary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "الكمية: ${line.quantity} حبة (${line.unit}) × ${FormatUtils.formatCurrency(line.unitSellPrice, currency)}",
                                        fontSize = 11.5.sp,
                                        color = SlateTextSecondary
                                    )
                                    if (line.discount > 0.01) {
                                        Text(
                                            text = "خصم: ${FormatUtils.formatCurrency(line.discount, currency)}",
                                            fontSize = 11.sp,
                                            color = FintechAmber,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DetailTotalsBreakdownCard(
                        rows = buildList {
                            if (discount > 0.01) {
                                add(Triple("الإجمالي قبل الخصم", FormatUtils.formatCurrency(subtotal, currency), SlateTextPrimary))
                                add(Triple("الخصم الممنوح", "- ${FormatUtils.formatCurrency(discount, currency)}", FintechAmber))
                            }
                            add(Triple("صافي الفاتورة", FormatUtils.formatCurrency(netTotal, currency), PdfHeaderNavy))
                            if (cashPaid > 0.01) add(Triple("المدفوع نقداً (كاش)", FormatUtils.formatCurrency(cashPaid, currency), FintechEmerald))
                            if (transferPaid > 0.01) add(Triple("المدفوع تحويل (${first.exchangeAccountName.ifBlank { "صرافة" }})", FormatUtils.formatCurrency(transferPaid, currency), FintechIndigo))
                            if (debtAmount > 0.01) add(Triple("المتبقي آجل (على العميل)", FormatUtils.formatCurrency(debtAmount, currency), FintechRose))
                        }
                    )

                    if (linkedReturns.isNotEmpty()) {
                        DialogInfoBanner(
                            title = "مرتجعات مسجلة على هذه الفاتورة:",
                            value = "${linkedReturns.sumOf { it.quantity }} قطعة بقيمة ${FormatUtils.formatCurrency(linkedReturns.sumOf { it.totalAmount }, currency)}",
                            containerColor = FintechAmberBg,
                            contentColor = FintechAmber,
                            borderColor = FintechAmberBorder,
                            icon = Icons.AutoMirrored.Filled.Undo
                        )
                    }
                }
            } else {
                FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
            }
        }

        PartyLedgerRefType.PURCHASE_INVOICE -> {
            val items = remember(entry, party, purchases) {
                StatementHelper.resolvePurchaseInvoiceItems(entry, party, purchases)
            }
            if (items.isNotEmpty()) {
                val first = items.first()
                val invNo = first.invoiceNumber.ifBlank { "PUR-${first.id}" }
                val totalCost = items.sumOf { it.totalCost }
                val cashPaid = items.sumOf { it.cashPaid }
                val transferPaid = items.sumOf { it.transferPaid }
                val debtAmount = items.sumOf { it.remainingDebt }
                val purIdSet = remember(items) { items.map { it.id }.toSet() }
                val linkedReturns = remember(returns, purIdSet) {
                    returns.filter {
                        it.returnType == ReturnTypes.PURCHASE_RETURN && it.originalLineId in purIdSet
                    }
                }

                fun buildPurchasePdf(): java.io.File = if (items.size == 1) {
                    InvoiceHelper.generatePurchaseInvoicePdf(context, first, settings)
                } else {
                    InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
                        context = context,
                        invoiceNumber = invNo,
                        supplierName = first.supplierName,
                        paymentMethod = first.paymentMethod,
                        exchangeAccountName = first.exchangeAccountName,
                        transferRef = "",
                        items = items.map { rec ->
                            val mult = ClothingUnits.getMultiplier(rec.unit).coerceAtLeast(1)
                            val count = rec.unitCount.takeIf { it > 0.0 } ?: (rec.quantity.toDouble() / mult)
                            CartPurchaseItem(
                                category = rec.category,
                                modelName = rec.modelName,
                                color = rec.color,
                                size = rec.size,
                                unit = rec.unit,
                                unitCount = count,
                                buyPricePerUnit = rec.unitBuyPrice * mult,
                                sellPricePerUnit = rec.unitSellPrice * mult,
                                colorsCount = rec.colorsCount,
                                notes = rec.notes
                            )
                        },
                        totalCost = totalCost,
                        cashPaid = cashPaid,
                        transferPaid = transferPaid,
                        debtAmount = debtAmount,
                        timestamp = first.timestamp,
                        settings = settings
                    )
                }

                fun buildPurchaseText(): String = if (items.size == 1) {
                    StatementHelper.formatPurchaseRecordText(first, settings)
                } else {
                    StatementHelper.formatMultiItemPurchaseText(
                        invoiceNumber = invNo,
                        supplierName = first.supplierName,
                        paymentMethod = first.paymentMethod,
                        itemsCount = items.size,
                        totalCost = totalCost,
                        cashPaid = cashPaid,
                        transferPaid = transferPaid,
                        debtAmount = debtAmount,
                        exchangeAccountName = first.exchangeAccountName,
                        transferRef = "",
                        timestamp = first.timestamp,
                        settings = settings
                    )
                }

                StoreOperationDialog(
                    onDismissRequest = onDismiss,
                    title = "تفاصيل فاتورة الشراء #$invNo",
                    subtitle = "${first.supplierName.ifBlank { party.name }} • ${FormatUtils.formatDateTime(first.timestamp)}",
                    icon = Icons.Default.Inventory2,
                    accentColor = PdfGoldDark,
                    maxWidth = 540.dp,
                    footer = {
                        InvoiceDetailFooter(
                            onPrintPdf = { InvoiceHelper.printPdfFile(context, buildPurchasePdf(), "مشتريات_$invNo") },
                            onSharePdf = { InvoiceHelper.sharePdfFile(context, buildPurchasePdf(), "مشاركة مشتريات_$invNo") },
                            onShareWhatsApp = { StatementHelper.shareText(context, buildPurchaseText(), "مشاركة فاتورة شراء $invNo") },
                            onClose = onDismiss
                        )
                    }
                ) {
                    DetailHeaderInfoCard(
                        rows = buildList {
                            add("المورد / المعمل" to first.supplierName.ifBlank { party.name })
                            if (party.phone.isNotBlank()) add("الهاتف" to party.phone)
                            add("التاريخ والوقت" to FormatUtils.formatDateTime(first.timestamp))
                            add("طريقة الدفع" to first.paymentMethod)
                            if (first.exchangeAccountName.isNotBlank()) {
                                add("حساب الصرافة" to first.exchangeAccountName)
                            }
                        }
                    )

                    DialogSectionHeader(
                        title = "البضاعة الموردة (${items.size} صنف • ${items.sumOf { it.quantity }} حبة)",
                        icon = Icons.Default.AddBusiness,
                        accentColor = PdfHeaderNavy
                    )

                    items.forEachIndexed { idx, line ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SlateBg,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${line.modelName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SlateTextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(line.totalCost, currency),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = PdfGoldDark
                                    )
                                }
                                val meta = listOfNotNull(
                                    line.category.takeIf { it.isNotBlank() },
                                    line.color.takeIf { it.isNotBlank() }?.let { "اللون: $it" },
                                    line.size.takeIf { it.isNotBlank() }?.let { "المقاس: $it" }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(meta, fontSize = 11.sp, color = SlateTextSecondary)
                                }
                                Text(
                                    text = "الكمية: ${line.quantity} حبة (${line.unit}) • شراء الحبة: ${FormatUtils.formatCurrency(line.unitBuyPrice, currency)} • سعر البيع: ${FormatUtils.formatCurrency(line.unitSellPrice, currency)}",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )
                                if (line.notes.isNotBlank()) {
                                    Text("ملاحظات: ${line.notes}", fontSize = 10.5.sp, color = SlateMuted)
                                }
                            }
                        }
                    }

                    DetailTotalsBreakdownCard(
                        rows = buildList {
                            add(Triple("إجمالي فاتورة الشراء", FormatUtils.formatCurrency(totalCost, currency), PdfHeaderNavy))
                            if (cashPaid > 0.01) add(Triple("المدفوع نقداً من الصندوق", FormatUtils.formatCurrency(cashPaid, currency), FintechEmerald))
                            if (transferPaid > 0.01) add(Triple("المدفوع تحويل (${first.exchangeAccountName.ifBlank { "صرافة" }})", FormatUtils.formatCurrency(transferPaid, currency), FintechIndigo))
                            if (debtAmount > 0.01) add(Triple("المتبقي آجل للمورد", FormatUtils.formatCurrency(debtAmount, currency), FintechRose))
                        }
                    )

                    if (linkedReturns.isNotEmpty()) {
                        DialogInfoBanner(
                            title = "مرتجعات مسجلة للمورد من هذه الفاتورة:",
                            value = "${linkedReturns.sumOf { it.quantity }} قطعة بقيمة ${FormatUtils.formatCurrency(linkedReturns.sumOf { it.totalAmount }, currency)}",
                            containerColor = FintechAmberBg,
                            contentColor = FintechAmber,
                            borderColor = FintechAmberBorder,
                            icon = Icons.AutoMirrored.Filled.Undo
                        )
                    }
                }
            } else {
                FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
            }
        }

        PartyLedgerRefType.SALE_RETURN, PartyLedgerRefType.PURCHASE_RETURN -> {
            val items = remember(entry, party, returns) {
                StatementHelper.resolveReturnItems(entry, party, returns)
            }
            if (items.isNotEmpty()) {
                val first = items.first()
                val isSaleRet = first.returnType == ReturnTypes.SALE_RETURN
                val totalReturn = items.sumOf { it.totalAmount }
                val totalCash = items.sumOf { it.cashAmount }
                val totalTransfer = items.sumOf { it.transferAmount }
                val totalDebtReduced = items.sumOf { it.debtReducedAmount }
                val totalSurplus = items.sumOf { it.surplusCreditAmount }

                StoreOperationDialog(
                    onDismissRequest = onDismiss,
                    title = if (isSaleRet) "تفاصيل مرتجع المبيعات #${first.returnNumber}" else "تفاصيل مرتجع المشتريات #${first.returnNumber}",
                    subtitle = "من الفاتورة #${first.originalInvoiceNumber} • ${FormatUtils.formatDateTime(first.timestamp)}",
                    icon = Icons.AutoMirrored.Filled.Undo,
                    accentColor = FintechAmber,
                    maxWidth = 540.dp,
                    footer = {
                        InvoiceDetailFooter(
                            onPrintPdf = {
                                val file = InvoiceHelper.generateReturnReceiptPdf(context, items, settings)
                                InvoiceHelper.printPdfFile(context, file, "مرتجع_${first.returnNumber}")
                            },
                            onSharePdf = {
                                val file = InvoiceHelper.generateReturnReceiptPdf(context, items, settings)
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة مرتجع #${first.returnNumber}")
                            },
                            onShareWhatsApp = {
                                val txt = StatementHelper.formatReturnReceiptText(items, settings)
                                StatementHelper.shareText(context, txt, "مشاركة مرتجع #${first.returnNumber}")
                            },
                            onClose = onDismiss
                        )
                    }
                ) {
                    DetailHeaderInfoCard(
                        rows = buildList {
                            add((if (isSaleRet) "العميل" else "المورد") to first.partyName.ifBlank { party.name })
                            add("الفاتورة الأصلية" to "#${first.originalInvoiceNumber}")
                            add("تاريخ المرتجع" to FormatUtils.formatDateTime(first.timestamp))
                            add("طريقة التسوية" to first.settlementMethod)
                            if (first.exchangeAccountName.isNotBlank()) add("حساب الصرافة" to first.exchangeAccountName)
                            if (first.notes.isNotBlank()) add("ملاحظات" to first.notes)
                        }
                    )

                    DialogSectionHeader(
                        title = "الأصناف المرتجعة (${items.size} صنف • ${items.sumOf { it.quantity }} قطعة)",
                        icon = Icons.Default.Inventory,
                        accentColor = FintechAmber
                    )

                    items.forEachIndexed { idx, line ->
                        val unitP = if (isSaleRet) line.unitSellPrice else line.unitBuyPrice
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SlateBg,
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${line.productName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(line.totalAmount, currency),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = FintechAmber
                                    )
                                }
                                Text(
                                    text = "${line.quantity} قطعة × ${FormatUtils.formatCurrency(unitP, currency)} • ${line.color} | ${line.size}",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )
                            }
                        }
                    }

                    DetailTotalsBreakdownCard(
                        rows = buildList {
                            add(Triple("إجمالي قيمة المرتجع", FormatUtils.formatCurrency(totalReturn, currency), PdfHeaderNavy))
                            if (totalDebtReduced > 0.01) add(Triple("مخصوم من الدين", FormatUtils.formatCurrency(totalDebtReduced, currency), FintechEmerald))
                            if (totalCash > 0.01) add(Triple("نقداً (كاش)", FormatUtils.formatCurrency(totalCash, currency), BrandBlue))
                            if (totalTransfer > 0.01) add(Triple("تحويل صرافة", FormatUtils.formatCurrency(totalTransfer, currency), FintechIndigo))
                            if (totalSurplus > 0.01) add(Triple("رصيد دائن بالحساب", FormatUtils.formatCurrency(totalSurplus, currency), FintechAmber))
                        }
                    )
                }
            } else {
                FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
            }
        }

        PartyLedgerRefType.PAYMENT_VOUCHER -> {
            val matchedPayments = remember(entry, party, payments) {
                StatementHelper.resolvePaymentRecords(entry, party, payments)
            }
            if (matchedPayments.isNotEmpty()) {
                val first = matchedPayments.first()
                val totalAmount = matchedPayments.sumOf { it.amount }
                val voucherNo = first.voucherNumber.ifBlank { "#${first.id}" }
                val exchName = remember(first, exchangeAccounts) {
                    if (first.exchangeAccountId != 0L) {
                        exchangeAccounts.firstOrNull { it.id == first.exchangeAccountId }?.name.orEmpty()
                    } else ""
                }
                val cleanNotes = remember(first.notes) {
                    first.notes
                        .replace(Regex("سند صرف لمورد:\\s*#[^|]+\\|?"), "")
                        .replace(Regex("سند:\\s*#[^|]+\\|?"), "")
                        .trim()
                }
                val appliedDebtSum = matchedPayments.filter { it.debtId != 0L }.sumOf { it.amount }
                val directSum = matchedPayments.filter { it.debtId == 0L }.sumOf { it.amount }
                val remainingOpenDebt = debts.filter {
                    it.type == party.type && !it.isSettled &&
                        it.partyName.trim().equals(party.name.trim(), ignoreCase = true)
                }.sumOf { it.remainingAmount }

                fun buildVoucherPdf(): java.io.File {
                    return if (isCustomer) {
                        InvoiceHelper.generateCollectionVoucherPdf(
                            context = context,
                            receipt = CollectionReceiptResult(
                                voucherNumber = voucherNo,
                                payerName = party.name,
                                amount = totalAmount,
                                paymentMethod = first.paymentMethod,
                                exchangeAccountName = exchName,
                                appliedToDebtAmount = appliedDebtSum,
                                directCollectionAmount = directSum,
                                remainingCustomerDebt = remainingOpenDebt,
                                notes = cleanNotes,
                                timestamp = first.timestamp
                            ),
                            settings = settings
                        )
                    } else {
                        InvoiceHelper.generateSupplierPaymentVoucherPdf(
                            context = context,
                            receipt = SupplierPaymentReceiptResult(
                                voucherNumber = voucherNo,
                                supplierName = party.name,
                                amount = totalAmount,
                                paymentMethod = first.paymentMethod,
                                exchangeAccountName = exchName,
                                appliedToDebtAmount = appliedDebtSum,
                                directPaymentAmount = directSum,
                                remainingSupplierDebt = remainingOpenDebt,
                                notes = cleanNotes,
                                timestamp = first.timestamp
                            ),
                            settings = settings
                        )
                    }
                }

                fun buildVoucherText(): String {
                    return if (isCustomer) {
                        StatementHelper.formatCollectionVoucherText(
                            CollectionReceiptResult(
                                voucherNumber = voucherNo,
                                payerName = party.name,
                                amount = totalAmount,
                                paymentMethod = first.paymentMethod,
                                exchangeAccountName = exchName,
                                appliedToDebtAmount = appliedDebtSum,
                                directCollectionAmount = directSum,
                                remainingCustomerDebt = remainingOpenDebt,
                                notes = cleanNotes,
                                timestamp = first.timestamp
                            ),
                            settings
                        )
                    } else {
                        StatementHelper.formatSupplierPaymentVoucherText(
                            SupplierPaymentReceiptResult(
                                voucherNumber = voucherNo,
                                supplierName = party.name,
                                amount = totalAmount,
                                paymentMethod = first.paymentMethod,
                                exchangeAccountName = exchName,
                                appliedToDebtAmount = appliedDebtSum,
                                directPaymentAmount = directSum,
                                remainingSupplierDebt = remainingOpenDebt,
                                notes = cleanNotes,
                                timestamp = first.timestamp
                            ),
                            settings
                        )
                    }
                }

                StoreOperationDialog(
                    onDismissRequest = onDismiss,
                    title = if (isCustomer) "تفاصيل سند القبض #$voucherNo" else "تفاصيل سند الصرف #$voucherNo",
                    subtitle = "${party.name} • ${FormatUtils.formatDateTime(first.timestamp)}",
                    icon = Icons.Default.Payments,
                    accentColor = if (isCustomer) FintechEmerald else FintechRose,
                    maxWidth = 520.dp,
                    footer = {
                        InvoiceDetailFooter(
                            onPrintPdf = {
                                val job = if (isCustomer) "سند_قبض_$voucherNo" else "سند_صرف_$voucherNo"
                                InvoiceHelper.printPdfFile(context, buildVoucherPdf(), job)
                            },
                            onSharePdf = {
                                val title = if (isCustomer) "مشاركة سند قبض #$voucherNo" else "مشاركة سند صرف #$voucherNo"
                                InvoiceHelper.sharePdfFile(context, buildVoucherPdf(), title)
                            },
                            onShareWhatsApp = {
                                StatementHelper.shareText(context, buildVoucherText(), "مشاركة سند #$voucherNo")
                            },
                            onClose = onDismiss
                        )
                    }
                ) {
                    DetailHeaderInfoCard(
                        rows = buildList {
                            add("رقم السند" to "#$voucherNo")
                            add((if (isCustomer) "استلمنا من" else "صرفنا إلى") to party.name)
                            add("التاريخ والوقت" to FormatUtils.formatDateTime(first.timestamp))
                            add("طريقة السداد" to first.paymentMethod)
                            if (exchName.isNotBlank()) add("حساب الصرافة" to exchName)
                            if (cleanNotes.isNotBlank()) add("البيان / الملاحظات" to cleanNotes)
                        }
                    )

                    DetailTotalsBreakdownCard(
                        rows = buildList {
                            add(
                                Triple(
                                    if (isCustomer) "إجمالي المبلغ المحصل" else "إجمالي المبلغ المسدد",
                                    FormatUtils.formatCurrency(totalAmount, currency),
                                    if (isCustomer) FintechEmerald else FintechRose
                                )
                            )
                            if (appliedDebtSum > 0.01) {
                                add(Triple("تغطية من سجل الديون", FormatUtils.formatCurrency(appliedDebtSum, currency), SlateTextPrimary))
                            }
                            if (directSum > 0.01) {
                                add(Triple("دفعة مباشرة / فائض", FormatUtils.formatCurrency(directSum, currency), BrandBlue))
                            }
                        }
                    )
                }
            } else {
                FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
            }
        }

        PartyLedgerRefType.DIRECT_DEBT -> {
            val debt = remember(entry, debts) {
                StatementHelper.resolveDebtRecord(entry, debts)
            }
            if (debt != null) {
                val linkedPayments = remember(debt, payments) {
                    payments.filter { it.debtId == debt.id }.sortedBy { it.timestamp }
                }
                StoreOperationDialog(
                    onDismissRequest = onDismiss,
                    title = "تفاصيل القيد / الدين",
                    subtitle = "${party.name} • ${FormatUtils.formatDateTime(debt.createdAt)}",
                    icon = Icons.Default.Description,
                    accentColor = PdfGoldDark,
                    maxWidth = 520.dp,
                    footer = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("إغلاق", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    DetailHeaderInfoCard(
                        rows = buildList {
                            add((if (isCustomer) "العميل" else "المورد") to debt.partyName)
                            add("البيان / السبب" to debt.details.ifBlank { entry.title })
                            add("تاريخ التسجيل" to FormatUtils.formatDateTime(debt.createdAt))
                            if (debt.dueDate > 0L) add("تاريخ الاستحقاق" to FormatUtils.formatDate(debt.dueDate))
                            add("الحالة" to if (debt.isSettled) "مسدد بالكامل" else "مفتوح (قيد السداد)")
                        }
                    )

                    DetailTotalsBreakdownCard(
                        rows = listOf(
                            Triple("المبلغ الأصلي", FormatUtils.formatCurrency(debt.originalAmount, currency), PdfHeaderNavy),
                            Triple("المسدد حتى الآن", FormatUtils.formatCurrency(debt.paidAmount, currency), FintechEmerald),
                            Triple("المتبقي", FormatUtils.formatCurrency(debt.remainingAmount, currency), if (debt.isSettled) FintechEmerald else FintechRose)
                        )
                    )

                    if (linkedPayments.isNotEmpty()) {
                        DialogSectionHeader(
                            title = "الدفعات المسددة على هذا الدين (${linkedPayments.size})",
                            icon = Icons.Default.History,
                            accentColor = FintechEmerald
                        )
                        linkedPayments.forEach { p ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SlateBg,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (p.voucherNumber.isNotBlank()) "سند #${p.voucherNumber}" else "دفعة سداد",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${FormatUtils.formatDate(p.timestamp)} • ${p.paymentMethod}",
                                            fontSize = 11.sp,
                                            color = SlateTextSecondary
                                        )
                                    }
                                    Text(
                                        text = FormatUtils.formatCurrency(p.amount, currency),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = FintechEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
            }
        }

        PartyLedgerRefType.OPENING_BALANCE -> {
            FallbackStatementEntryDialog(entry = entry, currency = currency, isCustomer = isCustomer, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun DetailHeaderInfoCard(rows: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SlateBg,
        border = BorderStroke(1.dp, SlateBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEachIndexed { idx, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$label:",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                }
                if (idx < rows.lastIndex) {
                    HorizontalDivider(color = SlateBorderLight)
                }
            }
        }
    }
}

@Composable
private fun DetailTotalsBreakdownCard(rows: List<Triple<String, String, Color>>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PdfGoldBg,
        border = BorderStroke(1.dp, PdfGoldPrimary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { (label, value, color) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SlateTextPrimary)
                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
                }
            }
        }
    }
}

@Composable
private fun InvoiceDetailFooter(
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPrintPdf,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_print_pdf_button")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("طباعة PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                onClick = onSharePdf,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_share_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("مشاركة PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onShareWhatsApp,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                modifier = Modifier
                    .weight(1.3f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_share_whatsapp_button")
            ) {
                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("مشاركة واتساب", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .weight(0.7f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_close_button")
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FallbackStatementEntryDialog(
    entry: PartyLedgerEntry,
    currency: String,
    isCustomer: Boolean,
    onDismiss: () -> Unit
) {
    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = entry.title,
        subtitle = FormatUtils.formatDateTime(entry.timestamp),
        icon = Icons.Default.Receipt,
        accentColor = PdfGoldPrimary,
        maxWidth = 480.dp,
        footer = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        DetailHeaderInfoCard(
            rows = buildList {
                add("العملية" to entry.title)
                if (entry.subDetails.isNotBlank()) add("التفاصيل" to entry.subDetails)
                add("التاريخ" to FormatUtils.formatDateTime(entry.timestamp))
            }
        )
        DetailTotalsBreakdownCard(
            rows = buildList {
                if (isCustomer) {
                    if (entry.debit > 0.01) add(Triple("مبلغ عليه", FormatUtils.formatCurrency(entry.debit, currency), FintechRose))
                    if (entry.credit > 0.01) add(Triple("مبلغ له", FormatUtils.formatCurrency(entry.credit, currency), FintechEmerald))
                } else {
                    if (entry.credit > 0.01) add(Triple("مبلغ له", FormatUtils.formatCurrency(entry.credit, currency), BrandBlue))
                    if (entry.debit > 0.01) add(Triple("المسدد له", FormatUtils.formatCurrency(entry.debit, currency), FintechEmerald))
                }
                add(
                    Triple(
                        "الرصيد بعد العملية",
                        when {
                            entry.runningBalance > 0.01 -> FormatUtils.formatCurrency(entry.runningBalance, currency)
                            entry.runningBalance < -0.01 -> "فائض ${FormatUtils.formatCurrency(-entry.runningBalance, currency)}"
                            else -> "خالص (0 $currency)"
                        },
                        PdfHeaderNavy
                    )
                )
            }
        )
    }
}
