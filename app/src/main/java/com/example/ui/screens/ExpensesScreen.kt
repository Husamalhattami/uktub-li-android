package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    val presetCategories = listOf(
        "إيجار المحل",
        "الكهرباء",
        "الماء",
        "الإنترنت",
        "النقل والشحن",
        "التغليف والأكياس",
        "التصليح والصيانة",
        "الضيافة والشاي",
        "الدعاية والإعلان",
        "مصروف متفرقات"
    )

    val totalExpensesThisMonth = remember(expenses) {
        expenses.filter { FormatUtils.isThisMonth(it.timestamp) }.sumOf { it.amount }
    }
    val totalExpensesToday = remember(expenses) {
        expenses.filter { FormatUtils.isToday(it.timestamp) }.sumOf { it.amount }
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
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("تسجيل مصروف جديد") },
                containerColor = RubyExpense,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_expense")
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
            // KPI Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RubyLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "مصروفات اليوم",
                                style = MaterialTheme.typography.labelMedium.copy(color = RubyExpense)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalExpensesToday),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RubyExpense
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "مصروفات هذا الشهر",
                                style = MaterialTheme.typography.labelMedium.copy(color = SlateMuted)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalExpensesThisMonth),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // Quick Category Shortcuts banner
            item {
                Text(
                    text = "سجل المصروفات المباشر",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد مصروفات مسجلة بعد. اضغط على الزر أدناه لتسجيل أي مصروف.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onDelete = { viewModel.deleteExpense(expense.id) }
                    )
                }
            }
        }
    }

    // Add Expense Dialog
    if (showAddDialog) {
        var selectedCat by remember { mutableStateOf(presetCategories.first()) }
        var amountStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var receiptNo by remember { mutableStateOf("") }
        var paidFromCash by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = RubyExpense)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل مصروف جديد", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "نوع المصروف:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presetCategories) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_amount_input")
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات (السبب أو التفاصيل)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = receiptNo,
                        onValueChange = { receiptNo = it },
                        label = { Text("رقم الوصل (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("خصم المبلغ من الصندوق النقدي:")
                        Switch(
                            checked = paidFromCash,
                            onCheckedChange = { paidFromCash = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordExpense(
                            category = selectedCat,
                            amount = amount,
                            notes = notes,
                            receiptNumber = receiptNo,
                            paidFromCash = paidFromCash,
                            onSuccess = { showAddDialog = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_expense_button")
                ) {
                    Text("حفظ المصروف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseRecord,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(RubyLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = RubyExpense,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = expense.category,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (expense.notes.isNotBlank()) {
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                    Text(
                        text = FormatUtils.formatDateTime(expense.timestamp) + if (expense.receiptNumber.isNotBlank()) " | وصل #${expense.receiptNumber}" else "",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted, fontSize = 10.sp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "- " + FormatUtils.formatCurrency(expense.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = RubyExpense
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
