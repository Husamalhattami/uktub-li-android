package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CashDrawerMovement
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.MovementItemRow
import com.example.ui.components.StatCard
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun HomeScreen(
    viewModel: StoreViewModel,
    onNavigate: (AppScreen) -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySalesTotal.collectAsStateWithLifecycle()
    val todayProfit by viewModel.todayProfitTotal.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val totalCustDebts by viewModel.totalCustomerDebts.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val accounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "حسابي للمحل",
                activeShift = activeShift,
                onShiftClick = { onNavigate(AppScreen.CASH_DRAWER) },
                trailingAction = {
                    IconButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier.testTag("btn_clear_data_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "تصفير وحذف البيانات",
                            tint = Color.White
                        )
                    }
                }
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
            // Section 1: Welcome & Store Identity Banner (Fixed layout: zero squishing, generous padding)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp)
                            ) {
                                Text(
                                    text = "محل الأناقة للملابس الرجالية",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "اليوم: ${FormatUtils.formatDate(System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }

                            // Quick sale button - fixed width and no line wrap
                            Button(
                                onClick = { onNavigate(AppScreen.SALE) },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryGoldLight),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 110.dp)
                                    .testTag("hero_quick_sale_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = null,
                                    tint = MidnightNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "بيع سريع",
                                    fontWeight = FontWeight.Bold,
                                    color = MidnightNavy,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary Drawer & Today metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "رصيد الصندوق الحالي (كاش)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(cashBalance),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "أرصدة الصرافين والمحافظ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalExchangeBalance),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = LuxuryGoldLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Quick Stats 2x2
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "أرباح مبيعات اليوم",
                        value = FormatUtils.formatCurrency(todayProfit),
                        icon = Icons.Default.TrendingUp,
                        containerColor = EmeraldLight,
                        contentColor = EmeraldProfit,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "قيمة المخزون (التكلفة)",
                        value = FormatUtils.formatCurrency(inventoryValue),
                        icon = Icons.Default.Inventory2,
                        containerColor = GoldContainer,
                        contentColor = LuxuryGold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section 3: Main Action Cards (10 Cards Grid)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أقسام المحل",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    TextButton(onClick = { showClearDataDialog = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = RubyExpense)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تصفير البيانات", color = RubyExpense, fontSize = 12.sp)
                    }
                }
            }

            // Grid Row 1: بيع + شراء بضاعة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "1. بيع",
                        subtitle = "كاش، تحويل، نص كاش، آجل",
                        icon = Icons.Default.ShoppingCartCheckout,
                        accentColor = EmeraldProfit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_sale_card"),
                        onClick = { onNavigate(AppScreen.SALE) }
                    )
                    MainActionCard(
                        title = "2. شراء بضاعة",
                        subtitle = "إضافة مشتريات للمخزون",
                        icon = Icons.Default.AddBusiness,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_purchase_card"),
                        onClick = { onNavigate(AppScreen.PURCHASE) }
                    )
                }
            }

            // Grid Row 2: المخزون + المصروفات
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "3. المخزون",
                        subtitle = "القطع والكميات والأسعار",
                        badge = if (lowStockCount > 0) "$lowStockCount قارب على النفاد" else null,
                        badgeColor = RubyExpense,
                        icon = Icons.Default.Checkroom,
                        accentColor = LuxuryGold,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_inventory_card"),
                        onClick = { onNavigate(AppScreen.INVENTORY) }
                    )
                    MainActionCard(
                        title = "4. المصروفات",
                        subtitle = "إيجار، كهرباء، شحن، تصليح",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = RubyExpense,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_expenses_card"),
                        onClick = { onNavigate(AppScreen.EXPENSES) }
                    )
                }
            }

            // Grid Row 3: العمال والرواتب + الديون
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "5. العمال والرواتب",
                        subtitle = "تعديل، حذف، سلف، رواتب",
                        icon = Icons.Default.Badge,
                        accentColor = Color(0xFF6366F1),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_employees_card"),
                        onClick = { onNavigate(AppScreen.EMPLOYEES) }
                    )
                    MainActionCard(
                        title = "6. الحسابات والديون",
                        subtitle = "الزبائن (عليهم) والموردين (عليا)",
                        badge = if (totalCustDebts > 0) FormatUtils.formatCurrency(totalCustDebts) else null,
                        badgeColor = RubyExpense,
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Color(0xFFD97706),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_debts_card"),
                        onClick = { onNavigate(AppScreen.DEBTS) }
                    )
                }
            }

            // Grid Row 4: التقارير + الصندوق
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "7. التقارير",
                        subtitle = "صافي الأرباح والإحصائيات",
                        icon = Icons.Default.Assessment,
                        accentColor = Color(0xFF0284C7),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_reports_card"),
                        onClick = { onNavigate(AppScreen.REPORTS) }
                    )
                    MainActionCard(
                        title = "8. الصندوق",
                        subtitle = "فتح وإغلاق الوردية والحركات",
                        icon = Icons.Default.Savings,
                        accentColor = EmeraldProfit,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_cash_drawer_card"),
                        onClick = { onNavigate(AppScreen.CASH_DRAWER) }
                    )
                }
            }

            // Grid Row 5: الصرافين والمحافظ + سجل وتعديل العمليات
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "9. الصرافين والمحافظ",
                        subtitle = "الكريمي، النجم، العمقي...",
                        badge = if (accounts.isNotEmpty()) "${accounts.size} حسابات" else null,
                        badgeColor = Color(0xFF0D9488),
                        icon = Icons.Default.AccountBalance,
                        accentColor = Color(0xFF0D9488),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_exchange_card"),
                        onClick = { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                    )
                    MainActionCard(
                        title = "سجل العمليات",
                        subtitle = "حذف وتعديل المبيعات والمشتريات",
                        icon = Icons.Default.HistoryEdu,
                        accentColor = Color(0xFF8B5CF6),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_transactions_log_card"),
                        onClick = { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                    )
                }
            }

            // Section: Store Inventory & Balance Audit (جرد المحل وبداية النظام من الصفر)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nav_store_audit_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.4f)),
                    onClick = { onNavigate(AppScreen.STORE_AUDIT) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(LuxuryGold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = LuxuryGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "جرد المحل وبدء النظام من الصفر",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = LuxuryGold
                                    ) {
                                        Text(
                                            text = "جرد شامل",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                                Text(
                                    text = "جرد الكاش، أرصدة الصرافين، المخزون، وتصفير الحركات القديمة",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = SlateMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Section 4: Daily Ledger / Recent Activity (دفتر اليومية المباشر)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دفتر اليومية المباشر (آخر الحركات)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    TextButton(
                        onClick = { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                    ) {
                        Text("إدارة العمليات", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (cashMovements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "لا توجد حركات مسجلة بعد. ابدأ بتسجيل بيع أو شراء بضاعة.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(cashMovements.take(8)) { movement ->
                    MovementItemRow(movement)
                }
            }
        }
    }

    // Dialog: Reset / Clear Demo Data
    if (showClearDataDialog) {
        var clearOption by remember { mutableStateOf(1) } // 1: Transactions only, 2: Everything completely

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RubyExpense) },
            title = { Text("تصفير وحذف البيانات السابقة", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اختر ما تريد حذفه وتصفيره للبدء في استخدام التطبيق:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearOption = 1 },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (clearOption == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (clearOption == 1) MaterialTheme.colorScheme.primary else SlateBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. تصفير الحركات والفواتير فقط",
                                fontWeight = FontWeight.Bold,
                                color = if (clearOption == 1) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "حذف جميع المبيعات، المشتريات، المصروفات، حركات الصندوق والديون مع الإبقاء على الأصناف والعمال.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (clearOption == 1) Color.White.copy(alpha = 0.8f) else SlateMuted
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearOption = 2 },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (clearOption == 2) RubyExpense.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (clearOption == 2) RubyExpense else SlateBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. حذف شامل لجميع البيانات (بدء من الصفر)",
                                fontWeight = FontWeight.Bold,
                                color = RubyExpense
                            )
                            Text(
                                text = "حذف كل شيء بالكامل: الأصناف، المخزون، العمال، الحسابات، والفواتير للحصول على تطبيق فارغ تماماً.",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearData(transactionsOnly = (clearOption == 1)) {
                            showClearDataDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_clear_data_button")
                ) {
                    Text("تنفيذ الحذف والتصفير")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun MainActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = RubyExpense,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(135.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SlateMuted,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
