package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExchangeAccount
import com.example.data.model.PartyAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils

@Composable
fun PurchaseScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()

    val supplierParties = remember(parties) { parties.filter { it.type == "SUPPLIER" } }
    var autoSaveSupplier by remember { mutableStateOf(true) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }

    val clothingTypes = listOf("بشت", "كوت", "دجلة", "جاكت", "ثوب", "قميص", "بنطلون", "دشداشة", "بدلة", "شال/غترة")
    val standardSizes = listOf(ClothingSizes.BIG, ClothingSizes.MEDIUM, ClothingSizes.SMALL, ClothingSizes.YOUTH)
    val standardUnits = listOf(ClothingUnits.DOZEN, ClothingUnits.HALF_DOZEN, ClothingUnits.PIECE, ClothingUnits.KORJAH)
    val popularColors = listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "رصاصي", "بني", "زيتي", "سكري", "أزرق")

    var selectedType by remember { mutableStateOf(clothingTypes.first()) }
    var modelName by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(ClothingSizes.BIG) }
    var selectedUnit by remember { mutableStateOf(ClothingUnits.DOZEN) }
    var unitCountStr by remember { mutableStateOf("1") }

    // Color assortment
    var colorsCount by remember { mutableIntStateOf(3) }
    val selectedColorsSet = remember { mutableStateListOf("أسود", "كحلي", "بيج") }
    var customColorsText by remember { mutableStateOf("") }

    var buyPricePerUnitStr by remember { mutableStateOf("15000") } // YER realistic
    var sellPricePerUnitStr by remember { mutableStateOf("22000") } // YER realistic
    var supplierName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقداً من الصندوق") }
    var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }
    var cashPaidStr by remember { mutableStateOf("") }
    var transferPaidStr by remember { mutableStateOf("") }

    val unitCount = unitCountStr.toDoubleOrNull() ?: 1.0
    val multiplier = ClothingUnits.getMultiplier(selectedUnit)
    val totalPieces = (unitCount * multiplier).toInt().coerceAtLeast(1)

    val buyPriceUnit = buyPricePerUnitStr.toDoubleOrNull() ?: 0.0
    val sellPriceUnit = sellPricePerUnitStr.toDoubleOrNull() ?: 0.0

    val totalCost = buyPriceUnit * unitCount
    val expectedRevenue = sellPriceUnit * unitCount
    val expectedProfit = expectedRevenue - totalCost
    val profitPercentage = if (totalCost > 0) (expectedProfit / totalCost) * 100 else 0.0

    // Auto calculate split when totalCost changes
    LaunchedEffect(totalCost, paymentMethod) {
        if (paymentMethod == "نصف كاش ونصف حوالة") {
            val half = (totalCost / 2).toLong()
            cashPaidStr = half.toString()
            transferPaidStr = (totalCost.toLong() - half).toString()
        }
    }

    LaunchedEffect(exchangeAccounts) {
        if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
            selectedExchangeAccount = exchangeAccounts.firstOrNull()
        }
    }

    val formattedColorString = remember(colorsCount, selectedColorsSet.toList(), customColorsText) {
        val colorNames = if (customColorsText.isNotBlank()) customColorsText else selectedColorsSet.joinToString("، ")
        val countLabel = when (colorsCount) {
            1 -> "لون واحد"
            2 -> "لونين"
            3 -> "3 ألوان"
            4 -> "4 ألوان"
            5 -> "5 ألوان"
            else -> "$colorsCount ألوان مشكلة"
        }
        if (colorNames.isNotBlank()) "$countLabel: $colorNames" else countLabel
    }

    // Active debt for selected supplier
    val activeDebtAmount = remember(supplierName, debts) {
        if (supplierName.isBlank()) 0.0
        else debts.filter { it.type == "SUPPLIER" && it.partyName.trim().equals(supplierName.trim(), ignoreCase = true) && !it.isSettled }
            .sumOf { it.remainingAmount }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "شراء بضاعة جديدة للمخزون",
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
            // STEP 1: Item & Category & Size
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FintechIndigoBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", color = FintechIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "نوع البضاعة والموديل والمقاس",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                        }

                        // Clothing Type Chips
                        Text(text = "اختر الصنف:", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(clothingTypes) { type ->
                                val isSelected = selectedType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedType = type },
                                    label = { Text(type, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FintechIndigoBg,
                                        selectedLabelColor = FintechIndigo
                                    ),
                                    modifier = Modifier.testTag("clothing_type_$type")
                                )
                            }
                        }

                        // Model name
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("الموديل أو الوصف (مثال: بشت ملكي صيفي، ثوب كويتي مطرز)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purchase_model_name"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Size Chips
                        Text(
                            text = "المقاس (كبير - وسط - صغير - شبابي):",
                            style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            standardSizes.forEach { size ->
                                val isSelected = selectedSize == size
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSize = size },
                                    label = {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text(size, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("size_chip_$size"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FintechIndigoBg,
                                        selectedLabelColor = FintechIndigo
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // STEP 2: Quantity & Color Assortment
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FintechAmberBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", color = FintechAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "وحدة الشراء وتشكيلة الألوان",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                        }

                        // Unit chips
                        Text(text = "وحدة الشراء:", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            standardUnits.forEach { unit ->
                                val isSelected = selectedUnit == unit
                                val multiplierLabel = when (unit) {
                                    ClothingUnits.PIECE -> "1 حبه"
                                    ClothingUnits.HALF_DOZEN -> "6 حبات"
                                    ClothingUnits.DOZEN -> "12 حبه"
                                    ClothingUnits.KORJAH -> "20 حبه"
                                    else -> ""
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) FintechAmberBg else SlateBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) FintechAmber else SlateBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedUnit = unit }
                                        .testTag("unit_chip_$unit")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = unit,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) FintechAmber else SlateTextPrimary
                                        )
                                        Text(
                                            text = multiplierLabel,
                                            fontSize = 10.sp,
                                            color = if (isSelected) FintechAmber else SlateTextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Quantity counter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = unitCountStr,
                                onValueChange = { unitCountStr = it },
                                label = { Text("عدد ($selectedUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("purchase_qty_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandBlueBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "إجمالي الحبات:", fontSize = 11.sp, color = BrandBlue)
                                    Text(
                                        text = "$totalPieces حبة بالمخزن",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandNavy
                                    )
                                }
                            }
                        }

                        // Colors count slider / buttons
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تشكيلة ألوان الـ ($selectedUnit):",
                                    style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary)
                                )
                                Text(
                                    text = "$colorsCount ألوان",
                                    fontWeight = FontWeight.Bold,
                                    color = FintechIndigo
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 2, 3, 4, 6).forEach { count ->
                                    val isSelected = colorsCount == count
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) FintechIndigo else SlateBg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) FintechIndigo else SlateBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { colorsCount = count }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (count) {
                                                    1 -> "لون"
                                                    2 -> "لونين"
                                                    else -> "$count ألوان"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else SlateTextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick color chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(popularColors) { color ->
                                    val isSelected = selectedColorsSet.contains(color)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) selectedColorsSet.remove(color)
                                            else selectedColorsSet.add(color)
                                        },
                                        label = { Text(color, fontSize = 11.sp) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = customColorsText,
                                onValueChange = { customColorsText = it },
                                label = { Text("أو اكتب تفاصيل الألوان يدوياً (اختياري)") },
                                placeholder = { Text("مثال: 4 أسود، 4 كحلي، 4 بيج") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // STEP 3: Prices & Expected Profit
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(FintechEmeraldBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("3", color = FintechEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "سعر الشراء وسعر البيع والأرباح",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = buyPricePerUnitStr,
                                onValueChange = { buyPricePerUnitStr = it },
                                label = { Text("سعر الشراء للـ ($selectedUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("purchase_buy_price"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = sellPricePerUnitStr,
                                onValueChange = { sellPricePerUnitStr = it },
                                label = { Text("سعر البيع المقترح") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("purchase_sell_price"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Quick adder for buy price
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("إضافة سريعة لسعر الشراء بالريال:", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted))
                            QuickThousandAdder(
                                currentValue = buyPricePerUnitStr,
                                onValueChange = { buyPricePerUnitStr = it }
                            )
                        }

                        // Cost & Profit Summary Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = FintechEmeraldBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FintechEmeraldBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "إجمالي فاتورة الشراء:", fontSize = 12.sp, color = SlateTextSecondary)
                                    Text(
                                        text = FormatUtils.formatCurrency(totalCost),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = FintechRose
                                        )
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "الربح المتوقع عند البيع:", fontSize = 12.sp, color = SlateTextSecondary)
                                    Text(
                                        text = "${FormatUtils.formatCurrency(expectedProfit)} (${profitPercentage.toInt()}%)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = FintechEmerald
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: Supplier & Payment
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(BrandNavyLight.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("4", color = BrandNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "حساب المورد وطريقة الدفع",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                        }

                        // Supplier chips
                        if (supplierParties.isNotEmpty()) {
                            Text(
                                text = "الموردون المسجلون:",
                                style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(supplierParties) { party ->
                                    val isSelected = supplierName.trim().equals(party.name.trim(), ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { supplierName = party.name },
                                        label = { Text(party.name) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text("اسم المورد أو الشركة *") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("purchase_supplier_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { showAddSupplierDialog = true },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = "إضافة مورد جديد", tint = BrandNavy)
                            }
                        }

                        // Supplier debt alert
                        if (supplierName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeDebtAmount > 0.01) FintechRoseBg else FintechEmeraldBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (activeDebtAmount > 0.01) FintechRoseBorder else FintechEmeraldBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (activeDebtAmount > 0.01) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (activeDebtAmount > 0.01) FintechRose else FintechEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (activeDebtAmount > 0.01) "عليا زلط سابقة لهذا المورد: ${FormatUtils.formatCurrency(activeDebtAmount)}" else "حساب هذا المورد خالص (لا توجد عليه مستحقات سابقة)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeDebtAmount > 0.01) FintechRose else FintechEmerald
                                    )
                                }
                            }
                        }

                        // Auto save supplier
                        if (supplierParties.none { it.name.trim().equals(supplierName.trim(), ignoreCase = true) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = autoSaveSupplier,
                                    onCheckedChange = { autoSaveSupplier = it }
                                )
                                Text("حفظ وفتح حساب جديد لهذا المورد في النظام", fontSize = 12.sp, color = SlateTextPrimary)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = invoiceNumber,
                                onValueChange = { invoiceNumber = it },
                                label = { Text("رقم الفاتورة (إن وجد)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("كود الصنف / الباركود") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Payment Method Buttons
                        Text(text = "طريقة دفع قيمة الفاتورة:", style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val methods = listOf(
                                "نقداً من الصندوق" to Icons.Default.AccountBalanceWallet,
                                "تحويل عبر الصراف" to Icons.Default.Transform,
                                "نصف كاش ونصف حوالة" to Icons.Default.CallSplit,
                                "آجل للمورد" to Icons.Default.HistoryEdu
                            )

                            methods.forEach { (method, icon) ->
                                val isSelected = paymentMethod == method
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BrandNavy else SlateBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandNavy else SlateBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            paymentMethod = method
                                            if (method == "نصف كاش ونصف حوالة") {
                                                val half = (totalCost / 2).toLong()
                                                cashPaidStr = half.toString()
                                                transferPaidStr = (totalCost.toLong() - half).toString()
                                            }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else SlateTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = when (method) {
                                                "نقداً من الصندوق" -> "كاش"
                                                "تحويل عبر الصراف" -> "تحويل"
                                                "نصف كاش ونصف حوالة" -> "نص ونص"
                                                else -> "آجل"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else SlateTextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Exchange Account Details
                        if (paymentMethod == "تحويل عبر الصراف" || paymentMethod == "نصف كاش ونصف حوالة") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FintechIndigoBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, FintechIndigoBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "اختر حساب الصراف أو المحفظة المدفوع منها للمورد:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = FintechIndigo)
                                    )

                                    if (exchangeAccounts.isEmpty()) {
                                        Text(
                                            text = "تنبيه: لا يوجد حساب صراف مضاف بعد، سيتم قيد الدفعة برقم الفاتورة.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = FintechRose)
                                        )
                                    } else {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(exchangeAccounts) { acc ->
                                                val isSelected = selectedExchangeAccount?.id == acc.id
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { selectedExchangeAccount = acc },
                                                    label = { Text("${acc.name} (${FormatUtils.formatCurrency(acc.balance)})") },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = FintechIndigo,
                                                        selectedLabelColor = Color.White
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    if (paymentMethod == "نصف كاش ونصف حوالة") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = cashPaidStr,
                                                onValueChange = {
                                                    cashPaidStr = it
                                                    val cash = it.toDoubleOrNull() ?: 0.0
                                                    transferPaidStr = (totalCost - cash).coerceAtLeast(0.0).toLong().toString()
                                                },
                                                label = { Text("المدفوع كاش (ريال)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = transferPaidStr,
                                                onValueChange = {
                                                    transferPaidStr = it
                                                    val tr = it.toDoubleOrNull() ?: 0.0
                                                    cashPaidStr = (totalCost - tr).coerceAtLeast(0.0).toLong().toString()
                                                },
                                                label = { Text("المدفوع تحويل (ريال)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات إضافية") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Main Submit Button
                        Button(
                            onClick = {
                                val cashPaid = when (paymentMethod) {
                                    "نقداً من الصندوق" -> totalCost
                                    "نصف كاش ونصف حوالة" -> cashPaidStr.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                                val transferPaid = when (paymentMethod) {
                                    "تحويل عبر الصراف" -> totalCost
                                    "نصف كاش ونصف حوالة" -> transferPaidStr.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                                val paidTotal = cashPaid + transferPaid
                                viewModel.recordPurchase(
                                    supplierName = supplierName,
                                    category = selectedType,
                                    modelName = if (modelName.isNotBlank()) modelName else "$selectedType $selectedSize",
                                    color = formattedColorString,
                                    size = selectedSize,
                                    unit = selectedUnit,
                                    unitCount = unitCount,
                                    buyPricePerUnit = buyPriceUnit,
                                    sellPricePerUnit = sellPriceUnit,
                                    paidAmount = paidTotal,
                                    invoiceNumber = invoiceNumber,
                                    paymentMethod = paymentMethod,
                                    barcode = barcode,
                                    notes = notes,
                                    colorsCount = colorsCount,
                                    colorDetails = formattedColorString,
                                    cashPaid = cashPaid,
                                    transferPaid = transferPaid,
                                    exchangeAccount = selectedExchangeAccount,
                                    onSuccess = {
                                        if (autoSaveSupplier && supplierName.isNotBlank() && supplierParties.none { it.name.trim().equals(supplierName.trim(), ignoreCase = true) }) {
                                            viewModel.saveParty(
                                                PartyAccount(
                                                    type = "SUPPLIER",
                                                    name = supplierName.trim(),
                                                    notes = "تم فتح الحساب من شاشة الشراء"
                                                ),
                                                initialDebt = 0.0,
                                                debtNotes = ""
                                            ) {}
                                        }
                                        onBack()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_purchase_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حفظ وإضافة البضاعة إلى المخزون",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSupplierDialog) {
        AddOrEditPartyDialog(
            initialType = "SUPPLIER",
            existingParty = null,
            onDismiss = { showAddSupplierDialog = false },
            onSave = { party, initialDebt, debtNotes ->
                viewModel.saveParty(
                    party = party,
                    initialDebt = initialDebt,
                    debtNotes = debtNotes,
                    onSuccess = {
                        supplierName = party.name
                        showAddSupplierDialog = false
                    }
                )
            }
        )
    }
}
