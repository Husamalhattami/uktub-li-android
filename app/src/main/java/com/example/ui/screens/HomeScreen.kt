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
    val totalSuppDebts by viewModel.totalSupplierDebts.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = storeSettings?.storeName ?: "حسابي للمحل",
                activeShift = activeShift,
                onShiftClick = { onNavigate(AppScreen.CASH_DRAWER) },
                trailingAction = {
                    Row {
                        IconButton(
                            onClick = { onNavigate(AppScreen.SETTINGS) },
                            modifier = Modifier.testTag("btn_settings_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات والنسخ الاحتياطي",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.testTag("btn_clear_data_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "جرد وتصفير",
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
                .background(SlateBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. Hero Financial Overview Card
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Column {
                                Text(
                                    text = storeSettings?.storeName ?: "محل الأناقة للملابس الرجالية",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "اليوم: ${FormatUtils.formatDate(System.currentTimeMillis())}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            // Shift Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (activeShift?.isOpen == true) FintechEmerald.copy(alpha = 0.2f) else FintechRose.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (activeShift?.isOpen == true) FintechEmeraldLight else FintechRoseLight
                                ),
                                modifier = Modifier.clickable { onNavigate(AppScreen.CASH_DRAWER) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (activeShift?.isOpen == true) FintechEmeraldLight else FintechRoseLight)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (activeShift?.isOpen == true) "وردية اليوم مفتوحة" else "الوردية مغلقة",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Cash Drawer & Exchange Balances
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate(AppScreen.CASH_DRAWER) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = FintechEmeraldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "نقدية الصندوق (الكاش)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(cashBalance),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                                    .clickable { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) },
                                horizontalAlignment = Alignment.End
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = FintechAmberLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "أرصدة الصرافين والمحافظ",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatCurrency(totalExchangeBalance),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = FintechAmberLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Today's Sales & Profits Row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PointOfSale, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مبيعات اليوم: ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                    Text(FormatUtils.formatCurrency(todaySales), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = FintechEmeraldLight, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الأرباح: ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                    Text("+ " + FormatUtils.formatCurrency(todayProfit), color = FintechEmeraldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. Primary 4 Fast Everyday Actions (2x2 Grid)
            // ==========================================
            item {
                Text(
                    text = "العمليات الأساسية السريعة",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FastActionCard(
                        title = "بيع سريع",
                        subtitle = "كاش، تحويل، أو دين",
                        icon = Icons.Default.ShoppingCart,
                        accentColor = FintechEmerald,
                        bgColor = FintechEmeraldBg,
                        borderColor = FintechEmeraldBorder,
                        modifier = Modifier.weight(1f).testTag("quick_action_sale"),
                        onClick = { onNavigate(AppScreen.SALE) }
                    )
                    FastActionCard(
                        title = "شراء بضاعة",
                        subtitle = "إضافة قطع ومشتريات",
                        icon = Icons.Default.AddBusiness,
                        accentColor = BrandBlue,
                        bgColor = BrandBlueBg,
                        borderColor = BrandBlue.copy(alpha = 0.25f),
                        modifier = Modifier.weight(1f).testTag("quick_action_purchase"),
                        onClick = { onNavigate(AppScreen.PURCHASE) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FastActionCard(
                        title = "الديون والحسابات",
                        subtitle = "الزبائن والموردين",
                        badge = if (totalCustDebts > 0) "لنا: ${FormatUtils.formatCurrency(totalCustDebts)}" else null,
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = FintechAmber,
                        bgColor = FintechAmberBg,
                        borderColor = FintechAmberBorder,
                        modifier = Modifier.weight(1f).testTag("quick_action_debts"),
                        onClick = { onNavigate(AppScreen.DEBTS) }
                    )
                    FastActionCard(
                        title = "المصروفات",
                        subtitle = "إيجار، كهرباء، شحن",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = FintechRose,
                        bgColor = FintechRoseBg,
                        borderColor = FintechRoseBorder,
                        modifier = Modifier.weight(1f).testTag("quick_action_expenses"),
                        onClick = { onNavigate(AppScreen.EXPENSES) }
                    )
                }
            }

            // ==========================================
            // 3. Store Management Modules (أقسام المحل)
            // ==========================================
            item {
                Text(
                    text = "أقسام إدارة المحل",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                )
            }

            // Module Row 1: المخزون + الصندوق
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        title = "المخزون والأصناف",
                        subtitle = "القطع والكميات والأسعار",
                        badge = if (lowStockCount > 0) "$lowStockCount قارب على النفاد!" else null,
                        badgeColor = FintechRose,
                        icon = Icons.Default.Checkroom,
                        accentColor = FintechIndigo,
                        modifier = Modifier.weight(1f).testTag("module_inventory"),
                        onClick = { onNavigate(AppScreen.INVENTORY) }
                    )
                    ModuleCard(
                        title = "الصندوق والورديات",
                        subtitle = "حركات الكاش والإغلاق",
                        badge = if (activeShift?.isOpen == true) "وردية مفتوحة" else "مغلقة",
                        badgeColor = if (activeShift?.isOpen == true) FintechEmerald else FintechRose,
                        icon = Icons.Default.Savings,
                        accentColor = FintechEmerald,
                        modifier = Modifier.weight(1f).testTag("module_cash_drawer"),
                        onClick = { onNavigate(AppScreen.CASH_DRAWER) }
                    )
                }
            }

            // Module Row 2: الصرافين + العمال
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        title = "الصرافين والمحافظ",
                        subtitle = "الكريمي، جوالي، تحويلات",
                        icon = Icons.Default.AccountBalance,
                        accentColor = FintechTeal,
                        modifier = Modifier.weight(1f).testTag("module_exchange"),
                        onClick = { onNavigate(AppScreen.EXCHANGE_ACCOUNTS) }
                    )
                    ModuleCard(
                        title = "العمال والرواتب",
                        subtitle = "السلف، الرواتب، العمولات",
                        icon = Icons.Default.Badge,
                        accentColor = BrandBlue,
                        modifier = Modifier.weight(1f).testTag("module_employees"),
                        onClick = { onNavigate(AppScreen.EMPLOYEES) }
                    )
                }
            }

            // Module Row 3: التقارير + الجرد الشامل
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCard(
                        title = "التقارير والأرباح",
                        subtitle = "الأرباح، المبيعات، الذمم",
                        icon = Icons.Default.BarChart,
                        accentColor = FintechAmber,
                        modifier = Modifier.weight(1f).testTag("module_reports"),
                        onClick = { onNavigate(AppScreen.REPORTS) }
                    )
                    ModuleCard(
                        title = "جرد المحل العام",
                        subtitle = "مطابقة النقدية والبدء",
                        icon = Icons.Default.FactCheck,
                        accentColor = BrandNavyLight,
                        modifier = Modifier.weight(1f).testTag("module_audit"),
                        onClick = { onNavigate(AppScreen.STORE_AUDIT) }
                    )
                }
            }

            // ==========================================
            // 4. Live Recent Transactions Section
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "آخر العمليات والحركات",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                    )
                    TextButton(onClick = { onNavigate(AppScreen.TRANSACTIONS_LOG) }) {
                        Text("عرض السجل الشامل", fontSize = 12.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val recentMovements = cashMovements.take(5)
            if (recentMovements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                    ) {
                        Text(
                            text = "لا توجد عمليات مسجلة بعد في الوردية الحالية.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextMuted)
                        )
                    }
                }
            } else {
                items(recentMovements, key = { it.id }) { movement ->
                    MovementItemRow(movement = movement)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Clear Data / Factory Reset Confirmation Dialog
    if (showClearDataDialog) {
        var wipeWordInput by remember { mutableStateOf("") }
        var showFullWipeConfirm by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showClearDataDialog = false
                showFullWipeConfirm = false
                wipeWordInput = ""
            },
            title = {
                Text(
                    text = if (showFullWipeConfirm) "تأكيد حذف جميع البيانات" else "خيارات الجرد وإعادة ضبط النظام",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!showFullWipeConfirm) {
                        Text(
                            text = "لإدارة حسابات محلك بأمان دون فقدان بياناتك بدون قصد، يرجى اختيار الإجراء المناسب:",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        // 1. Recommended: Store Audit Wizard
                        Button(
                            onClick = {
                                showClearDataDialog = false
                                onNavigate(AppScreen.STORE_AUDIT)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("معالج جرد المحل الفعلي والأرصدة (موصى به)")
                        }

                        // 2. Backup data first
                        OutlinedButton(
                            onClick = {
                                showClearDataDialog = false
                                onNavigate(AppScreen.SETTINGS)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ وتصدير نسخة احتياطية أولاً")
                        }

                        // 3. Clear only operations
                        OutlinedButton(
                            onClick = {
                                viewModel.clearData(transactionsOnly = true) {
                                    showClearDataDialog = false
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FintechAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصفير حركات البيع والشراء (مع بقاء البضاعة)")
                        }

                        // 4. Wipe everything trigger
                        OutlinedButton(
                            onClick = { showFullWipeConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FintechRose),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حذف كل شيء بالكامل (تصفير شامل)")
                        }
                    } else {
                        Surface(
                            color = FintechRose.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "تحذير شديد: سيتم مسح جميع الأصناف، الفواتير، الديون، وحسابات الصرافين نهائياً ولا يمكن التراجع!",
                                color = FintechRose,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Text(
                            text = "للتأكيد، يرجى كتابة كلمة «حذف» في المربع أدناه:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = FintechRose)
                        )

                        OutlinedTextField(
                            value = wipeWordInput,
                            onValueChange = { wipeWordInput = it },
                            placeholder = { Text("اكتب حذف") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_wipe_confirm_input")
                        )
                    }
                }
            },
            confirmButton = {
                if (showFullWipeConfirm) {
                    val canWipe = wipeWordInput.trim() == "حذف"
                    Button(
                        onClick = {
                            viewModel.clearData(transactionsOnly = false) {
                                showClearDataDialog = false
                                showFullWipeConfirm = false
                                wipeWordInput = ""
                            }
                        },
                        enabled = canWipe,
                        colors = ButtonDefaults.buttonColors(containerColor = FintechRose),
                        modifier = Modifier.testTag("btn_confirm_full_wipe")
                    ) {
                        Text("نعم، احذف كل شيء")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (showFullWipeConfirm) {
                            showFullWipeConfirm = false
                            wipeWordInput = ""
                        } else {
                            showClearDataDialog = false
                        }
                    }
                ) {
                    Text(if (showFullWipeConfirm) "رجوع" else "إلغاء")
                }
            }
        )
    }
}

// ==========================================
// Sub-Components
// ==========================================

@Composable
fun FastActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
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
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bgColor
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SlateTextSecondary,
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
fun ModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = FintechAmber,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    ),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SlateTextSecondary,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
