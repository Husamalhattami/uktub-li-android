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
import com.example.data.model.ExchangeTransfer
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
    val accountTypes by viewModel.accountTypes.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf<ExchangeAccount?>(null) }
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
                icon = { Icon(Icons.Default.AddCard, contentDescription = null, tint = Color.White) },
                text = { Text("إضافة حساب صراف / محفظة", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = BrandNavy,
                modifier = Modifier.testTag("fab_add_exchange_account")
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Header KPI Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BrandNavy
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "إجمالي أرصدة الصرافين والمحافظ",
                                style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = FintechIndigoLight.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = "${accounts.size} حسابات مسجلة",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = FormatUtils.formatCurrency(totalExchangeBalance),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = FintechAmberLight
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(FintechEmeraldLight)
                                )
                                Text(
                                    text = "رصيد الكاش: ${FormatUtils.formatCurrency(cashBalance)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(FintechIndigoLight)
                                )
                                Text(
                                    text = "الإجمالي العام: ${FormatUtils.formatCurrency(totalExchangeBalance + cashBalance)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        text = "قائمة حسابات الصرافين والمحافظ (${accounts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                    )
                }
            }

            if (accounts.isEmpty()) {
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
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = SlateTextMuted, modifier = Modifier.size(48.dp))
                            Text(
                                text = "لا توجد حسابات صرافة أو محافظ مسجلة بعد",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            )
                            Text(
                                text = "اضغط زر '+ إضافة حساب' لتسجيل حسابك لدى الكريمي، النجم، العمقي، أو محافظ كاش وجوالي",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateTextMuted)
                            )
                        }
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
                            color = SlateTextPrimary
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
    }

    // Add / Edit Account Dialog
    showAddEditDialog?.let { currentAcc ->
        var name by remember { mutableStateOf(currentAcc.name) }
        var accType by remember { mutableStateOf(currentAcc.type.ifBlank { accountTypes.firstOrNull() ?: "صراف" }) }
        var accNumber by remember { mutableStateOf(currentAcc.accountNumber) }
        var phone by remember { mutableStateOf(currentAcc.phone) }
        var initialBalanceStr by remember { mutableStateOf(if (currentAcc.id == 0L) "0" else currentAcc.balance.toLong().toString()) }
        var notes by remember { mutableStateOf(currentAcc.notes) }

        val commonPresets = listOf("الكريمي إكسبرس", "النجم للصرافة", "العمقي للصرافة", "محفظة جوالي", "محفظة كاش", "ون كاش", "محفظة فلوسك")

        StoreOperationDialog(
            onDismissRequest = { showAddEditDialog = null },
            title = if (currentAcc.id == 0L) "إضافة حساب صراف أو محفظة" else "تعديل بيانات الحساب",
            subtitle = "إدارة الحسابات البنكية والمحافظ الإلكترونية",
            icon = Icons.Default.AccountBalance,
            accentColor = FintechIndigo,
            confirmText = "حفظ الحساب",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_save_exchange_account_button",
            onConfirm = {
                val initBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                viewModel.saveExchangeAccount(
                    account = currentAcc.copy(
                        name = name,
                        type = accType,
                        accountNumber = accNumber,
                        phone = phone,
                        balance = if (currentAcc.id == 0L) initBal else currentAcc.balance,
                        notes = notes
                    ),
                    onSuccess = { showAddEditDialog = null }
                )
            }
        ) {
            DialogSectionHeader(title = "أسماء شائعة سريعة", icon = Icons.Default.Star, accentColor = PdfGoldPrimary)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(commonPresets) { preset ->
                    val isSelected = name == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { name = preset },
                        label = { Text(preset, fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PdfHeaderNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الصراف / المحفظة *") },
                leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exchange_account_name_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            DialogSectionHeader(title = "نوع الحساب", icon = Icons.Default.Category, accentColor = FintechIndigo)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(accountTypes) { typeOpt ->
                    FilterChip(
                        selected = accType == typeOpt,
                        onClick = { accType = typeOpt },
                        label = { Text(typeOpt, fontSize = 11.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FintechIndigo,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            OutlinedTextField(
                value = accNumber,
                onValueChange = { accNumber = it },
                label = { Text("رقم الحساب أو الآيبان") },
                leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null, tint = SlateTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم هاتف الصراف أو المحفظة") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (currentAcc.id == 0L) {
                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text("الرصيد الافتتاحي المتوفر حالياً (ريال)") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات (فرع، اسم الوكيل، الخ)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    // Transfer Dialog (Between Cash and Exchange or Between Accounts)
    showTransferDialog?.let { acc ->
        var amountStr by remember { mutableStateOf("") }
        var fromCashToExchange by remember { mutableStateOf(true) }
        var transferNotes by remember { mutableStateOf("") }
        var transferTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        StoreOperationDialog(
            onDismissRequest = { showTransferDialog = null },
            title = "مناقلة رصيد: ${acc.name}",
            subtitle = "الرصيد الحالي بالحساب: ${FormatUtils.formatCurrency(acc.balance)}",
            icon = Icons.Default.SwapHoriz,
            accentColor = FintechIndigo,
            confirmText = "تنفيذ المناقلة",
            confirmIcon = Icons.Default.SwapHoriz,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_transfer_button",
            onConfirm = {
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                viewModel.transferBetweenCashAndExchange(
                    accountId = acc.id,
                    amount = amt,
                    fromCashToExchange = fromCashToExchange,
                    notes = transferNotes,
                    timestamp = transferTimestamp,
                    onSuccess = { showTransferDialog = null }
                )
            }
        ) {
            DialogSectionHeader(title = "اختر اتجاه المناقلة", icon = Icons.Default.SwapHoriz, accentColor = FintechIndigo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = fromCashToExchange,
                    label = "إيداع بالصراف",
                    icon = Icons.Default.NorthEast,
                    selectedColor = FintechIndigo,
                    onClick = { fromCashToExchange = true },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = !fromCashToExchange,
                    label = "سحب للصندوق",
                    icon = Icons.Default.SouthWest,
                    selectedColor = FintechEmerald,
                    onClick = { fromCashToExchange = false },
                    modifier = Modifier.weight(1f)
                )
            }

            DialogInfoBanner(
                title = if (fromCashToExchange) "المتاح بالصندوق النقدي:" else "المتاح في ${acc.name}:",
                value = FormatUtils.formatCurrency(if (fromCashToExchange) cashBalance else acc.balance),
                containerColor = Color(0xFFEFF6FF),
                contentColor = Color(0xFF1E3A8A),
                borderColor = Color(0xFFBFDBFE),
                icon = Icons.Default.AccountBalanceWallet
            )

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ المحول (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechIndigo) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(FintechIndigo),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transfer_amount_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = transferNotes,
                onValueChange = { transferNotes = it },
                label = { Text("ملاحظات (رقم الحوالة، اسم المرسل...)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            TransactionDatePickerField(
                selectedTimestamp = transferTimestamp,
                onDateSelected = { transferTimestamp = it },
                label = "تاريخ التحويل / المناقلة",
                testTag = "exchange_transfer_date_picker"
            )
        }
    }

    // Delete Account Confirmation Dialog
    accountToDelete?.let { acc ->
        StoreOperationDialog(
            onDismissRequest = { accountToDelete = null },
            title = "تأكيد حذف حساب الصراف",
            subtitle = acc.name,
            icon = Icons.Default.Warning,
            accentColor = FintechRose,
            confirmText = "تأكيد الحذف",
            confirmIcon = Icons.Default.DeleteOutline,
            confirmColor = FintechRose,
            confirmTestTag = "confirm_delete_account_button",
            onConfirm = {
                viewModel.deleteExchangeAccount(acc.id)
                accountToDelete = null
            }
        ) {
            DialogInfoBanner(
                title = "الرصيد المسجل في الحساب:",
                value = FormatUtils.formatCurrency(acc.balance),
                containerColor = Color(0xFFFFF1F2),
                contentColor = FintechRose,
                borderColor = Color(0xFFFECDD3),
                icon = Icons.Default.Warning
            )
            Text(
                text = "هل أنت متأكد من حذف حساب (${acc.name})؟ سيتم إزالة بيانات الحساب من قائمة الحسابات.",
                fontSize = 13.sp,
                color = SlateTextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ExchangeAccountCard(
    account: ExchangeAccount,
    onTransferClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SlateCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(FintechIndigoBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = FintechIndigo,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                        )
                        val extra = buildList {
                            if (account.accountNumber.isNotBlank()) add("حساب: ${account.accountNumber}")
                            if (account.phone.isNotBlank()) add("هاتف: ${account.phone}")
                        }.joinToString(" | ")
                        if (extra.isNotBlank()) {
                            Text(
                                text = extra,
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary)
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = BrandBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = FintechRose,
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
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateTextSecondary)
                    )
                    Text(
                        text = FormatUtils.formatCurrency(account.balance),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (account.balance >= 0) FintechEmerald else FintechRose
                        )
                    )
                }

                Button(
                    onClick = onTransferClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FintechIndigo),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مناقلة رصيد", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ExchangeTransferRow(transfer: ExchangeTransfer) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SlateCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
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
                        .background(if (transfer.isCredit) FintechEmeraldBg else FintechRoseBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transfer.isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (transfer.isCredit) FintechEmerald else FintechRose,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = transfer.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                    )
                    Text(
                        text = "${transfer.accountName} | ${FormatUtils.formatDateTime(transfer.timestamp)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted)
                    )
                    if (transfer.notes.isNotBlank()) {
                        Text(
                            text = transfer.notes,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary, fontSize = 11.sp)
                        )
                    }
                }
            }

            Text(
                text = (if (transfer.isCredit) "+ " else "- ") + FormatUtils.formatCurrency(transfer.amount),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (transfer.isCredit) FintechEmerald else FintechRose
                )
            )
        }
    }
}
