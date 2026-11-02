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
    CASH_DRAWER
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
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
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
                unit = unit
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
                colorDetails = colorDetails
            )
            _userMessage.value = "تم تسجيل شراء البضاعة وإضافتها إلى المخزون!"
            onSuccess()
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
                paidFromCash = paidFromCash
            )
            _userMessage.value = "تم تسجيل المصروف بنجاح"
            onSuccess()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            _userMessage.value = "تم حذف المصروف"
        }
    }

    fun saveEmployee(employee: Employee, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (employee.name.isBlank()) {
                _userMessage.value = "يرجى إدخال اسم العامل"
                return@launch
            }
            repository.saveEmployee(employee)
            _userMessage.value = "تم حفظ بيانات العامل"
            onSuccess()
        }
    }

    fun recordEmployeeTransaction(
        employeeId: Long,
        employeeName: String,
        type: String,
        amount: Double,
        notes: String,
        paidFromCashDrawer: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _userMessage.value = "يرجى إدخال المبلغ"
                return@launch
            }
            repository.recordEmployeeTransaction(
                employeeId = employeeId,
                employeeName = employeeName,
                type = type,
                amount = amount,
                notes = notes,
                paidFromCashDrawer = paidFromCashDrawer
            )
            _userMessage.value = "تم تسجيل $type للعامل $employeeName بنجاح"
            onSuccess()
        }
    }

    fun recordDebtPayment(
        debt: DebtRecord,
        amount: Double,
        notes: String,
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
            repository.recordDebtPayment(debt, amount, notes)
            _userMessage.value = "تم تسجيل الدفعة بنجاح وتحديث الصندوق والدين"
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
}
