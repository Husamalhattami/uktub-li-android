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
import com.example.data.model.PurchaseRecord
import com.example.data.model.SaleTransaction
import com.example.data.model.StoreSettings
import com.example.ui.StoreViewModel
import com.example.ui.components.CollectionReceiptDialog
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.DialogSectionHeader
import com.example.ui.components.DialogSegmentedChoice
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.SupplierPaymentDialog
import com.example.ui.components.TransactionDatePickerField
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper
import com.example.ui.util.StatementMode

@Composable
fun DebtsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val debtPayments by viewModel.debtPayments.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
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
    var collectionInitialParty by remember { mutableStateOf<PartyAccount?>(null) }
    var showSupplierPaymentDialog by remember { mutableStateOf(false) }
    var supplierPayInitialParty by remember { mutableStateOf<PartyAccount?>(null) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                collectionInitialParty = null
                                showCollectionDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_debts_quick_collect"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تحصيل عميل (قبض)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                supplierPayInitialParty = null
                                showSupplierPaymentDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_debts_quick_supplier_pay"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تسديد مورد (صرف)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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

                        val contextCard = LocalContext.current
                        PartyAccountCard(
                            party = party,
                            remainingDebt = remainingDebt,
                            isCustomer = true,
                            onCollectOrPay = {
                                collectionInitialParty = party
                                showCollectionDialog = true
                            },
                            onShowStatement = { statementParty = party },
                            onShareWhatsApp = {
                                val summary = StatementHelper.buildPartyStatement(party, sales, purchases, debts, debtPayments)
                                val txt = StatementHelper.formatPartyStatementText(summary, StatementMode.DETAILED, storeSettings)
                                StatementHelper.shareText(contextCard, txt, "كشف حساب ${party.name}")
                            },
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

                        val contextCard = LocalContext.current
                        PartyAccountCard(
                            party = party,
                            remainingDebt = remainingDebt,
                            isCustomer = false,
                            onCollectOrPay = {
                                supplierPayInitialParty = party
                                showSupplierPaymentDialog = true
                            },
                            onShowStatement = { statementParty = party },
                            onShareWhatsApp = {
                                val summary = StatementHelper.buildPartyStatement(party, sales, purchases, debts, debtPayments)
                                val txt = StatementHelper.formatPartyStatementText(summary, StatementMode.DETAILED, storeSettings)
                                StatementHelper.shareText(contextCard, txt, "كشف حساب ${party.name}")
                            },
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
            onSave = { party, initialDebt, debtNotes, debtTs ->
                viewModel.saveParty(
                    party = party,
                    initialDebt = initialDebt,
                    debtNotes = debtNotes,
                    initialDebtTimestamp = debtTs,
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
            onSave = { updatedParty, _, _, _ ->
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
            onConfirm = { amount, paymentMethod, exchangeAcc, notes, transferRef, paymentTimestamp ->
                viewModel.recordPartyPayment(
                    partyName = party.name,
                    partyType = party.type,
                    totalPayment = amount,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAcc,
                    referenceNumber = transferRef,
                    timestamp = paymentTimestamp,
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
        var debtTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val isCustomer = party.type == "CUSTOMER"

        StoreOperationDialog(
            onDismissRequest = { addingDebtForParty = null },
            title = if (isCustomer) "تسجيل دين جديد على العميل" else "تسجيل دين جديد للمورد",
            subtitle = party.name,
            icon = Icons.Default.AddCard,
            accentColor = if (isCustomer) PdfGoldPrimary else FintechRose,
            confirmText = "حفظ الدين",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "submit_add_party_debt_btn",
            onConfirm = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val days = dueDateDays.toLongOrNull() ?: 15L
                viewModel.addNewDebt(
                    DebtRecord(
                        type = party.type,
                        partyName = party.name,
                        phone = party.phone,
                        originalAmount = amount,
                        paidAmount = 0.0,
                        remainingAmount = amount,
                        details = notesStr.ifBlank { if (isCustomer) "دين إضافي على العميل" else "دين إضافي للمورد" },
                        dueDate = debtTimestamp + 86400000L * days,
                        createdAt = debtTimestamp,
                        isSettled = false
                    ),
                    onSuccess = { addingDebtForParty = null }
                )
            }
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ المطلوب (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfHeaderNavy) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("add_debt_amount_input")
            )

            OutlinedTextField(
                value = notesStr,
                onValueChange = { notesStr = it },
                label = { Text("تفاصيل البضاعة / سبب الدين") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dueDateDays,
                onValueChange = { dueDateDays = it },
                label = { Text("مدة السداد المتوقعة (بالأيام)") },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = BrandBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(BrandBlue),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TransactionDatePickerField(
                selectedTimestamp = debtTimestamp,
                onDateSelected = { debtTimestamp = it },
                label = "تاريخ تسجيل الدين",
                testTag = "add_party_debt_date_picker"
            )
        }
    }

    // --- DIALOG: Account Statement (كشف حساب تفصيلي أو تقرير نهائي) ---
    statementParty?.let { party ->
        PartyStatementDialog(
            party = party,
            sales = sales,
            purchases = purchases,
            debts = debts,
            payments = debtPayments,
            settings = storeSettings,
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
        var paymentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        val isCustomerDebt = debt.type == "CUSTOMER"
        StoreOperationDialog(
            onDismissRequest = { payingDirectDebt = null },
            title = if (isCustomerDebt) "استلام دفعة من العميل" else "تسديد دفعة للمورد",
            subtitle = debt.partyName,
            icon = Icons.Default.ReceiptLong,
            accentColor = if (isCustomerDebt) FintechEmerald else FintechRose,
            confirmText = "تأكيد السداد",
            confirmIcon = Icons.Default.Check,
            confirmColor = if (isCustomerDebt) FintechEmerald else PdfHeaderNavy,
            confirmTestTag = "confirm_direct_debt_payment_btn",
            onConfirm = {
                val amount = paymentAmountStr.toDoubleOrNull() ?: 0.0
                viewModel.recordDebtPayment(
                    debt = debt,
                    amount = amount,
                    notes = paymentNotes,
                    paymentMethod = paymentMethod,
                    exchangeAccount = selectedExchangeAcc,
                    referenceNumber = paymentTransferRef.trim(),
                    timestamp = paymentTimestamp,
                    onSuccess = { payingDirectDebt = null }
                )
            }
        ) {
            DialogInfoBanner(
                title = "المبلغ المتبقي حالياً:",
                value = FormatUtils.formatCurrency(debt.remainingAmount),
                containerColor = GoldContainer,
                contentColor = PdfGoldDark,
                borderColor = PdfGoldPrimary.copy(alpha = 0.45f),
                icon = Icons.Default.AccountBalanceWallet
            )

            OutlinedTextField(
                value = paymentAmountStr,
                onValueChange = { paymentAmountStr = it },
                label = { Text("مبلغ الدفعة (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfHeaderNavy) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("direct_debt_payment_input")
            )

            DialogSectionHeader(title = "طريقة استلام/سداد المبلغ", icon = Icons.Default.AccountBalanceWallet, accentColor = PdfHeaderNavy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = paymentMethod == "نقداً",
                    label = if (isCustomerDebt) "نقداً بالصندوق" else "نقداً من الصندوق",
                    icon = Icons.Default.Payments,
                    selectedColor = FintechEmerald,
                    onClick = { paymentMethod = "نقداً" },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = paymentMethod == "صرافة/محفظة",
                    label = "صرافة / محفظة",
                    icon = Icons.Default.AccountBalance,
                    selectedColor = FintechIndigo,
                    onClick = {
                        paymentMethod = "صرافة/محفظة"
                        if (selectedExchangeAcc == null && exchangeAccounts.isNotEmpty()) {
                            selectedExchangeAcc = exchangeAccounts.first()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (paymentMethod == "صرافة/محفظة" && exchangeAccounts.isNotEmpty()) {
                DialogSectionHeader(title = "اختر حساب الصراف / المحفظة", icon = Icons.Default.AccountBalance, accentColor = FintechIndigo)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedExchangeAcc?.id == acc.id,
                            onClick = { selectedExchangeAcc = acc },
                            label = { Text(acc.name, fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = paymentTransferRef,
                onValueChange = { paymentTransferRef = it },
                label = { Text("رقم السند (اختياري)") },
                placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SND-0001)") },
                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfGoldDark, modifier = Modifier.size(18.dp)) },
                colors = storeDialogTextFieldColors(PdfGoldDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("direct_debt_voucher_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = paymentNotes,
                onValueChange = { paymentNotes = it },
                label = { Text("ملاحظات السداد") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            TransactionDatePickerField(
                selectedTimestamp = paymentTimestamp,
                onDateSelected = { paymentTimestamp = it },
                label = "تاريخ السداد / السند",
                testTag = "direct_debt_pay_date_picker"
            )
        }
    }

    // --- DIALOG: Edit Direct Debt Record ---
    editingDirectDebt?.let { debt ->
        var partyName by remember { mutableStateOf(debt.partyName) }
        var phone by remember { mutableStateOf(debt.phone) }
        var amountStr by remember { mutableStateOf(debt.originalAmount.toLong().toString()) }
        var details by remember { mutableStateOf(debt.details) }
        var createdAtTimestamp by remember(debt.id) { mutableLongStateOf(debt.createdAt) }

        StoreOperationDialog(
            onDismissRequest = { editingDirectDebt = null },
            title = if (debt.type == "CUSTOMER") "تعديل دين العميل" else "تعديل دين المورد",
            subtitle = debt.partyName,
            icon = Icons.Default.Edit,
            accentColor = BrandBlue,
            confirmText = "حفظ التعديل",
            confirmIcon = Icons.Default.Check,
            confirmColor = PdfHeaderNavy,
            onConfirm = {
                val newOriginal = amountStr.toDoubleOrNull() ?: debt.originalAmount
                if (newOriginal <= 0) {
                    viewModel.showMessage("يرجى إدخال مبلغ دين صحيح أكبر من صفر!")
                } else {
                    val newRemaining = (newOriginal - debt.paidAmount).coerceAtLeast(0.0)
                    viewModel.updateDebt(
                        debt.copy(
                            partyName = partyName.trim(),
                            phone = phone.trim(),
                            originalAmount = newOriginal,
                            remainingAmount = newRemaining,
                            details = details.trim(),
                            createdAt = createdAtTimestamp,
                            isSettled = newRemaining <= 0.01
                        ),
                        onSuccess = { editingDirectDebt = null }
                    )
                }
            }
        ) {
            OutlinedTextField(
                value = partyName,
                onValueChange = { partyName = it },
                label = { Text("الاسم") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ الأصلي للدين (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = BrandBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(BrandBlue),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("البيان / تفاصيل البضاعة") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            TransactionDatePickerField(
                selectedTimestamp = createdAtTimestamp,
                onDateSelected = { createdAtTimestamp = it },
                label = "تاريخ تسجيل الدين",
                testTag = "edit_debt_date_picker"
            )
        }
    }

    // --- DIALOG: Direct Single Debt Creation ---
    if (showDirectAddDebtDialog) {
        var partyName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amountStr by remember { mutableStateOf("") }
        var details by remember { mutableStateOf("") }
        var debtType by remember { mutableStateOf(if (selectedTab == 1) "SUPPLIER" else "CUSTOMER") }
        var debtTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

        StoreOperationDialog(
            onDismissRequest = { showDirectAddDebtDialog = false },
            title = if (debtType == "CUSTOMER") "تسجيل دين مباشر على زبون" else "تسجيل دين مباشر لمورد",
            subtitle = "إضافة سجل دين مستقل بالتاريخ والتفاصيل",
            icon = Icons.Default.AddCard,
            accentColor = if (debtType == "CUSTOMER") PdfGoldPrimary else FintechRose,
            confirmText = "حفظ الدين",
            confirmIcon = Icons.Default.Save,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "submit_direct_debt_btn",
            onConfirm = {
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
                        dueDate = debtTimestamp + 86400000L * 15,
                        createdAt = debtTimestamp,
                        isSettled = false
                    ),
                    onSuccess = { showDirectAddDebtDialog = false }
                )
            }
        ) {
            DialogSectionHeader(title = "تصنيف الدين", icon = Icons.Default.Category, accentColor = PdfHeaderNavy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = debtType == "CUSTOMER",
                    label = "دين على زبون (لنا)",
                    icon = Icons.Default.Person,
                    selectedColor = PdfHeaderNavy,
                    onClick = { debtType = "CUSTOMER" },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = debtType == "SUPPLIER",
                    label = "دين لمورد (عليا)",
                    icon = Icons.Default.Storefront,
                    selectedColor = FintechRose,
                    onClick = { debtType = "SUPPLIER" },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = partyName,
                onValueChange = { partyName = it },
                label = { Text(if (debtType == "CUSTOMER") "اسم الزبون *" else "اسم المورد *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_direct_debt_name")
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

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("المبلغ المطلوب (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfHeaderNavy) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_direct_debt_amount")
            )

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("تفاصيل المشتريات / البضاعة") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            TransactionDatePickerField(
                selectedTimestamp = debtTimestamp,
                onDateSelected = { debtTimestamp = it },
                label = "تاريخ تسجيل الدين",
                testTag = "direct_debt_date_picker"
            )
        }
    }

    if (showCollectionDialog) {
        CollectionReceiptDialog(
            customerParties = customerParties,
            customerDebts = debts,
            exchangeAccounts = exchangeAccounts,
            initialParty = collectionInitialParty,
            onDismiss = {
                showCollectionDialog = false
                collectionInitialParty = null
            },
            onConfirm = { payerName, party, amount, voucher, method, account, notes, timestamp ->
                viewModel.recordCollectionReceipt(
                    payerName = payerName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucher,
                    paymentMethod = method,
                    exchangeAccount = account,
                    notes = notes,
                    timestamp = timestamp
                ) {
                    showCollectionDialog = false
                    collectionInitialParty = null
                }
            }
        )
    }

    if (showSupplierPaymentDialog) {
        SupplierPaymentDialog(
            supplierParties = supplierParties,
            supplierDebts = debts,
            exchangeAccounts = exchangeAccounts,
            initialParty = supplierPayInitialParty,
            onDismiss = {
                showSupplierPaymentDialog = false
                supplierPayInitialParty = null
            },
            onConfirm = { supplierName, party, amount, voucher, method, account, notes, timestamp ->
                viewModel.recordSupplierPaymentReceipt(
                    supplierName = supplierName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucher,
                    paymentMethod = method,
                    exchangeAccount = account,
                    notes = notes,
                    timestamp = timestamp
                ) {
                    showSupplierPaymentDialog = false
                    supplierPayInitialParty = null
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
    onShareWhatsApp: () -> Unit,
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

                    // Quick WhatsApp Statement Share Button
                    IconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.size(34.dp).testTag("btn_whatsapp_party_${party.id}")
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = "مشاركة كشف الحساب واتساب", tint = EmeraldProfit, modifier = Modifier.size(18.dp))
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
    onSave: (PartyAccount, Double, String, Long) -> Unit
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
    var initialDebtTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isNew = existingParty == null
    val isCustomer = partyType == "CUSTOMER"

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = if (isNew) {
            if (isCustomer) "فتح حساب عميل جديد" else "فتح حساب مورد جديد"
        } else {
            if (isCustomer) "تعديل بيانات العميل" else "تعديل بيانات المورد"
        },
        subtitle = if (isCustomer) "بيانات العميل والرصيد الافتتاحي" else "بيانات المورد والرصيد الافتتاحي",
        icon = if (isCustomer) Icons.Default.PersonAdd else Icons.Default.Storefront,
        accentColor = if (isCustomer) PdfGoldPrimary else FintechRose,
        confirmText = if (isNew) "فتح الحساب وحفظه" else "حفظ التعديلات",
        confirmIcon = Icons.Default.Save,
        confirmColor = PdfHeaderNavy,
        confirmTestTag = "btn_save_party_account",
        onConfirm = {
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
            onSave(party, initialDebt, initialDebtNotes, initialDebtTimestamp)
        }
    ) {
        if (isNew) {
            DialogSectionHeader(title = "نوع الحساب", icon = Icons.Default.Category, accentColor = PdfHeaderNavy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSegmentedChoice(
                    selected = isCustomer,
                    label = "عميل (زبون)",
                    icon = Icons.Default.Person,
                    selectedColor = PdfHeaderNavy,
                    onClick = { partyType = "CUSTOMER" },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = !isCustomer,
                    label = "مورد (معمل)",
                    icon = Icons.Default.Storefront,
                    selectedColor = FintechRose,
                    onClick = { partyType = "SUPPLIER" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isCustomer) "اسم العميل الكامل *" else "اسم المورد / المعمل *") },
            leadingIcon = { Icon(if (isCustomer) Icons.Default.Person else Icons.Default.Storefront, contentDescription = null, tint = PdfHeaderNavy) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth().testTag("party_dialog_name_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("رقم الهاتف (للتواصل واتصال مباشر)") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SlateTextSecondary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("العنوان / المدينة") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = SlateTextSecondary) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات إضافية") },
            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Opening Balance Section (Only for new accounts)
        if (isNew) {
            HorizontalDivider(color = SlateBorder)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SlateBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCustomer) "يوجد رصيد سابق (عليه زلط سابقة)؟" else "يوجد رصيد سابق (عليا زلط للمورد)؟",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SlateTextPrimary)
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
            }

            if (hasInitialDebt) {
                OutlinedTextField(
                    value = initialDebtAmountStr,
                    onValueChange = { initialDebtAmountStr = it },
                    label = { Text(if (isCustomer) "المبلغ المطلوب منه (عليه زلط)" else "المبلغ المستحق له (عليا زلط)") },
                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfGoldDark) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = storeDialogTextFieldColors(PdfGoldDark),
                    modifier = Modifier.fillMaxWidth().testTag("party_dialog_debt_amount_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = initialDebtNotes,
                    onValueChange = { initialDebtNotes = it },
                    label = { Text("تفاصيل الرصيد السابق (اختياري)") },
                    placeholder = { Text("مثال: حساب بضاعة سابقة قبل النظام") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                TransactionDatePickerField(
                    selectedTimestamp = initialDebtTimestamp,
                    onDateSelected = { initialDebtTimestamp = it },
                    label = "تاريخ الرصيد / الدين السابق",
                    testTag = "party_initial_debt_date_picker"
                )
            }
        }
    }
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
    onConfirm: (amount: Double, paymentMethod: String, exchangeAccount: ExchangeAccount?, notes: String, transferRef: String, timestamp: Long) -> Unit
) {
    val isCustomer = party.type == "CUSTOMER"
    var amountStr by remember { mutableStateOf(totalDebt.toLong().toString()) }
    var paymentMethod by remember { mutableStateOf("نقداً") } // "نقداً" or "صرافة/محفظة"
    var selectedExchangeAcc by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var transferRef by remember { mutableStateOf("") }
    var notes by remember {
        mutableStateOf(if (isCustomer) "قبض زلط نقدية من العميل" else "سداد دفعة للمورد")
    }
    var paymentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = if (isCustomer) "قبض زلط من العميل" else "سداد دفعة للمورد",
        subtitle = party.name,
        icon = if (isCustomer) Icons.Default.Download else Icons.Default.Upload,
        accentColor = if (isCustomer) FintechEmerald else FintechRose,
        confirmText = if (isCustomer) "تأكيد قبض الزلط" else "تأكيد سداد المورد",
        confirmIcon = Icons.Default.Check,
        confirmColor = if (isCustomer) FintechEmerald else PdfHeaderNavy,
        confirmTestTag = "btn_confirm_party_payment",
        onConfirm = {
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            onConfirm(amount, paymentMethod, selectedExchangeAcc, notes, transferRef, paymentTimestamp)
        }
    ) {
        DialogInfoBanner(
            title = if (isCustomer) "إجمالي المطلوب من العميل:" else "إجمالي المستحق للمورد:",
            value = FormatUtils.formatCurrency(totalDebt),
            containerColor = if (isCustomer) GoldContainer else Color(0xFFFFF1F2),
            contentColor = if (isCustomer) PdfGoldDark else RubyExpense,
            borderColor = if (isCustomer) PdfGoldPrimary.copy(alpha = 0.45f) else Color(0xFFFECDD3),
            icon = Icons.Default.AccountBalanceWallet
        )

        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = { Text("المبلغ المدفوع (ريال) *") },
            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = PdfHeaderNavy) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth().testTag("party_payment_amount_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        DialogSectionHeader(
            title = if (isCustomer) "طريقة إيداع الزلط" else "طريقة صرف الزلط للمورد",
            icon = Icons.Default.AccountBalanceWallet,
            accentColor = PdfHeaderNavy
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogSegmentedChoice(
                selected = paymentMethod == "نقداً",
                label = if (isCustomer) "نقداً للصندوق" else "نقداً من الصندوق",
                icon = Icons.Default.Payments,
                selectedColor = FintechEmerald,
                onClick = { paymentMethod = "نقداً" },
                modifier = Modifier.weight(1f)
            )
            DialogSegmentedChoice(
                selected = paymentMethod == "صرافة/محفظة",
                label = "صرافة / محفظة",
                icon = Icons.Default.AccountBalance,
                selectedColor = FintechIndigo,
                onClick = {
                    paymentMethod = "صرافة/محفظة"
                    if (selectedExchangeAcc == null && exchangeAccounts.isNotEmpty()) {
                        selectedExchangeAcc = exchangeAccounts.first()
                    }
                },
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
                DialogSectionHeader(title = "اختر حساب الصراف أو المحفظة", icon = Icons.Default.AccountBalance, accentColor = FintechIndigo)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedExchangeAcc?.id == acc.id,
                            onClick = { selectedExchangeAcc = acc },
                            label = { Text(acc.name, fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechIndigo,
                                selectedLabelColor = Color.White
                            )
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
            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfGoldDark) },
            colors = storeDialogTextFieldColors(PdfGoldDark),
            modifier = Modifier.fillMaxWidth().testTag("party_payment_voucher_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات وتوثيق السداد") },
            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = SlateTextSecondary) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        TransactionDatePickerField(
            selectedTimestamp = paymentTimestamp,
            onDateSelected = { paymentTimestamp = it },
            label = "تاريخ الدفعة / السند",
            testTag = "party_payment_date_picker"
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
}

// ----------------------------------------------------
// Component: Party Statement Dialog (كشف حساب تفصيلي / تقرير نهائي + PDF + واتساب)
// ----------------------------------------------------
@Composable
fun PartyStatementDialog(
    party: PartyAccount,
    sales: List<SaleTransaction>,
    purchases: List<PurchaseRecord>,
    debts: List<DebtRecord>,
    payments: List<DebtPayment>,
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
    var mode by remember { mutableStateOf(StatementMode.DETAILED) }

    val summary = remember(party, sales, purchases, debts, payments) {
        StatementHelper.buildPartyStatement(
            party = party,
            sales = sales,
            purchases = purchases,
            debts = debts,
            payments = payments
        )
    }

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "كشف حساب: ${party.name}",
        subtitle = if (summary.isCustomer) "عميل / زبون" else "مورد / معمل",
        icon = Icons.Default.ReceiptLong,
        accentColor = PdfGoldPrimary,
        maxWidth = 560.dp,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val file = InvoiceHelper.generatePartyStatementPdf(context, summary, mode, settings)
                            InvoiceHelper.printPdfFile(context, file, "كشف_حساب_${party.name}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("btn_print_statement_pdf")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("طباعة PDF", fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val file = InvoiceHelper.generatePartyStatementPdf(context, summary, mode, settings)
                            InvoiceHelper.sharePdfFile(context, file, "مشاركة كشف حساب ${party.name}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        modifier = Modifier.weight(1f).testTag("btn_share_statement_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مشاركة PDF", fontSize = 11.sp, maxLines = 1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val text = StatementHelper.formatPartyStatementText(summary, mode, settings)
                            StatementHelper.shareText(context, text, "كشف حساب ${party.name}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit),
                        modifier = Modifier.weight(1.3f).testTag("btn_share_statement_text")
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مشاركة واتساب", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val text = StatementHelper.formatPartyStatementText(summary, mode, settings)
                            StatementHelper.copyToClipboard(context, text, "كشف حساب ${party.name}")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f).testTag("btn_copy_statement_text")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نسخ", fontSize = 11.sp, maxLines = 1)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("إغلاق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        // Mode Toggle: كشف مفصل vs تقرير نهائي
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogSegmentedChoice(
                selected = mode == StatementMode.DETAILED,
                label = "كشف مفصل (${summary.entries.size})",
                icon = Icons.Default.FormatListBulleted,
                selectedColor = PdfHeaderNavy,
                onClick = { mode = StatementMode.DETAILED },
                modifier = Modifier.weight(1f).testTag("tab_statement_detailed")
            )
            DialogSegmentedChoice(
                selected = mode == StatementMode.SUMMARY,
                label = "تقرير نهائي (ملخص)",
                icon = Icons.Default.Summarize,
                selectedColor = PdfGoldDark,
                onClick = { mode = StatementMode.SUMMARY },
                modifier = Modifier.weight(1f).testTag("tab_statement_summary")
            )
        }

        // 3-Column Summary: عليه | له | باقي
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (summary.isCustomer) RubyLight else Color(0xFFEFF6FF),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (summary.isCustomer) "كم عليه" else "كم له (توريدات)",
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = FormatUtils.formatCurrency(
                                    if (summary.isCustomer) summary.totalOnParty else summary.totalForParty,
                                    currency
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = if (summary.isCustomer) RubyExpense else BrandBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldLight,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (summary.isCustomer) "كم له (سدد)" else "كم عليه (سددنا)",
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = FormatUtils.formatCurrency(
                                    if (summary.isCustomer) summary.totalForParty else summary.totalOnParty,
                                    currency
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = EmeraldProfit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (summary.netBalance > 0.01) GoldContainer else EmeraldLight,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    summary.netBalance > 0.01 -> "كم باقي"
                                    summary.netBalance < -0.01 -> "رصيد زائد"
                                    else -> "خالص"
                                },
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                            Text(
                                text = FormatUtils.formatCurrency(kotlin.math.abs(summary.netBalance), currency),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                color = if (summary.netBalance > 0.01) LuxuryGold else EmeraldProfit,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (mode == StatementMode.SUMMARY) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("ملخص التقرير النهائي للحساب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            HorizontalDivider(color = SlateBorder)
                            Text("• عدد الفواتير المسجلة: ${summary.invoicesCount} فاتورة", fontSize = 12.sp)
                            Text("• عدد سندات القبض/الصرف: ${summary.paymentsCount} سند", fontSize = 12.sp)
                            Text("• سجلات الدين المفتوحة: ${summary.unsettledDebtsCount} سجل", fontSize = 12.sp)
                            HorizontalDivider(color = SlateBorder)
                            val statusDesc = when {
                                summary.isCustomer && summary.netBalance > 0.01 ->
                                    "صافي المتبقي على العميل للمحل: ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
                                summary.isCustomer && summary.netBalance < -0.01 ->
                                    "رصيد فائض للعميل لدى المحل: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
                                !summary.isCustomer && summary.netBalance > 0.01 ->
                                    "صافي المتبقي للمورد بذمتنا: ${FormatUtils.formatCurrency(summary.netBalance, currency)}"
                                !summary.isCustomer && summary.netBalance < -0.01 ->
                                    "رصيد مدفوع مقدماً لدى المورد: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}"
                                else -> "الحساب خالص ومقفل (0 $currency)"
                            }
                            Text(
                                text = statusDesc,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (summary.netBalance > 0.01) RubyExpense else EmeraldProfit
                            )
                        }
                    }
                } else {
                    Text("التفاصيل الكاملة لجميع العمليات:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    if (summary.entries.isEmpty()) {
                        Text("لا توجد عمليات أو فواتير أو دفعات مسجلة لهذا الحساب بعد.", color = SlateMuted, fontSize = 12.sp)
                    } else {
                        summary.entries.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = FormatUtils.formatDate(entry.timestamp),
                                            fontSize = 10.sp,
                                            color = SlateMuted
                                        )
                                    }
                                    if (entry.subDetails.isNotBlank()) {
                                        Text(entry.subDetails, fontSize = 10.5.sp, color = SlateMuted)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val amountLine = buildList {
                                            if (summary.isCustomer) {
                                                if (entry.debit > 0.01) add("عليه: ${FormatUtils.formatNumber(entry.debit)}")
                                                if (entry.credit > 0.01) add("له: ${FormatUtils.formatNumber(entry.credit)}")
                                            } else {
                                                if (entry.credit > 0.01) add("له: ${FormatUtils.formatNumber(entry.credit)}")
                                                if (entry.debit > 0.01) add("مسدد: ${FormatUtils.formatNumber(entry.debit)}")
                                            }
                                        }.joinToString(" | ")
                                        Text(
                                            text = amountLine,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (entry.kind == "PAYMENT") EmeraldProfit else SlateTextPrimary
                                        )
                                        val balStr = when {
                                            entry.runningBalance > 0.01 -> "الرصيد: ${FormatUtils.formatNumber(entry.runningBalance)}"
                                            entry.runningBalance < -0.01 -> "فائض: ${FormatUtils.formatNumber(-entry.runningBalance)}"
                                            else -> "خالص"
                                        }
                                        Text(
                                            text = balStr,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.runningBalance > 0.01) LuxuryGold else EmeraldProfit
                                        )
                                    }
                                }
                            }
                        }
                    }
        }
    }
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
