package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.StoreViewModel
import com.example.ui.components.MovementItemRow
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun CashDrawerScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val allMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()

    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var showCloseShiftDialog by remember { mutableStateOf(false) }
    var showWithdrawalDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }

    val isOpen = activeShift?.isOpen == true

    val totalIncome = remember(allMovements) {
        allMovements.filter { it.isIncome }.sumOf { it.amount }
    }
    val totalExpense = remember(allMovements) {
        allMovements.filter { !it.isIncome }.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "إدارة الصندوق والورديات",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
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
            // Hero: Current Cash & Shift Status Card
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
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الرصيد الفعلي الموجود بالصندوق",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(cashBalance),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = LuxuryGoldLight,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOpen) EmeraldProfit.copy(alpha = 0.25f) else RubyExpense.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = if (isOpen) "وردية نشطة" else "الوردية مغلقة",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isOpen) EmeraldProfit else RubyExpense,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        if (isOpen && activeShift != null) {
                            val shift = activeShift!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "افتتاح: ${FormatUtils.formatTime(shift.openedAt)} | ${shift.cashierName}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f))
                                )
                                Text(
                                    text = "رصيد الافتتاح: ${FormatUtils.formatCurrency(shift.openingBalance)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("إجمالي المقبوضات (+)", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldProfit))
                                Text(FormatUtils.formatCurrency(totalIncome), style = MaterialTheme.typography.titleSmall.copy(color = EmeraldProfit, fontWeight = FontWeight.Bold))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("إجمالي المدفوعات (-)", style = MaterialTheme.typography.labelSmall.copy(color = RubyExpense))
                                Text(FormatUtils.formatCurrency(totalExpense), style = MaterialTheme.typography.titleSmall.copy(color = RubyExpense, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            // Quick Actions: Shift Open/Close & Owner Withdrawal / Deposit
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isOpen) {
                        Button(
                            onClick = { showCloseShiftDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("close_shift_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                        ) {
                            Icon(Icons.Default.LockClock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إغلاق الوردية", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { showOpenShiftDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_shift_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح وردية جديدة", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showWithdrawalDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("owner_withdrawal_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Output, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سحب المالك", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showDepositDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("owner_deposit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Input, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إيداع سيولة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Store Audit & Reset Card
            item {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(com.example.ui.AppScreen.STORE_AUDIT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_audit_from_drawer_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryGold)
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = LuxuryGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("معالج جرد المحل وتصفير/ضبط الأرصدة", fontWeight = FontWeight.Bold)
                }
            }

            // Ledger Title
            item {
                Text(
                    text = "سجل حركة أموال الصندوق",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (allMovements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "لا توجد حركات نقدية مسجلة في الصندوق بعد.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(allMovements, key = { it.id }) { movement ->
                    MovementItemRow(movement)
                }
            }
        }
    }

    // Open Shift Dialog
    if (showOpenShiftDialog) {
        var cashierName by remember { mutableStateOf("صاحب المحل") }
        var openingCashStr by remember { mutableStateOf("1500") }
        var notes by remember { mutableStateOf("افتتاح الوردية") }

        AlertDialog(
            onDismissRequest = { showOpenShiftDialog = false },
            title = { Text("فتح وردية جديدة بالصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = cashierName,
                        onValueChange = { cashierName = it },
                        label = { Text("اسم الكاشير / المسؤول") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openingCashStr,
                        onValueChange = { openingCashStr = it },
                        label = { Text("المبلغ النقدي الافتتاحي بالصندوق (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuickThousandAdder(
                        currentValue = openingCashStr,
                        onValueChange = { openingCashStr = it }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val openingBalance = openingCashStr.toDoubleOrNull() ?: 0.0
                        viewModel.openShift(cashierName, openingBalance, notes) {
                            showOpenShiftDialog = false
                        }
                    }
                ) {
                    Text("فتح الوردية")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOpenShiftDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Close Shift Dialog
    if (showCloseShiftDialog && activeShift != null) {
        val shift = activeShift!!
        var actualCashStr by remember { mutableStateOf(cashBalance.toLong().toString()) }
        var notes by remember { mutableStateOf("") }

        val actualNum = actualCashStr.toDoubleOrNull() ?: 0.0
        val difference = actualNum - cashBalance

        AlertDialog(
            onDismissRequest = { showCloseShiftDialog = false },
            title = { Text("إغلاق الوردية وجرد الصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("المبلغ المفترض رياضياً بالصندوق:", color = Color.White)
                            Text(
                                text = FormatUtils.formatCurrency(cashBalance),
                                style = MaterialTheme.typography.titleLarge.copy(color = LuxuryGoldLight, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = actualCashStr,
                        onValueChange = { actualCashStr = it },
                        label = { Text("المبلغ الفعلي الموجود باليد (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuickThousandAdder(
                        currentValue = actualCashStr,
                        onValueChange = { actualCashStr = it }
                    )

                    // Difference alert
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (difference == 0.0) EmeraldLight else if (difference > 0) GoldContainer else RubyLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (difference == 0.0) "الصندوق مطابق تماماً بدون عجز أو زيادة!"
                            else if (difference > 0) "يوجد زيادة بالصندوق بمقدار: + ${FormatUtils.formatCurrency(difference)}"
                            else "يوجد عجز بالصندوق بمقدار: ${FormatUtils.formatCurrency(difference)}",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (difference == 0.0) EmeraldProfit else if (difference > 0) LuxuryGold else RubyExpense
                            )
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إغلاق الوردية") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeShift(shift, actualNum, notes) {
                            showCloseShiftDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                ) {
                    Text("إغلاق وتوثيق الوردية")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseShiftDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Owner Withdrawal Dialog
    if (showWithdrawalDialog) {
        var amountStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("سحب شخصي لصاحب المحل") }

        AlertDialog(
            onDismissRequest = { showWithdrawalDialog = false },
            title = { Text("تسجيل سحب صاحب المحل من الصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("مبلغ السحب (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("السبب أو البيان") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordOwnerWithdrawal(amount, reason) {
                            showWithdrawalDialog = false
                        }
                    }
                ) {
                    Text("تأكيد السحب")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWithdrawalDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Owner Deposit Dialog
    if (showDepositDialog) {
        var amountStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("إيداع سيولة نقدية إضافية") }

        AlertDialog(
            onDismissRequest = { showDepositDialog = false },
            title = { Text("إيداع نقدي في الصندوق", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("مبلغ الإيداع (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("السبب أو البيان") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.recordManualDeposit(amount, reason) {
                            showDepositDialog = false
                        }
                    }
                ) {
                    Text("تأكيد الإيداع")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDepositDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
