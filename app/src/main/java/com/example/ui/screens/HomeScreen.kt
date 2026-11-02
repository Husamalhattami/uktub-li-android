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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    val inventoryValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val totalCustDebts by viewModel.totalCustomerDebts.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "حسابي للمحل",
                activeShift = activeShift,
                onShiftClick = { onNavigate(AppScreen.CASH_DRAWER) }
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
            // Section 1: Welcome & Store Identity Banner
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
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "محل الأناقة للملابس الرجالية",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "اليوم: ${FormatUtils.formatDate(System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }
                            // Quick sale floating trigger button
                            Button(
                                onClick = { onNavigate(AppScreen.SALE) },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryGoldLight),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hero_quick_sale_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = null,
                                    tint = MidnightNavy
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "بيع سريع",
                                    fontWeight = FontWeight.Bold,
                                    color = MidnightNavy
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Drawer & Today metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "رصيد الصندوق الحالي",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(cashBalance),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "مبيعات اليوم",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(todaySales),
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

            // Section 3: 8 Main Action Cards (Grid)
            item {
                Text(
                    text = "أقسام المحل",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Grid Row 1: بيع + شراء بضاعة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainActionCard(
                        title = "1. بيع",
                        subtitle = "تسجيل بيع فوري ونقدي أو دين",
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
                        subtitle = "الرواتب، السلف، العمولات",
                        icon = Icons.Default.Badge,
                        accentColor = Color(0xFF6366F1),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_employees_card"),
                        onClick = { onNavigate(AppScreen.EMPLOYEES) }
                    )
                    MainActionCard(
                        title = "6. الديون",
                        subtitle = "ديون الزبائن والموردين",
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
                        onClick = { onNavigate(AppScreen.CASH_DRAWER) }
                    ) {
                        Text("عرض الكل", style = MaterialTheme.typography.labelLarge)
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
                            .size(42.dp)
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
                            color = badgeColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
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
                        maxLines = 1
                    )
                }
            }
        }
    }
}

