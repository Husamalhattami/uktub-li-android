package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProductItem
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun InventoryScreen(
    viewModel: StoreViewModel,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val totalCostValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val totalSellValue by viewModel.inventoryExpectedSalesValue.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("الكل") } // "الكل", "قارب على النفاد", "الأكثر مبيعاً", "بضاعة راكدة"
    var selectedCategoryFilter by remember { mutableStateOf("كل الأصناف") }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }

    // Identify top selling product IDs
    val topSellingProductIds = remember(sales) {
        sales.groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()
    }

    val categories = remember(products) {
        listOf("كل الأصناف") + products.map { it.category }.distinct()
    }

    val totalPieces = remember(products) { products.sumOf { it.quantity } }
    val lowStockCount = remember(products) { products.count { it.quantity <= 3 } }

    val filteredProducts = remember(products, searchQuery, selectedFilter, selectedCategoryFilter, topSellingProductIds) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86400000L

        products.filter { prod ->
            val matchesSearch = searchQuery.isBlank() ||
                    prod.modelName.contains(searchQuery, ignoreCase = true) ||
                    prod.color.contains(searchQuery, ignoreCase = true) ||
                    prod.size.contains(searchQuery, ignoreCase = true) ||
                    prod.supplierName.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategoryFilter == "كل الأصناف" || prod.category == selectedCategoryFilter

            val matchesFilter = when (selectedFilter) {
                "قارب على النفاد" -> prod.quantity <= 3
                "الأكثر مبيعاً" -> topSellingProductIds.contains(prod.id)
                "بضاعة راكدة" -> prod.lastSoldTimestamp < sevenDaysAgo && prod.quantity > 0
                else -> true
            }

            matchesSearch && matchesCategory && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "مخزون المحل",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(AppScreen.PURCHASE) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("شراء / إضافة بضاعة") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_product")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // KPI Bar: Total pieces, Inventory Value, Expected Profit
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                                text = "ملخص المخزون الحالي",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LuxuryGoldLight.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "$totalPieces قطعة بالمحل",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(color = LuxuryGoldLight, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "رأس المال (تكلفة البضاعة)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalCostValue),
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "قيمة البيع المتوقعة",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalSellValue),
                                    style = MaterialTheme.typography.titleMedium.copy(color = LuxuryGoldLight, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الأرباح المتوقعة عند بيع المخزون:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.85f))
                            )
                            Text(
                                text = "+ " + FormatUtils.formatCurrency((totalSellValue - totalCostValue).coerceAtLeast(0.0)),
                                style = MaterialTheme.typography.bodyMedium.copy(color = EmeraldProfit, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن صنف، لون، قياس، مورد...") },
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
                        .testTag("inventory_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Status Filter Tabs
            item {
                val statusFilters = listOf(
                    "الكل" to null,
                    "قارب على النفاد" to if (lowStockCount > 0) "$lowStockCount" else null,
                    "الأكثر مبيعاً" to null,
                    "بضاعة راكدة" to null
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(statusFilters) { (filter, badge) ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(filter)
                                    if (badge != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = RubyExpense,
                                            modifier = Modifier.padding(start = 2.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("filter_$filter")
                        )
                    }
                }
            }

            // Category Horizontal Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Items List
            if (filteredProducts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد أصناف مطابقة للبحث أو الفلتر المختار.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    InventoryProductCard(
                        product = product,
                        onQuickAddStock = {
                            viewModel.saveProduct(product.copy(quantity = product.quantity + 1)) {}
                        },
                        onQuickReduceStock = {
                            if (product.quantity > 0) {
                                viewModel.saveProduct(product.copy(quantity = product.quantity - 1)) {}
                            }
                        },
                        onEdit = { editingProduct = product },
                        onDelete = { viewModel.deleteProduct(product.id) }
                    )
                }
            }
        }
    }

    // Edit Product Dialog
    editingProduct?.let { product ->
        var editModel by remember { mutableStateOf(product.modelName) }
        var editColor by remember { mutableStateOf(product.color) }
        var editSize by remember { mutableStateOf(product.size) }
        var editQty by remember { mutableStateOf(product.quantity.toString()) }
        var editBuyPrice by remember { mutableStateOf(product.buyPrice.toLong().toString()) }
        var editSellPrice by remember { mutableStateOf(product.sellPrice.toLong().toString()) }

        AlertDialog(
            onDismissRequest = { editingProduct = null },
            title = { Text("تعديل بيانات الصنف", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text("اسم الموديل / الوصف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editColor,
                            onValueChange = { editColor = it },
                            label = { Text("اللون") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editSize,
                            onValueChange = { editSize = it },
                            label = { Text("القياس") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        label = { Text("الكمية الإجمالية بالحبات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBuyPrice,
                        onValueChange = { editBuyPrice = it },
                        label = { Text("سعر الشراء للحبة (ريال)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSellPrice,
                        onValueChange = { editSellPrice = it },
                        label = { Text("سعر البيع للحبة (ريال)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = product.copy(
                            modelName = editModel,
                            color = editColor,
                            size = editSize,
                            quantity = editQty.toIntOrNull() ?: product.quantity,
                            buyPrice = editBuyPrice.toDoubleOrNull() ?: product.buyPrice,
                            sellPrice = editSellPrice.toDoubleOrNull() ?: product.sellPrice
                        )
                        viewModel.saveProduct(updated) {
                            editingProduct = null
                        }
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingProduct = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun InventoryProductCard(
    product: ProductItem,
    onQuickAddStock: () -> Unit,
    onQuickReduceStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val profitPerPiece = product.sellPrice - product.buyPrice
    val profitMargin = if (product.buyPrice > 0) ((profitPerPiece / product.buyPrice) * 100).toInt() else 0
    val isLowStock = product.quantity <= 3

    val unitEquiv = when {
        product.quantity >= 20 -> "${product.quantity / 20} كورجه و ${product.quantity % 20} حبه"
        product.quantity >= 12 -> "${product.quantity / 12} درزن و ${product.quantity % 12} حبه"
        product.quantity >= 6 -> "${product.quantity / 6} نص درزن و ${product.quantity % 6} حبه"
        else -> "${product.quantity} حبه"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Category, Name, Stock Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = product.category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldContainer
                        ) {
                            Text(
                                text = "مقاس: ${product.size}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = LuxuryGold)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.modelName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "التشكيلة: ${product.color}",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLowStock) RubyLight else EmeraldLight
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isLowStock) "قاربت: ${product.quantity} حبه" else "متوفر: ${product.quantity} حبه",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isLowStock) RubyExpense else EmeraldProfit
                            )
                        )
                        Text(
                            text = "($unitEquiv)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = if (isLowStock) RubyExpense else EmeraldProfit
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Pricing & Profit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "سعر الشراء", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(
                        text = FormatUtils.formatCurrency(product.buyPrice),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Column {
                    Text(text = "سعر البيع", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(
                        text = FormatUtils.formatCurrency(product.sellPrice),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = LuxuryGold)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "ربح القطعة", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(
                        text = "+ " + FormatUtils.formatCurrency(profitPerPiece) + " ($profitMargin%)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit)
                    )
                }
            }

            if (product.supplierName.isNotBlank() || product.barcode.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (product.supplierName.isNotBlank()) {
                        Text(
                            text = "المورد: ${product.supplierName}",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                    if (product.barcode.isNotBlank()) {
                        Text(
                            text = "كود: ${product.barcode}",
                            style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                        )
                    }
                }
            }

            // Quick Actions: Adjust Stock, Edit, Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock fast adjusters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("تعديل فوري:", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    OutlinedIconButton(
                        onClick = onQuickReduceStock,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    OutlinedIconButton(
                        onClick = onQuickAddStock,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = RubyExpense)
                    }
                }
            }
        }
    }
}
