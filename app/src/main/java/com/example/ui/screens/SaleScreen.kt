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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Employee
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
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

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
    var paymentType by remember { mutableStateOf("نقداً") } // "نقداً" or "بالدين"
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }

    // When product or unit changes, recalculate suggested unit price
    LaunchedEffect(selectedProduct, selectedSaleUnit) {
        selectedProduct?.let { prod ->
            val multiplier = ClothingUnits.getMultiplier(selectedSaleUnit)
            sellPriceStr = (prod.sellPrice * multiplier).toLong().toString()
            quantityStr = "1"
            discountStr = "0"
        }
    }

    // Default employee to first if none selected
    LaunchedEffect(employees) {
        if (selectedEmployee == null && employees.isNotEmpty()) {
            selectedEmployee = employees.firstOrNull()
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

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "تسجيل بيع سريع",
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
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Select Product
            item {
                Text(
                    text = "1. اختر القطعة المباعة من المخزون",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث بالاسم، المقاس (كبير/وسط/صغير/شبابي)، أو اللون...") },
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
                        .testTag("sale_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Category filter chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("category_filter_$cat")
                        )
                    }
                }
            }

            // Product Cards horizontal carousel
            item {
                if (filteredProducts.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد بضاعة مطابقة. يرجى مراجعة المخزون أو تسجيل شراء جديد.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredProducts) { item ->
                            val isSelected = selectedProduct?.id == item.id
                            Card(
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { selectedProduct = item }
                                    .testTag("product_card_${item.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) GoldContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, LuxuryGold) else null,
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = item.category,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        // Stock badge
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (item.quantity <= 3) RubyLight else EmeraldLight
                                        ) {
                                            Text(
                                                text = "${item.quantity} حبه",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.quantity <= 3) RubyExpense else EmeraldProfit
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = item.modelName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "المقاس: ${item.size}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = item.color,
                                        style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = FormatUtils.formatCurrency(item.sellPrice) + " / حبه",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = LuxuryGold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Sale Details (if product chosen)
            if (selectedProduct != null) {
                val prod = selectedProduct!!

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "2. تفاصيل الفاتورة والبيع",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Selected item highlight summary
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${prod.modelName} (مقاس: ${prod.size})",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "التشكيلة: ${prod.color}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                                        )
                                        Text(
                                            text = "المخزون المتوفر: ${prod.quantity} حبه (${prod.quantity / 12} درزن و ${prod.quantity % 12} حبه)",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            // Selling Unit Selector: حبه / نص درزن / درزن / كورجه
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "نوع وحدة البيع:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    saleUnits.forEach { unit ->
                                        FilterChip(
                                            selected = selectedSaleUnit == unit,
                                            onClick = { selectedSaleUnit = unit },
                                            label = { Text(unit, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Quantity Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "الكمية بـ ($selectedSaleUnit):",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (selectedSaleUnit != ClothingUnits.PIECE) {
                                        Text(
                                            text = "تساوي $totalPiecesToSell حبه من المخزون",
                                            style = MaterialTheme.typography.bodySmall.copy(color = LuxuryGold, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledIconButton(
                                        onClick = {
                                            val current = quantityStr.toIntOrNull() ?: 1
                                            if (current > 1) quantityStr = (current - 1).toString()
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "إنقاص")
                                    }

                                    OutlinedTextField(
                                        value = quantityStr,
                                        onValueChange = { quantityStr = it },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .width(70.dp)
                                            .testTag("sale_quantity_input"),
                                        textStyle = LocalTextStyle.current.copy(
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        singleLine = true
                                    )

                                    FilledIconButton(
                                        onClick = {
                                            val current = quantityStr.toIntOrNull() ?: 1
                                            if ((current + 1) * multiplier <= prod.quantity) {
                                                quantityStr = (current + 1).toString()
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "زيادة")
                                    }
                                }
                            }

                            // Selling Price field (in Riyals)
                            Column {
                                Text(
                                    text = "سعر البيع لـ ($selectedSaleUnit) بالريال:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = sellPriceStr,
                                    onValueChange = { sellPriceStr = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sale_price_input"),
                                    singleLine = true
                                )
                                QuickThousandAdder(
                                    currentValue = sellPriceStr,
                                    onValueChange = { sellPriceStr = it }
                                )
                            }

                            // Discount field
                            Column {
                                Text(
                                    text = "الخصم بالريال (اختياري):",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = discountStr,
                                    onValueChange = { discountStr = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sale_discount_input"),
                                    singleLine = true
                                )
                            }

                            HorizontalDivider(color = SlateBorder)

                            // Customer Info
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "بيانات الزبون (اختياري):",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                OutlinedTextField(
                                    value = customerName,
                                    onValueChange = { customerName = it },
                                    placeholder = { Text("اسم الزبون") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sale_customer_name"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    placeholder = { Text("رقم هاتف الزبون") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sale_customer_phone"),
                                    singleLine = true
                                )
                            }

                            // Payment Type Toggle (نقداً vs بالدين)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "طريقة الدفع:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val isCash = paymentType == "نقداً"
                                    Button(
                                        onClick = { paymentType = "نقداً" },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("pay_cash_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCash) EmeraldProfit else SlateBorder,
                                            contentColor = if (isCash) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("نقداً (كاش)", fontWeight = FontWeight.Bold)
                                    }

                                    val isDebt = paymentType == "بالدين"
                                    Button(
                                        onClick = { paymentType = "بالدين" },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("pay_debt_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDebt) RubyExpense else SlateBorder,
                                            contentColor = if (isDebt) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(Icons.Default.CreditCard, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("بالدين (آجل)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Employee selector
                            if (employees.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "الموظف البائع:",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(employees) { emp ->
                                            FilterChip(
                                                selected = selectedEmployee?.id == emp.id,
                                                onClick = { selectedEmployee = emp },
                                                label = { Text(emp.name) },
                                                modifier = Modifier.testTag("employee_chip_${emp.id}")
                                            )
                                        }
                                    }
                                }
                            }

                            // Auto-Calculations Summary Box
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("الإجمالي المطلوب من الزبون بالريال:", color = Color.White)
                                        Text(
                                            text = FormatUtils.formatCurrency(totalAmount),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = LuxuryGoldLight
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "الربح المحسوب تلقائياً:",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.85f))
                                        )
                                        Text(
                                            text = "+ " + FormatUtils.formatCurrency(estimatedProfit),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldProfit
                                            )
                                        )
                                    }
                                }
                            }

                            // Big Confirmation Button
                            Button(
                                onClick = {
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
                                        onSuccess = {
                                            selectedProduct = null
                                            customerName = ""
                                            customerPhone = ""
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("confirm_sale_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إتمام البيع وحفظ الفاتورة",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
