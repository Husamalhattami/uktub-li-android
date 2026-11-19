package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CashDrawerMovement
import com.example.data.model.CashDrawerShift
import com.example.data.model.DebtRecord
import com.example.data.model.Employee
import com.example.data.model.ExchangeAccount
import com.example.data.model.ProductItem
import com.example.data.model.ReturnTypes
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.CollectionReceiptDialog
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.DialogSectionHeader
import com.example.ui.components.DialogSegmentedChoice
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.SupplierPaymentDialog
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StoreViewModel,
    onNavigate: (AppScreen) -> Unit
) {
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val totalCustomerDebts by viewModel.totalCustomerDebts.collectAsStateWithLifecycle()
    val totalSupplierDebts by viewModel.totalSupplierDebts.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()

    // Accounting Fix: Actual liquidity is cash + exchange/wallet balances only (excluding unsold inventory)
    val actualLiquidity = cashBalance + totalExchangeBalance
    // Total Assets = actual liquidity + inventory + customer debts - supplier debts
    val totalAssets = actualLiquidity + inventoryValue + totalCustomerDebts - totalSupplierDebts

    val storeAlerts = remember(
        products,
        debts,
        totalCustomerDebts,
        totalSupplierDebts,
        isPinConfigured,
        activeShift,
        cashBalance
    ) {
        buildStoreAlerts(
            products = products,
            debts = debts,
            totalCustomerDebts = totalCustomerDebts,
            totalSupplierDebts = totalSupplierDebts,
            isPinConfigured = isPinConfigured,
            activeShift = activeShift,
            cashBalance = cashBalance
        )
    }

    var showQuickAddSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showSupplierPaymentDialog by remember { mutableStateOf(false) }
    var showAddPartyDialog by remember { mutableStateOf<String?>(null) } // "CUSTOMER" or "SUPPLIER"
    var showAddExchangeDialog by remember { mutableStateOf(false) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showQuickAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("إضافة سريعة +", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = PdfGoldDark,
                modifier = Modifier.testTag("fab_quick_add_home")
            )
        },
        topBar = {
            val subtitleText = remember(storeSettings) {
                listOfNotNull(
                    storeSettings?.address?.takeIf { it.isNotBlank() },
                    storeSettings?.phone?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "إدارة أسهل .. لمحل أكثر نجاحاً" }
            }
            StoreTopBar(
                title = storeSettings?.storeName?.ifBlank { "متجري" } ?: "متجري",
                subtitle = subtitleText,
                logoPath = storeSettings?.logoPath.orEmpty(),
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showNotificationsSheet = true },
                            modifier = Modifier.testTag("btn_notifications_topbar")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "تنبيهات وإشعارات المحل",
                                    tint = Color.White
                                )
                                if (storeAlerts.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-3).dp)
                                            .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                            .clip(CircleShape)
                                            .background(FintechRose)
                                            .padding(horizontal = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (storeAlerts.size > 9) "9+" else "${storeAlerts.size}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { onNavigate(AppScreen.SETTINGS) },
                            modifier = Modifier.testTag("btn_settings_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val recentMovements = remember(cashMovements) { cashMovements.take(4) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
        ) {
            val isWideLayout = maxWidth >= 600.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ==========================================
                // 1. Unified Compact Hero + Assets Overview Card
                // ==========================================
                item(key = "home_hero_card") {
                    HomeHeroOverviewCard(
                        actualLiquidity = actualLiquidity,
                        totalAssets = totalAssets,
                        cashBalance = cashBalance,
                        totalExchangeBalance = totalExchangeBalance,
                        inventoryValue = inventoryValue,
                        totalCustomerDebts = totalCustomerDebts,
                        totalSupplierDebts = totalSupplierDebts,
                        currencySymbol = storeSettings?.currencySymbol ?: "ريال",
                        onNavigate = onNavigate
                    )
                }

                // ==========================================
                // 2. Compact 4 Fast Action Cards (4-in-row wide, 2x2 compact on phones)
                // ==========================================
                item(key = "home_quick_actions") {
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfActionCard(
                                title = "شراء بضاعة",
                                subtitle = "فاتورة شراء",
                                icon = Icons.Default.AddShoppingCart,
                                iconColor = Color(0xFF0F766E),
                                iconBg = Color(0xFFCCFBF1),
                                cardBg = PdfTealCard,
                                borderColor = Color(0xFF99F6E4),
                                modifier = Modifier.weight(1f).testTag("btn_action_purchase"),
                                onClick = { onNavigate(AppScreen.PURCHASE) }
                            )
                            PdfActionCard(
                                title = "بيع جديد",
                                subtitle = "فاتورة بيع",
                                icon = Icons.Default.PointOfSale,
                                iconColor = Color(0xFF15803D),
                                iconBg = Color(0xFFDCFCE7),
                                cardBg = PdfMintCard,
                                borderColor = Color(0xFFBBF7D0),
                                modifier = Modifier.weight(1f).testTag("btn_action_sale"),
                                onClick = { onNavigate(AppScreen.SALE) }
                            )
                            PdfActionCard(
                                title = "تحصيل زلط",
                                subtitle = "سند قبض",
                                icon = Icons.Default.PriceCheck,
                                iconColor = Color(0xFF166534),
                                iconBg = Color(0xFFDCFCE7),
                                cardBg = PdfGreenCard,
                                borderColor = Color(0xFFBBF7D0),
                                modifier = Modifier.weight(1f).testTag("btn_action_debt"),
                                onClick = { showCollectionDialog = true }
                            )
                            PdfActionCard(
                                title = "تسجيل مصروف",
                                subtitle = "مصروف جديد",
                                icon = Icons.Default.ReceiptLong,
                                iconColor = Color(0xFF991B1B),
                                iconBg = Color(0xFFFEE2E2),
                                cardBg = PdfRoseCard,
                                borderColor = Color(0xFFFECACA),
                                modifier = Modifier.weight(1f).testTag("btn_action_expense"),
                                onClick = { onNavigate(AppScreen.EXPENSES) }
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfActionCard(
                                    title = "شراء بضاعة",
                                    subtitle = "فاتورة شراء",
                                    icon = Icons.Default.AddShoppingCart,
                                    iconColor = Color(0xFF0F766E),
                                    iconBg = Color(0xFFCCFBF1),
                                    cardBg = PdfTealCard,
                                    borderColor = Color(0xFF99F6E4),
                                    modifier = Modifier.weight(1f).testTag("btn_action_purchase"),
                                    onClick = { onNavigate(AppScreen.PURCHASE) }
                                )
                                PdfActionCard(
                                    title = "بيع جديد",
                                    subtitle = "فاتورة بيع",
                                    icon = Icons.Default.PointOfSale,
                                    iconColor = Color(0xFF15803D),
                                    iconBg = Color(0xFFDCFCE7),
                                    cardBg = PdfMintCard,
                                    borderColor = Color(0xFFBBF7D0),
                                    modifier = Modifier.weight(1f).testTag("btn_action_sale"),
                                    onClick = { onNavigate(AppScreen.SALE) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfActionCard(
                                    title = "تحصيل زلط",
                                    subtitle = "سند قبض",
                                    icon = Icons.Default.PriceCheck,
                                    iconColor = Color(0xFF166534),
                                    iconBg = Color(0xFFDCFCE7),
                                    cardBg = PdfGreenCard,
                                    borderColor = Color(0xFFBBF7D0),
                                    modifier = Modifier.weight(1f).testTag("btn_action_debt"),
                                    onClick = { showCollectionDialog = true }
                                )
                                PdfActionCard(
                                    title = "تسجيل مصروف",
                                    subtitle = "مصروف جديد",
                                    icon = Icons.Default.ReceiptLong,
                                    iconColor = Color(0xFF991B1B),
                                    iconBg = Color(0xFFFEE2E2),
                                    cardBg = PdfRoseCard,
                                    borderColor = Color(0xFFFECACA),
                                    modifier = Modifier.weight(1f).testTag("btn_action_expense"),
                                    onClick = { onNavigate(AppScreen.EXPENSES) }
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 3. Compact 3-Row Entity & Module Shortcuts
                // ==========================================
                item(key = "home_entity_shortcuts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "إدارة الحسابات والأقسام",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 13.5.sp
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickEntityButton(
                                title = "العملاء والموردين",
                                subtitle = "الحسابات والديون",
                                icon = Icons.Default.People,
                                color = Color(0xFF1E3A8A),
                                bg = Color(0xFFEFF6FF),
                                border = Color(0xFFBFDBFE),
                                modifier = Modifier.weight(1f).testTag("btn_quick_parties"),
                                onClick = { onNavigate(AppScreen.DEBTS) }
                            )
                            QuickEntityButton(
                                title = "الصرافين والمحافظ",
                                subtitle = "الأرصدة والحوالات",
                                icon = Icons.Default.AccountBalance,
                                color = Color(0xFF0F766E),
                                bg = Color(0xFFF0FDFA),
                                border = Color(0xFF99F6E4),
                                modifier = Modifier.weight(1f).testTag("btn_quick_exchange"),
                                onClick = { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickEntityButton(
                                title = "العمال والرواتب",
                                subtitle = "السلف والعمولات",
                                icon = Icons.Default.Badge,
                                color = Color(0xFFB45309),
                                bg = Color(0xFFFFFBEB),
                                border = Color(0xFFFDE68A),
                                modifier = Modifier.weight(1f).testTag("btn_quick_employees"),
                                onClick = { onNavigate(AppScreen.EMPLOYEES) }
                            )
                            QuickEntityButton(
                                title = "إدارة الصندوق",
                                subtitle = "السيولة والسحب",
                                icon = Icons.Default.AccountBalanceWallet,
                                color = Color(0xFF15803D),
                                bg = Color(0xFFF0FDF4),
                                border = Color(0xFFBBF7D0),
                                modifier = Modifier.weight(1f).testTag("btn_quick_drawer"),
                                onClick = { onNavigate(AppScreen.CASH_DRAWER) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickEntityButton(
                                title = "المرتجعات",
                                subtitle = "حسب الفاتورة",
                                icon = Icons.AutoMirrored.Filled.Undo,
                                color = FintechAmber,
                                bg = FintechAmberBg,
                                border = FintechAmberBorder,
                                modifier = Modifier.weight(1f).testTag("btn_home_returns"),
                                onClick = { viewModel.openReturnsScreen(ReturnTypes.SALE_RETURN, null) }
                            )
                            QuickEntityButton(
                                title = "الفواتير والسجلات",
                                subtitle = "إدارة وتعديل الفواتير",
                                icon = Icons.Default.History,
                                color = Color(0xFF334155),
                                bg = Color(0xFFF1F5F9),
                                border = Color(0xFFCBD5E1),
                                modifier = Modifier.weight(1f).testTag("btn_home_transactions"),
                                onClick = { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                            )
                        }
                    }
                }

                // ==========================================
                // 4. Compact Last Movements
                // ==========================================
                item(key = "home_movements_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "آخر الحركات",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "عرض الكل  ›",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF475569),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                if (recentMovements.isEmpty()) {
                    item(key = "home_movements_empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = "لا توجد حركات مالية مسجلة بعد.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(recentMovements, key = { it.id }) { movement ->
                        PdfMovementRow(movement = movement)
                    }
                }

                item(key = "home_bottom_spacer") {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }

        // ==========================================
        // ModalBottomSheet: الإجراءات والإضافة السريعة
        // ==========================================
        if (showQuickAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQuickAddSheet = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "العمليات والإضافة السريعة",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "اختر العملية أو الحساب الذي تريد إضافته مباشرة:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 0. تحصيل زلط / سند قبض
                    QuickSheetActionRow(
                        title = "تحصيل زلط (سند قبض)",
                        subtitle = "استلام زلط من عميل أو تحصيل عام مع رقم سند اختياري أو تلقائي",
                        icon = Icons.Default.PriceCheck,
                        iconColor = Color(0xFF15803D),
                        iconBg = Color(0xFFDCFCE7),
                        onClick = {
                            showQuickAddSheet = false
                            showCollectionDialog = true
                        }
                    )

                    // 0b. تسديد لمورد / سند صرف
                    QuickSheetActionRow(
                        title = "تسديد لمورد (سند صرف)",
                        subtitle = "صرف دفعة لمورد نقداً أو عبر الصرافة مع رقم سند اختياري أو تلقائي",
                        icon = Icons.Default.LocalShipping,
                        iconColor = Color(0xFFDC2626),
                        iconBg = Color(0xFFFEE2E2),
                        onClick = {
                            showQuickAddSheet = false
                            showSupplierPaymentDialog = true
                        }
                    )

                    // 0c. مرتجع مبيعات من عميل (حسب الفاتورة)
                    QuickSheetActionRow(
                        title = "مرتجع مبيعات من عميل (حسب الفاتورة)",
                        subtitle = "إرجاع أصناف من فاتورة بيع بسعر نفس الفاتورة وتحديث المخزون والرصيد",
                        icon = Icons.AutoMirrored.Filled.Undo,
                        iconColor = FintechAmber,
                        iconBg = FintechAmberBg,
                        modifier = Modifier.testTag("sheet_sale_return"),
                        onClick = {
                            showQuickAddSheet = false
                            viewModel.openReturnsScreen(ReturnTypes.SALE_RETURN, null)
                        }
                    )

                    // 0d. مرتجع مشتريات إلى مورد (حسب الفاتورة)
                    QuickSheetActionRow(
                        title = "مرتجع مشتريات إلى مورد (حسب الفاتورة)",
                        subtitle = "إرجاع بضاعة لمورد بسعر فاتورة الشراء الأصلية وخصم الدين أو استرداد المبلغ",
                        icon = Icons.Default.Inventory2,
                        iconColor = BrandBlue,
                        iconBg = BrandBlueBg,
                        modifier = Modifier.testTag("sheet_purchase_return"),
                        onClick = {
                            showQuickAddSheet = false
                            viewModel.openReturnsScreen(ReturnTypes.PURCHASE_RETURN, null)
                        }
                    )

                    // 1. إضافة عميل
                    QuickSheetActionRow(
                        title = "إضافة عميل جديد (فتح حساب)",
                        subtitle = "تسجيل زبون وفتح حساب آجل له مع الرصيد السابق",
                        icon = Icons.Default.PersonAdd,
                        iconColor = Color(0xFF1E3A8A),
                        iconBg = Color(0xFFEFF6FF),
                        onClick = {
                            showQuickAddSheet = false
                            showAddPartyDialog = "CUSTOMER"
                        }
                    )

                    // 2. إضافة مورد
                    QuickSheetActionRow(
                        title = "إضافة مورد جديد (فتح حساب معمل)",
                        subtitle = "تسجيل مورد أقمشة وملابس مع الديون المستحقة له",
                        icon = Icons.Default.AddBusiness,
                        iconColor = Color(0xFF0F766E),
                        iconBg = Color(0xFFF0FDFA),
                        onClick = {
                            showQuickAddSheet = false
                            showAddPartyDialog = "SUPPLIER"
                        }
                    )

                    // 3. إضافة صراف أو محفظة
                    QuickSheetActionRow(
                        title = "إضافة حساب صراف أو محفظة إلكترونية",
                        subtitle = "إضافة حساب صرافة (كريمي، يمن كاش، جوالي...) للتحويلات",
                        icon = Icons.Default.AddCard,
                        iconColor = Color(0xFF0284C7),
                        iconBg = Color(0xFFE0F2FE),
                        onClick = {
                            showQuickAddSheet = false
                            showAddExchangeDialog = true
                        }
                    )

                    // 4. إضافة عامل
                    QuickSheetActionRow(
                        title = "إضافة عامل جديد للمحل",
                        subtitle = "تسجيل موظف أو بائع وتحديد الراتب الشهري والعمولة",
                        icon = Icons.Default.Badge,
                        iconColor = Color(0xFFB45309),
                        iconBg = Color(0xFFFFFBEB),
                        onClick = {
                            showQuickAddSheet = false
                            showAddEmployeeDialog = true
                        }
                    )

                    // 5. إيداع سيولة
                    QuickSheetActionRow(
                        title = "إيداع سيولة في الصندوق",
                        subtitle = "تغذية الصندوق برأس مال أو مقبوضات نقدية",
                        icon = Icons.Default.ArrowDownward,
                        iconColor = Color(0xFF15803D),
                        iconBg = Color(0xFFF0FDF4),
                        onClick = {
                            showQuickAddSheet = false
                            showDepositDialog = true
                        }
                    )

                    // 6. سحب شخصي
                    QuickSheetActionRow(
                        title = "سحب شخصي للمالك",
                        subtitle = "تسجيل مسحوبات صاحب المحل من الصندوق",
                        icon = Icons.Default.ArrowUpward,
                        iconColor = Color(0xFFDC2626),
                        iconBg = Color(0xFFFEF2F2),
                        onClick = {
                            showQuickAddSheet = false
                            showWithdrawDialog = true
                        }
                    )

                    // 7. تحويل بين الصندوق والصراف
                    QuickSheetActionRow(
                        title = "ترحيل سيولة: بين الصندوق والصراف",
                        subtitle = "تحويل مبالغ من الصندوق للصراف أو العكس وتغذية الحسابات",
                        icon = Icons.Default.SwapHoriz,
                        iconColor = Color(0xFF0F1E36),
                        iconBg = Color(0xFFF1F5F9),
                        onClick = {
                            showQuickAddSheet = false
                            onNavigate(AppScreen.CASH_DRAWER)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // ==========================================
        // ModalBottomSheet: مركز تنبيهات وإشعارات المحل
        // ==========================================
        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.testTag("store_notifications_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مركز تنبيهات المحل",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = "يراقب نواقص البضاعة، الديون المستحقة، الأمان، وحالة الوردية تلقائياً",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 12.sp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (storeAlerts.isEmpty()) FintechEmeraldBg else FintechRoseBg
                        ) {
                            Text(
                                text = if (storeAlerts.isEmpty()) "آمن ✓" else "${storeAlerts.size} تنبيه",
                                color = if (storeAlerts.isEmpty()) FintechEmerald else FintechRose,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder)

                    if (storeAlerts.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FintechEmeraldBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FintechEmeraldBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = FintechEmerald,
                                    modifier = Modifier.size(30.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "وضع المحل ممتاز ولا توجد تنبيهات عاجلة",
                                        fontWeight = FontWeight.Bold,
                                        color = FintechEmerald,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "يظهر هنا أي صنف نفد أو قارب على النفاد، الديون المتأخرة أو المستحقة، تنبيهات رمز المدير، والورديات المفتوحة طويلاً.",
                                        fontSize = 12.sp,
                                        color = SlateTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        storeAlerts.forEach { alert ->
                            QuickSheetActionRow(
                                title = alert.title,
                                subtitle = alert.message,
                                icon = alert.icon,
                                iconColor = alert.accentColor,
                                iconBg = alert.containerColor,
                                onClick = {
                                    showNotificationsSheet = false
                                    onNavigate(alert.targetScreen)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // --- Dialog 1: Add Customer or Supplier ---
        showAddPartyDialog?.let { partyType ->
            AddOrEditPartyDialog(
                initialType = partyType,
                existingParty = null,
                onDismiss = { showAddPartyDialog = null },
                onSave = { party, initialDebt, debtNotes, debtTs ->
                    viewModel.saveParty(
                        party = party,
                        initialDebt = initialDebt,
                        debtNotes = debtNotes,
                        initialDebtTimestamp = debtTs,
                        onSuccess = { showAddPartyDialog = null }
                    )
                }
            )
        }

        // --- Dialog 2: Add Exchange Account or Wallet ---
        if (showAddExchangeDialog) {
            var accName by remember { mutableStateOf("") }
            var accNumber by remember { mutableStateOf("") }
            var balanceStr by remember { mutableStateOf("") }
            var accType by remember { mutableStateOf("صرافة") }

            StoreOperationDialog(
                onDismissRequest = { showAddExchangeDialog = false },
                title = "إضافة حساب صراف أو محفظة",
                subtitle = "ربط حساب مالي أو محفظة إلكترونية",
                icon = Icons.Default.AccountBalance,
                accentColor = FintechIndigo,
                confirmText = "حفظ الحساب",
                confirmIcon = Icons.Default.Save,
                confirmColor = PdfHeaderNavy,
                onConfirm = {
                    if (accName.isBlank()) {
                        viewModel.showMessage("يرجى إدخال اسم الصراف أو المحفظة")
                    } else {
                        val initBal = balanceStr.toDoubleOrNull() ?: 0.0
                        viewModel.saveExchangeAccount(
                            ExchangeAccount(
                                name = accName,
                                accountNumber = accNumber,
                                phone = "",
                                balance = initBal,
                                type = accType,
                                notes = "تمت الإضافة من الإجراءات السريعة"
                            )
                        ) {
                            showAddExchangeDialog = false
                        }
                    }
                }
            ) {
                DialogSectionHeader(title = "نوع الحساب", icon = Icons.Default.AccountBalanceWallet, accentColor = FintechIndigo)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogSegmentedChoice(
                        selected = accType == "صرافة",
                        label = "شركة / محل صرافة",
                        icon = Icons.Default.AccountBalance,
                        selectedColor = PdfHeaderNavy,
                        onClick = { accType = "صرافة" },
                        modifier = Modifier.weight(1f)
                    )
                    DialogSegmentedChoice(
                        selected = accType == "محفظة",
                        label = "محفظة إلكترونية",
                        icon = Icons.Default.Smartphone,
                        selectedColor = FintechIndigo,
                        onClick = { accType = "محفظة" },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = accName,
                    onValueChange = { accName = it },
                    label = { Text("اسم الصراف أو المحفظة (مثلاً: الكريمي) *") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = PdfHeaderNavy) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accNumber,
                    onValueChange = { accNumber = it },
                    label = { Text("رقم الحساب أو المحفظة (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null, tint = SlateTextSecondary) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceStr,
                    onValueChange = { balanceStr = it },
                    label = { Text("الرصيد المتاح حالياً (ريال)") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- Dialog 3: Add Employee ---
        if (showAddEmployeeDialog) {
            var empName by remember { mutableStateOf("") }
            var empPhone by remember { mutableStateOf("") }
            var empRole by remember { mutableStateOf("بائع") }
            var empSalaryStr by remember { mutableStateOf("3000") }

            StoreOperationDialog(
                onDismissRequest = { showAddEmployeeDialog = false },
                title = "إضافة عامل / موظف جديد",
                subtitle = "تسجيل بيانات موظف وراتبه الشهري",
                icon = Icons.Default.PersonAdd,
                accentColor = BrandBlue,
                confirmText = "حفظ العامل",
                confirmIcon = Icons.Default.Save,
                confirmColor = PdfHeaderNavy,
                onConfirm = {
                    if (empName.isBlank()) {
                        viewModel.showMessage("يرجى إدخال اسم العامل")
                    } else {
                        val sal = empSalaryStr.toDoubleOrNull() ?: 0.0
                        viewModel.saveEmployee(
                            Employee(
                                name = empName,
                                phone = empPhone,
                                role = empRole,
                                baseSalary = sal,
                                commissionPercent = 0.0
                            )
                        ) {
                            showAddEmployeeDialog = false
                        }
                    }
                }
            ) {
                OutlinedTextField(
                    value = empName,
                    onValueChange = { empName = it },
                    label = { Text("اسم العامل الكامل *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = empPhone,
                    onValueChange = { empPhone = it },
                    label = { Text("رقم الهاتف للتواصل") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = empRole,
                    onValueChange = { empRole = it },
                    label = { Text("المسمى الوظيفي (بائع، خياط، كاشير...)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = BrandBlue) },
                    colors = storeDialogTextFieldColors(BrandBlue),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = empSalaryStr,
                    onValueChange = { empSalaryStr = it },
                    label = { Text("الراتب الشهري (ريال)") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- Dialog 4: Deposit Dialog ---
        if (showDepositDialog) {
            var depositAmountStr by remember { mutableStateOf("") }
            var depositReason by remember { mutableStateOf("إيداع رأس مال / تغذية الصندوق") }
            var depositTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

            StoreOperationDialog(
                onDismissRequest = { showDepositDialog = false },
                title = "إيداع سيولة جديدة في الصندوق",
                subtitle = "تغذية الصندوق النقدي أو رأس المال",
                icon = Icons.Default.Input,
                accentColor = FintechEmerald,
                confirmText = "تأكيد الإيداع",
                confirmIcon = Icons.Default.Check,
                confirmColor = FintechEmerald,
                onConfirm = {
                    val amt = depositAmountStr.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) {
                        viewModel.showMessage("يرجى إدخال مبلغ إيداع صحيح!")
                    } else {
                        viewModel.recordManualDeposit(amt, depositReason, depositTimestamp) {
                            showDepositDialog = false
                        }
                    }
                }
            ) {
                DialogInfoBanner(
                    title = "رصيد الصندوق الحالي:",
                    value = FormatUtils.formatCurrency(cashBalance),
                    containerColor = FintechEmeraldBg,
                    contentColor = FintechEmerald,
                    borderColor = FintechEmerald.copy(alpha = 0.35f),
                    icon = Icons.Default.AccountBalanceWallet
                )
                OutlinedTextField(
                    value = depositAmountStr,
                    onValueChange = { depositAmountStr = it },
                    label = { Text("مبلغ الإيداع (ريال) *") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = depositReason,
                    onValueChange = { depositReason = it },
                    label = { Text("مصدر السيولة / ملاحظات") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                TransactionDatePickerField(
                    selectedTimestamp = depositTimestamp,
                    onDateSelected = { depositTimestamp = it },
                    label = "تاريخ الإيداع",
                    testTag = "home_deposit_date_picker"
                )
            }
        }

        // --- Dialog 5: Withdrawal Dialog ---
        if (showWithdrawDialog) {
            var withdrawAmountStr by remember { mutableStateOf("") }
            var withdrawReason by remember { mutableStateOf("مسحوبات شخصية للمالك") }
            var withdrawTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

            StoreOperationDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = "سحب شخصي للمالك من الصندوق",
                subtitle = "توثيق المسحوبات النقدية من الصندوق",
                icon = Icons.Default.Output,
                accentColor = FintechRose,
                confirmText = "تأكيد السحب",
                confirmIcon = Icons.Default.Check,
                confirmColor = FintechRose,
                onConfirm = {
                    val amt = withdrawAmountStr.toDoubleOrNull() ?: 0.0
                    if (amt <= 0 || amt > cashBalance) {
                        viewModel.showMessage("المبلغ المسحوب غير صحيح أو يتجاوز رصيد الصندوق!")
                    } else {
                        viewModel.recordOwnerWithdrawal(amt, withdrawReason, withdrawTimestamp) {
                            showWithdrawDialog = false
                        }
                    }
                }
            ) {
                DialogInfoBanner(
                    title = "رصيد الصندوق المتاح:",
                    value = FormatUtils.formatCurrency(cashBalance),
                    containerColor = Color(0xFFFFF1F2),
                    contentColor = FintechRose,
                    borderColor = Color(0xFFFECDD3),
                    icon = Icons.Default.AccountBalanceWallet
                )
                OutlinedTextField(
                    value = withdrawAmountStr,
                    onValueChange = { withdrawAmountStr = it },
                    label = { Text("المبلغ المسحوب (ريال) *") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechRose) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechRose),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = withdrawReason,
                    onValueChange = { withdrawReason = it },
                    label = { Text("سبب السحب / ملاحظات") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                TransactionDatePickerField(
                    selectedTimestamp = withdrawTimestamp,
                    onDateSelected = { withdrawTimestamp = it },
                    label = "تاريخ السحب",
                    testTag = "home_withdraw_date_picker"
                )
            }
        }

        if (showCollectionDialog) {
            val customerParties by viewModel.customerParties.collectAsStateWithLifecycle()
            val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
            CollectionReceiptDialog(
                customerParties = customerParties,
                customerDebts = debts,
                exchangeAccounts = exchangeAccounts,
                onDismiss = { showCollectionDialog = false },
                onConfirm = { payerName, party, amount, voucher, method, account, notes, timestamp ->
                    viewModel.recordCollectionReceipt(
                        payerName = payerName,
                        party = party,
                        amount = amount,
                        voucherNumberInput = voucher,
                        paymentMethod = method,
                        exchangeAccount = account,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        showCollectionDialog = false
                    }
                }
            )
        }

        if (showSupplierPaymentDialog) {
            val supplierParties by viewModel.supplierParties.collectAsStateWithLifecycle()
            val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
            SupplierPaymentDialog(
                supplierParties = supplierParties,
                supplierDebts = debts,
                exchangeAccounts = exchangeAccounts,
                onDismiss = { showSupplierPaymentDialog = false },
                onConfirm = { supplierName, party, amount, voucher, method, account, notes, timestamp ->
                    viewModel.recordSupplierPaymentReceipt(
                        supplierName = supplierName,
                        party = party,
                        amount = amount,
                        voucherNumberInput = voucher,
                        paymentMethod = method,
                        exchangeAccount = account,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        showSupplierPaymentDialog = false
                    }
                }
            )
        }
        }
    }
}

@Composable
fun QuickSheetActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// Sub-Components Matching PDF Exactly
// ==========================================

@Composable
fun HomeHeroOverviewCard(
    actualLiquidity: Double,
    totalAssets: Double,
    cashBalance: Double,
    totalExchangeBalance: Double,
    inventoryValue: Double,
    totalCustomerDebts: Double,
    totalSupplierDebts: Double,
    currencySymbol: String,
    onNavigate: (AppScreen) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PdfHeaderNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A63))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F1E36), Color(0xFF162947))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Liquidity + Net Wealth Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "السيولة النقدية المتاحة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF90CDF4),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatNumber(actualLiquidity),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 23.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = currencySymbol,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFF90CDF4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.clickable { onNavigate(AppScreen.REPORTS) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "إجمالي الأصول والثروة",
                            fontSize = 9.5.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1
                        )
                        Text(
                            text = FormatUtils.formatCurrency(totalAssets),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = LuxuryGoldLight,
                            maxLines = 1
                        )
                    }
                }
            }

            // Middle Row: Cash vs Exchange
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A1526).copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(AppScreen.CASH_DRAWER) }
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(16.dp))
                    Column {
                        Text("كاش الصندوق", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                        Text(
                            FormatUtils.formatCurrency(cashBalance),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.16f))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                        .clickable { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                    Column {
                        Text("الصرافة والمحافظ", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                        Text(
                            FormatUtils.formatCurrency(totalExchangeBalance),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom Strip: Inventory | Customer Debts | Supplier Debts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).clickable { onNavigate(AppScreen.INVENTORY) }) {
                    Text("المخزون", fontSize = 9.5.sp, color = Color(0xFF64748B), maxLines = 1)
                    Text(FormatUtils.formatNumber(inventoryValue), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(modifier = Modifier.weight(1f).clickable { onNavigate(AppScreen.DEBTS) }) {
                    Text("عند العملاء (+)", fontSize = 9.5.sp, color = Color(0xFF64748B), maxLines = 1)
                    Text(FormatUtils.formatNumber(totalCustomerDebts), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(modifier = Modifier.weight(1f).clickable { onNavigate(AppScreen.DEBTS) }) {
                    Text("للموردين (-)", fontSize = 9.5.sp, color = Color(0xFF64748B), maxLines = 1)
                    Text(FormatUtils.formatNumber(totalSupplierDebts), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PdfActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    cardBg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(68.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = iconColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun QuickEntityButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PdfMovementRow(movement: CashDrawerMovement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                val (icon, iconColor, iconBg) = when {
                    movement.type.contains("SALE") -> Triple(Icons.Default.ShoppingCart, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    movement.type.contains("PURCHASE") -> Triple(Icons.Default.Inventory2, Color(0xFF2563EB), Color(0xFFDBEAFE))
                    movement.type.contains("EXPENSE") -> Triple(Icons.Default.ReceiptLong, Color(0xFFDC2626), Color(0xFFFEE2E2))
                    movement.isIncome -> Triple(Icons.Default.PriceCheck, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    else -> Triple(Icons.Default.ArrowOutward, Color(0xFFDC2626), Color(0xFFFEE2E2))
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column {
                    Text(
                        text = movement.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatDate(movement.timestamp) + " " + FormatUtils.formatTime(movement.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Text(
                text = (if (movement.isIncome) "+" else "-") + FormatUtils.formatCurrency(movement.amount),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (movement.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                    fontSize = 13.5.sp
                )
            )
        }
    }
}

internal data class StoreAlertItem(
    val id: String,
    val title: String,
    val message: String,
    val icon: ImageVector,
    val accentColor: Color,
    val containerColor: Color,
    val targetScreen: AppScreen
)

internal fun buildStoreAlerts(
    products: List<ProductItem>,
    debts: List<DebtRecord>,
    totalCustomerDebts: Double,
    totalSupplierDebts: Double,
    isPinConfigured: Boolean,
    activeShift: CashDrawerShift?,
    cashBalance: Double,
    nowMillis: Long = System.currentTimeMillis()
): List<StoreAlertItem> {
    val list = mutableListOf<StoreAlertItem>()

    if (!isPinConfigured) {
        list.add(
            StoreAlertItem(
                id = "pin_missing",
                title = "رمز حماية المدير (PIN) غير مضبوط",
                message = "اضبط رمز المدير الآن لحماية الأرصدة الافتتاحية والنسخ الاحتياطي والحذف.",
                icon = Icons.Default.Lock,
                accentColor = FintechRose,
                containerColor = FintechRoseBg,
                targetScreen = AppScreen.SETTINGS
            )
        )
    }

    val outOfStock = products.count { it.quantity <= 0 }
    if (outOfStock > 0) {
        list.add(
            StoreAlertItem(
                id = "out_of_stock",
                title = "أصناف نافدة من المخزن ($outOfStock صنف)",
                message = "يوجد $outOfStock صنف رصيده صفر حالياً — اضغط لمراجعة المخزون وتوريد كميات.",
                icon = Icons.Default.WarningAmber,
                accentColor = FintechRose,
                containerColor = FintechRoseBg,
                targetScreen = AppScreen.INVENTORY
            )
        )
    }

    val lowStock = products.count { it.quantity in 1..3 }
    if (lowStock > 0) {
        list.add(
            StoreAlertItem(
                id = "low_stock",
                title = "أصناف قاربت على النفاد ($lowStock صنف)",
                message = "رصيدها 3 قطع أو أقل بالمخزن — يوصى بإعادة التوريد قريباً.",
                icon = Icons.Default.Inventory2,
                accentColor = FintechAmber,
                containerColor = FintechAmberBg,
                targetScreen = AppScreen.INVENTORY
            )
        )
    }

    val overdueCustomerDebts = debts.filter {
        it.type == "CUSTOMER" && !it.isSettled && it.dueDate > 0L && it.dueDate < nowMillis
    }
    if (overdueCustomerDebts.isNotEmpty()) {
        val overdueSum = overdueCustomerDebts.sumOf { it.remainingAmount }
        list.add(
            StoreAlertItem(
                id = "overdue_debts",
                title = "ديون عملاء حلّ موعد استحقاقها (${overdueCustomerDebts.size})",
                message = "مبالغ متأخرة بقيمة ${FormatUtils.formatCurrency(overdueSum)} تستحق المتابعة والتحصيل.",
                icon = Icons.Default.EventBusy,
                accentColor = FintechRose,
                containerColor = FintechRoseBg,
                targetScreen = AppScreen.DEBTS
            )
        )
    } else if (totalCustomerDebts > 0.01) {
        list.add(
            StoreAlertItem(
                id = "open_customer_debts",
                title = "مبالغ آجلة لنا عند العملاء",
                message = "إجمالي المستحق تحصيله من العملاء: ${FormatUtils.formatCurrency(totalCustomerDebts)}.",
                icon = Icons.Default.People,
                accentColor = PdfGoldDark,
                containerColor = GoldContainer,
                targetScreen = AppScreen.DEBTS
            )
        )
    }

    if (totalSupplierDebts > 0.01) {
        list.add(
            StoreAlertItem(
                id = "open_supplier_debts",
                title = "مستحقات آجلة للموردين",
                message = "إجمالي المطلوب سداده للموردين: ${FormatUtils.formatCurrency(totalSupplierDebts)}.",
                icon = Icons.Default.LocalShipping,
                accentColor = BrandBlue,
                containerColor = BrandBlueBg,
                targetScreen = AppScreen.DEBTS
            )
        )
    }

    if (activeShift != null && (nowMillis - activeShift.openedAt) > 24L * 60L * 60L * 1000L) {
        list.add(
            StoreAlertItem(
                id = "long_shift",
                title = "وردية الصندوق مفتوحة منذ أكثر من 24 ساعة",
                message = "يُفضل جرد وإغلاق الوردية الحالية وبدء وردية جديدة لضبط الحساب اليومي.",
                icon = Icons.Default.Schedule,
                accentColor = FintechAmber,
                containerColor = FintechAmberBg,
                targetScreen = AppScreen.CASH_DRAWER
            )
        )
    }

    if (cashBalance < -0.01) {
        list.add(
            StoreAlertItem(
                id = "negative_cash",
                title = "تنبيه: رصيد الصندوق بالسالب",
                message = "الرصيد الحالي ${FormatUtils.formatCurrency(cashBalance)} — يرجى مراجعة حركات الصندوق والرصيد الافتتاحي.",
                icon = Icons.Default.ErrorOutline,
                accentColor = FintechRose,
                containerColor = FintechRoseBg,
                targetScreen = AppScreen.CASH_DRAWER
            )
        )
    }

    return list
}
