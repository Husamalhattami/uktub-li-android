package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CashDrawerMovement
import com.example.data.model.Employee
import com.example.data.model.ExchangeAccount
import com.example.data.model.PartyAccount
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.StoreTopBar
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

    // Accounting Fix: Actual liquidity is cash + exchange/wallet balances only (excluding unsold inventory)
    val actualLiquidity = cashBalance + totalExchangeBalance
    // Total Assets = actual liquidity + inventory + customer debts - supplier debts
    val totalAssets = actualLiquidity + inventoryValue + totalCustomerDebts - totalSupplierDebts

    var showQuickAddSheet by remember { mutableStateOf(false) }
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
            StoreTopBar(
                title = storeSettings?.storeName ?: "حسابي للمحل",
                subtitle = "إدارة أسهل .. لمحل أكثر نجاحاً",
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { /* Notification modal or shortcut */ },
                            modifier = Modifier.testTag("btn_notifications_topbar")
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "الإشعارات",
                                    tint = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(PdfGoldPrimary)
                                        .align(Alignment.TopEnd)
                                )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. Hero Liquidity Card (Page 1 in PDF)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PdfHeaderNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A63))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0F1E36),
                                        Color(0xFF162947)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: السيولة المتاحة فعلياً + Chart Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "السيولة النقدية المتاحة فعلياً",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "كاش الصندوق + أرصدة الصرافة والمحافظ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF90CDF4),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF90CDF4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Large Number: 125,750 ريال
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = FormatUtils.formatNumber(actualLiquidity),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 32.sp
                                    )
                                )
                                Text(
                                    text = storeSettings?.currencySymbol ?: "ريال",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF90CDF4),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Bottom Two Blocks: رصيد الصندوق | أرصدة الصرافة
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0A1526).copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Right: رصيد الصندوق
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onNavigate(AppScreen.CASH_DRAWER) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0D9488).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFF2DD4BF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "كاش الصندوق",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(cashBalance),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(30.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                // Left: أرصدة الصرافة والمحافظ
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                        .clickable { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "أرصدة الصرافة والمحافظ",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(totalExchangeBalance),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Total Assets Card (قيمة المخزون + الديون)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF0F1E36), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "إجمالي الأصول وصافي الثروة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F1E36)
                                )
                            }
                            Text(
                                text = FormatUtils.formatCurrency(totalAssets),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F1E36)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Inventory Value
                            Column(modifier = Modifier.clickable { onNavigate(AppScreen.INVENTORY) }) {
                                Text("قيمة المخزون", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(FormatUtils.formatCurrency(inventoryValue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            // Customer Debts
                            Column(modifier = Modifier.clickable { onNavigate(AppScreen.DEBTS) }) {
                                Text("ديون الزبائن (+)", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(FormatUtils.formatCurrency(totalCustomerDebts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            }
                            // Supplier Debts
                            Column(modifier = Modifier.clickable { onNavigate(AppScreen.DEBTS) }) {
                                Text("ديون الموردين (-)", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(FormatUtils.formatCurrency(totalSupplierDebts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. The 4 Fast Action Cards (Page 1 in PDF)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1: شراء بضاعة | بيع جديد
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PdfActionCard(
                            title = "شراء بضاعة",
                            subtitle = "تسجيل فاتورة شراء",
                            icon = Icons.Default.AddShoppingCart,
                            iconColor = Color(0xFF0F766E),
                            iconBg = Color(0xFFCCFBF1),
                            cardBg = PdfTealCard,
                            borderColor = Color(0xFF99F6E4),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_purchase"),
                            onClick = { onNavigate(AppScreen.PURCHASE) }
                        )

                        PdfActionCard(
                            title = "بيع جديد",
                            subtitle = "إصدار فاتورة بيع",
                            icon = Icons.Default.AddShoppingCart,
                            iconColor = Color(0xFF15803D),
                            iconBg = Color(0xFFDCFCE7),
                            cardBg = PdfMintCard,
                            borderColor = Color(0xFFBBF7D0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_sale"),
                            onClick = { onNavigate(AppScreen.SALE) }
                        )
                    }

                    // Row 2: تحصيل دين | تسجيل مصروف
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PdfActionCard(
                            title = "تحصيل دين",
                            subtitle = "استلام مبلغ من عميل",
                            icon = Icons.Default.PriceCheck,
                            iconColor = Color(0xFF166534),
                            iconBg = Color(0xFFDCFCE7),
                            cardBg = PdfGreenCard,
                            borderColor = Color(0xFFBBF7D0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_debt"),
                            onClick = { onNavigate(AppScreen.DEBTS) }
                        )

                        PdfActionCard(
                            title = "تسجيل مصروف",
                            subtitle = "إضافة مصروف جديد",
                            icon = Icons.Default.ReceiptLong,
                            iconColor = Color(0xFF991B1B),
                            iconBg = Color(0xFFFEE2E2),
                            cardBg = PdfRoseCard,
                            borderColor = Color(0xFFFECACA),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_expense"),
                            onClick = { onNavigate(AppScreen.EXPENSES) }
                        )
                    }
                }
            }

            // ==========================================
            // 2.5 Section: إدارة الحسابات والأطراف (العملاء، الموردين، الصرافين، العمال)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "إدارة الحسابات والأطراف",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "فتح حسابات ومتابعة",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Row 1: العملاء والموردين | الصرافين والمحافظ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickEntityButton(
                            title = "العملاء والموردين",
                            subtitle = "إضافة عميل ومورد • ديون",
                            icon = Icons.Default.People,
                            color = Color(0xFF1E3A8A),
                            bg = Color(0xFFEFF6FF),
                            border = Color(0xFFBFDBFE),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_quick_parties"),
                            onClick = { onNavigate(AppScreen.DEBTS) }
                        )

                        QuickEntityButton(
                            title = "الصرافين والمحافظ",
                            subtitle = "إضافة صراف ومحفظة",
                            icon = Icons.Default.AccountBalance,
                            color = Color(0xFF0F766E),
                            bg = Color(0xFFF0FDFA),
                            border = Color(0xFF99F6E4),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_quick_exchange"),
                            onClick = { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                        )
                    }

                    // Row 2: العمال والرواتب | إدارة الصندوق
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickEntityButton(
                            title = "العمال والرواتب",
                            subtitle = "إضافة عامل • سلف ورواتب",
                            icon = Icons.Default.Badge,
                            color = Color(0xFFB45309),
                            bg = Color(0xFFFFFBEB),
                            border = Color(0xFFFDE68A),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_quick_employees"),
                            onClick = { onNavigate(AppScreen.EMPLOYEES) }
                        )

                        QuickEntityButton(
                            title = "إدارة الصندوق",
                            subtitle = "سحب وإيداع • جرد السيولة",
                            icon = Icons.Default.AccountBalanceWallet,
                            color = Color(0xFF15803D),
                            bg = Color(0xFFF0FDF4),
                            border = Color(0xFFBBF7D0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_quick_drawer"),
                            onClick = { onNavigate(AppScreen.CASH_DRAWER) }
                        )
                    }
                }
            }

            // ==========================================
            // 3. Section: آخر الحركات (Page 1 in PDF)
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "آخر الحركات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 16.sp
                            )
                        )
                    }

                    Text(
                        text = "عرض الكل  ›",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clickable { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
            }

            // Real Movement List
            if (cashMovements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = "لا توجد حركات مالية مسجلة بعد.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(cashMovements.take(6), key = { it.id }) { movement ->
                    PdfMovementRow(movement = movement)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // --- Dialog 1: Add Customer or Supplier ---
        showAddPartyDialog?.let { partyType ->
            AddOrEditPartyDialog(
                initialType = partyType,
                existingParty = null,
                onDismiss = { showAddPartyDialog = null },
                onSave = { party, initialDebt, debtNotes ->
                    viewModel.saveParty(
                        party = party,
                        initialDebt = initialDebt,
                        debtNotes = debtNotes,
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

            AlertDialog(
                onDismissRequest = { showAddExchangeDialog = false },
                title = { Text("إضافة حساب صراف أو محفظة إلكترونية", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = accType == "صرافة",
                                onClick = { accType = "صرافة" },
                                label = { Text("شركة / محل صرافة") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = accType == "محفظة",
                                onClick = { accType = "محفظة" },
                                label = { Text("محفظة إلكترونية") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = accName,
                            onValueChange = { accName = it },
                            label = { Text("اسم الصراف أو المحفظة (مثلاً: صرافة الكريمي، جوالي) *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = accNumber,
                            onValueChange = { accNumber = it },
                            label = { Text("رقم الحساب أو رقم المحفظة (اختياري)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = balanceStr,
                            onValueChange = { balanceStr = it },
                            label = { Text("الرصيد المتاح حالياً (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
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
                        Text("حفظ الحساب")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddExchangeDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // --- Dialog 3: Add Employee ---
        if (showAddEmployeeDialog) {
            var empName by remember { mutableStateOf("") }
            var empPhone by remember { mutableStateOf("") }
            var empRole by remember { mutableStateOf("بائع") }
            var empSalaryStr by remember { mutableStateOf("3000") }

            AlertDialog(
                onDismissRequest = { showAddEmployeeDialog = false },
                title = { Text("إضافة عامل / موظف جديد للمحل", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = empName,
                            onValueChange = { empName = it },
                            label = { Text("اسم العامل الكامل *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = empPhone,
                            onValueChange = { empPhone = it },
                            label = { Text("رقم الهاتف للتواصل") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = empRole,
                            onValueChange = { empRole = it },
                            label = { Text("المسمى الوظيفي (بائع، خياط، كاشير...)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = empSalaryStr,
                            onValueChange = { empSalaryStr = it },
                            label = { Text("الراتب الشهري (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
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
                        Text("حفظ العامل")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showAddEmployeeDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // --- Dialog 4: Deposit Dialog ---
        if (showDepositDialog) {
            var depositAmountStr by remember { mutableStateOf("") }
            var depositReason by remember { mutableStateOf("إيداع رأس مال / تغذية الصندوق") }

            AlertDialog(
                onDismissRequest = { showDepositDialog = false },
                title = { Text("إيداع سيولة جديدة في الصندوق", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = depositAmountStr,
                            onValueChange = { depositAmountStr = it },
                            label = { Text("مبلغ الإيداع (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = depositReason,
                            onValueChange = { depositReason = it },
                            label = { Text("مصدر السيولة / ملاحظات") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = depositAmountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) {
                                viewModel.showMessage("يرجى إدخال مبلغ إيداع صحيح!")
                            } else {
                                viewModel.recordManualDeposit(amt, depositReason) {
                                    showDepositDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
                    ) {
                        Text("تأكيد الإيداع")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDepositDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // --- Dialog 5: Withdrawal Dialog ---
        if (showWithdrawDialog) {
            var withdrawAmountStr by remember { mutableStateOf("") }
            var withdrawReason by remember { mutableStateOf("مسحوبات شخصية للمالك") }

            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = { Text("سحب شخصي للمالك من الصندوق", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = withdrawAmountStr,
                            onValueChange = { withdrawAmountStr = it },
                            label = { Text("المبلغ المسحوب (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = withdrawReason,
                            onValueChange = { withdrawReason = it },
                            label = { Text("سبب السحب / ملاحظات") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = withdrawAmountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0 || amt > cashBalance) {
                                viewModel.showMessage("المبلغ المسحوب غير صحيح أو يتجاوز رصيد الصندوق!")
                            } else {
                                viewModel.recordOwnerWithdrawal(amt, withdrawReason) {
                                    showWithdrawDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FintechRose)
                    ) {
                        Text("تأكيد السحب")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showWithdrawDialog = false }) { Text("إلغاء") }
                }
            )
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
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
            .height(115.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = iconColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            .height(68.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 12.sp
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
        }
    }
}

@Composable
fun PdfMovementRow(movement: CashDrawerMovement) {
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circle icon
                val (icon, iconColor, iconBg) = when {
                    movement.type.contains("SALE") -> Triple(Icons.Default.ShoppingCart, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    movement.type.contains("PURCHASE") -> Triple(Icons.Default.Inventory2, Color(0xFF2563EB), Color(0xFFDBEAFE))
                    movement.type.contains("EXPENSE") -> Triple(Icons.Default.ReceiptLong, Color(0xFFDC2626), Color(0xFFFEE2E2))
                    movement.isIncome -> Triple(Icons.Default.PriceCheck, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    else -> Triple(Icons.Default.ArrowOutward, Color(0xFFDC2626), Color(0xFFFEE2E2))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = movement.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatDate(movement.timestamp) + " " + FormatUtils.formatTime(movement.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Text(
                text = (if (movement.isIncome) "+" else "-") + FormatUtils.formatCurrency(movement.amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (movement.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                    fontSize = 15.sp
                )
            )
        }
    }
}
