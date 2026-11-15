package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.StoreViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.StatementHelper

@Composable
internal fun OpeningKpiTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = SlateTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SlateTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun OpeningInventorySection(
    viewModel: StoreViewModel,
    products: List<ProductItem>,
    inventoryCostValue: Double,
    selectableCategories: List<String>,
    dynamicSizes: List<String>,
    dynamicColors: List<String>
) {
    var selectedCategory by remember { mutableStateOf(selectableCategories.firstOrNull() ?: "") }
    LaunchedEffect(selectableCategories) {
        if (selectedCategory.isBlank() && selectableCategories.isNotEmpty()) {
            selectedCategory = selectableCategories.first()
        }
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }

    var modelName by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(dynamicSizes.firstOrNull() ?: ClothingSizes.BIG) }
    LaunchedEffect(dynamicSizes) {
        if (selectedSize !in dynamicSizes && dynamicSizes.isNotEmpty()) {
            selectedSize = dynamicSizes.first()
        }
    }
    var colorText by remember { mutableStateOf(dynamicColors.firstOrNull() ?: "مشكل") }
    var selectedUnit by remember { mutableStateOf(ClothingUnits.PIECE) }
    var unitCountStr by remember { mutableStateOf("1") }
    var buyPriceStr by remember { mutableStateOf("") }
    var sellPriceStr by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val queuedItems = remember { mutableStateListOf<CartPurchaseItem>() }

    val parsedCount = unitCountStr.toDoubleOrNull() ?: 0.0
    val multiplier = ClothingUnits.getMultiplier(selectedUnit)
    val previewTotalPieces = (parsedCount * multiplier).toInt().coerceAtLeast(0)
    val parsedBuyUnit = buyPriceStr.toDoubleOrNull() ?: 0.0
    val parsedSellUnit = sellPriceStr.toDoubleOrNull() ?: 0.0
    val previewPieceBuy = if (multiplier > 0) parsedBuyUnit / multiplier else 0.0
    val previewPieceSell = if (multiplier > 0) parsedSellUnit / multiplier else 0.0

    fun buildItemOrNull(): CartPurchaseItem? {
        if (selectedCategory.isBlank()) {
            viewModel.showMessage("يرجى اختيار أو إضافة القسم / النوع أولاً")
            return null
        }
        if (parsedCount <= 0.0 || previewTotalPieces <= 0) {
            viewModel.showMessage("يرجى إدخال كمية صحيحة أكبر من الصفر")
            return null
        }
        if (parsedBuyUnit < 0.0) {
            viewModel.showMessage("سعر التكلفة لا يمكن أن يكون سالباً")
            return null
        }
        if (parsedSellUnit <= 0.0) {
            viewModel.showMessage("يرجى إدخال سعر بيع صحيح أكبر من الصفر")
            return null
        }
        val cleanModel = modelName.trim().ifBlank { "$selectedCategory $selectedSize" }
        val cleanColor = colorText.trim().ifBlank { "مشكل" }
        return CartPurchaseItem(
            category = selectedCategory,
            modelName = cleanModel,
            color = cleanColor,
            size = selectedSize,
            unit = selectedUnit,
            unitCount = parsedCount,
            buyPricePerUnit = parsedBuyUnit,
            sellPricePerUnit = parsedSellUnit,
            barcode = barcode.trim(),
            colorsCount = 1,
            colorDetails = cleanColor,
            notes = notes.trim().ifBlank { "رصيد افتتاحي للمخزن" }
        )
    }

    fun resetProductForm() {
        modelName = ""
        unitCountStr = "1"
        buyPriceStr = ""
        sellPriceStr = ""
        barcode = ""
        notes = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogSectionHeader(
                title = "إدخال البضاعة الموجودة في المحل (المخزن)",
                icon = Icons.Default.Inventory2,
                accentColor = FintechIndigo
            )

            DialogInfoBanner(
                title = "إجمالي تكلفة المخزن الحالي (${products.size} صنف):",
                value = FormatUtils.formatCurrency(inventoryCostValue),
                containerColor = BrandBlueBg,
                contentColor = PdfHeaderNavy,
                borderColor = BrandBlue.copy(alpha = 0.25f),
                icon = Icons.Default.Warehouse
            )

            Text(
                text = "لن يتم خصم تكلفة البضاعة من الصندوق ولن تُسجل كفاتورة شراء أو دين مورد.",
                fontSize = 12.sp,
                color = FintechEmerald,
                fontWeight = FontWeight.Medium
            )

            // Category selection + Add category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("القسم / نوع البضاعة *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                TextButton(
                    onClick = { showAddCategoryDialog = true },
                    modifier = Modifier.testTag("btn_opening_add_category")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة قسم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (selectableCategories.isEmpty()) {
                Text(
                    text = "لا توجد أقسام مضافة بعد — اضغط «إضافة قسم» لإنشاء قسم مثل: أكوات، ثياب، دجلات...",
                    fontSize = 12.sp,
                    color = RubyExpense
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectableCategories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("اسم الموديل / الصنف (اختياري)") },
                placeholder = { Text("مثال: كوت شتوي تركي") },
                leadingIcon = { Icon(Icons.Default.Checkroom, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_product_model_input")
            )

            Text("المقاس:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dynamicSizes) { sz ->
                    FilterChip(
                        selected = selectedSize == sz,
                        onClick = { selectedSize = sz },
                        label = { Text(sz, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = colorText,
                onValueChange = { colorText = it },
                label = { Text("اللون / تشكيلة الألوان") },
                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = FintechIndigo) },
                colors = storeDialogTextFieldColors(FintechIndigo),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(dynamicColors + listOf("مشكل")) { clr ->
                    AssistChip(
                        onClick = { colorText = clr },
                        label = { Text(clr, fontSize = 11.sp) }
                    )
                }
            }

            Text("وحدة الإدخال:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ClothingUnits.ALL_UNITS) { unit ->
                    FilterChip(
                        selected = selectedUnit == unit,
                        onClick = { selectedUnit = unit },
                        label = { Text(unit, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = unitCountStr,
                    onValueChange = { unitCountStr = it },
                    label = { Text("الكمية ($selectedUnit) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("opening_product_qty_input")
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FintechEmeraldBg,
                    border = BorderStroke(1.dp, FintechEmeraldBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("الإجمالي بالحبات", fontSize = 10.sp, color = FintechEmerald)
                        Text(
                            text = "$previewTotalPieces حبه",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FintechEmerald
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = buyPriceStr,
                    onValueChange = { buyPriceStr = it },
                    label = { Text("سعر التكلفة ($selectedUnit) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(PdfGoldDark),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("opening_product_buy_input")
                )
                OutlinedTextField(
                    value = sellPriceStr,
                    onValueChange = { sellPriceStr = it },
                    label = { Text("سعر البيع ($selectedUnit) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("opening_product_sell_input")
                )
            }

            if (parsedBuyUnit > 0 || parsedSellUnit > 0) {
                Text(
                    text = "سعر الحبة المحسوب — التكلفة: ${FormatUtils.formatCurrency(previewPieceBuy)} | البيع: ${FormatUtils.formatCurrency(previewPieceSell)}",
                    fontSize = 11.sp,
                    color = SlateTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("باركود (اختياري)") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الصنف") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            TransactionDatePickerField(
                selectedTimestamp = timestamp,
                onDateSelected = { timestamp = it },
                label = "تاريخ اعتماد الرصيد الافتتاحي",
                testTag = "opening_inventory_date_picker"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        buildItemOrNull()?.let { item ->
                            queuedItems.add(item)
                            resetProductForm()
                            viewModel.showMessage("تمت إضافة «${item.modelName}» لقائمة الجرد الافتتاحي")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("btn_opening_add_product_queue"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, PdfHeaderNavy)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = PdfHeaderNavy)
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة للقائمة", fontWeight = FontWeight.Bold, color = PdfHeaderNavy, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        buildItemOrNull()?.let { item ->
                            viewModel.addOpeningInventoryItems(listOf(item), timestamp) {
                                resetProductForm()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("btn_opening_save_single_product"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ مباشرة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }

    // Queued batch card
    if (queuedItems.isNotEmpty()) {
        val batchCost = queuedItems.sumOf { it.lineCost }
        val batchPieces = queuedItems.sumOf { it.totalPieces }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, FintechEmeraldBorder)
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
                        text = "قائمة أصناف بانتظار الحفظ (${queuedItems.size})",
                        fontWeight = FontWeight.Bold,
                        color = PdfHeaderNavy,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$batchPieces حبه • ${FormatUtils.formatCurrency(batchCost)}",
                        fontWeight = FontWeight.Bold,
                        color = FintechEmerald,
                        fontSize = 12.sp
                    )
                }

                queuedItems.forEachIndexed { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlateBg,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.modelName} (${item.category} • ${item.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SlateTextPrimary
                                )
                                Text(
                                    text = "${ClothingUnits.formatUnitDescription(item.unit, item.unitCount)} • التكلفة: ${FormatUtils.formatCurrency(item.lineCost)}",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )
                            }
                            IconButton(
                                onClick = { queuedItems.removeAt(idx) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف من القائمة", tint = RubyExpense)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val snapshot = queuedItems.toList()
                        viewModel.addOpeningInventoryItems(snapshot, timestamp) {
                            queuedItems.clear()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("btn_opening_save_inventory"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "اعتماد وحفظ كل البضاعة بالمخزن (${queuedItems.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Existing inventory preview
    if (products.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "البضاعة المسجلة حالياً بالمخزن (أحدث ${minOf(products.size, 8)} أصناف من أصل ${products.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PdfHeaderNavy
                )
                products.take(8).forEach { prod ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${prod.modelName} • ${prod.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                            Text(
                                text = "${prod.category} | الكمية: ${prod.quantity} حبه",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                        }
                        Text(
                            text = FormatUtils.formatCurrency(prod.buyPrice * prod.quantity),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PdfHeaderNavy
                        )
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        StoreOperationDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = "إضافة قسم جديد",
            subtitle = "سيُحفظ في إعدادات الأقسام ويُتاح فوراً",
            icon = Icons.Default.Category,
            accentColor = PdfHeaderNavy,
            confirmText = "إضافة",
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
            OutlinedTextField(
                value = newCategoryInput,
                onValueChange = { newCategoryInput = it },
                label = { Text("اسم القسم (مثال: أكوات، دجلات، ثياب)") },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun OpeningEmployeesSection(
    viewModel: StoreViewModel,
    employees: List<Employee>,
    employeeTransactions: List<EmployeeTransaction>,
    sales: List<SaleTransaction>,
    employeeRoles: List<String>
) {
    var selectedEmployeeId by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(employeeRoles.firstOrNull() ?: "بائع") }
    var salaryType by remember { mutableStateOf("شهري") }
    var baseSalaryStr by remember { mutableStateOf("") }
    var commissionStr by remember { mutableStateOf("") }
    var openingAmountStr by remember { mutableStateOf("") }
    var isOwedToEmployee by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun selectEmployee(emp: Employee?) {
        if (emp == null) {
            selectedEmployeeId = 0L
            name = ""
            phone = ""
            role = employeeRoles.firstOrNull() ?: "بائع"
            salaryType = "شهري"
            baseSalaryStr = ""
            commissionStr = ""
        } else {
            selectedEmployeeId = emp.id
            name = emp.name
            phone = emp.phone
            role = emp.role
            salaryType = emp.salaryType
            baseSalaryStr = if (emp.baseSalary > 0) FormatUtils.formatInputNumber(emp.baseSalary) else ""
            commissionStr = if (emp.commissionPercent > 0) FormatUtils.formatInputNumber(emp.commissionPercent) else ""
        }
        openingAmountStr = ""
        notes = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogSectionHeader(
                title = "العمال والأرصدة الافتتاحية للرواتب والسلف",
                icon = Icons.Default.Badge,
                accentColor = FintechAmber
            )

            Text(
                text = "سجل عمال المحل وأي مستحقات سابقة لهم أو سلف سابقة عليهم دون خصم من الصندوق أو الأرباح.",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )

            if (employees.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedEmployeeId == 0L,
                            onClick = { selectEmployee(null) },
                            label = { Text("+ عامل جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                    items(employees) { emp ->
                        FilterChip(
                            selected = selectedEmployeeId == emp.id,
                            onClick = { selectEmployee(emp) },
                            label = { Text(emp.name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم العامل الكامل *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_emp_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("الوظيفة") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(employeeRoles) { r ->
                    AssistChip(onClick = { role = r }, label = { Text(r, fontSize = 11.sp) })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSegmentedChoice(
                    selected = salaryType == "شهري",
                    label = "راتب شهري",
                    icon = Icons.Default.CalendarMonth,
                    selectedColor = PdfHeaderNavy,
                    onClick = { salaryType = "شهري" },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = salaryType == "يومي",
                    label = "راتب يومي",
                    icon = Icons.Default.Today,
                    selectedColor = FintechIndigo,
                    onClick = { salaryType = "يومي" },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = baseSalaryStr,
                    onValueChange = { baseSalaryStr = it },
                    label = { Text("الراتب الأساسي") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("opening_emp_salary_input")
                )
                OutlinedTextField(
                    value = commissionStr,
                    onValueChange = { commissionStr = it },
                    label = { Text("العمولة % (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = SlateBorder)

            Text(
                text = "الرصيد الافتتاحي السابق للعامل (اختياري):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSegmentedChoice(
                    selected = isOwedToEmployee,
                    label = "له مستحقات سابقة",
                    icon = Icons.Default.ArrowDownward,
                    selectedColor = FintechEmerald,
                    onClick = { isOwedToEmployee = true },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = !isOwedToEmployee,
                    label = "عليه سلفة سابقة",
                    icon = Icons.Default.ArrowUpward,
                    selectedColor = RubyExpense,
                    onClick = { isOwedToEmployee = false },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = openingAmountStr,
                onValueChange = { openingAmountStr = it },
                label = {
                    Text(
                        if (isOwedToEmployee) "مبلغ المستحقات السابقة للعامل (اختياري)"
                        else "مبلغ السلفة / العجز السابق على العامل (اختياري)"
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (isOwedToEmployee) FintechEmerald else RubyExpense
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(if (isOwedToEmployee) FintechEmerald else RubyExpense),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_emp_amount_input")
            )

            TransactionDatePickerField(
                selectedTimestamp = timestamp,
                onDateSelected = { timestamp = it },
                label = "تاريخ الرصيد الافتتاحي للعامل",
                testTag = "opening_emp_date_picker"
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات (اختياري)") },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (name.trim().isBlank()) {
                        viewModel.showMessage("يرجى إدخال اسم العامل")
                        return@Button
                    }
                    val baseSal = baseSalaryStr.toDoubleOrNull() ?: 0.0
                    val comm = commissionStr.toDoubleOrNull() ?: 0.0
                    val openAmt = openingAmountStr.toDoubleOrNull() ?: 0.0
                    viewModel.recordEmployeeOpeningBalance(
                        employeeId = selectedEmployeeId,
                        name = name,
                        phone = phone,
                        role = role,
                        salaryType = salaryType,
                        baseSalary = baseSal,
                        commissionPercent = comm,
                        openingAmount = openAmt,
                        isOwedToEmployee = isOwedToEmployee,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        selectEmployee(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_opening_save_employee"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("حفظ العامل ورصيده الافتتاحي", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    if (employees.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "العمال المسجلون وأرصدتهم (${employees.size}) — اضغط لتعديل الرصيد",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PdfHeaderNavy
                )
                employees.forEach { emp ->
                    val summary = remember(emp, employeeTransactions, sales) {
                        StatementHelper.buildEmployeeStatement(emp, employeeTransactions, sales)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateBg)
                            .clickable { selectEmployee(emp) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(emp.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            Text(
                                "${emp.role} • راتب ${emp.salaryType}: ${FormatUtils.formatCurrency(emp.baseSalary)}",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                        }
                        val balColor = if (summary.remainingBalance >= 0) FintechEmerald else RubyExpense
                        Text(
                            text = if (summary.remainingBalance >= 0) {
                                "له: ${FormatUtils.formatCurrency(summary.remainingBalance)}"
                            } else {
                                "عليه: ${FormatUtils.formatCurrency(-summary.remainingBalance)}"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = balColor
                        )
                    }
                }
            }
        }
    }
}
