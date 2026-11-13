package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExchangeAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.CollectionReceiptDialog
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.components.SupplierPaymentDialog
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper

data class StatementRow(
    val dateStr: String,
    val description: String,
    val accountName: String,
    val isIncome: Boolean,
    val amount: Double,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashDrawerScreen(
    viewModel: StoreViewModel,
    onNavigate: (com.example.ui.AppScreen) -> Unit = {},
    onBack: () -> Unit
) {
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val totalCustomerDebts by viewModel.totalCustomerDebts.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val exchangeTransfers by viewModel.exchangeTransfers.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val customerParties by viewModel.customerParties.collectAsStateWithLifecycle()
    val supplierParties by viewModel.supplierParties.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showWithdrawalDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showSupplierPaymentDialog by remember { mutableStateOf(false) }

    var selectedPeriod by remember { mutableStateOf("آخر 7 أيام") } // "اليوم", "أمس", "آخر 7 أيام", "هذا الشهر", "الكل"

    val totalTransferBalance = exchangeAccounts.sumOf { it.balance }
    val totalAllBalances = cashBalance + totalTransferBalance

    // Combined Statement rows (كشف الحساب)
    val statementRows = remember(cashMovements, exchangeTransfers, selectedPeriod) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86400000L

        val list = mutableListOf<StatementRow>()

        cashMovements.forEach { m ->
            list.add(
                StatementRow(
                    dateStr = FormatUtils.formatDate(m.timestamp),
                    description = m.title,
                    accountName = "الصندوق",
                    isIncome = m.isIncome,
                    amount = m.amount,
                    timestamp = m.timestamp
                )
            )
        }

        exchangeTransfers.forEach { t ->
            list.add(
                StatementRow(
                    dateStr = FormatUtils.formatDate(t.timestamp),
                    description = t.title,
                    accountName = t.accountName,
                    isIncome = t.isCredit,
                    amount = t.amount,
                    timestamp = t.timestamp
                )
            )
        }

        val filtered = list.filter { r ->
            when (selectedPeriod) {
                "اليوم" -> FormatUtils.isToday(r.timestamp)
                "آخر 7 أيام" -> r.timestamp >= sevenDaysAgo
                "هذا الشهر" -> FormatUtils.isThisMonth(r.timestamp)
                else -> true
            }
        }
        filtered.sortedByDescending { it.timestamp }
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
                            text = "الحسابات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "إدارة أموالك بسهولة وأمان",
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
                                text = storeSettings?.storeName ?: "متجر النور",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PdfGoldPrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "معاً لنمو أكبر",
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
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 600.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // ==========================================
            // 1. The 4 Balance Cards (Page 3 in PDF)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val firstAcc = exchangeAccounts.firstOrNull { it.name.contains("كريمي") } ?: exchangeAccounts.firstOrNull()
                    val secondAcc = exchangeAccounts.firstOrNull { it.name.contains("جوالي") || it.type == "محفظة" } ?: exchangeAccounts.getOrNull(1)

                    val cashCard: @Composable (Modifier) -> Unit = { mod ->
                        PdfAccountBalanceCard(
                            name = "الصندوق",
                            amount = cashBalance,
                            statusText = "متاح نقداً • إيداع/سحب",
                            icon = Icons.Default.Payments,
                            iconColor = Color(0xFF0F766E),
                            iconBg = Color(0xFFCCFBF1),
                            modifier = mod.clickable { showDepositDialog = true }
                        )
                    }
                    val acc1Card: @Composable (Modifier) -> Unit = { mod ->
                        PdfAccountBalanceCard(
                            name = firstAcc?.name ?: "صرافة / حساب 1",
                            amount = firstAcc?.balance ?: 0.0,
                            statusText = "حساب صرافة • إدارة",
                            icon = Icons.Default.AccountBalance,
                            iconColor = Color(0xFF1E3A8A),
                            iconBg = Color(0xFFDBEAFE),
                            modifier = mod.clickable { onNavigate(com.example.ui.AppScreen.EXCHANGE_ACCOUNTS) }
                        )
                    }
                    val acc2Card: @Composable (Modifier) -> Unit = { mod ->
                        PdfAccountBalanceCard(
                            name = secondAcc?.name ?: "محفظة إلكترونية",
                            amount = secondAcc?.balance ?: 0.0,
                            statusText = "محفظة • تحويلات",
                            icon = Icons.Default.PhoneAndroid,
                            iconColor = Color(0xFF0284C7),
                            iconBg = Color(0xFFE0F2FE),
                            modifier = mod.clickable { onNavigate(com.example.ui.AppScreen.EXCHANGE_ACCOUNTS) }
                        )
                    }
                    val debtsCard: @Composable (Modifier) -> Unit = { mod ->
                        PdfAccountBalanceCard(
                            name = "ديون العملاء",
                            amount = totalCustomerDebts,
                            statusText = "مستحقات • تحصيل",
                            icon = Icons.Default.Group,
                            iconColor = Color(0xFF4338CA),
                            iconBg = Color(0xFFEEF2FF),
                            modifier = mod.clickable { onNavigate(com.example.ui.AppScreen.DEBTS) }
                        )
                    }

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            cashCard(Modifier.weight(1f))
                            acc1Card(Modifier.weight(1f))
                            acc2Card(Modifier.weight(1f))
                            debtsCard(Modifier.weight(1f))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            cashCard(Modifier.weight(1f))
                            acc1Card(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            acc2Card(Modifier.weight(1f))
                            debtsCard(Modifier.weight(1f))
                        }
                    }

                    // Quick Management Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(com.example.ui.AppScreen.EXCHANGE_ACCOUNTS) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null, tint = Color(0xFF1E3A8A), modifier = Modifier.size(16.dp))
                                Text("إضافة وإدارة الصرافين والمحافظ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A), maxLines = 1)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigate(com.example.ui.AppScreen.DEBTS) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                                Text("إضافة وإدارة العملاء والموردين", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), maxLines = 1)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. Total Balances Banner (Page 3 in PDF)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total Balance
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PdfHeaderNavy),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = PdfGoldPrimary, modifier = Modifier.size(24.dp))
                            }

                            Column {
                                Text(
                                    text = "إجمالي الأرصدة",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalAllBalances),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        fontSize = 20.sp
                                    )
                                )
                            }
                        }

                        // Split Pills: كاش + حوالة
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("كاش + حوالة", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                                ) {
                                    Text(
                                        text = "${FormatUtils.formatNumber(cashBalance)} كاش",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                ) {
                                    Text(
                                        text = "${FormatUtils.formatNumber(totalTransferBalance)} حوالة",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Drawer Management Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Collection & Transfer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCollectionDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_cash_drawer_collect"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
                        ) {
                            Icon(Icons.Default.PriceCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحصيل زلط (سند قبض)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { showTransferDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_transfer_cash_exchange"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PdfGoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ترحيل صندوق/صراف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Secondary Buttons: تسديد مورد | سحب المالك | إيداع سيولة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showSupplierPaymentDialog = true },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(42.dp)
                                .testTag("btn_cash_drawer_supplier_pay"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FintechRose)
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسديد مورد", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { showWithdrawalDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FintechRose)
                        ) {
                            Icon(Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("سحب المالك", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { showDepositDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إيداع سيولة", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            // ==========================================
            // 3. Section: كشف الحساب (Statement Table) (Page 3)
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
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = "كشف الحساب",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = "جميع الحركة على حساباتك",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Print Daily Movement PDF
                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateDailyMovementPdf(
                                    context = context,
                                    movements = cashMovements,
                                    transfers = exchangeTransfers,
                                    currentCash = cashBalance,
                                    currentExchange = totalTransferBalance,
                                    periodTitle = selectedPeriod,
                                    settings = storeSettings
                                )
                                InvoiceHelper.printPdfFile(context, file, "كشف_حركة_الصندوق_$selectedPeriod")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(18.dp))
                        }

                        // Share Daily Movement PDF
                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateDailyMovementPdf(
                                    context = context,
                                    movements = cashMovements,
                                    transfers = exchangeTransfers,
                                    currentCash = cashBalance,
                                    currentExchange = totalTransferBalance,
                                    periodTitle = selectedPeriod,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة كشف الحركة")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(18.dp))
                        }

                        // Share Cash Movement Text (WhatsApp)
                        IconButton(
                            onClick = {
                                val income = statementRows.filter { it.isIncome }.sumOf { it.amount }
                                val expense = statementRows.filter { !it.isIncome }.sumOf { it.amount }
                                val recent = statementRows.take(15).map { r ->
                                    "${r.dateStr} | ${r.description} (${r.accountName}): ${if (r.isIncome) "+" else "-"}${FormatUtils.formatNumber(r.amount)}"
                                }
                                val txt = StatementHelper.formatCashMovementSummaryText(
                                    periodTitle = selectedPeriod,
                                    cashBalance = cashBalance,
                                    exchangeBalance = totalTransferBalance,
                                    incomeTotal = income,
                                    expenseTotal = expense,
                                    recentLines = recent,
                                    settings = storeSettings
                                )
                                StatementHelper.shareText(context, txt, "مشاركة حركة الصندوق والحسابات")
                            },
                            modifier = Modifier.size(36.dp).testTag("btn_share_cash_drawer_text")
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = "مشاركة واتساب", tint = FintechEmerald, modifier = Modifier.size(18.dp))
                        }

                        // Period Dropdown Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable {
                                selectedPeriod = when (selectedPeriod) {
                                    "آخر 7 أيام" -> "هذا الشهر"
                                    "هذا الشهر" -> "اليوم"
                                    "اليوم" -> "الكل"
                                    else -> "آخر 7 أيام"
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                Text(selectedPeriod, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Table Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("التاريخ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.1f))
                        Text("الوصف", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.6f))
                        Text("الحساب", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.2f))
                        Text("النوع", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.weight(1f))
                        Text("المبلغ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.3f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }

            // Table Rows
            if (statementRows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "لا توجد حركات مسجلة خلال الفترة المحددة.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(statementRows.take(15)) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(row.dateStr, fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1.1f))
                            Text(row.description, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.6f))
                            Text(row.accountName, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.2f))

                            // Type Pill
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (row.isIncome) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (row.isIncome) "مقبوضات" else "مصروفات",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }

                            // Amount
                            Text(
                                text = (if (row.isIncome) "+ " else "- ") + FormatUtils.formatNumber(row.amount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (row.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.weight(1.3f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
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

    // Owner Withdrawal Dialog
    if (showWithdrawalDialog) {
        var withdrawAmountStr by remember { mutableStateOf("") }
        var withdrawReason by remember { mutableStateOf("سحب شخصي للمالك") }

        AlertDialog(
            onDismissRequest = { showWithdrawalDialog = false },
            title = { Text("توثيق سحب المالك من الصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("الرصيد المتاح بالصندوق حالياً: ${FormatUtils.formatCurrency(cashBalance)}")
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
                                showWithdrawalDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRose)
                ) {
                    Text("تأكيد السحب")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWithdrawalDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Deposit Dialog
    if (showDepositDialog) {
        var depositAmountStr by remember { mutableStateOf("") }
        var depositReason by remember { mutableStateOf("إيداع رأس مال / تغذية الصندوق") }

        AlertDialog(
            onDismissRequest = { showDepositDialog = false },
            title = { Text("إيداع سيولة جديدة في الصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

    // Transfer Between Cash Drawer and Exchange Dialog
    if (showTransferDialog) {
        var fromCashToExchange by remember { mutableStateOf(true) }
        var selectedAcc by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
        var transferAmountStr by remember { mutableStateOf("") }
        var transferRefNumber by remember { mutableStateOf("") }
        var transferNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PdfHeaderNavy)
                    Text("ترحيل سيولة بين الصندوق والصراف", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Direction Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = fromCashToExchange,
                            onClick = { fromCashToExchange = true },
                            label = { Text("من الصندوق ➔ للصراف", fontSize = 11.sp, fontWeight = if (fromCashToExchange) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !fromCashToExchange,
                            onClick = { fromCashToExchange = false },
                            label = { Text("من الصراف ➔ للصندوق", fontSize = 11.sp, fontWeight = if (!fromCashToExchange) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Available balance display
                    val availableSource = if (fromCashToExchange) cashBalance else (selectedAcc?.balance ?: 0.0)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (fromCashToExchange) "الرصيد المتاح بالصندوق:" else "الرصيد المتاح بالحساب:", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                            Text(FormatUtils.formatCurrency(availableSource), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        }
                    }

                    // Exchange account selector
                    if (exchangeAccounts.isEmpty()) {
                        Text("لا توجد حسابات صرافة أو محافظ مسجلة! يرجى إضافة حساب صراف أولاً.", color = Color(0xFFDC2626), fontSize = 11.sp)
                    } else {
                        Text("اختر حساب الصراف أو المحفظة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(exchangeAccounts) { acc ->
                                val isSel = selectedAcc?.id == acc.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedAcc = acc },
                                    label = { Text("${acc.name} (${FormatUtils.formatNumber(acc.balance)})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = transferAmountStr,
                        onValueChange = { transferAmountStr = it },
                        label = { Text("المبلغ المحول (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick buttons
                    QuickThousandAdder(
                        currentValue = transferAmountStr,
                        onValueChange = { transferAmountStr = it }
                    )

                    OutlinedTextField(
                        value = transferRefNumber,
                        onValueChange = { transferRefNumber = it },
                        label = { Text("رقم الحوالة / سند التحويل (اختياري)") },
                        placeholder = { Text("مثال: 940281") },
                        leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfHeaderNavy, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth().testTag("cash_transfer_ref_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = transferNotes,
                        onValueChange = { transferNotes = it },
                        label = { Text("ملاحظات إضافية (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = transferAmountStr.toDoubleOrNull() ?: 0.0
                        val acc = selectedAcc
                        if (acc == null) {
                            viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة!")
                            return@Button
                        }
                        if (amt <= 0) {
                            viewModel.showMessage("يرجى إدخال مبلغ تحويل صحيح أكبر من صفر!")
                            return@Button
                        }
                        val available = if (fromCashToExchange) cashBalance else acc.balance
                        if (amt > available) {
                            viewModel.showMessage("المبلغ يتجاوز الرصيد المتوفر في جهة الإرسال (${FormatUtils.formatCurrency(available)})!")
                            return@Button
                        }

                        viewModel.transferBetweenCashAndExchange(
                            accountId = acc.id,
                            amount = amt,
                            fromCashToExchange = fromCashToExchange,
                            notes = transferNotes.ifBlank { if (fromCashToExchange) "إيداع نقدي في ${acc.name}" else "سحب نقدي من ${acc.name}" },
                            referenceNumber = transferRefNumber.trim()
                        ) {
                            showTransferDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                ) {
                    Text("تأكيد الترحيل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTransferDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showCollectionDialog) {
        CollectionReceiptDialog(
            customerParties = customerParties,
            customerDebts = debts,
            exchangeAccounts = exchangeAccounts,
            onDismiss = { showCollectionDialog = false },
            onConfirm = { payerName, party, amount, voucher, method, account, notes ->
                viewModel.recordCollectionReceipt(
                    payerName = payerName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucher,
                    paymentMethod = method,
                    exchangeAccount = account,
                    notes = notes
                ) {
                    showCollectionDialog = false
                }
            }
        )
    }

    if (showSupplierPaymentDialog) {
        SupplierPaymentDialog(
            supplierParties = supplierParties,
            supplierDebts = debts,
            exchangeAccounts = exchangeAccounts,
            onDismiss = { showSupplierPaymentDialog = false },
            onConfirm = { supplierName, party, amount, voucher, method, account, notes ->
                viewModel.recordSupplierPaymentReceipt(
                    supplierName = supplierName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucher,
                    paymentMethod = method,
                    exchangeAccount = account,
                    notes = notes
                ) {
                    showSupplierPaymentDialog = false
                }
            }
        )
    }
}

// ==========================================
// Account Balance Card (Top 4 Grid in Page 3)
// ==========================================
@Composable
fun PdfAccountBalanceCard(
    name: String,
    amount: Double,
    statusText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(115.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }

            Column {
                Text(
                    text = FormatUtils.formatCurrency(amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
