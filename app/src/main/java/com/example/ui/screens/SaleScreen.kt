package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Employee
import com.example.data.model.PartyAccount
import com.example.data.model.ProductItem
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils

@Composable
fun SaleScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()

    val customerParties = remember(parties) { parties.filter { it.type == "CUSTOMER" } }
    var autoSaveCustomer by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var selectedProduct by remember { mutableStateOf<ProductItem?>(null) }

    val saleUnits = listOf(ClothingUnits.PIECE, ClothingUnits.HALF_DOZEN, ClothingUnits.DOZEN, ClothingUnits.KORJAH)
    var selectedSaleUnit by remember { mutableStateOf(ClothingUnits.PIECE) }

    var quantityStr by remember { mutableStateOf("1") }
    var sellPriceStr by remember { mutableStateOf("") }
    var discountStr by remember { mutableStateOf("0") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("نقداً") } // "نقداً", "تحويل صرافة", "نصف كاش ونصف حوالة", "بالدين"
    var selectedExchangeAccount by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var cashPartStr by remember { mutableStateOf("") }
    var transferPartStr by remember { mutableStateOf("") }
    var transferRef by remember { mutableStateOf("") }
    var selectedEmployee by remember { mutableStateOf(employees.firstOrNull()) }

    LaunchedEffect(selectedProduct, selectedSaleUnit) {
        selectedProduct?.let { prod ->
            val multiplier = ClothingUnits.getMultiplier(selectedSaleUnit)
            sellPriceStr = (prod.sellPrice * multiplier).toLong().toString()
            quantityStr = "1"
            discountStr = "0"
        }
    }

    LaunchedEffect(employees) {
        if (selectedEmployee == null && employees.isNotEmpty()) {
            selectedEmployee = employees.firstOrNull()
        }
    }

    LaunchedEffect(exchangeAccounts) {
        if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
            selectedExchangeAccount = exchangeAccounts.firstOrNull()
        }
    }

    val categories = remember(products) {
        listOf("الكل") + products.map { it.category }.distinct()
    }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { prod ->
            val matchCategory = selectedCategory == "الكل" || prod.category == selectedCategory
            val matchQuery = searchQuery.isBlank() ||
                    prod.modelName.contains(searchQuery, ignoreCase = true) ||
                    prod.color.contains(searchQuery, ignoreCase = true) ||
                    prod.size.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val qty = quantityStr.toIntOrNull() ?: 1
    val multiplier = ClothingUnits.getMultiplier(selectedSaleUnit)
    val totalPiecesToSell = qty * multiplier

    val unitPrice = sellPriceStr.toDoubleOrNull() ?: 0.0
    val discount = discountStr.toDoubleOrNull() ?: 0.0
    val totalAmount = ((unitPrice * qty) - discount).coerceAtLeast(0.0)
    val cogs = (selectedProduct?.buyPrice ?: 0.0) * totalPiecesToSell
    val estimatedProfit = totalAmount - cogs

    // Auto-calculate split
    LaunchedEffect(totalAmount, paymentType) {
        if (paymentType == "نصف كاش ونصف حوالة") {
            val half = (totalAmount / 2).toLong()
            cashPartStr = half.toString()
            transferPartStr = (totalAmount.toLong() - half).toString()
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "تسجيل بيع جديد",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SlateBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // Step 1: Select Clothing Item from Stock
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. اختر القطعة المباعة من المخزون",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                            if (selectedProduct != null) {
                                TextButton(onClick = { selectedProduct = null }) {
                                    Text("تغيير القطعة", fontSize = 12.sp, color = BrandBlue)
                                }
                            }
                        }

                        if (selectedProduct == null) {
                            // Search bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث بالاسم أو المقاس أو اللون...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sale_product_search"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Category filter chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Product Items List
                            if (filteredProducts.isEmpty()) {
                                Text(
                                    text = "لا توجد قطع مطابقة في المخزون.",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = SlateTextMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredProducts.take(6).forEach { prod ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = SlateBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedProduct = prod }
                                                .testTag("select_product_${prod.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(prod.modelName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        "${prod.category} • مقاس: ${prod.size} • ${prod.color}",
                                                        fontSize = 11.sp,
                                                        color = SlateTextSecondary
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = FormatUtils.formatCurrency(prod.sellPrice),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = BrandBlue
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (prod.quantity <= 3) FintechRoseBg else FintechEmeraldBg
                                                    ) {
                                                        Text(
                                                            text = "متوفر: ${prod.quantity} حبة",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            fontSize = 10.sp,
                                                            color = if (prod.quantity <= 3) FintechRose else FintechEmerald,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Selected Item Showcase Box
                            val prod = selectedProduct!!
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandBlueBg,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.modelName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BrandNavy)
                                        Text(
                                            "النوع: ${prod.category} | المقاس: ${prod.size} | الألوان: ${prod.color}",
                                            fontSize = 11.sp,
                                            color = SlateTextSecondary
                                        )
                                        Text(
                                            "المخزون المتوفر: ${prod.quantity} حبة",
                                            fontSize = 11.sp,
                                            color = FintechEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = FormatUtils.formatCurrency(prod.sellPrice) + " / حبة",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BrandBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Step 2 & 3: Quantity, Unit, Price, and Payment (Only shown when product is picked)
            // ==========================================
            item {
            if (selectedProduct != null) {
                val prod = selectedProduct!!

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. تحديد الكمية ووحدة البيع والسعر",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                        )

                        // Unit Selector (حبه، نص درزن، درزن، كورجه)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("وحدة البيع:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                saleUnits.forEach { unit ->
                                    val isSelected = selectedSaleUnit == unit
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) BrandBlue else SlateBg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) BrandBlue else SlateBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clickable { selectedSaleUnit = unit }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = unit,
                                                color = if (isSelected) Color.White else SlateTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Quantity & Price Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = quantityStr,
                                onValueChange = { quantityStr = it },
                                label = { Text("عدد ($selectedSaleUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_quantity_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = sellPriceStr,
                                onValueChange = { sellPriceStr = it },
                                label = { Text("سعر البيع للـ ($selectedSaleUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_price_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Discount & Total Pieces Notice
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it },
                                label = { Text("خصم للزبون (ريال)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_discount_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.weight(1f).padding(top = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("القطع المخصومة من الرف:", fontSize = 10.sp, color = SlateTextSecondary)
                                    Text("$totalPiecesToSell حبة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandNavy)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // Step 3: Payment Type & Customer Selection
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "3. طريقة الدفع وبيانات الزبون",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                        )

                        // 4 Big Payment Method Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaymentOptionButton(
                                label = "نقداً (كاش)",
                                icon = Icons.Default.Payments,
                                isSelected = paymentType == "نقداً",
                                accentColor = FintechEmerald,
                                modifier = Modifier.weight(1f).testTag("pay_cash_button"),
                                onClick = { paymentType = "نقداً" }
                            )
                            PaymentOptionButton(
                                label = "تحويل صرافة",
                                icon = Icons.Default.AccountBalance,
                                isSelected = paymentType == "تحويل صرافة",
                                accentColor = FintechTeal,
                                modifier = Modifier.weight(1f).testTag("pay_transfer_button"),
                                onClick = { paymentType = "تحويل صرافة" }
                            )
                            PaymentOptionButton(
                                label = "نصف كاش / حوالة",
                                icon = Icons.Default.CallSplit,
                                isSelected = paymentType == "نصف كاش ونصف حوالة",
                                accentColor = FintechIndigo,
                                modifier = Modifier.weight(1f).testTag("pay_split_button"),
                                onClick = { paymentType = "نصف كاش ونصف حوالة" }
                            )
                            PaymentOptionButton(
                                label = "بالدين (آجل)",
                                icon = Icons.Default.HistoryEdu,
                                isSelected = paymentType == "بالدين",
                                accentColor = FintechAmber,
                                modifier = Modifier.weight(1f).testTag("pay_debt_button"),
                                onClick = { paymentType = "بالدين" }
                            )
                        }

                        // Exchange Account picker if transfer or split
                        if (paymentType == "تحويل صرافة" || paymentType == "نصف كاش ونصف حوالة") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandBlueBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("اختر حساب الصراف أو المحفظة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandNavy)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        exchangeAccounts.forEach { acc ->
                                            FilterChip(
                                                selected = selectedExchangeAccount?.id == acc.id,
                                                onClick = { selectedExchangeAccount = acc },
                                                label = { Text(acc.name, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = transferRef,
                                        onValueChange = { transferRef = it },
                                        placeholder = { Text("رقم الحوالة أو السند (اختياري)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = SlateBorder)

                        // Customer Selection & Quick Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الزبون (اختياري / مطلوب للآجل):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (customerParties.isNotEmpty()) {
                                    Text("أو اختر من الزبائن:", fontSize = 10.sp, color = SlateTextMuted)
                                }
                            }

                            if (customerParties.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(customerParties) { c ->
                                        val isSelected = customerName.trim().equals(c.name.trim(), ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                customerName = c.name
                                                if (c.phone.isNotBlank()) customerPhone = c.phone
                                            },
                                            label = { Text(c.name, fontSize = 11.sp) },
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it },
                                    placeholder = { Text("اسم الزبون") },
                                    modifier = Modifier.weight(1f).testTag("sale_customer_name"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    placeholder = { Text("الهاتف") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.weight(1f).testTag("sale_customer_phone"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Real-time debt warning for selected customer
                            if (customerName.isNotBlank()) {
                                val matchingDebts = debts.filter {
                                    it.partyName.trim().equals(customerName.trim(), ignoreCase = true) &&
                                            it.type == "CUSTOMER" &&
                                            !it.isSettled
                                }
                                val activeDebtAmount = matchingDebts.sumOf { it.remainingAmount }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (activeDebtAmount > 0.01) FintechAmberBg else FintechEmeraldBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeDebtAmount > 0.01) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (activeDebtAmount > 0.01) FintechAmber else FintechEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (activeDebtAmount > 0.01) "هذا الزبون عليه زلط سابقة: ${FormatUtils.formatCurrency(activeDebtAmount)}" else "حساب هذا الزبون خالص",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeDebtAmount > 0.01) FintechAmber else FintechEmerald
                                        )
                                    }
                                }
                            }
                        }

                        // Employee picker
                        if (employees.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("البائع:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(employees) { emp ->
                                        FilterChip(
                                            selected = selectedEmployee?.id == emp.id,
                                            onClick = { selectedEmployee = emp },
                                            label = { Text(emp.name, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // Total Summary & Big Confirmation Button
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المبلغ المطلوب من الزبون:",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                            )
                            Text(
                                text = FormatUtils.formatCurrency(totalAmount),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الربح المحسوب تلقائياً:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                            Text(
                                text = "+ " + FormatUtils.formatCurrency(estimatedProfit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FintechEmeraldLight
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Large Confirm Button
                        Button(
                            onClick = {
                                val cashPart = cashPartStr.toDoubleOrNull() ?: 0.0
                                val transferPart = transferPartStr.toDoubleOrNull() ?: 0.0
                                viewModel.executeSale(
                                    product = prod,
                                    quantity = qty,
                                    sellPrice = unitPrice,
                                    discount = discount,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    paymentType = paymentType,
                                    employee = selectedEmployee,
                                    unit = selectedSaleUnit,
                                    cashPart = cashPart,
                                    transferPart = transferPart,
                                    exchangeAccount = selectedExchangeAccount,
                                    transferRef = transferRef,
                                    onSuccess = {
                                        if (autoSaveCustomer && customerName.isNotBlank() && customerParties.none { it.name.trim().equals(customerName.trim(), ignoreCase = true) }) {
                                            viewModel.saveParty(
                                                PartyAccount(
                                                    type = "CUSTOMER",
                                                    name = customerName.trim(),
                                                    phone = customerPhone.trim(),
                                                    notes = "تم فتح الحساب من شاشة البيع"
                                                ),
                                                initialDebt = 0.0,
                                                debtNotes = ""
                                            ) {}
                                        }
                                        selectedProduct = null
                                        customerName = ""
                                        customerPhone = ""
                                        transferRef = ""
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("confirm_sale_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تأكيد البيع وطباعة الفاتورة",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun PaymentOptionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) accentColor else SlateBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) accentColor else SlateBorder
        ),
        modifier = modifier
            .height(58.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else SlateTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
