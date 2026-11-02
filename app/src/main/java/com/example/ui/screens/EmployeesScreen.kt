package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Employee
import com.example.data.model.EmployeeTransaction
import com.example.data.model.ExchangeAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun EmployeesScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val employeeTransactions by viewModel.employeeTransactions.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }
    var actionDialogState by remember { mutableStateOf<Triple<Employee, String, String>?>(null) } // (Employee, ActionType: "سلفة" | "خصم غياب" | "صرف راتب", defaultNotes)
    var viewHistoryEmployee by remember { mutableStateOf<Employee?>(null) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
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
                    val txs = employeeTransactions.filter { it.employeeId == employee.id }
                    val empSales = sales.filter { it.employeeId == employee.id }
                    val totalSalesAmount = empSales.sumOf { it.totalAmount }
                    val commissionEarned = totalSalesAmount * (employee.commissionPercent / 100.0)

                    val advances = txs.filter { it.type == "سلفة" }.sumOf { it.amount }
                    val deductions = txs.filter { it.type == "خصم غياب" }.sumOf { it.amount }
                    val salaryPaid = txs.filter { it.type == "صرف راتب" }.sumOf { it.amount }

                    val netEarned = employee.baseSalary + commissionEarned
                    val remainingBalance = (netEarned - deductions - advances - salaryPaid).coerceAtLeast(0.0)

                    EmployeeCard(
                        employee = employee,
                        totalSales = totalSalesAmount,
                        commissionEarned = commissionEarned,
                        advances = advances,
                        deductions = deductions,
                        salaryPaid = salaryPaid,
                        remainingBalance = remainingBalance,
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

    // Action Dialog (سلفة / خصم غياب / صرف راتب)
    actionDialogState?.let { (employee, actionType, defaultNote) ->
        var amountStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf(defaultNote) }
        var paidFromCash by remember { mutableStateOf(true) }
        var selectedExchangeAccount by remember { mutableStateOf<ExchangeAccount?>(null) }

        AlertDialog(
            onDismissRequest = { actionDialogState = null },
            title = {
                Text(
                    text = "تسجيل $actionType للعامل: ${employee.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("employee_tx_amount_input")
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("البيان / ملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (actionType != "خصم غياب") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (paidFromCash) "مصدر الدفع: الصندوق النقدي" else "مصدر الدفع: الصراف / المحفظة")
                            Switch(
                                checked = paidFromCash,
                                onCheckedChange = {
                                    paidFromCash = it
                                    if (it) selectedExchangeAccount = null
                                }
                            )
                        }
                        if (!paidFromCash) {
                            Text(
                                text = "اختر الحساب الذي سيُخصم منه المبلغ:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
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
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordEmployeeTransaction(
                            employeeId = employee.id,
                            employeeName = employee.name,
                            type = actionType,
                            amount = amount,
                            notes = notes,
                            paidFromCashDrawer = if (actionType == "خصم غياب") false else paidFromCash,
                            exchangeAccount = if (actionType == "خصم غياب") null else selectedExchangeAccount,
                            onSuccess = { actionDialogState = null }
                        )
                    },
                    modifier = Modifier.testTag("confirm_emp_tx_button")
                ) {
                    Text("حفظ العملية")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { actionDialogState = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Employee Dialog
    if (showAddEmployeeDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("بائع") }
        var salaryStr by remember { mutableStateOf("3500") }
        var commissionStr by remember { mutableStateOf("2.0") }

        AlertDialog(
            onDismissRequest = { showAddEmployeeDialog = false },
            title = { Text("إضافة عامل جديد للمحل", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العامل الكامل") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_employee_name")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("المسمى الوظيفي (مثال: بائع، خياط، كاشير)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it },
                        label = { Text("الراتب الشهري الأساسي (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuickThousandAdder(
                        currentValue = salaryStr,
                        onValueChange = { salaryStr = it }
                    )
                    OutlinedTextField(
                        value = commissionStr,
                        onValueChange = { commissionStr = it },
                        label = { Text("نسبة العمولة من المبيعات (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    },
                    modifier = Modifier.testTag("submit_new_employee_button")
                ) {
                    Text("حفظ العامل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddEmployeeDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // View History Modal
    viewHistoryEmployee?.let { emp ->
        val txs = employeeTransactions.filter { it.employeeId == emp.id }
        AlertDialog(
            onDismissRequest = { viewHistoryEmployee = null },
            title = { Text("كشف حركات: ${emp.name}", fontWeight = FontWeight.Bold) },
            text = {
                if (txs.isEmpty()) {
                    Text("لا توجد سلف أو خصومات مسجلة لهذا العامل بعد.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(txs) { tx ->
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
                                    Column {
                                        Text(text = tx.type, fontWeight = FontWeight.Bold)
                                        if (tx.notes.isNotBlank()) {
                                            Text(text = tx.notes, style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                                        }
                                        if (tx.type == "سلفة" || tx.type == "صرف راتب" || tx.type == "مكافأة") {
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
                                    Text(
                                        text = FormatUtils.formatCurrency(tx.amount),
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type == "سلفة" || tx.type == "صرف راتب") RubyExpense else EmeraldProfit
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewHistoryEmployee = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Edit Employee Dialog
    employeeToEdit?.let { emp ->
        var name by remember { mutableStateOf(emp.name) }
        var phone by remember { mutableStateOf(emp.phone) }
        var role by remember { mutableStateOf(emp.role) }
        var salaryStr by remember { mutableStateOf(emp.baseSalary.toLong().toString()) }
        var commissionStr by remember { mutableStateOf(emp.commissionPercent.toString()) }

        AlertDialog(
            onDismissRequest = { employeeToEdit = null },
            title = { Text("تعديل بيانات العامل", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العامل الكامل") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("المسمى الوظيفي (بائع، كاشير...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it },
                        label = { Text("الراتب الأساسي (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = commissionStr,
                        onValueChange = { commissionStr = it },
                        label = { Text("نسبة العمولة (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { employeeToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Employee Confirmation Dialog
    employeeToDelete?.let { emp ->
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RubyExpense) },
            title = { Text("تأكيد حذف العامل", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل أنت متأكد من حذف العامل (${emp.name}) من قائمة عمال المحل؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEmployee(emp.id)
                        employeeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { employeeToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
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
                    Text("سلفة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onAddDeduction,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("خصم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPaySalary,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("صرف راتب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onViewHistory) {
                    Icon(Icons.Default.History, contentDescription = "سجل الحركات", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
