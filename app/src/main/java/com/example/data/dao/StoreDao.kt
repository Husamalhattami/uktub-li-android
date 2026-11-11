package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    // --- Products / Inventory ---
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductItem>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductItem): Long

    @Update
    suspend fun updateProduct(product: ProductItem)

    @Query("UPDATE products SET quantity = :newQty, lastSoldTimestamp = :soldTime WHERE id = :productId")
    suspend fun updateStock(productId: Long, newQty: Int, soldTime: Long)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    // --- Sales ---
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM sales WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Long): SaleTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleTransaction): Long

    @Update
    suspend fun updateSale(sale: SaleTransaction)

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSale(id: Long)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    // --- Purchases ---
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseRecord>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: Long): PurchaseRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseRecord): Long

    @Update
    suspend fun updatePurchase(purchase: PurchaseRecord)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()

    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): ExpenseRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecord): Long

    @Update
    suspend fun updateExpense(expense: ExpenseRecord)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    // --- Employees ---
    @Query("SELECT * FROM employees WHERE active = 1 ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Long): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteEmployee(id: Long)

    @Query("DELETE FROM employees")
    suspend fun deleteAllEmployees()

    // --- Employee Transactions ---
    @Query("SELECT * FROM employee_transactions ORDER BY timestamp DESC")
    fun getAllEmployeeTransactions(): Flow<List<EmployeeTransaction>>

    @Query("SELECT * FROM employee_transactions WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getTransactionsForEmployee(employeeId: Long): Flow<List<EmployeeTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployeeTransaction(transaction: EmployeeTransaction): Long

    @Query("SELECT * FROM employee_transactions WHERE id = :id")
    suspend fun getEmployeeTransactionById(id: Long): EmployeeTransaction?

    @Update
    suspend fun updateEmployeeTransaction(transaction: EmployeeTransaction)

    @Query("DELETE FROM employee_transactions WHERE id = :id")
    suspend fun deleteEmployeeTransaction(id: Long)

    @Query("DELETE FROM employee_transactions")
    suspend fun deleteAllEmployeeTransactions()

    // --- Debts ---
    @Query("SELECT * FROM debts ORDER BY isSettled ASC, createdAt DESC")
    fun getAllDebts(): Flow<List<DebtRecord>>

    @Query("SELECT * FROM debts WHERE type = :type ORDER BY isSettled ASC, createdAt DESC")
    fun getDebtsByType(type: String): Flow<List<DebtRecord>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Long): DebtRecord?

    @Query("SELECT * FROM debts WHERE referenceSaleId = :saleId LIMIT 1")
    suspend fun getDebtBySaleId(saleId: Long): DebtRecord?

    @Query("SELECT * FROM debts WHERE referencePurchaseId = :purchaseId LIMIT 1")
    suspend fun getDebtByPurchaseId(purchaseId: Long): DebtRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtRecord): Long

    @Update
    suspend fun updateDebt(debt: DebtRecord)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    // --- Debt Payments ---
    @Query("SELECT * FROM debt_payments ORDER BY timestamp DESC")
    fun getAllDebtPayments(): Flow<List<DebtPayment>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY timestamp DESC")
    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPayment): Long

    @Query("DELETE FROM debt_payments")
    suspend fun deleteAllDebtPayments()

    // --- Cash Drawer Shifts ---
    @Query("SELECT * FROM shifts WHERE isOpen = 1 ORDER BY id DESC LIMIT 1")
    fun getActiveShift(): Flow<CashDrawerShift?>

    @Query("SELECT * FROM shifts ORDER BY id DESC")
    fun getAllShifts(): Flow<List<CashDrawerShift>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: CashDrawerShift): Long

    @Update
    suspend fun updateShift(shift: CashDrawerShift)

    @Query("DELETE FROM shifts")
    suspend fun deleteAllShifts()

    // --- Cash Movements ---
    @Query("SELECT * FROM cash_movements ORDER BY timestamp DESC")
    fun getAllCashMovements(): Flow<List<CashDrawerMovement>>

    @Query("SELECT * FROM cash_movements WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getMovementsForShift(shiftId: Long): Flow<List<CashDrawerMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashDrawerMovement): Long

    @Query("DELETE FROM cash_movements WHERE referenceSaleId = :saleId")
    suspend fun deleteCashMovementsForSale(saleId: Long)

    @Query("DELETE FROM cash_movements WHERE referencePurchaseId = :purchaseId")
    suspend fun deleteCashMovementsForPurchase(purchaseId: Long)

    @Query("DELETE FROM cash_movements WHERE referenceExpenseId = :expenseId")
    suspend fun deleteCashMovementsForExpense(expenseId: Long)

    @Query("DELETE FROM cash_movements WHERE referenceEmployeeTxId = :txId")
    suspend fun deleteCashMovementsForEmployeeTx(txId: Long)

    @Query("DELETE FROM cash_movements")
    suspend fun deleteAllCashMovements()

    // --- Exchange Accounts & Wallets ---
    @Query("SELECT * FROM exchange_accounts ORDER BY balance DESC, name ASC")
    fun getAllExchangeAccounts(): Flow<List<ExchangeAccount>>

    @Query("SELECT * FROM exchange_accounts WHERE id = :id")
    suspend fun getExchangeAccountById(id: Long): ExchangeAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeAccount(account: ExchangeAccount): Long

    @Update
    suspend fun updateExchangeAccount(account: ExchangeAccount)

    @Query("UPDATE exchange_accounts SET balance = balance + :amountDelta WHERE id = :id")
    suspend fun updateExchangeBalance(id: Long, amountDelta: Double)

    @Query("UPDATE exchange_accounts SET balance = :newBalance WHERE id = :id")
    suspend fun setExchangeBalance(id: Long, newBalance: Double)

    @Query("DELETE FROM exchange_accounts WHERE id = :id")
    suspend fun deleteExchangeAccount(id: Long)

    @Query("DELETE FROM exchange_accounts")
    suspend fun deleteAllExchangeAccounts()

    // --- Exchange Transfers ---
    @Query("SELECT * FROM exchange_transfers ORDER BY timestamp DESC")
    fun getAllExchangeTransfers(): Flow<List<ExchangeTransfer>>

    @Query("SELECT * FROM exchange_transfers WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransfersForAccount(accountId: Long): Flow<List<ExchangeTransfer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeTransfer(transfer: ExchangeTransfer): Long

    @Query("DELETE FROM exchange_transfers WHERE id = :id")
    suspend fun deleteExchangeTransfer(id: Long)

    @Query("DELETE FROM exchange_transfers")
    suspend fun deleteAllExchangeTransfers()

    // --- Parties (Customers & Suppliers) ---
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<PartyAccount>>

    @Query("SELECT * FROM parties WHERE type = :type ORDER BY name ASC")
    fun getPartiesByType(type: String): Flow<List<PartyAccount>>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getPartyById(id: Long): PartyAccount?

    @Query("SELECT * FROM parties WHERE name = :name LIMIT 1")
    suspend fun getPartyByName(name: String): PartyAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyAccount): Long

    @Update
    suspend fun updateParty(party: PartyAccount)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteParty(id: Long)

    @Query("DELETE FROM parties")
    suspend fun deleteAllParties()

    @Query("SELECT * FROM debts WHERE partyName = :partyName ORDER BY createdAt DESC")
    fun getDebtsByPartyName(partyName: String): Flow<List<DebtRecord>>

    @Query("SELECT * FROM debt_payments WHERE partyName = :partyName ORDER BY timestamp DESC")
    fun getPaymentsByPartyName(partyName: String): Flow<List<DebtPayment>>

    @Query("SELECT * FROM sales WHERE customerName = :customerName ORDER BY timestamp DESC")
    fun getSalesByCustomerName(customerName: String): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM purchases WHERE supplierName = :supplierName ORDER BY timestamp DESC")
    fun getPurchasesBySupplierName(supplierName: String): Flow<List<PurchaseRecord>>

    // --- Safe Stock Operations ---
    @Query("UPDATE products SET quantity = quantity - :qtySold, lastSoldTimestamp = :soldTime WHERE id = :productId AND quantity >= :qtySold")
    suspend fun decrementStockSafe(productId: Long, qtySold: Int, soldTime: Long): Int

    @Query("UPDATE products SET quantity = quantity + :qtyToAdd WHERE id = :productId")
    suspend fun incrementStock(productId: Long, qtyToAdd: Int)

    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogRecord): Long

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAllAuditLogs()

    // --- Store Settings ---
    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    fun getStoreSettings(): Flow<StoreSettings?>

    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    suspend fun getStoreSettingsDirect(): StoreSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoreSettings(settings: StoreSettings)

    @Update
    suspend fun updateStoreSettings(settings: StoreSettings)
}

