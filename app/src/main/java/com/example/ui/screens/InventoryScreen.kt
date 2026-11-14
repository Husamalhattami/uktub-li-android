package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ProductItem
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: StoreViewModel,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var productToDelete by remember { mutableStateOf<ProductItem?>(null) }

    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    val totalItemsCount = products.size
    val availableCount = products.count { it.quantity > 5 }
    val lowStockCount = products.count { it.quantity <= 5 }

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { prod ->
            val matchCat = when (selectedCategory) {
                "الكل" -> true
                else -> prod.category.equals(selectedCategory, ignoreCase = true) || prod.category.contains(selectedCategory, ignoreCase = true)
            }
            val matchQuery = searchQuery.isBlank() ||
                    prod.modelName.contains(searchQuery, ignoreCase = true) ||
                    prod.color.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PdfHeaderNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "المخزون",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "إدارة الأصناف والكميات بكل سهولة",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF90CDF4),
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = storeSettings?.storeName?.ifBlank { "إدارة المتجر" } ?: "إدارة المتجر",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PdfGoldPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "تجارة أكثر .. نمو أكبر",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            )
                        }
                        com.example.ui.util.StoreLogoBadge(
                            logoPath = storeSettings?.logoPath.orEmpty(),
                            size = 34.dp,
                            cornerRadius = 8.dp
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(AppScreen.PURCHASE) },
                containerColor = PdfGoldDark,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_purchase")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة بضاعة")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // ==========================================
            // 1. Search Bar (Page 4 in PDF)
            // ==========================================
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن صنف", fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFCBD5E1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }

            // ==========================================
            // 2. The 3 KPI Stat Cards (Page 4 in PDF)
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: إجمالي الأصناف
                    PdfInventoryKpiCard(
                        count = totalItemsCount,
                        label = "إجمالي الأصناف",
                        icon = Icons.Default.Inventory2,
                        iconColor = Color(0xFF0F1E36),
                        modifier = Modifier.weight(1f)
                    )

                    // Card 2: أصناف متوفرة (Green Dot)
                    PdfInventoryKpiCard(
                        count = availableCount,
                        label = "أصناف متوفرة",
                        dotColor = Color(0xFF16A34A),
                        icon = Icons.Default.Inventory2,
                        iconColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )

                    // Card 3: أصناف منخفضة (Red Dot)
                    PdfInventoryKpiCard(
                        count = lowStockCount,
                        label = "أصناف منخفضة",
                        dotColor = Color(0xFFDC2626),
                        icon = Icons.Default.Inventory2,
                        iconColor = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ==========================================
            // 3. Category Filter Pills (Page 4 in PDF)
            // ==========================================
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFF3B82F6)
                            ),
                            modifier = Modifier.clickable { showAddCategoryDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نوع جديد +", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PdfHeaderNavy else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PdfHeaderNavy else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. Products Inventory List (Page 4 in PDF)
            // ==========================================
            if (filteredProducts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                            Text("لا توجد أصناف في هذا القسم", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), fontSize = 15.sp)
                        }
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    PdfInventoryProductCard(
                        product = product,
                        onEdit = { editingProduct = product },
                        onDelete = { productToDelete = product }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
        }
    }

    // Edit Product Dialog
    editingProduct?.let { prod ->
        var editModelName by remember { mutableStateOf(prod.modelName) }
        var editQtyStr by remember { mutableStateOf(prod.quantity.toString()) }
        var editSellPriceStr by remember { mutableStateOf(prod.sellPrice.toInt().toString()) }
        var editPurchaseDate by remember(prod.id) { mutableLongStateOf(prod.purchaseDate) }

        StoreOperationDialog(
            onDismissRequest = { editingProduct = null },
            title = "تعديل بيانات الصنف",
            subtitle = prod.category.ifBlank { "مخزون المتجر" },
            icon = Icons.Default.Edit,
            accentColor = PdfGoldPrimary,
            confirmText = "حفظ التعديلات",
            confirmIcon = Icons.Default.Check,
            confirmColor = PdfHeaderNavy,
            onConfirm = {
                val q = editQtyStr.toIntOrNull() ?: prod.quantity
                val p = editSellPriceStr.toDoubleOrNull() ?: prod.sellPrice
                viewModel.saveProduct(
                    prod.copy(
                        modelName = editModelName,
                        quantity = q,
                        sellPrice = p,
                        purchaseDate = editPurchaseDate
                    )
                ) {
                    editingProduct = null
                }
            }
        ) {
            OutlinedTextField(
                value = editModelName,
                onValueChange = { editModelName = it },
                label = { Text("اسم الصنف / الموديل") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy)
            )
            OutlinedTextField(
                value = editQtyStr,
                onValueChange = { editQtyStr = it },
                label = { Text("الكمية المتوفرة بالمخزن") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy)
            )
            OutlinedTextField(
                value = editSellPriceStr,
                onValueChange = { editSellPriceStr = it },
                label = { Text("سعر البيع (ريال)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy)
            )
            TransactionDatePickerField(
                selectedTimestamp = editPurchaseDate,
                onDateSelected = { editPurchaseDate = it },
                label = "تاريخ التوريد",
                testTag = "edit_product_date_picker"
            )
        }
    }

    // Delete Product Dialog
    productToDelete?.let { prod ->
        StoreOperationDialog(
            onDismissRequest = { productToDelete = null },
            title = "حذف الصنف من المخزون",
            subtitle = prod.modelName,
            icon = Icons.Default.DeleteForever,
            accentColor = FintechRose,
            confirmText = "نعم، احذف",
            confirmIcon = Icons.Default.Delete,
            confirmColor = FintechRose,
            onConfirm = {
                viewModel.deleteProduct(prod.id)
                productToDelete = null
            }
        ) {
            DialogInfoBanner(
                title = "تنبيه حذف صنف",
                value = "هل أنت متأكد من حذف (${prod.modelName})؟ سيتم حذفه من قائمة المخزون.",
                containerColor = Color(0xFFFEF2F2),
                contentColor = FintechRose,
                borderColor = Color(0xFFFECACA),
                icon = Icons.Default.WarningAmber
            )
        }
    }

    if (showAddCategoryDialog) {
        StoreOperationDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = "إضافة نوع صنف جديد",
            subtitle = "سيظهر فوراً في قائمة الاختيارات السريعة",
            icon = Icons.Default.Category,
            accentColor = PdfGoldPrimary,
            confirmText = "إضافة واختيار",
            confirmIcon = Icons.Default.AddCircleOutline,
            confirmColor = PdfHeaderNavy,
            confirmEnabled = newCategoryInput.trim().isNotBlank(),
            confirmTestTag = "confirm_add_inventory_category_btn",
            onConfirm = {
                val trimmed = newCategoryInput.trim()
                if (trimmed.isNotBlank()) {
                    viewModel.addNewCategory(trimmed)
                    selectedCategory = trimmed
                    newCategoryInput = ""
                    showAddCategoryDialog = false
                }
            }
        ) {
            Text("اكتب اسم نوع الصنف وسيظهر فوراً في قائمة الاختيارات السريعة:", fontSize = 12.sp, color = SlateTextSecondary)
            OutlinedTextField(
                value = newCategoryInput,
                onValueChange = { newCategoryInput = it },
                label = { Text("اسم نوع الصنف (مثلاً: أرواب، سراويل، شيلان...)") },
                modifier = Modifier.fillMaxWidth().testTag("inventory_new_category_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy)
            )
        }
    }
}

// ==========================================
// Sub-Components for Page 4
// ==========================================

@Composable
fun PdfInventoryKpiCard(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    Card(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count.toString(),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (dotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PdfInventoryProductCard(
    product: ProductItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column (in RTL: 3-dots + Status pill)
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تعديل الصنف") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف الصنف", color = FintechRose) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = FintechRose) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Availability Badge
                val isAvailable = product.quantity > 5
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAvailable) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAvailable) Color(0xFFBBF7D0) else Color(0xFFFDE68A))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isAvailable) Color(0xFF16A34A) else Color(0xFFD97706))
                        )
                        Text(
                            text = if (isAvailable) "متوفر" else "منخفض",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAvailable) Color(0xFF16A34A) else Color(0xFFD97706)
                        )
                    }
                }
            }

            // Center Column: Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = product.modelName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${product.category} - فاخرة",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                // Barcode representation
                Text(
                    text = (product.barcode.ifBlank { "TS-${product.id}" }) + " ||||||||",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )

                // 3 Bottom indicators row: آخر توريد | سعر الوحدة | الكمية
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // آخر توريد
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("آخر توريد", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        Text(
                            FormatUtils.formatDate(product.purchaseDate),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    // سعر الوحدة
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("سعر الوحدة", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        Text(
                            "${product.sellPrice.toInt()} ر.س",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    // الكمية المتوفرة
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الكمية المتوفرة", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Checkroom, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                product.quantity.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            // Right Box: Category & Price Tag (بدون أي أيقونات أو صور بناءً على طلب العميل)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    product.category.contains("بشت") || product.modelName.contains("بشت") -> Color(0xFFFEF3C7)
                    product.category.contains("كوت") || product.modelName.contains("كوت") -> Color(0xFFEFF6FF)
                    product.category.contains("جاكت") || product.modelName.contains("جاكت") -> Color(0xFFF0FDF4)
                    else -> Color(0xFFF1F5F9)
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1).copy(alpha = 0.5f)),
                modifier = Modifier.width(72.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "النوع",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = product.category.ifBlank { "ملبوسات" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${product.sellPrice.toInt()} ريال",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }
    }
}
