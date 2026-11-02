package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DebtRecord
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun DebtsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Customers, 1: Suppliers
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var payingDebt by remember { mutableStateOf<DebtRecord?>(null) }
    var showSettled by remember { mutableStateOf(false) }

    val customerDebts = remember(debts, showSettled) {
        debts.filter { it.type == "CUSTOMER" && (showSettled || !it.isSettled) }
    }
    val supplierDebts = remember(debts, showSettled) {
        debts.filter { it.type == "SUPPLIER" && (showSettled || !it.isSettled) }
    }

    val totalActiveCustomerDebt = remember(debts) {
        debts.filter { it.type == "CUSTOMER" && !it.isSettled }.sumOf { it.remainingAmount }
    }
    val totalActiveSupplierDebt = remember(debts) {
        debts.filter { it.type == "SUPPLIER" && !it.isSettled }.sumOf { it.remainingAmount }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "إدارة الديون والحسابات",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDebtDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (selectedTab == 0) "تسجيل دين زبون" else "تسجيل دين مورد") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_debt")
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
            // Top Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GoldContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("لنا عند الزبائن", style = MaterialTheme.typography.labelMedium.copy(color = LuxuryGold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalActiveCustomerDebt),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LuxuryGold)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RubyLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("علينا للموردين", style = MaterialTheme.typography.labelMedium.copy(color = RubyExpense))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalActiveSupplierDebt),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RubyExpense)
                            )
                        }
                    }
                }
            }

            // Tab Row (ديون الزبائن / ديون الموردين)
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("ديون الزبائن (${customerDebts.count { !it.isSettled }})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_customer_debts")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("ديون الموردين (${supplierDebts.count { !it.isSettled }})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_supplier_debts")
                    )
                }
            }

            // Filter Toggle (إظهار المسددة)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == 0) "قائمة ذمم الزبائن" else "قائمة مستحقات الموردين",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("إظهار المسددة أيضاً", fontSize = 12.sp, color = SlateMuted)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = showSettled,
                            onCheckedChange = { showSettled = it }
                        )
                    }
                }
            }

            val currentList = if (selectedTab == 0) customerDebts else supplierDebts

            if (currentList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = if (selectedTab == 0) "لا توجد ديون مستحقة على الزبائن حالياً." else "لا توجد ديون مطلوبة للموردين حالياً.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(currentList, key = { it.id }) { debt ->
                    DebtItemCard(
                        debt = debt,
                        onRecordPayment = { payingDebt = debt },
                        onDelete = { viewModel.deleteDebt(debt.id) }
                    )
                }
            }
        }
    }

    // Payment Dialog
    payingDebt?.let { debt ->
        var paymentAmountStr by remember { mutableStateOf(debt.remainingAmount.toLong().toString()) }
        var paymentNotes by remember { mutableStateOf("دفعة سداد نقدية") }

        AlertDialog(
            onDismissRequest = { payingDebt = null },
            title = {
                Text(
                    text = if (debt.type == "CUSTOMER") "استلام دفعة من: ${debt.partyName}" else "تسديد دفعة للمورد: ${debt.partyName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "المبلغ المتبقي حالياً: ${FormatUtils.formatCurrency(debt.remainingAmount)}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = LuxuryGold, fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = paymentAmountStr,
                        onValueChange = { paymentAmountStr = it },
                        label = { Text("مبلغ الدفعة المسددة (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debt_payment_amount_input")
                    )

                    QuickThousandAdder(
                        currentValue = paymentAmountStr,
                        onValueChange = { paymentAmountStr = it }
                    )

                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("ملاحظات السداد") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (debt.type == "CUSTOMER") "سيتم إضافة المبلغ تلقائياً إلى رصيد الصندوق النقدي." else "سيتم خصم المبلغ المسدد تلقائياً من الصندوق النقدي.",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentAmountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordDebtPayment(
                            debt = debt,
                            amount = amount,
                            notes = paymentNotes,
                            onSuccess = { payingDebt = null }
                        )
                    },
                    modifier = Modifier.testTag("confirm_debt_payment_button")
                ) {
                    Text("تأكيد السداد")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { payingDebt = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add New Debt Dialog
    if (showAddDebtDialog) {
        var partyName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var details by remember { mutableStateOf("") }
        val isCustomer = selectedTab == 0

        AlertDialog(
            onDismissRequest = { showAddDebtDialog = false },
            title = {
                Text(
                    text = if (isCustomer) "تسجيل دين جديد على زبون" else "تسجيل دين جديد لمورد",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it },
                        label = { Text(if (isCustomer) "اسم الزبون" else "اسم المورد") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_debt_party_name")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ المطلوب (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_debt_amount_input")
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("تفاصيل المشتريات / البضاعة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.addNewDebt(
                            DebtRecord(
                                type = if (isCustomer) "CUSTOMER" else "SUPPLIER",
                                partyName = partyName,
                                phone = phone,
                                originalAmount = amount,
                                paidAmount = 0.0,
                                remainingAmount = amount,
                                details = details,
                                dueDate = System.currentTimeMillis() + 86400000L * 15,
                                isSettled = false
                            ),
                            onSuccess = { showAddDebtDialog = false }
                        )
                    },
                    modifier = Modifier.testTag("submit_new_debt_button")
                ) {
                    Text("حفظ الدين")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDebtDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DebtItemCard(
    debt: DebtRecord,
    onRecordPayment: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isNearDue = debt.dueDate > 0 && (debt.dueDate - now <= 86400000L * 3) && !debt.isSettled
    val isOverdue = debt.dueDate > 0 && debt.dueDate < now && !debt.isSettled

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.partyName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (debt.details.isNotBlank()) {
                        Text(
                            text = debt.details,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                    if (debt.phone.isNotBlank()) {
                        Text(
                            text = "هاتف: ${debt.phone}",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (debt.isSettled) EmeraldLight else if (isOverdue || isNearDue) RubyLight else GoldContainer
                ) {
                    Text(
                        text = if (debt.isSettled) "مسدد بالكامل" else if (isOverdue) "متأخر السداد!" else if (isNearDue) "قرب موعد السداد" else "مستحق",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (debt.isSettled) EmeraldProfit else if (isOverdue || isNearDue) RubyExpense else LuxuryGold
                        )
                    )
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Amounts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("المبلغ الأصلي", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(FormatUtils.formatCurrency(debt.originalAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Column {
                    Text("المدفوع", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(FormatUtils.formatCurrency(debt.paidAmount), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldProfit))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المتبقي المطلوب", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Text(
                        text = FormatUtils.formatCurrency(debt.remainingAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (debt.isSettled) SlateMuted else RubyExpense
                        )
                    )
                }
            }

            if (debt.dueDate > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "تاريخ الاستحقاق: ${FormatUtils.formatDate(debt.dueDate)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isOverdue) RubyExpense else SlateMuted,
                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    Text(
                        text = "تاريخ التسجيل: ${FormatUtils.formatDate(debt.createdAt)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (debt.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${debt.phone}")
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اتصال", fontSize = 11.sp)
                        }
                    }

                    if (!debt.isSettled) {
                        Button(
                            onClick = onRecordPayment,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("pay_debt_button_${debt.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسجيل دفعة سداد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = SlateMuted)
                }
            }
        }
    }
}
