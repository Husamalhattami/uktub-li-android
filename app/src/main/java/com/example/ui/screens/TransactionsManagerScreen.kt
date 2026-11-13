package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PurchaseRecord
import com.example.data.model.SaleTransaction
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper

@Composable
fun TransactionsManagerScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sales, 1: Purchases
    var searchQuery by remember { mutableStateOf("") }

    var saleToDelete by remember { mutableStateOf<SaleTransaction?>(null) }
    var saleToEdit by remember { mutableStateOf<SaleTransaction?>(null) }

    var purchaseToDelete by remember { mutableStateOf<PurchaseRecord?>(null) }
    var purchaseToEdit by remember { mutableStateOf<PurchaseRecord?>(null) }

    val filteredSales = remember(sales, searchQuery) {
        if (searchQuery.isBlank()) sales
        else sales.filter {
            it.productName.contains(searchQuery, ignoreCase = true) ||
                    it.customerName.contains(searchQuery, ignoreCase = true) ||
                    it.employeeName.contains(searchQuery, ignoreCase = true) ||
                    it.color.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredPurchases = remember(purchases, searchQuery) {
        if (searchQuery.isBlank()) purchases
        else purchases.filter {
            it.modelName.contains(searchQuery, ignoreCase = true) ||
                    it.supplierName.contains(searchQuery, ignoreCase = true) ||
                    it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                    it.color.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "سجل العمليات (تعديل وحذف)",
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
            // Tabs
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
                            text = "فواتير المبيعات (${sales.size})",
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
                            text = "فواتير المشتريات (${purchases.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) }
                )
            }

            // Search Bar
            PaddingValues(16.dp).let {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(if (selectedTab == 0) "ابحث في المبيعات بالصنف، الزبون، البائع..." else "ابحث في المشتريات بالصنف، المورد، الفاتورة...")
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            if (selectedTab == 0) {
                // Sales List
                if (filteredSales.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد فواتير مبيعات مسجلة", color = SlateMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSales, key = { it.id }) { sale ->
                            SaleItemCard(
                                sale = sale,
                                onEdit = { saleToEdit = sale },
                                onDelete = { saleToDelete = sale },
                                onPrintPdf = {
                                    val pdf = InvoiceHelper.generateSaleInvoicePdf(
                                        context = context,
                                        invoiceNumber = if (sale.invoiceNumber.isNotBlank()) sale.invoiceNumber else "INV-${sale.id}",
                                        customerName = sale.customerName,
                                        customerPhone = sale.customerPhone,
                                        employeeName = sale.employeeName,
                                        paymentType = sale.paymentType,
                                        items = listOf(
                                            InvoiceHelper.PdfSaleLineItem(
                                                name = sale.productName,
                                                details = "${sale.category} | ${sale.color} | ${sale.size}",
                                                quantity = sale.quantity,
                                                unitPrice = sale.unitSellPrice,
                                                total = sale.totalAmount,
                                                unit = sale.unit
                                            )
                                        ),
                                        subtotal = sale.unitSellPrice * sale.quantity,
                                        discount = sale.discount,
                                        netTotal = sale.totalAmount,
                                        cashPaid = sale.cashAmount,
                                        transferPaid = sale.transferAmount,
                                        debtAmount = sale.debtAmount,
                                        exchangeAccountName = sale.exchangeAccountName,
                                        transferRef = sale.transferRef,
                                        timestamp = sale.timestamp,
                                        settings = storeSettings
                                    )
                                    InvoiceHelper.printPdfFile(context, pdf, "فاتورة_${sale.id}")
                                },
                                onSharePdf = {
                                    val pdf = InvoiceHelper.generateSaleInvoicePdf(
                                        context = context,
                                        invoiceNumber = if (sale.invoiceNumber.isNotBlank()) sale.invoiceNumber else "INV-${sale.id}",
                                        customerName = sale.customerName,
                                        customerPhone = sale.customerPhone,
                                        employeeName = sale.employeeName,
                                        paymentType = sale.paymentType,
                                        items = listOf(
                                            InvoiceHelper.PdfSaleLineItem(
                                                name = sale.productName,
                                                details = "${sale.category} | ${sale.color} | ${sale.size}",
                                                quantity = sale.quantity,
                                                unitPrice = sale.unitSellPrice,
                                                total = sale.totalAmount,
                                                unit = sale.unit
                                            )
                                        ),
                                        subtotal = sale.unitSellPrice * sale.quantity,
                                        discount = sale.discount,
                                        netTotal = sale.totalAmount,
                                        cashPaid = sale.cashAmount,
                                        transferPaid = sale.transferAmount,
                                        debtAmount = sale.debtAmount,
                                        exchangeAccountName = sale.exchangeAccountName,
                                        transferRef = sale.transferRef,
                                        timestamp = sale.timestamp,
                                        settings = storeSettings
                                    )
                                    InvoiceHelper.sharePdfFile(context, pdf, "مشاركة فاتورة_${sale.id}")
                                },
                                onShareText = {
                                    val txt = StatementHelper.formatSaleRecordText(sale, storeSettings)
                                    StatementHelper.shareText(context, txt, "مشاركة فاتورة بيع #${sale.id}")
                                }
                            )
                        }
                    }
                }
            } else {
                // Purchases List
                if (filteredPurchases.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد فواتير مشتريات مسجلة", color = SlateMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPurchases, key = { it.id }) { purchase ->
                            PurchaseItemCard(
                                purchase = purchase,
                                onEdit = { purchaseToEdit = purchase },
                                onDelete = { purchaseToDelete = purchase },
                                onPrintPdf = {
                                    val pdf = InvoiceHelper.generatePurchaseInvoicePdf(context, purchase, storeSettings)
                                    InvoiceHelper.printPdfFile(context, pdf, "مشتريات_${purchase.id}")
                                },
                                onSharePdf = {
                                    val pdf = InvoiceHelper.generatePurchaseInvoicePdf(context, purchase, storeSettings)
                                    InvoiceHelper.sharePdfFile(context, pdf, "مشاركة مشتريات_${purchase.id}")
                                },
                                onShareText = {
                                    val txt = StatementHelper.formatPurchaseRecordText(purchase, storeSettings)
                                    StatementHelper.shareText(context, txt, "مشاركة فاتورة شراء #${purchase.id}")
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // --- Delete Sale Confirmation Dialog ---
    saleToDelete?.let { sale ->
        AlertDialog(
            onDismissRequest = { saleToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RubyExpense) },
            title = { Text("تأكيد حذف فاتورة البيع #${sale.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "هل أنت متأكد من حذف هذه الفاتورة؟\n" +
                                "• ستتم إعادة كمية البضاعة (${sale.quantity} حبه) إلى المخزون.\n" +
                                "• سيتم إلغاء واسترجاع المبالغ (${FormatUtils.formatCurrency(sale.totalAmount)}) من الصندوق أو حساب الصراف.\n" +
                                "• سيتم حذف أي دين مرتبط بهذه الفاتورة إن وجد."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSale(sale.id)
                        saleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_delete_sale_button")
                ) {
                    Text("تأكيد الحذف واسترجاع البضاعة")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { saleToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- Edit Sale Dialog ---
    saleToEdit?.let { sale ->
        var customerName by remember { mutableStateOf(sale.customerName) }
        var customerPhone by remember { mutableStateOf(sale.customerPhone) }
        var quantityStr by remember { mutableStateOf(sale.quantity.toString()) }
        var unitPriceStr by remember { mutableStateOf(sale.unitSellPrice.toLong().toString()) }
        var discountStr by remember { mutableStateOf(sale.discount.toLong().toString()) }

        AlertDialog(
            onDismissRequest = { saleToEdit = null },
            title = { Text("تعديل فاتورة البيع #${sale.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("الصنف: ${sale.productName}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("الكمية (حبه)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unitPriceStr,
                            onValueChange = { unitPriceStr = it },
                            label = { Text("سعر الحبة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text("الخصم (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("اسم الزبون") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("رقم هاتف الزبون") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val q = quantityStr.toIntOrNull() ?: sale.quantity
                    val p = unitPriceStr.toDoubleOrNull() ?: sale.unitSellPrice
                    val d = discountStr.toDoubleOrNull() ?: sale.discount
                    val previewTotal = ((p * q) - d).coerceAtLeast(0.0)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الإجمالي بعد التعديل: ${FormatUtils.formatCurrency(previewTotal)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = quantityStr.toIntOrNull() ?: sale.quantity
                        val p = unitPriceStr.toDoubleOrNull() ?: sale.unitSellPrice
                        val disc = discountStr.toDoubleOrNull() ?: sale.discount
                        val total = ((p * q) - disc).coerceAtLeast(0.0)
                        val profit = total - (sale.unitBuyPrice * q)

                        val (newCash, newTransfer, newDebt) = when {
                            sale.paymentType.contains("دين") || sale.paymentType.contains("آجل") -> Triple(0.0, 0.0, total)
                            sale.paymentType.contains("تحويل") -> Triple(0.0, total, 0.0)
                            sale.paymentType.contains("نصف") -> Triple((total / 2), (total - (total / 2)), 0.0)
                            else -> Triple(total, 0.0, 0.0)
                        }

                        viewModel.updateSale(
                            sale.copy(
                                customerName = customerName,
                                customerPhone = customerPhone,
                                quantity = q,
                                unitSellPrice = p,
                                discount = disc,
                                totalAmount = total,
                                profit = profit,
                                cashAmount = newCash,
                                transferAmount = newTransfer,
                                debtAmount = newDebt
                            )
                        )
                        saleToEdit = null
                    }
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { saleToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- Delete Purchase Confirmation Dialog ---
    purchaseToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { purchaseToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RubyExpense) },
            title = { Text("تأكيد حذف فاتورة الشراء #${p.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "هل أنت متأكد من حذف فاتورة شراء (${p.modelName})؟\n" +
                                "• سيتم خصم الكمية (${p.quantity} حبه) من المخزون.\n" +
                                "• سيتم إلغاء المبالغ المدفوعة واسترجاعها إلى الصندوق أو الصراف.\n" +
                                "• سيتم إلغاء أي دين مسجل للمورد لهذه الفاتورة."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePurchase(p.id)
                        purchaseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_delete_purchase_button")
                ) {
                    Text("تأكيد الحذف وخصم الكمية")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { purchaseToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- Edit Purchase Dialog ---
    purchaseToEdit?.let { p ->
        var supplierName by remember { mutableStateOf(p.supplierName) }
        var invoiceNo by remember { mutableStateOf(p.invoiceNumber) }
        var quantityStr by remember { mutableStateOf(p.quantity.toString()) }
        var unitBuyPriceStr by remember { mutableStateOf(p.unitBuyPrice.toLong().toString()) }
        var sellPriceStr by remember { mutableStateOf(p.unitSellPrice.toLong().toString()) }
        var notes by remember { mutableStateOf(p.notes) }

        AlertDialog(
            onDismissRequest = { purchaseToEdit = null },
            title = { Text("تعديل بيانات فاتورة الشراء #${p.id}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("الصنف: ${p.modelName} | المقاس: ${p.size}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("الكمية (حبه)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unitBuyPriceStr,
                            onValueChange = { unitBuyPriceStr = it },
                            label = { Text("سعر التكلفة للحبة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = sellPriceStr,
                        onValueChange = { sellPriceStr = it },
                        label = { Text("سعر البيع المقترح للحبة (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it },
                        label = { Text("اسم المورد") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoiceNo,
                        onValueChange = { invoiceNo = it },
                        label = { Text("رقم الفاتورة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val q = quantityStr.toIntOrNull() ?: p.quantity
                    val buyP = unitBuyPriceStr.toDoubleOrNull() ?: p.unitBuyPrice
                    val newTotal = q * buyP

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "إجمالي التكلفة بعد التعديل: ${FormatUtils.formatCurrency(newTotal)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = quantityStr.toIntOrNull() ?: p.quantity
                        val buyP = unitBuyPriceStr.toDoubleOrNull() ?: p.unitBuyPrice
                        val newSellPrice = sellPriceStr.toDoubleOrNull() ?: p.unitSellPrice
                        val newTotalCost = buyP * q
                        val newRemainingDebt = (newTotalCost - p.paidAmount).coerceAtLeast(0.0)

                        viewModel.updatePurchase(
                            p.copy(
                                supplierName = supplierName,
                                invoiceNumber = invoiceNo,
                                quantity = q,
                                unitBuyPrice = buyP,
                                unitSellPrice = newSellPrice,
                                totalCost = newTotalCost,
                                remainingDebt = newRemainingDebt,
                                notes = notes
                            )
                        )
                        purchaseToEdit = null
                    }
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { purchaseToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SaleItemCard(
    sale: SaleTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrintPdf: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onShareText: () -> Unit = {}
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = sale.productName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${sale.category} | مقاس: ${sale.size} | ${sale.color}",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                }

                Text(
                    text = FormatUtils.formatCurrency(sale.totalAmount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldProfit
                    )
                )
            }

            HorizontalDivider(color = SlateBorder)

            // Sale Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الزبون: ${sale.customerName}", style = MaterialTheme.typography.bodySmall)
                    Text("البائع: ${sale.employeeName}", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("الكمية: ${sale.quantity} حبه (${sale.unit})", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (sale.paymentType) {
                            "نقداً" -> EmeraldLight
                            "تحويل صرافة" -> Color(0xFF0D9488).copy(alpha = 0.15f)
                            "نصف كاش ونصف حوالة" -> LuxuryGold.copy(alpha = 0.15f)
                            else -> RubyLight
                        }
                    ) {
                        Text(
                            text = when (sale.paymentType) {
                                "نقداً" -> "💵 كاش الصندوق"
                                "تحويل صرافة" -> "🏦 تحويل: ${sale.exchangeAccountName.ifBlank { "صراف" }}"
                                "نصف كاش ونصف حوالة" -> "🔀 مجزأ: ${sale.cashAmount.toInt()} كاش + ${sale.transferAmount.toInt()} تحويل"
                                "بالدين" -> "⏳ آجل / دين"
                                else -> sale.paymentType
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (sale.paymentType) {
                                    "نقداً" -> EmeraldProfit
                                    "تحويل صرافة" -> Color(0xFF0D9488)
                                    "نصف كاش ونصف حوالة" -> LuxuryGold
                                    else -> RubyExpense
                                },
                                fontSize = 10.sp
                            )
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
                    text = "${FormatUtils.formatDateTime(sale.timestamp)} | #${sale.id}",
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
fun PurchaseItemCard(
    purchase: PurchaseRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrintPdf: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onShareText: () -> Unit = {}
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = purchase.modelName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${purchase.category} | مقاس: ${purchase.size} | ${purchase.color}",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                }

                Text(
                    text = FormatUtils.formatCurrency(purchase.totalCost),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = SlateBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المورد: ${purchase.supplierName}", style = MaterialTheme.typography.bodySmall)
                    if (purchase.invoiceNumber.isNotBlank()) {
                        Text("فاتورة: #${purchase.invoiceNumber}", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    val unitDesc = if (purchase.unit == com.example.ui.util.ClothingUnits.PIECE) "${purchase.quantity} حبه" else "${purchase.unitCount.toInt()} ${purchase.unit} (${purchase.quantity} حبه)"
                    Text("الكمية: $unitDesc", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (purchase.paymentMethod) {
                            "نقداً من الصندوق" -> EmeraldLight
                            "تحويل صرافة" -> Color(0xFF0D9488).copy(alpha = 0.15f)
                            "نصف كاش ونصف حوالة" -> LuxuryGold.copy(alpha = 0.15f)
                            else -> RubyLight
                        }
                    ) {
                        Text(
                            text = when (purchase.paymentMethod) {
                                "نقداً من الصندوق" -> "💵 كاش من الصندوق (${purchase.paidAmount.toInt()} ريال)"
                                "تحويل صرافة" -> "🏦 تحويل صراف: ${purchase.exchangeAccountName.ifBlank { "صراف" }} (${purchase.paidAmount.toInt()} ريال)"
                                "نصف كاش ونصف حوالة" -> "🔀 مجزأ: ${purchase.cashPaid.toInt()} كاش + ${purchase.transferPaid.toInt()} عبر ${purchase.exchangeAccountName.ifBlank { "الصراف" }}"
                                "آجل للمورد" -> "⏳ آجل للمورد"
                                else -> purchase.paymentMethod
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = when (purchase.paymentMethod) {
                                    "نقداً من الصندوق" -> EmeraldProfit
                                    "تحويل صرافة" -> Color(0xFF0D9488)
                                    "نصف كاش ونصف حوالة" -> LuxuryGold
                                    else -> RubyExpense
                                },
                                fontSize = 10.sp
                            )
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
                    text = "${FormatUtils.formatDateTime(purchase.timestamp)} | سجل #${purchase.id}",
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
