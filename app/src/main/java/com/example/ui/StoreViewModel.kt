package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import com.example.ui.util.FormatUtils
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

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("hesabi_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("initial_wipe_v8_done", false)) {
                repository.clearEverythingCompletely(clearConfigDictionaries = false)
                prefs.edit().putBoolean("initial_wipe_v8_done", true).apply()
            }
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
            if (!settings.isSetupCompleted) {
                _currentScreen.value = AppScreen.SETUP
            }
            _isInitialized.value = true
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun openSetupWizard() {
        _currentScreen.value = AppScreen.SETUP
    }

    fun navigateBack(): Boolean {
        if (_currentScreen.value == AppScreen.SETUP) {
            val completed = storeSettings.value?.isSetupCompleted == true
            if (completed) {
                _currentScreen.value = AppScreen.HOME
                return true
            }
            return false
        }
        if (_currentScreen.value != AppScreen.HOME) {
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

    val exchangeTransfers: StateFlow<List<ExchangeTransfer>> = repository.allExchangeTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parties: StateFlow<List<PartyAccount>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerParties: StateFlow<List<PartyAccount>> = parties.map { list ->
        list.filter { it.type == "CUSTOMER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierParties: StateFlow<List<PartyAccount>> = parties.map { list ->
        list.filter { it.type == "SUPPLIER" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogRecord>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeSettings: StateFlow<StoreSettings?> = repository.storeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                _userMessage.value = e.message ?: "تعذر تنفيذ عملية التحصيل"
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
                _userMessage.value = e.message ?: "تعذر تنفيذ عملية التسديد للمورد"
            }
        }
    }

    // --- Toast / Feedback Message ---
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
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
                _userMessage.value = e.message ?: "تعذر إتمام عملية البيع"
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
                _userMessage.value = e.message ?: "تعذر إتمام عملية البيع"
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
                _userMessage.value = e.message ?: "تعذر إتمام فاتورة الشراء"
            }
        }
    }

    fun deleteSale(saleId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteSale(saleId)
                _userMessage.value = "تم حذف فاتورة البيع واسترجاع البضاعة والمبالغ"
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "فشل إلغاء فاتورة البيع"
            }
        }
    }

    fun updateSale(sale: SaleTransaction) {
        viewModelScope.launch {
            try {
                repository.updateSale(sale)
                _userMessage.value = "تم تحديث بيانات فاتورة البيع"
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "فشل تحديث فاتورة البيع"
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
                _userMessage.value = e.message ?: "فشل تسجيل فاتورة الشراء"
            }
        }
    }

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            try {
                repository.deletePurchase(purchaseId)
                _userMessage.value = "تم حذف فاتورة الشراء وخصم الكمية واسترداد الدفعات"
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "تعذر حذف فاتورة الشراء"
            }
        }
    }

    fun updatePurchase(purchase: PurchaseRecord) {
        viewModelScope.launch {
            repository.updatePurchase(purchase)
            _userMessage.value = "تم تحديث بيانات فاتورة الشراء"
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
                _userMessage.value = "يرجى إدخال مبلغ المصروف"
                return@launch
            }
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
        }
    }

    fun updateExpense(expense: ExpenseRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateExpense(expense)
            _userMessage.value = "تم تعديل بيانات المصروف بنجاح"
            onSuccess()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            _userMessage.value = "تم حذف المصروف واسترجاع الرصيد"
        }
    }

    fun saveEmployee(employee: Employee, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (employee.name.isBlank()) {
                _userMessage.value = "يرجى إدخال اسم العامل"
                return@launch
            }
            repository.saveEmployee(employee)
            _userMessage.value = "تم حفظ بيانات العامل بنجاح"
            onSuccess()
        }
    }

    fun deleteEmployee(employeeId: Long) {
        viewModelScope.launch {
            repository.deleteEmployee(employeeId)
            _userMessage.value = "تم حذف بيانات العامل من المحل"
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
                _userMessage.value = "يرجى إدخال المبلغ"
                return@launch
            }
            if (!paidFromCashDrawer && exchangeAccount == null && type != "خصم غياب") {
                _userMessage.value = "اختر الصراف أو المحفظة التي سيُخصم منها المبلغ"
                return@launch
            }
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
        }
    }

    fun updateEmployeeTransaction(transaction: EmployeeTransaction, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateEmployeeTransaction(transaction)
            _userMessage.value = "تم تعديل حركة العامل بنجاح"
            onSuccess()
        }
    }

    fun deleteEmployeeTransaction(txId: Long) {
        viewModelScope.launch {
            repository.deleteEmployeeTransaction(txId)
            _userMessage.value = "تم حذف حركة العامل بنجاح"
        }
    }

    fun saveExchangeAccount(account: ExchangeAccount, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (account.name.isBlank()) {
                _userMessage.value = "يرجى إدخال اسم الصراف أو المحفظة"
                return@launch
            }
            repository.saveExchangeAccount(account)
            _userMessage.value = "تم حفظ بيانات حساب الصراف/المحفظة"
            onSuccess()
        }
    }

    fun deleteExchangeAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteExchangeAccount(id)
            _userMessage.value = "تم حذف حساب الصراف"
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
                _userMessage.value = "يرجى تحديد مبلغ التحويل"
                return@launch
            }
            repository.transferBetweenCashAndExchange(accountId, amount, fromCashToExchange, notes, referenceNumber, timestamp)
            val actionName = if (fromCashToExchange) "إيداع كاش لحساب الصراف" else "سحب كاش من الصراف للصندوق"
            _userMessage.value = "تم تنفيذ $actionName بنجاح!"
            onSuccess()
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
                _userMessage.value = "يرجى إدخال مبلغ الدفعة"
                return@launch
            }
            if (amount > debt.remainingAmount) {
                _userMessage.value = "مبلغ الدفعة أكبر من المبلغ المتبقي (${FormatUtils.formatCurrency(debt.remainingAmount)})"
                return@launch
            }
            repository.recordDebtPayment(debt, amount, notes, paymentMethod, exchangeAccount, referenceNumber, timestamp)
            _userMessage.value = "تم تسجيل الدفعة بنجاح وتحديث الصندوق/الصراف والدين"
            onSuccess()
        }
    }

    fun addNewDebt(debt: DebtRecord, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (debt.partyName.isBlank() || debt.originalAmount <= 0) {
                _userMessage.value = "يرجى كتابة الاسم والمبلغ"
                return@launch
            }
            repository.addNewDebt(debt)
            _userMessage.value = "تم تسجيل الدين الجديد"
            onSuccess()
        }
    }

    fun updateDebt(debt: DebtRecord, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (debt.originalAmount <= 0) {
                _userMessage.value = "يرجى إدخال مبلغ صحيح للدين"
                return@launch
            }
            repository.updateDebt(debt)
            _userMessage.value = "تم تعديل بيانات الدين بنجاح"
            onSuccess()
        }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch {
            repository.deleteDebt(id)
            _userMessage.value = "تم حذف سجل الدين"
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
            repository.saveParty(party, initialDebt, debtNotes, initialDebtTimestamp)
            val typeTitle = if (party.type == "CUSTOMER") "العميل" else "المورد"
            _userMessage.value = "تم حفظ وفتح حساب $typeTitle بنجاح"
            onSuccess()
        }
    }

    fun deleteParty(id: Long) {
        viewModelScope.launch {
            repository.deleteParty(id)
            _userMessage.value = "تم حذف الحساب بنجاح"
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
                _userMessage.value = "يرجى إدخال مبلغ الدفعة"
                return@launch
            }
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
                _userMessage.value = "تعذر تسجيل الدفعة، تأكد من وجود ديون مستحقة"
            }
        }
    }

    fun openShift(cashierName: String, openingBalance: Double, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.openShift(cashierName, openingBalance, notes)
            _userMessage.value = "تم فتح الوردية بنجاح"
            onSuccess()
        }
    }

    fun closeShift(shift: CashDrawerShift, actualCashInDrawer: Double, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.closeShift(shift, actualCashInDrawer, notes)
            _userMessage.value = "تم إغلاق الوردية وتوثيق الرصيد النهائي"
            onSuccess()
        }
    }

    fun recordOwnerWithdrawal(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis(), onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى تحديد مبلغ السحب"
                return@launch
            }
            repository.recordOwnerWithdrawal(amount, reason, timestamp)
            _userMessage.value = "تم توثيق سحب صاحب المحل من الصندوق"
            onSuccess()
        }
    }

    fun recordManualDeposit(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis(), onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى تحديد مبلغ الإيداع"
                return@launch
            }
            repository.recordManualDeposit(amount, reason, timestamp)
            _userMessage.value = "تم تسجيل الإيداع النقدي في الصندوق"
            onSuccess()
        }
    }

    fun clearData(transactionsOnly: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            if (transactionsOnly) {
                repository.clearTransactionsOnly()
                _userMessage.value = "تم تصفير وحذف جميع حركات البيع والشراء والمصروفات والديون"
            } else {
                repository.clearEverythingCompletely(clearConfigDictionaries = true)
                _userMessage.value = "تم تهيئة التطبيق وإفراغ جميع البيانات والمنتجات والأنواع بالكامل"
            }
            onSuccess()
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
            repository.executeStoreFullAudit(
                actualDrawerCash = actualDrawerCash,
                exchangeBalances = exchangeBalances,
                resetTransactions = resetTransactions,
                notes = notes
            )
            _userMessage.value = "تم اعتماد الجرد الفعلي وتحديث الصندوق وحسابات الصرافين بنجاح"
            onSuccess()
        }
    }

    fun saveSettings(settings: StoreSettings) {
        viewModelScope.launch {
            repository.saveStoreSettings(settings.copy(id = 1, isSetupCompleted = true))
            _userMessage.value = "تم حفظ إعدادات المحل والشعار بنجاح"
        }
    }

    fun completeInitialSetup(
        settings: StoreSettings,
        categories: List<String>,
        sizes: List<String>,
        expenseCategories: List<String>,
        accountTypes: List<String>,
        colors: List<String>,
        roles: List<String>,
        onCompleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveStoreSettings(settings.copy(id = 1, isSetupCompleted = true))
            repository.replaceConfigItemsForSetup(
                categories = categories,
                sizes = sizes,
                expenseCategories = expenseCategories,
                accountTypes = accountTypes,
                colors = colors,
                roles = roles
            )
            _currentScreen.value = AppScreen.HOME
            _userMessage.value = "تم حفظ تهيئة «${settings.shopNameAr}» وبدء تشغيل النظام بنجاح"
            onCompleted()
        }
    }

    fun restoreBackup(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val ok = com.example.ui.util.BackupManager.restoreFromJson(jsonString, repository)
                if (ok) {
                    _userMessage.value = "تمت استعادة النسخة الاحتياطية بنجاح!"
                } else {
                    _userMessage.value = "ملف النسخة الاحتياطية غير متوافق"
                }
                onResult(ok)
            } catch (e: Exception) {
                _userMessage.value = "حدث خطأ أثناء الاستعادة: ${e.message}"
                onResult(false)
            }
        }
    }
}
