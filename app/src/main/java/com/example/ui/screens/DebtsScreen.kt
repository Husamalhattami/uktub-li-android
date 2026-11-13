package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DebtPayment
import com.example.data.model.DebtRecord
import com.example.data.model.ExchangeAccount
import com.example.data.model.PartyAccount
import com.example.ui.StoreViewModel
import com.example.ui.components.CollectionReceiptDialog
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
    val debtPayments by viewModel.debtPayments.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Customers, 1: Suppliers, 2: Direct Debts Log

    // Dialog states
    var showAddPartyDialog by remember { mutableStateOf(false) }
    var addPartyDefaultType by remember { mutableStateOf("CUSTOMER") }
    var editingParty by remember { mutableStateOf<PartyAccount?>(null) }
    var statementParty by remember { mutableStateOf<PartyAccount?>(null) }
    var payingForParty by remember { mutableStateOf<Pair<PartyAccount, Double>?>(null) }
    var addingDebtForParty by remember { mutableStateOf<PartyAccount?>(null) }

    // Direct debt item payment & editing
    var payingDirectDebt by remember { mutableStateOf<DebtRecord?>(null) }
    var editingDirectDebt by remember { mutableStateOf<DebtRecord?>(null) }
    var showDirectAddDebtDialog by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }

    // Search and filter states
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") } // ALL, INDEBTED, SETTLED
    var showSettledDirectDebts by remember { mutableStateOf(false) }

    // Helper map of party debts
    val customerDebtsMap = remember(debts) {
        debts.filter { it.type == "CUSTOMER" }
            .groupBy { it.partyName.trim().lowercase() }
    }
    val supplierDebtsMap = remember(debts) {
        debts.filter { it.type == "SUPPLIER" }
            .groupBy { it.partyName.trim().lowercase() }
    }

    val customerParties = remember(parties) {
        parties.filter { it.type == "CUSTOMER" }
    }
    val supplierParties = remember(parties) {
        parties.filter { it.type == "SUPPLIER" }
    }

    val totalActiveCustomerDebt = remember(debts) {
        debts.filter { it.type == "CUSTOMER" && !it.isSettled }.sumOf { it.remainingAmount }
    }
    val totalActiveSupplierDebt = remember(debts) {
        debts.filter { it.type == "SUPPLIER" && !it.isSettled }.sumOf { it.remainingAmount }
    }

    val indebtedCustomersCount = remember(customerParties, customerDebtsMap) {
        customerParties.count { party ->
            val pDebts = customerDebtsMap[party.name.trim().lowercase()] ?: emptyList()
            pDebts.filter { !it.isSettled }.sumOf { it.remainingAmount } > 0.01
        }
    }
    val indebtedSuppliersCount = remember(supplierParties, supplierDebtsMap) {
        supplierParties.count { party ->
            val pDebts = supplierDebtsMap[party.name.trim().lowercase()] ?: emptyList()
            pDebts.filter { !it.isSettled }.sumOf { it.remainingAmount } > 0.01
        }
    }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "إدارة حسابات العملاء والموردين والديون",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            addPartyDefaultType = "CUSTOMER"
                            showAddPartyDialog = true
                        }
                        1 -> {
                            addPartyDefaultType = "SUPPLIER"
                            showAddPartyDialog = true
                        }
                        else -> {
                            showDirectAddDebtDialog = true
                        }
                    }
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = {
                    Text(
                        when (selectedTab) {
                            0 -> "فتح حساب عميل (زلط سابقة)"
                            1 -> "فتح حساب مورد (عليا زلط)"
                            else -> "تسجيل دين مباشر"
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_account_or_debt")
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
                    .fillMaxHeight()
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Button(
                        onClick = { showCollectionDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_debts_quick_collect"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("تحصيل زلط / إصدار سند قبض (اختياري أو تلقائي)", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            // Top Balance Summaries
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = GoldContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("لنا عند العملاء", style = MaterialTheme.typography.labelMedium.copy(color = LuxuryGold, fontWeight = FontWeight.Bold))
                                Surface(shape = CircleShape, color = LuxuryGoldLight) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = LuxuryGold, modifier = Modifier.padding(4.dp).size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalActiveCustomerDebt),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LuxuryGold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "عليهم زلط ($indebtedCustomersCount عميل مدين)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = LuxuryGold.copy(alpha = 0.8f))
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RubyLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("علينا للموردين", style = MaterialTheme.typography.labelMedium.copy(color = RubyExpense, fontWeight = FontWeight.Bold))
                                Surface(shape = CircleShape, color = RubyLight) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = RubyExpense, modifier = Modifier.padding(4.dp).size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = FormatUtils.formatCurrency(totalActiveSupplierDebt),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = RubyExpense)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "عليا زلط لهم ($indebtedSuppliersCount مورد مستحق)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = RubyExpense.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
            }

            // Tab Navigation
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
                        text = { Text("العملاء (${customerParties.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        modifier = Modifier.testTag("tab_customer_accounts")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("الموردين (${supplierParties.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        modifier = Modifier.testTag("tab_supplier_accounts")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("سجل الذمم (${debts.count { !it.isSettled }})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        modifier = Modifier.testTag("tab_all_debts_log")
                    )
                }
            }

            // Search Bar & Filter Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                when (selectedTab) {
                                    0 -> "ابحث باسم العميل أو رقم الهاتف..."
                                    1 -> "ابحث باسم المورد أو رقم الهاتف..."
                                    else -> "ابحث في سجل الذمم والفواتير..."
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_parties_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (selectedTab == 0 || selectedTab == 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = filterStatus == "ALL",
                                    onClick = { filterStatus = "ALL" },
                                    label = { Text("الكل", maxLines = 1) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filterStatus == "INDEBTED",
                                    onClick = { filterStatus = "INDEBTED" },
                                    label = {
                                        Text(if (selectedTab == 0) "عليهم زلط (مدينون)" else "عليا زلط لهم (مستحق)", maxLines = 1)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (selectedTab == 0) GoldContainer else RubyLight,
                                        selectedLabelColor = if (selectedTab == 0) LuxuryGold else RubyExpense
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filterStatus == "SETTLED",
                                    onClick = { filterStatus = "SETTLED" },
                                    label = { Text("خالص / مسدد", maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldLight,
                                        selectedLabelColor = EmeraldProfit
                                    )
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("إظهار الفواتير والديون المسددة", fontSize = 12.sp, color = SlateMuted)
                            Switch(
                                checked = showSettledDirectDebts,
                                onCheckedChange = { showSettledDirectDebts = it }
                            )
                        }
                    }
                }
            }

            // Tab 0: Customers Accounts
            if (selectedTab == 0) {
                val filteredCustomers = customerParties.filter { party ->
                    val matchQuery = searchQuery.isBlank() ||
                            party.name.contains(searchQuery, ignoreCase = true) ||
                            party.phone.contains(searchQuery, ignoreCase = true)

                    val pDebts = customerDebtsMap[party.name.trim().lowercase()] ?: emptyList()
                    val totalDebt = pDebts.filter { !it.isSettled }.sumOf { it.remainingAmount }
                    val isIndebted = totalDebt > 0.01

                    val matchStatus = when (filterStatus) {
                        "INDEBTED" -> isIndebted
                        "SETTLED" -> !isIndebted
                        else -> true
                    }
                    matchQuery && matchStatus
                }

                if (filteredCustomers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.PersonSearch, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(48.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك." else "لا توجد حسابات عملاء مسجلة حالياً.",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                                )
                                Button(
                                    onClick = {
                                        addPartyDefaultType = "CUSTOMER"
                                        showAddPartyDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح حساب عميل جديد (تسجيل زلط سابقة)")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredCustomers, key = { it.id }) { party ->
                        val pDebts = customerDebtsMap[party.name.trim().lowercase()] ?: emptyList()
                        val activeDebts = pDebts.filter { !it.isSettled }
                        val remainingDebt = activeDebts.sumOf { it.remainingAmount }

                        PartyAccountCard(
                            party = party,
                            remainingDebt = remainingDebt,
                            isCustomer = true,
                            onCollectOrPay = { payingForParty = Pair(party, remainingDebt) },
                            onShowStatement = { statementParty = party },
                            onAddDebt = { addingDebtForParty = party },
                            onEdit = { editingParty = party },
                            onDelete = { viewModel.deleteParty(party.id) }
                        )
                    }
                }
            }

            // Tab 1: Suppliers Accounts
            else if (selectedTab == 1) {
                val filteredSuppliers = supplierParties.filter { party ->
                    val matchQuery = searchQuery.isBlank() ||
                            party.name.contains(searchQuery, ignoreCase = true) ||
                            party.phone.contains(searchQuery, ignoreCase = true)

                    val pDebts = supplierDebtsMap[party.name.trim().lowercase()] ?: emptyList()
                    val totalDebt = pDebts.filter { !it.isSettled }.sumOf { it.remainingAmount }
                    val isIndebted = totalDebt > 0.01

                    val matchStatus = when (filterStatus) {
                        "INDEBTED" -> isIndebted
                        "SETTLED" -> !isIndebted
                        else -> true
                    }
                    matchQuery && matchStatus
                }

                if (filteredSuppliers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = SlateMuted, modifier = Modifier.size(48.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك." else "لا توجد حسابات موردين مسجلة حالياً.",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                                )
                                Button(
                                    onClick = {
                                        addPartyDefaultType = "SUPPLIER"
                                        showAddPartyDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فتح حساب مورد جديد (تسجيل عليا زلط)")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredSuppliers, key = { it.id }) { party ->
                        val pDebts = supplierDebtsMap[party.name.trim().lowercase()] ?: emptyList()
                        val activeDebts = pDebts.filter { !it.isSettled }
                        val remainingDebt = activeDebts.sumOf { it.remainingAmount }

                        PartyAccountCard(
                            party = party,
                            remainingDebt = remainingDebt,
                            isCustomer = false,
                            onCollectOrPay = { payingForParty = Pair(party, remainingDebt) },
                            onShowStatement = { statementParty = party },
                            onAddDebt = { addingDebtForParty = party },
                            onEdit = { editingParty = party },
                            onDelete = { viewModel.deleteParty(party.id) }
                        )
                    }
                }
            }

            // Tab 2: Direct Debts Registry
            else {
                val filteredDebts = debts.filter { debt ->
                    val matchSettled = showSettledDirectDebts || !debt.isSettled
                    val matchQuery = searchQuery.isBlank() ||
                            debt.partyName.contains(searchQuery, ignoreCase = true) ||
                            debt.details.contains(searchQuery, ignoreCase = true) ||
                            debt.phone.contains(searchQuery, ignoreCase = true)
                    matchSettled && matchQuery
                }

                if (filteredDebts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "لا توجد سجلات ذمم أو ديون مطابقة.",
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                            )
                        }
                    }
                } else {
                    items(filteredDebts, key = { it.id }) { debt ->
                        DebtItemCard(
                            debt = debt,
                            onRecordPayment = { payingDirectDebt = debt },
                            onEdit = { editingDirectDebt = debt },
                            onDelete = { viewModel.deleteDebt(debt.id) }
                        )
                    }
                }
            }

            // Bottom space for FAB
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
        }
    }

    // --- DIALOG: Add / Open New Account (Customer or Supplier) ---
    if (showAddPartyDialog) {
        AddOrEditPartyDialog(
            initialType = addPartyDefaultType,
            existingParty = null,
            onDismiss = { showAddPartyDialog = false },
            onSave = { party, initialDebt, debtNotes ->
                viewModel.saveParty(
                    party = party,
                    initialDebt = initialDebt,
                    debtNotes = debtNotes,
                    onSuccess = { showAddPartyDialog = false }
                )
            }
        )
    }

    // --- DIALOG: Edit Existing Account ---
    editingParty?.let { party ->
        AddOrEditPartyDialog(
            initialType = party.type,
            existingParty = party,
            onDismiss = { editingParty = null },
            onSave = { updatedParty, _, _ ->
                viewModel.saveParty(
                    party = updatedParty,
                    onSuccess = { editingParty = null }
                )
            }
        )
    }

    // --- DIALOG: Pay or Collect for a Party Account (Cash Drawer or Exchange Account) ---
    payingForParty?.let { (party, totalDebt) ->
        PartyPaymentDialog(
            party = party,
            totalDebt = totalDebt,
            exchangeAccounts = exchangeAccounts,
            onDismiss = { payingForParty = null },
            onConfirm = { amount, paymentMethod, exchangeAcc, notes, transferRef ->
                viewModel.recordPartyPayment(
                    partyName = party.name,
                    partyType = party.type,
                    totalPayment = amount,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAcc,
                    referenceNumber = transferRef,
                    onSuccess = { payingForParty = null }
                )
            }
        )
    }

    // --- DIALOG: Add additional debt directly to party ---
    addingDebtForParty?.let { party ->
        var amountStr by remember { mutableStateOf("") }
        var notesStr by remember { mutableStateOf("") }
        var dueDateDays by remember { mutableStateOf("15") }
        val isCustomer = party.type == "CUSTOMER"

        AlertDialog(
            onDismissRequest = { addingDebtForParty = null },
            title = {
                Text(
                    text = if (isCustomer) "تسجيل دين جديد على العميل: ${party.name}" else "تسجيل دين جديد للمورد: ${party.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ المطلوب (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("add_debt_amount_input")
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("تفاصيل البضاعة / سبب الدين") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dueDateDays,
                        onValueChange = { dueDateDays = it },
                        label = { Text("مدة السداد المتوقعة (بالأيام)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        val days = dueDateDays.toLongOrNull() ?: 15L
                        val now = System.currentTimeMillis()
                        viewModel.addNewDebt(
                            DebtRecord(
                                type = party.type,
                                partyName = party.name,
                                phone = party.phone,
                                originalAmount = amount,
                                paidAmount = 0.0,
                                remainingAmount = amount,
                                details = notesStr.ifBlank { if (isCustomer) "دين إضافي على العميل" else "دين إضافي للمورد" },
                                dueDate = now + 86400000L * days,
                                createdAt = now,
                                isSettled = false
                            ),
                            onSuccess = { addingDebtForParty = null }
                        )
                    },
                    modifier = Modifier.testTag("submit_add_party_debt_btn")
                ) {
                    Text("حفظ الدين")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { addingDebtForParty = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- DIALOG: Account Statement (كشف حساب مفصل) ---
    statementParty?.let { party ->
        val partyDebts = remember(debts, party) {
            debts.filter { it.partyName.trim().equals(party.name.trim(), ignoreCase = true) && it.type == party.type }
        }
        val partyPayments = remember(debtPayments, party) {
            debtPayments.filter { it.partyName.trim().equals(party.name.trim(), ignoreCase = true) }
        }

        PartyStatementDialog(
            party = party,
            debts = partyDebts,
            payments = partyPayments,
            onDismiss = { statementParty = null }
        )
    }

    // --- DIALOG: Direct Single Debt Payment ---
    payingDirectDebt?.let { debt ->
        var paymentAmountStr by remember { mutableStateOf(debt.remainingAmount.toLong().toString()) }
        var paymentNotes by remember { mutableStateOf("دفعة سداد") }
        var paymentMethod by remember { mutableStateOf("نقداً") }
        var selectedExchangeAcc by remember { mutableStateOf<ExchangeAccount?>(null) }
        var paymentTransferRef by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { payingDirectDebt = null },
            title = {
                Text(
                    text = if (debt.type == "CUSTOMER") "استلام دفعة من: ${debt.partyName}" else "تسديد دفعة للمورد: ${debt.partyName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "المبلغ المتبقي حالياً: ${FormatUtils.formatCurrency(debt.remainingAmount)}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = LuxuryGold, fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = paymentAmountStr,
                        onValueChange = { paymentAmountStr = it },
                        label = { Text("مبلغ الدفعة (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("direct_debt_payment_input")
                    )

                    QuickThousandAdder(
                        currentValue = paymentAmountStr,
                        onValueChange = { paymentAmountStr = it }
                    )

                    // Payment Method selector (Cash Drawer vs Exchange Account)
                    Text("طريقة استلام/سداد المبلغ:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paymentMethod == "نقداً",
                            onClick = { paymentMethod = "نقداً" },
                            label = { Text(if (debt.type == "CUSTOMER") "نقداً بالصندوق" else "نقداً من الصندوق", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMethod == "صرافة/محفظة",
                            onClick = {
                                paymentMethod = "صرافة/محفظة"
                                if (selectedExchangeAcc == null && exchangeAccounts.isNotEmpty()) {
                                    selectedExchangeAcc = exchangeAccounts.first()
                                }
                            },
                            label = { Text("عبر الصرافة/المحفظة", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (paymentMethod == "صرافة/محفظة" && exchangeAccounts.isNotEmpty()) {
                        Text("اختر حساب الصراف / المحفظة:", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(exchangeAccounts) { acc ->
                                FilterChip(
                                    selected = selectedExchangeAcc?.id == acc.id,
                                    onClick = { selectedExchangeAcc = acc },
                                    label = { Text(acc.name, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = paymentTransferRef,
                        onValueChange = { paymentTransferRef = it },
                        label = { Text("رقم السند (اختياري)") },
                        placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SND-0001)") },
                        leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = LuxuryGold, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth().testTag("direct_debt_voucher_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("ملاحظات السداد") },
                        modifier = Modifier.fillMaxWidth()
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
                            paymentMethod = paymentMethod,
                            exchangeAccount = selectedExchangeAcc,
                            referenceNumber = paymentTransferRef.trim(),
                            onSuccess = { payingDirectDebt = null }
                        )
                    },
                    modifier = Modifier.testTag("confirm_direct_debt_payment_btn")
                ) {
                    Text("تأكيد السداد")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { payingDirectDebt = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- DIALOG: Edit Direct Debt Record ---
    editingDirectDebt?.let { debt ->
        var partyName by remember { mutableStateOf(debt.partyName) }
        var phone by remember { mutableStateOf(debt.phone) }
        var amountStr by remember { mutableStateOf(debt.originalAmount.toLong().toString()) }
        var details by remember { mutableStateOf(debt.details) }

        AlertDialog(
            onDismissRequest = { editingDirectDebt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (debt.type == "CUSTOMER") "تعديل دين العميل: ${debt.partyName}" else "تعديل دين المورد: ${debt.partyName}",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it },
                        label = { Text("الاسم") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("المبلغ الأصلي للدين (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    QuickThousandAdder(
                        currentValue = amountStr,
                        onValueChange = { amountStr = it }
                    )

                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("البيان / تفاصيل البضاعة") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newOriginal = amountStr.toDoubleOrNull() ?: debt.originalAmount
                        if (newOriginal <= 0) {
                            viewModel.showMessage("يرجى إدخال مبلغ دين صحيح أكبر من صفر!")
                            return@Button
                        }
                        val newRemaining = (newOriginal - debt.paidAmount).coerceAtLeast(0.0)
                        viewModel.updateDebt(
                            debt.copy(
                                partyName = partyName.trim(),
                                phone = phone.trim(),
                                originalAmount = newOriginal,
                                remainingAmount = newRemaining,
                                details = details.trim(),
                                isSettled = newRemaining <= 0.01
                            ),
                            onSuccess = { editingDirectDebt = null }
                        )
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingDirectDebt = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // --- DIALOG: Direct Single Debt Creation ---
    if (showDirectAddDebtDialog) {
        var partyName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var details by remember { mutableStateOf("") }
        var debtType by remember { mutableStateOf(if (selectedTab == 1) "SUPPLIER" else "CUSTOMER") }

        AlertDialog(
            onDismissRequest = { showDirectAddDebtDialog = false },
            title = {
                Text(
                    text = if (debtType == "CUSTOMER") "تسجيل دين مباشر على زبون" else "تسجيل دين مباشر لمورد",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = debtType == "CUSTOMER",
                            onClick = { debtType = "CUSTOMER" },
                            label = { Text("دين على زبون (لنا)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = debtType == "SUPPLIER",
                            onClick = { debtType = "SUPPLIER" },
                            label = { Text("دين لمورد (عليا)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it },
                        label = { Text(if (debtType == "CUSTOMER") "اسم الزبون" else "اسم المورد") },
                        modifier = Modifier.fillMaxWidth().testTag("new_direct_debt_name")
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
                        modifier = Modifier.fillMaxWidth().testTag("new_direct_debt_amount")
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
                                type = debtType,
                                partyName = partyName,
                                phone = phone,
                                originalAmount = amount,
                                paidAmount = 0.0,
                                remainingAmount = amount,
                                details = details,
                                dueDate = System.currentTimeMillis() + 86400000L * 15,
                                isSettled = false
                            ),
                            onSuccess = { showDirectAddDebtDialog = false }
                        )
                    },
                    modifier = Modifier.testTag("submit_direct_debt_btn")
                ) {
                    Text("حفظ الدين")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDirectAddDebtDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showCollectionDialog) {
        CollectionReceiptDialog(
            customerParties = customerParties,
            customerDebts = debts,
            exchangeAccounts = exchangeAccounts,
            onDismiss = { showCollectionDialog = false },
            onConfirm = { payerName, party, amount, voucher, method, account, notes ->
                viewModel.recordCollectionReceipt(
                    payerName = payerName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucher,
                    paymentMethod = method,
                    exchangeAccount = account,
                    notes = notes
                ) {
                    showCollectionDialog = false
                }
            }
        )
    }
}

// ----------------------------------------------------
// Component: Party Account Card (Customer or Supplier)
// ----------------------------------------------------
@Composable
fun PartyAccountCard(
    party: PartyAccount,
    remainingDebt: Double,
    isCustomer: Boolean,
    onCollectOrPay: () -> Unit,
    onShowStatement: () -> Unit,
    onAddDebt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val hasDebt = remainingDebt > 0.01

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Avatar, Name, Phone, and Balance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCustomer) GoldContainer else RubyLight,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCustomer) Icons.Default.Person else Icons.Default.Storefront,
                                contentDescription = null,
                                tint = if (isCustomer) LuxuryGold else RubyExpense,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = party.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (party.phone.isNotBlank()) {
                            Text(
                                text = "هاتف: ${party.phone}",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                        }
                        if (party.address.isNotBlank() || party.notes.isNotBlank()) {
                            val subtext = listOf(party.address, party.notes).filter { it.isNotBlank() }.joinToString(" • ")
                            Text(
                                text = subtext,
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Balance Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasDebt) {
                        if (isCustomer) GoldContainer else RubyLight
                    } else {
                        EmeraldLight
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (hasDebt) {
                                if (isCustomer) "عليه زلط" else "عليا زلط له"
                            } else {
                                "الحساب خالص"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (hasDebt) {
                                    if (isCustomer) LuxuryGold else RubyExpense
                                } else {
                                    EmeraldProfit
                                }
                            )
                        )
                        if (hasDebt) {
                            Text(
                                text = FormatUtils.formatCurrency(remainingDebt),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCustomer) LuxuryGold else RubyExpense
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Pay / Collect Button
                    if (hasDebt) {
                        Button(
                            onClick = onCollectOrPay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCustomer) LuxuryGold else RubyExpense
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_pay_collect_${party.id}")
                        ) {
                            Icon(
                                imageVector = if (isCustomer) Icons.Default.Download else Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCustomer) "قبض زلط" else "سداد دفعة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Statement Button (كشف الحساب)
                    OutlinedButton(
                        onClick = onShowStatement,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_statement_${party.id}")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("كشف الحساب", fontSize = 11.sp)
                    }

                    // Add Debt Button
                    IconButton(
                        onClick = onAddDebt,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "إضافة دين", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Call Button
                    if (party.phone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${party.phone}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال", tint = EmeraldProfit)
                        }
                    }
                }

                // Edit and Delete Menu
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = SlateMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = SlateMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// Component: Add or Edit Party Dialog
// ----------------------------------------------------
@Composable
fun AddOrEditPartyDialog(
    initialType: String,
    existingParty: PartyAccount?,
    onDismiss: () -> Unit,
    onSave: (PartyAccount, Double, String) -> Unit
) {
    var partyType by remember { mutableStateOf(existingParty?.type ?: initialType) }
    var name by remember { mutableStateOf(existingParty?.name ?: "") }
    var phone by remember { mutableStateOf(existingParty?.phone ?: "") }
    var address by remember { mutableStateOf(existingParty?.address ?: "") }
    var notes by remember { mutableStateOf(existingParty?.notes ?: "") }

    // Opening Balance / Previous debt (زلط سابقة)
    var hasInitialDebt by remember { mutableStateOf(false) }
    var initialDebtAmountStr by remember { mutableStateOf("") }
    var initialDebtNotes by remember { mutableStateOf("") }

    val isNew = existingParty == null
    val isCustomer = partyType == "CUSTOMER"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) {
                    if (isCustomer) "فتح حساب عميل جديد" else "فتح حساب مورد جديد"
                } else {
                    if (isCustomer) "تعديل بيانات العميل" else "تعديل بيانات المورد"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isNew) {
                    // Type selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isCustomer,
                            onClick = { partyType = "CUSTOMER" },
                            label = { Text("عميل (زبون)") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isCustomer,
                            onClick = { partyType = "SUPPLIER" },
                            label = { Text("مورد (معمل)") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isCustomer) "اسم العميل الكامل *" else "اسم المورد / المعمل *") },
                    modifier = Modifier.fillMaxWidth().testTag("party_dialog_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف (للتواصل واتصال مباشر)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان / المدينة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Opening Balance Section (Only for new accounts)
                if (isNew) {
                    HorizontalDivider(color = SlateBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isCustomer) "يوجد رصيد سابق (عليه زلط سابقة)؟" else "يوجد رصيد سابق (عليا زلط للمورد)؟",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "تسجيل دين وافتتاح الحساب به تلقائياً",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = SlateMuted)
                            )
                        }
                        Switch(
                            checked = hasInitialDebt,
                            onCheckedChange = { hasInitialDebt = it }
                        )
                    }

                    if (hasInitialDebt) {
                        OutlinedTextField(
                            value = initialDebtAmountStr,
                            onValueChange = { initialDebtAmountStr = it },
                            label = { Text(if (isCustomer) "المبلغ المطلوب منه بالريال (عليه زلط)" else "المبلغ المستحق له بالريال (عليا زلط)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("party_dialog_debt_amount_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        QuickThousandAdder(
                            currentValue = initialDebtAmountStr,
                            onValueChange = { initialDebtAmountStr = it }
                        )

                        OutlinedTextField(
                            value = initialDebtNotes,
                            onValueChange = { initialDebtNotes = it },
                            label = { Text("تفاصيل الرصيد السابق (اختياري)") },
                            placeholder = { Text("مثال: حساب بضاعة سابقة قبل النظام") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val initialDebt = if (hasInitialDebt) initialDebtAmountStr.toDoubleOrNull() ?: 0.0 else 0.0
                    val party = (existingParty ?: PartyAccount(
                        type = partyType,
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        initialBalance = initialDebt
                    )).copy(
                        type = partyType,
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                    onSave(party, initialDebt, initialDebtNotes)
                },
                modifier = Modifier.testTag("btn_save_party_account")
            ) {
                Text(if (isNew) "فتح الحساب وحفظه" else "حفظ التعديلات")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ----------------------------------------------------
// Component: Party Payment Dialog (Payment / Collection)
// ----------------------------------------------------
@Composable
fun PartyPaymentDialog(
    party: PartyAccount,
    totalDebt: Double,
    exchangeAccounts: List<ExchangeAccount>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, paymentMethod: String, exchangeAccount: ExchangeAccount?, notes: String, transferRef: String) -> Unit
) {
    val isCustomer = party.type == "CUSTOMER"
    var amountStr by remember { mutableStateOf(totalDebt.toLong().toString()) }
    var paymentMethod by remember { mutableStateOf("نقداً") } // "نقداً" or "صرافة/محفظة"
    var selectedExchangeAcc by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var transferRef by remember { mutableStateOf("") }
    var notes by remember {
        mutableStateOf(if (isCustomer) "قبض زلط نقدية من العميل" else "سداد دفعة للمورد")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isCustomer) "قبض زلط من: ${party.name}" else "سداد دفعة للمورد: ${party.name}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isCustomer) "إجمالي المبلغ المطلوب منه: ${FormatUtils.formatCurrency(totalDebt)}" else "إجمالي المستحق للمورد: ${FormatUtils.formatCurrency(totalDebt)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCustomer) LuxuryGold else RubyExpense
                    )
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("المبلغ المدفوع (ريال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("party_payment_amount_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                QuickThousandAdder(
                    currentValue = amountStr,
                    onValueChange = { amountStr = it }
                )

                // Method selector
                Text(
                    text = if (isCustomer) "طريقة إيداع الزلط:" else "طريقة صرف الزلط للمورد:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMethod == "نقداً",
                        onClick = { paymentMethod = "نقداً" },
                        label = { Text(if (isCustomer) "نقداً للصندوق" else "نقداً من الصندوق", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentMethod == "صرافة/محفظة",
                        onClick = {
                            paymentMethod = "صرافة/محفظة"
                            if (selectedExchangeAcc == null && exchangeAccounts.isNotEmpty()) {
                                selectedExchangeAcc = exchangeAccounts.first()
                            }
                        },
                        label = { Text("عبر الصرافة/المحفظة", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (paymentMethod == "صرافة/محفظة") {
                    if (exchangeAccounts.isEmpty()) {
                        Text(
                            text = "تنبيه: لا يوجد حساب صراف مضاف! يرجى إضافة حساب صراف أولاً.",
                            color = RubyExpense,
                            fontSize = 11.sp
                        )
                    } else {
                        Text("اختر حساب الصراف أو المحفظة:", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(exchangeAccounts) { acc ->
                                FilterChip(
                                    selected = selectedExchangeAcc?.id == acc.id,
                                    onClick = { selectedExchangeAcc = acc },
                                    label = { Text(acc.name, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = transferRef,
                    onValueChange = { transferRef = it },
                    label = { Text("رقم السند (اختياري)") },
                    placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SND-0001)") },
                    modifier = Modifier.fillMaxWidth().testTag("party_payment_voucher_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات وتوثيق السداد") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text(
                    text = if (isCustomer) {
                        if (paymentMethod == "نقداً") "سيتم إضافة المبلغ تلقائياً إلى رصيد الصندوق النقدي." else "سيتم زيادة رصيد حساب الصراف المحدد تلقائياً."
                    } else {
                        if (paymentMethod == "نقداً") "سيتم خصم المبلغ تلقائياً من رصيد الصندوق النقدي." else "سيتم خصم المبلغ من رصيد حساب الصراف المحدد."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    onConfirm(amount, paymentMethod, selectedExchangeAcc, notes, transferRef)
                },
                modifier = Modifier.testTag("btn_confirm_party_payment")
            ) {
                Text(if (isCustomer) "تأكيد قبض الزلط" else "تأكيد سداد المورد")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ----------------------------------------------------
// Component: Party Statement Dialog (كشف حساب تفصيلي)
// ----------------------------------------------------
@Composable
fun PartyStatementDialog(
    party: PartyAccount,
    debts: List<DebtRecord>,
    payments: List<DebtPayment>,
    onDismiss: () -> Unit
) {
    val isCustomer = party.type == "CUSTOMER"
    val totalOriginal = debts.sumOf { it.originalAmount }
    val totalPaid = debts.sumOf { it.paidAmount }
    val remainingBalance = debts.filter { !it.isSettled }.sumOf { it.remainingAmount }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("كشف حساب: ${party.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (isCustomer) "عميل / زبون" else "مورد / معمل", style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إجمالي المسحوبات/المشتريات", fontSize = 10.sp, color = SlateMuted)
                            Text(FormatUtils.formatCurrency(totalOriginal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إجمالي المدفوع", fontSize = 10.sp, color = SlateMuted)
                            Text(FormatUtils.formatCurrency(totalPaid), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldProfit)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(if (isCustomer) "المتبقي (عليه زلط)" else "المتبقي (عليا زلط)", fontSize = 10.sp, color = SlateMuted)
                            Text(
                                FormatUtils.formatCurrency(remainingBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (remainingBalance > 0.01) RubyExpense else EmeraldProfit
                            )
                        }
                    }
                }

                Text("تفاصيل الفواتير والذمم المسجلة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                if (debts.isEmpty()) {
                    Text("لا توجد فواتير أو ديون سابقة لهذا الحساب.", color = SlateMuted, fontSize = 12.sp)
                } else {
                    debts.forEach { debt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = debt.details.ifBlank { "عملية دين / بضاعة" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (debt.isSettled) "مسدد" else "متبقي: ${FormatUtils.formatCurrency(debt.remainingAmount)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (debt.isSettled) EmeraldProfit else RubyExpense
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المبلغ الأصلي: ${FormatUtils.formatCurrency(debt.originalAmount)} | المدفوع: ${FormatUtils.formatCurrency(debt.paidAmount)}",
                                        fontSize = 10.sp,
                                        color = SlateMuted
                                    )
                                    Text(
                                        text = FormatUtils.formatDate(debt.createdAt),
                                        fontSize = 10.sp,
                                        color = SlateMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (payments.isNotEmpty()) {
                    Text("سجل الدفعات والمسددات الأخيرة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    payments.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EmeraldLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val vBadge = if (p.voucherNumber.isNotBlank()) "سند #${p.voucherNumber} • " else ""
                            Text(
                                text = "${vBadge}دفعة: ${FormatUtils.formatCurrency(p.amount)} (${p.paymentMethod})",
                                fontSize = 11.sp,
                                color = EmeraldProfit,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(FormatUtils.formatDate(p.timestamp), fontSize = 10.sp, color = SlateMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

// ----------------------------------------------------
// Component: Debt Item Card (Direct Registry)
// ----------------------------------------------------
@Composable
fun DebtItemCard(
    debt: DebtRecord,
    onRecordPayment: () -> Unit,
    onEdit: () -> Unit = {},
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
                        text = if (debt.isSettled) "مسدد بالكامل" else if (isOverdue) "متأخر السداد!" else if (isNearDue) "قرب موعد السداد" else if (debt.type == "SUPPLIER") "مستحق للمورد" else "مطلوب من الزبون",
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
                    Text(if (debt.type == "CUSTOMER") "المتبقي عليه" else "المتبقي له", style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted))
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
                            Text(if (debt.type == "CUSTOMER") "قبض دفعة" else "سداد دفعة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = BrandBlue)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = SlateMuted)
                    }
                }
            }
        }
    }
}
