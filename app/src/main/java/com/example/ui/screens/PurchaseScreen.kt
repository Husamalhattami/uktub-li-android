package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CartPurchaseItem
import com.example.data.model.ExchangeAccount
import com.example.data.model.PartyAccount
import com.example.data.model.StoreSettings
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.theme.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper
import java.io.File

data class CompletedPurchaseReceipt(
    val invoiceNumber: String,
    val supplierName: String,
    val paymentMethod: String,
    val items: List<CartPurchaseItem>,
    val totalCost: Double,
    val cashPaid: Double,
    val transferPaid: Double,
    val debtAmount: Double,
    val exchangeAccountName: String,
    val transferRef: String,
    val timestamp: Long,
    val pdfFile: File? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val supplierParties = remember(parties) { parties.filter { it.type == "SUPPLIER" } }
    val allDynamicCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val dynamicSizes by viewModel.productSizes.collectAsStateWithLifecycle()
    val dynamicColors by viewModel.productColors.collectAsStateWithLifecycle()

    // Filter out "الكل" for item category selection
    val selectableCategories = remember(allDynamicCategories) {
        allDynamicCategories.filter { it != "الكل" && it.isNotBlank() }
    }

    // Invoice Header Information
    var supplierName by remember { mutableStateOf("") }
    var invoiceNumberInput by remember { mutableStateOf("") }
    var showAddSupplierDialog by remember { mutableStateOf(false) }

    // Multi-Item Purchase Cart
    val purchaseCartItems = remember { mutableStateListOf<CartPurchaseItem>() }

    // Item Creation Form State
    var selectedCategory by remember { mutableStateOf(selectableCategories.firstOrNull() ?: "") }
    LaunchedEffect(selectableCategories) {
        if (selectedCategory.isBlank() && selectableCategories.isNotEmpty()) {
            selectedCategory = selectableCategories.first()
        } else if (selectableCategories.isEmpty()) {
            selectedCategory = ""
        }
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var showAddSizeDialog by remember { mutableStateOf(false) }
    var newSizeInput by remember { mutableStateOf("") }
    var showAddColorDialog by remember { mutableStateOf(false) }
    var newColorInput by remember { mutableStateOf("") }

    var modelName by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf(dynamicSizes.firstOrNull() ?: ClothingSizes.BIG) }
    LaunchedEffect(dynamicSizes) {
        if (selectedSize !in dynamicSizes && dynamicSizes.isNotEmpty()) {
            selectedSize = dynamicSizes.first()
        }
    }
    var selectedUnit by remember { mutableStateOf(ClothingUnits.DOZEN) }
    var unitCountStr by remember { mutableStateOf("1") }
    var buyPricePerUnitStr by remember { mutableStateOf("15000") }
    var sellPricePerUnitStr by remember { mutableStateOf("22000") }
    var itemBarcode by remember { mutableStateOf("") }
    var itemNotes by remember { mutableStateOf("") }

    // Color Assortment for item
    val selectedColorsSet = remember { mutableStateListOf<String>() }
    LaunchedEffect(dynamicColors) {
        if (selectedColorsSet.isEmpty() && dynamicColors.isNotEmpty()) {
            selectedColorsSet.addAll(dynamicColors.take(3))
        }
    }
    var customColorsText by remember { mutableStateOf("") }
    var colorsCount by remember { mutableIntStateOf(3) }

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

    // Payment Section State
    var paymentMethod by remember { mutableStateOf("نقداً من الصندوق") }
    var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }
    var transferRef by remember { mutableStateOf("") }
    var cashPaidPartStr by remember { mutableStateOf("") }
    var transferPaidPartStr by remember { mutableStateOf("") }
    var debtPartStr by remember { mutableStateOf("") }
    var invoiceNotes by remember { mutableStateOf("") }

    LaunchedEffect(exchangeAccounts) {
        if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
            selectedExchangeAccount = exchangeAccounts.firstOrNull()
        }
    }

    val totalCartCost = remember(purchaseCartItems.toList()) {
        purchaseCartItems.sumOf { it.lineCost }
    }

    // Auto-update split values when totalCartCost or paymentMethod changes
    LaunchedEffect(totalCartCost, paymentMethod) {
        if (paymentMethod == "نصف كاش ونصف حوالة") {
            val half = (totalCartCost / 2).toLong()
            cashPaidPartStr = half.toString()
            transferPaidPartStr = (totalCartCost.toLong() - half).toString()
            debtPartStr = "0"
        } else if (paymentMethod == "مختلط (كاش + حوالة + آجل)" && cashPaidPartStr.isBlank() && transferPaidPartStr.isBlank() && debtPartStr.isBlank()) {
            val third = (totalCartCost / 3).toLong()
            cashPaidPartStr = third.toString()
            transferPaidPartStr = third.toString()
            debtPartStr = (totalCartCost.toLong() - (third * 2)).toString()
        }
    }

    // Completed Invoice Success Dialog State
    var completedReceipt by remember { mutableStateOf<CompletedPurchaseReceipt?>(null) }

    fun addItemToCart() {
        if (selectedCategory.isBlank()) {
            viewModel.showMessage("يرجى إضافة أو اختيار نوع الصنف أولاً")
            return
        }
        val count = unitCountStr.toDoubleOrNull() ?: 1.0
        val buyPrice = buyPricePerUnitStr.toDoubleOrNull() ?: 0.0
        val sellPrice = sellPricePerUnitStr.toDoubleOrNull() ?: 0.0

        if (count <= 0) {
            viewModel.showMessage("يرجى إدخال كمية صحيحة أكبر من الصفر")
            return
        }
        if (buyPrice <= 0) {
            viewModel.showMessage("يرجى إدخال سعر شراء صحيح")
            return
        }
        if (sellPrice <= 0) {
            viewModel.showMessage("يرجى إدخال سعر بيع مقترح صحيح")
            return
        }

        val finalModel = if (modelName.isNotBlank()) modelName.trim() else "$selectedCategory $selectedSize"
        val newItem = CartPurchaseItem(
            category = selectedCategory,
            modelName = finalModel,
            color = formattedColorString,
            size = selectedSize,
            unit = selectedUnit,
            unitCount = count,
            buyPricePerUnit = buyPrice,
            sellPricePerUnit = sellPrice,
            barcode = itemBarcode.trim(),
            colorsCount = colorsCount,
            colorDetails = formattedColorString,
            notes = itemNotes.trim()
        )
        purchaseCartItems.add(newItem)
        viewModel.showMessage("تمت إضافة (${newItem.modelName}) إلى الفاتورة")

        // Reset item fields for next entry
        modelName = ""
        unitCountStr = "1"
        itemBarcode = ""
        itemNotes = ""
    }

    fun submitPurchaseInvoice(andPrint: Boolean = false, andShare: Boolean = false) {
        if (purchaseCartItems.isEmpty()) {
            viewModel.showMessage("فاتورة الشراء فارغة! أضف صنفاً واحداً على الأقل قبل التأكيد.")
            return
        }

        val actualCash: Double
        val actualTransfer: Double
        val actualDebt: Double

        when (paymentMethod) {
            "نقداً من الصندوق", "نقداً" -> {
                actualCash = totalCartCost
                actualTransfer = 0.0
                actualDebt = 0.0
            }
            "تحويل عبر الصرافة", "تحويل عبر الصراف", "تحويل صرافة" -> {
                if (selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة المدفوع منها!")
                    return
                }
                actualCash = 0.0
                actualTransfer = totalCartCost
                actualDebt = 0.0
            }
            "نصف كاش ونصف حوالة" -> {
                if (selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة للدفعة المحولة!")
                    return
                }
                val c = cashPaidPartStr.toDoubleOrNull() ?: (totalCartCost / 2.0)
                val t = transferPaidPartStr.toDoubleOrNull() ?: (totalCartCost - c)
                actualCash = c
                actualTransfer = t
                actualDebt = 0.0
            }
            "آجل للمورد", "آجل (دين)" -> {
                if (supplierName.trim().isBlank()) {
                    viewModel.showMessage("يرجى إدخال اسم المورد لتقييد المبلغ الآجل في ذمة المحل!")
                    return
                }
                actualCash = 0.0
                actualTransfer = 0.0
                actualDebt = totalCartCost
            }
            "مختلط (كاش + حوالة + آجل)" -> {
                val c = cashPaidPartStr.toDoubleOrNull() ?: 0.0
                val t = transferPaidPartStr.toDoubleOrNull() ?: 0.0
                val d = debtPartStr.toDoubleOrNull() ?: 0.0
                val sumParts = c + t + d
                if (kotlin.math.abs(sumParts - totalCartCost) > 1.0) {
                    viewModel.showMessage("مجموع المبالغ (${FormatUtils.formatCurrency(sumParts)}) لا يعادل إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCartCost)})!")
                    return
                }
                if (t > 0 && selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة للمبلغ المحول ($t ريال)!")
                    return
                }
                if (d > 0 && supplierName.trim().isBlank()) {
                    viewModel.showMessage("يرجى إدخال اسم المورد لتسجيل المبلغ الآجل ($d ريال)!")
                    return
                }
                actualCash = c
                actualTransfer = t
                actualDebt = d
            }
            else -> {
                actualCash = totalCartCost
                actualTransfer = 0.0
                actualDebt = 0.0
            }
        }

        val itemsSnapshot = purchaseCartItems.toList()
        val finalSupplier = supplierName.trim().ifBlank { "مورد عام" }

        viewModel.executeCartPurchase(
            items = itemsSnapshot,
            supplierName = finalSupplier,
            invoiceNumber = invoiceNumberInput.trim(),
            paymentMethod = paymentMethod,
            cashPaidPart = actualCash,
            transferPaidPart = actualTransfer,
            debtPart = actualDebt,
            exchangeAccount = selectedExchangeAccount,
            transferRef = transferRef.trim(),
            notes = invoiceNotes.trim()
        ) { generatedInvoiceNumber ->
            val now = System.currentTimeMillis()
            val pdf = try {
                InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
                    context = context,
                    invoiceNumber = generatedInvoiceNumber,
                    supplierName = finalSupplier,
                    paymentMethod = paymentMethod,
                    exchangeAccountName = selectedExchangeAccount?.name ?: "",
                    transferRef = transferRef.trim(),
                    items = itemsSnapshot,
                    totalCost = totalCartCost,
                    cashPaid = actualCash,
                    transferPaid = actualTransfer,
                    debtAmount = actualDebt,
                    timestamp = now,
                    settings = storeSettings
                )
            } catch (e: Exception) {
                null
            }

            if (andPrint && pdf != null) {
                InvoiceHelper.printPdfFile(context, pdf, "فاتورة_شراء_${generatedInvoiceNumber}")
            } else if (andShare && pdf != null) {
                InvoiceHelper.sharePdfFile(context, pdf, "مشاركة فاتورة شراء #${generatedInvoiceNumber}")
            }

            completedReceipt = CompletedPurchaseReceipt(
                invoiceNumber = generatedInvoiceNumber,
                supplierName = finalSupplier,
                paymentMethod = paymentMethod,
                items = itemsSnapshot,
                totalCost = totalCartCost,
                cashPaid = actualCash,
                transferPaid = actualTransfer,
                debtAmount = actualDebt,
                exchangeAccountName = selectedExchangeAccount?.name ?: "",
                transferRef = transferRef.trim(),
                timestamp = now,
                pdfFile = pdf
            )

            // Clear Cart after successful recording
            purchaseCartItems.clear()
            invoiceNumberInput = ""
            transferRef = ""
            invoiceNotes = ""
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
                            text = "فاتورة مشتريات بضاعة للمستودع",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = storeSettings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF90CDF4),
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                actions = {
                    if (purchaseCartItems.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { purchaseCartItems.clear() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تفريغ", fontSize = 12.sp)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 920.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // ==========================================
            // 1. INVOICE META CARD: Supplier & Invoice Number
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = PdfHeaderNavy)
                            Text("بيانات الفاتورة والمورد", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PdfHeaderNavy)
                        }

                        // Supplier input & quick chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text("اسم المورد / المعمل / الشركة") },
                                placeholder = { Text("مثال: معمل الأصالة، وكالة الأقمشة") },
                                modifier = Modifier.weight(1f).testTag("supplier_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = PdfGoldDark) }
                            )

                            IconButton(
                                onClick = { showAddSupplierDialog = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEFF6FF))
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة مورد", tint = Color(0xFF2563EB))
                            }
                        }

                        if (supplierParties.isNotEmpty()) {
                            Text("الموردون المسجلون مسبقاً:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(supplierParties) { party ->
                                    val isSelected = supplierName.trim().equals(party.name.trim(), ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { supplierName = party.name },
                                        label = { Text(party.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Physical Supplier Invoice Number (User Request #3)
                        OutlinedTextField(
                            value = invoiceNumberInput,
                            onValueChange = { invoiceNumberInput = it },
                            label = { Text("رقم فاتورة الشراء / المورد (مطلوب من الفاتورة الورقية)") },
                            placeholder = { Text("مثال: 782 أو فاتورة رقم 104") },
                            modifier = Modifier.fillMaxWidth().testTag("purchase_invoice_number_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, tint = PdfGoldDark) }
                        )
                    }
                }
            }

            // ==========================================
            // 2. ITEM CREATION FORM: Add items to invoice
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = PdfGoldDark)
                                Text("إضافة صنف إلى الفاتورة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "الصنف رقم ${purchaseCartItems.size + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Category Quick Selector + Add Category Chip (User Request #1)
                        Text("نوع الصنف (قائمة الاختيارات السريعة):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Add New Category Chip
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable { showAddCategoryDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نوع جديد +", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            items(selectableCategories) { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        if (selectableCategories.isEmpty()) {
                            Text(
                                "لا توجد أنواع مسجلة حالياً — اضغط «نوع جديد +» لإضافة أول نوع صنف",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }

                        // Model / Title Name
                        val modelLabelHint = if (selectedCategory.isNotBlank()) "$selectedCategory $selectedSize" else "اسم الموديل"
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("اسم الموديل / الوصف (اختياري، الافتراضي: $modelLabelHint)") },
                            placeholder = { Text("مثال: ${selectedCategory.ifBlank { "موديل جديد" }} ملكي مطرز...") },
                            modifier = Modifier.fillMaxWidth().testTag("purchase_model_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Size Selection
                        Text("المقاس:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable { showAddSizeDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مقاس +", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                            items(dynamicSizes) { sz ->
                                val isSel = selectedSize == sz
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedSize = sz },
                                    label = { Text(sz, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        // Unit & Quantity Configuration
                        Text("الوحدة والكمية المشتراة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(ClothingUnits.DOZEN, ClothingUnits.HALF_DOZEN, ClothingUnits.PIECE, ClothingUnits.KORJAH).forEach { un ->
                                val isSel = selectedUnit == un
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedUnit = un },
                                    label = { Text(un, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
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
                                label = { Text("العدد ($selectedUnit)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("purchase_unit_count_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            val uCount = unitCountStr.toDoubleOrNull() ?: 1.0
                            val multiplier = ClothingUnits.getMultiplier(selectedUnit)
                            val totalPieces = (uCount * multiplier).toInt().coerceAtLeast(1)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEFF6FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("الإجمالي بالقطع", fontSize = 10.sp, color = Color(0xFF1E3A8A))
                                    Text("$totalPieces حبة", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                }
                            }
                        }

                        // Pricing Inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = buyPricePerUnitStr,
                                    onValueChange = { buyPricePerUnitStr = it },
                                    label = { Text("سعر الشراء لـ $selectedUnit (ريال)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("purchase_buy_price_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                QuickThousandAdder(
                                    currentValue = buyPricePerUnitStr,
                                    onValueChange = { buyPricePerUnitStr = it }
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = sellPricePerUnitStr,
                                    onValueChange = { sellPricePerUnitStr = it },
                                    label = { Text("سعر البيع لـ $selectedUnit (ريال)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("purchase_sell_price_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                QuickThousandAdder(
                                    currentValue = sellPricePerUnitStr,
                                    onValueChange = { sellPricePerUnitStr = it }
                                )
                            }
                        }

                        // Colors Assortment
                        Text("الألوان والتشكيلة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable { showAddColorDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("لون +", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                            items(dynamicColors) { col ->
                                val isSelected = selectedColorsSet.contains(col)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedColorsSet.remove(col) else selectedColorsSet.add(col)
                                    },
                                    label = { Text(col, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customColorsText,
                            onValueChange = { customColorsText = it },
                            label = { Text("تفصيل الألوان كتابة (اختياري)") },
                            placeholder = { Text("مثال: 6 أسود، 4 كحلي، 2 بيج") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Add to Cart Button
                        Button(
                            onClick = { addItemToCart() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_item_to_purchase_cart_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إضافة الصنف إلى فاتورة الشراء +", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }

            // ==========================================
            // 3. PURCHASE CART TABLE: Itemized list (User Request #2)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ListAlt, contentDescription = null, tint = PdfHeaderNavy)
                                Text("أصناف فاتورة الشراء", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PdfHeaderNavy)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (purchaseCartItems.isNotEmpty()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "${purchaseCartItems.size} أصناف مضافة",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (purchaseCartItems.isNotEmpty()) Color(0xFF16A34A) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        if (purchaseCartItems.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(44.dp))
                                Text("لم تتم إضافة أي أصناف للفاتورة بعد", color = Color(0xFF64748B), fontSize = 13.sp)
                                Text("املأ بيانات الصنف أعلاه واضغط (إضافة الصنف إلى فاتورة الشراء)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        } else {
                            purchaseCartItems.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PdfHeaderNavy,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(item.modelName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFE0F2FE)
                                                ) {
                                                    Text(item.category, fontSize = 10.sp, color = Color(0xFF0369A1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }

                                            Text(
                                                text = "المقاس: ${item.size}  •  ${item.color}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )

                                            val unitDesc = if (item.unit == "حبه") "${item.totalPieces} حبة" else "${item.unitCount.toInt()} ${item.unit} (${item.totalPieces} حبة)"
                                            Text(
                                                text = "الكمية: $unitDesc  •  سعر الوحدة: ${FormatUtils.formatCurrency(item.buyPricePerUnit)}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF334155)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = FormatUtils.formatCurrency(item.lineCost),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A)
                                            )

                                            IconButton(
                                                onClick = { purchaseCartItems.removeAt(index) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الصنف", tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. PAYMENT & SETTLEMENT CARD (User Request #4)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = PdfHeaderNavy)
                            Text("طريقة الدفع وسداد المورد", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PdfHeaderNavy)
                        }

                        // Total Invoice Cost Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
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
                                    Text("إجمالي فاتورة الشراء:", fontSize = 12.sp, color = Color(0xFF64748B))
                                    Text("${purchaseCartItems.size} أصناف في الفاتورة", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(totalCartCost),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        fontSize = 22.sp
                                    )
                                )
                            }
                        }

                        // Payment Methods
                        Text("اختر طريقة سداد الفاتورة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        val paymentOptions = listOf(
                            "نقداً من الصندوق",
                            "تحويل عبر الصرافة",
                            "نصف كاش ونصف حوالة",
                            "آجل للمورد",
                            "مختلط (كاش + حوالة + آجل)"
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(paymentOptions) { opt ->
                                val isSel = paymentMethod == opt
                                FilterChip(
                                    selected = isSel,
                                    onClick = { paymentMethod = opt },
                                    label = { Text(opt, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        // Exchange Account Selector & Transfer Reference (User Request #4)
                        val needsExchange = paymentMethod == "تحويل عبر الصرافة" ||
                                paymentMethod == "نصف كاش ونصف حوالة" ||
                                (paymentMethod == "مختلط (كاش + حوالة + آجل)" && (transferPaidPartStr.toDoubleOrNull() ?: 0.0) > 0.0)

                        if (needsExchange) {
                            Text("اختر حساب الصراف أو المحفظة المدفوع منها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(exchangeAccounts) { acc ->
                                    val isAcc = selectedExchangeAccount?.id == acc.id
                                    FilterChip(
                                        selected = isAcc,
                                        onClick = { selectedExchangeAccount = acc },
                                        label = { Text("${acc.name} (${FormatUtils.formatNumber(acc.balance)})", fontSize = 11.sp) }
                                    )
                                }
                            }

                            // Transfer reference number (User Request #4)
                            OutlinedTextField(
                                value = transferRef,
                                onValueChange = { transferRef = it },
                                label = { Text("رقم الحوالة / إشعار الصراف (سند التحويل) *") },
                                placeholder = { Text("أدخل رقم الحوالة هنا لتوثيقها") },
                                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth().testTag("purchase_transfer_ref_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Split payment fields
                        if (paymentMethod == "نصف كاش ونصف حوالة" || paymentMethod == "مختلط (كاش + حوالة + آجل)") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("توزيع مبالغ الدفع على الفاتورة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = cashPaidPartStr,
                                            onValueChange = { cashPaidPartStr = it },
                                            label = { Text("كاش (صندوق)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        OutlinedTextField(
                                            value = transferPaidPartStr,
                                            onValueChange = { transferPaidPartStr = it },
                                            label = { Text("تحويل صرافة", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        if (paymentMethod == "مختلط (كاش + حوالة + آجل)") {
                                            OutlinedTextField(
                                                value = debtPartStr,
                                                onValueChange = { debtPartStr = it },
                                                label = { Text("آجل (دين)", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    val c = cashPaidPartStr.toDoubleOrNull() ?: 0.0
                                    val t = transferPaidPartStr.toDoubleOrNull() ?: 0.0
                                    val d = if (paymentMethod == "مختلط (كاش + حوالة + آجل)") debtPartStr.toDoubleOrNull() ?: 0.0 else 0.0
                                    val sum = c + t + d
                                    val isMatch = kotlin.math.abs(sum - totalCartCost) <= 1.0

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("المجموع: ${FormatUtils.formatCurrency(sum)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isMatch) Color(0xFF16A34A) else Color(0xFFDC2626))
                                        Text(if (isMatch) "مطابق ✓" else "الفارق: ${FormatUtils.formatCurrency(totalCartCost - sum)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isMatch) Color(0xFF16A34A) else Color(0xFFDC2626))
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = invoiceNotes,
                            onValueChange = { invoiceNotes = it },
                            label = { Text("ملاحظات إضافية على فاتورة الشراء (اختياري)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 2 Action Buttons: Save & Print / Save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { submitPurchaseInvoice(andPrint = true) },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("purchase_save_print_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ وطباعة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = { submitPurchaseInvoice(andShare = true) },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("purchase_save_share_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ ومشاركة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

    // ==========================================
    // DIALOGS: Add Category, Add Supplier, Success Receipt
    // ==========================================

    // 1. Add Category Dialog (User Request #1)
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Category, contentDescription = null, tint = PdfGoldDark)
                    Text("إضافة نوع صنف جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("اكتب اسم نوع الصنف وسيظهر فوراً في قائمة الاختيارات السريعة للشراء والبيع والمخزون:", fontSize = 12.sp, color = Color(0xFF64748B))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text("اسم نوع الصنف (مثلاً: أرواب، سراويل، شيلان...)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_purchase_category_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.addNewCategory(trimmed)
                            selectedCategory = trimmed
                            newCategoryInput = ""
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark),
                    modifier = Modifier.testTag("confirm_add_purchase_category_btn")
                ) {
                    Text("إضافة واختيار")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCategoryDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showAddSizeDialog) {
        AlertDialog(
            onDismissRequest = { showAddSizeDialog = false },
            title = { Text("إضافة مقاس جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newSizeInput,
                        onValueChange = { newSizeInput = it },
                        label = { Text("اسم المقاس (مثلاً: شبابي، جامبو، 42...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newSizeInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.addConfigItem(com.example.data.model.StoreConfigTypes.PRODUCT_SIZE, trimmed)
                            selectedSize = trimmed
                            newSizeInput = ""
                            showAddSizeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                ) { Text("إضافة واختيار") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddSizeDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showAddColorDialog) {
        AlertDialog(
            onDismissRequest = { showAddColorDialog = false },
            title = { Text("إضافة لون جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newColorInput,
                        onValueChange = { newColorInput = it },
                        label = { Text("اسم اللون (مثلاً: عودي، خمري، فضي...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newColorInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.addConfigItem(com.example.data.model.StoreConfigTypes.PRODUCT_COLOR, trimmed)
                            if (!selectedColorsSet.contains(trimmed)) {
                                selectedColorsSet.add(trimmed)
                            }
                            newColorInput = ""
                            showAddColorDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                ) { Text("إضافة واختيار") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddColorDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // 2. Add Supplier Dialog
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

    // 3. Purchase Success Receipt Dialog
    completedReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { completedReceipt = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(36.dp))
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("تم حفظ فاتورة الشراء بنجاح!", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                    Text("رقم الفاتورة: #${receipt.invoiceNumber}", fontSize = 13.sp, color = Color(0xFF64748B))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المورد:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(receipt.supplierName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("عدد الأصناف:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("${receipt.items.size} أصناف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الفاتورة:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(FormatUtils.formatCurrency(receipt.totalCost), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                            }
                            if (receipt.transferRef.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("رقم الحوالة:", fontSize = 12.sp, color = Color(0xFF0F766E))
                                    Text("#${receipt.transferRef}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                }
                            }
                            if (receipt.debtAmount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الآجل للمورد:", fontSize = 12.sp, color = Color(0xFFDC2626))
                                    Text(FormatUtils.formatCurrency(receipt.debtAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = receipt.pdfFile ?: InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
                                    context = context,
                                    invoiceNumber = receipt.invoiceNumber,
                                    supplierName = receipt.supplierName,
                                    paymentMethod = receipt.paymentMethod,
                                    exchangeAccountName = receipt.exchangeAccountName,
                                    transferRef = receipt.transferRef,
                                    items = receipt.items,
                                    totalCost = receipt.totalCost,
                                    cashPaid = receipt.cashPaid,
                                    transferPaid = receipt.transferPaid,
                                    debtAmount = receipt.debtAmount,
                                    timestamp = receipt.timestamp,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة فاتورة شراء #${receipt.invoiceNumber}")
                            },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("btn_purchase_receipt_share_pdf"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val txt = StatementHelper.formatMultiItemPurchaseText(
                                    invoiceNumber = receipt.invoiceNumber,
                                    supplierName = receipt.supplierName,
                                    paymentMethod = receipt.paymentMethod,
                                    itemsCount = receipt.items.size,
                                    totalCost = receipt.totalCost,
                                    cashPaid = receipt.cashPaid,
                                    transferPaid = receipt.transferPaid,
                                    debtAmount = receipt.debtAmount,
                                    exchangeAccountName = receipt.exchangeAccountName,
                                    transferRef = receipt.transferRef,
                                    timestamp = receipt.timestamp,
                                    settings = storeSettings
                                )
                                StatementHelper.shareText(context, txt, "مشاركة فاتورة شراء #${receipt.invoiceNumber}")
                            },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("btn_purchase_receipt_share_whatsapp"),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("واتساب / نص", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = receipt.pdfFile ?: InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
                            context = context,
                            invoiceNumber = receipt.invoiceNumber,
                            supplierName = receipt.supplierName,
                            paymentMethod = receipt.paymentMethod,
                            exchangeAccountName = receipt.exchangeAccountName,
                            transferRef = receipt.transferRef,
                            items = receipt.items,
                            totalCost = receipt.totalCost,
                            cashPaid = receipt.cashPaid,
                            transferPaid = receipt.transferPaid,
                            debtAmount = receipt.debtAmount,
                            timestamp = receipt.timestamp,
                            settings = storeSettings
                        )
                        InvoiceHelper.printPdfFile(context, file, "فاتورة_شراء_${receipt.invoiceNumber}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة PDF")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { completedReceipt = null }) {
                    Text("فاتورة جديدة")
                }
            }
        )
        }
    }
}
