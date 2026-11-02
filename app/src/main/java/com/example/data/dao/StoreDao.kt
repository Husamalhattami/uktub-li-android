package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    // --- Sales ---
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM sales WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<SaleTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleTransaction): Long

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSale(id: Long)

    // --- Purchases ---
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseRecord): Long

    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<ExpenseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecord): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    // --- Employees ---
    @Query("SELECT * FROM employees WHERE active = 1 ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Long): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    // --- Employee Transactions ---
    @Query("SELECT * FROM employee_transactions ORDER BY timestamp DESC")
    fun getAllEmployeeTransactions(): Flow<List<EmployeeTransaction>>

    @Query("SELECT * FROM employee_transactions WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getTransactionsForEmployee(employeeId: Long): Flow<List<EmployeeTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployeeTransaction(transaction: EmployeeTransaction): Long

    // --- Debts ---
    @Query("SELECT * FROM debts ORDER BY isSettled ASC, createdAt DESC")
    fun getAllDebts(): Flow<List<DebtRecord>>

    @Query("SELECT * FROM debts WHERE type = :type ORDER BY isSettled ASC, createdAt DESC")
    fun getDebtsByType(type: String): Flow<List<DebtRecord>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Long): DebtRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtRecord): Long

    @Update
    suspend fun updateDebt(debt: DebtRecord)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    // --- Debt Payments ---
    @Query("SELECT * FROM debt_payments ORDER BY timestamp DESC")
    fun getAllDebtPayments(): Flow<List<DebtPayment>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY timestamp DESC")
    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPayment): Long

    // --- Cash Drawer Shifts ---
    @Query("SELECT * FROM shifts WHERE isOpen = 1 ORDER BY id DESC LIMIT 1")
    fun getActiveShift(): Flow<CashDrawerShift?>

    @Query("SELECT * FROM shifts ORDER BY id DESC")
    fun getAllShifts(): Flow<List<CashDrawerShift>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: CashDrawerShift): Long

    @Update
    suspend fun updateShift(shift: CashDrawerShift)

    // --- Cash Movements ---
    @Query("SELECT * FROM cash_movements ORDER BY timestamp DESC")
    fun getAllCashMovements(): Flow<List<CashDrawerMovement>>

    @Query("SELECT * FROM cash_movements WHERE shiftId = :shiftId ORDER BY timestamp DESC")
    fun getMovementsForShift(shiftId: Long): Flow<List<CashDrawerMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashDrawerMovement): Long
}
