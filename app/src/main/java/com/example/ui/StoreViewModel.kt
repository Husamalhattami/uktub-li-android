package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.PinVerificationResult
import com.example.data.repository.StoreRepository
import com.example.ui.util.BackupManager
import com.example.ui.util.BackupPreview
import com.example.ui.util.FormatUtils
import com.example.ui.util.PinSecurityManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppScreen {
    SETUP,
    HOME,
    SALE,
    PURCHASE,
    INVENTORY,
    EXPENSES,
    EMPLOYEES,
    DEBTS,
    REPORTS,
    CASH_DRAWER,
    EXCHANGE_ACCOUNTS,
    TRANSACTIONS_LOG,
    STORE_AUDIT,
    OPENING_BALANCES,
    RETURNS,
    SETTINGS,
    AUDIT_LOGS
}

data class CompletedSaleReceipt(
    val sale: SaleTransaction,
    val dateStr: String,
    val timeStr: String
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val dbInstance = AppDatabase.getInstance(application)
    private val repository = StoreRepository(dbInstance.storeDao(), dbInstance)

    fun getRepository(): StoreRepository = repository

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(false)

    // --- Navigation State with True Backstack ---
    private val _backStack = MutableStateFlow(listOf(AppScreen.HOME))
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _openingBalancesAuthorized = MutableStateFlow(false)
    val openingBalancesAuthorized: StateFlow<Boolean> = _openingBalancesAuthorized.asStateFlow()

    fun authorizeOpeningBalancesSession() {
        _openingBalancesAuthorized.value = true
    }

    fun revokeOpeningBalancesSession() {
        _openingBalancesAuthorized.value = false
    }

    init {
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("hesabi_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("config_dicts_seeded_v9", false)) {
                if (repository.getConfigItemsCount() == 0) {
                    repository.seedRecommendedDictionaries(includeSampleCategories = false)
                }
                prefs.edit().putBoolean("config_dicts_seeded_v9", true).apply()
            }
            val existing = repository.getStoreSettingsDirect()
            val settings = if (existing == null) {
                val initial = StoreSettings(
                    id = 1,
                    shopNameAr = "",
                    shopNameEn = "",
                    phone = "",
                    address = "",
                    currency = "ريال",
                    taxNumber = "",
                    invoiceHeader = "",
                    invoiceFooter = "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية.",
                    logoPath = "",
                    isSetupCompleted = false
                )
                repository.saveStoreSettings(initial)
                initial
            } else {
                existing
            }
            _isSetupCompleted.value = settings.isSetupCompleted
            if (!settings.isSetupCompleted) {
                _backStack.value = listOf(AppScreen.SETUP)
                _currentScreen.value = AppScreen.SETUP
            }
            _isInitialized.value = true
        }
    }

    fun navigateTo(screen: AppScreen, resetToRoot: Boolean = false) {
        val prev = _currentScreen.value
        if (prev == AppScreen.OPENING_BALANCES && screen != AppScreen.OPENING_BALANCES) {
            _openingBalancesAuthorized.value = false
        }
        if (screen == prev) return

        val newStack = when {
            screen == AppScreen.HOME || resetToRoot -> listOf(screen)
            else -> {
                val current = _backStack.value
                if (current.lastOrNull() == screen) current
                else (current + screen).takeLast(30)
            }
        }
        _backStack.value = newStack
        _currentScreen.value = newStack.last()
    }

    fun openSetupWizard() {
        navigateTo(AppScreen.SETUP)
    }

    fun navigateBack(): Boolean {
        val current = _currentScreen.value
        val setupDone = _isSetupCompleted.value || (storeSettings.value?.isSetupCompleted == true)
        if (current == AppScreen.SETUP && !setupDone) {
            return false
        }
        if (current == AppScreen.OPENING_BALANCES) {
            _openingBalancesAuthorized.value = false
        }

        val stack = _backStack.value
        if (stack.size > 1) {
            val popped = stack.dropLast(1)
            _backStack.value = popped
            _currentScreen.value = popped.last()
            return true
        }
        if (current != AppScreen.HOME) {
            _backStack.value = listOf(AppScreen.HOME)
            _currentScreen.value = AppScreen.HOME
            return true
        }
        return false
    }

    // --- Reactive Data Streams ---
    val products: StateFlow<List<ProductItem>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allConfigItems: StateFlow<List<StoreConfigItem>> = repository.allConfigItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productCategoryItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.PRODUCT_CATEGORY }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productSizeItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.PRODUCT_SIZE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategoryItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.EXPENSE_CATEGORY }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountTypeItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.ACCOUNT_TYPE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productColorItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.PRODUCT_COLOR }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employeeRoleItems: StateFlow<List<StoreConfigItem>> = allConfigItems.map { list ->
        list.filter { it.type == StoreConfigTypes.EMPLOYEE_ROLE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<String>> = combine(products, productCategoryItems) { prods, catItems ->
        val fromConfig = catItems.map { it.name.trim() }.filter { it.isNotBlank() }
        val fromProds = prods.map { it.category.trim() }.filter { it.isNotBlank() }
        (listOf("الكل") + fromConfig + fromProds).distinct().filter { it.isNotBlank() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        listOf("الكل")
    )

    val productSizes: StateFlow<List<String>> = productSizeItems.map { items ->
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        names.ifEmpty { listOf("كبير", "شبابي", "وسط", "صغير") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("كبير", "شبابي", "وسط", "صغير"))

    val expenseCategories: StateFlow<List<String>> = expenseCategoryItems.map { items ->
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        names.ifEmpty { listOf("صرفة يومية لعامل", "إيجار المحل", "الكهرباء", "مصروف متفرقات") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("صرفة يومية لعامل", "إيجار المحل", "الكهرباء", "مصروف متفرقات"))

    val accountTypes: StateFlow<List<String>> = accountTypeItems.map { items ->
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        names.ifEmpty { listOf("صراف", "محفظة", "حساب بنكي") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("صراف", "محفظة", "حساب بنكي"))

    val productColors: StateFlow<List<String>> = productColorItems.map { items ->
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        names.ifEmpty { listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "بني") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "بني"))

    val employeeRoles: StateFlow<List<String>> = employeeRoleItems.map { items ->
        val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
        names.ifEmpty { listOf("بائع", "كاشير", "خياط", "محاسب") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("بائع", "كاشير", "خياط", "محاسب"))

    fun addNewCategory(name: String) {
        addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, name, "تمت إضافة نوع الصنف: ${name.trim()}")
    }

    fun addConfigItem(type: String, name: String, customSuccessMsg: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _userMessage.value = "يرجى كتابة الاسم أولاً"
            return
        }
        viewModelScope.launch {
            val inserted = repository.addConfigItem(type, trimmed)
            _userMessage.value = if (inserted) {
                customSuccessMsg ?: "تمت إضافة «$trimmed» بنجاح"
            } else {
                "«$trimmed» موجود مسبقاً"
            }
        }
    }

    fun updateConfigItem(item: StoreConfigItem, newName: String, onSuccess: () -> Unit = {}) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _userMessage.value = "يرجى كتابة الاسم الجديد"
            return
        }
        viewModelScope.launch {
            val updated = repository.updateConfigItem(item, trimmed)
            if (updated) {
                _userMessage.value = "تم التعديل إلى «$trimmed» بنجاح"
                onSuccess()
            } else {
                _userMessage.value = "يوجد عنصر آخر بنفس الاسم"
            }
        }
    }

    fun deleteConfigItem(item: StoreConfigItem) {
        viewModelScope.launch {
            repository.deleteConfigItem(item.id)
            _userMessage.value = "تم حذف «${item.name}»"
        }
    }

    fun restoreRecommendedDictionaries(includeSampleCategories: Boolean = false) {
        viewModelScope.launch {
            repository.seedRecommendedDictionaries(includeSampleCategories = includeSampleCategories)
            _userMessage.value = "تمت إضافة القوائم الافتراضية بنجاح"
        }
    }

    val sales: StateFlow<List<SaleTransaction>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<PurchaseRecord>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employees: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employeesIncludingArchived: StateFlow<List<Employee>> = repository.allEmployeesIncludingArchived
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employeeTransactions: StateFlow<List<EmployeeTransaction>> = repository.allEmployeeTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debts: StateFlow<List<DebtRecord>> = repository.allDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtPayments: StateFlow<List<DebtPayment>> = repository.allDebtPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeShift: StateFlow<CashDrawerShift?> = repository.activeShift
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allShifts: StateFlow<List<CashDrawerShift>> = repository.allShifts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashMovements: StateFlow<List<CashDrawerMovement>> = repository.allCashMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exchangeAccounts: StateFlow<List<ExchangeAccount>> = repository.allExchangeAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exchangeAccountsIncludingArchived: StateFlow<List<ExchangeAccount>> = repository.allExchangeAccountsIncludingArchived
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exchangeTransfers: StateFlow<List<ExchangeTransfer>> = repository.allExchangeTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parties: StateFlow<List<PartyAccount>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val partiesIncludingArchived: StateFlow<List<PartyAccount>> = repository.allPartiesIncludingArchived
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerParties: StateFlow<List<PartyAccount>> = parties.map { list ->
        list.filter { it.type == "CUSTOMER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierParties: StateFlow<List<PartyAccount>> = parties.map { list ->
        list.filter { it.type == "SUPPLIER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerPartiesIncludingArchived: StateFlow<List<PartyAccount>> = partiesIncludingArchived.map { list ->
        list.filter { it.type == "CUSTOMER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierPartiesIncludingArchived: StateFlow<List<PartyAccount>> = partiesIncludingArchived.map { list ->
        list.filter { it.type == "SUPPLIER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogRecord>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val returns: StateFlow<List<ReturnRecord>> = repository.allReturns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingReturnTarget = MutableStateFlow<Pair<String, String?>?>(null)
    val pendingReturnTarget: StateFlow<Pair<String, String?>?> = _pendingReturnTarget.asStateFlow()

    fun openReturnsScreen(returnType: String = ReturnTypes.SALE_RETURN, invoiceKey: String? = null) {
        _pendingReturnTarget.value = returnType to invoiceKey
        navigateTo(AppScreen.RETURNS)
    }

    fun clearPendingReturnTarget() {
        _pendingReturnTarget.value = null
    }

    private val _completedReturnResult = MutableStateFlow<CompletedReturnResult?>(null)
    val completedReturnResult: StateFlow<CompletedReturnResult?> = _completedReturnResult.asStateFlow()

    fun dismissReturnReceipt() {
        _completedReturnResult.value = null
    }

    val storeSettings: StateFlow<StoreSettings?> = repository.storeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canNavigateBack: StateFlow<Boolean> = combine(
        _backStack,
        _currentScreen,
        _isSetupCompleted,
        storeSettings
    ) { stack, current, setupDoneFlag, settings ->
        val setupDone = setupDoneFlag || (settings?.isSetupCompleted == true)
        if (current == AppScreen.SETUP && !setupDone) {
            false
        } else {
            stack.size > 1 || current != AppScreen.HOME
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPinConfigured: StateFlow<Boolean> = storeSettings.map { settings ->
        settings != null && PinSecurityManager.isPinConfigured(settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val mustChangePin: StateFlow<Boolean> = storeSettings.map { settings ->
        settings?.mustChangePin == true || (settings != null && !PinSecurityManager.isPinConfigured(settings))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Completed Receipt Dialog Event ---
    private val _completedReceipt = MutableStateFlow<CompletedSaleReceipt?>(null)
    val completedReceipt: StateFlow<CompletedSaleReceipt?> = _completedReceipt.asStateFlow()

    fun dismissReceipt() {
        _completedReceipt.value = null
    }

    // --- Completed Collection Receipt Dialog Event ---
    private val _completedCollectionReceipt = MutableStateFlow<CollectionReceiptResult?>(null)
    val completedCollectionReceipt: StateFlow<CollectionReceiptResult?> = _completedCollectionReceipt.asStateFlow()

    fun dismissCollectionReceipt() {
        _completedCollectionReceipt.value = null
    }

    fun recordCollectionReceipt(
        payerName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumberInput: String,
        paymentMethod: String,
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (CollectionReceiptResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (amount <= 0.0) {
                _userMessage.value = "يرجى إدخال مبلغ التحصيل"
                return@launch
            }
            if (paymentMethod != "نقداً" && exchangeAccount == null) {
                _userMessage.value = "يرجى اختيار حساب الصرافة أو المحفظة"
                return@launch
            }
            try {
                val result = repository.recordCollectionReceipt(
                    payerName = payerName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucherNumberInput,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAccount,
                    notes = notes,
                    timestamp = timestamp
                )
                _completedCollectionReceipt.value = result
                _userMessage.value = "تم التحصيل بنجاح • رقم السند: ${result.voucherNumber}"
                onSuccess(result)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تنفيذ عملية التحصيل")
            }
        }
    }

    // --- Completed Supplier Payment Receipt Dialog Event ---
    private val _completedSupplierPaymentReceipt = MutableStateFlow<SupplierPaymentReceiptResult?>(null)
    val completedSupplierPaymentReceipt: StateFlow<SupplierPaymentReceiptResult?> = _completedSupplierPaymentReceipt.asStateFlow()

    fun dismissSupplierPaymentReceipt() {
        _completedSupplierPaymentReceipt.value = null
    }

    fun recordSupplierPaymentReceipt(
        supplierName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumberInput: String,
        paymentMethod: String,
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (SupplierPaymentReceiptResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (amount <= 0.0) {
                _userMessage.value = "يرجى إدخال مبلغ السداد"
                return@launch
            }
            if (paymentMethod != "نقداً" && exchangeAccount == null) {
                _userMessage.value = "يرجى اختيار حساب الصرافة أو المحفظة"
                return@launch
            }
            try {
                val result = repository.recordSupplierPaymentReceipt(
                    supplierName = supplierName,
                    party = party,
                    amount = amount,
                    voucherNumberInput = voucherNumberInput,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAccount,
                    notes = notes,
                    timestamp = timestamp
                )
                _completedSupplierPaymentReceipt.value = result
                _userMessage.value = "تم تسديد المورد بنجاح • رقم السند: ${result.voucherNumber}"
                onSuccess(result)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تنفيذ عملية التسديد للمورد")
            }
        }
    }

    // --- Toast / Feedback & Persistent Error Dialog ---
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _errorDialogMessage = MutableStateFlow<String?>(null)
    val errorDialogMessage: StateFlow<String?> = _errorDialogMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun dismissErrorDialog() {
        _errorDialogMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    private fun reportError(msg: String) {
        _userMessage.value = msg
        _errorDialogMessage.value = msg
    }

    fun verifyManagerPin(pin: String, onResult: (PinVerificationResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.verifyManagerPin(pin)
            onResult(result)
        }
    }

    fun updateManagerPin(currentPin: String?, newPin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateManagerPin(newPin = newPin, currentPin = currentPin)
                _userMessage.value = "تم تحديث رمز المدير (PIN) وتأمينه بنجاح"
                onResult(true, null)
            } catch (e: Exception) {
                val err = e.message ?: "تعذر تحديث رمز المدير"
                reportError(err)
                onResult(false, err)
            }
        }
    }

    // --- Computed Live Metrics ---
    val todaySalesTotal: StateFlow<Double> = sales.map { list ->
        list.filter { FormatUtils.isToday(it.timestamp) }.sumOf { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayProfitTotal: StateFlow<Double> = sales.map { list ->
        list.filter { FormatUtils.isToday(it.timestamp) }.sumOf { it.profit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentCashBalance: StateFlow<Double> = cashMovements.map { list ->
        var balance = 0.0
        for (m in list) {
            if (m.isIncome) balance += m.amount else balance -= m.amount
        }
        balance
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val inventoryCostValue: StateFlow<Double> = products.map { list ->
        list.sumOf { it.buyPrice * it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val inventoryExpectedSalesValue: StateFlow<Double> = products.map { list ->
        list.sumOf { it.sellPrice * it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val lowStockCount: StateFlow<Int> = products.map { list ->
        list.count { it.quantity <= 3 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCustomerDebts: StateFlow<Double> = debts.map { list ->
        list.filter { it.type == "CUSTOMER" && !it.isSettled }.sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSupplierDebts: StateFlow<Double> = debts.map { list ->
        list.filter { it.type == "SUPPLIER" && !it.isSettled }.sumOf { it.remainingAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExchangeBalance: StateFlow<Double> = exchangeAccounts.map { list ->
        list.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- User Actions ---

    fun executeSale(
        product: ProductItem,
        quantity: Int,
        sellPrice: Double,
        discount: Double,
        customerName: String,
        customerPhone: String,
        paymentType: String,
        employee: Employee?,
        unit: String = com.example.ui.util.ClothingUnits.PIECE,
        cashPart: Double = 0.0,
        transferPart: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        transferRef: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val sale = repository.executeSale(
                    product = product,
                    quantity = quantity,
                    sellPrice = sellPrice,
                    discount = discount,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paymentType = paymentType,
                    employee = employee,
                    unit = unit,
                    cashPart = cashPart,
                    transferPart = transferPart,
                    exchangeAccount = exchangeAccount,
                    transferRef = transferRef,
                    timestamp = timestamp
                )

                _completedReceipt.value = CompletedSaleReceipt(
                    sale = sale,
                    dateStr = FormatUtils.formatDate(sale.timestamp),
                    timeStr = FormatUtils.formatTime(sale.timestamp)
                )
                _userMessage.value = "تم إتمام عملية البيع بنجاح!"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إتمام عملية البيع")
            }
        }
    }

    fun executeCartSale(
        items: List<CartSaleItem>,
        discount: Double,
        customerName: String,
        customerPhone: String,
        paymentType: String,
        cashPart: Double = 0.0,
        transferPart: Double = 0.0,
        debtPart: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        transferRef: String = "",
        employee: Employee? = null,
        customInvoiceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (invoiceNumber: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val invoiceNumber = repository.executeCartSale(
                    items = items,
                    discount = discount,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paymentType = paymentType,
                    cashPart = cashPart,
                    transferPart = transferPart,
                    debtPart = debtPart,
                    exchangeAccount = exchangeAccount,
                    transferRef = transferRef,
                    employee = employee,
                    customInvoiceNumber = customInvoiceNumber,
                    timestamp = timestamp
                )
                _userMessage.value = "تم حفظ فاتورة البيع ($invoiceNumber) بنجاح!"
                onSuccess(invoiceNumber)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إتمام عملية البيع")
            }
        }
    }

    fun executeCartPurchase(
        items: List<CartPurchaseItem>,
        supplierName: String,
        invoiceNumber: String,
        paymentMethod: String,
        cashPaidPart: Double = 0.0,
        transferPaidPart: Double = 0.0,
        debtPart: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        transferRef: String = "",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (invoiceNumber: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val finalInvoiceNum = repository.executeCartPurchase(
                    items = items,
                    supplierName = supplierName,
                    invoiceNumber = invoiceNumber,
                    paymentMethod = paymentMethod,
                    cashPaidPart = cashPaidPart,
                    transferPaidPart = transferPaidPart,
                    debtPart = debtPart,
                    exchangeAccount = exchangeAccount,
                    transferRef = transferRef,
                    notes = notes,
                    timestamp = timestamp
                )
                _userMessage.value = "تم حفظ وتأكيد فاتورة الشراء ($finalInvoiceNum) بنجاح!"
                onSuccess(finalInvoiceNum)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إتمام فاتورة الشراء")
            }
        }
    }

    fun deleteSale(saleId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteSale(saleId)
                _userMessage.value = "تم حذف فاتورة البيع واسترجاع البضاعة والمبالغ"
            } catch (e: Exception) {
                reportError(e.message ?: "فشل إلغاء فاتورة البيع")
            }
        }
    }

    fun deleteSaleInvoice(invoiceNumber: String) {
        viewModelScope.launch {
            try {
                repository.deleteSaleInvoice(invoiceNumber)
                _userMessage.value = "تم إلغاء فاتورة البيع ($invoiceNumber) وعكس كامل الأصناف والحسابات"
            } catch (e: Exception) {
                reportError(e.message ?: "فشل إلغاء فاتورة البيع")
            }
        }
    }

    fun updateSale(sale: SaleTransaction, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateSale(sale)
                _userMessage.value = "تم تحديث بيانات فاتورة البيع"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "فشل تحديث فاتورة البيع")
            }
        }
    }

    fun updateSaleInvoice(
        originalInvoiceKey: String,
        updatedItems: List<SaleTransaction>,
        newInvoiceNumber: String,
        customerName: String,
        customerPhone: String,
        paymentType: String,
        cashAmount: Double,
        transferAmount: Double,
        debtAmount: Double,
        exchangeAccount: ExchangeAccount?,
        transferRef: String,
        timestamp: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateSaleInvoice(
                    originalInvoiceKey = originalInvoiceKey,
                    updatedItems = updatedItems,
                    newInvoiceNumber = newInvoiceNumber,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paymentType = paymentType,
                    cashAmount = cashAmount,
                    transferAmount = transferAmount,
                    debtAmount = debtAmount,
                    exchangeAccount = exchangeAccount,
                    transferRef = transferRef,
                    timestamp = timestamp
                )
                val label = newInvoiceNumber.ifBlank { originalInvoiceKey }
                _userMessage.value = "تم تحديث فاتورة البيع ($label) وإعادة احتساب المخزون والأرصدة"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "فشل تحديث فاتورة البيع")
            }
        }
    }

    fun recordPurchase(
        supplierName: String,
        category: String,
        modelName: String,
        color: String,
        size: String,
        unit: String,
        unitCount: Double,
        buyPricePerUnit: Double,
        sellPricePerUnit: Double,
        paidAmount: Double,
        invoiceNumber: String,
        paymentMethod: String,
        barcode: String,
        notes: String,
        colorsCount: Int,
        colorDetails: String,
        cashPaid: Double = 0.0,
        transferPaid: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.recordPurchase(
                    supplierName = supplierName,
                    category = category,
                    modelName = modelName,
                    color = color,
                    size = size,
                    unit = unit,
                    unitCount = unitCount,
                    buyPricePerUnit = buyPricePerUnit,
                    sellPricePerUnit = sellPricePerUnit,
                    paidAmount = paidAmount,
                    invoiceNumber = invoiceNumber,
                    paymentMethod = paymentMethod,
                    barcode = barcode,
                    notes = notes,
                    colorsCount = colorsCount,
                    colorDetails = colorDetails,
                    cashPaidPart = cashPaid,
                    transferPaidPart = transferPaid,
                    exchangeAccount = exchangeAccount,
                    timestamp = timestamp
                )
                _userMessage.value = "تم تسجيل شراء البضاعة وإضافتها إلى المخزون!"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "فشل تسجيل فاتورة الشراء")
            }
        }
    }

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            try {
                repository.deletePurchase(purchaseId)
                _userMessage.value = "تم حذف فاتورة الشراء وخصم الكمية واسترداد الدفعات"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف فاتورة الشراء")
            }
        }
    }

    fun deletePurchaseInvoice(invoiceNumber: String) {
        viewModelScope.launch {
            try {
                repository.deletePurchaseInvoice(invoiceNumber)
                _userMessage.value = "تم إلغاء فاتورة الشراء ($invoiceNumber) وعكس كامل الأصناف والحسابات"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف فاتورة الشراء")
            }
        }
    }

    fun updatePurchase(purchase: PurchaseRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updatePurchase(purchase)
                _userMessage.value = "تم تحديث بيانات فاتورة الشراء"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "فشل تحديث فاتورة الشراء")
            }
        }
    }

    fun updatePurchaseInvoice(
        originalInvoiceKey: String,
        updatedItems: List<PurchaseRecord>,
        newInvoiceNumber: String,
        supplierName: String,
        paymentMethod: String,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccount: ExchangeAccount?,
        timestamp: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updatePurchaseInvoice(
                    originalInvoiceKey = originalInvoiceKey,
                    updatedItems = updatedItems,
                    newInvoiceNumber = newInvoiceNumber,
                    supplierName = supplierName,
                    paymentMethod = paymentMethod,
                    cashPaid = cashPaid,
                    transferPaid = transferPaid,
                    debtAmount = debtAmount,
                    exchangeAccount = exchangeAccount,
                    timestamp = timestamp
                )
                val label = newInvoiceNumber.ifBlank { originalInvoiceKey }
                _userMessage.value = "تم تحديث فاتورة الشراء ($label) وإعادة احتساب المخزون والأرصدة"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "فشل تحديث فاتورة الشراء")
            }
        }
    }

    fun saveProduct(product: ProductItem, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveProduct(product)
            _userMessage.value = "تم حفظ بيانات الصنف"
            onSuccess()
        }
    }

    fun deleteProduct(id: Long) {
        viewModelScope.launch {
            repository.deleteProduct(id)
            _userMessage.value = "تم حذف الصنف من المخزون"
        }
    }

    fun recordExpense(
        category: String,
        amount: Double,
        notes: String,
        receiptNumber: String,
        paidFromCash: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        employee: Employee? = null,
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى إدخال مبلغ المصروف")
                return@launch
            }
            if (!paidFromCash && exchangeAccount == null) {
                reportError("اختر الصراف أو المحفظة التي سيُخصم منها المصروف")
                return@launch
            }
            try {
                repository.recordExpense(
                    category = category,
                    amount = amount,
                    notes = notes,
                    receiptNumber = receiptNumber,
                    paidFromCash = paidFromCash,
                    exchangeAccount = exchangeAccount,
                    employee = employee,
                    timestamp = timestamp
                )
                _userMessage.value = if (employee != null) "تم تسجيل صرفة يومية للعامل (${employee.name}) بنجاح" else "تم تسجيل المصروف بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل المصروف")
            }
        }
    }

    fun updateExpense(expense: ExpenseRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateExpense(expense)
                _userMessage.value = "تم تعديل بيانات المصروف بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تعديل المصروف")
            }
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(id)
                _userMessage.value = "تم حذف المصروف واسترجاع الرصيد"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف المصروف")
            }
        }
    }

    fun saveEmployee(employee: Employee, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (employee.name.isBlank()) {
                _userMessage.value = "يرجى إدخال اسم العامل"
                return@launch
            }
            try {
                repository.saveEmployee(employee)
                _userMessage.value = "تم حفظ بيانات العامل بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ بيانات العامل")
            }
        }
    }

    fun deleteEmployee(employeeId: Long) {
        viewModelScope.launch {
            try {
                when (repository.deleteEmployee(employeeId)) {
                    DeleteEntityResult.ARCHIVED -> {
                        _userMessage.value = "تمت أرشفة العامل لوجود حركات مالية أو مبيعات مرتبطة به (تم الحفاظ على كشوفاته)"
                    }
                    DeleteEntityResult.DELETED -> {
                        _userMessage.value = "تم حذف بيانات العامل من المحل"
                    }
                    DeleteEntityResult.NOT_FOUND -> {
                        _userMessage.value = "لم يتم العثور على سجل العامل"
                    }
                }
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف بيانات العامل")
            }
        }
    }

    fun recordEmployeeTransaction(
        employeeId: Long,
        employeeName: String,
        type: String,
        amount: Double,
        notes: String,
        paidFromCashDrawer: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى إدخال المبلغ")
                return@launch
            }
            if (!paidFromCashDrawer && exchangeAccount == null && type != "خصم غياب") {
                reportError("اختر الصراف أو المحفظة التي سيُخصم منها المبلغ")
                return@launch
            }
            try {
                repository.recordEmployeeTransaction(
                    employeeId = employeeId,
                    employeeName = employeeName,
                    type = type,
                    amount = amount,
                    notes = notes,
                    paidFromCashDrawer = paidFromCashDrawer,
                    exchangeAccount = exchangeAccount,
                    referenceNumber = referenceNumber,
                    timestamp = timestamp
                )
                _userMessage.value = "تم تسجيل $type للعامل $employeeName بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل حركة العامل")
            }
        }
    }

    fun updateEmployeeTransaction(transaction: EmployeeTransaction, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateEmployeeTransaction(transaction)
                _userMessage.value = "تم تعديل حركة العامل بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تعديل حركة العامل")
            }
        }
    }

    fun deleteEmployeeTransaction(txId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteEmployeeTransaction(txId)
                _userMessage.value = "تم حذف حركة العامل بنجاح"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف حركة العامل")
            }
        }
    }

    fun saveExchangeAccount(account: ExchangeAccount, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (account.name.isBlank()) {
                _userMessage.value = "يرجى إدخال اسم الصراف أو المحفظة"
                return@launch
            }
            try {
                repository.saveExchangeAccount(account)
                _userMessage.value = "تم حفظ بيانات حساب الصراف/المحفظة"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ بيانات الحساب")
            }
        }
    }

    fun deleteExchangeAccount(id: Long) {
        viewModelScope.launch {
            try {
                when (repository.deleteExchangeAccount(id)) {
                    DeleteEntityResult.ARCHIVED -> {
                        _userMessage.value = "تمت أرشفة حساب الصرافة لوجود عمليات مرتبطة به (تم الحفاظ على كشف الحساب)"
                    }
                    DeleteEntityResult.DELETED -> {
                        _userMessage.value = "تم حذف حساب الصراف"
                    }
                    DeleteEntityResult.NOT_FOUND -> {
                        _userMessage.value = "لم يتم العثور على الحساب"
                    }
                }
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف حساب الصراف")
            }
        }
    }

    fun transferBetweenCashAndExchange(
        accountId: Long,
        amount: Double,
        fromCashToExchange: Boolean,
        notes: String,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى تحديد مبلغ التحويل")
                return@launch
            }
            try {
                repository.transferBetweenCashAndExchange(accountId, amount, fromCashToExchange, notes, referenceNumber, timestamp)
                val actionName = if (fromCashToExchange) "إيداع كاش لحساب الصراف" else "سحب كاش من الصراف للصندوق"
                _userMessage.value = "تم تنفيذ $actionName بنجاح!"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تنفيذ التحويل")
            }
        }
    }

    fun recordDebtPayment(
        debt: DebtRecord,
        amount: Double,
        notes: String,
        paymentMethod: String = "نقداً",
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى إدخال مبلغ الدفعة")
                return@launch
            }
            if (amount > debt.remainingAmount + 0.01) {
                reportError("مبلغ الدفعة أكبر من المبلغ المتبقي (${FormatUtils.formatCurrency(debt.remainingAmount)})")
                return@launch
            }
            try {
                repository.recordDebtPayment(debt, amount, notes, paymentMethod, exchangeAccount, referenceNumber, timestamp)
                _userMessage.value = "تم تسجيل الدفعة بنجاح وتحديث الصندوق/الصراف والدين"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل الدفعة")
            }
        }
    }

    fun addNewDebt(debt: DebtRecord, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (debt.partyName.isBlank() || debt.originalAmount <= 0) {
                _userMessage.value = "يرجى كتابة الاسم والمبلغ"
                return@launch
            }
            try {
                repository.addNewDebt(debt)
                _userMessage.value = "تم تسجيل الدين الجديد"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل الدين")
            }
        }
    }

    fun updateDebt(debt: DebtRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (debt.originalAmount <= 0) {
                _userMessage.value = "يرجى إدخال مبلغ صحيح للدين"
                return@launch
            }
            try {
                repository.updateDebt(debt)
                _userMessage.value = "تم تعديل بيانات الدين بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تعديل بيانات الدين")
            }
        }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteDebt(id)
                _userMessage.value = "تم حذف سجل الدين"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف سجل الدين")
            }
        }
    }

    fun saveParty(
        party: PartyAccount,
        initialDebt: Double = 0.0,
        debtNotes: String = "",
        initialDebtTimestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (party.name.isBlank()) {
                _userMessage.value = "يرجى إدخال الاسم"
                return@launch
            }
            try {
                repository.saveParty(party, initialDebt, debtNotes, initialDebtTimestamp)
                val typeTitle = if (party.type == "CUSTOMER") "العميل" else "المورد"
                _userMessage.value = "تم حفظ وفتح حساب $typeTitle بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ بيانات الحساب")
            }
        }
    }

    fun deleteParty(id: Long) {
        viewModelScope.launch {
            try {
                when (repository.deleteParty(id)) {
                    DeleteEntityResult.ARCHIVED -> {
                        _userMessage.value = "تمت أرشفة الحساب لوجود فواتير أو ديون أو دفعات مرتبطة به (تم الحفاظ على كشف الحساب)"
                    }
                    DeleteEntityResult.DELETED -> {
                        _userMessage.value = "تم حذف الحساب بنجاح"
                    }
                    DeleteEntityResult.NOT_FOUND -> {
                        _userMessage.value = "لم يتم العثور على الحساب"
                    }
                }
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حذف الحساب")
            }
        }
    }

    fun recordPartyPayment(
        partyName: String,
        partyType: String,
        totalPayment: Double,
        notes: String,
        paymentMethod: String = "نقداً",
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (totalPayment <= 0) {
                reportError("يرجى إدخال مبلغ الدفعة")
                return@launch
            }
            try {
                val result = repository.recordPartyPayment(
                    partyName = partyName,
                    partyType = partyType,
                    totalPayment = totalPayment,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAccount,
                    referenceNumber = referenceNumber,
                    timestamp = timestamp
                )
                if (result) {
                    val actionType = if (partyType == "CUSTOMER") "قبض زلط من العميل" else "سداد دفعة للمورد"
                    _userMessage.value = "تم توثيق $actionType بنجاح وتحديث الحساب والأرصدة"
                    onSuccess()
                } else {
                    reportError("تعذر تسجيل الدفعة، تأكد من وجود ديون مستحقة")
                }
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل الدفعة")
            }
        }
    }

    fun openShift(cashierName: String, openingBalance: Double, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.openShift(cashierName, openingBalance, notes)
                _userMessage.value = "تم فتح الوردية بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر فتح الوردية")
            }
        }
    }

    fun closeShift(shift: CashDrawerShift, actualCashInDrawer: Double, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.closeShift(shift, actualCashInDrawer, notes)
                _userMessage.value = "تم إغلاق الوردية وتوثيق الرصيد النهائي"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إغلاق الوردية")
            }
        }
    }

    fun recordOwnerWithdrawal(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis(), onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى تحديد مبلغ السحب")
                return@launch
            }
            try {
                repository.recordOwnerWithdrawal(amount, reason, timestamp)
                _userMessage.value = "تم توثيق سحب صاحب المحل من الصندوق"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل السحب")
            }
        }
    }

    fun recordManualDeposit(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis(), onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                reportError("يرجى تحديد مبلغ الإيداع")
                return@launch
            }
            try {
                repository.recordManualDeposit(amount, reason, timestamp)
                _userMessage.value = "تم تسجيل الإيداع النقدي في الصندوق"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل الإيداع")
            }
        }
    }

    fun clearData(transactionsOnly: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (transactionsOnly) {
                    repository.clearTransactionsOnly()
                    _userMessage.value = "تم تصفير وحذف جميع حركات البيع والشراء والمصروفات والديون"
                } else {
                    repository.clearEverythingCompletely(clearConfigDictionaries = true)
                    _userMessage.value = "تم تهيئة التطبيق وإفراغ جميع البيانات والمنتجات والأنواع بالكامل"
                }
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تصفير البيانات")
            }
        }
    }

    fun executeStoreFullAudit(
        actualDrawerCash: Double,
        exchangeBalances: Map<Long, Double>,
        resetTransactions: Boolean,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.executeStoreFullAudit(
                    actualDrawerCash = actualDrawerCash,
                    exchangeBalances = exchangeBalances,
                    resetTransactions = resetTransactions,
                    notes = notes
                )
                _userMessage.value = "تم اعتماد الجرد الفعلي وتحديث الصندوق وحسابات الصرافين بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر اعتماد الجرد الفعلي")
            }
        }
    }

    fun saveSettings(settings: StoreSettings) {
        viewModelScope.launch {
            try {
                repository.saveStoreSettings(settings.copy(id = 1, isSetupCompleted = true))
                _userMessage.value = "تم حفظ إعدادات المحل والشعار بنجاح"
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ الإعدادات")
            }
        }
    }

    fun completeInitialSetup(
        settings: StoreSettings,
        rawManagerPin: String = "",
        categories: List<String>,
        sizes: List<String>,
        expenseCategories: List<String>,
        accountTypes: List<String>,
        colors: List<String>,
        roles: List<String>,
        targetScreenAfterSetup: AppScreen = AppScreen.HOME,
        onCompleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val existing = repository.getStoreSettingsDirect()
                val alreadyHasPin = PinSecurityManager.isPinConfigured(existing)
                val targetSettings = if (rawManagerPin.isNotBlank() || !alreadyHasPin) {
                    val strengthErr = PinSecurityManager.validatePinStrength(rawManagerPin)
                    if (strengthErr != null) {
                        reportError(strengthErr)
                        return@launch
                    }
                    PinSecurityManager.withNewPin(
                        settings.copy(id = 1, isSetupCompleted = true),
                        rawManagerPin
                    )
                } else {
                    settings.copy(id = 1, isSetupCompleted = true)
                }
                repository.saveStoreSettings(targetSettings)
                _isSetupCompleted.value = true
                repository.replaceConfigItemsForSetup(
                    categories = categories,
                    sizes = sizes,
                    expenseCategories = expenseCategories,
                    accountTypes = accountTypes,
                    colors = colors,
                    roles = roles
                )
                if (targetScreenAfterSetup == AppScreen.OPENING_BALANCES) {
                    if (rawManagerPin.isNotBlank() || !alreadyHasPin) {
                        _openingBalancesAuthorized.value = true
                    }
                    _backStack.value = listOf(AppScreen.HOME, AppScreen.OPENING_BALANCES)
                } else if (targetScreenAfterSetup == AppScreen.HOME) {
                    _backStack.value = listOf(AppScreen.HOME)
                } else {
                    _backStack.value = listOf(AppScreen.HOME, targetScreenAfterSetup)
                }
                _currentScreen.value = targetScreenAfterSetup
                _userMessage.value = "تم حفظ تهيئة «${settings.shopNameAr}» وبدء تشغيل النظام بنجاح"
                onCompleted()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ تهيئة المحل")
            }
        }
    }

    // ====================================================================
    // Opening Balances (الأرصدة الافتتاحية الشاملة للمحل بدون خصم من الصندوق)
    // ====================================================================

    fun setOpeningCashBalance(
        amount: Double,
        replaceExistingBalance: Boolean,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.setOpeningCashBalance(
                    amount = amount,
                    replaceExistingBalance = replaceExistingBalance,
                    notes = notes,
                    timestamp = timestamp
                )
                _userMessage.value = if (replaceExistingBalance) {
                    "تم اعتماد الرصيد الافتتاحي الفعلي للصندوق بنجاح"
                } else {
                    "تمت إضافة الرصيد الافتتاحي إلى الصندوق بنجاح"
                }
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ الرصيد الافتتاحي للصندوق")
            }
        }
    }

    fun saveOpeningExchangeAccount(
        accountId: Long = 0L,
        name: String,
        type: String,
        accountNumber: String = "",
        phone: String = "",
        openingBalance: Double,
        replaceExistingBalance: Boolean = false,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.saveOpeningExchangeAccount(
                    accountId = accountId,
                    name = name,
                    type = type,
                    accountNumber = accountNumber,
                    phone = phone,
                    openingBalance = openingBalance,
                    replaceExistingBalance = replaceExistingBalance,
                    notes = notes,
                    timestamp = timestamp
                )
                _userMessage.value = "تم حفظ الرصيد الافتتاحي لـ «${name.trim()}» دون خصم من الصندوق"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ الرصيد الافتتاحي للصراف أو المحفظة")
            }
        }
    }

    fun addOpeningInventoryItems(
        items: List<CartPurchaseItem>,
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val count = repository.addOpeningInventoryItems(items = items, timestamp = timestamp)
                _userMessage.value = "تمت إضافة $count صنف/أصناف إلى المخزن كرصيد افتتاحي دون خصم من الصندوق"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إضافة بضاعة المخزن الافتتاحية")
            }
        }
    }

    fun recordPartyOpeningBalance(
        type: String,
        name: String,
        phone: String = "",
        address: String = "",
        amount: Double,
        isNormalDebtDirection: Boolean = true,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.recordPartyOpeningBalance(
                    type = type,
                    name = name,
                    phone = phone,
                    address = address,
                    amount = amount,
                    isNormalDebtDirection = isNormalDebtDirection,
                    notes = notes,
                    timestamp = timestamp
                )
                val partyLabel = if (type == "CUSTOMER") "العميل" else "المورد"
                _userMessage.value = "تم تسجيل الرصيد الافتتاحي لـ$partyLabel «${name.trim()}» بنجاح"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر حفظ الرصيد الافتتاحي")
            }
        }
    }

    fun recordEmployeeOpeningBalance(
        employeeId: Long = 0L,
        name: String,
        phone: String = "",
        role: String = "بائع",
        salaryType: String = "شهري",
        baseSalary: Double = 0.0,
        commissionPercent: Double = 0.0,
        openingAmount: Double = 0.0,
        isOwedToEmployee: Boolean = true,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.recordEmployeeOpeningBalance(
                    employeeId = employeeId,
                    name = name,
                    phone = phone,
                    role = role,
                    salaryType = salaryType,
                    baseSalary = baseSalary,
                    commissionPercent = commissionPercent,
                    openingAmount = openingAmount,
                    isOwedToEmployee = isOwedToEmployee,
                    notes = notes,
                    timestamp = timestamp
                )
                _userMessage.value = "تم تسجيل بيانات والرصيد الافتتاحي للعامل «${name.trim()}» بدون خصم من الصندوق"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تسجيل بيانات العامل")
            }
        }
    }

    fun validateBackupPreview(jsonString: String): Result<BackupPreview> {
        return BackupManager.validateAndParseBackup(jsonString).map { it.preview }
    }

    fun restoreBackup(jsonString: String, onResult: (Result<BackupPreview>) -> Unit) {
        viewModelScope.launch {
            try {
                val result = BackupManager.restoreFromJson(jsonString, repository)
                if (result.isSuccess) {
                    _userMessage.value = "تمت استعادة النسخة الاحتياطية كاملة بنجاح!"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "ملف النسخة الاحتياطية غير صالح"
                    reportError(err)
                }
                onResult(result)
            } catch (e: Exception) {
                val err = "حدث خطأ أثناء الاستعادة: ${e.message}"
                reportError(err)
                onResult(Result.failure(e))
            }
        }
    }

    // ====================================================================
    // Invoice-Based Sales & Purchase Returns (المرتجعات حسب الفاتورة)
    // ====================================================================

    fun executeSaleReturn(
        originalInvoiceKey: String,
        lines: List<ReturnLineRequest>,
        returnNumberInput: String = "",
        settlementMethod: String,
        cashAmount: Double = 0.0,
        transferAmount: Double = 0.0,
        debtReducedAmount: Double = 0.0,
        surplusCreditAmount: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (CompletedReturnResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.executeSaleReturn(
                    originalInvoiceKey = originalInvoiceKey,
                    lines = lines,
                    returnNumberInput = returnNumberInput,
                    settlementMethod = settlementMethod,
                    cashAmount = cashAmount,
                    transferAmount = transferAmount,
                    debtReducedAmount = debtReducedAmount,
                    surplusCreditAmount = surplusCreditAmount,
                    exchangeAccount = exchangeAccount,
                    notes = notes,
                    timestamp = timestamp
                )
                _completedReturnResult.value = result
                _userMessage.value = "تم توثيق مرتجع المبيعات #${result.returnNumber} بنجاح"
                onSuccess(result)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تنفيذ مرتجع المبيعات")
            }
        }
    }

    fun executePurchaseReturn(
        originalInvoiceKey: String,
        lines: List<ReturnLineRequest>,
        returnNumberInput: String = "",
        settlementMethod: String,
        cashAmount: Double = 0.0,
        transferAmount: Double = 0.0,
        debtReducedAmount: Double = 0.0,
        surplusCreditAmount: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        onSuccess: (CompletedReturnResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.executePurchaseReturn(
                    originalInvoiceKey = originalInvoiceKey,
                    lines = lines,
                    returnNumberInput = returnNumberInput,
                    settlementMethod = settlementMethod,
                    cashAmount = cashAmount,
                    transferAmount = transferAmount,
                    debtReducedAmount = debtReducedAmount,
                    surplusCreditAmount = surplusCreditAmount,
                    exchangeAccount = exchangeAccount,
                    notes = notes,
                    timestamp = timestamp
                )
                _completedReturnResult.value = result
                _userMessage.value = "تم توثيق مرتجع المشتريات #${result.returnNumber} بنجاح"
                onSuccess(result)
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر تنفيذ مرتجع المشتريات")
            }
        }
    }

    fun deleteReturn(returnNumber: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteReturn(returnNumber)
                _userMessage.value = "تم إلغاء وحذف حركة المرتجع #$returnNumber وعكس آثارها المالية والمخزنية"
                onSuccess()
            } catch (e: Exception) {
                reportError(e.message ?: "تعذر إلغاء حركة المرتجع")
            }
        }
    }
}
