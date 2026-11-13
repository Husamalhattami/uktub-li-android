package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.model.ExpenseRecord
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun ExpensesScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val dynamicExpenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ExpenseRecord?>(null) }
    var selectedFilterCategory by remember { mutableStateOf("الكل") }

    val presetCategories = remember(dynamicExpenseCategories, expenses) {
        (dynamicExpenseCategories + expenses.map { it.category.trim() }.filter { it.isNotBlank() }).distinct()
    }

    val totalExpensesThisMonth = remember(expenses) {
        expenses.filter { FormatUtils.isThisMonth(it.timestamp) }.sumOf { it.amount }
    }
    val totalExpensesToday = remember(expenses) {
        expenses.filter { FormatUtils.isToday(it.timestamp) }.sumOf { it.amount }
    }

    val filteredExpenses = remember(expenses, selectedFilterCategory) {
        if (selectedFilterCategory == "الكل") expenses
        else expenses.filter { it.category == selectedFilterCategory }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "مصروفات المحل",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("تسجيل مصروف جديد", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = FintechRose,
                modifier = Modifier.testTag("fab_add_expense")
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
            // KPI Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = FintechRoseBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FintechRoseBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(FintechRose.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = FintechRose, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = "مصروفات اليوم",
                                    style = MaterialTheme.typography.labelMedium.copy(color = FintechRose, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalExpensesToday),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FintechRose
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(BrandNavy.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = "مصروفات هذا الشهر",
                                    style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalExpensesThisMonth),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BrandNavy
                                )
                            )
                        }
                    }
                }
            }

            // Category Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "تصنيف المصروفات:",
                        style = MaterialTheme.typography.labelMedium.copy(color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val filterList = listOf("الكل") + presetCategories
                        items(filterList) { cat ->
                            val isSelected = selectedFilterCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterCategory = cat },
                                label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandNavy,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل المصروفات (${filteredExpenses.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                    )
                    if (selectedFilterCategory != "الكل") {
                        TextButton(onClick = { selectedFilterCategory = "الكل" }) {
                            Text("عرض الكل", fontSize = 12.sp, color = BrandBlue)
                        }
                    }
                }
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = SlateTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لا توجد مصروفات مسجلة في هذا القسم",
                                style = MaterialTheme.typography.bodyMedium.copy(color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "اضغط على زر '+ تسجيل مصروف جديد' لإضافة فاتورة أو إيجار أو ضيافة",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateTextMuted)
                            )
                        }
                    }
                }
            } else {
                items(filteredExpenses, key = { it.id }) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onEdit = { expenseToEdit = expense },
                        onDelete = { viewModel.deleteExpense(expense.id) }
                    )
                }
            }
        }
        }
    }

    // Add Expense Dialog
    if (showAddDialog) {
        var selectedCat by remember { mutableStateOf(dynamicExpenseCategories.firstOrNull() ?: "مصروف متفرقات") }
        var selectedEmployee by remember { mutableStateOf(employees.firstOrNull()) }
        var amountStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var receiptNo by remember { mutableStateOf("") }
        var paymentSource by remember { mutableStateOf("كاش") }
        var selectedExchangeAcc by remember { mutableStateOf<ExchangeAccount?>(exchangeAccounts.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FintechRoseBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = FintechRose, modifier = Modifier.size(18.dp))
                    }
                    Text("تسجيل مصروف جديد", fontWeight = FontWeight.Bold, color = SlateTextPrimary, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "نوع المصروف:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(dynamicExpenseCategories) { cat ->
                            val isSelected = selectedCat == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCat = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (cat == "صرفة يومية لعامل") BrandBlue else BrandNavy,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Employee selector when daily stipend is chosen
                    if (selectedCat == "صرفة يومية لعامل") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("اختر العامل المستلم للصرفة اليومية:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                if (employees.isEmpty()) {
                                    Text("لا يوجد عمال مسجلون حالياً! يرجى إضافة العامل في قسم العمال أولاً.", fontSize = 11.sp, color = Color(0xFFDC2626))
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(employees) { emp ->
                                            val isSel = selectedEmployee?.id == emp.id
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { selectedEmployee = emp },
                                                label = { Text(emp.name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF1E40AF),
                                                    selectedLabelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ بالريال *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات (السبب أو التفاصيل)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = receiptNo,
                        onValueChange = { receiptNo = it },
                        label = { Text("رقم السند أو الفاتورة (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "مصدر دفع المصروف:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isCash = paymentSource == "كاش"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCash) FintechEmerald else SlateBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCash) FintechEmerald else SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentSource = "كاش" }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("كاش من الصندوق", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isCash) Color.White else SlateTextPrimary)
                            }
                        }

                        val isExchange = paymentSource == "صراف"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExchange) FintechIndigo else SlateBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isExchange) FintechIndigo else SlateBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentSource = "صراف" }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("من حساب صراف/محفظة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isExchange) Color.White else SlateTextPrimary)
                            }
                        }
                    }

                    if (paymentSource == "صراف") {
                        if (exchangeAccounts.isEmpty()) {
                            Text("لا توجد حسابات صرافة مضافة بعد.", color = FintechRose, fontSize = 11.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(exchangeAccounts) { acc ->
                                    val isSelected = selectedExchangeAcc?.id == acc.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedExchangeAcc = acc },
                                        label = { Text("${acc.name} (${FormatUtils.formatCurrency(acc.balance)})", fontSize = 11.sp) },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        val isCash = paymentSource == "كاش"
                        val empToPass = if (selectedCat == "صرفة يومية لعامل") selectedEmployee else null
                        if (selectedCat == "صرفة يومية لعامل" && empToPass == null) {
                            viewModel.showMessage("يرجى اختيار العامل لتسجيل الصرفة اليومية!")
                            return@Button
                        }
                        if (amount <= 0) {
                            viewModel.showMessage("يرجى إدخال مبلغ صحيح أكبر من صفر!")
                            return@Button
                        }

                        viewModel.recordExpense(
                            category = selectedCat,
                            amount = amount,
                            notes = notes,
                            receiptNumber = receiptNo,
                            paidFromCash = isCash,
                            exchangeAccount = if (!isCash) selectedExchangeAcc else null,
                            employee = empToPass,
                            onSuccess = { showAddDialog = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRose),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_expense_button")
                ) {
                    Text("حفظ المصروف", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }, shape = RoundedCornerShape(10.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Edit Expense Dialog
    expenseToEdit?.let { exp ->
        var cat by remember { mutableStateOf(exp.category) }
        var amountStr by remember { mutableStateOf(exp.amount.toLong().toString()) }
        var notes by remember { mutableStateOf(exp.notes) }
        var receiptNo by remember { mutableStateOf(exp.receiptNumber) }
        var paymentSource by remember { mutableStateOf(if (exp.paidFromCash) "كاش" else "صراف") }
        var selectedExchangeAcc by remember {
            mutableStateOf(exchangeAccounts.firstOrNull { it.id == exp.exchangeAccountId } ?: exchangeAccounts.firstOrNull())
        }

        AlertDialog(
            onDismissRequest = { expenseToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Text("تعديل بيانات المصروف / الصرفة", fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("نوع المصروف:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SlateTextSecondary))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetCategories) { c ->
                            val isSel = cat == c
                            FilterChip(
                                selected = isSel,
                                onClick = { cat = c },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ (ريال) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    // Payment source
                    Text("مصدر الدفع:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = SlateTextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isCash = paymentSource == "كاش"
                        FilterChip(
                            selected = isCash,
                            onClick = { paymentSource = "كاش" },
                            label = { Text("كاش من الصندوق") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isCash,
                            onClick = { paymentSource = "صراف" },
                            label = { Text("من حساب صراف/محفظة") },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (paymentSource == "صراف") {
                        if (exchangeAccounts.isNotEmpty()) {
                            Text("اختر حساب الصراف أو المحفظة:", style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(exchangeAccounts) { acc ->
                                    val isSel = selectedExchangeAcc?.id == acc.id
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedExchangeAcc = acc },
                                        label = { Text(acc.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = receiptNo,
                        onValueChange = { receiptNo = it },
                        label = { Text("رقم الوصل / الحوالة (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAmount = amountStr.toDoubleOrNull() ?: exp.amount
                        if (newAmount <= 0) {
                            viewModel.showMessage("يرجى إدخال مبلغ صحيح أكبر من الصفر!")
                            return@Button
                        }
                        val isCash = paymentSource == "كاش"
                        viewModel.updateExpense(
                            exp.copy(
                                category = cat,
                                amount = newAmount,
                                notes = notes,
                                receiptNumber = receiptNo,
                                paidFromCash = isCash,
                                exchangeAccountId = if (isCash) 0L else (selectedExchangeAcc?.id ?: 0L),
                                exchangeAccountName = if (isCash) "" else (selectedExchangeAcc?.name ?: "")
                            ),
                            onSuccess = { expenseToEdit = null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التعديل", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { expenseToEdit = null }, shape = RoundedCornerShape(10.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SlateCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
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
                val isStipend = expense.category == "صرفة يومية لعامل" || expense.category.contains("صرفة")
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isStipend) Color(0xFFEFF6FF) else FintechRoseBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStipend) Icons.Default.Badge else Icons.Default.Receipt,
                        contentDescription = null,
                        tint = if (isStipend) Color(0xFF1E40AF) else FintechRose,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = expense.category,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                        )
                        if (isStipend) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDBEAFE),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD))
                            ) {
                                Text(
                                    text = "صرفة عامل",
                                    color = Color(0xFF1E40AF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (expense.notes.isNotBlank()) {
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary)
                        )
                    }
                    val sourceText = if (expense.paidFromCash) "الصندوق (كاش)" else "حساب صرافة: ${expense.exchangeAccountName}"
                    Text(
                        text = "${FormatUtils.formatDateTime(expense.timestamp)} | $sourceText",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 10.sp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "- " + FormatUtils.formatCurrency(expense.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FintechRose
                    )
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = BrandBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = FintechRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
