package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppScreen {
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
    TRANSACTIONS_LOG
}

data class CompletedSaleReceipt(
    val sale: SaleTransaction,
    val dateStr: String,
    val timeStr: String
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StoreRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = StoreRepository(db.storeDao())
    }

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (_currentScreen.value != AppScreen.HOME) {
            _currentScreen.value = AppScreen.HOME
            return true
        }
        return false
    }

    // --- Reactive Data Streams ---
    val products: StateFlow<List<ProductItem>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // --- Completed Receipt Dialog Event ---
    private val _completedReceipt = MutableStateFlow<CompletedSaleReceipt?>(null)
    val completedReceipt: StateFlow<CompletedSaleReceipt?> = _completedReceipt.asStateFlow()

    fun dismissReceipt() {
        _completedReceipt.value = null
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val totalPieces = quantity * com.example.ui.util.ClothingUnits.getMultiplier(unit)
            if (quantity <= 0) {
                _userMessage.value = "يرجى تحديد كمية صحيحة"
                return@launch
            }
            if (totalPieces > product.quantity) {
                _userMessage.value = "الكمية المطلوبة ($totalPieces حبه) أكبر من المتوفر بالمخزون (${product.quantity} حبه)"
                return@launch
            }

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
                transferRef = transferRef
            )

            _completedReceipt.value = CompletedSaleReceipt(
                sale = sale,
                dateStr = FormatUtils.formatDate(sale.timestamp),
                timeStr = FormatUtils.formatTime(sale.timestamp)
            )
            _userMessage.value = "تم إتمام عملية البيع بنجاح!"
            onSuccess()
        }
    }

    fun deleteSale(saleId: Long) {
        viewModelScope.launch {
            repository.deleteSale(saleId)
            _userMessage.value = "تم حذف فاتورة البيع واسترجاع البضاعة والمبالغ"
        }
    }

    fun updateSale(sale: SaleTransaction) {
        viewModelScope.launch {
            repository.updateSale(sale)
            _userMessage.value = "تم تحديث بيانات فاتورة البيع"
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (modelName.isBlank() || unitCount <= 0 || buyPricePerUnit <= 0) {
                _userMessage.value = "يرجى إكمال بيانات البضاعة وسعر الشراء والكمية"
                return@launch
            }
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
                exchangeAccount = exchangeAccount
            )
            _userMessage.value = "تم تسجيل شراء البضاعة وإضافتها إلى المخزون!"
            onSuccess()
        }
    }

    fun deletePurchase(purchaseId: Long) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
            _userMessage.value = "تم حذف فاتورة الشراء وخصم الكمية واسترداد الدفعات"
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
                exchangeAccount = exchangeAccount
            )
            _userMessage.value = "تم تسجيل المصروف بنجاح"
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
                exchangeAccount = exchangeAccount
            )
            _userMessage.value = "تم تسجيل $type للعامل $employeeName بنجاح"
            onSuccess()
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
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى تحديد مبلغ التحويل"
                return@launch
            }
            repository.transferBetweenCashAndExchange(accountId, amount, fromCashToExchange, notes)
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
            repository.recordDebtPayment(debt, amount, notes, paymentMethod, exchangeAccount)
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

    fun deleteDebt(id: Long) {
        viewModelScope.launch {
            repository.deleteDebt(id)
            _userMessage.value = "تم حذف سجل الدين"
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

    fun recordOwnerWithdrawal(amount: Double, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى تحديد مبلغ السحب"
                return@launch
            }
            repository.recordOwnerWithdrawal(amount, reason)
            _userMessage.value = "تم توثيق سحب صاحب المحل من الصندوق"
            onSuccess()
        }
    }

    fun recordManualDeposit(amount: Double, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى تحديد مبلغ الإيداع"
                return@launch
            }
            repository.recordManualDeposit(amount, reason)
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
                repository.clearEverythingCompletely()
                _userMessage.value = "تم حذف جميع البيانات والمخزون والبدء من جديد بحساب فارغ"
            }
            onSuccess()
        }
    }
}
