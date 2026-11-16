package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.StoreViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper

@Composable
fun ReturnsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val returns by viewModel.returns.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val pendingReturnTarget by viewModel.pendingReturnTarget.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sale Returns, 1: Purchase Returns, 2: Returns History
    var searchQuery by remember { mutableStateOf("") }
    var activeSaleInvoiceKey by remember { mutableStateOf<String?>(null) }
    var activePurchaseInvoiceKey by remember { mutableStateOf<String?>(null) }
    var returnNumberToDelete by remember { mutableStateOf<String?>(null) }
    var pendingPinAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(pendingReturnTarget) {
        pendingReturnTarget?.let { (type, invKey) ->
            selectedTab = if (type == ReturnTypes.PURCHASE_RETURN) 1 else 0
            if (!invKey.isNullOrBlank()) {
                if (type == ReturnTypes.PURCHASE_RETURN) activePurchaseInvoiceKey = invKey
                else activeSaleInvoiceKey = invKey
            }
            viewModel.clearPendingReturnTarget()
        }
    }

    val saleReturnsByLineId = remember(returns) {
        returns.filter { it.returnType == ReturnTypes.SALE_RETURN }.groupBy { it.originalLineId }
    }
    val purchaseReturnsByLineId = remember(returns) {
        returns.filter { it.returnType == ReturnTypes.PURCHASE_RETURN }.groupBy { it.originalLineId }
    }

    val groupedSales = remember(sales, searchQuery) {
        val groups = sales.groupBy {
            if (it.invoiceNumber.isNotBlank()) it.invoiceNumber.trim() else "ID:${it.id}"
        }.entries.sortedByDescending { (_, list) -> list.maxOfOrNull { it.timestamp } ?: 0L }
        if (searchQuery.isBlank()) groups
        else groups.filter { (key, list) ->
            key.contains(searchQuery, ignoreCase = true) ||
                list.any {
                    it.customerName.contains(searchQuery, ignoreCase = true) ||
                        it.productName.contains(searchQuery, ignoreCase = true) ||
                        it.invoiceNumber.contains(searchQuery, ignoreCase = true)
                }
        }
    }

    val groupedPurchases = remember(purchases, searchQuery) {
        val groups = purchases.groupBy {
            if (it.invoiceNumber.isNotBlank()) it.invoiceNumber.trim() else "ID:${it.id}"
        }.entries.sortedByDescending { (_, list) -> list.maxOfOrNull { it.timestamp } ?: 0L }
        if (searchQuery.isBlank()) groups
        else groups.filter { (key, list) ->
            key.contains(searchQuery, ignoreCase = true) ||
                list.any {
                    it.supplierName.contains(searchQuery, ignoreCase = true) ||
                        it.modelName.contains(searchQuery, ignoreCase = true) ||
                        it.invoiceNumber.contains(searchQuery, ignoreCase = true)
                }
        }
    }

    val groupedReturns = remember(returns, searchQuery) {
        val groups = returns.groupBy { it.returnNumber.ifBlank { "RET-${it.id}" } }
            .values.sortedByDescending { list -> list.maxOfOrNull { it.timestamp } ?: 0L }
        if (searchQuery.isBlank()) groups
        else groups.filter { list ->
            list.any {
                it.returnNumber.contains(searchQuery, ignoreCase = true) ||
                    it.originalInvoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    it.partyName.contains(searchQuery, ignoreCase = true) ||
                    it.productName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "المرتجعات حسب الفاتورة",
                subtitle = "مرتجع مبيعات من عميل • مرتجع مشتريات إلى مورد",
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
                    contentColor = PdfHeaderNavy
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_sale_returns"),
                        text = {
                            Text(
                                "مرتجع مبيعات (${groupedSales.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_purchase_returns"),
                        text = {
                            Text(
                                "مرتجع مشتريات (${groupedPurchases.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("tab_returns_history"),
                        text = {
                            Text(
                                "السجل (${groupedReturns.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            when (selectedTab) {
                                0 -> "ابحث برقم فاتورة البيع، اسم العميل، أو الصنف..."
                                1 -> "ابحث برقم فاتورة الشراء، اسم المورد، أو الصنف..."
                                else -> "ابحث برقم المرتجع، الفاتورة، العميل، أو المورد..."
                            }
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
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("returns_search_input")
                )

                when (selectedTab) {
                    0 -> if (groupedSales.isEmpty()) {
                        ReturnsEmptyState("لا توجد فواتير مبيعات مسجلة لعمل مرتجع منها")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(groupedSales, key = { "SR_${it.key}" }) { (invKey, group) ->
                                val first = group.first()
                                val totalOrigQty = group.sumOf { it.quantity }
                                val alreadyRetQty = group.sumOf { line ->
                                    saleReturnsByLineId[line.id]?.sumOf { it.quantity } ?: 0
                                }
                                val remainingQty = (totalOrigQty - alreadyRetQty).coerceAtLeast(0)
                                val invLabel = first.invoiceNumber.ifBlank { "#${first.id}" }

                                InvoiceForReturnCard(
                                    isSale = true,
                                    invoiceLabel = invLabel,
                                    partyName = first.customerName.ifBlank { "زبون نقدي" },
                                    timestamp = first.timestamp,
                                    totalAmount = group.sumOf { it.totalAmount },
                                    linesPreview = group.map { line ->
                                        val unitP = if (line.quantity > 0) line.totalAmount / line.quantity else line.unitSellPrice
                                        val retQ = saleReturnsByLineId[line.id]?.sumOf { it.quantity } ?: 0
                                        "${line.productName} (${line.quantity} حبة × ${FormatUtils.formatNumber(unitP)})" +
                                            if (retQ > 0) " — أُرجع $retQ" else ""
                                    },
                                    alreadyReturnedQty = alreadyRetQty,
                                    remainingQty = remainingQty,
                                    buttonTestTag = "btn_return_sale_$invKey",
                                    onReturnClick = { activeSaleInvoiceKey = invKey }
                                )
                            }
                        }
                    }

                    1 -> if (groupedPurchases.isEmpty()) {
                        ReturnsEmptyState("لا توجد فواتير مشتريات مسجلة لإرجاعها للمورد")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(groupedPurchases, key = { "PR_${it.key}" }) { (invKey, group) ->
                                val first = group.first()
                                val totalOrigQty = group.sumOf { it.quantity }
                                val alreadyRetQty = group.sumOf { line ->
                                    purchaseReturnsByLineId[line.id]?.sumOf { it.quantity } ?: 0
                                }
                                val remainingQty = (totalOrigQty - alreadyRetQty).coerceAtLeast(0)
                                val invLabel = first.invoiceNumber.ifBlank { "#${first.id}" }

                                InvoiceForReturnCard(
                                    isSale = false,
                                    invoiceLabel = invLabel,
                                    partyName = first.supplierName.ifBlank { "مورد عام" },
                                    timestamp = first.timestamp,
                                    totalAmount = group.sumOf { it.totalCost },
                                    linesPreview = group.map { line ->
                                        val unitP = if (line.quantity > 0) line.totalCost / line.quantity else line.unitBuyPrice
                                        val retQ = purchaseReturnsByLineId[line.id]?.sumOf { it.quantity } ?: 0
                                        "${line.modelName} (${line.quantity} حبة × ${FormatUtils.formatNumber(unitP)})" +
                                            if (retQ > 0) " — أُرجع $retQ" else ""
                                    },
                                    alreadyReturnedQty = alreadyRetQty,
                                    remainingQty = remainingQty,
                                    buttonTestTag = "btn_return_purchase_$invKey",
                                    onReturnClick = { activePurchaseInvoiceKey = invKey }
                                )
                            }
                        }
                    }

                    else -> if (groupedReturns.isEmpty()) {
                        ReturnsEmptyState("لا توجد حركات مرتجع مسجلة حتى الآن")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(groupedReturns, key = { "RET_${it.first().returnNumber}" }) { group ->
                                val first = group.first()
                                val isSaleRet = first.returnType == ReturnTypes.SALE_RETURN
                                ReturnHistoryCard(
                                    records = group,
                                    isSaleReturn = isSaleRet,
                                    onPrintPdf = {
                                        val file = InvoiceHelper.generateReturnReceiptPdf(context, group, storeSettings)
                                        InvoiceHelper.printPdfFile(context, file, "مرتجع_${first.returnNumber}")
                                    },
                                    onSharePdf = {
                                        val file = InvoiceHelper.generateReturnReceiptPdf(context, group, storeSettings)
                                        InvoiceHelper.sharePdfFile(context, file, "مشاركة مرتجع #${first.returnNumber}")
                                    },
                                    onShareWhatsApp = {
                                        val txt = StatementHelper.formatReturnReceiptText(group, storeSettings)
                                        StatementHelper.shareText(context, txt, "مشاركة مرتجع #${first.returnNumber}")
                                    },
                                    onDelete = { returnNumberToDelete = first.returnNumber }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Sale Return Dialog ---
    activeSaleInvoiceKey?.let { invKey ->
        val group = groupedSales.firstOrNull { it.key == invKey }?.value
            ?: sales.filter { (if (it.invoiceNumber.isNotBlank()) it.invoiceNumber.trim() else "ID:${it.id}") == invKey }
        if (group.isEmpty()) {
            activeSaleInvoiceKey = null
        } else {
            val first = group.first()
            val custName = first.customerName.trim()
            val custOpenDebt = remember(debts, custName) {
                if (custName.isBlank()) 0.0
                else debts.filter {
                    it.type == "CUSTOMER" && !it.isSettled &&
                        it.partyName.trim().equals(custName, ignoreCase = true)
                }.sumOf { it.remainingAmount }
            }
            val lines = remember(group, saleReturnsByLineId) {
                group.map { orig ->
                    val priors = saleReturnsByLineId[orig.id].orEmpty()
                    val unitP = if (orig.quantity > 0) orig.totalAmount / orig.quantity else orig.unitSellPrice
                    ReturnableInvoiceLine(
                        lineId = orig.id,
                        productName = orig.productName,
                        category = orig.category,
                        color = orig.color,
                        size = orig.size,
                        unit = orig.unit,
                        originalQty = orig.quantity,
                        alreadyReturnedQty = priors.sumOf { it.quantity },
                        exactInvoiceUnitPrice = unitP,
                        origLineTotal = orig.totalAmount,
                        alreadyReturnedAmount = priors.sumOf { it.totalAmount }
                    )
                }
            }

            InvoiceReturnOperationDialog(
                returnType = ReturnTypes.SALE_RETURN,
                invoiceLabel = first.invoiceNumber.ifBlank { "ID:${first.id}" },
                invoiceTimestamp = first.timestamp,
                partyName = custName,
                partyOpenDebt = custOpenDebt,
                lines = lines,
                cashBalance = cashBalance,
                exchangeAccounts = exchangeAccounts,
                onDismiss = { activeSaleInvoiceKey = null },
                onConfirm = { reqs, retNo, method, cashAmt, transferAmt, debtAmt, surplusAmt, acc, notes, ts ->
                    viewModel.executeSaleReturn(
                        originalInvoiceKey = invKey,
                        lines = reqs,
                        returnNumberInput = retNo,
                        settlementMethod = method,
                        cashAmount = cashAmt,
                        transferAmount = transferAmt,
                        debtReducedAmount = debtAmt,
                        surplusCreditAmount = surplusAmt,
                        exchangeAccount = acc,
                        notes = notes,
                        timestamp = ts
                    ) {
                        activeSaleInvoiceKey = null
                    }
                }
            )
        }
    }

    // --- Purchase Return Dialog ---
    activePurchaseInvoiceKey?.let { invKey ->
        val group = groupedPurchases.firstOrNull { it.key == invKey }?.value
            ?: purchases.filter { (if (it.invoiceNumber.isNotBlank()) it.invoiceNumber.trim() else "ID:${it.id}") == invKey }
        if (group.isEmpty()) {
            activePurchaseInvoiceKey = null
        } else {
            val first = group.first()
            val supName = first.supplierName.trim()
            val supOpenDebt = remember(debts, supName) {
                if (supName.isBlank()) 0.0
                else debts.filter {
                    it.type == "SUPPLIER" && !it.isSettled &&
                        it.partyName.trim().equals(supName, ignoreCase = true)
                }.sumOf { it.remainingAmount }
            }
            val lines = remember(group, purchaseReturnsByLineId, products) {
                group.map { orig ->
                    val priors = purchaseReturnsByLineId[orig.id].orEmpty()
                    val unitP = if (orig.quantity > 0) orig.totalCost / orig.quantity else orig.unitBuyPrice
                    val matchedProd = products.find {
                        it.modelName.trim().equals(orig.modelName.trim(), ignoreCase = true) &&
                            it.color.trim().equals(orig.color.trim(), ignoreCase = true) &&
                            it.size.trim().equals(orig.size.trim(), ignoreCase = true)
                    }
                    ReturnableInvoiceLine(
                        lineId = orig.id,
                        productName = orig.modelName,
                        category = orig.category,
                        color = orig.color,
                        size = orig.size,
                        unit = orig.unit,
                        originalQty = orig.quantity,
                        alreadyReturnedQty = priors.sumOf { it.quantity },
                        exactInvoiceUnitPrice = unitP,
                        origLineTotal = orig.totalCost,
                        alreadyReturnedAmount = priors.sumOf { it.totalAmount },
                        currentStockQty = matchedProd?.quantity ?: 0
                    )
                }
            }

            InvoiceReturnOperationDialog(
                returnType = ReturnTypes.PURCHASE_RETURN,
                invoiceLabel = first.invoiceNumber.ifBlank { "ID:${first.id}" },
                invoiceTimestamp = first.timestamp,
                partyName = supName,
                partyOpenDebt = supOpenDebt,
                lines = lines,
                cashBalance = cashBalance,
                exchangeAccounts = exchangeAccounts,
                onDismiss = { activePurchaseInvoiceKey = null },
                onConfirm = { reqs, retNo, method, cashAmt, transferAmt, debtAmt, surplusAmt, acc, notes, ts ->
                    viewModel.executePurchaseReturn(
                        originalInvoiceKey = invKey,
                        lines = reqs,
                        returnNumberInput = retNo,
                        settlementMethod = method,
                        cashAmount = cashAmt,
                        transferAmount = transferAmt,
                        debtReducedAmount = debtAmt,
                        surplusCreditAmount = surplusAmt,
                        exchangeAccount = acc,
                        notes = notes,
                        timestamp = ts
                    ) {
                        activePurchaseInvoiceKey = null
                    }
                }
            )
        }
    }

    // --- Delete Return Confirmation ---
    returnNumberToDelete?.let { retNo ->
        StoreOperationDialog(
            onDismissRequest = { returnNumberToDelete = null },
            title = "إلغاء وحذف حركة المرتجع (#$retNo)",
            subtitle = "سيتم عكس حركة المخزون والصندوق والصراف والديون المرتبطة",
            icon = Icons.Default.Warning,
            accentColor = RubyExpense,
            confirmText = "حذف وعكس الحركة",
            confirmIcon = Icons.Default.DeleteOutline,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_delete_return_button",
            onConfirm = {
                returnNumberToDelete = null
                pendingPinAction = "تأكيد رمز المدير لإلغاء حركة المرتجع (#$retNo)" to {
                    viewModel.deleteReturn(retNo)
                }
            }
        ) {
            DialogInfoBanner(
                title = "تنبيه:",
                value = "سيُعاد رصيد المخزون والأرصدة المالية والديون لما كانت عليه قبل تسجيل #$retNo",
                containerColor = FintechRoseBg,
                contentColor = FintechRose,
                borderColor = FintechRoseBorder,
                icon = Icons.Default.Info
            )
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
private fun ReturnsEmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(44.dp))
            Text(text, color = SlateMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun InvoiceForReturnCard(
    isSale: Boolean,
    invoiceLabel: String,
    partyName: String,
    timestamp: Long,
    totalAmount: Double,
    linesPreview: List<String>,
    alreadyReturnedQty: Int,
    remainingQty: Int,
    buttonTestTag: String,
    onReturnClick: () -> Unit
) {
    val accent = if (isSale) FintechAmber else BrandBlue
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSale) Icons.Default.ReceiptLong else Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${if (isSale) "فاتورة بيع" else "فاتورة شراء"} #$invoiceLabel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SlateTextPrimary
                        )
                        Text(
                            text = "${if (isSale) "العميل" else "المورد"}: $partyName • ${FormatUtils.formatDate(timestamp)}",
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )
                    }
                }
                Text(
                    text = FormatUtils.formatCurrency(totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = PdfHeaderNavy
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                linesPreview.take(4).forEach { line ->
                    Text("• $line", fontSize = 12.sp, color = SlateTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (linesPreview.size > 4) {
                    Text("... و ${linesPreview.size - 4} أصناف أخرى", fontSize = 11.sp, color = SlateMuted)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (alreadyReturnedQty > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (remainingQty == 0) FintechRoseBg else FintechAmberBg
                        ) {
                            Text(
                                text = if (remainingQty == 0) "مُرجع بالكامل ($alreadyReturnedQty)" else "أُرجع: $alreadyReturnedQty | متبقي: $remainingQty",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingQty == 0) FintechRose else FintechAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text("متاح للإرجاع: $remainingQty حبة", fontSize = 11.sp, color = SlateMuted)
                    }
                }

                Button(
                    onClick = onReturnClick,
                    enabled = remainingQty > 0,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag(buttonTestTag)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSale) "إرجاع من الفاتورة" else "إرجاع للمورد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReturnHistoryCard(
    records: List<ReturnRecord>,
    isSaleReturn: Boolean,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onDelete: () -> Unit
) {
    val first = records.first()
    val totalAmount = records.sumOf { it.totalAmount }
    val totalQty = records.sumOf { it.quantity }
    val accent = if (isSaleReturn) FintechAmber else BrandBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.14f)) {
                            Text(
                                text = if (isSaleReturn) "مرتجع مبيعات" else "مرتجع مشتريات",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Text("#${first.returnNumber}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = PdfHeaderNavy)
                    }
                    Text(
                        text = "${if (isSaleReturn) "العميل" else "المورد"}: ${first.partyName} • فاتورة #${first.originalInvoiceNumber}",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                }
                Text(FormatUtils.formatCurrency(totalAmount), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = accent)
            }

            records.forEach { line ->
                val price = if (isSaleReturn) line.unitSellPrice else line.unitBuyPrice
                Text(
                    text = "• ${line.productName} (${line.quantity} حبة × ${FormatUtils.formatNumber(price)}) = ${FormatUtils.formatCurrency(line.totalAmount)}",
                    fontSize = 12.sp,
                    color = SlateTextSecondary
                )
            }

            Text(
                text = "التسوية: ${first.settlementMethod} • $totalQty حبة • ${FormatUtils.formatDateTime(first.timestamp)}",
                fontSize = 11.sp,
                color = SlateMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPrintPdf, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة PDF", tint = PdfHeaderNavy)
                    }
                    IconButton(onClick = onSharePdf, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة PDF", tint = PdfHeaderNavy)
                    }
                    IconButton(onClick = onShareWhatsApp, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Forum, contentDescription = "مشاركة واتساب", tint = FintechEmerald)
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = RubyExpense),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_delete_return_${first.returnNumber}")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إلغاء المرتجع", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
