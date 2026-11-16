package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.StoreViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.StatementHelper

private enum class OpeningBalanceTab(
    val title: String,
    val icon: ImageVector,
    val tag: String
) {
    CASH("الصندوق", Icons.Default.AccountBalanceWallet, "tab_opening_cash"),
    EXCHANGE("الصرافين والمحافظ", Icons.Default.AccountBalance, "tab_opening_exchange"),
    INVENTORY("المخزن والبضاعة", Icons.Default.Inventory2, "tab_opening_inventory"),
    CUSTOMERS("العملاء", Icons.Default.People, "tab_opening_customers"),
    SUPPLIERS("الموردين", Icons.Default.LocalShipping, "tab_opening_suppliers"),
    EMPLOYEES("العمال", Icons.Default.Badge, "tab_opening_employees")
}

@Composable
fun OpeningBalancesScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val inventoryCostValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val customerParties by viewModel.customerParties.collectAsStateWithLifecycle()
    val supplierParties by viewModel.supplierParties.collectAsStateWithLifecycle()
    val totalCustomerDebts by viewModel.totalCustomerDebts.collectAsStateWithLifecycle()
    val totalSupplierDebts by viewModel.totalSupplierDebts.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val employeeTransactions by viewModel.employeeTransactions.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val debtPayments by viewModel.debtPayments.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val productSizes by viewModel.productSizes.collectAsStateWithLifecycle()
    val productColors by viewModel.productColors.collectAsStateWithLifecycle()
    val accountTypes by viewModel.accountTypes.collectAsStateWithLifecycle()
    val employeeRoles by viewModel.employeeRoles.collectAsStateWithLifecycle()

    val selectableCategories = remember(allCategories) {
        allCategories.filter { it != "الكل" && it.isNotBlank() }
    }

    val isAuthorized by viewModel.openingBalancesAuthorized.collectAsStateWithLifecycle()
    var showPinPrompt by remember(isAuthorized) { mutableStateOf(!isAuthorized) }
    var selectedTab by remember { mutableStateOf(OpeningBalanceTab.CASH) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "الأرصدة الافتتاحية للمحل",
                subtitle = if (isAuthorized) "إدخال ما في الصندوق والمخزن والحسابات بدون خصم" else "منطقة محمية بصلاحية المدير",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        if (!isAuthorized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(SlateBg)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .testTag("opening_balances_locked_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PdfHeaderNavy.copy(alpha = 0.08f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PdfHeaderNavy,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "الأرصدة الافتتاحية محمية برمز المدير",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = SlateTextPrimary
                        )
                        Text(
                            text = "لا يُسمح بدخول أو تعديل أرصدة الصندوق والمخزن والعملاء والموردين إلا للمدير أو المسؤول بعد تأكيد رمز الحماية (PIN).",
                            fontSize = 13.sp,
                            color = SlateTextSecondary,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showPinPrompt = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_unlock_opening_balances")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إدخال رمز المدير للفتح", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("رجوع للصفحة السابقة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showPinPrompt) {
                PinVerificationDialog(
                    viewModel = viewModel,
                    title = "تحقق صلاحية المدير",
                    subtitle = "أدخل رمز المدير للوصول إلى الأرصدة الافتتاحية وتعديلها",
                    onDismiss = { showPinPrompt = false },
                    onVerified = {
                        showPinPrompt = false
                        viewModel.authorizeOpeningBalancesSession()
                    }
                )
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SlateBg),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 600.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header / Accounting Promise Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PdfHeaderNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = PdfGoldPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "تأسيس أرصدة المحل بالكامل بدون أي خصم",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "كل ما تسجله هنا يُعتمد كرصيد افتتاحي لما هو موجود بالمحل — بدون أي خصم من الصندوق وبدون احتسابه كمشتريات أو مصروفات يومية.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Live Store Totals Summary Grid
                item {
                    val totalPieces = remember(products) { products.sumOf { it.quantity } }
                    val tiles = listOf(
                        Triple("الصندوق (كاش)", FormatUtils.formatCurrency(cashBalance), "السيولة النقدية") to (Icons.Default.AccountBalanceWallet to OpeningBalanceTab.CASH),
                        Triple("الصرافين والمحافظ", FormatUtils.formatCurrency(totalExchangeBalance), "${exchangeAccounts.size} حساب") to (Icons.Default.AccountBalance to OpeningBalanceTab.EXCHANGE),
                        Triple("المخزن والبضاعة", FormatUtils.formatCurrency(inventoryCostValue), "${products.size} صنف ($totalPieces حبه)") to (Icons.Default.Inventory2 to OpeningBalanceTab.INVENTORY),
                        Triple("لنا عند العملاء", FormatUtils.formatCurrency(totalCustomerDebts), "${customerParties.size} عميل") to (Icons.Default.People to OpeningBalanceTab.CUSTOMERS),
                        Triple("علينا للموردين", FormatUtils.formatCurrency(totalSupplierDebts), "${supplierParties.size} مورد") to (Icons.Default.LocalShipping to OpeningBalanceTab.SUPPLIERS),
                        Triple("العمال المسجلون", "${employees.size} عامل", "سلف ورواتب سابقة") to (Icons.Default.Badge to OpeningBalanceTab.EMPLOYEES)
                    )
                    val columnsCount = if (isWide) 3 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.chunked(columnsCount).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (textTriple, iconAndTab) ->
                                    val accent = when (iconAndTab.second) {
                                        OpeningBalanceTab.CASH -> FintechEmerald
                                        OpeningBalanceTab.EXCHANGE -> BrandBlue
                                        OpeningBalanceTab.INVENTORY -> FintechIndigo
                                        OpeningBalanceTab.CUSTOMERS -> PdfGoldDark
                                        OpeningBalanceTab.SUPPLIERS -> RubyExpense
                                        OpeningBalanceTab.EMPLOYEES -> FintechAmber
                                    }
                                    OpeningKpiTile(
                                        title = textTriple.first,
                                        value = textTriple.second,
                                        subtitle = textTriple.third,
                                        icon = iconAndTab.first,
                                        accentColor = accent,
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedTab = iconAndTab.second }
                                    )
                                }
                            }
                        }
                    }
                }

                // 6 Interactive Section Tabs
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(OpeningBalanceTab.entries) { tab ->
                            val isSelected = selectedTab == tab
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(tab.tag)
                            )
                        }
                    }
                }

                // Active Section Content
                item {
                    when (selectedTab) {
                        OpeningBalanceTab.CASH -> OpeningCashSection(
                            viewModel = viewModel,
                            currentCashBalance = cashBalance
                        )
                        OpeningBalanceTab.EXCHANGE -> OpeningExchangeSection(
                            viewModel = viewModel,
                            exchangeAccounts = exchangeAccounts,
                            accountTypes = accountTypes
                        )
                        OpeningBalanceTab.INVENTORY -> OpeningInventorySection(
                            viewModel = viewModel,
                            products = products,
                            inventoryCostValue = inventoryCostValue,
                            selectableCategories = selectableCategories,
                            dynamicSizes = productSizes,
                            dynamicColors = productColors
                        )
                        OpeningBalanceTab.CUSTOMERS -> OpeningPartySection(
                            viewModel = viewModel,
                            partyType = "CUSTOMER",
                            parties = customerParties,
                            sales = sales,
                            purchases = purchases,
                            debts = debts,
                            debtPayments = debtPayments
                        )
                        OpeningBalanceTab.SUPPLIERS -> OpeningPartySection(
                            viewModel = viewModel,
                            partyType = "SUPPLIER",
                            parties = supplierParties,
                            sales = sales,
                            purchases = purchases,
                            debts = debts,
                            debtPayments = debtPayments
                        )
                        OpeningBalanceTab.EMPLOYEES -> OpeningEmployeesSection(
                            viewModel = viewModel,
                            employees = employees,
                            employeeTransactions = employeeTransactions,
                            sales = sales,
                            employeeRoles = employeeRoles
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun OpeningCashSection(
    viewModel: StoreViewModel,
    currentCashBalance: Double
) {
    var replaceExisting by remember { mutableStateOf(false) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogSectionHeader(
                title = "الرصيد الافتتاحي لصندوق المحل (الكاش)",
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = FintechEmerald
            )

            DialogInfoBanner(
                title = "رصيد الصندوق الحالي:",
                value = FormatUtils.formatCurrency(currentCashBalance),
                containerColor = FintechEmeraldBg,
                contentColor = FintechEmerald,
                borderColor = FintechEmeraldBorder,
                icon = Icons.Default.AccountBalanceWallet
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSegmentedChoice(
                    selected = !replaceExisting,
                    label = "إضافة فوق الرصيد الحالي",
                    icon = Icons.Default.AddCircleOutline,
                    selectedColor = FintechEmerald,
                    onClick = { replaceExisting = false },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = replaceExisting,
                    label = "اعتماد كرصيد فعلي",
                    icon = Icons.Default.FactCheck,
                    selectedColor = PdfHeaderNavy,
                    onClick = { replaceExisting = true },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = {
                    Text(
                        if (replaceExisting) "الرصيد الفعلي الموجود بالصندوق الآن (ريال) *"
                        else "المبلغ النقدي الموجود بالصندوق للإضافة (ريال) *"
                    )
                },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(FintechEmerald),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_cash_amount_input")
            )

            TransactionDatePickerField(
                selectedTimestamp = timestamp,
                onDateSelected = { timestamp = it },
                label = "تاريخ الرصيد الافتتاحي للصندوق",
                testTag = "opening_cash_date_picker"
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات (اختياري)") },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_cash_notes_input")
            )

            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || (!replaceExisting && amt <= 0.0) || (replaceExisting && amt < 0.0)) {
                        viewModel.showMessage("يرجى إدخال مبلغ صحيح للصندوق")
                        return@Button
                    }
                    viewModel.setOpeningCashBalance(
                        amount = amt,
                        replaceExistingBalance = replaceExisting,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        amountStr = ""
                        notes = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_opening_save_cash"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("حفظ الرصيد الافتتاحي للصندوق", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun OpeningExchangeSection(
    viewModel: StoreViewModel,
    exchangeAccounts: List<ExchangeAccount>,
    accountTypes: List<String>
) {
    var selectedAccountId by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(accountTypes.firstOrNull() ?: "صراف") }
    var accountNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var balanceStr by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun selectAccount(acc: ExchangeAccount?) {
        if (acc == null) {
            selectedAccountId = 0L
            name = ""
            selectedType = accountTypes.firstOrNull() ?: "صراف"
            accountNumber = ""
            phone = ""
            balanceStr = ""
        } else {
            selectedAccountId = acc.id
            name = acc.name
            selectedType = acc.type
            accountNumber = acc.accountNumber
            phone = acc.phone
            balanceStr = FormatUtils.formatInputNumber(acc.balance)
        }
        notes = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogSectionHeader(
                title = "الصرافين والمحافظ والحسابات البنكية",
                icon = Icons.Default.AccountBalance,
                accentColor = BrandBlue
            )

            Text(
                text = "أضف حسابات الصرافين والمحافظ وأرصدتها الحالية بالمحل دون خصم أي ريال من الصندوق.",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )

            if (exchangeAccounts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedAccountId == 0L,
                            onClick = { selectAccount(null) },
                            label = { Text("+ حساب جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectAccount(acc) },
                            label = { Text(acc.name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Text("نوع الحساب:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accountTypes) { typeItem ->
                    FilterChip(
                        selected = selectedType == typeItem,
                        onClick = { selectedType = typeItem },
                        label = { Text(typeItem, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم الصراف / المحفظة (مثال: الكريمي، جوالي) *") },
                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_exchange_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("رقم الحساب (اختياري)") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("الهاتف (اختياري)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedAccountId > 0L) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogSegmentedChoice(
                        selected = replaceExisting,
                        label = "اعتماد كرصيد فعلي",
                        icon = Icons.Default.FactCheck,
                        selectedColor = BrandBlue,
                        onClick = { replaceExisting = true },
                        modifier = Modifier.weight(1f)
                    )
                    DialogSegmentedChoice(
                        selected = !replaceExisting,
                        label = "إضافة فوق الحالي",
                        icon = Icons.Default.AddCircleOutline,
                        selectedColor = FintechEmerald,
                        onClick = { replaceExisting = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = balanceStr,
                onValueChange = { balanceStr = it },
                label = { Text("الرصيد الافتتاحي الموجود بالحساب (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = BrandBlue) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(BrandBlue),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("opening_exchange_balance_input")
            )

            TransactionDatePickerField(
                selectedTimestamp = timestamp,
                onDateSelected = { timestamp = it },
                label = "تاريخ الرصيد الافتتاحي للحساب",
                testTag = "opening_exchange_date_picker"
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات (اختياري)") },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amt = balanceStr.toDoubleOrNull() ?: 0.0
                    viewModel.saveOpeningExchangeAccount(
                        accountId = selectedAccountId,
                        name = name,
                        type = selectedType,
                        accountNumber = accountNumber,
                        phone = phone,
                        openingBalance = amt,
                        replaceExistingBalance = if (selectedAccountId > 0L) replaceExisting else false,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        selectAccount(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_opening_save_exchange"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("حفظ حساب الصراف ورصيده الافتتاحي", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    if (exchangeAccounts.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الحسابات المسجلة (${exchangeAccounts.size}) — اضغط لتعديل الرصيد",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PdfHeaderNavy
                )
                exchangeAccounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateBg)
                            .clickable { selectAccount(acc) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acc.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            Text(acc.type, fontSize = 11.sp, color = SlateTextSecondary)
                        }
                        Text(
                            text = FormatUtils.formatCurrency(acc.balance),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpeningPartySection(
    viewModel: StoreViewModel,
    partyType: String,
    parties: List<PartyAccount>,
    sales: List<SaleTransaction>,
    purchases: List<PurchaseRecord>,
    debts: List<DebtRecord>,
    debtPayments: List<DebtPayment>
) {
    val isCustomer = partyType == "CUSTOMER"
    val accentColor = if (isCustomer) PdfGoldDark else RubyExpense

    var selectedPartyName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var isNormalDebtDirection by remember { mutableStateOf(true) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun selectParty(party: PartyAccount?) {
        if (party == null) {
            selectedPartyName = ""
            name = ""
            phone = ""
            address = ""
        } else {
            selectedPartyName = party.name
            name = party.name
            phone = party.phone
            address = party.address
        }
        amountStr = ""
        notes = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DialogSectionHeader(
                title = if (isCustomer) "أرصدة العملاء (كم عندهم للمحل)" else "أرصدة الموردين (كم علينا لهم)",
                icon = if (isCustomer) Icons.Default.People else Icons.Default.LocalShipping,
                accentColor = accentColor
            )

            if (parties.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedPartyName.isBlank(),
                            onClick = { selectParty(null) },
                            label = {
                                Text(
                                    if (isCustomer) "+ عميل جديد" else "+ مورد جديد",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                    items(parties) { party ->
                        FilterChip(
                            selected = selectedPartyName == party.name,
                            onClick = { selectParty(party) },
                            label = { Text(party.name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (isCustomer) "اسم العميل الكامل *" else "اسم المورد / المعمل *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PdfHeaderNavy) },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(if (isCustomer) "opening_customer_name_input" else "opening_supplier_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان (اختياري)") },
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("طبيعة الرصيد الافتتاحي:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSegmentedChoice(
                    selected = isNormalDebtDirection,
                    label = if (isCustomer) "عليه للمحل (لنا)" else "عليا للمورد (له)",
                    icon = Icons.Default.ReceiptLong,
                    selectedColor = accentColor,
                    onClick = { isNormalDebtDirection = true },
                    modifier = Modifier.weight(1f)
                )
                DialogSegmentedChoice(
                    selected = !isNormalDebtDirection,
                    label = if (isCustomer) "له فائض عند المحل" else "لنا رصيد مقدم عنده",
                    icon = Icons.Default.AccountBalanceWallet,
                    selectedColor = BrandBlue,
                    onClick = { isNormalDebtDirection = false },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("مبلغ الرصيد الافتتاحي (ريال) *") },
                leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null, tint = accentColor) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = storeDialogTextFieldColors(accentColor),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(if (isCustomer) "opening_customer_amount_input" else "opening_supplier_amount_input")
            )

            TransactionDatePickerField(
                selectedTimestamp = timestamp,
                onDateSelected = { timestamp = it },
                label = if (isCustomer) "تاريخ الرصيد الافتتاحي للعميل" else "تاريخ الرصيد الافتتاحي للمورد",
                testTag = if (isCustomer) "opening_customer_date_picker" else "opening_supplier_date_picker"
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات / البيان (اختياري)") },
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    viewModel.recordPartyOpeningBalance(
                        type = partyType,
                        name = name,
                        phone = phone,
                        address = address,
                        amount = amt,
                        isNormalDebtDirection = isNormalDebtDirection,
                        notes = notes,
                        timestamp = timestamp
                    ) {
                        selectParty(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(if (isCustomer) "btn_opening_save_customer" else "btn_opening_save_supplier"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isCustomer) "حفظ الرصيد الافتتاحي للعميل" else "حفظ الرصيد الافتتاحي للمورد",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    if (parties.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isCustomer) "العملاء المسجلون (${parties.size}) — اضغط للإضافة" else "الموردون المسجلون (${parties.size}) — اضغط للإضافة",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PdfHeaderNavy
                )
                parties.forEach { party ->
                    val stmt = remember(party, sales, purchases, debts, debtPayments) {
                        StatementHelper.buildPartyStatement(party, sales, purchases, debts, debtPayments)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateBg)
                            .clickable { selectParty(party) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(party.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                            if (party.phone.isNotBlank()) {
                                Text(party.phone, fontSize = 11.sp, color = SlateTextSecondary)
                            }
                        }
                        val net = stmt.netBalance
                        val (label, col) = when {
                            net > 0.01 && isCustomer -> "عليه: ${FormatUtils.formatCurrency(net)}" to PdfGoldDark
                            net < -0.01 && isCustomer -> "له فائض: ${FormatUtils.formatCurrency(-net)}" to BrandBlue
                            net > 0.01 && !isCustomer -> "له علينا: ${FormatUtils.formatCurrency(net)}" to RubyExpense
                            net < -0.01 && !isCustomer -> "لنا عنده: ${FormatUtils.formatCurrency(-net)}" to BrandBlue
                            else -> "متوازن" to FintechEmerald
                        }
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col)
                    }
                }
            }
        }
    }
}
