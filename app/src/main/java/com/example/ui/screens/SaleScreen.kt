package com.example.ui.screens

import androidx.compose.foundation.Image
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
import com.example.data.model.ExchangeAccount
import com.example.data.model.ProductItem
import com.example.ui.StoreViewModel
import com.example.ui.theme.*
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper

data class CartItem(
    val product: ProductItem,
    var quantity: Int,
    val unitPrice: Double,
    val unit: String = ClothingUnits.PIECE
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
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }

    // Multi-Item Invoice Cart
    val cartItems = remember { mutableStateListOf<CartItem>() }
    var discountValue by remember { mutableStateOf(0.0) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var discountInputStr by remember { mutableStateOf("0") }

    // Payment details
    var paymentType by remember { mutableStateOf("نقداً") } // "نقداً", "تحويل عبر الصراف", "نصف كاش ونصف حوالة", "آجل (دين)"
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }
    var cashPaidAmount by remember { mutableStateOf(0.0) }
    var transferPaidAmount by remember { mutableStateOf(0.0) }

    LaunchedEffect(exchangeAccounts) {
        if (selectedExchangeAccount == null && exchangeAccounts.isNotEmpty()) {
            selectedExchangeAccount = exchangeAccounts.firstOrNull()
        }
    }

    val categories = listOf("الكل", "ثياب", "بشوت", "إكسسوارات", "أقمشة")

    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { prod ->
            val matchCat = when (selectedCategory) {
                "الكل" -> true
                "ثياب" -> prod.category.contains("ثوب") || prod.category.contains("ثياب")
                "بشوت" -> prod.category.contains("بشت") || prod.category.contains("دجلة")
                "إكسسوارات" -> prod.category.contains("شماغ") || prod.category.contains("عقال") || prod.category.contains("إكسسوار")
                "أقمشة" -> prod.category.contains("قماش") || prod.category.contains("طاقة")
                else -> prod.category.contains(selectedCategory)
            }
            val matchQuery = searchQuery.isBlank() ||
                    prod.modelName.contains(searchQuery, ignoreCase = true) ||
                    prod.color.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val subtotal = cartItems.sumOf { it.unitPrice * it.quantity }
    val totalAmount = (subtotal - discountValue).coerceAtLeast(0.0)

    fun addToCart(product: ProductItem) {
        val existing = cartItems.find { it.product.id == product.id }
        if (existing != null) {
            if (existing.quantity < product.quantity) {
                existing.quantity++
            } else {
                viewModel.showMessage("الكمية المتوفرة بالمخزن (${product.quantity} حبه) فقط!")
            }
        } else {
            if (product.quantity > 0) {
                cartItems.add(CartItem(product = product, quantity = 1, unitPrice = product.sellPrice))
            } else {
                viewModel.showMessage("هذا الصنف نفد من المخزون!")
            }
        }
    }

    fun completeMultiItemSale(andPrint: Boolean = false, andShare: Boolean = false) {
        if (cartItems.isEmpty()) {
            viewModel.showMessage("سلة الفاتورة فارغة! أضف أصنافاً أولاً.")
            return
        }
        if ((paymentType == "آجل (دين)" || paymentType == "بالدين") && customerName.isBlank()) {
            viewModel.showMessage("يرجى كتابة اسم العميل لتسجيل فاتورة الآجل.")
            return
        }
        if (paymentType == "تحويل عبر الصراف" && selectedExchangeAccount == null) {
            viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة.")
            return
        }

        // Execute sale for each item in transaction
        var errorOccurred = false
        val finalCustomer = customerName.ifBlank { "زبون نقدي" }

        // Process all items
        val itemsSnapshot = cartItems.toList()
        for (item in itemsSnapshot) {
            val itemProportion = if (subtotal > 0) (item.unitPrice * item.quantity) / subtotal else 1.0
            val itemDiscount = discountValue * itemProportion

            viewModel.executeSale(
                product = item.product,
                quantity = item.quantity,
                sellPrice = item.unitPrice,
                discount = itemDiscount,
                customerName = finalCustomer,
                customerPhone = customerPhone,
                paymentType = paymentType,
                employee = null,
                unit = item.unit,
                cashPart = if (paymentType == "نصف كاش ونصف حوالة") totalAmount / 2 else 0.0,
                transferPart = if (paymentType == "نصف كاش ونصف حوالة") totalAmount / 2 else 0.0,
                exchangeAccount = selectedExchangeAccount,
                transferRef = ""
            ) {
                // Success per item
            }
        }

        if (andShare) {
            val invoiceText = InvoiceHelper.generateThermalReceiptText(
                storeName = storeSettings?.storeName ?: "أزياء الأصالة",
                phoneNumber = storeSettings?.phoneNumber ?: "0501234567",
                address = storeSettings?.address ?: "صنعاء",
                invoiceId = System.currentTimeMillis() % 10000,
                timestamp = System.currentTimeMillis(),
                customerName = finalCustomer,
                cashierName = "المدير",
                items = itemsSnapshot.map { it.product.modelName to Triple(it.quantity, it.unitPrice, it.unitPrice * it.quantity) },
                totalAmount = totalAmount,
                discount = discountValue,
                paymentType = paymentType,
                headerNote = storeSettings?.invoiceHeader ?: "جودة .. تراث .. أناقة",
                footerNote = storeSettings?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام"
            )
            InvoiceHelper.shareInvoiceText(context, invoiceText)
        }

        cartItems.clear()
        discountValue = 0.0
        viewModel.showMessage("تم حفظ الفاتورة بنجاح!")
        onBack()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
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
                            PdfProductShelfCard(
                                product = product,
                                onAddClick = { addToCart(product) }
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
                            text = "سلة الفاتورة (${cartItems.sumOf { it.quantity }})",
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
                            Text("اضغط على علامة (+) في أي صنف بالأعلى لإضافته للفاتورة", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            } else {
                items(cartItems) { item ->
                    PdfCartItemRow(
                        item = item,
                        onIncrement = {
                            if (item.quantity < item.product.quantity) {
                                item.quantity++
                            } else {
                                viewModel.showMessage("وصلت للحد الأقصى المتوفر بالمخزون!")
                            }
                        },
                        onDecrement = {
                            if (item.quantity > 1) {
                                item.quantity--
                            } else {
                                cartItems.remove(item)
                            }
                        },
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
                        // Customer name input
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("اسم العميل / الزبون (اختياري للبيع النقدي)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Payment Methods Chips
                        Text("طريقة الدفع:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF475569))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("نقداً", "تحويل عبر الصراف", "نصف كاش ونصف حوالة", "آجل (دين)").forEach { pType ->
                                val isPSelected = paymentType == pType
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isPSelected) PdfHeaderNavy else Color(0xFFF1F5F9),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { paymentType = pType }
                                ) {
                                    Text(
                                        text = pType,
                                        color = if (isPSelected) Color.White else Color(0xFF475569),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Exchange Account Selector if transfer
                        if (paymentType == "تحويل عبر الصراف" || paymentType == "نصف كاش ونصف حوالة") {
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

    // Discount Dialog
    if (showDiscountDialog) {
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("إضافة خصم على الفاتورة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

// ==========================================
// Product Shelf Card (Horizontal Scrollable item)
// ==========================================
@Composable
fun PdfProductShelfCard(
    product: ProductItem,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(105.dp)
            .height(175.dp),
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Product Image
            val imageRes = when {
                product.modelName.contains("بشت") || product.category.contains("بشت") -> R.drawable.img_bisht
                product.modelName.contains("شماغ") || product.category.contains("شماغ") -> R.drawable.img_shemagh
                product.modelName.contains("قماش") || product.category.contains("قماش") -> R.drawable.img_fabrics
                else -> R.drawable.img_thobe
            }

            Image(
                painter = painterResource(id = imageRes),
                contentDescription = product.modelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = product.modelName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.sellPrice.toInt()} ر.س",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }

            // Dark plus circular button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
                    .clickable { onAddClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة للسلة", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ==========================================
// Cart Item Row Component
// ==========================================
@Composable
fun PdfCartItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Product Image
                val imageRes = when {
                    item.product.modelName.contains("بشت") || item.product.category.contains("بشت") -> R.drawable.img_bisht
                    item.product.modelName.contains("شماغ") || item.product.category.contains("شماغ") -> R.drawable.img_shemagh
                    item.product.modelName.contains("قماش") || item.product.category.contains("قماش") -> R.drawable.img_fabrics
                    else -> R.drawable.img_thobe
                }

                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                )

                // Stepper: [-] Qty [+]
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
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
                    }

                    Text(
                        text = item.quantity.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
                    }
                }

                // Name & Spec
                Column(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = item.product.modelName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${item.product.color} - مقاس ${item.product.size}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Price & Remove Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${(item.unitPrice * item.quantity).toInt()} ر.س",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
