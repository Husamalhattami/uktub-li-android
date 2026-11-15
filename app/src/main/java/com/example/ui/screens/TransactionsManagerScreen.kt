package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CartPurchaseItem
import com.example.data.model.CartSaleItem
import com.example.data.model.ExchangeAccount
import com.example.data.model.ProductItem
import com.example.data.model.PurchaseRecord
import com.example.data.model.SaleTransaction
import com.example.ui.StoreViewModel
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.DialogSectionHeader
import com.example.ui.components.DialogSegmentedChoice
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper
import kotlin.math.abs

private data class EditableSaleLine(
    val source: SaleTransaction,
    val qtyStr: String,
    val unitPriceStr: String
)

private data class EditablePurchaseLine(
    val source: PurchaseRecord,
    val qtyStr: String,
    val buyPriceStr: String,
    val sellPriceStr: String
)

@Composable
fun TransactionsManagerScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val exchangeAccountsAll by viewModel.exchangeAccountsIncludingArchived.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sales, 1: Purchases
    var searchQuery by remember { mutableStateOf("") }

    var saleGroupToDelete by remember { mutableStateOf<List<SaleTransaction>?>(null) }
    var saleGroupToEdit by remember { mutableStateOf<List<SaleTransaction>?>(null) }

    var purchaseGroupToDelete by remember { mutableStateOf<List<PurchaseRecord>?>(null) }
    var purchaseGroupToEdit by remember { mutableStateOf<List<PurchaseRecord>?>(null) }

    var pendingPinAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val groupedSales = remember(sales, searchQuery) {
        val groups = sales.groupBy {
            if (it.invoiceNumber.isNotBlank()) "INV:${it.invoiceNumber.trim()}" else "ID:${it.id}"
        }.values.toList().sortedByDescending { group -> group.maxOfOrNull { it.timestamp } ?: 0L }
        if (searchQuery.isBlank()) groups
        else groups.filter { group ->
            group.any {
                it.productName.contains(searchQuery, ignoreCase = true) ||
                    it.customerName.contains(searchQuery, ignoreCase = true) ||
                    it.employeeName.contains(searchQuery, ignoreCase = true) ||
                    it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    it.color.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedPurchases = remember(purchases, searchQuery) {
        val groups = purchases.groupBy {
            if (it.invoiceNumber.isNotBlank()) "INV:${it.invoiceNumber.trim()}" else "ID:${it.id}"
        }.values.toList().sortedByDescending { group -> group.maxOfOrNull { it.timestamp } ?: 0L }
        if (searchQuery.isBlank()) groups
        else groups.filter { group ->
            group.any {
                it.modelName.contains(searchQuery, ignoreCase = true) ||
                    it.supplierName.contains(searchQuery, ignoreCase = true) ||
                    it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    it.color.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "سجل الفواتير (تعديل وحذف موحد)",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "فواتير المبيعات (${groupedSales.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "فواتير المشتريات (${groupedPurchases.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) }
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedTab == 0) "ابحث برقم الفاتورة، الصنف، الزبون، البائع..."
                            else "ابحث برقم الفاتورة، الصنف، المورد..."
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (selectedTab == 0) {
                    if (groupedSales.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد فواتير مبيعات مسجلة", color = SlateMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = groupedSales,
                                key = { group ->
                                    val first = group.first()
                                    if (first.invoiceNumber.isNotBlank()) "S_INV_${first.invoiceNumber}" else "S_ID_${first.id}"
                                }
                            ) { group ->
                                val first = group.first()
                                val invNo = first.invoiceNumber.ifBlank { "INV-${first.id}" }
                                val subtotal = group.sumOf { it.unitSellPrice * it.quantity }
                                val discount = group.sumOf { it.discount }
                                val netTotal = group.sumOf { it.totalAmount }
                                val cashPaid = group.sumOf { it.cashAmount }
                                val transferPaid = group.sumOf { it.transferAmount }
                                val debtAmount = group.sumOf { it.debtAmount }

                                val buildSalePdf = {
                                    InvoiceHelper.generateSaleInvoicePdf(
                                        context = context,
                                        invoiceNumber = invNo,
                                        customerName = first.customerName,
                                        customerPhone = first.customerPhone,
                                        employeeName = first.employeeName,
                                        paymentType = first.paymentType,
                                        items = group.map { line ->
                                            InvoiceHelper.PdfSaleLineItem(
                                                name = line.productName,
                                                details = "${line.category} | ${line.color} | ${line.size}",
                                                quantity = line.quantity,
                                                unitPrice = line.unitSellPrice,
                                                total = line.totalAmount,
                                                unit = line.unit
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
                                        settings = storeSettings
                                    )
                                }

                                SaleInvoiceGroupCard(
                                    items = group,
                                    onEdit = { saleGroupToEdit = group },
                                    onDelete = { saleGroupToDelete = group },
                                    onPrintPdf = {
                                        InvoiceHelper.printPdfFile(context, buildSalePdf(), "فاتورة_$invNo")
                                    },
                                    onSharePdf = {
                                        InvoiceHelper.sharePdfFile(context, buildSalePdf(), "مشاركة فاتورة_$invNo")
                                    },
                                    onShareText = {
                                        val txt = if (group.size == 1) {
                                            StatementHelper.formatSaleRecordText(first, storeSettings)
                                        } else {
                                            StatementHelper.formatMultiItemSaleText(
                                                invoiceNumber = invNo,
                                                customerName = first.customerName,
                                                paymentType = first.paymentType,
                                                items = group.map { line ->
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
                                                settings = storeSettings
                                            )
                                        }
                                        StatementHelper.shareText(context, txt, "مشاركة فاتورة بيع $invNo")
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (groupedPurchases.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد فواتير مشتريات مسجلة", color = SlateMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = groupedPurchases,
                                key = { group ->
                                    val first = group.first()
                                    if (first.invoiceNumber.isNotBlank()) "P_INV_${first.invoiceNumber}" else "P_ID_${first.id}"
                                }
                            ) { group ->
                                val first = group.first()
                                val invNo = first.invoiceNumber.ifBlank { "PUR-${first.id}" }
                                val totalCost = group.sumOf { it.totalCost }
                                val cashPaid = group.sumOf { it.cashPaid }
                                val transferPaid = group.sumOf { it.transferPaid }
                                val debtAmount = group.sumOf { it.remainingDebt }

                                val buildPurchasePdf = {
                                    if (group.size == 1) {
                                        InvoiceHelper.generatePurchaseInvoicePdf(context, first, storeSettings)
                                    } else {
                                        InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
                                            context = context,
                                            invoiceNumber = invNo,
                                            supplierName = first.supplierName,
                                            paymentMethod = first.paymentMethod,
                                            exchangeAccountName = first.exchangeAccountName,
                                            transferRef = "",
                                            items = group.map { rec ->
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
                                            settings = storeSettings
                                        )
                                    }
                                }

                                PurchaseInvoiceGroupCard(
                                    items = group,
                                    onEdit = { purchaseGroupToEdit = group },
                                    onDelete = { purchaseGroupToDelete = group },
                                    onPrintPdf = {
                                        InvoiceHelper.printPdfFile(context, buildPurchasePdf(), "مشتريات_$invNo")
                                    },
                                    onSharePdf = {
                                        InvoiceHelper.sharePdfFile(context, buildPurchasePdf(), "مشاركة مشتريات_$invNo")
                                    },
                                    onShareText = {
                                        val txt = if (group.size == 1) {
                                            StatementHelper.formatPurchaseRecordText(first, storeSettings)
                                        } else {
                                            StatementHelper.formatMultiItemPurchaseText(
                                                invoiceNumber = invNo,
                                                supplierName = first.supplierName,
                                                paymentMethod = first.paymentMethod,
                                                itemsCount = group.size,
                                                totalCost = totalCost,
                                                cashPaid = cashPaid,
                                                transferPaid = transferPaid,
                                                debtAmount = debtAmount,
                                                exchangeAccountName = first.exchangeAccountName,
                                                transferRef = "",
                                                timestamp = first.timestamp,
                                                settings = storeSettings
                                            )
                                        }
                                        StatementHelper.shareText(context, txt, "مشاركة فاتورة شراء $invNo")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Delete Whole Sale Invoice Confirmation Dialog ---
    saleGroupToDelete?.let { group ->
        val first = group.first()
        val label = first.invoiceNumber.ifBlank { "#${first.id}" }
        val totalPieces = group.sumOf { it.quantity }
        val totalAmount = group.sumOf { it.totalAmount }
        val totalCash = group.sumOf { it.cashAmount }
        val totalTransfer = group.sumOf { it.transferAmount }
        val totalDebt = group.sumOf { it.debtAmount }

        StoreOperationDialog(
            onDismissRequest = { saleGroupToDelete = null },
            title = "تأكيد إلغاء وحذف فاتورة البيع ($label)",
            subtitle = "${group.size} أصناف • إجمالي $totalPieces حبة",
            icon = Icons.Default.Warning,
            accentColor = RubyExpense,
            confirmText = "تأكيد الإلغاء الكامل",
            confirmIcon = Icons.Default.DeleteOutline,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_delete_sale_button",
            onConfirm = {
                val invNo = first.invoiceNumber
                val firstId = first.id
                saleGroupToDelete = null
                pendingPinAction = "تأكيد رمز المدير لإلغاء فاتورة البيع ($label)" to {
                    if (invNo.isNotBlank()) {
                        viewModel.deleteSaleInvoice(invNo)
                    } else {
                        viewModel.deleteSale(firstId)
                    }
                }
            }
        ) {
            DialogInfoBanner(
                title = "صافي الفاتورة التي سيتم عكسها:",
                value = FormatUtils.formatCurrency(totalAmount),
                containerColor = Color(0xFFFFF1F2),
                contentColor = RubyExpense,
                borderColor = Color(0xFFFECDD3),
                icon = Icons.Default.ReceiptLong
            )
            Text(
                text = buildString {
                    appendLine("سيتم إلغاء الفاتورة بالكامل (${group.size} أصناف) وتنفيذ الآتي:")
                    appendLine("• إعادة كامل الكمية ($totalPieces حبة) إلى المخزون.")
                    if (totalCash > 0.01) appendLine("• خصم ${FormatUtils.formatCurrency(totalCash)} من الصندوق النقدي.")
                    if (totalTransfer > 0.01) appendLine("• عكس تحويل صرافة بقيمة ${FormatUtils.formatCurrency(totalTransfer)} وإلغاء القيد.")
                    if (totalDebt > 0.01) appendLine("• حذف سجل دين العميل المرتبط بقيمة ${FormatUtils.formatCurrency(totalDebt)}.")
                }.trimEnd(),
                fontSize = 13.sp,
                color = SlateTextPrimary,
                lineHeight = 20.sp
            )
        }
    }

    // --- Edit Whole Sale Invoice Dialog ---
    saleGroupToEdit?.let { group ->
        val first = group.first()
        val originalKey = if (first.invoiceNumber.isNotBlank()) first.invoiceNumber else "ID:${first.id}"
        var invoiceNumber by remember(originalKey) { mutableStateOf(first.invoiceNumber) }
        var customerName by remember(originalKey) { mutableStateOf(first.customerName) }
        var customerPhone by remember(originalKey) { mutableStateOf(first.customerPhone) }
        var discountStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(group.sumOf { it.discount })) }
        var editTimestamp by remember(originalKey) { mutableLongStateOf(first.timestamp) }

        val lineStates = remember(originalKey) {
            mutableStateListOf<EditableSaleLine>().apply {
                addAll(
                    group.map {
                        EditableSaleLine(
                            source = it,
                            qtyStr = it.quantity.toString(),
                            unitPriceStr = FormatUtils.formatInputNumber(it.unitSellPrice)
                        )
                    }
                )
            }
        }

        val initialCash = group.sumOf { it.cashAmount }
        val initialTransfer = group.sumOf { it.transferAmount }
        val initialDebt = group.sumOf { it.debtAmount }
        val initialPaymentType = remember(originalKey) {
            when {
                first.paymentType == "تحويل صرافة" || (initialTransfer > 0.01 && initialCash <= 0.01 && initialDebt <= 0.01) -> "تحويل صرافة"
                first.paymentType == "نصف كاش ونصف حوالة" || (initialCash > 0.01 && initialTransfer > 0.01 && initialDebt <= 0.01) -> "نصف كاش ونصف حوالة"
                first.paymentType == "بالدين" || first.paymentType == "آجل" || (initialDebt > 0.01 && initialCash <= 0.01 && initialTransfer <= 0.01) -> "بالدين"
                first.paymentType == "دفع مجزأ (مخصص)" || (initialCash > 0.01 && initialDebt > 0.01) || (initialTransfer > 0.01 && initialDebt > 0.01) -> "دفع مجزأ (مخصص)"
                else -> "نقداً"
            }
        }

        var paymentType by remember(originalKey) { mutableStateOf(initialPaymentType) }
        var cashPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialCash)) }
        var transferPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialTransfer)) }
        var debtPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialDebt)) }
        var transferRef by remember(originalKey) { mutableStateOf(first.transferRef) }
        var selectedAccount by remember(originalKey) {
            mutableStateOf(
                exchangeAccountsAll.firstOrNull { it.id == first.exchangeAccountId }
                    ?: exchangeAccounts.firstOrNull()
            )
        }
        var localError by remember { mutableStateOf<String?>(null) }

        val subtotal = lineStates.sumOf { line ->
            val q = (line.qtyStr.toIntOrNull() ?: 0).coerceAtLeast(0)
            val p = (line.unitPriceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            q * p
        }
        val discount = (discountStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        val netTotal = (subtotal - discount).coerceAtLeast(0.0)

        val splitInputsSum = (cashPartStr.toDoubleOrNull() ?: 0.0) +
            (transferPartStr.toDoubleOrNull() ?: 0.0) +
            (if (paymentType == "دفع مجزأ (مخصص)") (debtPartStr.toDoubleOrNull() ?: 0.0) else 0.0)
        val splitDiff = netTotal - splitInputsSum

        StoreOperationDialog(
            onDismissRequest = { saleGroupToEdit = null },
            title = "تعديل فاتورة البيع (${first.invoiceNumber.ifBlank { "#${first.id}" }})",
            subtitle = "${group.size} أصناف • تعديل موحد للأصناف والدفع والأرصدة",
            icon = Icons.Default.Edit,
            accentColor = PdfHeaderNavy,
            maxWidth = 580.dp,
            confirmText = "حفظ تعديل الفاتورة",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_edit_sale_invoice_button",
            onConfirm = {
                localError = null
                if (lineStates.any { (it.qtyStr.toIntOrNull() ?: 0) <= 0 || (it.unitPriceStr.toDoubleOrNull() ?: 0.0) <= 0.0 }) {
                    localError = "يرجى التأكد أن كمية وسعر كل صنف أكبر من الصفر."
                    return@StoreOperationDialog
                }
                if (discount < 0 || discount >= subtotal) {
                    localError = "الخصم يجب أن يكون أقل من إجمالي الأصناف."
                    return@StoreOperationDialog
                }

                val (finalCash, finalTransfer, finalDebt) = when (paymentType) {
                    "نقداً" -> Triple(netTotal, 0.0, 0.0)
                    "تحويل صرافة" -> Triple(0.0, netTotal, 0.0)
                    "بالدين" -> Triple(0.0, 0.0, netTotal)
                    "نصف كاش ونصف حوالة" -> {
                        val c = cashPartStr.toDoubleOrNull() ?: -1.0
                        val t = transferPartStr.toDoubleOrNull() ?: -1.0
                        if (c < 0 || t < 0) {
                            localError = "لا يمكن إدخال مبالغ دفع سالبة."
                            return@StoreOperationDialog
                        }
                        if (abs((c + t) - netTotal) > 0.01) {
                            localError = "مجموع الكاش والتحويل يجب أن يطابق الصافي (${FormatUtils.formatCurrency(netTotal)})."
                            return@StoreOperationDialog
                        }
                        Triple(c, t, 0.0)
                    }
                    else -> {
                        val c = cashPartStr.toDoubleOrNull() ?: -1.0
                        val t = transferPartStr.toDoubleOrNull() ?: -1.0
                        val d = debtPartStr.toDoubleOrNull() ?: -1.0
                        if (c < 0 || t < 0 || d < 0) {
                            localError = "لا يمكن إدخال مبالغ دفع سالبة."
                            return@StoreOperationDialog
                        }
                        if (abs((c + t + d) - netTotal) > 0.01) {
                            localError = "مجموع المبالغ المجزأة يجب أن يطابق الصافي (${FormatUtils.formatCurrency(netTotal)})."
                            return@StoreOperationDialog
                        }
                        Triple(c, t, d)
                    }
                }

                if (finalTransfer > 0.001 && selectedAccount == null) {
                    localError = "يرجى اختيار حساب الصراف أو المحفظة للدفعة المحولة."
                    return@StoreOperationDialog
                }
                if (finalDebt > 0.001 && customerName.trim().isBlank()) {
                    localError = "يرجى إدخال اسم الزبون لتسجيل المبلغ الآجل."
                    return@StoreOperationDialog
                }

                val updatedSales = lineStates.map { line ->
                    val q = line.qtyStr.toInt()
                    val p = line.unitPriceStr.toDouble()
                    val gross = q * p
                    val lineDiscount = if (subtotal > 0.0) discount * (gross / subtotal) else 0.0
                    val lineNet = (gross - lineDiscount).coerceAtLeast(0.0)
                    val lineProfit = lineNet - (line.source.unitBuyPrice * q)
                    line.source.copy(
                        quantity = q,
                        unitSellPrice = p,
                        discount = lineDiscount,
                        totalAmount = lineNet,
                        profit = lineProfit
                    )
                }

                viewModel.updateSaleInvoice(
                    originalInvoiceKey = originalKey,
                    updatedItems = updatedSales,
                    newInvoiceNumber = invoiceNumber,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paymentType = paymentType,
                    cashAmount = finalCash,
                    transferAmount = finalTransfer,
                    debtAmount = finalDebt,
                    exchangeAccount = if (finalTransfer > 0.001) selectedAccount else null,
                    transferRef = transferRef,
                    timestamp = editTimestamp,
                    onSuccess = { saleGroupToEdit = null }
                )
            }
        ) {
            DialogSectionHeader(title = "أصناف الفاتورة (${lineStates.size})", icon = Icons.Default.Inventory2, accentColor = PdfHeaderNavy)
            lineStates.forEachIndexed { idx, line ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${idx + 1}. ${line.source.productName} (${line.source.color} | ${line.source.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PdfHeaderNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = line.qtyStr,
                                onValueChange = { v -> lineStates[idx] = line.copy(qtyStr = v) },
                                label = { Text("الكمية (حبه)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = line.unitPriceStr,
                                onValueChange = { v -> lineStates[idx] = line.copy(unitPriceStr = v) },
                                label = { Text("سعر الحبة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = discountStr,
                    onValueChange = { discountStr = it },
                    label = { Text("إجمالي الخصم (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = storeDialogTextFieldColors(FintechAmber),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("رقم الفاتورة") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("اسم الزبون") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            DialogSectionHeader(title = "طريقة الدفع والحساب", icon = Icons.Default.Payments, accentColor = FintechEmerald)
            val salePayModes = listOf("نقداً", "تحويل صرافة", "نصف كاش ونصف حوالة", "بالدين", "دفع مجزأ (مخصص)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(salePayModes) { mode ->
                    FilterChip(
                        selected = paymentType == mode,
                        onClick = {
                            paymentType = mode
                            if (mode == "نصف كاش ونصف حوالة") {
                                val half = kotlin.math.round((netTotal / 2.0) * 100.0) / 100.0
                                cashPartStr = FormatUtils.formatInputNumber(half)
                                transferPartStr = FormatUtils.formatInputNumber(netTotal - half)
                            }
                        },
                        label = { Text(mode, fontSize = 11.sp) }
                    )
                }
            }

            if (paymentType == "نصف كاش ونصف حوالة" || paymentType == "دفع مجزأ (مخصص)") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cashPartStr,
                        onValueChange = { cashPartStr = it },
                        label = { Text("كاش") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = storeDialogTextFieldColors(FintechEmerald),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = transferPartStr,
                        onValueChange = { transferPartStr = it },
                        label = { Text("حوالة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = storeDialogTextFieldColors(FintechIndigo),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (paymentType == "دفع مجزأ (مخصص)") {
                        OutlinedTextField(
                            value = debtPartStr,
                            onValueChange = { debtPartStr = it },
                            label = { Text("آجل") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = storeDialogTextFieldColors(RubyExpense),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                val isSplitExact = abs(splitDiff) <= 0.01
                DialogInfoBanner(
                    title = if (isSplitExact) "التوزيع مطابق للصافي:" else "الفارق عن صافي الفاتورة:",
                    value = if (isSplitExact) FormatUtils.formatCurrency(netTotal) else FormatUtils.formatCurrency(splitDiff),
                    containerColor = if (isSplitExact) FintechEmeraldBg else FintechRoseBg,
                    contentColor = if (isSplitExact) FintechEmerald else FintechRose,
                    borderColor = if (isSplitExact) FintechEmeraldBorder else FintechRoseBorder,
                    icon = if (isSplitExact) Icons.Default.CheckCircle else Icons.Default.Warning
                )
            }

            if (paymentType == "تحويل صرافة" || paymentType == "نصف كاش ونصف حوالة" || paymentType == "دفع مجزأ (مخصص)") {
                val accChoices = remember(exchangeAccounts, selectedAccount) {
                    val list = exchangeAccounts.toMutableList()
                    val sel = selectedAccount
                    if (sel != null && list.none { it.id == sel.id }) list.add(0, sel)
                    list
                }
                if (accChoices.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accChoices) { acc ->
                            FilterChip(
                                selected = selectedAccount?.id == acc.id,
                                onClick = { selectedAccount = acc },
                                label = { Text("${acc.name}${if (acc.isArchived) " (مؤرشف)" else ""}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = transferRef,
                    onValueChange = { transferRef = it },
                    label = { Text("مرجع / رقم الحوالة") },
                    colors = storeDialogTextFieldColors(FintechIndigo),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TransactionDatePickerField(
                selectedTimestamp = editTimestamp,
                onDateSelected = { editTimestamp = it },
                label = "تاريخ الفاتورة",
                testTag = "edit_sale_date_picker"
            )

            DialogInfoBanner(
                title = "صافي الفاتورة بعد التعديل:",
                value = FormatUtils.formatCurrency(netTotal),
                containerColor = FintechEmeraldBg,
                contentColor = FintechEmerald,
                borderColor = FintechEmeraldBorder,
                icon = Icons.Default.Calculate
            )

            localError?.let { err ->
                DialogInfoBanner(
                    title = "تنبيه:",
                    value = err,
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.ErrorOutline
                )
            }
        }
    }

    // --- Delete Whole Purchase Invoice Confirmation Dialog ---
    purchaseGroupToDelete?.let { group ->
        val first = group.first()
        val label = first.invoiceNumber.ifBlank { "#${first.id}" }
        val totalPieces = group.sumOf { it.quantity }
        val totalCost = group.sumOf { it.totalCost }
        val totalCash = group.sumOf { it.cashPaid }
        val totalTransfer = group.sumOf { it.transferPaid }
        val totalDebt = group.sumOf { it.remainingDebt }

        StoreOperationDialog(
            onDismissRequest = { purchaseGroupToDelete = null },
            title = "تأكيد إلغاء وحذف فاتورة الشراء ($label)",
            subtitle = "${group.size} أصناف • إجمالي $totalPieces حبة",
            icon = Icons.Default.Warning,
            accentColor = RubyExpense,
            confirmText = "تأكيد الإلغاء الكامل",
            confirmIcon = Icons.Default.DeleteOutline,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_delete_purchase_button",
            onConfirm = {
                val invNo = first.invoiceNumber
                val firstId = first.id
                purchaseGroupToDelete = null
                pendingPinAction = "تأكيد رمز المدير لحذف فاتورة الشراء ($label)" to {
                    if (invNo.isNotBlank()) {
                        viewModel.deletePurchaseInvoice(invNo)
                    } else {
                        viewModel.deletePurchase(firstId)
                    }
                }
            }
        ) {
            DialogInfoBanner(
                title = "إجمالي فاتورة الشراء:",
                value = FormatUtils.formatCurrency(totalCost),
                containerColor = Color(0xFFFFF1F2),
                contentColor = RubyExpense,
                borderColor = Color(0xFFFECDD3),
                icon = Icons.Default.Inventory
            )
            Text(
                text = buildString {
                    appendLine("سيتم إلغاء فاتورة الشراء بالكامل (${group.size} أصناف) وتنفيذ الآتي:")
                    appendLine("• خصم الكمية الإجمالية ($totalPieces حبة) من المخزون (يُمنع الحذف إذا بِيع جزء منها).")
                    if (totalCash > 0.01) appendLine("• استرداد ${FormatUtils.formatCurrency(totalCash)} إلى الصندوق النقدي.")
                    if (totalTransfer > 0.01) appendLine("• عكس تحويل صرافة بقيمة ${FormatUtils.formatCurrency(totalTransfer)} وإلغاء القيد.")
                    if (totalDebt > 0.01) appendLine("• حذف سجل دين المورد المرتبط بقيمة ${FormatUtils.formatCurrency(totalDebt)}.")
                }.trimEnd(),
                fontSize = 13.sp,
                color = SlateTextPrimary,
                lineHeight = 20.sp
            )
        }
    }

    // --- Edit Whole Purchase Invoice Dialog ---
    purchaseGroupToEdit?.let { group ->
        val first = group.first()
        val originalKey = if (first.invoiceNumber.isNotBlank()) first.invoiceNumber else "ID:${first.id}"
        var supplierName by remember(originalKey) { mutableStateOf(first.supplierName) }
        var invoiceNo by remember(originalKey) { mutableStateOf(first.invoiceNumber) }
        var editTimestamp by remember(originalKey) { mutableLongStateOf(first.timestamp) }

        val lineStates = remember(originalKey) {
            mutableStateListOf<EditablePurchaseLine>().apply {
                addAll(
                    group.map {
                        EditablePurchaseLine(
                            source = it,
                            qtyStr = it.quantity.toString(),
                            buyPriceStr = FormatUtils.formatInputNumber(it.unitBuyPrice),
                            sellPriceStr = FormatUtils.formatInputNumber(it.unitSellPrice)
                        )
                    }
                )
            }
        }

        val initialCash = group.sumOf { it.cashPaid }
        val initialTransfer = group.sumOf { it.transferPaid }
        val initialDebt = group.sumOf { it.remainingDebt }
        val initialPayMethod = remember(originalKey) {
            when {
                first.paymentMethod.contains("تحويل") && initialCash <= 0.01 && initialDebt <= 0.01 -> "تحويل صرافة"
                first.paymentMethod.contains("نصف") || (initialCash > 0.01 && initialTransfer > 0.01 && initialDebt <= 0.01) -> "نصف كاش ونصف حوالة"
                first.paymentMethod.contains("آجل") && initialCash <= 0.01 && initialTransfer <= 0.01 -> "آجل للمورد"
                first.paymentMethod.contains("مجزأ") || initialDebt > 0.01 -> "دفع مجزأ (مخصص)"
                else -> "نقداً من الصندوق"
            }
        }

        var paymentMethod by remember(originalKey) { mutableStateOf(initialPayMethod) }
        var cashPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialCash)) }
        var transferPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialTransfer)) }
        var debtPartStr by remember(originalKey) { mutableStateOf(FormatUtils.formatInputNumber(initialDebt)) }
        var selectedAccount by remember(originalKey) {
            mutableStateOf(
                exchangeAccountsAll.firstOrNull { it.id == first.exchangeAccountId }
                    ?: exchangeAccounts.firstOrNull()
            )
        }
        var localError by remember { mutableStateOf<String?>(null) }

        val totalCost = lineStates.sumOf { line ->
            val q = (line.qtyStr.toIntOrNull() ?: 0).coerceAtLeast(0)
            val buyP = (line.buyPriceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            q * buyP
        }
        val splitSum = (cashPartStr.toDoubleOrNull() ?: 0.0) +
            (transferPartStr.toDoubleOrNull() ?: 0.0) +
            (if (paymentMethod == "دفع مجزأ (مخصص)") (debtPartStr.toDoubleOrNull() ?: 0.0) else 0.0)
        val splitDiff = totalCost - splitSum

        StoreOperationDialog(
            onDismissRequest = { purchaseGroupToEdit = null },
            title = "تعديل فاتورة الشراء (${first.invoiceNumber.ifBlank { "#${first.id}" }})",
            subtitle = "${group.size} أصناف • تعديل موحد للأصناف والدفع والمخزون",
            icon = Icons.Default.Edit,
            accentColor = PdfHeaderNavy,
            maxWidth = 580.dp,
            confirmText = "حفظ تعديل الفاتورة",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_edit_purchase_invoice_button",
            onConfirm = {
                localError = null
                if (lineStates.any {
                        (it.qtyStr.toIntOrNull() ?: 0) <= 0 ||
                            (it.buyPriceStr.toDoubleOrNull() ?: 0.0) <= 0.0 ||
                            (it.sellPriceStr.toDoubleOrNull() ?: 0.0) <= 0.0
                    }
                ) {
                    localError = "يرجى التأكد أن الكمية وسعر الشراء والبيع لكل صنف أكبر من الصفر."
                    return@StoreOperationDialog
                }

                val (finalCash, finalTransfer, finalDebt) = when (paymentMethod) {
                    "نقداً من الصندوق" -> Triple(totalCost, 0.0, 0.0)
                    "تحويل صرافة" -> Triple(0.0, totalCost, 0.0)
                    "آجل للمورد" -> Triple(0.0, 0.0, totalCost)
                    "نصف كاش ونصف حوالة" -> {
                        val c = cashPartStr.toDoubleOrNull() ?: -1.0
                        val t = transferPartStr.toDoubleOrNull() ?: -1.0
                        if (c < 0 || t < 0) {
                            localError = "لا يمكن إدخال مبالغ دفع سالبة."
                            return@StoreOperationDialog
                        }
                        if (abs((c + t) - totalCost) > 0.01) {
                            localError = "مجموع الكاش والتحويل يجب أن يطابق إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCost)})."
                            return@StoreOperationDialog
                        }
                        Triple(c, t, 0.0)
                    }
                    else -> {
                        val c = cashPartStr.toDoubleOrNull() ?: -1.0
                        val t = transferPartStr.toDoubleOrNull() ?: -1.0
                        val d = debtPartStr.toDoubleOrNull() ?: -1.0
                        if (c < 0 || t < 0 || d < 0) {
                            localError = "لا يمكن إدخال مبالغ دفع سالبة."
                            return@StoreOperationDialog
                        }
                        if (abs((c + t + d) - totalCost) > 0.01) {
                            localError = "مجموع الدفع والدين يجب أن يطابق إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCost)})."
                            return@StoreOperationDialog
                        }
                        Triple(c, t, d)
                    }
                }

                if (finalTransfer > 0.001 && selectedAccount == null) {
                    localError = "يرجى اختيار حساب الصراف أو المحفظة للدفعة المحولة."
                    return@StoreOperationDialog
                }
                if (finalDebt > 0.001 && supplierName.trim().isBlank()) {
                    localError = "يرجى إدخال اسم المورد لتسجيل المبلغ الآجل."
                    return@StoreOperationDialog
                }

                val updatedPurchases = lineStates.map { line ->
                    val q = line.qtyStr.toInt()
                    val buyP = line.buyPriceStr.toDouble()
                    val sellP = line.sellPriceStr.toDouble()
                    val lineCost = q * buyP
                    val mult = ClothingUnits.getMultiplier(line.source.unit).coerceAtLeast(1)
                    line.source.copy(
                        quantity = q,
                        unitCount = q.toDouble() / mult,
                        unitBuyPrice = buyP,
                        unitSellPrice = sellP,
                        totalCost = lineCost
                    )
                }

                viewModel.updatePurchaseInvoice(
                    originalInvoiceKey = originalKey,
                    updatedItems = updatedPurchases,
                    newInvoiceNumber = invoiceNo,
                    supplierName = supplierName,
                    paymentMethod = paymentMethod,
                    cashPaid = finalCash,
                    transferPaid = finalTransfer,
                    debtAmount = finalDebt,
                    exchangeAccount = if (finalTransfer > 0.001) selectedAccount else null,
                    timestamp = editTimestamp,
                    onSuccess = { purchaseGroupToEdit = null }
                )
            }
        ) {
            DialogSectionHeader(title = "أصناف فاتورة الشراء (${lineStates.size})", icon = Icons.Default.Inventory2, accentColor = PdfHeaderNavy)
            lineStates.forEachIndexed { idx, line ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${idx + 1}. ${line.source.modelName} (${line.source.color} | ${line.source.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PdfHeaderNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = line.qtyStr,
                                onValueChange = { v -> lineStates[idx] = line.copy(qtyStr = v) },
                                label = { Text("الكمية") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = line.buyPriceStr,
                                onValueChange = { v -> lineStates[idx] = line.copy(buyPriceStr = v) },
                                label = { Text("شراء الحبة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = line.sellPriceStr,
                                onValueChange = { v -> lineStates[idx] = line.copy(sellPriceStr = v) },
                                label = { Text("بيع الحبة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = storeDialogTextFieldColors(FintechEmerald),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("اسم المورد") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("رقم الفاتورة") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            DialogSectionHeader(title = "طريقة الدفع", icon = Icons.Default.Payments, accentColor = PdfHeaderNavy)
            val purPayModes = listOf("نقداً من الصندوق", "تحويل صرافة", "نصف كاش ونصف حوالة", "آجل للمورد", "دفع مجزأ (مخصص)")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(purPayModes) { mode ->
                    FilterChip(
                        selected = paymentMethod == mode,
                        onClick = {
                            paymentMethod = mode
                            if (mode == "نصف كاش ونصف حوالة") {
                                val half = kotlin.math.round((totalCost / 2.0) * 100.0) / 100.0
                                cashPartStr = FormatUtils.formatInputNumber(half)
                                transferPartStr = FormatUtils.formatInputNumber(totalCost - half)
                            }
                        },
                        label = { Text(mode, fontSize = 11.sp) }
                    )
                }
            }

            if (paymentMethod == "نصف كاش ونصف حوالة" || paymentMethod == "دفع مجزأ (مخصص)") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cashPartStr,
                        onValueChange = { cashPartStr = it },
                        label = { Text("كاش") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = storeDialogTextFieldColors(FintechEmerald),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = transferPartStr,
                        onValueChange = { transferPartStr = it },
                        label = { Text("حوالة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = storeDialogTextFieldColors(FintechIndigo),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (paymentMethod == "دفع مجزأ (مخصص)") {
                        OutlinedTextField(
                            value = debtPartStr,
                            onValueChange = { debtPartStr = it },
                            label = { Text("آجل للمورد") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = storeDialogTextFieldColors(RubyExpense),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                val isSplitExact = abs(splitDiff) <= 0.01
                DialogInfoBanner(
                    title = if (isSplitExact) "التوزيع مطابق للإجمالي:" else "الفارق عن إجمالي الفاتورة:",
                    value = if (isSplitExact) FormatUtils.formatCurrency(totalCost) else FormatUtils.formatCurrency(splitDiff),
                    containerColor = if (isSplitExact) FintechEmeraldBg else FintechRoseBg,
                    contentColor = if (isSplitExact) FintechEmerald else FintechRose,
                    borderColor = if (isSplitExact) FintechEmeraldBorder else FintechRoseBorder,
                    icon = if (isSplitExact) Icons.Default.CheckCircle else Icons.Default.Warning
                )
            }

            if (paymentMethod == "تحويل صرافة" || paymentMethod == "نصف كاش ونصف حوالة" || paymentMethod == "دفع مجزأ (مخصص)") {
                val accChoices = remember(exchangeAccounts, selectedAccount) {
                    val list = exchangeAccounts.toMutableList()
                    val sel = selectedAccount
                    if (sel != null && list.none { it.id == sel.id }) list.add(0, sel)
                    list
                }
                if (accChoices.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accChoices) { acc ->
                            FilterChip(
                                selected = selectedAccount?.id == acc.id,
                                onClick = { selectedAccount = acc },
                                label = { Text("${acc.name}${if (acc.isArchived) " (مؤرشف)" else ""}", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            TransactionDatePickerField(
                selectedTimestamp = editTimestamp,
                onDateSelected = { editTimestamp = it },
                label = "تاريخ فاتورة الشراء",
                testTag = "edit_purchase_date_picker"
            )

            DialogInfoBanner(
                title = "إجمالي التكلفة بعد التعديل:",
                value = FormatUtils.formatCurrency(totalCost),
                containerColor = Color(0xFFEFF6FF),
                contentColor = Color(0xFF1E3A8A),
                borderColor = Color(0xFFBFDBFE),
                icon = Icons.Default.Calculate
            )

            localError?.let { err ->
                DialogInfoBanner(
                    title = "تنبيه:",
                    value = err,
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.ErrorOutline
                )
            }
        }
    }

    pendingPinAction?.let { (title, action) ->
        PinVerificationDialog(
            viewModel = viewModel,
            title = "تحقق صلاحية المدير",
            subtitle = title,
            onDismiss = { pendingPinAction = null },
            onVerified = {
                pendingPinAction = null
                action()
            }
        )
    }
}

@Composable
private fun SaleInvoiceGroupCard(
    items: List<SaleTransaction>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onShareText: () -> Unit
) {
    val first = items.first()
    val totalAmount = items.sumOf { it.totalAmount }
    val totalQty = items.sumOf { it.quantity }
    val totalCash = items.sumOf { it.cashAmount }
    val totalTransfer = items.sumOf { it.transferAmount }
    val totalDebt = items.sumOf { it.debtAmount }
    val invLabel = first.invoiceNumber.ifBlank { "#${first.id}" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = EmeraldProfit,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (items.size > 1) "فاتورة بيع $invLabel" else first.productName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (items.size > 1) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFDBEAFE)) {
                                    Text(
                                        text = "${items.size} أصناف",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (items.size == 1) "${first.category} | مقاس: ${first.size} | ${first.color}"
                            else "رقم الفاتورة: $invLabel • إجمالي القطع: $totalQty",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                }

                Text(
                    text = FormatUtils.formatCurrency(totalAmount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldProfit
                    )
                )
            }

            if (items.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items.forEach { line ->
                        Text(
                            text = "• ${line.productName} (${line.quantity} ${line.unit}) = ${FormatUtils.formatCurrency(line.totalAmount)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary)
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الزبون: ${first.customerName}", style = MaterialTheme.typography.bodySmall)
                    Text("البائع: ${first.employeeName}", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("الكمية: $totalQty حبه", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            totalDebt > 0.01 && totalCash <= 0.01 && totalTransfer <= 0.01 -> RubyLight
                            totalTransfer > 0.01 && totalCash > 0.01 -> LuxuryGold.copy(alpha = 0.15f)
                            totalTransfer > 0.01 -> Color(0xFF0D9488).copy(alpha = 0.15f)
                            else -> EmeraldLight
                        }
                    ) {
                        val badgeText = when {
                            totalDebt > 0.01 && totalCash <= 0.01 && totalTransfer <= 0.01 -> "⏳ آجل / دين"
                            totalTransfer > 0.01 && totalCash <= 0.01 && totalDebt <= 0.01 -> "🏦 تحويل: ${first.exchangeAccountName.ifBlank { "صراف" }}"
                            totalCash > 0.01 && totalTransfer <= 0.01 && totalDebt <= 0.01 -> "💵 كاش الصندوق"
                            else -> "🔀 ${FormatUtils.formatNumber(totalCash)} كاش + ${FormatUtils.formatNumber(totalTransfer)} حوالة${if (totalDebt > 0.01) " + ${FormatUtils.formatNumber(totalDebt)} آجل" else ""}"
                        }
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${FormatUtils.formatDateTime(first.timestamp)} | $invLabel",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted, fontSize = 10.sp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrintPdf, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onSharePdf, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShareText, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Chat, contentDescription = "مشاركة نص للواتساب", tint = EmeraldProfit, modifier = Modifier.size(16.dp))
                    }
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("تعديل", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = RubyExpense),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("حذف", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseInvoiceGroupCard(
    items: List<PurchaseRecord>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onShareText: () -> Unit
) {
    val first = items.first()
    val totalCost = items.sumOf { it.totalCost }
    val totalQty = items.sumOf { it.quantity }
    val totalCash = items.sumOf { it.cashPaid }
    val totalTransfer = items.sumOf { it.transferPaid }
    val totalDebt = items.sumOf { it.remainingDebt }
    val invLabel = first.invoiceNumber.ifBlank { "#${first.id}" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddBusiness,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (items.size > 1) "فاتورة شراء $invLabel" else first.modelName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (items.size > 1) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFDBEAFE)) {
                                    Text(
                                        text = "${items.size} أصناف",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (items.size == 1) "${first.category} | مقاس: ${first.size} | ${first.color}"
                            else "فاتورة: $invLabel • إجمالي القطع: $totalQty",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                }

                Text(
                    text = FormatUtils.formatCurrency(totalCost),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (items.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items.forEach { line ->
                        Text(
                            text = "• ${line.modelName} (${line.quantity} حبه) = ${FormatUtils.formatCurrency(line.totalCost)}",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary)
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المورد: ${first.supplierName}", style = MaterialTheme.typography.bodySmall)
                    Text("فاتورة: $invLabel", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("الكمية: $totalQty حبه", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            totalDebt > 0.01 && totalCash <= 0.01 && totalTransfer <= 0.01 -> RubyLight
                            totalTransfer > 0.01 && totalCash > 0.01 -> LuxuryGold.copy(alpha = 0.15f)
                            totalTransfer > 0.01 -> Color(0xFF0D9488).copy(alpha = 0.15f)
                            else -> EmeraldLight
                        }
                    ) {
                        val badgeText = when {
                            totalDebt > 0.01 && totalCash <= 0.01 && totalTransfer <= 0.01 -> "⏳ آجل للمورد"
                            totalTransfer > 0.01 && totalCash <= 0.01 && totalDebt <= 0.01 -> "🏦 تحويل: ${first.exchangeAccountName.ifBlank { "صراف" }}"
                            totalCash > 0.01 && totalTransfer <= 0.01 && totalDebt <= 0.01 -> "💵 كاش من الصندوق"
                            else -> "🔀 ${FormatUtils.formatNumber(totalCash)} كاش + ${FormatUtils.formatNumber(totalTransfer)} حوالة${if (totalDebt > 0.01) " + ${FormatUtils.formatNumber(totalDebt)} آجل" else ""}"
                        }
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${FormatUtils.formatDateTime(first.timestamp)} | $invLabel",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted, fontSize = 10.sp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrintPdf, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onSharePdf, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShareText, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Chat, contentDescription = "مشاركة نص للواتساب", tint = EmeraldProfit, modifier = Modifier.size(16.dp))
                    }
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("تعديل", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = RubyExpense),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("حذف", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
