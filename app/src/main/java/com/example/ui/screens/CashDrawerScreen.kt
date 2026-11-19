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
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.DialogSectionHeader
import com.example.ui.components.DialogSegmentedChoice
import com.example.ui.components.StatementDateRangeBar
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.SupplierPaymentDialog
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper

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

    var selectedPeriod by remember { mutableStateOf("آخر 7 أيام") } // "اليوم", "آخر 7 أيام", "هذا الشهر", "الكل", "فترة مخصصة"
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    var showPeriodMenu by remember { mutableStateOf(false) }

    val totalTransferBalance = remember(exchangeAccounts) { exchangeAccounts.sumOf { it.balance } }
    val totalAllBalances = cashBalance + totalTransferBalance

    val effectiveRange = remember(selectedPeriod, customStartMillis, customEndMillis) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = FormatUtils.startOfDayMillis(now - 6L * 86_400_000L)
        val todayStart = FormatUtils.todayStartMillis()
        val tomorrowEnd = FormatUtils.tomorrowStartMillis() - 1L
        val monthStart = FormatUtils.monthStartMillis()
        val nextMonthEnd = java.util.Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(java.util.Calendar.MONTH, 1)
        }.timeInMillis - 1L
        when (selectedPeriod) {
            "اليوم" -> todayStart to tomorrowEnd
            "آخر 7 أيام" -> sevenDaysAgo to tomorrowEnd
            "هذا الشهر" -> monthStart to nextMonthEnd
            "فترة مخصصة" -> customStartMillis to customEndMillis
            else -> null to null
        }
    }

    val effectivePeriodLabel = remember(selectedPeriod, customStartMillis, customEndMillis) {
        if (selectedPeriod == "فترة مخصصة") {
            FormatUtils.formatDateRangeLabel(customStartMillis, customEndMillis)
        } else {
            selectedPeriod
        }
    }

    val ledgerSummary = remember(
        cashMovements,
        exchangeTransfers,
        cashBalance,
        totalTransferBalance,
        effectiveRange,
        effectivePeriodLabel
    ) {
        StatementHelper.buildCashDrawerLedger(
            movements = cashMovements,
            transfers = exchangeTransfers,
            currentCashBalance = cashBalance,
            currentExchangeBalance = totalTransferBalance,
            startMillis = effectiveRange.first,
            endMillis = effectiveRange.second,
            periodTitle = effectivePeriodLabel
        )
    }

    // Display newest first in UI while preserving each row's exact chronological runningBalance
    val displayLedgerRows = remember(ledgerSummary) { ledgerSummary.rows.asReversed() }

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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Print Daily Movement PDF (A4 8-Column RTL + ملخص الحساب)
                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateDailyMovementPdf(
                                    context = context,
                                    summary = ledgerSummary,
                                    settings = storeSettings
                                )
                                InvoiceHelper.printPdfFile(context, file, "كشف_حركة_الصندوق_${effectivePeriodLabel}")
                            },
                            modifier = Modifier.size(48.dp).testTag("btn_print_cash_drawer_pdf")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(20.dp))
                        }

                        // Share Daily Movement PDF
                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateDailyMovementPdf(
                                    context = context,
                                    summary = ledgerSummary,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة كشف الحركة")
                            },
                            modifier = Modifier.size(48.dp).testTag("btn_share_cash_drawer_pdf")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة PDF", tint = PdfHeaderNavy, modifier = Modifier.size(20.dp))
                        }

                        // Share Cash Movement Text (WhatsApp)
                        IconButton(
                            onClick = {
                                val recent = displayLedgerRows.take(15).map { r ->
                                    val amtPrefix = if (r.isIncome) "+${FormatUtils.formatNumber(r.creditReceived)}" else "-${FormatUtils.formatNumber(r.debitPaid)}"
                                    "${FormatUtils.formatDate(r.timestamp)} | ${r.title} (${r.accountLabel}): $amtPrefix | رصيد: ${FormatUtils.formatNumber(r.runningBalance)}"
                                }
                                val txt = StatementHelper.formatCashMovementSummaryText(
                                    periodTitle = effectivePeriodLabel,
                                    cashBalance = cashBalance,
                                    exchangeBalance = totalTransferBalance,
                                    incomeTotal = ledgerSummary.totalCreditReceived,
                                    expenseTotal = ledgerSummary.totalDebitPaid,
                                    recentLines = recent,
                                    settings = storeSettings
                                )
                                StatementHelper.shareText(context, txt, "مشاركة حركة الصندوق والحسابات")
                            },
                            modifier = Modifier.size(48.dp).testTag("btn_share_cash_drawer_text")
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = "مشاركة واتساب", tint = FintechEmerald, modifier = Modifier.size(20.dp))
                        }

                        // Period Dropdown Selector
                        Box {
                            Surface(
                                onClick = { showPeriodMenu = true },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_cash_drawer_period_picker")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                    Text(selectedPeriod, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = showPeriodMenu,
                                onDismissRequest = { showPeriodMenu = false }
                            ) {
                                listOf("اليوم", "آخر 7 أيام", "هذا الشهر", "فترة مخصصة", "الكل").forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                fontWeight = if (selectedPeriod == option) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedPeriod == option) PdfHeaderNavy else SlateTextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedPeriod = option
                                            showPeriodMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPeriod == "فترة مخصصة") {
                item(key = "cash_custom_date_range") {
                    StatementDateRangeBar(
                        startMillis = customStartMillis,
                        endMillis = customEndMillis,
                        onRangeChange = { s, e ->
                            customStartMillis = s
                            customEndMillis = e
                        },
                        modifier = Modifier.testTag("cash_drawer_date_range")
                    )
                }
            }

            // Period Totals Summary Strip (ملخص الحساب السريع)
            item(key = "cash_period_summary_strip") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("دائن قبض (+)", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                            Text(
                                text = FormatUtils.formatNumber(ledgerSummary.totalCreditReceived),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("مدين دفع (-)", fontSize = 10.sp, color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                            Text(
                                text = FormatUtils.formatNumber(ledgerSummary.totalDebitPaid),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الرصيد الختامي", fontSize = 10.sp, color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold)
                            Text(
                                text = FormatUtils.formatNumber(ledgerSummary.closingBalance),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                }
            }

            // Table Header
            item(key = "cash_table_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("التاريخ", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.0f))
                        Text("البيان / الحساب", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.8f))
                        Text("قبض / دفع", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("الرصيد", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.weight(1.1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }

            // Table Rows
            if (displayLedgerRows.isEmpty()) {
                item(key = "cash_empty_state") {
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
                items(displayLedgerRows.take(25), key = { it.rowKey }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = FormatUtils.formatDate(row.timestamp),
                                fontSize = 9.5.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.weight(1.0f)
                            )
                            Column(modifier = Modifier.weight(1.8f)) {
                                Text(
                                    text = row.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${row.accountLabel} • ${row.kindLabel}",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (row.isIncome) "+${FormatUtils.formatNumber(row.creditReceived)}" else "-${FormatUtils.formatNumber(row.debitPaid)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (row.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = FormatUtils.formatNumber(row.runningBalance),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1.1f)
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
        var withdrawTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        StoreOperationDialog(
            onDismissRequest = { showWithdrawalDialog = false },
            title = "توثيق سحب المالك من الصندوق",
            subtitle = "صرف نقدي شخصي من رصيد الصندوق",
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
                        showWithdrawalDialog = false
                    }
                }
            }
        ) {
            DialogInfoBanner(
                title = "الرصيد المتاح بالصندوق حالياً:",
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
            val wAmt = withdrawAmountStr.toDoubleOrNull() ?: 0.0
            if (wAmt > 0.0) {
                val cashAfter = cashBalance - wAmt
                val ok = cashAfter >= -0.001
                DialogInfoBanner(
                    title = "رصيد الصندوق بعد السحب:",
                    value = FormatUtils.formatCurrency(cashAfter),
                    containerColor = if (ok) Color(0xFFFFF7ED) else Color(0xFFFFF1F2),
                    contentColor = if (ok) Color(0xFFB45309) else FintechRose,
                    borderColor = if (ok) Color(0xFFFED7AA) else Color(0xFFFECDD3),
                    icon = if (ok) Icons.Default.Calculate else Icons.Default.Warning
                )
            }
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
                testTag = "cash_withdraw_date_picker"
            )
        }
    }

    // Deposit Dialog
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
                title = "الرصيد الحالي بالصندوق:",
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
            val dAmt = depositAmountStr.toDoubleOrNull() ?: 0.0
            if (dAmt > 0.0) {
                DialogInfoBanner(
                    title = "رصيد الصندوق بعد الإيداع:",
                    value = FormatUtils.formatCurrency(cashBalance + dAmt),
                    containerColor = FintechEmeraldBg,
                    contentColor = FintechEmerald,
                    borderColor = FintechEmerald.copy(alpha = 0.35f),
                    icon = Icons.Default.Calculate
                )
            }
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
                testTag = "cash_deposit_date_picker"
            )
        }
    }

    // Transfer Between Cash Drawer and Exchange Dialog
    if (showTransferDialog) {
        var fromCashToExchange by remember { mutableStateOf(true) }
        var selectedAcc by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
        var transferAmountStr by remember { mutableStateOf("") }
        var transferRefNumber by remember { mutableStateOf("") }
        var transferNotes by remember { mutableStateOf("") }
        var transferTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        StoreOperationDialog(
            onDismissRequest = { showTransferDialog = false },
            title = "ترحيل سيولة بين الصندوق والصراف",
            subtitle = "تحويل رصيد بين النقدي والحسابات البنكية",
            icon = Icons.Default.SwapHoriz,
            accentColor = PdfGoldPrimary,
            confirmText = "تأكيد الترحيل",
            confirmIcon = Icons.Default.SwapHoriz,
            confirmColor = PdfHeaderNavy,
            onConfirm = {
                val amt = transferAmountStr.toDoubleOrNull() ?: 0.0
                val acc = selectedAcc
                if (acc == null) {
                    viewModel.showMessage("يرجى اختيار حساب الصراف أو المحفظة!")
                } else if (amt <= 0) {
                    viewModel.showMessage("يرجى إدخال مبلغ تحويل صحيح أكبر من صفر!")
                } else {
                    val available = if (fromCashToExchange) cashBalance else acc.balance
                    if (amt > available) {
                        viewModel.showMessage("المبلغ يتجاوز الرصيد المتوفر في جهة الإرسال (${FormatUtils.formatCurrency(available)})!")
                    } else {
                        viewModel.transferBetweenCashAndExchange(
                            accountId = acc.id,
                            amount = amt,
                            fromCashToExchange = fromCashToExchange,
                            notes = transferNotes.ifBlank { if (fromCashToExchange) "إيداع نقدي في ${acc.name}" else "سحب نقدي من ${acc.name}" },
                            referenceNumber = transferRefNumber.trim(),
                            timestamp = transferTimestamp
                        ) {
                            showTransferDialog = false
                        }
                    }
                }
            }
        ) {
            DialogSectionHeader(title = "اتجاه الترحيل", icon = Icons.Default.SwapHoriz, accentColor = PdfHeaderNavy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = fromCashToExchange,
                    label = "من الصندوق للصراف",
                    icon = Icons.Default.NorthEast,
                    selectedColor = PdfHeaderNavy,
                    onClick = { fromCashToExchange = true },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = !fromCashToExchange,
                    label = "من الصراف للصندوق",
                    icon = Icons.Default.SouthWest,
                    selectedColor = FintechEmerald,
                    onClick = { fromCashToExchange = false },
                    modifier = Modifier.weight(1f)
                )
            }

            val availableSource = if (fromCashToExchange) cashBalance else (selectedAcc?.balance ?: 0.0)
            DialogInfoBanner(
                title = if (fromCashToExchange) "الرصيد المتاح بالصندوق:" else "الرصيد المتاح بالحساب:",
                value = FormatUtils.formatCurrency(availableSource),
                containerColor = Color(0xFFEFF6FF),
                contentColor = Color(0xFF1E3A8A),
                borderColor = Color(0xFFBFDBFE),
                icon = Icons.Default.AccountBalance
            )

            if (exchangeAccounts.isEmpty()) {
                Text("لا توجد حسابات صرافة أو محافظ مسجلة! يرجى إضافة حساب صراف أولاً.", color = Color(0xFFDC2626), fontSize = 11.sp)
            } else {
                DialogSectionHeader(title = "حساب الصراف أو المحفظة", icon = Icons.Default.AccountBalance, accentColor = FintechIndigo)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(exchangeAccounts) { acc ->
                        val isSel = selectedAcc?.id == acc.id
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedAcc = acc },
                            label = { Text("${acc.name} (${FormatUtils.formatNumber(acc.balance)})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PdfHeaderNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = transferAmountStr,
                onValueChange = { transferAmountStr = it },
                label = { Text("المبلغ المحول (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfHeaderNavy) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val tAmt = transferAmountStr.toDoubleOrNull() ?: 0.0
            val selAcc = selectedAcc
            if (tAmt > 0.0 && selAcc != null) {
                val cashAfter = if (fromCashToExchange) cashBalance - tAmt else cashBalance + tAmt
                val accAfter = if (fromCashToExchange) selAcc.balance + tAmt else selAcc.balance - tAmt
                val ok = cashAfter >= -0.001 && accAfter >= -0.001
                DialogInfoBanner(
                    title = "الأرصدة بعد الترحيل:",
                    value = "الصندوق: ${FormatUtils.formatCurrency(cashAfter)} | ${selAcc.name}: ${FormatUtils.formatCurrency(accAfter)}",
                    containerColor = if (ok) FintechEmeraldBg else Color(0xFFFFF1F2),
                    contentColor = if (ok) FintechEmerald else FintechRose,
                    borderColor = if (ok) FintechEmerald.copy(alpha = 0.35f) else Color(0xFFFECDD3),
                    icon = if (ok) Icons.Default.Calculate else Icons.Default.Warning
                )
            }

            OutlinedTextField(
                value = transferRefNumber,
                onValueChange = { transferRefNumber = it },
                label = { Text("رقم الحوالة / سند التحويل (اختياري)") },
                placeholder = { Text("مثال: 940281") },
                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfHeaderNavy, modifier = Modifier.size(18.dp)) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("cash_transfer_ref_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = transferNotes,
                onValueChange = { transferNotes = it },
                label = { Text("ملاحظات إضافية (اختياري)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            TransactionDatePickerField(
                selectedTimestamp = transferTimestamp,
                onDateSelected = { transferTimestamp = it },
                label = "تاريخ الترحيل / العملية",
                testTag = "cash_transfer_date_picker"
            )
        }
    }

    if (showCollectionDialog) {
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
