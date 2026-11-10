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
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper

@Composable
fun ReportsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val employeeTransactions by viewModel.employeeTransactions.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPeriod by remember { mutableStateOf("اليوم") } // "اليوم", "أمس", "هذا الأسبوع", "هذا الشهر", "الكل"

    val isYesterday: (Long) -> Boolean = remember {
        { ts ->
            val calNow = java.util.Calendar.getInstance()
            calNow.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val calTarget = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            calNow.get(java.util.Calendar.YEAR) == calTarget.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == calTarget.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }

    val filteredSales = remember(sales, selectedPeriod) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86400000L
        sales.filter {
            when (selectedPeriod) {
                "اليوم" -> FormatUtils.isToday(it.timestamp)
                "أمس" -> isYesterday(it.timestamp)
                "هذا الأسبوع" -> it.timestamp >= sevenDaysAgo
                "هذا الشهر" -> FormatUtils.isThisMonth(it.timestamp)
                else -> true
            }
        }
    }

    val filteredExpenses = remember(expenses, selectedPeriod) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86400000L
        expenses.filter {
            when (selectedPeriod) {
                "اليوم" -> FormatUtils.isToday(it.timestamp)
                "أمس" -> isYesterday(it.timestamp)
                "هذا الأسبوع" -> it.timestamp >= sevenDaysAgo
                "هذا الشهر" -> FormatUtils.isThisMonth(it.timestamp)
                else -> true
            }
        }
    }

    val filteredSalaries = remember(employeeTransactions, selectedPeriod) {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 86400000L
        employeeTransactions.filter { (it.type == "صرف راتب" || it.type == "سلفة") }.filter {
            when (selectedPeriod) {
                "اليوم" -> FormatUtils.isToday(it.timestamp)
                "أمس" -> isYesterday(it.timestamp)
                "هذا الأسبوع" -> it.timestamp >= sevenDaysAgo
                "هذا الشهر" -> FormatUtils.isThisMonth(it.timestamp)
                else -> true
            }
        }
    }

    // Calculations
    val totalSales = remember(filteredSales) { filteredSales.sumOf { it.totalAmount } }
    val cashSales = remember(filteredSales) { filteredSales.sumOf { it.cashAmount } }
    val transferSales = remember(filteredSales) { filteredSales.sumOf { it.transferAmount } }
    val debtSales = remember(filteredSales) { filteredSales.filter { it.paymentType.contains("دين") || it.paymentType.contains("آجل") }.sumOf { it.totalAmount } }

    val cogs = remember(filteredSales) { filteredSales.sumOf { it.unitBuyPrice * it.quantity } }
    val grossProfit = totalSales - cogs

    val totalExpensesAmount = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val totalSalariesAmount = remember(filteredSalaries) { filteredSalaries.sumOf { it.amount } }

    // Real Net Profit Equation: Sales - COGS - Expenses - Salaries
    val netProfit = grossProfit - totalExpensesAmount - totalSalariesAmount

    // Inventory Value
    val inventoryCapital = remember(products) { products.sumOf { it.buyPrice * it.quantity } }

    // Outstanding Debts
    val customerDebtsTotal = remember(debts) { debts.filter { it.type == "CUSTOMER" && !it.isSettled }.sumOf { it.remainingAmount } }
    val supplierDebtsTotal = remember(debts) { debts.filter { it.type == "SUPPLIER" && !it.isSettled }.sumOf { it.remainingAmount } }

    // Item profit breakdown
    val itemProfitList = remember(filteredSales) {
        filteredSales.groupBy { it.productName }
            .map { (name, list) ->
                val qty = list.sumOf { it.quantity }
                val rev = list.sumOf { it.totalAmount }
                val prof = list.sumOf { it.profit }
                Triple(name, qty, prof)
            }
            .sortedByDescending { it.third }
    }

    // Employee sales breakdown
    val employeeSalesList = remember(filteredSales) {
        filteredSales.groupBy { it.employeeName.ifBlank { "صاحب المحل" } }
            .map { (empName, list) ->
                val count = list.size
                val total = list.sumOf { it.totalAmount }
                val profit = list.sumOf { it.profit }
                Triple(empName, total, profit)
            }
            .sortedByDescending { it.second }
    }

    fun shareReportText() {
        val storeTitle = storeSettings?.storeName ?: "الجرادي للملابس الجاهزة"
        val report = buildString {
            appendLine("📊 تقرير أرباح ومبيعات: $storeTitle")
            appendLine("الفترة: $selectedPeriod (${FormatUtils.formatDate(System.currentTimeMillis())})")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("• إجمالي المبيعات: ${FormatUtils.formatCurrency(totalSales)}")
            appendLine("  - نقداً (كاش): ${FormatUtils.formatCurrency(cashSales)}")
            appendLine("  - تحويل صرافة: ${FormatUtils.formatCurrency(transferSales)}")
            appendLine("  - آجل (بالدين): ${FormatUtils.formatCurrency(debtSales)}")
            appendLine("• تكلفة البضاعة المباعة: ${FormatUtils.formatCurrency(cogs)}")
            appendLine("• مجمل الربح التجاري: ${FormatUtils.formatCurrency(grossProfit)}")
            appendLine("• المصروفات التشغيلية: ${FormatUtils.formatCurrency(totalExpensesAmount)}")
            appendLine("• الرواتب والسلف: ${FormatUtils.formatCurrency(totalSalariesAmount)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🌟 صافي الربح الحقيقي: ${FormatUtils.formatCurrency(netProfit)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📦 قيمة المخزون الحالي: ${FormatUtils.formatCurrency(inventoryCapital)}")
            appendLine("💰 ديون الزبائن المستحقة: ${FormatUtils.formatCurrency(customerDebtsTotal)}")
            appendLine("🚚 ديون الموردين المطلوب سدادها: ${FormatUtils.formatCurrency(supplierDebtsTotal)}")
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "تقرير مالي - $storeTitle")
            putExtra(android.content.Intent.EXTRA_TEXT, report)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة التقرير المحاسبي"))
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
                                    periodTitle = selectedPeriod,
                                    sales = filteredSales,
                                    expenses = filteredExpenses,
                                    employeeTx = filteredSalaries,
                                    purchases = purchases,
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
                                    periodTitle = selectedPeriod,
                                    sales = filteredSales,
                                    expenses = filteredExpenses,
                                    employeeTx = filteredSalaries,
                                    purchases = purchases,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Period selector
            item {
                val periods = listOf("اليوم", "أمس", "هذا الأسبوع", "هذا الشهر", "الكل")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(periods) { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = { Text(period, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("report_period_$period")
                        )
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

                        EquationLine(label = "إجمالي المبيعات", value = FormatUtils.formatCurrency(totalSales), isPositive = true)
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

            // Sales breakdown (Cash vs Debt)
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

            // Debts Overview
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
                items(itemProfitList) { (name, qty, prof) ->
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
                items(employeeSalesList) { (empName, total, profit) ->
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
