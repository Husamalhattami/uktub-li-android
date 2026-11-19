package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.StoreViewModel
import com.example.ui.components.StatCard
import com.example.ui.components.StatementDateRangeBar
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper

@Composable
fun ReportsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val returns by viewModel.returns.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val employeeTransactions by viewModel.employeeTransactions.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val debtPayments by viewModel.debtPayments.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPeriod by remember { mutableStateOf("اليوم") } // "اليوم", "أمس", "هذا الأسبوع", "هذا الشهر", "الكل", "فترة مخصصة"
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }

    val periodRange = remember(selectedPeriod, customStartMillis, customEndMillis) {
        val todayStart = FormatUtils.todayStartMillis()
        val tomorrowStart = FormatUtils.tomorrowStartMillis()
        when (selectedPeriod) {
            "اليوم" -> todayStart to tomorrowStart
            "أمس" -> {
                val yesterdayStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = todayStart
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }.timeInMillis
                yesterdayStart to todayStart
            }
            "هذا الأسبوع" -> (System.currentTimeMillis() - 7L * 86_400_000L) to Long.MAX_VALUE
            "هذا الشهر" -> {
                val mStart = FormatUtils.monthStartMillis()
                val mEnd = java.util.Calendar.getInstance().apply {
                    timeInMillis = mStart
                    add(java.util.Calendar.MONTH, 1)
                }.timeInMillis
                mStart to mEnd
            }
            "فترة مخصصة" -> {
                val s = customStartMillis ?: Long.MIN_VALUE
                val e = customEndMillis?.plus(1L) ?: Long.MAX_VALUE
                s to e
            }
            else -> Long.MIN_VALUE to Long.MAX_VALUE
        }
    }

    val effectivePeriodLabel = remember(selectedPeriod, customStartMillis, customEndMillis) {
        if (selectedPeriod == "فترة مخصصة") {
            FormatUtils.formatDateRangeLabel(customStartMillis, customEndMillis)
        } else {
            selectedPeriod
        }
    }

    fun matchesSelectedPeriod(timestamp: Long): Boolean {
        return timestamp >= periodRange.first && timestamp < periodRange.second
    }

    val filteredSales = remember(sales, periodRange) { sales.filter { matchesSelectedPeriod(it.timestamp) } }
    val filteredReturns = remember(returns, periodRange) { returns.filter { matchesSelectedPeriod(it.timestamp) } }
    val filteredPurchases = remember(purchases, periodRange) { purchases.filter { matchesSelectedPeriod(it.timestamp) } }
    val filteredExpenses = remember(expenses, periodRange) { expenses.filter { matchesSelectedPeriod(it.timestamp) } }
    val filteredEmployeeTx = remember(employeeTransactions, periodRange) { employeeTransactions.filter { matchesSelectedPeriod(it.timestamp) } }
    val filteredPayments = remember(debtPayments, periodRange) { debtPayments.filter { matchesSelectedPeriod(it.timestamp) } }

    val reportSummary = remember(
        effectivePeriodLabel,
        filteredSales,
        filteredReturns,
        filteredPurchases,
        filteredExpenses,
        filteredEmployeeTx,
        filteredPayments,
        debts,
        products,
        cashBalance,
        totalExchangeBalance
    ) {
        StatementHelper.buildDailyReportSummary(
            periodTitle = effectivePeriodLabel,
            filteredSales = filteredSales,
            filteredPurchases = filteredPurchases,
            filteredExpenses = filteredExpenses,
            filteredEmployeeTx = filteredEmployeeTx,
            filteredPayments = filteredPayments,
            allDebts = debts,
            allProducts = products,
            currentCashBalance = cashBalance,
            currentExchangeBalance = totalExchangeBalance,
            filteredReturns = filteredReturns
        )
    }

    // Calculations
    val totalSales = reportSummary.totalSales
    val cashSales = reportSummary.cashSales
    val debtSales = reportSummary.debtSales
    val cogs = reportSummary.cogs
    val totalExpensesAmount = reportSummary.expensesTotal
    val totalSalariesAmount = reportSummary.salariesAndAdvancesTotal
    val netProfit = reportSummary.netProfit
    val customerDebtsTotal = reportSummary.totalCustomerDebts
    val supplierDebtsTotal = reportSummary.totalSupplierDebts

    // Item profit breakdown
    val itemProfitList = remember(filteredSales) {
        filteredSales.groupBy { it.productName }
            .map { (name, list) ->
                val qty = list.sumOf { it.quantity }
                val prof = list.sumOf { it.profit }
                Triple(name, qty, prof)
            }
            .sortedByDescending { it.third }
    }

    // Employee sales breakdown
    val employeeSalesList = remember(filteredSales) {
        filteredSales.groupBy { it.employeeName.ifBlank { "صاحب المحل" } }
            .map { (empName, list) ->
                val total = list.sumOf { it.totalAmount }
                val profit = list.sumOf { it.profit }
                Triple(empName, total, profit)
            }
            .sortedByDescending { it.second }
    }

    fun shareReportText() {
        val text = StatementHelper.formatDailyReportText(reportSummary, storeSettings)
        StatementHelper.shareText(context, text, "مشاركة التقرير المحاسبي")
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "التقارير وصافي الأرباح",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack,
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateFinancialReportPdf(
                                    context = context,
                                    report = reportSummary,
                                    topItems = itemProfitList,
                                    employeeSales = employeeSalesList,
                                    settings = storeSettings
                                )
                                InvoiceHelper.printPdfFile(context, file, "تقرير_مالي_$selectedPeriod")
                            },
                            modifier = Modifier.testTag("btn_print_report_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "طباعة تقرير PDF",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val file = InvoiceHelper.generateFinancialReportPdf(
                                    context = context,
                                    report = reportSummary,
                                    topItems = itemProfitList,
                                    employeeSales = employeeSalesList,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة تقرير مالي")
                            },
                            modifier = Modifier.testTag("btn_share_report_pdf")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة PDF",
                                tint = Color.White
                            )
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
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideLayout = maxWidth >= 700.dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Period selector
            item(key = "reports_period_chips") {
                val periods = remember { listOf("اليوم", "أمس", "هذا الأسبوع", "هذا الشهر", "الكل", "فترة مخصصة") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(periods, key = { it }) { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("report_period_$period")
                            )
                        }
                    }
                    if (selectedPeriod == "فترة مخصصة") {
                        StatementDateRangeBar(
                            startMillis = customStartMillis,
                            endMillis = customEndMillis,
                            onRangeChange = { s, e ->
                                customStartMillis = s
                                customEndMillis = e
                            },
                            modifier = Modifier.testTag("reports_custom_date_range")
                        )
                    }
                }
            }

            // Export & Share Action Bar (PDF + WhatsApp + Copy)
            item(key = "reports_export_bar") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "تصدير ومشاركة تقرير ($effectivePeriodLabel)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PdfHeaderNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val file = InvoiceHelper.generateFinancialReportPdf(
                                        context = context,
                                        report = reportSummary,
                                        topItems = itemProfitList,
                                        employeeSales = employeeSalesList,
                                        settings = storeSettings
                                    )
                                    InvoiceHelper.printPdfFile(context, file, "تقرير_مالي_$selectedPeriod")
                                },
                                modifier = Modifier.weight(1f).height(42.dp).testTag("btn_report_print_pdf"),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("طباعة PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val file = InvoiceHelper.generateFinancialReportPdf(
                                        context = context,
                                        report = reportSummary,
                                        topItems = itemProfitList,
                                        employeeSales = employeeSalesList,
                                        settings = storeSettings
                                    )
                                    InvoiceHelper.sharePdfFile(context, file, "مشاركة تقرير مالي")
                                },
                                modifier = Modifier.weight(1f).height(42.dp).testTag("btn_report_share_pdf"),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { shareReportText() },
                                modifier = Modifier.weight(1f).height(42.dp).testTag("btn_report_share_text"),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val txt = StatementHelper.formatDailyReportText(reportSummary, storeSettings)
                                    StatementHelper.copyToClipboard(context, txt, "تقرير $selectedPeriod")
                                },
                                modifier = Modifier.weight(1f).height(42.dp).testTag("btn_report_copy_text"),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ التقرير", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Core Accounting Equation Hero Banner
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
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "معادلة صافي الربح ($selectedPeriod)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = LuxuryGoldLight,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "صافي الربح = المبيعات - تكلفة البضاعة المباعة - المصروفات - الرواتب",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f))
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        EquationLine(label = "صافي المبيعات (بعد المرتجعات)", value = FormatUtils.formatCurrency(totalSales), isPositive = true)
                        if (reportSummary.salesReturnsTotal > 0.01) {
                            EquationLine(label = "  • مرتجعات المبيعات (${reportSummary.salesReturnsCount} مستند)", value = "- " + FormatUtils.formatCurrency(reportSummary.salesReturnsTotal), isPositive = false)
                        }
                        if (reportSummary.purchaseReturnsTotal > 0.01) {
                            EquationLine(label = "  • مرتجعات المشتريات (${reportSummary.purchaseReturnsCount} مستند)", value = FormatUtils.formatCurrency(reportSummary.purchaseReturnsTotal), isPositive = true)
                        }
                        EquationLine(label = "- تكلفة البضاعة المباعة (رأس المال)", value = "- " + FormatUtils.formatCurrency(cogs), isPositive = false)
                        EquationLine(label = "- إجمالي المصروفات التشغيلية", value = "- " + FormatUtils.formatCurrency(totalExpensesAmount), isPositive = false)
                        EquationLine(label = "- الرواتب والسلف المدفوعة", value = "- " + FormatUtils.formatCurrency(totalSalariesAmount), isPositive = false)

                        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "صافي الربح الحقيقي:",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = FormatUtils.formatCurrency(netProfit),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (netProfit >= 0) EmeraldProfit else RubyExpense
                                )
                            )
                        }
                    }
                }
            }

            // Collections & Supplier Payments Overview Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "تحصيلات العملاء ($selectedPeriod)",
                        value = FormatUtils.formatCurrency(reportSummary.collectionsTotal),
                        icon = Icons.Default.CallReceived,
                        containerColor = EmeraldLight,
                        contentColor = EmeraldProfit,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "سداد الموردين ($selectedPeriod)",
                        value = FormatUtils.formatCurrency(reportSummary.supplierPaymentsTotal),
                        icon = Icons.Default.CallMade,
                        containerColor = RubyLight,
                        contentColor = RubyExpense,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Sales & Debts Overview Cards (4 in row on wide, 2x2 on narrow)
            if (isWideLayout) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "مبيعات نقدية",
                            value = FormatUtils.formatCurrency(cashSales),
                            icon = Icons.Default.Payments,
                            containerColor = EmeraldLight,
                            contentColor = EmeraldProfit,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "مبيعات بالدين (آجل)",
                            value = FormatUtils.formatCurrency(debtSales),
                            icon = Icons.Default.CreditCard,
                            containerColor = GoldContainer,
                            contentColor = LuxuryGold,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "ديون الزبائن المستحقة",
                            value = FormatUtils.formatCurrency(customerDebtsTotal),
                            icon = Icons.Default.AccountBalanceWallet,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = LuxuryGold,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "ديون الموردين المطلوب سدادها",
                            value = FormatUtils.formatCurrency(supplierDebtsTotal),
                            icon = Icons.Default.LocalShipping,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = RubyExpense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "مبيعات نقدية",
                            value = FormatUtils.formatCurrency(cashSales),
                            icon = Icons.Default.Payments,
                            containerColor = EmeraldLight,
                            contentColor = EmeraldProfit,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "مبيعات بالدين (آجل)",
                            value = FormatUtils.formatCurrency(debtSales),
                            icon = Icons.Default.CreditCard,
                            containerColor = GoldContainer,
                            contentColor = LuxuryGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "ديون الزبائن المستحقة",
                            value = FormatUtils.formatCurrency(customerDebtsTotal),
                            icon = Icons.Default.AccountBalanceWallet,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = LuxuryGold,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "ديون الموردين المطلوب سدادها",
                            value = FormatUtils.formatCurrency(supplierDebtsTotal),
                            icon = Icons.Default.LocalShipping,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = RubyExpense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Item Profitability Breakdown
            item {
                Text(
                    text = "أرباح ومبيعات كل صنف ($selectedPeriod)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (itemProfitList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد مبيعات في الفترة المحددة.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(itemProfitList, key = { it.first }) { (name, qty, prof) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = name, fontWeight = FontWeight.Bold)
                                Text(text = "تم بيع $qty قطع", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "ربح محقق", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                                Text(
                                    text = "+ " + FormatUtils.formatCurrency(prof),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit)
                                )
                            }
                        }
                    }
                }
            }

            // Employee Performance Breakdown
            item {
                Text(
                    text = "أداء ومبيعات كل بائع ($selectedPeriod)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (employeeSalesList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد مبيعات مسجلة للبائعين في هذه الفترة.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(employeeSalesList, key = { it.first }) { (empName, total, profit) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = empName, fontWeight = FontWeight.Bold)
                                Text(text = "إجمالي المبيعات: ${FormatUtils.formatCurrency(total)}", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "أرباح المحل من مبيعاته", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                                Text(
                                    text = "+ " + FormatUtils.formatCurrency(profit),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EquationLine(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.85f))
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color.White else RubyExpense
            )
        )
    }
}
