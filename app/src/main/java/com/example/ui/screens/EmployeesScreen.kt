package com.example.ui.screens

import androidx.compose.foundation.background
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
import com.example.data.model.Employee
import com.example.data.model.EmployeeTransaction
import com.example.data.model.ExchangeAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.DialogSectionHeader
import com.example.ui.components.DialogSegmentedChoice
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper
import com.example.ui.util.StatementMode

@Composable
fun EmployeesScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val employeeTransactions by viewModel.employeeTransactions.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val employeeRoles by viewModel.employeeRoles.collectAsStateWithLifecycle()

    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }
    var actionDialogState by remember { mutableStateOf<Triple<Employee, String, String>?>(null) } // (Employee, ActionType: "سلفة" | "خصم غياب" | "صرف راتب", defaultNotes)
    var viewHistoryEmployee by remember { mutableStateOf<Employee?>(null) }
    var editingEmployeeTx by remember { mutableStateOf<com.example.data.model.EmployeeTransaction?>(null) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "العمال والرواتب",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddEmployeeDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("إضافة عامل جديد") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_employee")
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Header summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "إدارة رواتب وعمولات عمال المحل",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "حساب تلقائي: الراتب + العمولة - السلف - خصومات الغياب = المتبقي",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            if (employees.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا يوجد عمال مسجلون حالياً. أضف عمال المحل لمتابعة مبيعاتهم وسلفهم.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(employees, key = { it.id }) { employee ->
                    val empSummary = remember(employee, employeeTransactions, sales) {
                        StatementHelper.buildEmployeeStatement(employee, employeeTransactions, sales)
                    }

                    EmployeeCard(
                        employee = employee,
                        totalSales = empSummary.totalSalesAmount,
                        commissionEarned = empSummary.commissionEarned,
                        advances = empSummary.advances + empSummary.dailyStipends,
                        deductions = empSummary.deductions,
                        salaryPaid = empSummary.salaryPaid,
                        remainingBalance = empSummary.remainingBalance,
                        onAddAdvance = { actionDialogState = Triple(employee, "سلفة", "سلفة نقدية على الحساب") },
                        onAddDeduction = { actionDialogState = Triple(employee, "خصم غياب", "خصم غياب يوم") },
                        onPaySalary = { actionDialogState = Triple(employee, "صرف راتب", "صرف دفعة من الراتب") },
                        onViewHistory = { viewHistoryEmployee = employee },
                        onEdit = { employeeToEdit = employee },
                        onDelete = { employeeToDelete = employee }
                    )
                }
            }
        }
        }
    }

    // Action Dialog (سلفة / خصم غياب / صرف راتب)
    actionDialogState?.let { (employee, actionType, defaultNote) ->
        var amountStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf(defaultNote) }
        var paidFromCash by remember { mutableStateOf(true) }
        var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }
        var transferRef by remember { mutableStateOf("") }
        var txTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        val actionColor = when (actionType) {
            "سلفة", "خصم غياب" -> FintechRose
            "مكافأة" -> FintechEmerald
            else -> PdfHeaderNavy
        }

        StoreOperationDialog(
            onDismissRequest = { actionDialogState = null },
            title = "تسجيل $actionType للعامل",
            subtitle = "${employee.name} • ${employee.role}",
            icon = when (actionType) {
                "صرف راتب" -> Icons.Default.Payments
                "مكافأة" -> Icons.Default.CardGiftcard
                "خصم غياب" -> Icons.Default.EventBusy
                else -> Icons.Default.RequestQuote
            },
            accentColor = actionColor,
            confirmText = "حفظ العملية",
            confirmIcon = Icons.Default.Save,
            confirmColor = actionColor,
            confirmTestTag = "confirm_emp_tx_button",
            onConfirm = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                viewModel.recordEmployeeTransaction(
                    employeeId = employee.id,
                    employeeName = employee.name,
                    type = actionType,
                    amount = amount,
                    notes = notes,
                    paidFromCashDrawer = if (actionType == "خصم غياب") false else paidFromCash,
                    exchangeAccount = if (actionType == "خصم غياب") null else selectedExchangeAccount,
                    referenceNumber = if (actionType == "خصم غياب" || paidFromCash) "" else transferRef,
                    timestamp = txTimestamp,
                    onSuccess = { actionDialogState = null }
                )
            }
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = actionColor) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(actionColor),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_tx_amount_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("البيان / ملاحظات") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            TransactionDatePickerField(
                selectedTimestamp = txTimestamp,
                onDateSelected = { txTimestamp = it },
                label = "تاريخ العملية / الصرفة",
                testTag = "emp_tx_date_picker"
            )

            if (actionType != "خصم غياب") {
                DialogSectionHeader(title = "مصدر الدفع", icon = Icons.Default.AccountBalanceWallet, accentColor = PdfHeaderNavy)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialogSegmentedChoice(
                        selected = paidFromCash,
                        label = "الصندوق النقدي",
                        icon = Icons.Default.Payments,
                        selectedColor = FintechEmerald,
                        onClick = {
                            paidFromCash = true
                            selectedExchangeAccount = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DialogSegmentedChoice(
                        selected = !paidFromCash,
                        label = "صراف / محفظة",
                        icon = Icons.Default.AccountBalance,
                        selectedColor = FintechIndigo,
                        onClick = { paidFromCash = false },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!paidFromCash) {
                    DialogSectionHeader(title = "الحساب الذي سيُخصم منه المبلغ", icon = Icons.Default.AccountBalance, accentColor = FintechIndigo)
                    if (exchangeAccounts.isEmpty()) {
                        Text(
                            text = "لا توجد حسابات صرافة. أضف حساباً من شاشة الصرافين والمحافظ أولاً.",
                            color = RubyExpense,
                            fontSize = 12.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            exchangeAccounts.forEach { account ->
                                FilterChip(
                                    selected = selectedExchangeAccount?.id == account.id,
                                    onClick = { selectedExchangeAccount = account },
                                    label = {
                                        Text("${account.name} — الرصيد: ${FormatUtils.formatCurrency(account.balance)}")
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FintechIndigo,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        OutlinedTextField(
                            value = transferRef,
                            onValueChange = { transferRef = it },
                            label = { Text("رقم الحوالة / سند الصراف (اختياري)") },
                            placeholder = { Text("مثال: 94821034") },
                            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfHeaderNavy) },
                            colors = storeDialogTextFieldColors(PdfHeaderNavy),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }

    // Add Employee Dialog
    if (showAddEmployeeDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("بائع") }
        var salaryStr by remember { mutableStateOf("3500") }
        var commissionStr by remember { mutableStateOf("2.0") }

        StoreOperationDialog(
            onDismissRequest = { showAddEmployeeDialog = false },
            title = "إضافة عامل جديد للمحل",
            subtitle = "تسجيل الراتب الأساسي ونسبة العمولة",
            icon = Icons.Default.PersonAdd,
            accentColor = BrandBlue,
            confirmText = "حفظ العامل",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "submit_new_employee_button",
            onConfirm = {
                val baseSalary = salaryStr.toDoubleOrNull() ?: 0.0
                val comm = commissionStr.toDoubleOrNull() ?: 0.0
                viewModel.saveEmployee(
                    Employee(
                        name = name,
                        phone = phone,
                        role = role,
                        baseSalary = baseSalary,
                        commissionPercent = comm
                    ),
                    onSuccess = { showAddEmployeeDialog = false }
                )
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم العامل الكامل *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_employee_name")
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DialogSectionHeader(title = "المسمى الوظيفي", icon = Icons.Default.Badge, accentColor = BrandBlue)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(employeeRoles) { roleOpt ->
                    FilterChip(
                        selected = role == roleOpt,
                        onClick = { role = roleOpt },
                        label = { Text(roleOpt, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PdfHeaderNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("المسمى الوظيفي (مثال: بائع، خياط، كاشير)") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = BrandBlue) },
                colors = storeDialogTextFieldColors(BrandBlue),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = salaryStr,
                onValueChange = { salaryStr = it },
                label = { Text("الراتب الشهري الأساسي (ريال)") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(FintechEmerald),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = commissionStr,
                onValueChange = { commissionStr = it },
                label = { Text("نسبة العمولة من المبيعات (%)") },
                leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, tint = PdfGoldDark) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = storeDialogTextFieldColors(PdfGoldDark),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // View History & Statement Modal
    viewHistoryEmployee?.let { emp ->
        val txs = remember(emp.id, employeeTransactions) {
            employeeTransactions.filter { it.employeeId == emp.id }.sortedByDescending { it.timestamp }
        }
        val empSummary = remember(emp, employeeTransactions, sales) {
            StatementHelper.buildEmployeeStatement(emp, employeeTransactions, sales)
        }
        var statementMode by remember(emp.id) { mutableStateOf(StatementMode.DETAILED) }

        StoreOperationDialog(
            onDismissRequest = { viewHistoryEmployee = null },
            title = "كشف حساب العامل: ${emp.name}",
            subtitle = "${emp.role} • مبيعات: ${empSummary.salesCount} (${FormatUtils.formatCurrency(empSummary.totalSalesAmount)})",
            icon = Icons.Default.AssignmentInd,
            accentColor = PdfGoldPrimary,
            maxWidth = 560.dp,
            footer = {
                Button(
                    onClick = { viewHistoryEmployee = null },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            // Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = statementMode == StatementMode.DETAILED,
                    label = "كشف مفصل",
                    icon = Icons.Default.FormatListBulleted,
                    selectedColor = PdfHeaderNavy,
                    onClick = { statementMode = StatementMode.DETAILED },
                    modifier = Modifier.weight(1f).testTag("btn_emp_mode_detailed")
                )
                DialogSegmentedChoice(
                    selected = statementMode == StatementMode.SUMMARY,
                    label = "تقرير نهائي",
                    icon = Icons.Default.Summarize,
                    selectedColor = PdfGoldDark,
                    onClick = { statementMode = StatementMode.SUMMARY },
                    modifier = Modifier.weight(1f).testTag("btn_emp_mode_summary")
                )
            }

            // 3 KPI Boxes: كم له / كم عليه / كم باقي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("كم له", fontSize = 10.sp, color = EmeraldProfit, fontWeight = FontWeight.Bold)
                                Text(
                                    FormatUtils.formatNumber(empSummary.totalEarned),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldProfit
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = RubyLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("كم عليه", fontSize = 10.sp, color = RubyExpense, fontWeight = FontWeight.Bold)
                                Text(
                                    FormatUtils.formatNumber(empSummary.totalDeductedAndPaid),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RubyExpense
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PdfHeaderNavy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (empSummary.remainingBalance >= 0) "كم باقي له" else "زائد عليه",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    FormatUtils.formatNumber(kotlin.math.abs(empSummary.remainingBalance)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LuxuryGoldLight
                                )
                            }
                        }
                    }

                    // Export & Share Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = InvoiceHelper.generateEmployeeStatementPdf(
                                    context = context,
                                    summary = empSummary,
                                    mode = statementMode,
                                    settings = storeSettings
                                )
                                InvoiceHelper.printPdfFile(context, file, "كشف_عامل_${emp.name}")
                            },
                            modifier = Modifier.weight(1f).height(42.dp).testTag("btn_emp_print_pdf"),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("طباعة PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val file = InvoiceHelper.generateEmployeeStatementPdf(
                                    context = context,
                                    summary = empSummary,
                                    mode = statementMode,
                                    settings = storeSettings
                                )
                                InvoiceHelper.sharePdfFile(context, file, "مشاركة كشف حساب ${emp.name}")
                            },
                            modifier = Modifier.weight(1f).height(42.dp).testTag("btn_emp_share_pdf"),
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
                            onClick = {
                                val txt = StatementHelper.formatEmployeeStatementText(empSummary, statementMode, storeSettings)
                                StatementHelper.shareText(context, txt, "مشاركة كشف ${emp.name}")
                            },
                            modifier = Modifier.weight(1f).height(42.dp).testTag("btn_emp_share_whatsapp"),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val txt = StatementHelper.formatEmployeeStatementText(empSummary, statementMode, storeSettings)
                                StatementHelper.copyToClipboard(context, txt, "كشف حساب ${emp.name}")
                            },
                            modifier = Modifier.weight(1f).height(42.dp).testTag("btn_emp_copy_text"),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ النص", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = SlateBorder)

                    if (statementMode == StatementMode.SUMMARY) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("الملخص المالي النهائي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الراتب الأساسي:", fontSize = 12.sp, color = SlateMuted)
                                    Text(FormatUtils.formatCurrency(emp.baseSalary), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("عمولة المبيعات (${emp.commissionPercent}%):", fontSize = 12.sp, color = SlateMuted)
                                    Text(FormatUtils.formatCurrency(empSummary.commissionEarned), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldProfit)
                                }
                                if (empSummary.bonuses > 0.01) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("المكافآت:", fontSize = 12.sp, color = SlateMuted)
                                        Text(FormatUtils.formatCurrency(empSummary.bonuses), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldProfit)
                                    }
                                }
                                HorizontalDivider(color = SlateBorder)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("السلف المسحوبة:", fontSize = 12.sp, color = SlateMuted)
                                    Text(FormatUtils.formatCurrency(empSummary.advances), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RubyExpense)
                                }
                                if (empSummary.dailyStipends > 0.01) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("الصرفيات اليومية:", fontSize = 12.sp, color = SlateMuted)
                                        Text(FormatUtils.formatCurrency(empSummary.dailyStipends), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RubyExpense)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("خصومات الغياب:", fontSize = 12.sp, color = SlateMuted)
                                    Text(FormatUtils.formatCurrency(empSummary.deductions), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RubyExpense)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الرواتب المصروفة:", fontSize = 12.sp, color = SlateMuted)
                                    Text(FormatUtils.formatCurrency(empSummary.salaryPaid), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        if (txs.isEmpty()) {
                            Text("لا توجد سلف أو خصومات مسجلة لهذا العامل بعد.", fontSize = 12.sp, color = SlateMuted)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                txs.forEach { tx ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = tx.type, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (tx.notes.isNotBlank()) {
                                                    Text(text = tx.notes, style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                                                }
                                                if (tx.type == "سلفة" || tx.type == "صرف راتب" || tx.type == "مكافأة" || tx.type == "صرفة يومية") {
                                                    Text(
                                                        text = if (tx.paidFromCash) "المصدر: الصندوق النقدي" else "المصدر: ${tx.exchangeAccountName.ifBlank { "حساب صرافة" }}",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (tx.paidFromCash) LuxuryGold else MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                }
                                                Text(text = FormatUtils.formatDateTime(tx.timestamp), fontSize = 10.sp, color = SlateMuted)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = FormatUtils.formatCurrency(tx.amount),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (tx.type == "مكافأة") EmeraldProfit else RubyExpense
                                                )
                                                IconButton(onClick = { editingEmployeeTx = tx }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = BrandBlue, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(onClick = { viewModel.deleteEmployeeTransaction(tx.id) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = RubyExpense, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
        }
    }

    // Edit Employee Transaction Dialog
    editingEmployeeTx?.let { tx ->
        var amountStr by remember { mutableStateOf(tx.amount.toLong().toString()) }
        var notes by remember { mutableStateOf(tx.notes) }
        var paidFromCash by remember { mutableStateOf(tx.paidFromCash) }
        var selectedExchangeAccount by remember {
            mutableStateOf(exchangeAccounts.firstOrNull { it.id == tx.exchangeAccountId } ?: exchangeAccounts.firstOrNull())
        }
        var editTimestamp by remember(tx.id) { mutableLongStateOf(tx.timestamp) }

        StoreOperationDialog(
            onDismissRequest = { editingEmployeeTx = null },
            title = "تعديل حركة العامل (${tx.type})",
            subtitle = tx.employeeName,
            icon = Icons.Default.Edit,
            accentColor = BrandBlue,
            confirmText = "حفظ التعديل",
            confirmIcon = Icons.Default.Check,
            confirmColor = PdfHeaderNavy,
            onConfirm = {
                val amount = amountStr.toDoubleOrNull() ?: tx.amount
                if (amount <= 0) {
                    viewModel.showMessage("يرجى إدخال مبلغ صحيح أكبر من الصفر!")
                } else {
                    val isCash = if (tx.type == "خصم غياب") false else paidFromCash
                    viewModel.updateEmployeeTransaction(
                        tx.copy(
                            amount = amount,
                            notes = notes,
                            paidFromCash = isCash,
                            exchangeAccountId = if (isCash || tx.type == "خصم غياب") 0L else (selectedExchangeAccount?.id ?: 0L),
                            exchangeAccountName = if (isCash || tx.type == "خصم غياب") "" else (selectedExchangeAccount?.name ?: ""),
                            timestamp = editTimestamp
                        ),
                        onSuccess = { editingEmployeeTx = null }
                    )
                }
            }
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = BrandBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(BrandBlue),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("البيان / ملاحظات") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            TransactionDatePickerField(
                selectedTimestamp = editTimestamp,
                onDateSelected = { editTimestamp = it },
                label = "تاريخ الحركة",
                testTag = "edit_emp_tx_date_picker"
            )

            if (tx.type != "خصم غياب") {
                DialogSectionHeader(title = "مصدر الدفع", icon = Icons.Default.AccountBalanceWallet, accentColor = PdfHeaderNavy)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialogSegmentedChoice(
                        selected = paidFromCash,
                        label = "الصندوق النقدي",
                        icon = Icons.Default.Payments,
                        selectedColor = FintechEmerald,
                        onClick = {
                            paidFromCash = true
                            selectedExchangeAccount = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DialogSegmentedChoice(
                        selected = !paidFromCash,
                        label = "صراف / محفظة",
                        icon = Icons.Default.AccountBalance,
                        selectedColor = FintechIndigo,
                        onClick = { paidFromCash = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!paidFromCash && exchangeAccounts.isNotEmpty()) {
                    DialogSectionHeader(title = "اختر حساب الصراف أو المحفظة", icon = Icons.Default.AccountBalance, accentColor = FintechIndigo)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(exchangeAccounts) { acc ->
                            val isSel = selectedExchangeAccount?.id == acc.id
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedExchangeAccount = acc },
                                label = { Text(acc.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FintechIndigo,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Employee Dialog
    employeeToEdit?.let { emp ->
        var name by remember { mutableStateOf(emp.name) }
        var phone by remember { mutableStateOf(emp.phone) }
        var role by remember { mutableStateOf(emp.role) }
        var salaryStr by remember { mutableStateOf(emp.baseSalary.toLong().toString()) }
        var commissionStr by remember { mutableStateOf(emp.commissionPercent.toString()) }

        StoreOperationDialog(
            onDismissRequest = { employeeToEdit = null },
            title = "تعديل بيانات العامل",
            subtitle = emp.name,
            icon = Icons.Default.Edit,
            accentColor = BrandBlue,
            confirmText = "حفظ التعديلات",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            onConfirm = {
                val base = salaryStr.toDoubleOrNull() ?: emp.baseSalary
                val comm = commissionStr.toDoubleOrNull() ?: emp.commissionPercent
                viewModel.saveEmployee(
                    emp.copy(
                        name = name,
                        phone = phone,
                        role = role,
                        baseSalary = base,
                        commissionPercent = comm
                    ),
                    onSuccess = { employeeToEdit = null }
                )
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم العامل الكامل") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            DialogSectionHeader(title = "المسمى الوظيفي", icon = Icons.Default.Badge, accentColor = BrandBlue)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(employeeRoles) { roleOpt ->
                    FilterChip(
                        selected = role == roleOpt,
                        onClick = { role = roleOpt },
                        label = { Text(roleOpt, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PdfHeaderNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("المسمى الوظيفي (بائع، كاشير...)") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = BrandBlue) },
                colors = storeDialogTextFieldColors(BrandBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = salaryStr,
                onValueChange = { salaryStr = it },
                label = { Text("الراتب الأساسي (ريال)") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(FintechEmerald),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = commissionStr,
                onValueChange = { commissionStr = it },
                label = { Text("نسبة العمولة (%)") },
                leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, tint = PdfGoldDark) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = storeDialogTextFieldColors(PdfGoldDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }

    // Delete Employee Confirmation Dialog
    employeeToDelete?.let { emp ->
        StoreOperationDialog(
            onDismissRequest = { employeeToDelete = null },
            title = "تأكيد حذف العامل",
            subtitle = emp.name,
            icon = Icons.Default.Warning,
            accentColor = RubyExpense,
            confirmText = "تأكيد الحذف",
            confirmIcon = Icons.Default.DeleteOutline,
            confirmColor = RubyExpense,
            onConfirm = {
                viewModel.deleteEmployee(emp.id)
                employeeToDelete = null
            }
        ) {
            DialogInfoBanner(
                title = "المسمى الوظيفي:",
                value = emp.role,
                containerColor = Color(0xFFFFF1F2),
                contentColor = RubyExpense,
                borderColor = Color(0xFFFECDD3),
                icon = Icons.Default.PersonOff
            )
            Text(
                text = "هل أنت متأكد من حذف العامل (${emp.name}) من قائمة عمال المحل؟",
                fontSize = 13.sp,
                color = SlateTextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    totalSales: Double,
    commissionEarned: Double,
    advances: Double,
    deductions: Double,
    salaryPaid: Double,
    remainingBalance: Double,
    onAddAdvance: () -> Unit,
    onAddDeduction: () -> Unit,
    onPaySalary: () -> Unit,
    onViewHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Name, Role, Phone & Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = employee.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${employee.role} | هاتف: ${employee.phone.ifBlank { "غير مسجل" }}",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldContainer
                    ) {
                        Text(
                            text = "عمولة ${employee.commissionPercent}%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = LuxuryGold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = RubyExpense, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Calculation Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الراتب الأساسي", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(FormatUtils.formatCurrency(employee.baseSalary), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("المبيعات المنجزة", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(FormatUtils.formatCurrency(totalSales), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("العمولة المستحقة", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text("+ " + FormatUtils.formatCurrency(commissionEarned), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("السلف المسحوبة", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text("- " + FormatUtils.formatCurrency(advances), style = MaterialTheme.typography.bodyMedium.copy(color = RubyExpense, fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("خصومات الغياب", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text("- " + FormatUtils.formatCurrency(deductions), style = MaterialTheme.typography.bodyMedium.copy(color = RubyExpense, fontWeight = FontWeight.Bold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المدفوع من الراتب", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(FormatUtils.formatCurrency(salaryPaid), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            // Net Balance Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (remainingBalance > 0) MaterialTheme.colorScheme.primaryContainer else SlateBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الرصيد المتبقي للعامل:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(remainingBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = LuxuryGoldLight,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddAdvance,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("سلفة", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAddDeduction,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("خصم", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = onPaySalary,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("صرف راتب", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = onViewHistory) {
                    Icon(Icons.Default.History, contentDescription = "سجل الحركات", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
