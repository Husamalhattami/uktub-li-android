package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CartSaleItem
import com.example.data.model.Employee
import com.example.data.model.ExchangeAccount
import com.example.data.model.PaymentMethods
import com.example.data.model.ProductItem
import com.example.ui.StoreViewModel
import com.example.ui.theme.*
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper

data class CartItem(
    val product: ProductItem,
    var quantity: Int,
    var unitPrice: Double,
    var unit: String = ClothingUnits.PIECE
) {
    val multiplier: Int get() = ClothingUnits.getMultiplier(unit)
    val totalPieces: Int get() = quantity * multiplier
    val lineTotal: Double get() = quantity * unitPrice
}

data class CompletedInvoiceReceipt(
    val invoiceNumber: String,
    val customerName: String,
    val customerPhone: String,
    val employeeName: String,
    val paymentType: String,
    val items: List<CartItem>,
    val subtotal: Double,
    val discount: Double,
    val netTotal: Double,
    val cashPaid: Double,
    val transferPaid: Double,
    val debtAmount: Double,
    val exchangeAccountName: String,
    val transferRef: String,
    val timestamp: Long,
    val pdfFile: java.io.File? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val customerParties = remember(parties) { parties.filter { it.type == "CUSTOMER" } }
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }

    // Multi-Item Invoice Cart
    val cartItems = remember { mutableStateListOf<CartItem>() }
    var discountValue by remember { mutableStateOf(0.0) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var discountInputStr by remember { mutableStateOf("0") }

    // Unit & Quantity Configuration Modal for product
    var productToConfigure by remember { mutableStateOf<ProductItem?>(null) }

    // Success Receipt Dialog
    var completedInvoiceReceipt by remember { mutableStateOf<CompletedInvoiceReceipt?>(null) }
    var editingCartItemPrice by remember { mutableStateOf<CartItem?>(null) }

    // Employee Selection
    var selectedEmployee by remember { mutableStateOf<Employee?>(null) }

    // Payment details
    var paymentType by remember { mutableStateOf(PaymentMethods.CASH) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }
    var transferRef by remember { mutableStateOf("") }

    // Custom Split (Cash + Transfer + Debt)
    var cashPartInput by remember { mutableStateOf("") }
    var transferPartInput by remember { mutableStateOf("") }
    var debtPartInput by remember { mutableStateOf("") }

    LaunchedEffect(exchangeAccounts) {
        if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
            selectedExchangeAccount = exchangeAccounts.firstOrNull()
        }
    }

    LaunchedEffect(employees) {
        if (selectedEmployee == null && employees.isNotEmpty()) {
            selectedEmployee = employees.firstOrNull()
        }
    }

    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var customInvoiceNumber by remember { mutableStateOf("") }

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

    val subtotal = cartItems.sumOf { it.lineTotal }
    val totalAmount = (subtotal - discountValue).coerceAtLeast(0.0)

    // Sync custom split defaults if split chosen
    LaunchedEffect(paymentType, totalAmount) {
        if (paymentType == PaymentMethods.CUSTOM_SPLIT && cashPartInput.isBlank() && transferPartInput.isBlank() && debtPartInput.isBlank()) {
            val third = (totalAmount / 3).toLong()
            cashPartInput = third.toString()
            transferPartInput = third.toString()
            debtPartInput = (totalAmount.toLong() - (third * 2)).toString()
        }
    }

    fun addConfiguredItemToCart(product: ProductItem, unit: String, quantity: Int, unitPrice: Double) {
        val multiplier = ClothingUnits.getMultiplier(unit)
        val piecesNeeded = quantity * multiplier
        val alreadyInCartPieces = cartItems.filter { it.product.id == product.id }.sumOf { it.totalPieces }

        if (alreadyInCartPieces + piecesNeeded > product.quantity) {
            viewModel.showMessage("الكمية المطلوبة تتجاوز المتوفر بالمخزون! المتاح: ${product.quantity} حبة فقط.")
            return
        }

        val existing = cartItems.find { it.product.id == product.id && it.unit == unit }
        if (existing != null) {
            existing.quantity += quantity
            existing.unitPrice = unitPrice
        } else {
            cartItems.add(CartItem(product = product, quantity = quantity, unitPrice = unitPrice, unit = unit))
        }
        viewModel.showMessage("تمت إضافة $quantity $unit من (${product.modelName}) إلى السلة")
    }

    fun completeMultiItemSale(andPrint: Boolean = false, andShare: Boolean = false) {
        if (cartItems.isEmpty()) {
            viewModel.showMessage("سلة الفاتورة فارغة! أضف أصنافاً أولاً.")
            return
        }

        val finalCustomer = customerName.ifBlank { "زبون نقدي" }

        val actualCash: Double
        val actualTransfer: Double
        val actualDebt: Double

        when (paymentType) {
            PaymentMethods.CASH, "نقداً", "نقداً (كاش)" -> {
                actualCash = totalAmount
                actualTransfer = 0.0
                actualDebt = 0.0
            }
            PaymentMethods.TRANSFER, "تحويل عبر الصراف", "تحويل صرافة" -> {
                if (selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة المحول إليها.")
                    return
                }
                actualCash = 0.0
                actualTransfer = totalAmount
                actualDebt = 0.0
            }
            PaymentMethods.CREDIT, "آجل (دين)", "بالدين" -> {
                if (customerName.trim().isBlank()) {
                    viewModel.showMessage("يرجى كتابة أو اختيار اسم العميل لتسجيل فاتورة الآجل.")
                    return
                }
                actualCash = 0.0
                actualTransfer = 0.0
                actualDebt = totalAmount
            }
            PaymentMethods.SPLIT, "نصف كاش ونصف حوالة" -> {
                if (selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
                    return
                }
                val half = (totalAmount / 2).toLong().toDouble()
                actualCash = half
                actualTransfer = totalAmount - half
                actualDebt = 0.0
            }
            PaymentMethods.CUSTOM_SPLIT, "مختلط (كاش + حوالة + آجل)" -> {
                val c = cashPartInput.toDoubleOrNull() ?: 0.0
                val t = transferPartInput.toDoubleOrNull() ?: 0.0
                val d = debtPartInput.toDoubleOrNull() ?: 0.0
                val sumParts = c + t + d
                if (kotlin.math.abs(sumParts - totalAmount) > 1.0) {
                    viewModel.showMessage("مجموع الكاش ($c) والتحويل ($t) والآجل ($d) يجب أن يعادل صافي الفاتورة ($totalAmount ريال)!")
                    return
                }
                if (t > 0 && selectedExchangeAccount == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة لمبلغ التحويل ($t ريال).")
                    return
                }
                if (d > 0 && customerName.trim().isBlank()) {
                    viewModel.showMessage("يرجى كتابة أو اختيار اسم العميل لتسجيل المبلغ الآجل ($d ريال).")
                    return
                }
                actualCash = c
                actualTransfer = t
                actualDebt = d
            }
            else -> {
                actualCash = totalAmount
                actualTransfer = 0.0
                actualDebt = 0.0
            }
        }

        val cartSaleItems = cartItems.map {
            CartSaleItem(
                product = it.product,
                quantity = it.quantity,
                sellPrice = it.unitPrice,
                unit = it.unit
            )
        }

        val itemsSnapshot = cartItems.toList()

        viewModel.executeCartSale(
            items = cartSaleItems,
            discount = discountValue,
            customerName = if (actualDebt > 0) customerName.ifBlank { "عميل آجل" } else finalCustomer,
            customerPhone = customerPhone,
            paymentType = paymentType,
            cashPart = actualCash,
            transferPart = actualTransfer,
            debtPart = actualDebt,
            exchangeAccount = selectedExchangeAccount,
            transferRef = transferRef,
            employee = selectedEmployee,
            customInvoiceNumber = customInvoiceNumber
        ) { generatedInvoiceNumber ->
            val pdfFile = try {
                InvoiceHelper.generateSaleInvoicePdf(
                    context = context,
                    invoiceNumber = generatedInvoiceNumber,
                    customerName = finalCustomer,
                    customerPhone = customerPhone,
                    employeeName = selectedEmployee?.name ?: "صاحب المحل",
                    paymentType = paymentType,
                    items = itemsSnapshot.map {
                        InvoiceHelper.PdfSaleLineItem(
                            name = it.product.modelName,
                            details = "النوع: ${it.product.category} | ${it.product.color} - ${it.product.size}",
                            quantity = it.quantity,
                            unitPrice = it.unitPrice,
                            total = it.lineTotal,
                            unit = it.unit
                        )
                    },
                    subtotal = subtotal,
                    discount = discountValue,
                    netTotal = totalAmount,
                    cashPaid = actualCash,
                    transferPaid = actualTransfer,
                    debtAmount = actualDebt,
                    exchangeAccountName = selectedExchangeAccount?.name ?: "",
                    transferRef = transferRef,
                    timestamp = System.currentTimeMillis(),
                    settings = storeSettings
                )
            } catch (e: Exception) {
                null
            }

            if (andPrint && pdfFile != null) {
                InvoiceHelper.printPdfFile(context, pdfFile, "فاتورة_${generatedInvoiceNumber}")
            } else if (andShare && pdfFile != null) {
                InvoiceHelper.sharePdfFile(context, pdfFile, "مشاركة فاتورة_${generatedInvoiceNumber}")
            }

            completedInvoiceReceipt = CompletedInvoiceReceipt(
                invoiceNumber = generatedInvoiceNumber,
                customerName = finalCustomer,
                customerPhone = customerPhone,
                employeeName = selectedEmployee?.name ?: "صاحب المحل",
                paymentType = paymentType,
                items = itemsSnapshot,
                subtotal = subtotal,
                discount = discountValue,
                netTotal = totalAmount,
                cashPaid = actualCash,
                transferPaid = actualTransfer,
                debtAmount = actualDebt,
                exchangeAccountName = selectedExchangeAccount?.name ?: "",
                transferRef = transferRef,
                timestamp = System.currentTimeMillis(),
                pdfFile = pdfFile
            )

            cartItems.clear()
            discountValue = 0.0
            customerName = ""
            customerPhone = ""
            transferRef = ""
            cashPartInput = ""
            transferPartInput = ""
            debtPartInput = ""
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
                            text = "فاتورة بيع جديدة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = storeSettings?.invoiceHeader ?: "جودة .. تراث .. أناقة",
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
                                text = storeSettings?.storeName ?: "أزياء الأصالة",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PdfGoldPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "للملابس الرجالية",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = PdfGoldPrimary, modifier = Modifier.size(20.dp))
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
                .background(Color(0xFFF8FAFC))
        ) {
            val isWideLayout = maxWidth >= 720.dp

            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Left/Start Pane: Search + Categories + Product Grid
                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        "البحث عن منتج بالاسم أو الباركود ...",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Color(0xFFCBD5E1),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0F1E36),
                                modifier = Modifier
                                    .height(52.dp)
                                    .clickable {
                                        viewModel.showMessage("تم تشغيل مسح الباركود للكاميرا")
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("مسح باركود", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable { showAddCategoryDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("نوع جديد +", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) PdfGoldDark else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) PdfGoldDark else Color(0xFFE2E8F0)),
                                    modifier = Modifier.clickable { selectedCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        if (filteredProducts.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Text(
                                    text = "لا توجد أصناف مطابقة للبحث.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 155.dp),
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                gridItems(filteredProducts, key = { it.id }) { product ->
                                    val inCartPieces = cartItems.filter { it.product.id == product.id }.sumOf { it.totalPieces }
                                    PdfProductShelfCard(
                                        product = product,
                                        inCartPieces = inCartPieces,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(165.dp),
                                        onAddClick = { productToConfigure = product }
                                    )
                                }
                            }
                        }
                    }

                    // Right/End Pane: Invoice Cart + Payment Details
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "سلة الفاتورة (${cartItems.size} أصناف - ${cartItems.sumOf { it.totalPieces }} حبة)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (cartItems.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clickable { cartItems.clear() }
                                            .padding(4.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Text("إفراغ السلة", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (cartItems.isEmpty()) {
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
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(34.dp))
                                        Text("السلة فارغة حالياً", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), fontSize = 14.sp)
                                        Text("اختر أي صنف من القائمة الجانبية لإضافته للفاتورة", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        } else {
                            items(cartItems) { item ->
                                PdfCartItemRow(
                                    item = item,
                                    onUnitChange = { newUnit ->
                                        val newMultiplier = ClothingUnits.getMultiplier(newUnit)
                                        val otherPieces = cartItems.filter { it != item && it.product.id == item.product.id }.sumOf { it.totalPieces }
                                        if (otherPieces + (item.quantity * newMultiplier) > item.product.quantity) {
                                            viewModel.showMessage("الكمية المطلوبة تتجاوز المتوفر بالمخزون (${item.product.quantity} حبة)!")
                                        } else {
                                            item.unit = newUnit
                                            item.unitPrice = item.product.sellPrice * newMultiplier
                                        }
                                    },
                                    onIncrement = {
                                        val otherPieces = cartItems.filter { it != item && it.product.id == item.product.id }.sumOf { it.totalPieces }
                                        if (otherPieces + ((item.quantity + 1) * item.multiplier) <= item.product.quantity) {
                                            item.quantity++
                                        } else {
                                            viewModel.showMessage("وصلت للحد الأقصى المتوفر بالمخزون (${item.product.quantity} حبة)!")
                                        }
                                    },
                                    onDecrement = {
                                        if (item.quantity > 1) item.quantity-- else cartItems.remove(item)
                                    },
                                    onEditPrice = { editingCartItemPrice = item },
                                    onRemove = { cartItems.remove(item) }
                                )
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customerName,
                                            onValueChange = { customerName = it },
                                            label = { Text(if (paymentType == "آجل (دين)" || paymentType == "بالدين") "اسم العميل الكامل (إجباري للآجل) *" else "اسم العميل / الزبون (اختياري)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilledTonalIconButton(
                                            onClick = { showAddCustomerDialog = true },
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل جديد", tint = PdfHeaderNavy)
                                        }
                                    }

                                    if (customerParties.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(customerParties) { party ->
                                                val isSelected = customerName.trim().equals(party.name.trim(), ignoreCase = true)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        customerName = party.name
                                                        customerPhone = party.phone
                                                    },
                                                    label = { Text(party.name, fontSize = 11.sp, maxLines = 1) }
                                                )
                                            }
                                        }
                                    }

                                    if (employees.isNotEmpty()) {
                                        Text("البائع / الموظف المسؤول:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(employees) { emp ->
                                                val isEmp = selectedEmployee?.id == emp.id
                                                FilterChip(
                                                    selected = isEmp,
                                                    onClick = { selectedEmployee = emp },
                                                    label = { Text(emp.name, fontSize = 11.sp, maxLines = 1) }
                                                )
                                            }
                                        }
                                    }

                                    Text("طريقة الدفع:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF475569))
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(listOf("نقداً", "تحويل عبر الصراف", "آجل (دين)", "نصف كاش ونصف حوالة", "مختلط (كاش + حوالة + آجل)")) { pType ->
                                            val isPSelected = paymentType == pType
                                            FilterChip(
                                                selected = isPSelected,
                                                onClick = { paymentType = pType },
                                                label = { Text(pType, fontSize = 11.sp, maxLines = 1, fontWeight = if (isPSelected) FontWeight.Bold else FontWeight.Normal) }
                                            )
                                        }
                                    }

                                    if (paymentType == "مختلط (كاش + حوالة + آجل)") {
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("توزيع مبالغ الدفع المختلط (كاش + تحويل + آجل):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedTextField(
                                                        value = cashPartInput,
                                                        onValueChange = { cashPartInput = it },
                                                        label = { Text("كاش", fontSize = 10.sp) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    OutlinedTextField(
                                                        value = transferPartInput,
                                                        onValueChange = { transferPartInput = it },
                                                        label = { Text("صرافة", fontSize = 10.sp) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    OutlinedTextField(
                                                        value = debtPartInput,
                                                        onValueChange = { debtPartInput = it },
                                                        label = { Text("آجل", fontSize = 10.sp) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = customInvoiceNumber,
                                        onValueChange = { customInvoiceNumber = it },
                                        label = { Text("رقم الفاتورة الدفترية / الورقية (اختياري)", fontSize = 11.sp) },
                                        placeholder = { Text("فارغ = ترقيم تلقائي", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PdfGoldDark, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.fillMaxWidth().testTag("custom_invoice_number_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    val needsExchangeWide = paymentType == "تحويل عبر الصراف" ||
                                            paymentType == "نصف كاش ونصف حوالة" ||
                                            (paymentType == "مختلط (كاش + حوالة + آجل)" && (transferPartInput.toDoubleOrNull() ?: 0.0) > 0.0)

                                    if (needsExchangeWide) {
                                        Text("اختر حساب الصراف أو المحفظة المحول إليها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(exchangeAccounts) { acc ->
                                                val isAcc = selectedExchangeAccount?.id == acc.id
                                                FilterChip(
                                                    selected = isAcc,
                                                    onClick = { selectedExchangeAccount = acc },
                                                    label = { Text(acc.name, fontSize = 12.sp, maxLines = 1) }
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            value = transferRef,
                                            onValueChange = { transferRef = it },
                                            label = { Text("رقم الحوالة / إشعار الصراف (سند التحويل) *", fontSize = 11.sp) },
                                            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp)) },
                                            modifier = Modifier.fillMaxWidth().testTag("transfer_ref_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFFFBEB),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                            modifier = Modifier.clickable { showDiscountDialog = true }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = if (discountValue > 0) "خصم: ${FormatUtils.formatCurrency(discountValue)}" else "إضافة خصم",
                                                    color = Color(0xFFD97706),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("الإجمالي", fontSize = 12.sp, color = Color(0xFF64748B))
                                            Text(
                                                text = FormatUtils.formatCurrency(totalAmount),
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF0F172A)
                                                )
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { completeMultiItemSale(andPrint = true) },
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(50.dp)
                                                .testTag("btn_save_and_print"),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("حفظ وطباعة", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { completeMultiItemSale(andShare = true) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("btn_share_invoice"),
                                            shape = RoundedCornerShape(14.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0F1E36))
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0F1E36), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مشاركة الفاتورة", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F1E36), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
            // ==========================================
            // 1. Search Bar & Barcode Scan (Page 2)
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("البحث عن منتج بالاسم أو الباركود ...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                        trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFCBD5E1),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    // Barcode Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F1E36),
                        modifier = Modifier
                            .height(52.dp)
                            .clickable {
                                viewModel.showMessage("تم تشغيل مسح الباركود للكاميرا")
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("مسح باركود", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // 2. Horizontal Category Chips (Page 2)
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
                                Text(
                                    text = "نوع جديد +",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PdfGoldDark else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PdfGoldDark else Color(0xFFE2E8F0)
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
            // 3. Horizontal Product Shelf (Page 2)
            // ==========================================
            item {
                if (filteredProducts.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "لا توجد أصناف مطابقة للبحث.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts) { product ->
                            val inCartPieces = cartItems.filter { it.product.id == product.id }.sumOf { it.totalPieces }
                            PdfProductShelfCard(
                                product = product,
                                inCartPieces = inCartPieces,
                                onAddClick = { productToConfigure = product }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. Invoice Cart Section ("سلة الفاتورة")
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                        Text(
                            text = "سلة الفاتورة (${cartItems.size} أصناف - ${cartItems.sumOf { it.totalPieces }} حبة)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 16.sp
                            )
                        )
                    }

                    if (cartItems.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { cartItems.clear() }
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Text("إفراغ السلة", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Cart Items List
            if (cartItems.isEmpty()) {
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
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                            Text("السلة فارغة حالياً", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), fontSize = 15.sp)
                            Text("اضغط على أي صنف بالأعلى لاختيار العدد (حبة/درزن/نص) وإضافته للفاتورة", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            } else {
                items(cartItems) { item ->
                    PdfCartItemRow(
                        item = item,
                        onUnitChange = { newUnit ->
                            val newMultiplier = ClothingUnits.getMultiplier(newUnit)
                            val otherPieces = cartItems.filter { it != item && it.product.id == item.product.id }.sumOf { it.totalPieces }
                            if (otherPieces + (item.quantity * newMultiplier) > item.product.quantity) {
                                viewModel.showMessage("الكمية المطلوبة تتجاوز المتوفر بالمخزون (${item.product.quantity} حبة)!")
                            } else {
                                item.unit = newUnit
                                item.unitPrice = item.product.sellPrice * newMultiplier
                            }
                        },
                        onIncrement = {
                            val otherPieces = cartItems.filter { it != item && it.product.id == item.product.id }.sumOf { it.totalPieces }
                            if (otherPieces + ((item.quantity + 1) * item.multiplier) <= item.product.quantity) {
                                item.quantity++
                            } else {
                                viewModel.showMessage("وصلت للحد الأقصى المتوفر بالمخزون (${item.product.quantity} حبة)!")
                            }
                        },
                        onDecrement = {
                            if (item.quantity > 1) {
                                item.quantity--
                            } else {
                                cartItems.remove(item)
                            }
                        },
                        onEditPrice = { editingCartItemPrice = item },
                        onRemove = { cartItems.remove(item) }
                    )
                }
            }

            // ==========================================
            // 5. Payment details & Totals (Page 2)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Customer name input + Add Customer button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text(if (paymentType == "آجل (دين)" || paymentType == "بالدين") "اسم العميل الكامل (إجباري للآجل) *" else "اسم العميل / الزبون (اختياري)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { showAddCustomerDialog = true },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل جديد", tint = PdfHeaderNavy)
                            }
                        }

                        // Selectable customer chips
                        if (customerParties.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(customerParties) { party ->
                                    val isSelected = customerName.trim().equals(party.name.trim(), ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            customerName = party.name
                                            customerPhone = party.phone
                                        },
                                        label = { Text(party.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Employee / Cashier selector
                        if (employees.isNotEmpty()) {
                            Text("البائع / الموظف المسؤول:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(employees) { emp ->
                                    val isEmp = selectedEmployee?.id == emp.id
                                    FilterChip(
                                        selected = isEmp,
                                        onClick = { selectedEmployee = emp },
                                        label = { Text(emp.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Payment Methods Chips
                        Text("طريقة الدفع:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF475569))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(listOf("نقداً", "تحويل عبر الصراف", "آجل (دين)", "نصف كاش ونصف حوالة", "مختلط (كاش + حوالة + آجل)")) { pType ->
                                val isPSelected = paymentType == pType
                                FilterChip(
                                    selected = isPSelected,
                                    onClick = { paymentType = pType },
                                    label = { Text(pType, fontSize = 11.sp, fontWeight = if (isPSelected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }

                        // Custom Split UI (Cash + Transfer + Debt)
                        if (paymentType == "مختلط (كاش + حوالة + آجل)") {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("توزيع مبالغ الدفع المختلط (كاش + تحويل + آجل):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = cashPartInput,
                                            onValueChange = { cashPartInput = it },
                                            label = { Text("نقداً (كاش)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        OutlinedTextField(
                                            value = transferPartInput,
                                            onValueChange = { transferPartInput = it },
                                            label = { Text("تحويل صرافة", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        OutlinedTextField(
                                            value = debtPartInput,
                                            onValueChange = { debtPartInput = it },
                                            label = { Text("آجل (دين)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    val cVal = cashPartInput.toDoubleOrNull() ?: 0.0
                                    val tVal = transferPartInput.toDoubleOrNull() ?: 0.0
                                    val dVal = debtPartInput.toDoubleOrNull() ?: 0.0
                                    val sumVal = cVal + tVal + dVal
                                    val isMatch = kotlin.math.abs(sumVal - totalAmount) <= 1.0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المجموع المدخل: ${FormatUtils.formatCurrency(sumVal)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMatch) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        )
                                        Text(
                                            text = if (isMatch) "متطابق مع الإجمالي ✓" else "الفارق: ${FormatUtils.formatCurrency(totalAmount - sumVal)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMatch) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }

                        // Paper Booklet Invoice Number (Optional)
                        OutlinedTextField(
                            value = customInvoiceNumber,
                            onValueChange = { customInvoiceNumber = it },
                            label = { Text("رقم الفاتورة الدفترية / الورقية (اختياري)", fontSize = 11.sp) },
                            placeholder = { Text("فارغ = ترقيم تلقائي", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PdfGoldDark, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth().testTag("custom_invoice_number_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Exchange Account Selector if transfer is involved
                        val needsExchange = paymentType == "تحويل عبر الصراف" ||
                                paymentType == "نصف كاش ونصف حوالة" ||
                                (paymentType == "مختلط (كاش + حوالة + آجل)" && (transferPartInput.toDoubleOrNull() ?: 0.0) > 0.0)

                        if (needsExchange) {
                            Text("اختر حساب الصراف أو المحفظة المحول إليها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(exchangeAccounts) { acc ->
                                    val isAcc = selectedExchangeAccount?.id == acc.id
                                    FilterChip(
                                        selected = isAcc,
                                        onClick = { selectedExchangeAccount = acc },
                                        label = { Text(acc.name, fontSize = 12.sp) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = transferRef,
                                onValueChange = { transferRef = it },
                                label = { Text("رقم الحوالة / إشعار الصراف (سند التحويل) *", fontSize = 11.sp) },
                                placeholder = { Text("أدخل رقم الحوالة هنا", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth().testTag("transfer_ref_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Discount & Totals Line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Discount Tag button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFBEB),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.clickable { showDiscountDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (discountValue > 0) "خصم: ${FormatUtils.formatCurrency(discountValue)}" else "إضافة خصم",
                                        color = Color(0xFFD97706),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Total Display
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الإجمالي", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    text = FormatUtils.formatCurrency(totalAmount),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        fontSize = 24.sp
                                    )
                                )
                            }
                        }

                        // Bottom 2 Big Buttons: حفظ وطباعة | مشاركة الفاتورة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary Gold Button: حفظ وطباعة
                            Button(
                                onClick = { completeMultiItemSale(andPrint = true) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(52.dp)
                                    .testTag("btn_save_and_print"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ وطباعة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }

                            // Outlined Button: مشاركة الفاتورة
                            OutlinedButton(
                                onClick = { completeMultiItemSale(andShare = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("btn_share_invoice"),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0F1E36))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0F1E36), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة الفاتورة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F1E36))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
            }
        }
    }

    // Discount Dialog
    if (showDiscountDialog) {
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("إضافة خصم على الفاتورة", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("المبلغ الإجمالي قبل الخصم: ${FormatUtils.formatCurrency(subtotal)}")
                    OutlinedTextField(
                        value = discountInputStr,
                        onValueChange = { discountInputStr = it },
                        label = { Text("قيمة الخصم (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = discountInputStr.toDoubleOrNull() ?: 0.0
                        if (d >= subtotal) {
                            viewModel.showMessage("الخصم لا يمكن أن يعادل أو يتجاوز الفاتورة!")
                        } else {
                            discountValue = d
                            showDiscountDialog = false
                        }
                    }
                ) {
                    Text("تطبيق الخصم")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscountDialog = false }) { Text("إلغاء") }
            }
        )
    }

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
                    Text("اكتب اسم نوع الصنف وسيظهر فوراً في قائمة الاختيارات السريعة:", fontSize = 12.sp, color = Color(0xFF64748B))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        label = { Text("اسم نوع الصنف (مثلاً: أرواب، سراويل، كوفيات...)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_category_input_field"),
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
                    modifier = Modifier.testTag("confirm_add_category_btn")
                ) {
                    Text("إضافة واختيار")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCategoryDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showAddCustomerDialog) {
        AddOrEditPartyDialog(
            initialType = "CUSTOMER",
            existingParty = null,
            onDismiss = { showAddCustomerDialog = false },
            onSave = { party, initialDebt, debtNotes ->
                viewModel.saveParty(
                    party = party,
                    initialDebt = initialDebt,
                    debtNotes = debtNotes,
                    onSuccess = {
                        customerName = party.name
                        customerPhone = party.phone
                        showAddCustomerDialog = false
                    }
                )
            }
        )
    }

    // Modal to choose unit (حبة / نصف درزن / درزن) and quantity
    productToConfigure?.let { prod ->
        val inCartPieces = cartItems.filter { it.product.id == prod.id }.sumOf { it.totalPieces }
        AddProductToCartDialog(
            product = prod,
            alreadyInCartPieces = inCartPieces,
            onDismiss = { productToConfigure = null },
            onConfirm = { unit, quantity, unitPrice ->
                addConfiguredItemToCart(prod, unit, quantity, unitPrice)
                productToConfigure = null
            }
        )
    }

    // Success Invoice Receipt Dialog
    completedInvoiceReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { completedInvoiceReceipt = null },
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
                    Text("تم حفظ الفاتورة بنجاح!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    Text("رقم الفاتورة: ${receipt.invoiceNumber}", fontSize = 13.sp, color = Color(0xFF64748B))
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
                                Text("العميل:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(receipt.customerName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("طريقة الدفع:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(receipt.paymentType, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Text("الأصناف في الفاتورة (${receipt.items.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            receipt.items.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.product.modelName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text("النوع: ${line.product.category} | ${line.quantity} ${line.unit} (${line.totalPieces} حبة)", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                    Text(
                                        text = FormatUtils.formatCurrency(line.lineTotal),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            if (receipt.discount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الخصم الممنوح:", fontSize = 12.sp, color = Color(0xFFD97706))
                                    Text("- ${FormatUtils.formatCurrency(receipt.discount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المبلغ الصافي:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                Text(FormatUtils.formatCurrency(receipt.netTotal), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                            }
                        }
                    }

                    // Print & Share PDF Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdf = receipt.pdfFile ?: InvoiceHelper.generateSaleInvoicePdf(
                                    context = context,
                                    invoiceNumber = receipt.invoiceNumber,
                                    customerName = receipt.customerName,
                                    customerPhone = receipt.customerPhone,
                                    employeeName = receipt.employeeName,
                                    paymentType = receipt.paymentType,
                                    items = receipt.items.map {
                                        InvoiceHelper.PdfSaleLineItem(
                                            name = it.product.modelName,
                                            details = "النوع: ${it.product.category} | ${it.product.color} - ${it.product.size}",
                                            quantity = it.quantity,
                                            unitPrice = it.unitPrice,
                                            total = it.lineTotal,
                                            unit = it.unit
                                        )
                                    },
                                    subtotal = receipt.subtotal,
                                    discount = receipt.discount,
                                    netTotal = receipt.netTotal,
                                    cashPaid = receipt.cashPaid,
                                    transferPaid = receipt.transferPaid,
                                    debtAmount = receipt.debtAmount,
                                    exchangeAccountName = receipt.exchangeAccountName,
                                    transferRef = receipt.transferRef,
                                    timestamp = receipt.timestamp,
                                    settings = storeSettings
                                )
                                InvoiceHelper.printPdfFile(context, pdf, "فاتورة_${receipt.invoiceNumber}")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("طباعة PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                val pdf = receipt.pdfFile ?: InvoiceHelper.generateSaleInvoicePdf(
                                    context = context,
                                    invoiceNumber = receipt.invoiceNumber,
                                    customerName = receipt.customerName,
                                    customerPhone = receipt.customerPhone,
                                    employeeName = receipt.employeeName,
                                    paymentType = receipt.paymentType,
                                    items = receipt.items.map {
                                        InvoiceHelper.PdfSaleLineItem(
                                            name = it.product.modelName,
                                            details = "النوع: ${it.product.category} | ${it.product.color} - ${it.product.size}",
                                            quantity = it.quantity,
                                            unitPrice = it.unitPrice,
                                            total = it.lineTotal,
                                            unit = it.unit
                                        )
                                    },
                                    subtotal = receipt.subtotal,
                                    discount = receipt.discount,
                                    netTotal = receipt.netTotal,
                                    cashPaid = receipt.cashPaid,
                                    transferPaid = receipt.transferPaid,
                                    debtAmount = receipt.debtAmount,
                                    exchangeAccountName = receipt.exchangeAccountName,
                                    transferRef = receipt.transferRef,
                                    timestamp = receipt.timestamp,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, pdf, "مشاركة فاتورة_${receipt.invoiceNumber}")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0F1E36), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F1E36))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { completedInvoiceReceipt = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1E36))
                ) {
                    Text("فاتورة جديدة +")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        completedInvoiceReceipt = null
                        onBack()
                    }
                ) {
                    Text("الرئيسية")
                }
            }
        )
    }

    // Edit Item Price Dialog (مراعاة الزبون / تعديل سعر صنف)
    if (editingCartItemPrice != null) {
        val targetItem = editingCartItemPrice!!
        var newPriceStr by remember { mutableStateOf(targetItem.unitPrice.toLong().toString()) }
        AlertDialog(
            onDismissRequest = { editingCartItemPrice = null },
            title = { Text("تعديل سعر البيع للصنف", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("الصنف: ${targetItem.product.modelName}", fontWeight = FontWeight.Bold)
                    Text("السعر الافتراضي: ${FormatUtils.formatCurrency(targetItem.product.sellPrice)}", color = Color(0xFF64748B), fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPriceStr,
                        onValueChange = { newPriceStr = it },
                        label = { Text("سعر البيع الجديد للحبة (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = newPriceStr.toDoubleOrNull() ?: targetItem.unitPrice
                        if (p <= 0) {
                            viewModel.showMessage("يرجى إدخال سعر صحيح أكبر من الصفر!")
                        } else {
                            targetItem.unitPrice = p
                            editingCartItemPrice = null
                        }
                    }
                ) {
                    Text("تطبيق السعر")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingCartItemPrice = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ==========================================
// Product Shelf Card (Horizontal Scrollable item)
// بدون أي صور أو أيقونات - يظهر: النوع، الاسم، الكمية، السعر
// ==========================================
@Composable
fun PdfProductShelfCard(
    product: ProductItem,
    inCartPieces: Int = 0,
    modifier: Modifier = Modifier
        .width(155.dp)
        .height(165.dp),
    onAddClick: () -> Unit
) {
    val isOutOfStock = product.quantity <= 0
    Card(
        modifier = modifier
            .clickable(enabled = !isOutOfStock) { onAddClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (inCartPieces > 0) PdfGoldDark else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top row: Type Tag (النوع) & Stock Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        product.category.contains("بشت") || product.modelName.contains("بشت") -> Color(0xFFFEF3C7)
                        product.category.contains("كوت") || product.modelName.contains("كوت") -> Color(0xFFEFF6FF)
                        product.category.contains("جاكت") || product.modelName.contains("جاكت") -> Color(0xFFF0FDF4)
                        else -> Color(0xFFF1F5F9)
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = product.category.ifBlank { "ملبوسات" },
                        color = Color(0xFF1E293B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isOutOfStock -> Color(0xFFFEE2E2)
                        product.quantity <= 3 -> Color(0xFFFEF3C7)
                        else -> Color(0xFFDCFCE7)
                    }
                ) {
                    Text(
                        text = if (isOutOfStock) "نفد" else "${product.quantity} حبة",
                        color = when {
                            isOutOfStock -> Color(0xFFDC2626)
                            product.quantity <= 3 -> Color(0xFFD97706)
                            else -> Color(0xFF16A34A)
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. Middle: Name (الاسم) & Spec
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = product.modelName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.color} | مقاس: ${product.size}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Bottom: Price (السعر) & Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${product.sellPrice.toInt()} ريال",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "${(product.sellPrice * 12).toInt()} درزن",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (inCartPieces > 0) PdfGoldDark else Color(0xFF0F1E36)
                ) {
                    Text(
                        text = if (inCartPieces > 0) "$inCartPieces بالسلة" else "+ إضافة",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// Cart Item Row Component
// بدون أي أيقونات أو صور - مع اختيار الوحدة والعدد وتعديل السعر
// ==========================================
@Composable
fun PdfCartItemRow(
    item: CartItem,
    onUnitChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditPrice: () -> Unit = {},
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Item Name, Category Tag, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.product.modelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = "النوع: ${item.product.category}",
                            color = Color(0xFF475569),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }

            // Row 2: Unit Selector (حبه / نص درزن / درزن) and Quantity Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit choice chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val unitOptions = listOf(ClothingUnits.PIECE, ClothingUnits.HALF_DOZEN, ClothingUnits.DOZEN)
                    unitOptions.forEach { u ->
                        val isSel = item.unit == u
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) PdfHeaderNavy else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PdfHeaderNavy else Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { if (!isSel) onUnitChange(u) }
                        ) {
                            Text(
                                text = u,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Quantity Stepper: [-] Qty [+] and total pieces
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .clickable { onDecrement() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF334155))
                    }

                    Text(
                        text = "${item.quantity} ${item.unit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A)
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .clickable { onIncrement() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF334155))
                    }

                    Text(
                        text = "(${item.totalPieces} حبة)",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Row 3: Price (clickable for bargaining) & Line Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { onEditPrice() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "سعر ${item.unit}: ${FormatUtils.formatCurrency(item.unitPrice)}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                        Icon(Icons.Default.Edit, contentDescription = "تعديل السعر", tint = Color(0xFF64748B), modifier = Modifier.size(11.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الإجمالي:", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = FormatUtils.formatCurrency(item.lineTotal),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF16A34A)
                    )
                }
            }
        }
    }
}

// ==========================================
// Modal to choose unit (حبة / نصف درزن / درزن) and quantity
// ==========================================
@Composable
fun AddProductToCartDialog(
    product: ProductItem,
    alreadyInCartPieces: Int,
    onDismiss: () -> Unit,
    onConfirm: (unit: String, quantity: Int, unitPrice: Double) -> Unit
) {
    var selectedUnit by remember { mutableStateOf(ClothingUnits.PIECE) }
    var quantity by remember { mutableIntStateOf(1) }
    var unitPriceStr by remember(selectedUnit) {
        val defaultPrice = product.sellPrice * ClothingUnits.getMultiplier(selectedUnit)
        mutableStateOf(defaultPrice.toInt().toString())
    }

    val multiplier = ClothingUnits.getMultiplier(selectedUnit)
    val totalPiecesNeeded = quantity * multiplier
    val remainingStock = product.quantity - alreadyInCartPieces
    val isStockExceeded = totalPiecesNeeded > remainingStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.modelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Text(
                            text = "النوع: ${product.category.ifBlank { "ملابس" }}",
                            color = Color(0xFF1E40AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "${product.color} | مقاس: ${product.size}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stock Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (remainingStock > 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (remainingStock > 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الكمية المتوفرة بالمخزن:", fontSize = 12.sp, color = Color(0xFF334155))
                        Text(
                            text = "$remainingStock حبة (${remainingStock / 12} درزن)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingStock > 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }

                // 1. Choose Unit: بالحبة / نصف درزن / درزن
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("اختر نوع العدد (الوحدة):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val units = listOf(
                            ClothingUnits.PIECE to "بالحبة (1)",
                            ClothingUnits.HALF_DOZEN to "نصف درزن (6)",
                            ClothingUnits.DOZEN to "درزن (12)"
                        )
                        units.forEach { (u, label) ->
                            val isSel = selectedUnit == u
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) PdfHeaderNavy else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) PdfHeaderNavy else Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedUnit = u
                                        val newP = product.sellPrice * ClothingUnits.getMultiplier(u)
                                        unitPriceStr = newP.toInt().toString()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Quantity selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الكمية المطلوبة (من $selectedUnit):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { if (quantity > 1) quantity-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF334155))
                            }

                            Text(
                                text = quantity.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .clickable { quantity++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF334155))
                            }
                        }

                        Text(
                            text = "= $totalPiecesNeeded حبة",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStockExceeded) Color(0xFFDC2626) else Color(0xFF2563EB)
                        )
                    }
                    if (isStockExceeded) {
                        Text(
                            text = "تنبيه: الكمية المطلوبة ($totalPiecesNeeded حبة) تتجاوز المتاح بالمخزن ($remainingStock حبة)!",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Price per selected unit (Editable for bargaining)
                OutlinedTextField(
                    value = unitPriceStr,
                    onValueChange = { unitPriceStr = it },
                    label = { Text("سعر البيع لـ ($selectedUnit) بالريال") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Line total calculation
                val priceVal = unitPriceStr.toDoubleOrNull() ?: 0.0
                val totalVal = quantity * priceVal
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إجمالي الصنف بالفاتورة:", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(
                            text = FormatUtils.formatCurrency(totalVal),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = unitPriceStr.toDoubleOrNull() ?: 0.0
                    if (p <= 0) return@Button
                    if (isStockExceeded) return@Button
                    onConfirm(selectedUnit, quantity, p)
                },
                enabled = !isStockExceeded && (unitPriceStr.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
            ) {
                Text("إضافة إلى الفاتورة +", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
