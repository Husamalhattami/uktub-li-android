package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val clothingTypes = listOf("بشت", "كوت", "دجلة", "جاكت", "ثوب", "قميص", "بنطلون", "دشداشة", "بدلة", "شال/غترة")
    val standardSizes = listOf(ClothingSizes.BIG, ClothingSizes.MEDIUM, ClothingSizes.SMALL, ClothingSizes.YOUTH)
    val standardUnits = listOf(ClothingUnits.PIECE, ClothingUnits.HALF_DOZEN, ClothingUnits.DOZEN, ClothingUnits.KORJAH)

    val popularColors = listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "رصاصي", "بني", "زيتي", "سكري", "أزرق")

    var selectedType by remember { mutableStateOf(clothingTypes.first()) }
    var modelName by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(ClothingSizes.BIG) }
    var selectedUnit by remember { mutableStateOf(ClothingUnits.DOZEN) } // Default dozen
    var unitCountStr by remember { mutableStateOf("1") } // 1 dozen, 1 korjah, etc.

    // Color assortment
    var colorsCount by remember { mutableIntStateOf(3) } // 3 ألوان
    val selectedColorsSet = remember { mutableStateListOf("أسود", "كحلي", "بيج") }
    var customColorsText by remember { mutableStateOf("") }

    var buyPricePerUnitStr by remember { mutableStateOf("180") } // ريال
    var sellPricePerUnitStr by remember { mutableStateOf("280") } // ريال
    var supplierName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقداً من الصندوق") }
    var selectedExchangeAccount by remember { mutableStateOf<com.example.data.model.ExchangeAccount?>(null) }
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

    // Build the formatted colors string
    val formattedColorString = remember(colorsCount, selectedColorsSet.toList(), customColorsText) {
        val colorNames = if (customColorsText.isNotBlank()) {
            customColorsText
        } else {
            selectedColorsSet.joinToString("، ")
        }

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

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "تسجيل بضاعة جديدة (شراء)",
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
                            text = "بيانات البضاعة والمقاس والكمية",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        // 1. Clothing Type Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "نوع البضاعة:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(clothingTypes) { type ->
                                    FilterChip(
                                        selected = selectedType == type,
                                        onClick = { selectedType = type },
                                        label = { Text(type) },
                                        modifier = Modifier.testTag("clothing_type_$type")
                                    )
                                }
                            }
                        }

                        // 2. Model / Description
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("الموديل أو الوصف (مثال: بشت ملكي صيفي، ثوب كويتي...)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purchase_model_name"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 3. Size: كبير، وسط، صغير، شبابي
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "المقاس المطلوب (كبير - وسط - صغير - شبابي):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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
                                        label = { Text(size, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("size_chip_$size"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldContainer,
                                            selectedLabelColor = LuxuryGold
                                        )
                                    )
                                }
                            }
                        }

                        // 4. Quantity Units: بالحبه، بنص درزن، درزن، كورجه
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "وحدة الكمية (حبه - نص درزن - درزن - كورجه):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
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
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedUnit = unit },
                                        label = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(unit, fontWeight = FontWeight.Bold)
                                                Text(multiplierLabel, fontSize = 9.sp, color = SlateMuted)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("unit_chip_$unit")
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
                                    label = { Text("العدد بـ ($selectedUnit)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("purchase_qty_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("إجمالي الحبات بالمخزون", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text(
                                            text = "$totalPieces حبه",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = LuxuryGoldLight,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Colors of Dozen / Batch (ألوان الدرزن والبضاعة)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ألوان البضاعة والدرزن (عدد الألوان والتشكيلة):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Quick selector: لون، لونين، 3 ألوان، 4 ألوان...
                            Text(text = "كم عدد الألوان في هذه الدفعة؟", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                            val countsList = listOf(1, 2, 3, 4, 5, 6)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(countsList) { count ->
                                    val countText = when (count) {
                                        1 -> "لون واحد"
                                        2 -> "لونين"
                                        3 -> "3 ألوان"
                                        4 -> "4 ألوان"
                                        5 -> "5 ألوان"
                                        else -> "مشكل ألوان"
                                    }
                                    FilterChip(
                                        selected = colorsCount == count,
                                        onClick = { colorsCount = count },
                                        label = { Text(countText, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.testTag("color_count_$count")
                                    )
                                }
                            }

                            // Choose the colors from popular list
                            Text(text = "حدد الألوان من القائمة السريعة:", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(popularColors) { color ->
                                    val isColorSelected = selectedColorsSet.contains(color)
                                    FilterChip(
                                        selected = isColorSelected,
                                        onClick = {
                                            if (isColorSelected) {
                                                selectedColorsSet.remove(color)
                                            } else {
                                                selectedColorsSet.add(color)
                                            }
                                        },
                                        label = { Text(color, fontSize = 11.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = customColorsText,
                                onValueChange = { customColorsText = it },
                                placeholder = { Text("أو اكتب أسماء الألوان بالتفصيل هنا...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            // Live summary of colors entered
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldContainer
                            ) {
                                Text(
                                    text = "المسجل للبضاعة: $formattedColorString",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = LuxuryGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // 6. Buy Price per Unit (in Riyals)
                        Column {
                            OutlinedTextField(
                                value = buyPricePerUnitStr,
                                onValueChange = { buyPricePerUnitStr = it },
                                label = { Text("سعر الشراء لـ ($selectedUnit) بالريال") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("purchase_buy_price"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            QuickThousandAdder(
                                currentValue = buyPricePerUnitStr,
                                onValueChange = { buyPricePerUnitStr = it }
                            )
                        }

                        // 7. Sell Price per Unit (in Riyals)
                        Column {
                            OutlinedTextField(
                                value = sellPricePerUnitStr,
                                onValueChange = { sellPricePerUnitStr = it },
                                label = { Text("سعر البيع المقترح لـ ($selectedUnit) بالريال") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("purchase_sell_price"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            QuickThousandAdder(
                                currentValue = sellPricePerUnitStr,
                                onValueChange = { sellPricePerUnitStr = it }
                            )
                        }

                        // 8. Auto Calculation Summary in Riyal
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("إجمالي تكلفة الشراء بالريال:", color = Color.White)
                                    Text(
                                        text = FormatUtils.formatCurrency(totalCost),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("قيمة البيع المتوقعة:", color = Color.White.copy(alpha = 0.85f))
                                    Text(
                                        text = FormatUtils.formatCurrency(expectedRevenue),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LuxuryGoldLight)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("الربح المتوقع الإجمالي:", color = Color.White.copy(alpha = 0.85f))
                                    Text(
                                        text = "+ " + FormatUtils.formatCurrency(expectedProfit) + " (${profitPercentage.toInt()}%)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = SlateBorder)

                        // 9. Supplier & Payment
                        Text(
                            text = "بيانات المورد وطريقة الدفع",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = supplierName,
                            onValueChange = { supplierName = it },
                            label = { Text("اسم المورد أو المعمل") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purchase_supplier_name"),
                            shape = RoundedCornerShape(12.dp)
                        )

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

                        // Default account selector
                        LaunchedEffect(exchangeAccounts) {
                            if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
                                selectedExchangeAccount = exchangeAccounts.firstOrNull()
                            }
                        }

                        // Auto calculate split when totalCost changes
                        LaunchedEffect(totalCost, paymentMethod) {
                            if (paymentMethod == "نصف كاش ونصف حوالة") {
                                val half = (totalCost / 2).toLong()
                                cashPaidStr = half.toString()
                                transferPaidStr = (totalCost.toLong() - half).toString()
                            }
                        }

                        // Payment Method Toggle (نقداً، تحويل، نصف كاش ونصف حوالة، آجل)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "طريقة دفع قيمة البضاعة للمورد:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isCash = paymentMethod == "نقداً من الصندوق"
                                FilterChip(
                                    selected = isCash,
                                    onClick = { paymentMethod = "نقداً من الصندوق" },
                                    label = { Text("كاش من الصندوق", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                val isTransfer = paymentMethod == "تحويل عبر الصراف"
                                FilterChip(
                                    selected = isTransfer,
                                    onClick = { paymentMethod = "تحويل عبر الصراف" },
                                    label = { Text("تحويل صرافة", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                val isSplit = paymentMethod == "نصف كاش ونصف حوالة"
                                FilterChip(
                                    selected = isSplit,
                                    onClick = {
                                        paymentMethod = "نصف كاش ونصف حوالة"
                                        val half = (totalCost / 2).toLong()
                                        cashPaidStr = half.toString()
                                        transferPaidStr = (totalCost.toLong() - half).toString()
                                    },
                                    label = { Text("نصف كاش ونصف حوالة", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1.3f)
                                )
                                val isCredit = paymentMethod == "آجل للمورد"
                                FilterChip(
                                    selected = isCredit,
                                    onClick = { paymentMethod = "آجل للمورد" },
                                    label = { Text("آجل (دين)", fontSize = 11.sp) },
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            // Choose exchange account for transfer or split
                            if (paymentMethod == "تحويل عبر الصراف" || paymentMethod == "نصف كاش ونصف حوالة") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "اختر حساب الصراف أو المحفظة المدفوع منها للمورد:",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )

                                        if (exchangeAccounts.isEmpty()) {
                                            Text(
                                                text = "تنبيه: لا يوجد حساب صراف مضاف بعد، سيتم قيد الدفعة برقم الفاتورة.",
                                                style = MaterialTheme.typography.bodySmall.copy(color = RubyExpense)
                                            )
                                        } else {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(exchangeAccounts) { acc ->
                                                    FilterChip(
                                                        selected = selectedExchangeAccount?.id == acc.id,
                                                        onClick = { selectedExchangeAccount = acc },
                                                        label = { Text("${acc.name} (${acc.balance.toLong()} ريال)") }
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
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات إضافية") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Submit Button
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
                                    onSuccess = onBack
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_purchase_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حفظ البضاعة وإضافتها إلى المخزون",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
