package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.model.ExchangeAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun ExchangeAccountsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val accounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val transfers by viewModel.exchangeTransfers.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf<ExchangeAccount?>(null) } // null = closed, id=0 -> new, id>0 -> edit
    var showTransferDialog by remember { mutableStateOf<ExchangeAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<ExchangeAccount?>(null) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "الصرافين والمحافظ الإلكترونية",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showAddEditDialog = ExchangeAccount(
                        name = "",
                        accountNumber = "",
                        phone = "",
                        balance = 0.0,
                        notes = ""
                    )
                },
                icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                text = { Text("إضافة حساب صراف / محفظة") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_exchange_account")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header KPI Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "إجمالي أرصدة الصرافين والمحافظ",
                            style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatCurrency(totalExchangeBalance),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = LuxuryGoldLight
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "رصيد الصندوق النقدي (كاش): ${FormatUtils.formatCurrency(cashBalance)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldProfit.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "${accounts.size} حسابات نشطة",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Section: Accounts List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "قائمة حسابات الصرافين والمحافظ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "لا توجد حسابات صرافة أو محافظ مسجلة حتى الآن. اضغط زر الإضافة لتسجيل حساباتك (الكريمي، النجم، العمقي، جوالي، كاش...).",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                        )
                    }
                }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    ExchangeAccountCard(
                        account = acc,
                        onTransferClick = { showTransferDialog = acc },
                        onEditClick = { showAddEditDialog = acc },
                        onDeleteClick = { accountToDelete = acc }
                    )
                }
            }

            // Section: Transfers Ledger
            if (transfers.isNotEmpty()) {
                item {
                    Text(
                        text = "سجل التحويلات والمناقلات (آخر العمليات)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(transfers.take(15)) { tx ->
                    ExchangeTransferRow(tx)
                }
            }
        }
    }

    // Add / Edit Account Dialog
    showAddEditDialog?.let { currentAcc ->
        var name by remember { mutableStateOf(currentAcc.name) }
        var accNumber by remember { mutableStateOf(currentAcc.accountNumber) }
        var phone by remember { mutableStateOf(currentAcc.phone) }
        var initialBalanceStr by remember { mutableStateOf(if (currentAcc.id == 0L) "0" else currentAcc.balance.toLong().toString()) }
        var notes by remember { mutableStateOf(currentAcc.notes) }

        val commonPresets = listOf("الكريمي إكسبرس", "النجم للصرافة", "العمقي للصرافة", "محفظة جوالي", "محفظة كاش", "ون كاش", "محفظة فلوسك")

        AlertDialog(
            onDismissRequest = { showAddEditDialog = null },
            title = {
                Text(
                    text = if (currentAcc.id == 0L) "إضافة حساب صراف أو محفظة" else "تعديل بيانات الحساب",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("اختر اسم شائع أو اكتب الاسم:", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonPresets.take(3).forEach { preset ->
                            FilterChip(
                                selected = name == preset,
                                onClick = { name = preset },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الصراف / المحفظة *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exchange_account_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = accNumber,
                        onValueChange = { accNumber = it },
                        label = { Text("رقم الحساب أو الآيبان") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم هاتف الصراف أو المحفظة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (currentAcc.id == 0L) {
                        OutlinedTextField(
                            value = initialBalanceStr,
                            onValueChange = { initialBalanceStr = it },
                            label = { Text("الرصيد الافتتاحي المتوفر حالياً (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        QuickThousandAdder(
                            currentValue = initialBalanceStr,
                            onValueChange = { initialBalanceStr = it }
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات (فرع، اسم الوكيل، الخ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val initBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                        viewModel.saveExchangeAccount(
                            account = currentAcc.copy(
                                name = name,
                                accountNumber = accNumber,
                                phone = phone,
                                balance = if (currentAcc.id == 0L) initBal else currentAcc.balance,
                                notes = notes
                            ),
                            onSuccess = { showAddEditDialog = null }
                        )
                    },
                    modifier = Modifier.testTag("confirm_save_exchange_account")
                ) {
                    Text("حفظ الحساب")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddEditDialog = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Transfer (Cash <-> Exchange) Dialog
    showTransferDialog?.let { acc ->
        var amountStr by remember { mutableStateOf("") }
        var fromCashToExchange by remember { mutableStateOf(true) } // true: إيداع كاش للصراف, false: سحب كاش من الصراف للصندوق
        var transferNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTransferDialog = null },
            title = {
                Text(
                    text = "مناقلة / تحويل أموال مع ${acc.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الرصيد الحالي لدى الصراف: ${FormatUtils.formatCurrency(acc.balance)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "الرصيد في الصندوق النقدي: ${FormatUtils.formatCurrency(cashBalance)}",
                        style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { fromCashToExchange = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (fromCashToExchange) EmeraldProfit else SlateBorder,
                                contentColor = if (fromCashToExchange) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("إيداع للصراف (من الكاش)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { fromCashToExchange = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!fromCashToExchange) RubyExpense else SlateBorder,
                                contentColor = if (!fromCashToExchange) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("سحب من الصراف (للكاش)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ بالريال *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_amount_input"),
                        singleLine = true
                    )
                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = transferNotes,
                        onValueChange = { transferNotes = it },
                        label = { Text("ملاحظات أو رقم الإشعار / الحوالة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        viewModel.transferBetweenCashAndExchange(
                            accountId = acc.id,
                            amount = amt,
                            fromCashToExchange = fromCashToExchange,
                            notes = transferNotes,
                            onSuccess = { showTransferDialog = null }
                        )
                    },
                    modifier = Modifier.testTag("confirm_transfer_button")
                ) {
                    Text("تنفيذ المناقلة")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTransferDialog = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Account Confirmation Dialog
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = RubyExpense) },
            title = { Text("تأكيد حذف حساب الصراف", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل أنت متأكد من حذف حساب (${acc.name})؟ رصيده الحالي هو ${FormatUtils.formatCurrency(acc.balance)}. سيتم حذف بيانات الحساب.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExchangeAccount(acc.id)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { accountToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ExchangeAccountCard(
    account: ExchangeAccount,
    onTransferClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(LuxuryGoldLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MidnightNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val extra = buildList {
                            if (account.accountNumber.isNotBlank()) add("حساب: ${account.accountNumber}")
                            if (account.phone.isNotBlank()) add("هاتف: ${account.phone}")
                        }.joinToString(" | ")
                        if (extra.isNotBlank()) {
                            Text(
                                text = extra,
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = RubyExpense,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "الرصيد المتوفر لدى الصراف:",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(account.balance),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (account.balance >= 0) EmeraldProfit else RubyExpense
                        )
                    )
                }

                Button(
                    onClick = onTransferClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مناقلة / إيداع / سحب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ExchangeTransferRow(transfer: com.example.data.model.ExchangeTransfer) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (transfer.isCredit) EmeraldLight else RubyLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transfer.isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (transfer.isCredit) EmeraldProfit else RubyExpense,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = transfer.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${transfer.accountName} | ${FormatUtils.formatDateTime(transfer.timestamp)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                    if (transfer.notes.isNotBlank()) {
                        Text(
                            text = transfer.notes,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                        )
                    }
                }
            }

            Text(
                text = (if (transfer.isCredit) "+ " else "- ") + FormatUtils.formatCurrency(transfer.amount),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (transfer.isCredit) EmeraldProfit else RubyExpense
                )
            )
        }
    }
}
