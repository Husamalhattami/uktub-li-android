package com.example.data.repository

import com.example.data.dao.StoreDao
import com.example.data.model.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.PinSecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

data class StoreBackupSnapshot(
    val products: List<ProductItem>,
    val sales: List<SaleTransaction>,
    val purchases: List<PurchaseRecord>,
    val expenses: List<ExpenseRecord>,
    val employees: List<Employee>,
    val employeeTransactions: List<EmployeeTransaction>,
    val parties: List<PartyAccount>,
    val debts: List<DebtRecord>,
    val debtPayments: List<DebtPayment>,
    val shifts: List<CashDrawerShift>,
    val cashMovements: List<CashDrawerMovement>,
    val exchangeAccounts: List<ExchangeAccount>,
    val exchangeTransfers: List<ExchangeTransfer>,
    val auditLogs: List<AuditLogRecord>,
    val storeSettings: StoreSettings?,
    val configItems: List<StoreConfigItem>,
    val returnRecords: List<ReturnRecord> = emptyList()
)

sealed class PinVerificationResult {
    data object Success : PinVerificationResult()
    data object NotConfigured : PinVerificationResult()
    data class Invalid(val remainingAttempts: Int) : PinVerificationResult()
    data class Locked(val remainingSeconds: Long) : PinVerificationResult()
}

class StoreRepository(
    private val dao: StoreDao,
    private val db: AppDatabase? = null
) {

    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

    val allProducts: Flow<List<ProductItem>> = dao.getAllProducts()
    val allSales: Flow<List<SaleTransaction>> = dao.getAllSales()
    val allPurchases: Flow<List<PurchaseRecord>> = dao.getAllPurchases()
    val allExpenses: Flow<List<ExpenseRecord>> = dao.getAllExpenses()
    val allEmployees: Flow<List<Employee>> = dao.getAllEmployees()
    val allEmployeesIncludingArchived: Flow<List<Employee>> = dao.getAllEmployeesIncludingArchived()
    val allEmployeeTransactions: Flow<List<EmployeeTransaction>> = dao.getAllEmployeeTransactions()
    val allDebts: Flow<List<DebtRecord>> = dao.getAllDebts()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()
    val activeShift: Flow<CashDrawerShift?> = dao.getActiveShift()
    val allShifts: Flow<List<CashDrawerShift>> = dao.getAllShifts()
    val allCashMovements: Flow<List<CashDrawerMovement>> = dao.getAllCashMovements()
    val allExchangeAccounts: Flow<List<ExchangeAccount>> = dao.getAllExchangeAccounts()
    val allExchangeAccountsIncludingArchived: Flow<List<ExchangeAccount>> = dao.getAllExchangeAccountsIncludingArchived()
    val allExchangeTransfers: Flow<List<ExchangeTransfer>> = dao.getAllExchangeTransfers()
    val allParties: Flow<List<PartyAccount>> = dao.getAllParties()
    val allPartiesIncludingArchived: Flow<List<PartyAccount>> = dao.getAllPartiesIncludingArchived()
    val allAuditLogs: Flow<List<AuditLogRecord>> = dao.getAllAuditLogs()
    val storeSettings: Flow<StoreSettings?> = dao.getStoreSettings()
    val allConfigItems: Flow<List<StoreConfigItem>> = dao.getAllConfigItems()
    val allReturns: Flow<List<ReturnRecord>> = dao.getAllReturns()

    suspend fun getStoreSettingsDirect(): StoreSettings? = dao.getStoreSettingsDirect()

    suspend fun logAudit(action: String, actor: String = "المدير", details: String) {
        dao.insertAuditLog(AuditLogRecord(action = action, actor = actor, details = details, timestamp = System.currentTimeMillis()))
    }

    suspend fun saveStoreSettings(settings: StoreSettings) {
        val existing = dao.getStoreSettingsDirect()
        val preserved = if (settings.managerPinHash.isBlank() && existing != null && existing.managerPinHash.isNotBlank()) {
            settings.copy(
                managerPin = "",
                managerPinHash = existing.managerPinHash,
                managerPinSalt = existing.managerPinSalt,
                mustChangePin = existing.mustChangePin,
                pinFailedAttempts = existing.pinFailedAttempts,
                pinLockUntilTimestamp = existing.pinLockUntilTimestamp
            )
        } else {
            settings.copy(managerPin = "")
        }
        if (existing == null) {
            dao.insertStoreSettings(preserved)
        } else {
            dao.updateStoreSettings(preserved)
        }
        logAudit("تحديث الإعدادات", "المدير", "تم تحديث بيانات المحل والشعار ورقم الهاتف وإعدادات الفاتورة")
    }

    suspend fun verifyManagerPin(pin: String): PinVerificationResult {
        val settings = dao.getStoreSettingsDirect() ?: return PinVerificationResult.NotConfigured
        if (!PinSecurityManager.isPinConfigured(settings)) {
            return PinVerificationResult.NotConfigured
        }
        val now = System.currentTimeMillis()
        if (settings.pinLockUntilTimestamp > now) {
            val remSec = ((settings.pinLockUntilTimestamp - now) / 1000L).coerceAtLeast(1L)
            return PinVerificationResult.Locked(remSec)
        }
        if (PinSecurityManager.verifyPinAgainstSettings(pin, settings)) {
            if (settings.pinFailedAttempts != 0 || settings.pinLockUntilTimestamp != 0L) {
                dao.updateStoreSettings(settings.copy(pinFailedAttempts = 0, pinLockUntilTimestamp = 0L))
            }
            return PinVerificationResult.Success
        }
        val nextAttempts = settings.pinFailedAttempts + 1
        return if (nextAttempts >= PinSecurityManager.MAX_FAILED_ATTEMPTS) {
            val lockUntil = now + PinSecurityManager.LOCKOUT_DURATION_MS
            dao.updateStoreSettings(settings.copy(pinFailedAttempts = 0, pinLockUntilTimestamp = lockUntil))
            PinVerificationResult.Locked((PinSecurityManager.LOCKOUT_DURATION_MS / 1000L).coerceAtLeast(1L))
        } else {
            dao.updateStoreSettings(settings.copy(pinFailedAttempts = nextAttempts))
            PinVerificationResult.Invalid(PinSecurityManager.MAX_FAILED_ATTEMPTS - nextAttempts)
        }
    }

    suspend fun updateManagerPin(newPin: String, currentPin: String? = null) {
        val currentSettings = dao.getStoreSettingsDirect() ?: StoreSettings()
        if (PinSecurityManager.isPinConfigured(currentSettings)) {
            if (currentPin == null) {
                throw SecurityException("يجب إدخال رمز المدير الحالي أولاً.")
            }
            when (val check = verifyManagerPin(currentPin)) {
                is PinVerificationResult.Success -> Unit
                is PinVerificationResult.Locked -> throw SecurityException("تم قفل المحاولات مؤقتاً لمدة ${check.remainingSeconds} ثانية.")
                is PinVerificationResult.Invalid -> throw SecurityException("رمز المدير الحالي غير صحيح! (المحاولات المتبقية: ${check.remainingAttempts})")
                PinVerificationResult.NotConfigured -> Unit
            }
        }
        val updated = PinSecurityManager.withNewPin(currentSettings, newPin)
        if (dao.getStoreSettingsDirect() == null) {
            dao.insertStoreSettings(updated)
        } else {
            dao.updateStoreSettings(updated)
        }
        logAudit("تغيير رمز الأمان", "المدير", "تم تحديث رمز PIN للمدير وتشفيره بنجاح")
    }

    private suspend fun requireSufficientCash(requiredOutflow: Double) {
        if (requiredOutflow <= 0.0001) return
        val availableCash = dao.getAvailableCashBalance()
        if (availableCash + 0.001 < requiredOutflow) {
            throw IllegalStateException(
                "رصيد الصندوق النقدي غير كافٍ! المتوفر: ${FormatUtils.formatCurrency(availableCash)}، والمطلوب: ${FormatUtils.formatCurrency(requiredOutflow)}."
            )
        }
    }

    private suspend fun requireSufficientExchange(accountId: Long, requiredOutflow: Double) {
        if (requiredOutflow <= 0.0001) return
        val acc = dao.getExchangeAccountById(accountId)
            ?: throw IllegalArgumentException("حساب الصراف أو المحفظة المحدد غير موجود!")
        if (acc.balance + 0.001 < requiredOutflow) {
            throw IllegalStateException(
                "رصيد حساب (${acc.name}) غير كافٍ! المتوفر: ${FormatUtils.formatCurrency(acc.balance)}، والمطلوب: ${FormatUtils.formatCurrency(requiredOutflow)}."
            )
        }
    }

    private suspend fun reverseTransfersList(
        transfers: List<ExchangeTransfer>,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        for (orig in transfers) {
            if (orig.isReversed || orig.isReversalEntry) continue
            if (orig.isCredit) {
                requireSufficientExchange(orig.accountId, orig.amount)
            }
            val balanceDelta = if (orig.isCredit) -orig.amount else orig.amount
            dao.updateExchangeBalance(orig.accountId, balanceDelta)
            val reversalId = dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = orig.accountId,
                    accountName = orig.accountName,
                    type = "عكس: ${orig.type}",
                    amount = orig.amount,
                    isCredit = !orig.isCredit,
                    title = "إلغاء/عكس: ${orig.title}",
                    referenceNumber = orig.referenceNumber,
                    notes = reason,
                    timestamp = timestamp,
                    referenceType = orig.referenceType,
                    referenceId = orig.referenceId,
                    isReversed = false,
                    reversedByTransferId = orig.id,
                    isReversalEntry = true
                )
            )
            dao.markTransferReversed(orig.id, reversalId)
        }
    }

    private suspend fun reverseLinkedTransfersOrFallback(
        refType: String,
        refIds: List<Long>,
        fallbackRefNumber: String = "",
        fallbackAccountId: Long = 0L,
        fallbackAmount: Double = 0.0,
        fallbackWasCredit: Boolean = false,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val linked = if (refIds.isNotEmpty()) {
            dao.getActiveTransfersByReferences(refType, refIds).toMutableList()
        } else {
            mutableListOf()
        }
        if (linked.isEmpty() && fallbackRefNumber.isNotBlank()) {
            linked.addAll(dao.getActiveTransfersByReferenceNumber(fallbackRefNumber))
        }
        if (linked.isNotEmpty()) {
            reverseTransfersList(linked, reason, timestamp)
        } else if (fallbackAmount > 0.0001 && fallbackAccountId > 0L) {
            if (fallbackWasCredit) {
                requireSufficientExchange(fallbackAccountId, fallbackAmount)
            }
            val delta = if (fallbackWasCredit) -fallbackAmount else fallbackAmount
            dao.updateExchangeBalance(fallbackAccountId, delta)
        }
    }

    private fun nextSequentialCode(existingNumbers: List<String>, prefix: String): String {
        val existingSet = existingNumbers.map { it.trim().uppercase() }.toSet()
        val regex = Regex("^${Regex.escape(prefix.uppercase())}-(\\d+)$")
        val maxSuffix = existingSet.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0
        var candidateNum = maxOf(maxSuffix + 1, existingSet.size + 1)
        while (true) {
            val candidate = "$prefix-${candidateNum.toString().padStart(4, '0')}"
            if (candidate.uppercase() !in existingSet) return candidate
            candidateNum++
        }
    }

    private suspend fun resolveSaleInvoiceNumber(input: String, excludeInvoiceNumber: String = ""): String {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            val conflictCount = if (excludeInvoiceNumber.isBlank()) {
                dao.countSalesByInvoiceNumber(trimmed)
            } else {
                dao.countOtherSalesByInvoiceNumber(trimmed, excludeInvoiceNumber)
            }
            if (conflictCount > 0) {
                throw IllegalArgumentException("رقم فاتورة البيع ($trimmed) مستخدم مسبقاً! يرجى إدخال رقم آخر أو تركه فارغاً للتوليد التلقائي.")
            }
            return trimmed
        }
        return nextSequentialCode(dao.getAllSaleInvoiceNumbers(), "INV")
    }

    private suspend fun resolvePurchaseInvoiceNumber(input: String, excludeInvoiceNumber: String = ""): String {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            val conflictCount = if (excludeInvoiceNumber.isBlank()) {
                dao.countPurchasesByInvoiceNumber(trimmed)
            } else {
                dao.countOtherPurchasesByInvoiceNumber(trimmed, excludeInvoiceNumber)
            }
            if (conflictCount > 0) {
                throw IllegalArgumentException("رقم فاتورة الشراء ($trimmed) مستخدم مسبقاً! يرجى إدخال رقم آخر أو تركه فارغاً للتوليد التلقائي.")
            }
            return trimmed
        }
        return nextSequentialCode(dao.getAllPurchaseInvoiceNumbers(), "PUR")
    }

    private suspend fun resolveReturnNumber(input: String, prefix: String): String {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            if (dao.countReturnsByReturnNumber(trimmed) > 0) {
                throw IllegalArgumentException("رقم مستند المرتجع ($trimmed) مستخدم مسبقاً! يرجى إدخال رقم آخر أو تركه فارغاً للتوليد التلقائي.")
            }
            return trimmed
        }
        return nextSequentialCode(dao.getAllReturnNumbers(), prefix)
    }

    suspend fun addConfigItem(type: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val existing = dao.getConfigItemByTypeAndName(type, trimmed)
        if (existing != null) return false
        dao.insertConfigItem(StoreConfigItem(type = type, name = trimmed))
        return true
    }

    suspend fun updateConfigItem(item: StoreConfigItem, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val conflict = dao.getConfigItemByTypeAndName(item.type, trimmed)
        if (conflict != null && conflict.id != item.id) return false

        val oldName = item.name
        runInTransaction {
            dao.updateConfigItem(item.copy(name = trimmed))
            if (oldName != trimmed) {
                when (item.type) {
                    StoreConfigTypes.PRODUCT_CATEGORY -> {
                        dao.renameProductCategory(oldName, trimmed)
                        dao.renameSaleCategory(oldName, trimmed)
                        dao.renamePurchaseCategory(oldName, trimmed)
                    }
                    StoreConfigTypes.PRODUCT_SIZE -> {
                        dao.renameProductSize(oldName, trimmed)
                        dao.renameSaleSize(oldName, trimmed)
                        dao.renamePurchaseSize(oldName, trimmed)
                    }
                    StoreConfigTypes.EXPENSE_CATEGORY -> {
                        dao.renameExpenseCategory(oldName, trimmed)
                    }
                    StoreConfigTypes.ACCOUNT_TYPE -> {
                        dao.renameExchangeAccountType(oldName, trimmed)
                    }
                    StoreConfigTypes.EMPLOYEE_ROLE -> {
                        dao.renameEmployeeRole(oldName, trimmed)
                    }
                }
            }
            logAudit("تعديل قائمة النظام", "المدير", "تعديل '${oldName}' إلى '${trimmed}'")
        }
        return true
    }

    suspend fun deleteConfigItem(id: Long) {
        dao.deleteConfigItem(id)
    }

    suspend fun getConfigItemsCount(): Int = dao.getConfigItemsCount()

    suspend fun seedRecommendedDictionaries(includeSampleCategories: Boolean = false) {
        runInTransaction {
            val defaultSizes = listOf("كبير", "شبابي", "وسط", "صغير", "حر")
            val defaultExpenseCategories = listOf(
                "صرفة يومية لعامل",
                "إيجار المحل",
                "الكهرباء",
                "الماء",
                "الإنترنت",
                "النقل والشحن",
                "التغليف والأكياس",
                "التصليح والصيانة",
                "الضيافة والشاي",
                "الدعاية والإعلان",
                "مصروف متفرقات"
            )
            val defaultAccountTypes = listOf("صراف", "محفظة", "حساب بنكي", "شركة حوالات")
            val defaultColors = listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "بني", "زيتي", "سكري")
            val defaultRoles = listOf("بائع", "كاشير", "خياط", "محاسب", "مدير فرع")

            defaultSizes.forEach { addConfigItem(StoreConfigTypes.PRODUCT_SIZE, it) }
            defaultExpenseCategories.forEach { addConfigItem(StoreConfigTypes.EXPENSE_CATEGORY, it) }
            defaultAccountTypes.forEach { addConfigItem(StoreConfigTypes.ACCOUNT_TYPE, it) }
            defaultColors.forEach { addConfigItem(StoreConfigTypes.PRODUCT_COLOR, it) }
            defaultRoles.forEach { addConfigItem(StoreConfigTypes.EMPLOYEE_ROLE, it) }

            if (includeSampleCategories) {
                val sampleCategories = listOf("أكوات", "دجلات", "ثياب", "بشت", "جاكيتات", "شمغ")
                sampleCategories.forEach { addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, it) }
            }
        }
    }

    suspend fun replaceConfigItemsForSetup(
        categories: List<String>,
        sizes: List<String>,
        expenseCategories: List<String>,
        accountTypes: List<String>,
        colors: List<String>,
        roles: List<String>
    ) {
        runInTransaction {
            dao.deleteAllConfigItems()
            categories.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.PRODUCT_CATEGORY, name = it))
            }
            sizes.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.PRODUCT_SIZE, name = it))
            }
            expenseCategories.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.EXPENSE_CATEGORY, name = it))
            }
            accountTypes.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.ACCOUNT_TYPE, name = it))
            }
            colors.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.PRODUCT_COLOR, name = it))
            }
            roles.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
                dao.insertConfigItem(StoreConfigItem(type = StoreConfigTypes.EMPLOYEE_ROLE, name = it))
            }
        }
    }

    suspend fun seedInitialDataIfEmpty() {
        // التطبيق يبدأ فارغاً بالكامل بدون أي منتجات أو أصناف أو حركات افتراضية
    }

    // --- Product CRUD ---
    suspend fun saveProduct(product: ProductItem): Long {
        return if (product.id == 0L) {
            dao.insertProduct(product)
        } else {
            dao.updateProduct(product)
            product.id
        }
    }

    suspend fun deleteProduct(id: Long) = dao.deleteProduct(id)

    // --- Multi-Item Cart Sale Execution (Single Transaction & Unified Invoice) ---
    suspend fun executeCartSale(
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
        timestamp: Long = System.currentTimeMillis()
    ): String {
        if (items.isEmpty()) {
            throw IllegalArgumentException("سلة المبيعات فارغة، يرجى إضافة منتج واحد على الأقل.")
        }
        for (item in items) {
            if (item.quantity <= 0) {
                throw IllegalArgumentException("يرجى إدخال كمية بيع صحيحة للمنتج (${item.product.modelName}).")
            }
            if (item.sellPrice <= 0) {
                throw IllegalArgumentException("سعر بيع المنتج (${item.product.modelName}) يجب أن يكون أكبر من الصفر.")
            }
        }
        if (discount < 0) {
            throw IllegalArgumentException("لا يمكن إدخال خصم بقيمة سالبة.")
        }

        val subtotal = items.sumOf { it.sellPrice * it.quantity }
        if (discount >= subtotal) {
            throw IllegalArgumentException("قيمة الخصم (${FormatUtils.formatCurrency(discount)}) لا يمكن أن تتجاوز أو تعادل إجمالي الفاتورة (${FormatUtils.formatCurrency(subtotal)}).")
        }
        val netTotal = subtotal - discount

        // Determine payment portions
        val actualCash: Double
        val actualTransfer: Double
        val actualDebt: Double

        when (paymentType) {
            PaymentMethods.CASH, "نقداً", "نقداً (كاش)" -> {
                actualCash = netTotal
                actualTransfer = 0.0
                actualDebt = 0.0
            }
            PaymentMethods.TRANSFER, "تحويل عبر الصراف", "تحويل صرافة" -> {
                if (exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المحول إليها قبل تأكيد البيع.")
                }
                actualCash = 0.0
                actualTransfer = netTotal
                actualDebt = 0.0
            }
            PaymentMethods.CREDIT, "آجل (دين)", "بالدين" -> {
                if (customerName.trim().isBlank()) {
                    throw IllegalArgumentException("يجب كتابة أو اختيار اسم العميل لتسجيل عملية البيع بالدين.")
                }
                actualCash = 0.0
                actualTransfer = 0.0
                actualDebt = netTotal
            }
            PaymentMethods.SPLIT, "نصف كاش ونصف حوالة" -> {
                if (exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
                }
                if (cashPart > 0.0 || transferPart > 0.0) {
                    if (cashPart < 0 || transferPart < 0) {
                        throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل الدفع.")
                    }
                    if (kotlin.math.abs((cashPart + transferPart) - netTotal) > 0.01) {
                        throw IllegalArgumentException("مجموع الدفع النقدي ($cashPart) والتحويل ($transferPart) يجب أن يساوي تماماً صافي الفاتورة ($netTotal).")
                    }
                    actualCash = cashPart
                    actualTransfer = transferPart
                } else {
                    val half = netTotal / 2.0
                    actualCash = half
                    actualTransfer = netTotal - half
                }
                actualDebt = 0.0
            }
            PaymentMethods.CUSTOM_SPLIT, "مختلط (كاش + حوالة + آجل)" -> {
                if (cashPart < 0 || transferPart < 0 || debtPart < 0) {
                    throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل الدفع.")
                }
                if (transferPart > 0 && exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة ($transferPart ريال).")
                }
                if (debtPart > 0 && customerName.trim().isBlank()) {
                    throw IllegalArgumentException("يجب كتابة أو اختيار اسم العميل لتسجيل المبلغ الآجل ($debtPart ريال).")
                }
                val sumParts = cashPart + transferPart + debtPart
                if (kotlin.math.abs(sumParts - netTotal) > 0.01) {
                    throw IllegalArgumentException("مجموع المبالغ ($sumParts ريال) يجب أن يساوي تماماً صافي الفاتورة ($netTotal ريال).")
                }
                actualCash = cashPart
                actualTransfer = transferPart
                actualDebt = debtPart
            }
            else -> {
                actualCash = netTotal
                actualTransfer = 0.0
                actualDebt = 0.0
            }
        }

        return runInTransaction {
            val now = timestamp
            val invoiceNumber = resolveSaleInvoiceNumber(customInvoiceNumber)
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val finalCustomer = customerName.trim().ifBlank { if (actualDebt > 0) "عميل آجل" else "زبون نقدي" }

            // Phase 1: Verify all products have enough stock BEFORE making any changes
            val freshProducts = mutableListOf<Pair<CartSaleItem, ProductItem>>()
            for (item in items) {
                val fresh = dao.getProductById(item.product.id)
                    ?: throw IllegalStateException("المنتج (${item.product.modelName}) غير متوفر في قاعدة البيانات!")
                val multiplier = ClothingUnits.getMultiplier(item.unit)
                val totalPieces = item.quantity * multiplier
                if (fresh.quantity < totalPieces) {
                    throw IllegalStateException("الكمية المطلوبة من (${fresh.modelName}) غير متوفرة! المطلوب: $totalPieces حبة، المتوفر في المخزن: ${fresh.quantity} حبة فقط.")
                }
                freshProducts.add(item to fresh)
            }

            // Phase 2: Safely decrement stock for each item
            for ((item, _) in freshProducts) {
                val multiplier = ClothingUnits.getMultiplier(item.unit)
                val totalPieces = item.quantity * multiplier
                val updatedRows = dao.decrementStockSafe(item.product.id, totalPieces, now)
                if (updatedRows == 0) {
                    throw IllegalStateException("تعذر خصم كمية (${item.product.modelName}) من المخزون بسبب عدم كفاية الرصيد أو تزامن عملية أخرى!")
                }
            }

            // Phase 3: Insert SaleTransaction for each item linked by unified invoiceNumber
            var firstSaleId = 0L
            for ((item, fresh) in freshProducts) {
                val multiplier = ClothingUnits.getMultiplier(item.unit)
                val totalPieces = item.quantity * multiplier
                val itemGross = item.sellPrice * item.quantity
                val itemDiscount = if (subtotal > 0) discount * (itemGross / subtotal) else 0.0
                val itemNet = itemGross - itemDiscount
                val cost = fresh.buyPrice * totalPieces
                val profit = itemNet - cost

                val itemCash = if (netTotal > 0) actualCash * (itemNet / netTotal) else 0.0
                val itemTransfer = if (netTotal > 0) actualTransfer * (itemNet / netTotal) else 0.0
                val itemDebt = if (netTotal > 0) actualDebt * (itemNet / netTotal) else 0.0

                val saleRecord = SaleTransaction(
                    productId = fresh.id,
                    productName = fresh.modelName,
                    category = fresh.category,
                    color = fresh.color,
                    size = fresh.size,
                    quantity = totalPieces,
                    unitBuyPrice = fresh.buyPrice,
                    unitSellPrice = item.sellPrice / multiplier,
                    discount = itemDiscount,
                    totalAmount = itemNet,
                    profit = profit,
                    customerName = finalCustomer,
                    customerPhone = customerPhone,
                    paymentType = paymentType,
                    cashAmount = itemCash,
                    transferAmount = itemTransfer,
                    debtAmount = itemDebt,
                    exchangeAccountId = exchangeAccount?.id ?: 0L,
                    exchangeAccountName = exchangeAccount?.name ?: "",
                    transferRef = transferRef,
                    employeeName = employee?.name ?: "صاحب المحل",
                    employeeId = employee?.id ?: 0L,
                    timestamp = now,
                    shiftId = shiftId,
                    unit = item.unit,
                    invoiceNumber = invoiceNumber
                )
                val sId = dao.insertSale(saleRecord)
                if (firstSaleId == 0L) firstSaleId = sId
            }

            val itemsCountText = "${items.size} أصناف"

            // Phase 4: Single Cash Drawer Movement
            if (actualCash > 0) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "SALE_CASH",
                        amount = actualCash,
                        isIncome = true,
                        title = "فاتورة بيع #$invoiceNumber ($itemsCountText)",
                        details = "العميل: $finalCustomer${if (actualTransfer > 0 || actualDebt > 0) " (جزء نقدي)" else ""}",
                        timestamp = now,
                        referenceSaleId = firstSaleId
                    )
                )
            }

            // Phase 5: Single Exchange Transfer & Balance Update
            if (actualTransfer > 0 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, actualTransfer)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مبيعات تحويل",
                        amount = actualTransfer,
                        isCredit = true,
                        title = "تحويل فاتورة #$invoiceNumber ($itemsCountText)",
                        referenceNumber = transferRef.ifBlank { invoiceNumber },
                        notes = "فاتورة بيع #${invoiceNumber} | العميل: $finalCustomer",
                        timestamp = now,
                        referenceType = "SALE",
                        referenceId = firstSaleId
                    )
                )
            }

            // Phase 6: Single Debt Record
            if (actualDebt > 0) {
                dao.insertDebt(
                    DebtRecord(
                        type = "CUSTOMER",
                        partyName = finalCustomer,
                        phone = customerPhone,
                        originalAmount = actualDebt,
                        paidAmount = 0.0,
                        remainingAmount = actualDebt,
                        details = "آجل فاتورة بيع #$invoiceNumber ($itemsCountText)",
                        referenceSaleId = firstSaleId,
                        dueDate = now + 86400000L * 15,
                        createdAt = now,
                        isSettled = false
                    )
                )
            }

            // Phase 7: Single Audit Log
            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تسجيل فاتورة بيع",
                    actor = employee?.name ?: "صاحب المحل",
                    details = "فاتورة #$invoiceNumber ($itemsCountText): صافي ${FormatUtils.formatCurrency(netTotal)} ($paymentType)",
                    timestamp = now
                )
            )

            invoiceNumber
        }
    }

    // --- Sale Execution (Cash, Wallet Transfer, Split Half-Cash/Half-Transfer, Debt) ---
    suspend fun executeSale(
        product: ProductItem,
        quantity: Int,
        sellPrice: Double,
        discount: Double,
        customerName: String,
        customerPhone: String,
        paymentType: String,
        employee: Employee?,
        unit: String = ClothingUnits.PIECE,
        cashPart: Double = 0.0,
        transferPart: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        transferRef: String = "",
        timestamp: Long = System.currentTimeMillis(),
        customInvoiceNumber: String = ""
    ): SaleTransaction {
        val multiplier = ClothingUnits.getMultiplier(unit)
        val totalPieces = quantity * multiplier

        // Strict Validation Checks
        if (quantity <= 0) {
            throw IllegalArgumentException("يرجى إدخال كمية بيع صحيحة أكبر من الصفر.")
        }
        if (sellPrice <= 0) {
            throw IllegalArgumentException("لا يمكن بيع البضاعة بسعر صفري أو سالب.")
        }
        if (discount < 0) {
            throw IllegalArgumentException("لا يمكن إدخال خصم بقيمة سالبة.")
        }
        val grossTotal = sellPrice * quantity
        if (discount >= grossTotal) {
            throw IllegalArgumentException("قيمة الخصم ($discount ريال) لا يمكن أن تتجاوز أو تعادل إجمالي البيع ($grossTotal ريال).")
        }
        val totalAmount = grossTotal - discount

        if (paymentType == "آجل (دين)" || paymentType == "بالدين") {
            if (customerName.trim().isBlank()) {
                throw IllegalArgumentException("يجب كتابة أو اختيار اسم العميل لتسجيل عملية البيع بالدين.")
            }
        }
        if (paymentType == "تحويل عبر الصراف" || paymentType == "تحويل صرافة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المحول إليها قبل تأكيد البيع.")
            }
        }
        if (paymentType == "نصف كاش ونصف حوالة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف للدفعة المحولة.")
            }
            if (cashPart < 0 || transferPart < 0) {
                throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل الدفع.")
            }
            if (kotlin.math.abs((cashPart + transferPart) - totalAmount) > 0.01) {
                throw IllegalArgumentException("مجموع الدفع النقدي ($cashPart) والتحويل ($transferPart) يجب أن يعادل إجمالي الفاتورة ($totalAmount ريال).")
            }
        }

        return runInTransaction {
            val freshProduct = dao.getProductById(product.id)
                ?: throw IllegalStateException("الصنف المطلوب بيعه غير موجود في المخزون!")

            if (freshProduct.quantity < totalPieces) {
                throw IllegalStateException("الكمية المطلوبة ($totalPieces حبة) غير متوفرة! المتبقي في المخزن فقط (${freshProduct.quantity} حبة).")
            }

            val now = timestamp
            val invoiceNumber = resolveSaleInvoiceNumber(customInvoiceNumber)
            val updatedRows = dao.decrementStockSafe(product.id, totalPieces, now)
            if (updatedRows == 0) {
                throw IllegalStateException("تعذر خصم الكمية من المخزون بسبب عدم كفاية الرصيد أو حدوث حركة متزامنة!")
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L

            val costOfGoods = freshProduct.buyPrice * totalPieces
            val profit = totalAmount - costOfGoods

            val finalCashAmount = when (paymentType) {
                "نقداً", "نقداً (كاش)" -> totalAmount
                "نصف كاش ونصف حوالة" -> cashPart
                else -> 0.0
            }
            val finalTransferAmount = when (paymentType) {
                "تحويل عبر الصراف", "تحويل صرافة" -> totalAmount
                "نصف كاش ونصف حوالة" -> transferPart
                else -> 0.0
            }
            val finalDebtAmount = if (paymentType == "آجل (دين)" || paymentType == "بالدين") totalAmount else 0.0

            val sale = SaleTransaction(
                productId = product.id,
                productName = product.modelName,
                category = product.category,
                color = product.color,
                size = product.size,
                quantity = totalPieces,
                unitBuyPrice = freshProduct.buyPrice,
                unitSellPrice = sellPrice / multiplier,
                discount = discount,
                totalAmount = totalAmount,
                profit = profit,
                customerName = customerName.ifBlank { "زبون نقدي" },
                customerPhone = customerPhone,
                paymentType = paymentType,
                cashAmount = finalCashAmount,
                transferAmount = finalTransferAmount,
                debtAmount = finalDebtAmount,
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: "",
                transferRef = transferRef,
                employeeName = employee?.name ?: "صاحب المحل",
                employeeId = employee?.id ?: 0L,
                timestamp = now,
                shiftId = shiftId,
                unit = unit,
                invoiceNumber = invoiceNumber
            )
            val saleId = dao.insertSale(sale)

            val unitDesc = if (unit == ClothingUnits.PIECE) "$quantity حبة" else "$quantity $unit ($totalPieces حبة)"

            if (finalCashAmount > 0) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "SALE_CASH",
                        amount = finalCashAmount,
                        isIncome = true,
                        title = "مبيعات نقدية: ${product.modelName} ($unitDesc)",
                        details = "الزبون: ${customerName.ifBlank { "نقدي" }}${if (finalTransferAmount > 0) " (جزء نقدي)" else ""}",
                        timestamp = now,
                        referenceSaleId = saleId
                    )
                )
            }

            if (finalTransferAmount > 0 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, finalTransferAmount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مبيعات تحويل",
                        amount = finalTransferAmount,
                        isCredit = true,
                        title = "مبيعات تحويل: ${product.modelName} ($unitDesc)",
                        referenceNumber = transferRef.ifBlank { invoiceNumber },
                        notes = "فاتورة بيع #$invoiceNumber | الزبون: ${customerName.ifBlank { "تحويل" }}",
                        timestamp = now,
                        referenceType = "SALE",
                        referenceId = saleId
                    )
                )
            }

            if (finalDebtAmount > 0) {
                val debt = DebtRecord(
                    type = "CUSTOMER",
                    partyName = customerName.ifBlank { "زبون آجل" },
                    phone = customerPhone,
                    originalAmount = finalDebtAmount,
                    paidAmount = 0.0,
                    remainingAmount = finalDebtAmount,
                    details = "فاتورة بيع آجل #$invoiceNumber: ${product.modelName} ($unitDesc)",
                    referenceSaleId = saleId,
                    dueDate = now + 86400000L * 15,
                    createdAt = now,
                    isSettled = false
                )
                dao.insertDebt(debt)
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تسجيل بيع",
                    actor = employee?.name ?: "صاحب المحل",
                    details = "فاتورة بيع #$invoiceNumber: ${product.modelName} ($unitDesc) بقيمة ${FormatUtils.formatCurrency(totalAmount)} ($paymentType)",
                    timestamp = now
                )
            )

            sale.copy(id = saleId)
        }
    }

    // --- Delete / Reverse Sale (Whole Invoice Atomically) ---
    suspend fun deleteSale(saleId: Long) {
        runInTransaction {
            val target = dao.getSaleById(saleId) ?: return@runInTransaction
            val invoiceItems = if (target.invoiceNumber.isNotBlank()) {
                val byInv = dao.getSalesByInvoiceNumber(target.invoiceNumber)
                if (byInv.isNotEmpty()) byInv else listOf(target)
            } else {
                listOf(target)
            }
            deleteSaleItemsInternal(invoiceItems)
        }
    }

    suspend fun deleteSaleInvoice(invoiceNumber: String) {
        runInTransaction {
            val items = dao.getSalesByInvoiceNumber(invoiceNumber)
            if (items.isNotEmpty()) {
                deleteSaleItemsInternal(items)
            }
        }
    }

    private suspend fun deleteSaleItemsInternal(items: List<SaleTransaction>) {
        if (items.isEmpty()) return
        val saleIds = items.map { it.id }
        val firstSale = items.first()
        val label = firstSale.invoiceNumber.ifBlank { "#${firstSale.id}" }

        // 0. Protect invoices that have return records linked to them
        if (dao.getReturnsByOriginalLineIds(ReturnTypes.SALE_RETURN, saleIds).isNotEmpty()) {
            throw IllegalStateException("لا يمكن حذف فاتورة البيع ($label) لوجود مرتجعات مسجلة عليها! يرجى حذف حركة المرتجع أولاً.")
        }

        // 1. Protect debts that already have collected payments
        val linkedDebts = dao.getDebtsBySaleIds(saleIds)
        for (debt in linkedDebts) {
            if (debt.paidAmount > 0.01 || dao.countPaymentsForDebt(debt.id) > 0) {
                throw IllegalStateException("لا يمكن حذف الفاتورة ($label) لوجود دفعات تحصيل مسددة على دينها!")
            }
        }

        // 2. Ensure cash drawer has enough funds to reverse cash part
        val totalCash = items.sumOf { it.cashAmount }
        requireSufficientCash(totalCash)

        // 3. Reverse exchange transfers & balance atomically
        val totalTransfer = items.sumOf { it.transferAmount }
        reverseLinkedTransfersOrFallback(
            refType = "SALE",
            refIds = saleIds,
            fallbackRefNumber = firstSale.transferRef,
            fallbackAccountId = firstSale.exchangeAccountId,
            fallbackAmount = totalTransfer,
            fallbackWasCredit = true,
            reason = "إلغاء فاتورة بيع $label"
        )

        // 4. Restore stock for ALL items in the invoice
        for (item in items) {
            dao.incrementStock(item.productId, item.quantity)
        }

        // 5. Delete cash movements, debts, and all sale lines
        dao.deleteCashMovementsForSales(saleIds)
        linkedDebts.forEach { dao.deleteDebt(it.id) }
        items.forEach { dao.deleteSale(it.id) }

        dao.insertAuditLog(
            AuditLogRecord(
                action = "إلغاء بيع",
                actor = "المدير",
                details = "تم إلغاء فاتورة البيع $label بالكامل (${items.size} أصناف) وإرجاع المخزون والأرصدة",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSale(newSale: SaleTransaction) {
        runInTransaction {
            val oldSale = dao.getSaleById(newSale.id) ?: throw IllegalArgumentException("فاتورة البيع غير موجودة!")
            if (oldSale.invoiceNumber.isNotBlank()) {
                val siblingSales = dao.getSalesByInvoiceNumber(oldSale.invoiceNumber)
                if (siblingSales.size > 1) {
                    throw IllegalStateException("هذه الفاتورة متعددة الأصناف! يجب تعديل الفاتورة كاملة من نافذة تعديل الفاتورة الموحدة.")
                }
            }
            val exAcc = if (newSale.exchangeAccountId > 0L) dao.getExchangeAccountById(newSale.exchangeAccountId) else null
            val key = if (oldSale.invoiceNumber.isNotBlank()) oldSale.invoiceNumber else "ID:${oldSale.id}"
            updateSaleInvoiceInternal(
                originalInvoiceKey = key,
                updatedItems = listOf(newSale),
                newInvoiceNumber = newSale.invoiceNumber.ifBlank { oldSale.invoiceNumber },
                customerName = newSale.customerName,
                customerPhone = newSale.customerPhone,
                paymentType = newSale.paymentType,
                cashAmount = newSale.cashAmount,
                transferAmount = newSale.transferAmount,
                debtAmount = newSale.debtAmount,
                exchangeAccount = exAcc,
                transferRef = newSale.transferRef,
                timestamp = newSale.timestamp
            )
        }
    }

    suspend fun updateSaleInvoice(
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
        timestamp: Long
    ) {
        runInTransaction {
            updateSaleInvoiceInternal(
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
        }
    }

    private suspend fun updateSaleInvoiceInternal(
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
        timestamp: Long
    ) {
        if (updatedItems.isEmpty()) throw IllegalArgumentException("يجب أن تحتوي الفاتورة على صنف واحد على الأقل.")
        for (item in updatedItems) {
            if (item.quantity <= 0) throw IllegalArgumentException("كمية البيع للصنف (${item.productName}) يجب أن تكون أكبر من الصفر.")
            if (item.unitSellPrice <= 0) throw IllegalArgumentException("سعر البيع للصنف (${item.productName}) يجب أن يكون أكبر من الصفر.")
            if (item.discount < 0) throw IllegalArgumentException("لا يمكن إدخال خصم سالب.")
            if (item.totalAmount <= 0) throw IllegalArgumentException("صافي مبلغ الصنف (${item.productName}) يجب أن يكون أكبر من الصفر.")
        }
        if (cashAmount < 0 || transferAmount < 0 || debtAmount < 0) {
            throw IllegalArgumentException("لا يمكن إدخال مبالغ دفع سالبة.")
        }
        val netTotal = updatedItems.sumOf { it.totalAmount }
        if (kotlin.math.abs((cashAmount + transferAmount + debtAmount) - netTotal) > 0.01) {
            throw IllegalArgumentException("مجموع الدفع (${FormatUtils.formatCurrency(cashAmount + transferAmount + debtAmount)}) لا يطابق صافي الفاتورة (${FormatUtils.formatCurrency(netTotal)}).")
        }
        if (transferAmount > 0.001 && exchangeAccount == null) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
        }
        if (debtAmount > 0.001 && customerName.trim().isBlank()) {
            throw IllegalArgumentException("يجب إدخال اسم العميل لتسجيل المبلغ الآجل.")
        }

        val oldItems = if (originalInvoiceKey.startsWith("ID:")) {
            listOfNotNull(dao.getSaleById(originalInvoiceKey.removePrefix("ID:").toLong()))
        } else {
            dao.getSalesByInvoiceNumber(originalInvoiceKey)
        }
        if (oldItems.isEmpty()) throw IllegalArgumentException("فاتورة البيع غير موجودة!")
        val oldIds = oldItems.map { it.id }
        val oldFirst = oldItems.first()
        val label = oldFirst.invoiceNumber.ifBlank { "#${oldFirst.id}" }
        if (dao.getReturnsByOriginalLineIds(ReturnTypes.SALE_RETURN, oldIds).isNotEmpty()) {
            throw IllegalStateException("لا يمكن تعديل فاتورة البيع ($label) لوجود مرتجعات مسجلة عليها! يرجى حذف حركة المرتجع أولاً.")
        }

        val excludeNum = if (originalInvoiceKey.startsWith("ID:")) oldFirst.invoiceNumber else originalInvoiceKey
        val finalInvoiceNumber = resolveSaleInvoiceNumber(newInvoiceNumber, excludeInvoiceNumber = excludeNum)
        val finalCustomer = customerName.trim().ifBlank { if (debtAmount > 0) "عميل آجل" else "زبون نقدي" }

        // Debt check before mutating
        val oldDebts = dao.getDebtsBySaleIds(oldIds)
        val alreadyPaidOnDebt = oldDebts.sumOf { it.paidAmount }
        val hasDebtPayments = oldDebts.any { dao.countPaymentsForDebt(it.id) > 0 } || alreadyPaidOnDebt > 0.01
        if (hasDebtPayments && debtAmount + 0.01 < alreadyPaidOnDebt) {
            throw IllegalStateException("لا يمكن تخفيض الدين لأقل من المبلغ المحصل مسبقاً (${FormatUtils.formatCurrency(alreadyPaidOnDebt)})!")
        }

        // 1. Restore old stock, then decrement new stock safely
        for (old in oldItems) {
            dao.incrementStock(old.productId, old.quantity)
        }
        val now = System.currentTimeMillis()
        for (item in updatedItems) {
            val updatedRows = dao.decrementStockSafe(item.productId, item.quantity, timestamp)
            if (updatedRows == 0) {
                throw IllegalStateException("الكمية المطلوبة من (${item.productName}) غير متوفرة في المخزون بعد التعديل!")
            }
        }

        // 2. Replace cash movement atomically & prevent negative drawer
        dao.deleteCashMovementsForSales(oldIds)
        val drawerBalanceAfterRemoval = dao.getAvailableCashBalance()
        if (drawerBalanceAfterRemoval + cashAmount < -0.001) {
            throw IllegalStateException("رصيد الصندوق النقدي لا يكفي لتغطية الفرق بعد تعديل الفاتورة!")
        }

        // 3. Reverse old exchange transfers atomically
        val oldTotalTransfer = oldItems.sumOf { it.transferAmount }
        reverseLinkedTransfersOrFallback(
            refType = "SALE",
            refIds = oldIds,
            fallbackRefNumber = oldFirst.transferRef,
            fallbackAccountId = oldFirst.exchangeAccountId,
            fallbackAmount = oldTotalTransfer,
            fallbackWasCredit = true,
            reason = "تعديل فاتورة بيع #$finalInvoiceNumber",
            timestamp = now
        )

        // 4. Persist updated sale lines (preserve IDs where possible)
        val oldById = oldItems.associateBy { it.id }
        val keptIds = updatedItems.mapNotNull { it.id.takeIf { id -> id in oldById } }.toSet()
        oldItems.filter { it.id !in keptIds }.forEach { dao.deleteSale(it.id) }

        var primarySaleId = 0L
        for (item in updatedItems) {
            val ratio = if (netTotal > 0.0) item.totalAmount / netTotal else 0.0
            val savedItem = item.copy(
                customerName = finalCustomer,
                customerPhone = customerPhone,
                paymentType = paymentType,
                cashAmount = cashAmount * ratio,
                transferAmount = transferAmount * ratio,
                debtAmount = debtAmount * ratio,
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: "",
                transferRef = transferRef,
                timestamp = timestamp,
                invoiceNumber = finalInvoiceNumber
            )
            val savedId = if (savedItem.id in oldById) {
                dao.updateSale(savedItem)
                savedItem.id
            } else {
                dao.insertSale(savedItem.copy(id = 0L))
            }
            if (primarySaleId == 0L) primarySaleId = savedId
        }

        // 5. Re-insert cash movement
        val shiftId = oldFirst.shiftId.takeIf { it > 0L } ?: (dao.getActiveShift().firstOrNull()?.id ?: 0L)
        if (cashAmount > 0.001) {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "SALE_CASH",
                    amount = cashAmount,
                    isIncome = true,
                    title = "فاتورة بيع #$finalInvoiceNumber (${updatedItems.size} أصناف)",
                    details = "العميل: $finalCustomer (فاتورة محدثة)",
                    timestamp = timestamp,
                    referenceSaleId = primarySaleId
                )
            )
        }

        // 6. Apply new exchange transfer
        if (transferAmount > 0.001 && exchangeAccount != null) {
            dao.updateExchangeBalance(exchangeAccount.id, transferAmount)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = exchangeAccount.id,
                    accountName = exchangeAccount.name,
                    type = "مبيعات تحويل",
                    amount = transferAmount,
                    isCredit = true,
                    title = "تحويل فاتورة #$finalInvoiceNumber (${updatedItems.size} أصناف)",
                    referenceNumber = transferRef.ifBlank { finalInvoiceNumber },
                    notes = "فاتورة بيع #$finalInvoiceNumber | العميل: $finalCustomer",
                    timestamp = timestamp,
                    referenceType = "SALE",
                    referenceId = primarySaleId
                )
            )
        }

        // 7. Reconcile debt record cleanly
        if (debtAmount > 0.001) {
            val primaryDebt = oldDebts.firstOrNull()
            if (primaryDebt != null) {
                oldDebts.drop(1).forEach { dao.deleteDebt(it.id) }
                val newRemaining = (debtAmount - primaryDebt.paidAmount).coerceAtLeast(0.0)
                dao.updateDebt(
                    primaryDebt.copy(
                        partyName = finalCustomer,
                        phone = customerPhone,
                        originalAmount = debtAmount,
                        remainingAmount = newRemaining,
                        referenceSaleId = primarySaleId,
                        createdAt = timestamp,
                        dueDate = timestamp + 86400000L * 15,
                        isSettled = newRemaining <= 0.01
                    )
                )
            } else {
                dao.insertDebt(
                    DebtRecord(
                        type = "CUSTOMER",
                        partyName = finalCustomer,
                        phone = customerPhone,
                        originalAmount = debtAmount,
                        paidAmount = 0.0,
                        remainingAmount = debtAmount,
                        details = "آجل فاتورة بيع #$finalInvoiceNumber",
                        referenceSaleId = primarySaleId,
                        createdAt = timestamp,
                        dueDate = timestamp + 86400000L * 15,
                        isSettled = false
                    )
                )
            }
        } else {
            oldDebts.forEach { dao.deleteDebt(it.id) }
        }

        dao.insertAuditLog(
            AuditLogRecord(
                action = "تعديل بيع",
                actor = "المدير",
                details = "تم تعديل فاتورة البيع #$finalInvoiceNumber للعميل ($finalCustomer) بإجمالي ${FormatUtils.formatCurrency(netTotal)}",
                timestamp = now
            )
        )
    }

    // --- Multi-Item Purchase Cart Execution (Single Room Transaction & Unified Invoice) ---
    suspend fun executeCartPurchase(
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
        timestamp: Long = System.currentTimeMillis()
    ): String {
        if (items.isEmpty()) {
            throw IllegalArgumentException("فاتورة الشراء فارغة! يرجى إضافة صنف واحد على الأقل.")
        }
        for (item in items) {
            if (item.unitCount <= 0) {
                throw IllegalArgumentException("يرجى إدخال كمية شراء صحيحة للصنف (${item.modelName}).")
            }
            if (item.buyPricePerUnit <= 0) {
                throw IllegalArgumentException("سعر شراء الصنف (${item.modelName}) يجب أن يكون أكبر من الصفر.")
            }
            if (item.sellPricePerUnit <= 0) {
                throw IllegalArgumentException("سعر بيع الصنف (${item.modelName}) يجب أن يكون أكبر من الصفر.")
            }
        }

        val totalCost = items.sumOf { it.lineCost }
        val actualCash: Double
        val actualTransfer: Double
        val actualDebt: Double

        when (paymentMethod) {
            "نقداً من الصندوق", "نقداً (كاش)", "نقداً" -> {
                actualCash = totalCost
                actualTransfer = 0.0
                actualDebt = 0.0
            }
            "تحويل عبر الصرافة", "تحويل عبر الصراف", "تحويل صرافة" -> {
                if (exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المدفوع منها.")
                }
                actualCash = 0.0
                actualTransfer = totalCost
                actualDebt = 0.0
            }
            "نصف كاش ونصف حوالة" -> {
                if (exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
                }
                if (cashPaidPart > 0.0 || transferPaidPart > 0.0) {
                    if (cashPaidPart < 0 || transferPaidPart < 0) {
                        throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل الدفع.")
                    }
                    if (kotlin.math.abs((cashPaidPart + transferPaidPart) - totalCost) > 0.01) {
                        throw IllegalArgumentException("مجموع الدفع النقدي ($cashPaidPart) والتحويل ($transferPaidPart) يجب أن يساوي إجمالي الشراء ($totalCost).")
                    }
                    actualCash = cashPaidPart
                    actualTransfer = transferPaidPart
                } else {
                    val half = totalCost / 2.0
                    actualCash = half
                    actualTransfer = totalCost - half
                }
                actualDebt = 0.0
            }
            "آجل للمورد", "آجل (دين)", "دين مورد" -> {
                if (supplierName.trim().isBlank()) {
                    throw IllegalArgumentException("يجب كتابة أو اختيار اسم المورد لتسجيل فاتورة الشراء بالآجل.")
                }
                actualCash = 0.0
                actualTransfer = 0.0
                actualDebt = totalCost
            }
            "مختلط (كاش + حوالة + آجل)" -> {
                if (cashPaidPart < 0 || transferPaidPart < 0 || debtPart < 0) {
                    throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل دفع الشراء.")
                }
                if (transferPaidPart > 0 && exchangeAccount == null) {
                    throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة ($transferPaidPart ريال).")
                }
                if (debtPart > 0 && supplierName.trim().isBlank()) {
                    throw IllegalArgumentException("يجب كتابة اسم المورد لتسجيل المبلغ الآجل ($debtPart ريال).")
                }
                val sumParts = cashPaidPart + transferPaidPart + debtPart
                if (kotlin.math.abs(sumParts - totalCost) > 0.01) {
                    throw IllegalArgumentException("مجموع مبالغ الشراء ($sumParts ريال) يجب أن يساوي تماماً إجمالي الفاتورة ($totalCost ريال).")
                }
                actualCash = cashPaidPart
                actualTransfer = transferPaidPart
                actualDebt = debtPart
            }
            else -> {
                actualCash = totalCost
                actualTransfer = 0.0
                actualDebt = 0.0
            }
        }

        return runInTransaction {
            requireSufficientCash(actualCash)
            if (actualTransfer > 0 && exchangeAccount != null) {
                requireSufficientExchange(exchangeAccount.id, actualTransfer)
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp
            val finalInvoiceNumber = resolvePurchaseInvoiceNumber(invoiceNumber)
            val finalSupplier = supplierName.trim().ifBlank { "مورد عام" }

            var firstPurchaseId = 0L

            // 1. Process each item: insert purchase record & update/create inventory product
            val allExistingProducts = dao.getAllProductsDirect().toMutableList()
            for (item in items) {
                val totalPieces = item.totalPieces
                val lineCost = item.lineCost
                val buyPricePerPiece = item.buyPricePerPiece
                val sellPricePerPiece = item.sellPricePerPiece

                val itemCash = if (totalCost > 0) actualCash * (lineCost / totalCost) else 0.0
                val itemTransfer = if (totalCost > 0) actualTransfer * (lineCost / totalCost) else 0.0
                val itemDebt = if (totalCost > 0) actualDebt * (lineCost / totalCost) else 0.0

                val purchase = PurchaseRecord(
                    supplierName = finalSupplier,
                    category = item.category,
                    modelName = item.modelName,
                    color = item.color,
                    size = item.size,
                    quantity = totalPieces,
                    unitBuyPrice = buyPricePerPiece,
                    unitSellPrice = sellPricePerPiece,
                    totalCost = lineCost,
                    paidAmount = itemCash + itemTransfer,
                    remainingDebt = itemDebt,
                    invoiceNumber = finalInvoiceNumber,
                    paymentMethod = paymentMethod,
                    cashPaid = itemCash,
                    transferPaid = itemTransfer,
                    exchangeAccountId = exchangeAccount?.id ?: 0L,
                    exchangeAccountName = exchangeAccount?.name ?: "",
                    timestamp = now,
                    unit = item.unit,
                    unitCount = item.unitCount,
                    colorsCount = item.colorsCount,
                    notes = if (transferRef.isNotBlank()) "حوالة: $transferRef | ${item.notes}" else item.notes
                )
                val pId = dao.insertPurchase(purchase)
                if (firstPurchaseId == 0L) firstPurchaseId = pId

                // Update stock or insert new product
                val existingIndex = allExistingProducts.indexOfFirst {
                    it.modelName.trim().equals(item.modelName.trim(), ignoreCase = true) &&
                        it.color.trim().equals(item.color.trim(), ignoreCase = true) &&
                        it.size.trim().equals(item.size.trim(), ignoreCase = true)
                }

                if (existingIndex >= 0) {
                    val existing = allExistingProducts[existingIndex]
                    val updated = existing.copy(
                        quantity = existing.quantity + totalPieces,
                        buyPrice = buyPricePerPiece,
                        sellPrice = sellPricePerPiece,
                        supplierName = finalSupplier.ifBlank { existing.supplierName },
                        barcode = item.barcode.ifBlank { existing.barcode },
                        notes = if (item.notes.isNotBlank()) item.notes else existing.notes,
                        unit = item.unit,
                        colorsCount = item.colorsCount,
                        colorDetails = item.colorDetails
                    )
                    dao.updateProduct(updated)
                    allExistingProducts[existingIndex] = updated
                } else {
                    val newProduct = ProductItem(
                        category = item.category,
                        modelName = item.modelName,
                        color = item.color,
                        size = item.size,
                        quantity = totalPieces,
                        buyPrice = buyPricePerPiece,
                        sellPrice = sellPricePerPiece,
                        supplierName = finalSupplier,
                        purchaseDate = now,
                        invoiceNumber = finalInvoiceNumber,
                        barcode = item.barcode,
                        notes = item.notes,
                        unit = item.unit,
                        colorsCount = item.colorsCount,
                        colorDetails = item.colorDetails
                    )
                    val insertedProdId = dao.insertProduct(newProduct)
                    allExistingProducts.add(newProduct.copy(id = insertedProdId))
                }
            }

            val itemsCountText = "${items.size} أصناف"

            // 2. Single Cash Drawer deduction
            if (actualCash > 0) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "PURCHASE_PAID",
                        amount = actualCash,
                        isIncome = false,
                        title = "فاتورة مشتريات #$finalInvoiceNumber ($itemsCountText)",
                        details = "المورد: $finalSupplier${if (actualTransfer > 0 || actualDebt > 0) " (جزء نقدي)" else ""}",
                        timestamp = now,
                        referencePurchaseId = firstPurchaseId
                    )
                )
            }

            // 3. Single Exchange Account deduction
            if (actualTransfer > 0 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, -actualTransfer)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مشتريات تحويل",
                        amount = actualTransfer,
                        isCredit = false,
                        title = "تحويل مشتريات فاتورة #$finalInvoiceNumber ($itemsCountText)",
                        referenceNumber = transferRef.ifBlank { finalInvoiceNumber },
                        notes = "فاتورة مشتريات #$finalInvoiceNumber | المورد: $finalSupplier",
                        timestamp = now,
                        referenceType = "PURCHASE",
                        referenceId = firstPurchaseId
                    )
                )
            }

            // 4. Single Supplier Debt
            if (actualDebt > 0) {
                dao.insertDebt(
                    DebtRecord(
                        type = "SUPPLIER",
                        partyName = finalSupplier,
                        originalAmount = actualDebt,
                        paidAmount = 0.0,
                        remainingAmount = actualDebt,
                        details = "آجل فاتورة شراء #$finalInvoiceNumber ($itemsCountText)",
                        referencePurchaseId = firstPurchaseId,
                        dueDate = now + 86400000L * 30,
                        createdAt = now,
                        isSettled = false
                    )
                )
            }

            // 5. Audit Log
            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تسجيل مشتريات",
                    actor = "المدير",
                    details = "فاتورة شراء #$finalInvoiceNumber (${items.size} أصناف) بمبلغ ${FormatUtils.formatCurrency(totalCost)} من المورد ($finalSupplier)",
                    timestamp = now
                )
            )

            finalInvoiceNumber
        }
    }

    // --- Purchase Goods / Restock ---
    suspend fun recordPurchase(
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
        cashPaidPart: Double = 0.0,
        transferPaidPart: Double = 0.0,
        exchangeAccount: ExchangeAccount? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        // Strict accounting validations
        if (unitCount <= 0) {
            throw IllegalArgumentException("يرجى إدخال كمية شراء صحيحة أكبر من الصفر.")
        }
        if (buyPricePerUnit <= 0) {
            throw IllegalArgumentException("سعر الشراء يجب أن يكون أكبر من الصفر.")
        }
        if (sellPricePerUnit <= 0) {
            throw IllegalArgumentException("سعر البيع المقترح يجب أن يكون أكبر من الصفر.")
        }
        if (paidAmount < 0) {
            throw IllegalArgumentException("المبلغ المدفوع لا يمكن أن يكون سالباً.")
        }
        val totalCost = buyPricePerUnit * unitCount
        if (paidAmount > totalCost + 0.01) {
            throw IllegalArgumentException("المبلغ المدفوع (${FormatUtils.formatCurrency(paidAmount)}) لا يمكن أن يتجاوز إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCost)}).")
        }

        if (paymentMethod == "تحويل عبر الصرافة" || paymentMethod == "تحويل عبر الصراف" || paymentMethod == "تحويل صرافة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المدفوع منها قبل تأكيد الشراء.")
            }
        }
        if (paymentMethod == "نصف كاش ونصف حوالة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
            }
            if (cashPaidPart < 0 || transferPaidPart < 0) {
                throw IllegalArgumentException("لا يمكن إدخال مبالغ سالبة في تفاصيل الدفع.")
            }
            if (kotlin.math.abs((cashPaidPart + transferPaidPart) - paidAmount) > 0.01) {
                throw IllegalArgumentException("مجموع الدفع النقدي ($cashPaidPart) والتحويل ($transferPaidPart) يجب أن يعادل المبلغ المدفوع ($paidAmount).")
            }
        }

        val finalCashPaid = when (paymentMethod) {
            "نقداً من الصندوق", "نقداً (كاش)", "نقداً" -> paidAmount
            "نصف كاش ونصف حوالة" -> cashPaidPart
            else -> 0.0
        }
        val finalTransferPaid = when (paymentMethod) {
            "تحويل عبر الصرافة", "تحويل عبر الصراف", "تحويل صرافة" -> paidAmount
            "نصف كاش ونصف حوالة" -> transferPaidPart
            else -> 0.0
        }

        return runInTransaction {
            requireSufficientCash(finalCashPaid)
            if (finalTransferPaid > 0 && exchangeAccount != null) {
                requireSufficientExchange(exchangeAccount.id, finalTransferPaid)
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp
            val finalInvoiceNumber = resolvePurchaseInvoiceNumber(invoiceNumber)

            val multiplier = ClothingUnits.getMultiplier(unit)
            val totalPieces = (unitCount * multiplier).toInt().coerceAtLeast(1)
            val remainingDebt = (totalCost - paidAmount).coerceAtLeast(0.0)

            val buyPricePerPiece = totalCost / totalPieces
            val sellPricePerPiece = (sellPricePerUnit * unitCount) / totalPieces
            val finalSupplier = supplierName.trim().ifBlank { "مورد عام" }

            val purchase = PurchaseRecord(
                supplierName = finalSupplier,
                category = category,
                modelName = modelName,
                color = color,
                size = size,
                quantity = totalPieces,
                unitBuyPrice = buyPricePerPiece,
                unitSellPrice = sellPricePerPiece,
                totalCost = totalCost,
                paidAmount = paidAmount,
                remainingDebt = remainingDebt,
                invoiceNumber = finalInvoiceNumber,
                paymentMethod = paymentMethod,
                cashPaid = finalCashPaid,
                transferPaid = finalTransferPaid,
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: "",
                timestamp = now,
                unit = unit,
                unitCount = unitCount,
                colorsCount = colorsCount,
                notes = notes
            )
            val purchaseId = dao.insertPurchase(purchase)

            // Add or update product stock atomically
            val allProds = dao.getAllProductsDirect()
            val existing = allProds.find {
                it.modelName.trim().equals(modelName.trim(), ignoreCase = true) &&
                    it.color.trim().equals(color.trim(), ignoreCase = true) &&
                    it.size.trim().equals(size.trim(), ignoreCase = true)
            }

            if (existing != null) {
                val updated = existing.copy(
                    quantity = existing.quantity + totalPieces,
                    buyPrice = buyPricePerPiece,
                    sellPrice = sellPricePerPiece,
                    supplierName = finalSupplier.ifBlank { existing.supplierName },
                    barcode = barcode.ifBlank { existing.barcode },
                    notes = if (notes.isNotBlank()) notes else existing.notes,
                    unit = unit,
                    colorsCount = colorsCount,
                    colorDetails = colorDetails
                )
                dao.updateProduct(updated)
            } else {
                val newProduct = ProductItem(
                    category = category,
                    modelName = modelName,
                    color = color,
                    size = size,
                    quantity = totalPieces,
                    buyPrice = buyPricePerPiece,
                    sellPrice = sellPricePerPiece,
                    supplierName = finalSupplier,
                    purchaseDate = now,
                    invoiceNumber = finalInvoiceNumber,
                    barcode = barcode,
                    notes = notes,
                    unit = unit,
                    colorsCount = colorsCount,
                    colorDetails = colorDetails
                )
                dao.insertProduct(newProduct)
            }

            val unitDesc = if (unit == ClothingUnits.PIECE) "$totalPieces حبه" else "${unitCount.toInt()} $unit ($totalPieces حبه)"

            if (finalCashPaid > 0) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "PURCHASE_PAID",
                        amount = finalCashPaid,
                        isIncome = false,
                        title = "شراء بضاعة: $modelName ($unitDesc)",
                        details = "المورد: $finalSupplier | فاتورة: $finalInvoiceNumber${if (finalTransferPaid > 0) " (جزء نقدي)" else ""}",
                        timestamp = now,
                        referencePurchaseId = purchaseId
                    )
                )
            }

            if (finalTransferPaid > 0 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, -finalTransferPaid)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مشتريات تحويل",
                        amount = finalTransferPaid,
                        isCredit = false,
                        title = "مشتريات تحويل: $modelName ($unitDesc)",
                        referenceNumber = finalInvoiceNumber,
                        notes = "فاتورة شراء #$finalInvoiceNumber | المورد: $finalSupplier",
                        timestamp = now,
                        referenceType = "PURCHASE",
                        referenceId = purchaseId
                    )
                )
            }

            if (remainingDebt > 0) {
                dao.insertDebt(
                    DebtRecord(
                        type = "SUPPLIER",
                        partyName = finalSupplier,
                        phone = "",
                        originalAmount = totalCost,
                        paidAmount = paidAmount,
                        remainingAmount = remainingDebt,
                        details = "متبقي شراء بضاعة: $modelName ($unitDesc) فاتورة #$finalInvoiceNumber",
                        referencePurchaseId = purchaseId,
                        dueDate = now + 86400000L * 30,
                        createdAt = now,
                        isSettled = false
                    )
                )
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تسجيل مشتريات",
                    actor = "صاحب المحل",
                    details = "فاتورة شراء #$finalInvoiceNumber: $modelName ($unitDesc) من $finalSupplier بإجمالي ${FormatUtils.formatCurrency(totalCost)}",
                    timestamp = now
                )
            )

            purchaseId
        }
    }

    // --- Delete / Reverse Purchase (Whole Invoice Atomically, Safeguarded against sold stock) ---
    suspend fun deletePurchase(purchaseId: Long) {
        runInTransaction {
            val target = dao.getPurchaseById(purchaseId) ?: return@runInTransaction
            val invoiceItems = if (target.invoiceNumber.isNotBlank()) {
                val byInv = dao.getPurchasesByInvoiceNumber(target.invoiceNumber)
                if (byInv.isNotEmpty()) byInv else listOf(target)
            } else {
                listOf(target)
            }
            deletePurchaseItemsInternal(invoiceItems)
        }
    }

    suspend fun deletePurchaseInvoice(invoiceNumber: String) {
        runInTransaction {
            val items = dao.getPurchasesByInvoiceNumber(invoiceNumber)
            if (items.isNotEmpty()) {
                deletePurchaseItemsInternal(items)
            }
        }
    }

    private suspend fun deletePurchaseItemsInternal(items: List<PurchaseRecord>) {
        if (items.isEmpty()) return
        val purchaseIds = items.map { it.id }
        val firstPurchase = items.first()
        val label = firstPurchase.invoiceNumber.ifBlank { "#${firstPurchase.id}" }

        // 0. Protect invoices that have return records linked to them
        if (dao.getReturnsByOriginalLineIds(ReturnTypes.PURCHASE_RETURN, purchaseIds).isNotEmpty()) {
            throw IllegalStateException("لا يمكن حذف فاتورة الشراء ($label) لوجود مرتجعات مسجلة عليها! يرجى حذف حركة المرتجع أولاً.")
        }

        // 1. Protect supplier debts that already have payments recorded
        val linkedDebts = dao.getDebtsByPurchaseIds(purchaseIds)
        for (debt in linkedDebts) {
            if (debt.paidAmount > 0.01 && dao.countPaymentsForDebt(debt.id) > 0) {
                throw IllegalStateException("لا يمكن حذف فاتورة الشراء ($label) لوجود دفعات سداد مسجلة للمورد على دينها!")
            }
        }

        // 2. Verify aggregate stock across ALL invoice lines has not already been sold
        val allProds = dao.getAllProductsDirect()
        val qtyNeededByProdId = mutableMapOf<Long, Pair<ProductItem, Int>>()
        for (item in items) {
            val prod = allProds.find {
                it.modelName.trim().equals(item.modelName.trim(), ignoreCase = true) &&
                    it.color.trim().equals(item.color.trim(), ignoreCase = true) &&
                    it.size.trim().equals(item.size.trim(), ignoreCase = true)
            }
            if (prod != null) {
                val prev = qtyNeededByProdId[prod.id]?.second ?: 0
                qtyNeededByProdId[prod.id] = prod to (prev + item.quantity)
            }
        }
        for ((_, pair) in qtyNeededByProdId) {
            val (prod, neededQty) = pair
            if (prod.quantity < neededQty) {
                throw IllegalStateException(
                    "لا يمكن حذف فاتورة الشراء هذه لأن جزءاً من البضاعة (${prod.modelName}) تم بيعه بالفعل! " +
                        "(المتبقي في المخزن ${prod.quantity} حبة بينما الفاتورة تحتوي على $neededQty حبة). " +
                        "يرجى تسوية المخزون عبر الجرد بدلاً من حذف الفاتورة."
                )
            }
        }
        val now = System.currentTimeMillis()
        for ((prodId, pair) in qtyNeededByProdId) {
            dao.updateStock(prodId, pair.first.quantity - pair.second, now)
        }

        // 3. Refund cash movements
        dao.deleteCashMovementsForPurchases(purchaseIds)

        // 4. Reverse exchange transfers & refund account balance
        val totalTransfer = items.sumOf { it.transferPaid }
        reverseLinkedTransfersOrFallback(
            refType = "PURCHASE",
            refIds = purchaseIds,
            fallbackRefNumber = firstPurchase.invoiceNumber,
            fallbackAccountId = firstPurchase.exchangeAccountId,
            fallbackAmount = totalTransfer,
            fallbackWasCredit = false,
            reason = "إلغاء فاتورة شراء $label",
            timestamp = now
        )

        // 5. Delete supplier debts & all purchase rows
        linkedDebts.forEach { dao.deleteDebt(it.id) }
        items.forEach { dao.deletePurchase(it.id) }

        dao.insertAuditLog(
            AuditLogRecord(
                action = "إلغاء مشتريات",
                actor = "المدير",
                details = "تم إلغاء فاتورة الشراء $label بالكامل (${items.size} أصناف) واسترداد المبالغ المدفوعة.",
                timestamp = now
            )
        )
    }

    suspend fun updatePurchase(newPurchase: PurchaseRecord) {
        runInTransaction {
            val oldPurchase = dao.getPurchaseById(newPurchase.id) ?: throw IllegalArgumentException("فاتورة الشراء غير موجودة!")
            if (oldPurchase.invoiceNumber.isNotBlank()) {
                val siblingPurchases = dao.getPurchasesByInvoiceNumber(oldPurchase.invoiceNumber)
                if (siblingPurchases.size > 1) {
                    throw IllegalStateException("هذه الفاتورة متعددة الأصناف! يجب تعديل الفاتورة كاملة من نافذة تعديل الفاتورة الموحدة.")
                }
            }
            val exAcc = if (newPurchase.exchangeAccountId > 0L) dao.getExchangeAccountById(newPurchase.exchangeAccountId) else null
            val key = if (oldPurchase.invoiceNumber.isNotBlank()) oldPurchase.invoiceNumber else "ID:${oldPurchase.id}"
            updatePurchaseInvoiceInternal(
                originalInvoiceKey = key,
                updatedItems = listOf(newPurchase),
                newInvoiceNumber = newPurchase.invoiceNumber.ifBlank { oldPurchase.invoiceNumber },
                supplierName = newPurchase.supplierName,
                paymentMethod = newPurchase.paymentMethod,
                cashPaid = newPurchase.cashPaid,
                transferPaid = newPurchase.transferPaid,
                debtAmount = newPurchase.remainingDebt,
                exchangeAccount = exAcc,
                timestamp = newPurchase.timestamp
            )
        }
    }

    suspend fun updatePurchaseInvoice(
        originalInvoiceKey: String,
        updatedItems: List<PurchaseRecord>,
        newInvoiceNumber: String,
        supplierName: String,
        paymentMethod: String,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccount: ExchangeAccount?,
        timestamp: Long
    ) {
        runInTransaction {
            updatePurchaseInvoiceInternal(
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
        }
    }

    private suspend fun updatePurchaseInvoiceInternal(
        originalInvoiceKey: String,
        updatedItems: List<PurchaseRecord>,
        newInvoiceNumber: String,
        supplierName: String,
        paymentMethod: String,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccount: ExchangeAccount?,
        timestamp: Long
    ) {
        if (updatedItems.isEmpty()) throw IllegalArgumentException("يجب أن تحتوي فاتورة الشراء على صنف واحد على الأقل.")
        for (item in updatedItems) {
            if (item.quantity <= 0) throw IllegalArgumentException("كمية الشراء للصنف (${item.modelName}) يجب أن تكون أكبر من الصفر.")
            if (item.totalCost <= 0) throw IllegalArgumentException("تكلفة الشراء للصنف (${item.modelName}) يجب أن تكون أكبر من الصفر.")
        }
        if (cashPaid < 0 || transferPaid < 0 || debtAmount < 0) {
            throw IllegalArgumentException("لا يمكن إدخال مبالغ دفع سالبة.")
        }
        val totalCost = updatedItems.sumOf { it.totalCost }
        if (kotlin.math.abs((cashPaid + transferPaid + debtAmount) - totalCost) > 0.01) {
            throw IllegalArgumentException("مجموع الدفع والدين (${FormatUtils.formatCurrency(cashPaid + transferPaid + debtAmount)}) لا يطابق إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCost)}).")
        }
        if (transferPaid > 0.001 && exchangeAccount == null) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
        }
        if (debtAmount > 0.001 && supplierName.trim().isBlank()) {
            throw IllegalArgumentException("يجب إدخال اسم المورد لتسجيل المبلغ الآجل.")
        }

        val oldItems = if (originalInvoiceKey.startsWith("ID:")) {
            listOfNotNull(dao.getPurchaseById(originalInvoiceKey.removePrefix("ID:").toLong()))
        } else {
            dao.getPurchasesByInvoiceNumber(originalInvoiceKey)
        }
        if (oldItems.isEmpty()) throw IllegalArgumentException("فاتورة الشراء غير موجودة!")
        val oldIds = oldItems.map { it.id }
        val oldFirst = oldItems.first()
        val label = oldFirst.invoiceNumber.ifBlank { "#${oldFirst.id}" }
        if (dao.getReturnsByOriginalLineIds(ReturnTypes.PURCHASE_RETURN, oldIds).isNotEmpty()) {
            throw IllegalStateException("لا يمكن تعديل فاتورة الشراء ($label) لوجود مرتجعات مسجلة عليها! يرجى حذف حركة المرتجع أولاً.")
        }

        val excludeNum = if (originalInvoiceKey.startsWith("ID:")) oldFirst.invoiceNumber else originalInvoiceKey
        val finalInvoiceNumber = resolvePurchaseInvoiceNumber(newInvoiceNumber, excludeInvoiceNumber = excludeNum)
        val finalSupplier = supplierName.trim().ifBlank { "مورد عام" }

        // Check existing supplier debt payments
        val oldDebts = dao.getDebtsByPurchaseIds(oldIds)
        val alreadyPaidOnDebt = oldDebts.sumOf { if (dao.countPaymentsForDebt(it.id) > 0) it.paidAmount else 0.0 }
        if (alreadyPaidOnDebt > 0.01 && debtAmount + 0.01 < alreadyPaidOnDebt) {
            throw IllegalStateException("لا يمكن تخفيض دين المورد لأقل من المبلغ المسدد له مسبقاً (${FormatUtils.formatCurrency(alreadyPaidOnDebt)})!")
        }

        // 1. Reconcile product stock deltas across all old & new items
        val allProds = dao.getAllProductsDirect().toMutableList()
        val now = System.currentTimeMillis()
        for (old in oldItems) {
            val idx = allProds.indexOfFirst {
                it.modelName.trim().equals(old.modelName.trim(), ignoreCase = true) &&
                    it.color.trim().equals(old.color.trim(), ignoreCase = true) &&
                    it.size.trim().equals(old.size.trim(), ignoreCase = true)
            }
            if (idx >= 0) {
                val prod = allProds[idx]
                if (prod.quantity < old.quantity) {
                    // Track tentatively; final check after adding new items ensures net delta doesn't drop below 0
                    allProds[idx] = prod.copy(quantity = prod.quantity - old.quantity)
                } else {
                    allProds[idx] = prod.copy(quantity = prod.quantity - old.quantity)
                }
            }
        }
        for (item in updatedItems) {
            val idx = allProds.indexOfFirst {
                it.modelName.trim().equals(item.modelName.trim(), ignoreCase = true) &&
                    it.color.trim().equals(item.color.trim(), ignoreCase = true) &&
                    it.size.trim().equals(item.size.trim(), ignoreCase = true)
            }
            if (idx >= 0) {
                val prod = allProds[idx]
                val newQty = prod.quantity + item.quantity
                if (newQty < 0) {
                    throw IllegalStateException("لا يمكن إنقاص كمية الشراء للصنف (${item.modelName}) لأن جزءاً من البضاعة تم بيعه بالفعل!")
                }
                allProds[idx] = prod.copy(
                    quantity = newQty,
                    buyPrice = item.unitBuyPrice,
                    sellPrice = item.unitSellPrice
                )
            } else {
                val newProd = ProductItem(
                    category = item.category,
                    modelName = item.modelName,
                    color = item.color,
                    size = item.size,
                    quantity = item.quantity,
                    buyPrice = item.unitBuyPrice,
                    sellPrice = item.unitSellPrice,
                    supplierName = finalSupplier,
                    purchaseDate = timestamp,
                    invoiceNumber = finalInvoiceNumber
                )
                val insertedId = dao.insertProduct(newProd)
                allProds.add(newProd.copy(id = insertedId))
            }
        }
        allProds.forEach { prod ->
            if (prod.quantity < 0) {
                throw IllegalStateException("لا يمكن إنقاص كمية الشراء للصنف (${prod.modelName}) لأن جزءاً من البضاعة تم بيعه بالفعل!")
            }
            dao.updateProduct(prod)
        }

        // 2. Replace cash movement & verify sufficient drawer cash
        dao.deleteCashMovementsForPurchases(oldIds)
        requireSufficientCash(cashPaid)

        // 3. Reverse old exchange transfers, then verify exchange account has sufficient funds
        val oldTotalTransfer = oldItems.sumOf { it.transferPaid }
        reverseLinkedTransfersOrFallback(
            refType = "PURCHASE",
            refIds = oldIds,
            fallbackRefNumber = oldFirst.invoiceNumber,
            fallbackAccountId = oldFirst.exchangeAccountId,
            fallbackAmount = oldTotalTransfer,
            fallbackWasCredit = false,
            reason = "تعديل فاتورة شراء #$finalInvoiceNumber",
            timestamp = now
        )
        if (transferPaid > 0.001 && exchangeAccount != null) {
            requireSufficientExchange(exchangeAccount.id, transferPaid)
        }

        // 4. Persist purchase lines
        val oldById = oldItems.associateBy { it.id }
        val keptIds = updatedItems.mapNotNull { it.id.takeIf { id -> id in oldById } }.toSet()
        oldItems.filter { it.id !in keptIds }.forEach { dao.deletePurchase(it.id) }

        var primaryPurchaseId = 0L
        for (item in updatedItems) {
            val ratio = if (totalCost > 0.0) item.totalCost / totalCost else 0.0
            val itemCash = cashPaid * ratio
            val itemTransfer = transferPaid * ratio
            val itemDebt = debtAmount * ratio
            val savedItem = item.copy(
                supplierName = finalSupplier,
                invoiceNumber = finalInvoiceNumber,
                paymentMethod = paymentMethod,
                cashPaid = itemCash,
                transferPaid = itemTransfer,
                paidAmount = itemCash + itemTransfer,
                remainingDebt = itemDebt,
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: "",
                timestamp = timestamp
            )
            val savedId = if (savedItem.id in oldById) {
                dao.updatePurchase(savedItem)
                savedItem.id
            } else {
                dao.insertPurchase(savedItem.copy(id = 0L))
            }
            if (primaryPurchaseId == 0L) primaryPurchaseId = savedId
        }

        // 5. Re-insert cash movement
        val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
        if (cashPaid > 0.001) {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "PURCHASE_PAID",
                    amount = cashPaid,
                    isIncome = false,
                    title = "فاتورة مشتريات #$finalInvoiceNumber (${updatedItems.size} أصناف)",
                    details = "المورد: $finalSupplier (فاتورة محدثة)",
                    timestamp = timestamp,
                    referencePurchaseId = primaryPurchaseId
                )
            )
        }

        // 6. Apply new exchange transfer
        if (transferPaid > 0.001 && exchangeAccount != null) {
            dao.updateExchangeBalance(exchangeAccount.id, -transferPaid)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = exchangeAccount.id,
                    accountName = exchangeAccount.name,
                    type = "مشتريات تحويل",
                    amount = transferPaid,
                    isCredit = false,
                    title = "تحويل مشتريات فاتورة #$finalInvoiceNumber (${updatedItems.size} أصناف)",
                    referenceNumber = finalInvoiceNumber,
                    notes = "فاتورة مشتريات #$finalInvoiceNumber | المورد: $finalSupplier",
                    timestamp = timestamp,
                    referenceType = "PURCHASE",
                    referenceId = primaryPurchaseId
                )
            )
        }

        // 7. Reconcile supplier debt record cleanly
        if (debtAmount > 0.001) {
            val primaryDebt = oldDebts.firstOrNull()
            if (primaryDebt != null) {
                oldDebts.drop(1).forEach { dao.deleteDebt(it.id) }
                val paidAgainstDebt = if (dao.countPaymentsForDebt(primaryDebt.id) > 0) primaryDebt.paidAmount else 0.0
                val newRemaining = (debtAmount - paidAgainstDebt).coerceAtLeast(0.0)
                dao.updateDebt(
                    primaryDebt.copy(
                        partyName = finalSupplier,
                        originalAmount = debtAmount,
                        paidAmount = paidAgainstDebt,
                        remainingAmount = newRemaining,
                        referencePurchaseId = primaryPurchaseId,
                        createdAt = timestamp,
                        dueDate = timestamp + 86400000L * 30,
                        isSettled = newRemaining <= 0.01
                    )
                )
            } else {
                dao.insertDebt(
                    DebtRecord(
                        type = "SUPPLIER",
                        partyName = finalSupplier,
                        originalAmount = debtAmount,
                        paidAmount = 0.0,
                        remainingAmount = debtAmount,
                        details = "آجل فاتورة شراء #$finalInvoiceNumber",
                        referencePurchaseId = primaryPurchaseId,
                        createdAt = timestamp,
                        dueDate = timestamp + 86400000L * 30,
                        isSettled = false
                    )
                )
            }
        } else {
            oldDebts.forEach { dao.deleteDebt(it.id) }
        }

        dao.insertAuditLog(
            AuditLogRecord(
                action = "تعديل مشتريات",
                actor = "المدير",
                details = "تم تعديل فاتورة الشراء #$finalInvoiceNumber للمورد ($finalSupplier) بإجمالي ${FormatUtils.formatCurrency(totalCost)}",
                timestamp = now
            )
        )
    }

    // --- Expenses ---
    suspend fun recordExpense(
        category: String,
        amount: Double,
        notes: String,
        receiptNumber: String,
        paidFromCash: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        employee: Employee? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        if (amount <= 0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ مصروف صحيح أكبر من الصفر.")
        }
        if (!paidFromCash && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند عدم الدفع من الصندوق النقدي.")
        }

        return runInTransaction {
            if (paidFromCash) {
                requireSufficientCash(amount)
            } else if (exchangeAccount != null) {
                requireSufficientExchange(exchangeAccount.id, amount)
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp

            val finalNotes = if (employee != null) {
                "صرفة يومية للعامل: ${employee.name}${if (notes.isNotBlank()) " - $notes" else ""}"
            } else {
                notes
            }

            val expense = ExpenseRecord(
                category = category,
                amount = amount,
                notes = finalNotes,
                receiptNumber = receiptNumber,
                timestamp = now,
                paidFromCash = paidFromCash,
                exchangeAccountId = if (paidFromCash) 0L else (exchangeAccount?.id ?: 0L),
                exchangeAccountName = if (paidFromCash) "" else (exchangeAccount?.name ?: ""),
                shiftId = shiftId
            )
            val id = dao.insertExpense(expense)

            var empTxId = 0L
            if (employee != null) {
                val empTx = EmployeeTransaction(
                    employeeId = employee.id,
                    employeeName = employee.name,
                    type = "صرفة يومية",
                    amount = amount,
                    notes = notes.ifBlank { "صرفة يومية من خانة المصروفات" },
                    timestamp = now,
                    shiftId = shiftId,
                    paidFromCash = paidFromCash,
                    exchangeAccountId = if (paidFromCash) 0L else (exchangeAccount?.id ?: 0L),
                    exchangeAccountName = if (paidFromCash) "" else (exchangeAccount?.name ?: ""),
                    linkedExpenseId = id
                )
                empTxId = dao.insertEmployeeTransaction(empTx)
                dao.updateExpense(expense.copy(id = id, linkedEmployeeTxId = empTxId))
            }

            if (paidFromCash) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = if (employee != null) "SALARY_PAID" else "EXPENSE_PAID",
                        amount = amount,
                        isIncome = false,
                        title = if (employee != null) "صرفة يومية: ${employee.name}" else "مصروف: $category",
                        details = finalNotes.ifBlank { "وصل: $receiptNumber" },
                        timestamp = now,
                        referenceExpenseId = id,
                        referenceEmployeeTxId = empTxId.takeIf { it > 0L }
                    )
                )
            } else if (exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, -amount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "سحب مصروف",
                        amount = amount,
                        isCredit = false,
                        title = if (employee != null) "صرفة يومية: ${employee.name} (عبر ${exchangeAccount.name})" else "مصروف من الصراف: $category",
                        referenceNumber = receiptNumber,
                        notes = finalNotes,
                        timestamp = now,
                        referenceType = "EXPENSE",
                        referenceId = id
                    )
                )
            }
            id
        }
    }

    suspend fun updateExpense(newExpense: ExpenseRecord) {
        if (newExpense.amount <= 0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ مصروف صحيح أكبر من الصفر.")
        }
        if (!newExpense.paidFromCash && newExpense.exchangeAccountId <= 0L) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند الدفع بغير الصندوق.")
        }

        runInTransaction {
            val oldExpense = dao.getExpenseById(newExpense.id)
                ?: throw IllegalArgumentException("سجل المصروف غير موجود!")

            // Revert old financial impact
            dao.deleteCashMovementsForExpense(oldExpense.id)
            if (oldExpense.linkedEmployeeTxId > 0L) {
                dao.deleteCashMovementsForEmployeeTx(oldExpense.linkedEmployeeTxId)
            }
            reverseLinkedTransfersOrFallback(
                refType = "EXPENSE",
                refIds = listOf(oldExpense.id),
                fallbackAccountId = if (!oldExpense.paidFromCash) oldExpense.exchangeAccountId else 0L,
                fallbackAmount = if (!oldExpense.paidFromCash) oldExpense.amount else 0.0,
                fallbackWasCredit = false,
                reason = "تعديل مصروف #${oldExpense.id}"
            )
            if (oldExpense.linkedEmployeeTxId > 0L) {
                reverseLinkedTransfersOrFallback(
                    refType = "EMPLOYEE_TX",
                    refIds = listOf(oldExpense.linkedEmployeeTxId),
                    reason = "تعديل مصروف مرتبط #${oldExpense.id}"
                )
            }

            // Validate sufficient balances before applying updated outflow
            val targetAcc = if (!newExpense.paidFromCash) dao.getExchangeAccountById(newExpense.exchangeAccountId) else null
            if (!newExpense.paidFromCash && targetAcc == null) {
                throw IllegalArgumentException("حساب الصراف أو المحفظة المحدد غير موجود!")
            }
            if (newExpense.paidFromCash) {
                requireSufficientCash(newExpense.amount)
            } else if (targetAcc != null) {
                requireSufficientExchange(targetAcc.id, newExpense.amount)
            }

            val sanitizedExpense = newExpense.copy(
                exchangeAccountId = if (newExpense.paidFromCash) 0L else (targetAcc?.id ?: newExpense.exchangeAccountId),
                exchangeAccountName = if (newExpense.paidFromCash) "" else (targetAcc?.name ?: newExpense.exchangeAccountName),
                linkedEmployeeTxId = oldExpense.linkedEmployeeTxId
            )

            // Apply new financial impact
            val shiftId = oldExpense.shiftId.takeIf { it > 0L } ?: (dao.getActiveShift().firstOrNull()?.id ?: 0L)
            val isStipend = sanitizedExpense.category == "صرفة يومية لعامل" || sanitizedExpense.category.contains("صرفة") || oldExpense.linkedEmployeeTxId > 0L
            if (sanitizedExpense.paidFromCash) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = if (isStipend) "SALARY_PAID" else "EXPENSE_PAID",
                        amount = sanitizedExpense.amount,
                        isIncome = false,
                        title = "مصروف: ${sanitizedExpense.category}",
                        details = sanitizedExpense.notes.ifBlank { "وصل: ${sanitizedExpense.receiptNumber}" },
                        timestamp = sanitizedExpense.timestamp,
                        referenceExpenseId = sanitizedExpense.id,
                        referenceEmployeeTxId = oldExpense.linkedEmployeeTxId.takeIf { it > 0L }
                    )
                )
            } else if (targetAcc != null) {
                dao.updateExchangeBalance(targetAcc.id, -sanitizedExpense.amount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = targetAcc.id,
                        accountName = targetAcc.name,
                        type = "سحب مصروف",
                        amount = sanitizedExpense.amount,
                        isCredit = false,
                        title = "مصروف من الصراف: ${sanitizedExpense.category}",
                        referenceNumber = sanitizedExpense.receiptNumber,
                        notes = sanitizedExpense.notes,
                        timestamp = sanitizedExpense.timestamp,
                        referenceType = "EXPENSE",
                        referenceId = sanitizedExpense.id
                    )
                )
            }

            dao.updateExpense(sanitizedExpense)

            // Cascade update to linked EmployeeTransaction if present
            if (oldExpense.linkedEmployeeTxId > 0L) {
                val linkedEmpTx = dao.getEmployeeTransactionById(oldExpense.linkedEmployeeTxId)
                if (linkedEmpTx != null) {
                    dao.updateEmployeeTransaction(
                        linkedEmpTx.copy(
                            amount = sanitizedExpense.amount,
                            notes = sanitizedExpense.notes,
                            timestamp = sanitizedExpense.timestamp,
                            paidFromCash = sanitizedExpense.paidFromCash,
                            exchangeAccountId = sanitizedExpense.exchangeAccountId,
                            exchangeAccountName = sanitizedExpense.exchangeAccountName
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteExpense(id: Long) {
        runInTransaction {
            val exp = dao.getExpenseById(id) ?: return@runInTransaction
            dao.deleteCashMovementsForExpense(id)
            if (exp.linkedEmployeeTxId > 0L) {
                dao.deleteCashMovementsForEmployeeTx(exp.linkedEmployeeTxId)
            }
            reverseLinkedTransfersOrFallback(
                refType = "EXPENSE",
                refIds = listOf(id),
                fallbackAccountId = if (!exp.paidFromCash) exp.exchangeAccountId else 0L,
                fallbackAmount = if (!exp.paidFromCash) exp.amount else 0.0,
                fallbackWasCredit = false,
                reason = "حذف مصروف #${exp.id}"
            )
            if (exp.linkedEmployeeTxId > 0L) {
                reverseLinkedTransfersOrFallback(
                    refType = "EMPLOYEE_TX",
                    refIds = listOf(exp.linkedEmployeeTxId),
                    reason = "حذف مصروف مرتبط #${exp.id}"
                )
                dao.deleteEmployeeTransaction(exp.linkedEmployeeTxId)
            }
            dao.deleteExpense(id)
        }
    }

    // --- Employees & Payroll ---
    suspend fun saveEmployee(employee: Employee): Long {
        return if (employee.id == 0L) {
            dao.insertEmployee(employee)
        } else {
            dao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun deleteEmployee(employeeId: Long): DeleteEntityResult {
        return runInTransaction {
            val emp = dao.getEmployeeById(employeeId) ?: return@runInTransaction DeleteEntityResult.NOT_FOUND
            val hasLinks = dao.countEmployeeTxByEmployee(employeeId) > 0 ||
                dao.countSalesByEmployee(employeeId, emp.name) > 0
            if (hasLinks) {
                dao.updateEmployee(emp.copy(active = false))
                logAudit("أرشفة عامل", "المدير", "تم أرشفة العامل (${emp.name}) للحفاظ على الحركات والمبيعات المرتبطة به")
                DeleteEntityResult.ARCHIVED
            } else {
                dao.deleteEmployee(employeeId)
                logAudit("حذف عامل", "المدير", "تم حذف العامل (${emp.name})")
                DeleteEntityResult.DELETED
            }
        }
    }

    private fun isMonetaryEmployeeTxType(type: String): Boolean =
        type == "سلفة" || type == "صرف راتب" || type == "مكافأة" || type == "صرفة يومية"

    suspend fun updateEmployeeTransaction(newTx: EmployeeTransaction) {
        if (newTx.amount <= 0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ صحيح أكبر من الصفر.")
        }
        val isMonetary = isMonetaryEmployeeTxType(newTx.type)
        if (isMonetary && !newTx.paidFromCash && newTx.exchangeAccountId <= 0L) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند الصرف بغير الصندوق.")
        }

        runInTransaction {
            val oldTx = dao.getEmployeeTransactionById(newTx.id)
                ?: throw IllegalArgumentException("حركة العامل غير موجودة!")
            val oldWasMonetary = isMonetaryEmployeeTxType(oldTx.type)

            // Revert old financial impact
            dao.deleteCashMovementsForEmployeeTx(oldTx.id)
            if (oldTx.linkedExpenseId > 0L) {
                dao.deleteCashMovementsForExpense(oldTx.linkedExpenseId)
            }
            reverseLinkedTransfersOrFallback(
                refType = "EMPLOYEE_TX",
                refIds = listOf(oldTx.id),
                fallbackAccountId = if (!oldTx.paidFromCash && oldWasMonetary) oldTx.exchangeAccountId else 0L,
                fallbackAmount = if (!oldTx.paidFromCash && oldWasMonetary) oldTx.amount else 0.0,
                fallbackWasCredit = false,
                reason = "تعديل حركة عامل #${oldTx.id}"
            )
            if (oldTx.linkedExpenseId > 0L) {
                reverseLinkedTransfersOrFallback(
                    refType = "EXPENSE",
                    refIds = listOf(oldTx.linkedExpenseId),
                    reason = "تعديل حركة عامل مرتبطة بمصروف #${oldTx.id}"
                )
            }

            val targetAcc = if (isMonetary && !newTx.paidFromCash) dao.getExchangeAccountById(newTx.exchangeAccountId) else null
            if (isMonetary && !newTx.paidFromCash && targetAcc == null) {
                throw IllegalArgumentException("حساب الصراف أو المحفظة المحدد غير موجود!")
            }
            if (isMonetary) {
                if (newTx.paidFromCash) {
                    requireSufficientCash(newTx.amount)
                } else if (targetAcc != null) {
                    requireSufficientExchange(targetAcc.id, newTx.amount)
                }
            }

            val sanitizedTx = newTx.copy(
                exchangeAccountId = if (!isMonetary || newTx.paidFromCash) 0L else (targetAcc?.id ?: newTx.exchangeAccountId),
                exchangeAccountName = if (!isMonetary || newTx.paidFromCash) "" else (targetAcc?.name ?: newTx.exchangeAccountName),
                linkedExpenseId = oldTx.linkedExpenseId
            )

            // Apply new impact for monetary types
            val shiftId = oldTx.shiftId.takeIf { it > 0L } ?: (dao.getActiveShift().firstOrNull()?.id ?: 0L)
            if (isMonetary) {
                if (sanitizedTx.paidFromCash) {
                    val isSalary = sanitizedTx.type == "صرف راتب" || sanitizedTx.type == "صرفة يومية"
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = if (isSalary) "SALARY_PAID" else "ADVANCE_PAID",
                            amount = sanitizedTx.amount,
                            isIncome = false,
                            title = "${sanitizedTx.type}: ${sanitizedTx.employeeName}",
                            details = sanitizedTx.notes,
                            timestamp = sanitizedTx.timestamp,
                            referenceEmployeeTxId = sanitizedTx.id,
                            referenceExpenseId = oldTx.linkedExpenseId.takeIf { it > 0L }
                        )
                    )
                } else if (targetAcc != null) {
                    dao.updateExchangeBalance(targetAcc.id, -sanitizedTx.amount)
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = targetAcc.id,
                            accountName = targetAcc.name,
                            type = if (oldTx.linkedExpenseId > 0L) "سحب مصروف" else "سحب لموظف",
                            amount = sanitizedTx.amount,
                            isCredit = false,
                            title = "${sanitizedTx.type}: ${sanitizedTx.employeeName}",
                            referenceNumber = "",
                            notes = sanitizedTx.notes,
                            timestamp = sanitizedTx.timestamp,
                            referenceType = if (oldTx.linkedExpenseId > 0L) "EXPENSE" else "EMPLOYEE_TX",
                            referenceId = if (oldTx.linkedExpenseId > 0L) oldTx.linkedExpenseId else sanitizedTx.id
                        )
                    )
                }
            }

            dao.updateEmployeeTransaction(sanitizedTx)

            // Bidirectional cascade update to linked ExpenseRecord
            if (oldTx.linkedExpenseId > 0L) {
                val linkedExp = dao.getExpenseById(oldTx.linkedExpenseId)
                if (linkedExp != null) {
                    dao.updateExpense(
                        linkedExp.copy(
                            amount = sanitizedTx.amount,
                            notes = sanitizedTx.notes,
                            timestamp = sanitizedTx.timestamp,
                            paidFromCash = sanitizedTx.paidFromCash,
                            exchangeAccountId = sanitizedTx.exchangeAccountId,
                            exchangeAccountName = sanitizedTx.exchangeAccountName
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteEmployeeTransaction(txId: Long) {
        runInTransaction {
            val tx = dao.getEmployeeTransactionById(txId) ?: return@runInTransaction
            dao.deleteCashMovementsForEmployeeTx(txId)
            if (tx.linkedExpenseId > 0L) {
                dao.deleteCashMovementsForExpense(tx.linkedExpenseId)
            }
            val wasMonetary = isMonetaryEmployeeTxType(tx.type)
            reverseLinkedTransfersOrFallback(
                refType = "EMPLOYEE_TX",
                refIds = listOf(txId),
                fallbackAccountId = if (!tx.paidFromCash && wasMonetary) tx.exchangeAccountId else 0L,
                fallbackAmount = if (!tx.paidFromCash && wasMonetary) tx.amount else 0.0,
                fallbackWasCredit = false,
                reason = "حذف حركة عامل #${tx.id}"
            )
            if (tx.linkedExpenseId > 0L) {
                reverseLinkedTransfersOrFallback(
                    refType = "EXPENSE",
                    refIds = listOf(tx.linkedExpenseId),
                    reason = "حذف حركة عامل مرتبطة بمصروف #${tx.id}"
                )
                dao.deleteExpense(tx.linkedExpenseId)
            }
            dao.deleteEmployeeTransaction(txId)
        }
    }

    suspend fun recordEmployeeTransaction(
        employeeId: Long,
        employeeName: String,
        type: String,
        amount: Double,
        notes: String,
        paidFromCashDrawer: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        if (amount <= 0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ صحيح أكبر من الصفر.")
        }
        val isMonetaryOutflow = type == "سلفة" || type == "صرف راتب" || type == "مكافأة" || type == "صرفة يومية"
        if (isMonetaryOutflow && !paidFromCashDrawer && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند الصرف بغير الصندوق النقدي.")
        }

        return runInTransaction {
            if (isMonetaryOutflow) {
                if (paidFromCashDrawer) {
                    requireSufficientCash(amount)
                } else if (exchangeAccount != null) {
                    requireSufficientExchange(exchangeAccount.id, amount)
                }
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp

            val tx = EmployeeTransaction(
                employeeId = employeeId,
                employeeName = employeeName,
                type = type,
                amount = amount,
                notes = if (referenceNumber.isNotBlank()) "مرجع الحوالة: $referenceNumber | $notes" else notes,
                timestamp = now,
                shiftId = shiftId,
                paidFromCash = paidFromCashDrawer,
                exchangeAccountId = if (isMonetaryOutflow && !paidFromCashDrawer) (exchangeAccount?.id ?: 0L) else 0L,
                exchangeAccountName = if (isMonetaryOutflow && !paidFromCashDrawer) (exchangeAccount?.name ?: "") else ""
            )
            val id = dao.insertEmployeeTransaction(tx)

            var linkedExpId = 0L
            if (type == "صرفة يومية") {
                val expNotes = buildString {
                    append("صرفة يومية للعامل: $employeeName")
                    if (notes.isNotBlank()) append(" - $notes")
                }
                val exp = ExpenseRecord(
                    category = "صرفة يومية لعامل",
                    amount = amount,
                    notes = expNotes,
                    receiptNumber = referenceNumber,
                    timestamp = now,
                    paidFromCash = paidFromCashDrawer,
                    exchangeAccountId = if (!paidFromCashDrawer) (exchangeAccount?.id ?: 0L) else 0L,
                    exchangeAccountName = if (!paidFromCashDrawer) (exchangeAccount?.name ?: "") else "",
                    shiftId = shiftId,
                    linkedEmployeeTxId = id
                )
                linkedExpId = dao.insertExpense(exp)
                dao.updateEmployeeTransaction(tx.copy(id = id, linkedExpenseId = linkedExpId))
            }

            if (isMonetaryOutflow && paidFromCashDrawer) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = if (type == "صرف راتب" || type == "صرفة يومية") "SALARY_PAID" else "ADVANCE_PAID",
                        amount = amount,
                        isIncome = false,
                        title = "$type للعامل: $employeeName",
                        details = notes,
                        timestamp = now,
                        referenceEmployeeTxId = id,
                        referenceExpenseId = linkedExpId.takeIf { it > 0L }
                    )
                )
            } else if (isMonetaryOutflow && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, -amount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = if (linkedExpId > 0L) "سحب مصروف" else type,
                        amount = amount,
                        isCredit = false,
                        title = "$type للعامل: $employeeName",
                        referenceNumber = referenceNumber,
                        notes = notes,
                        timestamp = now,
                        referenceType = if (linkedExpId > 0L) "EXPENSE" else "EMPLOYEE_TX",
                        referenceId = if (linkedExpId > 0L) linkedExpId else id
                    )
                )
            }
            id
        }
    }

    suspend fun resolveVoucherNumber(input: String, prefix: String = "SND", checkDuplicate: Boolean = true): String {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            if (checkDuplicate && dao.countPaymentsByVoucherNumber(trimmed) > 0) {
                throw IllegalArgumentException("رقم السند ($trimmed) مستخدم مسبقاً! يرجى إدخال رقم آخر أو تركه فارغاً للتوليد التلقائي.")
            }
            return trimmed
        }

        val existingPayments = dao.getAllPaymentVoucherNumbers()
        val existingTransfers = dao.getAllExchangeTransfersDirect().map { it.referenceNumber }
        return nextSequentialCode(existingPayments + existingTransfers, prefix)
    }

    // --- Debts & Collections ---
    suspend fun recordDebtPayment(
        debt: DebtRecord,
        paymentAmount: Double,
        notes: String,
        paymentMethod: String = "نقداً", // "نقداً" or "صرافة/محفظة"
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis(),
        allowBatchVoucherReuse: Boolean = false
    ): Long {
        if (paymentAmount <= 0.0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ دفع صحيح أكبر من الصفر.")
        }
        if (paymentAmount > debt.remainingAmount + 0.01) {
            throw IllegalArgumentException("المبلغ المدفوع (${FormatUtils.formatCurrency(paymentAmount)}) يتجاوز المتبقي من الدين (${FormatUtils.formatCurrency(debt.remainingAmount)})!")
        }
        if (paymentMethod != "نقداً" && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند الدفع عبر الصرافة.")
        }

        return runInTransaction {
            val freshDebt = dao.getDebtById(debt.id)
                ?: throw IllegalArgumentException("سجل الدين المحدد غير موجود!")
            if (paymentAmount > freshDebt.remainingAmount + 0.01) {
                throw IllegalArgumentException("المبلغ المدفوع (${FormatUtils.formatCurrency(paymentAmount)}) يتجاوز المتبقي من الدين (${FormatUtils.formatCurrency(freshDebt.remainingAmount)})!")
            }
            if (freshDebt.type != "CUSTOMER") {
                if (paymentMethod == "نقداً") {
                    requireSufficientCash(paymentAmount)
                } else if (exchangeAccount != null) {
                    requireSufficientExchange(exchangeAccount.id, paymentAmount)
                }
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp
            val finalVoucher = resolveVoucherNumber(referenceNumber, if (freshDebt.type == "CUSTOMER") "SND" else "SRF", checkDuplicate = !allowBatchVoucherReuse)

            val newPaid = freshDebt.paidAmount + paymentAmount
            val newRemaining = (freshDebt.originalAmount - newPaid).coerceAtLeast(0.0)
            val isSettled = newRemaining <= 0.001

            dao.updateDebt(
                freshDebt.copy(
                    paidAmount = newPaid,
                    remainingAmount = newRemaining,
                    isSettled = isSettled
                )
            )

            val paymentNotes = buildString {
                append("سند: #$finalVoucher")
                if (notes.isNotBlank()) append(" | $notes")
            }
            val payment = DebtPayment(
                debtId = freshDebt.id,
                partyName = freshDebt.partyName,
                amount = paymentAmount,
                timestamp = now,
                notes = paymentNotes,
                shiftId = shiftId,
                paymentMethod = paymentMethod,
                exchangeAccountId = if (paymentMethod == "نقداً") 0L else (exchangeAccount?.id ?: 0L),
                voucherNumber = finalVoucher
            )
            val paymentId = dao.insertDebtPayment(payment)

            if (freshDebt.type == "CUSTOMER") {
                if (paymentMethod == "نقداً") {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = "DEBT_COLLECTED",
                            amount = paymentAmount,
                            isIncome = true,
                            title = "تحصيل زلط (سند #$finalVoucher): ${freshDebt.partyName}",
                            details = "المتبقي بعد الدفع: ${newRemaining.toLong()} ريال",
                            timestamp = now
                        )
                    )
                } else if (exchangeAccount != null) {
                    dao.updateExchangeBalance(exchangeAccount.id, paymentAmount)
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = exchangeAccount.id,
                            accountName = exchangeAccount.name,
                            type = "تحصيل دين",
                            amount = paymentAmount,
                            isCredit = true,
                            title = "تحصيل دين عبر الصراف (سند #$finalVoucher): ${freshDebt.partyName}",
                            referenceNumber = finalVoucher,
                            notes = paymentNotes,
                            timestamp = now,
                            referenceType = "DEBT_PAYMENT",
                            referenceId = paymentId
                        )
                    )
                }
            } else {
                // Supplier debt payment
                if (paymentMethod == "نقداً") {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = "SUPPLIER_PAID",
                            amount = paymentAmount,
                            isIncome = false,
                            title = "تسديد دين للمورد (سند #$finalVoucher): ${freshDebt.partyName}",
                            details = "المتبقي للمورد: ${newRemaining.toLong()} ريال",
                            timestamp = now
                        )
                    )
                } else if (exchangeAccount != null) {
                    dao.updateExchangeBalance(exchangeAccount.id, -paymentAmount)
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = exchangeAccount.id,
                            accountName = exchangeAccount.name,
                            type = "تسديد دين مورد",
                            amount = paymentAmount,
                            isCredit = false,
                            title = "تسديد دين للمورد عبر الصراف (سند #$finalVoucher): ${freshDebt.partyName}",
                            referenceNumber = finalVoucher,
                            notes = paymentNotes,
                            timestamp = now,
                            referenceType = "DEBT_PAYMENT",
                            referenceId = paymentId
                        )
                    )
                }
            }

            paymentId
        }
    }

    suspend fun addNewDebt(debt: DebtRecord): Long = dao.insertDebt(debt)
    suspend fun updateDebt(debt: DebtRecord) {
        if (debt.originalAmount <= 0) throw IllegalArgumentException("مبلغ الدين يجب أن يكون أكبر من الصفر.")
        if (debt.originalAmount + 0.01 < debt.paidAmount) {
            throw IllegalStateException("لا يمكن تخفيض قيمة الدين لأقل من المبلغ المدفوع (${FormatUtils.formatCurrency(debt.paidAmount)})!")
        }
        val newRemaining = (debt.originalAmount - debt.paidAmount).coerceAtLeast(0.0)
        dao.updateDebt(debt.copy(remainingAmount = newRemaining, isSettled = newRemaining <= 0.01))
    }

    suspend fun deleteDebt(id: Long) {
        runInTransaction {
            val debt = dao.getDebtById(id) ?: return@runInTransaction
            if (debt.referenceSaleId != null || debt.referencePurchaseId != null) {
                throw IllegalStateException("لا يمكن حذف دين مرتبط بفاتورة مبيعات أو مشتريات؛ عدّل أو احذف الفاتورة الأصلية.")
            }
            if (debt.paidAmount > 0.01 || dao.countPaymentsForDebt(id) > 0) {
                throw IllegalStateException("لا يمكن حذف سجل دين عليه دفعات أو سندات قبض/صرف مسجلة!")
            }
            dao.deleteDebt(id)
            logAudit("حذف سجل دين", "المدير", "تم حذف دين ${debt.partyName} بمبلغ ${FormatUtils.formatCurrency(debt.originalAmount)}")
        }
    }

    // --- Parties Management (Customers & Suppliers) ---
    suspend fun saveParty(
        party: PartyAccount,
        initialDebt: Double = 0.0,
        debtNotes: String = "",
        initialDebtTimestamp: Long = System.currentTimeMillis()
    ): Long {
        val now = initialDebtTimestamp
        val partyId = if (party.id == 0L) {
            dao.insertParty(party.copy(initialBalance = initialDebt))
        } else {
            dao.updateParty(party)
            party.id
        }

        if (party.id == 0L && initialDebt > 0) {
            val isCustomer = party.type == "CUSTOMER"
            val debt = DebtRecord(
                type = if (isCustomer) "CUSTOMER" else "SUPPLIER",
                partyName = party.name,
                phone = party.phone,
                originalAmount = initialDebt,
                paidAmount = 0.0,
                remainingAmount = initialDebt,
                details = debtNotes.ifBlank {
                    if (isCustomer) "رصيد افتتاحي (عليه زلط سابقة عند فتح الحساب)"
                    else "رصيد افتتاحي (عليا زلط للمورد عند فتح الحساب)"
                },
                dueDate = now + if (isCustomer) 86400000L * 15 else 86400000L * 30,
                createdAt = now,
                isSettled = false
            )
            dao.insertDebt(debt)
        }
        return partyId
    }

    suspend fun deleteParty(id: Long): DeleteEntityResult {
        return runInTransaction {
            val party = dao.getPartyById(id) ?: return@runInTransaction DeleteEntityResult.NOT_FOUND
            val hasLinks = dao.countDebtsByPartyName(party.name) > 0 ||
                dao.countPaymentsByPartyName(party.name) > 0 ||
                dao.countSalesByCustomerName(party.name) > 0 ||
                dao.countPurchasesBySupplierName(party.name) > 0 ||
                dao.countReturnsByPartyName(party.name) > 0
            if (hasLinks) {
                dao.updateParty(party.copy(isArchived = true))
                logAudit("أرشفة حساب طرف", "المدير", "تم أرشفة (${party.name}) للحفاظ على الفواتير والديون والسندات المرتبطة به")
                DeleteEntityResult.ARCHIVED
            } else {
                dao.deleteParty(id)
                logAudit("حذف حساب طرف", "المدير", "تم حذف (${party.name})")
                DeleteEntityResult.DELETED
            }
        }
    }

    suspend fun recordPartyPayment(
        partyName: String,
        partyType: String,
        totalPayment: Double,
        notes: String,
        paymentMethod: String = "نقداً",
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (totalPayment <= 0) return false
        if (paymentMethod != "نقداً" && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
            throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند الدفع عبر الصرافة.")
        }
        return runInTransaction {
            if (partyType != "CUSTOMER") {
                if (paymentMethod == "نقداً") requireSufficientCash(totalPayment)
                else if (exchangeAccount != null) requireSufficientExchange(exchangeAccount.id, totalPayment)
            }
            val allDebtsList = dao.getAllDebtsDirect()
            val partyDebts = allDebtsList.filter {
                it.partyName.trim().equals(partyName.trim(), ignoreCase = true) &&
                    it.type == partyType &&
                    !it.isSettled
            }.sortedBy { it.createdAt }

            val finalVoucher = resolveVoucherNumber(referenceNumber, if (partyType == "CUSTOMER") "SND" else "SRF")
            var remainingToApply = totalPayment
            for (debt in partyDebts) {
                if (remainingToApply <= 0.001) break
                val paymentForThisDebt = minOf(remainingToApply, debt.remainingAmount)
                recordDebtPayment(
                    debt = debt,
                    paymentAmount = paymentForThisDebt,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    exchangeAccount = exchangeAccount,
                    referenceNumber = finalVoucher,
                    timestamp = timestamp,
                    allowBatchVoucherReuse = true
                )
                remainingToApply -= paymentForThisDebt
            }
            true
        }
    }

    suspend fun recordCollectionReceipt(
        payerName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumberInput: String,
        paymentMethod: String, // "نقداً" or "صرافة/محفظة"
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): CollectionReceiptResult {
        require(amount > 0.0) { "يرجى إدخال مبلغ التحصيل" }
        require(paymentMethod == "نقداً" || exchangeAccount != null) { "اختر حساب الصرافة أو المحفظة" }

        return runInTransaction {
            val now = timestamp
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val finalVoucher = resolveVoucherNumber(voucherNumberInput, "SND")
            val rawPayer = (party?.name ?: payerName).trim()
            val finalPayer = rawPayer.ifBlank { "تحصيل نقدي عام" }

            var appliedToDebt = 0.0
            var remainingToCollect = amount

            if (rawPayer.isNotBlank()) {
                val allDebtsList = dao.getAllDebtsDirect()
                val matchingDebts = allDebtsList.filter {
                    it.type == "CUSTOMER" &&
                        !it.isSettled &&
                        it.partyName.trim().equals(rawPayer, ignoreCase = true)
                }.sortedBy { it.createdAt }

                for (debt in matchingDebts) {
                    if (remainingToCollect <= 0.001) break
                    val applyNow = minOf(remainingToCollect, debt.remainingAmount)
                    if (applyNow > 0.001) {
                        recordDebtPayment(
                            debt = debt,
                            paymentAmount = applyNow,
                            notes = notes.ifBlank { "تحصيل زلط / سند قبض" },
                            paymentMethod = paymentMethod,
                            exchangeAccount = exchangeAccount,
                            referenceNumber = finalVoucher,
                            timestamp = now,
                            allowBatchVoucherReuse = true
                        )
                        appliedToDebt += applyNow
                        remainingToCollect -= applyNow
                    }
                }
            }

            if (remainingToCollect > 0.001) {
                val receiptNote = buildString {
                    append("سند: #$finalVoucher")
                    if (notes.isNotBlank()) append(" | $notes")
                }
                val paymentId = dao.insertDebtPayment(
                    DebtPayment(
                        debtId = 0L,
                        partyName = finalPayer,
                        amount = remainingToCollect,
                        timestamp = now,
                        notes = receiptNote,
                        shiftId = shiftId,
                        paymentMethod = paymentMethod,
                        exchangeAccountId = exchangeAccount?.id ?: 0L,
                        voucherNumber = finalVoucher
                    )
                )

                if (paymentMethod == "نقداً") {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = "DEBT_COLLECTED",
                            amount = remainingToCollect,
                            isIncome = true,
                            title = "تحصيل زلط (سند #$finalVoucher): $finalPayer",
                            details = notes.ifBlank { "سند قبض مباشر للصندوق" },
                            timestamp = now
                        )
                    )
                } else if (exchangeAccount != null) {
                    dao.updateExchangeBalance(exchangeAccount.id, remainingToCollect)
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = exchangeAccount.id,
                            accountName = exchangeAccount.name,
                            type = "سند قبض",
                            amount = remainingToCollect,
                            isCredit = true,
                            title = "تحصيل زلط عبر الصراف (سند #$finalVoucher): $finalPayer",
                            referenceNumber = finalVoucher,
                            notes = notes.ifBlank { "سند قبض مباشر" },
                            timestamp = now,
                            referenceType = "DEBT_PAYMENT",
                            referenceId = paymentId
                        )
                    )
                }
            }

            val updatedDebtsList = dao.getAllDebtsDirect()
            val remainingCustomerDebt = if (rawPayer.isNotBlank()) {
                updatedDebtsList.filter {
                    it.type == "CUSTOMER" &&
                        !it.isSettled &&
                        it.partyName.trim().equals(rawPayer, ignoreCase = true)
                }.sumOf { it.remainingAmount }
            } else 0.0

            logAudit(
                action = "تحصيل زلط (سند قبض)",
                details = "سند رقم #$finalVoucher | الجهة: $finalPayer | المبلغ: ${FormatUtils.formatCurrency(amount)} | طريقة القبض: $paymentMethod"
            )

            CollectionReceiptResult(
                voucherNumber = finalVoucher,
                payerName = finalPayer,
                amount = amount,
                paymentMethod = paymentMethod,
                exchangeAccountName = exchangeAccount?.name ?: "",
                appliedToDebtAmount = appliedToDebt,
                directCollectionAmount = remainingToCollect.coerceAtLeast(0.0),
                remainingCustomerDebt = remainingCustomerDebt,
                notes = notes,
                timestamp = now
            )
        }
    }

    suspend fun recordSupplierPaymentReceipt(
        supplierName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumberInput: String,
        paymentMethod: String, // "نقداً" or "صرافة/محفظة"
        exchangeAccount: ExchangeAccount? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): SupplierPaymentReceiptResult {
        require(amount > 0.0) { "يرجى إدخال مبلغ السداد" }
        require(paymentMethod == "نقداً" || exchangeAccount != null) { "اختر حساب الصرافة أو المحفظة" }

        return runInTransaction {
            if (paymentMethod == "نقداً") {
                requireSufficientCash(amount)
            } else if (exchangeAccount != null) {
                requireSufficientExchange(exchangeAccount.id, amount)
            }

            val now = timestamp
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val finalVoucher = resolveVoucherNumber(voucherNumberInput, "SRF")
            val rawSupplier = (party?.name ?: supplierName).trim()
            val finalSupplier = rawSupplier.ifBlank { "مورد عام" }

            var appliedToDebt = 0.0
            var remainingToPay = amount

            if (rawSupplier.isNotBlank()) {
                val allDebtsList = dao.getAllDebtsDirect()
                val matchingDebts = allDebtsList.filter {
                    it.type == "SUPPLIER" &&
                        !it.isSettled &&
                        it.partyName.trim().equals(rawSupplier, ignoreCase = true)
                }.sortedBy { it.createdAt }

                for (debt in matchingDebts) {
                    if (remainingToPay <= 0.001) break
                    val applyNow = minOf(remainingToPay, debt.remainingAmount)
                    if (applyNow > 0.001) {
                        recordDebtPayment(
                            debt = debt,
                            paymentAmount = applyNow,
                            notes = notes.ifBlank { "تسديد لمورد / سند صرف" },
                            paymentMethod = paymentMethod,
                            exchangeAccount = exchangeAccount,
                            referenceNumber = finalVoucher,
                            timestamp = now,
                            allowBatchVoucherReuse = true
                        )
                        appliedToDebt += applyNow
                        remainingToPay -= applyNow
                    }
                }
            }

            if (remainingToPay > 0.001) {
                val receiptNote = buildString {
                    append("سند صرف لمورد: #$finalVoucher")
                    if (notes.isNotBlank()) append(" | $notes")
                }
                val paymentId = dao.insertDebtPayment(
                    DebtPayment(
                        debtId = 0L,
                        partyName = finalSupplier,
                        amount = remainingToPay,
                        timestamp = now,
                        notes = receiptNote,
                        shiftId = shiftId,
                        paymentMethod = paymentMethod,
                        exchangeAccountId = exchangeAccount?.id ?: 0L,
                        voucherNumber = finalVoucher
                    )
                )

                if (paymentMethod == "نقداً") {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = "SUPPLIER_PAID",
                            amount = remainingToPay,
                            isIncome = false,
                            title = "تسديد لمورد (سند #$finalVoucher): $finalSupplier",
                            details = notes.ifBlank { "سند صرف نقدي مباشر لمورد" },
                            timestamp = now
                        )
                    )
                } else if (exchangeAccount != null) {
                    dao.updateExchangeBalance(exchangeAccount.id, -remainingToPay)
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = exchangeAccount.id,
                            accountName = exchangeAccount.name,
                            type = "سند صرف لمورد",
                            amount = remainingToPay,
                            isCredit = false,
                            title = "تسديد لمورد عبر الصراف (سند #$finalVoucher): $finalSupplier",
                            referenceNumber = finalVoucher,
                            notes = notes.ifBlank { "سند صرف مباشر لمورد" },
                            timestamp = now,
                            referenceType = "DEBT_PAYMENT",
                            referenceId = paymentId
                        )
                    )
                }
            }

            val updatedDebtsList = dao.getAllDebtsDirect()
            val remainingSupplierDebt = if (rawSupplier.isNotBlank()) {
                updatedDebtsList.filter {
                    it.type == "SUPPLIER" &&
                        !it.isSettled &&
                        it.partyName.trim().equals(rawSupplier, ignoreCase = true)
                }.sumOf { it.remainingAmount }
            } else 0.0

            logAudit(
                action = "تسديد لمورد (سند صرف)",
                details = "سند رقم #$finalVoucher | المورد: $finalSupplier | المبلغ: ${FormatUtils.formatCurrency(amount)} | طريقة الدفع: $paymentMethod"
            )

            SupplierPaymentReceiptResult(
                voucherNumber = finalVoucher,
                supplierName = finalSupplier,
                amount = amount,
                paymentMethod = paymentMethod,
                exchangeAccountName = exchangeAccount?.name ?: "",
                appliedToDebtAmount = appliedToDebt,
                directPaymentAmount = remainingToPay.coerceAtLeast(0.0),
                remainingSupplierDebt = remainingSupplierDebt,
                notes = notes,
                timestamp = now
            )
        }
    }

    // --- Exchange Accounts & Wallets Management ---
    suspend fun saveExchangeAccount(account: ExchangeAccount): Long {
        if (account.balance < 0.0) {
            throw IllegalArgumentException("رصيد حساب الصراف لا يمكن أن يكون سالباً.")
        }
        return if (account.id == 0L) {
            dao.insertExchangeAccount(account)
        } else {
            dao.updateExchangeAccount(account)
            account.id
        }
    }

    suspend fun deleteExchangeAccount(id: Long): DeleteEntityResult {
        return runInTransaction {
            val acc = dao.getExchangeAccountById(id) ?: return@runInTransaction DeleteEntityResult.NOT_FOUND
            val hasLinks = dao.countTransfersByAccount(id) > 0 ||
                dao.countSalesByAccount(id) > 0 ||
                dao.countPurchasesByAccount(id) > 0 ||
                dao.countExpensesByAccount(id) > 0 ||
                dao.countEmployeeTxByAccount(id) > 0 ||
                dao.countDebtPaymentsByAccount(id) > 0
            if (hasLinks) {
                dao.updateExchangeAccount(acc.copy(isArchived = true))
                logAudit("أرشفة حساب صراف", "المدير", "تم أرشفة حساب (${acc.name}) للحفاظ على الحركات والتحويلات المرتبطة به")
                DeleteEntityResult.ARCHIVED
            } else {
                dao.deleteExchangeAccount(id)
                logAudit("حذف حساب صراف", "المدير", "تم حذف حساب (${acc.name})")
                DeleteEntityResult.DELETED
            }
        }
    }

    suspend fun transferBetweenCashAndExchange(
        accountId: Long,
        amount: Double,
        fromCashToExchange: Boolean,
        notes: String,
        referenceNumber: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (amount <= 0.0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ تحويل صحيح أكبر من الصفر.")
        }
        runInTransaction {
            val account = dao.getExchangeAccountById(accountId)
                ?: throw IllegalArgumentException("حساب الصراف المحدد غير موجود!")
            if (fromCashToExchange) {
                requireSufficientCash(amount)
            } else {
                requireSufficientExchange(accountId, amount)
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp

            val fullNotes = if (referenceNumber.isNotBlank()) "رقم السند/الحوالة: $referenceNumber | $notes" else notes

            if (fromCashToExchange) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "TRANSFER_TO_EXCHANGE",
                        amount = amount,
                        isIncome = false,
                        title = "إيداع نقدي لحساب الصراف: ${account.name}",
                        details = fullNotes,
                        timestamp = now
                    )
                )
                dao.updateExchangeBalance(accountId, amount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = accountId,
                        accountName = account.name,
                        type = "إيداع من الكاش",
                        amount = amount,
                        isCredit = true,
                        title = "إيداع نقدي من الصندوق",
                        referenceNumber = referenceNumber,
                        notes = fullNotes,
                        timestamp = now,
                        referenceType = "MANUAL_TRANSFER",
                        referenceId = accountId
                    )
                )
            } else {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "CAPITAL_DEPOSIT",
                        amount = amount,
                        isIncome = true,
                        title = "سحب كاش من الصراف: ${account.name}",
                        details = fullNotes,
                        timestamp = now
                    )
                )
                dao.updateExchangeBalance(accountId, -amount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = accountId,
                        accountName = account.name,
                        type = "سحب للكاش",
                        amount = amount,
                        isCredit = false,
                        title = "سحب نقدي إلى الصندوق",
                        referenceNumber = referenceNumber,
                        notes = fullNotes,
                        timestamp = now,
                        referenceType = "MANUAL_TRANSFER",
                        referenceId = accountId
                    )
                )
            }
        }
    }

    // --- Shifts & Drawer ---
    suspend fun openShift(cashierName: String, openingBalance: Double, notes: String): Long {
        if (openingBalance < 0.0) {
            throw IllegalArgumentException("الرصيد الافتتاحي للوردية لا يمكن أن يكون سالباً.")
        }
        val now = System.currentTimeMillis()
        val shift = CashDrawerShift(
            openedAt = now,
            closedAt = null,
            openingBalance = openingBalance,
            closingBalanceActual = null,
            isOpen = true,
            cashierName = cashierName.ifBlank { "الكاشير" },
            notes = notes
        )
        val shiftId = dao.insertShift(shift)

        dao.insertCashMovement(
            CashDrawerMovement(
                shiftId = shiftId,
                type = "CAPITAL_DEPOSIT",
                amount = openingBalance,
                isIncome = true,
                title = "رصيد افتتاح الوردية",
                details = notes,
                timestamp = now
            )
        )
        return shiftId
    }

    suspend fun closeShift(shift: CashDrawerShift, actualCashInDrawer: Double, notes: String) {
        val now = System.currentTimeMillis()
        val updated = shift.copy(
            closedAt = now,
            closingBalanceActual = actualCashInDrawer,
            isOpen = false,
            notes = if (notes.isNotBlank()) "${shift.notes} | إغلاق: $notes" else shift.notes
        )
        dao.updateShift(updated)
    }

    suspend fun recordOwnerWithdrawal(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis()) {
        if (amount <= 0.0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ سحب صحيح أكبر من الصفر.")
        }
        runInTransaction {
            requireSufficientCash(amount)
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = timestamp

            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "WITHDRAWAL",
                    amount = amount,
                    isIncome = false,
                    title = "سحب صاحب المحل",
                    details = reason.ifBlank { "سحب شخصي" },
                    timestamp = now
                )
            )
        }
    }

    suspend fun recordManualDeposit(amount: Double, reason: String, timestamp: Long = System.currentTimeMillis()) {
        if (amount <= 0.0) {
            throw IllegalArgumentException("يرجى إدخال مبلغ إيداع صحيح أكبر من الصفر.")
        }
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = timestamp

        dao.insertCashMovement(
            CashDrawerMovement(
                shiftId = shiftId,
                type = "CAPITAL_DEPOSIT",
                amount = amount,
                isIncome = true,
                title = "إيداع نقدي بالصندوق",
                details = reason.ifBlank { "زيادة رأس مال / سيولة" },
                timestamp = now
            )
        )
    }

    // --- Reset / Clear Data ---
    suspend fun clearTransactionsOnly() {
        runInTransaction {
            dao.deleteAllReturns()
            dao.deleteAllSales()
            dao.deleteAllPurchases()
            dao.deleteAllExpenses()
            dao.deleteAllEmployeeTransactions()
            dao.deleteAllDebts()
            dao.deleteAllDebtPayments()
            dao.deleteAllCashMovements()
            dao.deleteAllShifts()
            dao.deleteAllExchangeTransfers()
        }
    }

    suspend fun clearEverythingCompletely(clearConfigDictionaries: Boolean = false) {
        runInTransaction {
            dao.deleteAllReturns()
            dao.deleteAllSales()
            dao.deleteAllPurchases()
            dao.deleteAllExpenses()
            dao.deleteAllEmployeeTransactions()
            dao.deleteAllDebts()
            dao.deleteAllDebtPayments()
            dao.deleteAllCashMovements()
            dao.deleteAllShifts()
            dao.deleteAllExchangeTransfers()
            dao.deleteAllProducts()
            dao.deleteAllEmployees()
            dao.deleteAllExchangeAccounts()
            dao.deleteAllParties()
            dao.deleteAllAuditLogs()
            if (clearConfigDictionaries) {
                dao.deleteAllConfigItems()
            }
        }
    }

    suspend fun executeStoreFullAudit(
        actualDrawerCash: Double,
        exchangeBalances: Map<Long, Double>,
        resetTransactions: Boolean,
        notes: String
    ) {
        if (actualDrawerCash < 0.0) {
            throw IllegalArgumentException("رصيد الصندوق الفعلي لا يمكن أن يكون سالباً.")
        }
        if (exchangeBalances.values.any { it < 0.0 }) {
            throw IllegalArgumentException("أرصدة الصرافين لا يمكن أن تكون سالبة.")
        }

        runInTransaction {
            val now = System.currentTimeMillis()
            if (resetTransactions) {
                dao.deleteAllReturns()
                dao.deleteAllSales()
                dao.deleteAllPurchases()
                dao.deleteAllExpenses()
                dao.deleteAllEmployeeTransactions()
                dao.deleteAllDebts()
                dao.deleteAllDebtPayments()
                dao.deleteAllCashMovements()
                dao.deleteAllShifts()
                dao.deleteAllExchangeTransfers()
            } else {
                val currentActiveShift = dao.getActiveShift().firstOrNull()
                if (currentActiveShift != null) {
                    closeShift(currentActiveShift, actualDrawerCash, "إغلاق قبل اعتماد الجرد")
                }
            }

            // Open new active shift with audited actual cash
            val newShiftId = dao.insertShift(
                CashDrawerShift(
                    openedAt = now,
                    closedAt = null,
                    openingBalance = actualDrawerCash,
                    closingBalanceActual = null,
                    isOpen = true,
                    cashierName = "صاحب المحل",
                    notes = "اعتماد جرد المحل: ${notes.ifBlank { "بداية الأرصدة الفعلية" }}"
                )
            )

            if (resetTransactions) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = newShiftId,
                        type = "CAPITAL_DEPOSIT",
                        amount = actualDrawerCash,
                        isIncome = true,
                        title = "رصيد الصندوق المعتمد من الجرد",
                        details = notes,
                        timestamp = now
                    )
                )
            } else {
                val currentCash = dao.getAvailableCashBalance()
                val diff = actualDrawerCash - currentCash
                if (kotlin.math.abs(diff) > 0.01) {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = newShiftId,
                            type = if (diff > 0) "CAPITAL_DEPOSIT" else "WITHDRAWAL",
                            amount = kotlin.math.abs(diff),
                            isIncome = diff > 0,
                            title = "تسوية رصيد الصندوق بعد الجرد",
                            details = notes,
                            timestamp = now
                        )
                    )
                }
            }

            // Update all exchange account balances
            exchangeBalances.forEach { (accountId, balance) ->
                val acc = dao.getExchangeAccountById(accountId)
                val oldBal = acc?.balance ?: 0.0
                dao.setExchangeBalance(accountId, balance)
                val delta = if (resetTransactions) balance else (balance - oldBal)
                if (resetTransactions || kotlin.math.abs(delta) > 0.01) {
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = accountId,
                            accountName = acc?.name ?: "حساب صراف",
                            type = "تعديل رصيد",
                            amount = kotlin.math.abs(delta),
                            isCredit = delta >= 0.0,
                            title = if (resetTransactions) "رصيد معتمد من جرد المحل" else "تسوية رصيد صراف بالجرد",
                            referenceNumber = "AUDIT",
                            notes = notes,
                            timestamp = now,
                            referenceType = "AUDIT",
                            referenceId = accountId
                        )
                    )
                }
            }
        }
    }

    // ====================================================================
    // Opening Balances (الأرصدة الافتتاحية الشاملة للمحل بدون أي خصم من الصندوق)
    // ====================================================================

    suspend fun setOpeningCashBalance(
        amount: Double,
        replaceExistingBalance: Boolean,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (replaceExistingBalance) {
            if (amount < 0.0) throw IllegalArgumentException("رصيد الصندوق الفعلي لا يمكن أن يكون سالباً.")
        } else {
            if (amount <= 0.0) throw IllegalArgumentException("يرجى إدخال مبلغ صحيح أكبر من الصفر.")
        }

        runInTransaction {
            val currentCash = dao.getAvailableCashBalance()
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = if (activeShiftObj != null) {
                activeShiftObj.id
            } else {
                dao.insertShift(
                    CashDrawerShift(
                        openedAt = timestamp,
                        closedAt = null,
                        openingBalance = if (replaceExistingBalance) amount else (currentCash + amount).coerceAtLeast(0.0),
                        closingBalanceActual = null,
                        isOpen = true,
                        cashierName = "صاحب المحل",
                        notes = notes.ifBlank { "فتح وردية — الرصيد الافتتاحي للمحل" }
                    )
                )
            }

            if (!replaceExistingBalance) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "CAPITAL_DEPOSIT",
                        amount = amount,
                        isIncome = true,
                        title = "رصيد افتتاحي للصندوق",
                        details = notes.ifBlank { "نقدية موجودة بالصندوق عند بدء النظام" },
                        timestamp = timestamp
                    )
                )
                logAudit("رصيد افتتاحي للصندوق", "المدير", "إضافة رصيد افتتاحي نقدي: ${FormatUtils.formatCurrency(amount)}")
            } else {
                val diff = amount - currentCash
                if (kotlin.math.abs(diff) > 0.01) {
                    dao.insertCashMovement(
                        CashDrawerMovement(
                            shiftId = shiftId,
                            type = if (diff > 0.0) "CAPITAL_DEPOSIT" else "WITHDRAWAL",
                            amount = kotlin.math.abs(diff),
                            isIncome = diff > 0.0,
                            title = "اعتماد الرصيد الافتتاحي للصندوق",
                            details = notes.ifBlank { "اعتماد رصيد الصندوق الفعلي: ${FormatUtils.formatCurrency(amount)}" },
                            timestamp = timestamp
                        )
                    )
                }
                logAudit("رصيد افتتاحي للصندوق", "المدير", "اعتماد الرصيد الافتتاحي الفعلي للصندوق: ${FormatUtils.formatCurrency(amount)}")
            }
        }
    }

    suspend fun saveOpeningExchangeAccount(
        accountId: Long = 0L,
        name: String,
        type: String,
        accountNumber: String = "",
        phone: String = "",
        openingBalance: Double,
        replaceExistingBalance: Boolean = false,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw IllegalArgumentException("يرجى إدخال اسم الصراف أو المحفظة.")
        if (openingBalance < 0.0) throw IllegalArgumentException("رصيد الصراف أو المحفظة لا يمكن أن يكون سالباً.")

        return runInTransaction {
            val allAccounts = dao.getAllExchangeAccountsIncludingArchivedDirect()
            val existing = if (accountId > 0L) {
                allAccounts.firstOrNull { it.id == accountId }
            } else {
                allAccounts.firstOrNull { !it.isArchived && it.name.trim().equals(trimmedName, ignoreCase = true) }
                    ?: allAccounts.firstOrNull { it.name.trim().equals(trimmedName, ignoreCase = true) }
            }

            if (type.isNotBlank()) {
                addConfigItem(StoreConfigTypes.ACCOUNT_TYPE, type.trim())
            }

            if (existing != null) {
                val oldBalance = existing.balance
                val targetBalance = if (replaceExistingBalance) openingBalance else (oldBalance + openingBalance)
                val delta = targetBalance - oldBalance
                dao.updateExchangeAccount(
                    existing.copy(
                        name = trimmedName,
                        type = type.trim().ifBlank { existing.type },
                        accountNumber = accountNumber.trim().ifBlank { existing.accountNumber },
                        phone = phone.trim().ifBlank { existing.phone },
                        balance = targetBalance,
                        notes = notes.trim().ifBlank { existing.notes },
                        isArchived = false
                    )
                )
                if (kotlin.math.abs(delta) > 0.01) {
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = existing.id,
                            accountName = trimmedName,
                            type = "رصيد افتتاحي",
                            amount = kotlin.math.abs(delta),
                            isCredit = delta >= 0.0,
                            title = if (replaceExistingBalance) "اعتماد الرصيد الافتتاحي: $trimmedName" else "إضافة رصيد افتتاحي: $trimmedName",
                            referenceNumber = "OPENING",
                            notes = notes.ifBlank { "رصيد افتتاحي بدون خصم من الصندوق" },
                            timestamp = timestamp,
                            referenceType = "OPENING_BALANCE",
                            referenceId = existing.id
                        )
                    )
                }
                logAudit("رصيد افتتاحي لصراف/محفظة", "المدير", "تحديث رصيد ($trimmedName) إلى ${FormatUtils.formatCurrency(targetBalance)} كرصيد افتتاحي")
                existing.id
            } else {
                val newId = dao.insertExchangeAccount(
                    ExchangeAccount(
                        name = trimmedName,
                        type = type.trim().ifBlank { "صراف" },
                        accountNumber = accountNumber.trim(),
                        phone = phone.trim(),
                        balance = openingBalance,
                        notes = notes.trim().ifBlank { "رصيد افتتاحي قبل بدء النظام" },
                        createdAt = timestamp,
                        isArchived = false
                    )
                )
                if (openingBalance > 0.01) {
                    dao.insertExchangeTransfer(
                        ExchangeTransfer(
                            accountId = newId,
                            accountName = trimmedName,
                            type = "رصيد افتتاحي",
                            amount = openingBalance,
                            isCredit = true,
                            title = "رصيد افتتاحي للحساب: $trimmedName",
                            referenceNumber = "OPENING",
                            notes = notes.ifBlank { "رصيد افتتاحي بدون خصم من الصندوق" },
                            timestamp = timestamp,
                            referenceType = "OPENING_BALANCE",
                            referenceId = newId
                        )
                    )
                }
                logAudit("رصيد افتتاحي لصراف/محفظة", "المدير", "إضافة حساب ($trimmedName) برصيد افتتاحي ${FormatUtils.formatCurrency(openingBalance)}")
                newId
            }
        }
    }

    suspend fun addOpeningInventoryItems(
        items: List<CartPurchaseItem>,
        timestamp: Long = System.currentTimeMillis()
    ): Int {
        if (items.isEmpty()) throw IllegalArgumentException("يرجى إضافة صنف واحد على الأقل للمخزن.")
        items.forEach { item ->
            if (item.category.trim().isBlank()) throw IllegalArgumentException("يرجى تحديد القسم / النوع لكل صنف.")
            if (item.totalPieces <= 0) throw IllegalArgumentException("الكمية الإجمالية بالقطع يجب أن تكون أكبر من الصفر.")
            if (item.buyPricePerUnit < 0.0) throw IllegalArgumentException("سعر التكلفة لا يمكن أن يكون سالباً.")
            if (item.sellPricePerUnit <= 0.0) throw IllegalArgumentException("يرجى إدخال سعر بيع صحيح أكبر من الصفر.")
        }

        return runInTransaction {
            items.forEach { item ->
                val cleanCat = item.category.trim()
                val cleanSize = item.size.trim().ifBlank { ClothingSizes.BIG }
                val cleanModel = item.modelName.trim().ifBlank { "$cleanCat $cleanSize" }
                addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, cleanCat)
                addConfigItem(StoreConfigTypes.PRODUCT_SIZE, cleanSize)

                val totalPieces = item.totalPieces
                val unitBuyPrice = item.lineCost / totalPieces
                val unitSellPrice = (item.sellPricePerUnit * item.unitCount) / totalPieces

                dao.insertProduct(
                    ProductItem(
                        category = cleanCat,
                        modelName = cleanModel,
                        color = item.color.trim().ifBlank { "مشكل" },
                        size = cleanSize,
                        quantity = totalPieces,
                        buyPrice = unitBuyPrice,
                        sellPrice = unitSellPrice,
                        unit = item.unit,
                        barcode = item.barcode.trim(),
                        colorsCount = item.colorsCount,
                        colorDetails = item.colorDetails,
                        notes = item.notes.trim().ifBlank { "رصيد افتتاحي للمخزن" },
                        invoiceNumber = "OPENING",
                        lastSoldTimestamp = timestamp
                    )
                )
            }
            val totalCost = items.sumOf { it.lineCost }
            val totalPiecesAll = items.sumOf { it.totalPieces }
            logAudit(
                action = "رصيد افتتاحي للمخزن",
                details = "إضافة ${items.size} أصناف ($totalPiecesAll قطعة) بقيمة تكلفة ${FormatUtils.formatCurrency(totalCost)} دون أي خصم من الصندوق أو قيود شراء"
            )
            items.size
        }
    }

    suspend fun recordPartyOpeningBalance(
        type: String,
        name: String,
        phone: String = "",
        address: String = "",
        amount: Double,
        isNormalDebtDirection: Boolean = true,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        if (type != "CUSTOMER" && type != "SUPPLIER") {
            throw IllegalArgumentException("نوع الطرف غير صالح.")
        }
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw IllegalArgumentException("يرجى إدخال الاسم الكامل.")
        if (amount < 0.0) throw IllegalArgumentException("المبلغ لا يمكن أن يكون سالباً.")

        return runInTransaction {
            val allParties = dao.getAllPartiesIncludingArchivedDirect()
            val existing = allParties.firstOrNull {
                it.type == type && !it.isArchived && it.name.trim().equals(trimmedName, ignoreCase = true)
            } ?: allParties.firstOrNull {
                it.type == type && it.name.trim().equals(trimmedName, ignoreCase = true)
            }

            val defaultDebtDetails = when {
                type == "CUSTOMER" && isNormalDebtDirection -> "رصيد افتتاحي (عليه للمحل قبل النظام)"
                type == "CUSTOMER" && !isNormalDebtDirection -> "رصيد افتتاحي (له فائض عند المحل)"
                type == "SUPPLIER" && isNormalDebtDirection -> "رصيد افتتاحي (عليا للمورد قبل النظام)"
                else -> "رصيد افتتاحي (لنا فائض عند المورد)"
            }

            val partyId = if (existing != null) {
                val partyDebts = dao.getAllDebtsDirect().filter {
                    it.type == type && it.partyName.trim().equals(existing.name.trim(), ignoreCase = true)
                }
                // Materialize legacy positive initialBalance if it didn't have a DebtRecord before mutating
                if (existing.initialBalance > 0.01) {
                    val hasMatchingDebt = partyDebts.any { d ->
                        d.referenceSaleId == null && d.referencePurchaseId == null &&
                            kotlin.math.abs(d.originalAmount - existing.initialBalance) <= 0.01
                    }
                    if (!hasMatchingDebt) {
                        dao.insertDebt(
                            DebtRecord(
                                type = type,
                                partyName = existing.name,
                                phone = existing.phone,
                                originalAmount = existing.initialBalance,
                                paidAmount = 0.0,
                                remainingAmount = existing.initialBalance,
                                details = "رصيد افتتاحي سابق",
                                dueDate = existing.createdAt + if (type == "CUSTOMER") 86400000L * 15 else 86400000L * 30,
                                createdAt = existing.createdAt,
                                isSettled = false
                            )
                        )
                    }
                }

                val updatedInitialBalance = if (amount > 0.01 && !isNormalDebtDirection) {
                    if (existing.initialBalance < 0.0) existing.initialBalance - amount else -amount
                } else {
                    if (existing.initialBalance > 0.01) existing.initialBalance else existing.initialBalance
                }

                dao.updateParty(
                    existing.copy(
                        phone = phone.trim().ifBlank { existing.phone },
                        address = address.trim().ifBlank { existing.address },
                        notes = notes.trim().ifBlank { existing.notes },
                        initialBalance = updatedInitialBalance,
                        isArchived = false
                    )
                )

                if (amount > 0.01 && isNormalDebtDirection) {
                    dao.insertDebt(
                        DebtRecord(
                            type = type,
                            partyName = existing.name,
                            phone = phone.trim().ifBlank { existing.phone },
                            originalAmount = amount,
                            paidAmount = 0.0,
                            remainingAmount = amount,
                            details = notes.trim().ifBlank { defaultDebtDetails },
                            dueDate = timestamp + if (type == "CUSTOMER") 86400000L * 15 else 86400000L * 30,
                            createdAt = timestamp,
                            isSettled = false
                        )
                    )
                }
                existing.id
            } else {
                val initBal = when {
                    amount <= 0.01 -> 0.0
                    isNormalDebtDirection -> amount
                    else -> -amount
                }
                val newId = dao.insertParty(
                    PartyAccount(
                        type = type,
                        name = trimmedName,
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim().ifBlank { "حساب برصيد افتتاحي" },
                        initialBalance = initBal,
                        createdAt = timestamp,
                        isArchived = false
                    )
                )
                if (amount > 0.01 && isNormalDebtDirection) {
                    dao.insertDebt(
                        DebtRecord(
                            type = type,
                            partyName = trimmedName,
                            phone = phone.trim(),
                            originalAmount = amount,
                            paidAmount = 0.0,
                            remainingAmount = amount,
                            details = notes.trim().ifBlank { defaultDebtDetails },
                            dueDate = timestamp + if (type == "CUSTOMER") 86400000L * 15 else 86400000L * 30,
                            createdAt = timestamp,
                            isSettled = false
                        )
                    )
                }
                newId
            }

            val roleAr = if (type == "CUSTOMER") "عميل" else "مورد"
            logAudit(
                action = "رصيد افتتاحي لـ$roleAr",
                details = "$trimmedName | المبلغ: ${FormatUtils.formatCurrency(amount)} | الحالة: $defaultDebtDetails"
            )
            partyId
        }
    }

    suspend fun recordEmployeeOpeningBalance(
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
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) throw IllegalArgumentException("يرجى إدخال اسم العامل.")
        if (baseSalary < 0.0) throw IllegalArgumentException("الراتب الأساسي لا يمكن أن يكون سالباً.")
        if (commissionPercent < 0.0) throw IllegalArgumentException("نسبة العمولة لا يمكن أن تكون سالبة.")
        if (openingAmount < 0.0) throw IllegalArgumentException("المبلغ الافتتاحي لا يمكن أن يكون سالباً.")

        return runInTransaction {
            val allEmps = dao.getAllEmployeesIncludingArchivedDirect()
            val existing = if (employeeId > 0L) {
                allEmps.firstOrNull { it.id == employeeId }
            } else {
                allEmps.firstOrNull { it.active && it.name.trim().equals(trimmedName, ignoreCase = true) }
                    ?: allEmps.firstOrNull { it.name.trim().equals(trimmedName, ignoreCase = true) }
            }

            val finalRole = role.trim().ifBlank { "بائع" }
            addConfigItem(StoreConfigTypes.EMPLOYEE_ROLE, finalRole)

            val savedEmpId: Long
            val savedEmpName: String
            if (existing != null) {
                savedEmpId = existing.id
                savedEmpName = existing.name
                dao.updateEmployee(
                    existing.copy(
                        phone = phone.trim().ifBlank { existing.phone },
                        role = finalRole,
                        salaryType = salaryType.ifBlank { existing.salaryType },
                        baseSalary = if (baseSalary > 0.0) baseSalary else existing.baseSalary,
                        commissionPercent = if (commissionPercent > 0.0) commissionPercent else existing.commissionPercent,
                        active = true
                    )
                )
            } else {
                savedEmpName = trimmedName
                savedEmpId = dao.insertEmployee(
                    Employee(
                        name = trimmedName,
                        phone = phone.trim(),
                        role = finalRole,
                        salaryType = salaryType.ifBlank { "شهري" },
                        baseSalary = baseSalary,
                        commissionPercent = commissionPercent,
                        startDate = timestamp,
                        active = true
                    )
                )
            }

            if (openingAmount > 0.01) {
                val txType = if (isOwedToEmployee) "رصيد افتتاحي (له)" else "رصيد افتتاحي (عليه)"
                val defaultNote = if (isOwedToEmployee) {
                    "مستحقات سابقة للعامل قبل بدء النظام"
                } else {
                    "سلفة / رصيد سابق على العامل قبل بدء النظام"
                }
                dao.insertEmployeeTransaction(
                    EmployeeTransaction(
                        employeeId = savedEmpId,
                        employeeName = savedEmpName,
                        type = txType,
                        amount = openingAmount,
                        notes = notes.trim().ifBlank { defaultNote },
                        timestamp = timestamp,
                        shiftId = 0L,
                        paidFromCash = false,
                        exchangeAccountId = 0L,
                        exchangeAccountName = ""
                    )
                )
            }

            logAudit(
                action = "رصيد افتتاحي لعامل",
                details = "$savedEmpName ($finalRole) | رصيد افتتاحي: ${FormatUtils.formatCurrency(openingAmount)} (${if (isOwedToEmployee) "له" else "عليه"}) دون خصم من الصندوق"
            )
            savedEmpId
        }
    }

    suspend fun exportFullBackupSnapshot(): StoreBackupSnapshot = runInTransaction {
        StoreBackupSnapshot(
            products = dao.getAllProductsDirect(),
            sales = dao.getAllSalesDirect(),
            purchases = dao.getAllPurchasesDirect(),
            expenses = dao.getAllExpensesDirect(),
            employees = dao.getAllEmployeesIncludingArchivedDirect(),
            employeeTransactions = dao.getAllEmployeeTransactionsDirect(),
            parties = dao.getAllPartiesIncludingArchivedDirect(),
            debts = dao.getAllDebtsDirect(),
            debtPayments = dao.getAllDebtPaymentsDirect(),
            shifts = dao.getAllShiftsDirect(),
            cashMovements = dao.getAllCashMovementsDirect(),
            exchangeAccounts = dao.getAllExchangeAccountsIncludingArchivedDirect(),
            exchangeTransfers = dao.getAllExchangeTransfersDirect(),
            auditLogs = dao.getAllAuditLogsDirect(),
            storeSettings = dao.getStoreSettingsDirect(),
            configItems = dao.getAllConfigItemsDirect(),
            returnRecords = dao.getAllReturnsDirect()
        )
    }

    suspend fun restoreFullBackupSnapshot(snapshot: StoreBackupSnapshot) {
        runInTransaction {
            val currentSettings = dao.getStoreSettingsDirect()

            // Clear all existing tables strictly inside transaction AFTER validation passed
            dao.deleteAllReturns()
            dao.deleteAllSales()
            dao.deleteAllPurchases()
            dao.deleteAllExpenses()
            dao.deleteAllEmployeeTransactions()
            dao.deleteAllDebts()
            dao.deleteAllDebtPayments()
            dao.deleteAllCashMovements()
            dao.deleteAllShifts()
            dao.deleteAllExchangeTransfers()
            dao.deleteAllProducts()
            dao.deleteAllEmployees()
            dao.deleteAllExchangeAccounts()
            dao.deleteAllParties()
            dao.deleteAllAuditLogs()
            if (snapshot.configItems.isNotEmpty()) {
                dao.deleteAllConfigItems()
            }

            // Insert all 17 tables preserving original primary IDs & foreign keys
            snapshot.products.forEach { dao.insertProduct(it) }
            snapshot.exchangeAccounts.forEach { dao.insertExchangeAccount(it) }
            snapshot.parties.forEach { dao.insertParty(it) }
            snapshot.employees.forEach { dao.insertEmployee(it) }
            snapshot.shifts.forEach { dao.insertShift(it) }
            snapshot.sales.forEach { dao.insertSale(it) }
            snapshot.purchases.forEach { dao.insertPurchase(it) }
            snapshot.expenses.forEach { dao.insertExpense(it) }
            snapshot.employeeTransactions.forEach { dao.insertEmployeeTransaction(it) }
            snapshot.debts.forEach { dao.insertDebt(it) }
            snapshot.debtPayments.forEach { dao.insertDebtPayment(it) }
            snapshot.cashMovements.forEach { dao.insertCashMovement(it) }
            snapshot.exchangeTransfers.forEach { dao.insertExchangeTransfer(it) }
            snapshot.auditLogs.forEach { dao.insertAuditLog(it) }
            snapshot.configItems.forEach { dao.insertConfigItem(it) }
            snapshot.returnRecords.forEach { dao.insertReturn(it) }

            snapshot.storeSettings?.let { restoredSettings ->
                val mergedSettings = if (restoredSettings.managerPinHash.isBlank() && currentSettings != null && currentSettings.managerPinHash.isNotBlank()) {
                    restoredSettings.copy(
                        managerPin = "",
                        managerPinHash = currentSettings.managerPinHash,
                        managerPinSalt = currentSettings.managerPinSalt,
                        mustChangePin = currentSettings.mustChangePin,
                        pinFailedAttempts = 0,
                        pinLockUntilTimestamp = 0L
                    )
                } else {
                    restoredSettings.copy(managerPin = "")
                }
                if (dao.getStoreSettingsDirect() == null) {
                    dao.insertStoreSettings(mergedSettings)
                } else {
                    dao.updateStoreSettings(mergedSettings)
                }
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "استعادة نسخة احتياطية",
                    actor = "المدير",
                    details = "تمت استعادة النسخة الاحتياطية الكاملة بنجاح مع الحفاظ على كافة السجلات والروابط",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun restoreFullData(
        productsJson: JSONArray?,
        salesJson: JSONArray?,
        purchasesJson: JSONArray?,
        expensesJson: JSONArray?,
        accountsJson: JSONArray?,
        partiesJson: JSONArray?,
        debtsJson: JSONArray?
    ) {
        val root = JSONObject().apply {
            put("app", "HesabiStore")
            put("version", 6)
            put("products", productsJson ?: JSONArray())
            put("sales", salesJson ?: JSONArray())
            put("purchases", purchasesJson ?: JSONArray())
            put("expenses", expensesJson ?: JSONArray())
            put("exchangeAccounts", accountsJson ?: JSONArray())
            put("parties", partiesJson ?: JSONArray())
            put("debts", debtsJson ?: JSONArray())
        }
        val parsed = com.example.ui.util.BackupManager.validateAndParseBackup(root.toString()).getOrThrow()
        restoreFullBackupSnapshot(parsed.snapshot)
    }

    // ====================================================================
    // Sales & Purchase Returns (المرتجعات حسب الفاتورة الأصلية)
    // ====================================================================

    private suspend fun reducePartyOpenDebtsForReturn(
        debtType: String,
        partyName: String,
        linkedDebts: List<DebtRecord>,
        amountToReduce: Double
    ) {
        if (amountToReduce <= 0.001) return
        val linkedIds = linkedDebts.map { it.id }.toSet()
        val partyDebts = dao.getAllDebtsDirect()
            .filter {
                it.type == debtType &&
                    !it.isSettled &&
                    it.remainingAmount > 0.001 &&
                    it.id !in linkedIds &&
                    it.partyName.trim().equals(partyName.trim(), ignoreCase = true)
            }
            .sortedBy { it.createdAt }
        val orderedTargets = (linkedDebts.filter { !it.isSettled && it.remainingAmount > 0.001 }.sortedBy { it.createdAt }) + partyDebts
        val totalAvailable = orderedTargets.sumOf { it.remainingAmount }
        if (amountToReduce > totalAvailable + 0.01) {
            val label = if (debtType == "CUSTOMER") "العميل" else "المورد"
            throw IllegalStateException(
                "مبلغ خصم الدين (${FormatUtils.formatCurrency(amountToReduce)}) يتجاوز إجمالي الديون المفتوحة لـ$label (${FormatUtils.formatCurrency(totalAvailable)})!"
            )
        }
        var remaining = amountToReduce
        for (debt in orderedTargets) {
            if (remaining <= 0.001) break
            val fresh = dao.getDebtById(debt.id) ?: continue
            val applyNow = minOf(remaining, fresh.remainingAmount)
            if (applyNow > 0.001) {
                val newPaid = fresh.paidAmount + applyNow
                val newRemaining = (fresh.originalAmount - newPaid).coerceAtLeast(0.0)
                dao.updateDebt(
                    fresh.copy(
                        paidAmount = newPaid,
                        remainingAmount = newRemaining,
                        isSettled = newRemaining <= 0.001
                    )
                )
                remaining -= applyNow
            }
        }
    }

    private suspend fun restorePartyDebtsFromDeletedReturn(
        debtType: String,
        partyName: String,
        linkedDebts: List<DebtRecord>,
        amountToRestore: Double
    ) {
        if (amountToRestore <= 0.001) return
        val linkedIds = linkedDebts.map { it.id }.toSet()
        val otherPartyDebts = if (partyName.isNotBlank()) {
            dao.getAllDebtsDirect()
                .filter {
                    it.type == debtType &&
                        it.paidAmount > 0.001 &&
                        it.id !in linkedIds &&
                        it.partyName.trim().equals(partyName.trim(), ignoreCase = true)
                }
                .sortedBy { it.createdAt }
        } else emptyList()
        val orderedTargets = (linkedDebts.filter { it.paidAmount > 0.001 }.sortedBy { it.createdAt }) + otherPartyDebts
        var remaining = amountToRestore
        for (debt in orderedTargets) {
            if (remaining <= 0.001) break
            val fresh = dao.getDebtById(debt.id) ?: continue
            val restoreNow = minOf(remaining, fresh.paidAmount)
            if (restoreNow > 0.001) {
                val newPaid = (fresh.paidAmount - restoreNow).coerceAtLeast(0.0)
                val newRemaining = (fresh.originalAmount - newPaid).coerceAtLeast(0.0)
                dao.updateDebt(
                    fresh.copy(
                        paidAmount = newPaid,
                        remainingAmount = newRemaining,
                        isSettled = newRemaining <= 0.001
                    )
                )
                remaining -= restoreNow
            }
        }
    }

    suspend fun executeSaleReturn(
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
        timestamp: Long = System.currentTimeMillis()
    ): CompletedReturnResult {
        val mergedRequests = lines
            .filter { it.returnQuantity > 0 }
            .groupBy { it.originalLineId }
            .map { (lineId, reqs) -> ReturnLineRequest(lineId, reqs.sumOf { it.returnQuantity }) }
        if (mergedRequests.isEmpty()) {
            throw IllegalArgumentException("يرجى تحديد كمية الإرجاع لصنف واحد على الأقل من الفاتورة.")
        }
        if (cashAmount < 0.0 || transferAmount < 0.0 || debtReducedAmount < 0.0 || surplusCreditAmount < 0.0) {
            throw IllegalArgumentException("لا يمكن إدخال مبالغ تسوية سالبة.")
        }

        return runInTransaction {
            val originalLines = if (originalInvoiceKey.startsWith("ID:")) {
                val saleId = originalInvoiceKey.removePrefix("ID:").toLongOrNull()
                    ?: throw IllegalArgumentException("معرف الفاتورة غير صالح!")
                listOfNotNull(dao.getSaleById(saleId))
            } else {
                dao.getSalesByInvoiceNumber(originalInvoiceKey)
            }
            if (originalLines.isEmpty()) {
                throw IllegalArgumentException("فاتورة البيع الأصلية غير موجودة!")
            }

            val firstSale = originalLines.first()
            val invoiceLabel = firstSale.invoiceNumber.ifBlank { "ID:${firstSale.id}" }
            val partyName = firstSale.customerName.trim()
            val partyPhone = firstSale.customerPhone.trim()

            val priorReturns = dao.getReturnsByOriginalLineIds(
                ReturnTypes.SALE_RETURN,
                originalLines.map { it.id }
            )
            val prevQtyByLine = priorReturns.groupBy { it.originalLineId }
                .mapValues { (_, list) -> list.sumOf { it.quantity } }
            val prevAmountByLine = priorReturns.groupBy { it.originalLineId }
                .mapValues { (_, list) -> list.sumOf { it.totalAmount } }

            data class PreparedSaleReturnLine(
                val orig: SaleTransaction,
                val returnQty: Int,
                val effectiveNetUnitPrice: Double,
                val lineTotal: Double,
                val profitAdj: Double
            )

            val preparedLines = mergedRequests.map { req ->
                val orig = originalLines.find { it.id == req.originalLineId }
                    ?: throw IllegalArgumentException("الصنف المطلوب إرجاعه غير موجود ضمن فاتورة البيع ($invoiceLabel)!")
                val alreadyReturnedQty = prevQtyByLine[orig.id] ?: 0
                val remainingQty = orig.quantity - alreadyReturnedQty
                if (req.returnQuantity > remainingQty) {
                    throw IllegalStateException(
                        "الكمية المطلوب إرجاعها من (${orig.productName}) هي ${req.returnQuantity} حبة، والمتبقي القابل للإرجاع في هذه الفاتورة هو $remainingQty حبة فقط!"
                    )
                }
                val effectiveNetUnitPrice = if (orig.quantity > 0) orig.totalAmount / orig.quantity else orig.unitSellPrice
                val alreadyReturnedAmount = prevAmountByLine[orig.id] ?: 0.0
                val lineTotal = if (req.returnQuantity == remainingQty) {
                    (orig.totalAmount - alreadyReturnedAmount).coerceAtLeast(0.0)
                } else {
                    effectiveNetUnitPrice * req.returnQuantity
                }
                val profitAdj = lineTotal - (orig.unitBuyPrice * req.returnQuantity)
                PreparedSaleReturnLine(
                    orig = orig,
                    returnQty = req.returnQuantity,
                    effectiveNetUnitPrice = effectiveNetUnitPrice,
                    lineTotal = lineTotal,
                    profitAdj = profitAdj
                )
            }

            val totalReturnAmount = preparedLines.sumOf { it.lineTotal }
            val settlementSum = cashAmount + transferAmount + debtReducedAmount + surplusCreditAmount
            if (kotlin.math.abs(settlementSum - totalReturnAmount) > 0.01) {
                throw IllegalArgumentException(
                    "مجموع التسوية (${FormatUtils.formatCurrency(settlementSum)}) يجب أن يطابق إجمالي المرتجع (${FormatUtils.formatCurrency(totalReturnAmount)})."
                )
            }
            if (transferAmount > 0.001 && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة عند رد المبلغ عبر الصراف.")
            }
            if ((debtReducedAmount > 0.001 || surplusCreditAmount > 0.001) && partyName.isBlank()) {
                throw IllegalArgumentException("لا يمكن خصم دين أو حفظ رصيد دائن لزبون بدون اسم! يرجى اختيار الرد النقدي أو عبر الصراف.")
            }

            requireSufficientCash(cashAmount)
            if (transferAmount > 0.001 && exchangeAccount != null) {
                requireSufficientExchange(exchangeAccount.id, transferAmount)
            }

            if (debtReducedAmount > 0.001) {
                val linkedDebts = dao.getDebtsBySaleIds(originalLines.map { it.id })
                reducePartyOpenDebtsForReturn(
                    debtType = "CUSTOMER",
                    partyName = partyName,
                    linkedDebts = linkedDebts,
                    amountToReduce = debtReducedAmount
                )
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val finalReturnNumber = resolveReturnNumber(returnNumberInput, "RS")
            val allProds = dao.getAllProductsDirect().toMutableList()
            val insertedRecords = mutableListOf<ReturnRecord>()
            var firstReturnId = 0L

            for (item in preparedLines) {
                val orig = item.orig
                var targetProductId = orig.productId
                val byId = if (targetProductId > 0L) dao.getProductById(targetProductId) else null
                if (byId != null) {
                    dao.incrementStock(byId.id, item.returnQty)
                } else {
                    val matched = allProds.find {
                        it.modelName.trim().equals(orig.productName.trim(), ignoreCase = true) &&
                            it.color.trim().equals(orig.color.trim(), ignoreCase = true) &&
                            it.size.trim().equals(orig.size.trim(), ignoreCase = true)
                    }
                    if (matched != null) {
                        targetProductId = matched.id
                        dao.incrementStock(matched.id, item.returnQty)
                    } else {
                        val recreated = ProductItem(
                            category = orig.category,
                            modelName = orig.productName,
                            color = orig.color,
                            size = orig.size,
                            quantity = item.returnQty,
                            buyPrice = orig.unitBuyPrice,
                            sellPrice = orig.unitSellPrice,
                            notes = "صنف مُرجع من فاتورة #$invoiceLabel",
                            unit = orig.unit
                        )
                        targetProductId = dao.insertProduct(recreated)
                        allProds.add(recreated.copy(id = targetProductId))
                    }
                }

                val ratio = if (totalReturnAmount > 0.0001) item.lineTotal / totalReturnAmount else (1.0 / preparedLines.size)
                val record = ReturnRecord(
                    returnType = ReturnTypes.SALE_RETURN,
                    returnNumber = finalReturnNumber,
                    originalInvoiceNumber = invoiceLabel,
                    originalLineId = orig.id,
                    productId = targetProductId,
                    productName = orig.productName,
                    category = orig.category,
                    color = orig.color,
                    size = orig.size,
                    unit = orig.unit,
                    quantity = item.returnQty,
                    unitBuyPrice = orig.unitBuyPrice,
                    unitSellPrice = item.effectiveNetUnitPrice,
                    totalAmount = item.lineTotal,
                    profitAdjustment = item.profitAdj,
                    partyName = partyName.ifBlank { "زبون نقدي" },
                    partyPhone = partyPhone,
                    settlementMethod = settlementMethod,
                    cashAmount = cashAmount * ratio,
                    transferAmount = transferAmount * ratio,
                    debtReducedAmount = debtReducedAmount * ratio,
                    surplusCreditAmount = surplusCreditAmount * ratio,
                    exchangeAccountId = exchangeAccount?.id ?: 0L,
                    exchangeAccountName = exchangeAccount?.name ?: "",
                    notes = notes.trim(),
                    timestamp = timestamp,
                    shiftId = shiftId
                )
                val savedId = dao.insertReturn(record)
                if (firstReturnId == 0L) firstReturnId = savedId
                insertedRecords.add(record.copy(id = savedId))
            }

            val totalPieces = preparedLines.sumOf { it.returnQty }
            val partyLabel = partyName.ifBlank { "زبون نقدي" }

            if (cashAmount > 0.001) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "SALE_RETURN_CASH",
                        amount = cashAmount,
                        isIncome = false,
                        title = "مرتجع مبيعات #$finalReturnNumber (فاتورة #$invoiceLabel)",
                        details = "العميل: $partyLabel | $totalPieces حبة",
                        timestamp = timestamp,
                        referenceReturnId = firstReturnId
                    )
                )
            }

            if (transferAmount > 0.001 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, -transferAmount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مرتجع مبيعات",
                        amount = transferAmount,
                        isCredit = false,
                        title = "رد مبلغ مرتجع مبيعات #$finalReturnNumber",
                        referenceNumber = finalReturnNumber,
                        notes = "مرتجع فاتورة بيع #$invoiceLabel | العميل: $partyLabel",
                        timestamp = timestamp,
                        referenceType = ReturnTypes.SALE_RETURN,
                        referenceId = firstReturnId
                    )
                )
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "مرتجع مبيعات",
                    actor = "المدير",
                    details = "مرتجع #$finalReturnNumber على فاتورة بيع #$invoiceLabel للعميل ($partyLabel) | $totalPieces حبة بقيمة ${FormatUtils.formatCurrency(totalReturnAmount)} ($settlementMethod)",
                    timestamp = timestamp
                )
            )

            CompletedReturnResult(
                returnType = ReturnTypes.SALE_RETURN,
                returnNumber = finalReturnNumber,
                originalInvoiceNumber = invoiceLabel,
                partyName = partyLabel,
                settlementMethod = settlementMethod,
                totalReturnAmount = totalReturnAmount,
                cashAmount = cashAmount,
                transferAmount = transferAmount,
                debtReducedAmount = debtReducedAmount,
                surplusCreditAmount = surplusCreditAmount,
                exchangeAccountName = exchangeAccount?.name ?: "",
                totalPiecesReturned = totalPieces,
                records = insertedRecords,
                timestamp = timestamp
            )
        }
    }

    suspend fun executePurchaseReturn(
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
        timestamp: Long = System.currentTimeMillis()
    ): CompletedReturnResult {
        val mergedRequests = lines
            .filter { it.returnQuantity > 0 }
            .groupBy { it.originalLineId }
            .map { (lineId, reqs) -> ReturnLineRequest(lineId, reqs.sumOf { it.returnQuantity }) }
        if (mergedRequests.isEmpty()) {
            throw IllegalArgumentException("يرجى تحديد كمية الإرجاع لصنف واحد على الأقل من فاتورة الشراء.")
        }
        if (cashAmount < 0.0 || transferAmount < 0.0 || debtReducedAmount < 0.0 || surplusCreditAmount < 0.0) {
            throw IllegalArgumentException("لا يمكن إدخال مبالغ تسوية سالبة.")
        }

        return runInTransaction {
            val originalLines = if (originalInvoiceKey.startsWith("ID:")) {
                val purId = originalInvoiceKey.removePrefix("ID:").toLongOrNull()
                    ?: throw IllegalArgumentException("معرف فاتورة الشراء غير صالح!")
                listOfNotNull(dao.getPurchaseById(purId))
            } else {
                dao.getPurchasesByInvoiceNumber(originalInvoiceKey)
            }
            if (originalLines.isEmpty()) {
                throw IllegalArgumentException("فاتورة الشراء الأصلية غير موجودة!")
            }

            val firstPurchase = originalLines.first()
            val invoiceLabel = firstPurchase.invoiceNumber.ifBlank { "ID:${firstPurchase.id}" }
            val partyName = firstPurchase.supplierName.trim()

            val priorReturns = dao.getReturnsByOriginalLineIds(
                ReturnTypes.PURCHASE_RETURN,
                originalLines.map { it.id }
            )
            val prevQtyByLine = priorReturns.groupBy { it.originalLineId }
                .mapValues { (_, list) -> list.sumOf { it.quantity } }
            val prevAmountByLine = priorReturns.groupBy { it.originalLineId }
                .mapValues { (_, list) -> list.sumOf { it.totalAmount } }

            val allProds = dao.getAllProductsDirect()

            data class PreparedPurchaseReturnLine(
                val orig: PurchaseRecord,
                val product: ProductItem,
                val returnQty: Int,
                val effectiveUnitBuyPrice: Double,
                val lineTotal: Double
            )

            val preparedLines = mergedRequests.map { req ->
                val orig = originalLines.find { it.id == req.originalLineId }
                    ?: throw IllegalArgumentException("الصنف المطلوب إرجاعه غير موجود ضمن فاتورة الشراء ($invoiceLabel)!")
                val alreadyReturnedQty = prevQtyByLine[orig.id] ?: 0
                val remainingQty = orig.quantity - alreadyReturnedQty
                if (req.returnQuantity > remainingQty) {
                    throw IllegalStateException(
                        "الكمية المطلوب إرجاعها من (${orig.modelName}) هي ${req.returnQuantity} حبة، والمتبقي القابل للإرجاع في هذه الفاتورة هو $remainingQty حبة فقط!"
                    )
                }
                val prod = allProds.find {
                    it.modelName.trim().equals(orig.modelName.trim(), ignoreCase = true) &&
                        it.color.trim().equals(orig.color.trim(), ignoreCase = true) &&
                        it.size.trim().equals(orig.size.trim(), ignoreCase = true)
                } ?: throw IllegalStateException(
                    "الصنف (${orig.modelName} - ${orig.color} - ${orig.size}) غير موجود في المخزون لإتمام الإرجاع للمورد!"
                )
                val effectiveUnitBuyPrice = if (orig.quantity > 0) orig.totalCost / orig.quantity else orig.unitBuyPrice
                val alreadyReturnedAmount = prevAmountByLine[orig.id] ?: 0.0
                val lineTotal = if (req.returnQuantity == remainingQty) {
                    (orig.totalCost - alreadyReturnedAmount).coerceAtLeast(0.0)
                } else {
                    effectiveUnitBuyPrice * req.returnQuantity
                }
                PreparedPurchaseReturnLine(
                    orig = orig,
                    product = prod,
                    returnQty = req.returnQuantity,
                    effectiveUnitBuyPrice = effectiveUnitBuyPrice,
                    lineTotal = lineTotal
                )
            }

            // Verify aggregate stock availability if multiple lines map to same product
            preparedLines.groupBy { it.product.id }.forEach { (_, group) ->
                val prod = group.first().product
                val totalNeeded = group.sumOf { it.returnQty }
                if (prod.quantity < totalNeeded) {
                    throw IllegalStateException(
                        "الكمية المتوفرة في المخزون من (${prod.modelName}) هي ${prod.quantity} حبة فقط ولا تكفي لإرجاع $totalNeeded حبة للمورد!"
                    )
                }
            }

            val totalReturnAmount = preparedLines.sumOf { it.lineTotal }
            val settlementSum = cashAmount + transferAmount + debtReducedAmount + surplusCreditAmount
            if (kotlin.math.abs(settlementSum - totalReturnAmount) > 0.01) {
                throw IllegalArgumentException(
                    "مجموع التسوية (${FormatUtils.formatCurrency(settlementSum)}) يجب أن يطابق إجمالي المرتجع (${FormatUtils.formatCurrency(totalReturnAmount)})."
                )
            }
            if (transferAmount > 0.001 && (exchangeAccount == null || exchangeAccount.id <= 0L)) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المستلم للمبلغ من المورد.")
            }
            if ((debtReducedAmount > 0.001 || surplusCreditAmount > 0.001) && partyName.isBlank()) {
                throw IllegalArgumentException("لا يمكن خصم دين أو تسجيل رصيد دائن لمورد بدون اسم!")
            }

            if (debtReducedAmount > 0.001) {
                val linkedDebts = dao.getDebtsByPurchaseIds(originalLines.map { it.id })
                reducePartyOpenDebtsForReturn(
                    debtType = "SUPPLIER",
                    partyName = partyName,
                    linkedDebts = linkedDebts,
                    amountToReduce = debtReducedAmount
                )
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val finalReturnNumber = resolveReturnNumber(returnNumberInput, "RP")
            val insertedRecords = mutableListOf<ReturnRecord>()
            var firstReturnId = 0L

            for (item in preparedLines) {
                val updatedRows = dao.decrementStockSafe(item.product.id, item.returnQty, timestamp)
                if (updatedRows == 0) {
                    throw IllegalStateException(
                        "الكمية المتوفرة في المخزون من (${item.product.modelName}) لا تكفي لإتمام الإرجاع للمورد!"
                    )
                }
                val orig = item.orig
                val ratio = if (totalReturnAmount > 0.0001) item.lineTotal / totalReturnAmount else (1.0 / preparedLines.size)
                val record = ReturnRecord(
                    returnType = ReturnTypes.PURCHASE_RETURN,
                    returnNumber = finalReturnNumber,
                    originalInvoiceNumber = invoiceLabel,
                    originalLineId = orig.id,
                    productId = item.product.id,
                    productName = orig.modelName,
                    category = orig.category,
                    color = orig.color,
                    size = orig.size,
                    unit = orig.unit,
                    quantity = item.returnQty,
                    unitBuyPrice = item.effectiveUnitBuyPrice,
                    unitSellPrice = orig.unitSellPrice,
                    totalAmount = item.lineTotal,
                    profitAdjustment = 0.0,
                    partyName = partyName.ifBlank { "مورد عام" },
                    partyPhone = "",
                    settlementMethod = settlementMethod,
                    cashAmount = cashAmount * ratio,
                    transferAmount = transferAmount * ratio,
                    debtReducedAmount = debtReducedAmount * ratio,
                    surplusCreditAmount = surplusCreditAmount * ratio,
                    exchangeAccountId = exchangeAccount?.id ?: 0L,
                    exchangeAccountName = exchangeAccount?.name ?: "",
                    notes = notes.trim(),
                    timestamp = timestamp,
                    shiftId = shiftId
                )
                val savedId = dao.insertReturn(record)
                if (firstReturnId == 0L) firstReturnId = savedId
                insertedRecords.add(record.copy(id = savedId))
            }

            val totalPieces = preparedLines.sumOf { it.returnQty }
            val partyLabel = partyName.ifBlank { "مورد عام" }

            if (cashAmount > 0.001) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "PURCHASE_RETURN_CASH",
                        amount = cashAmount,
                        isIncome = true,
                        title = "مرتجع مشتريات #$finalReturnNumber (فاتورة #$invoiceLabel)",
                        details = "المورد: $partyLabel | $totalPieces حبة",
                        timestamp = timestamp,
                        referenceReturnId = firstReturnId
                    )
                )
            }

            if (transferAmount > 0.001 && exchangeAccount != null) {
                dao.updateExchangeBalance(exchangeAccount.id, transferAmount)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = exchangeAccount.id,
                        accountName = exchangeAccount.name,
                        type = "مرتجع مشتريات",
                        amount = transferAmount,
                        isCredit = true,
                        title = "استلام مبلغ مرتجع مشتريات #$finalReturnNumber",
                        referenceNumber = finalReturnNumber,
                        notes = "مرتجع فاتورة شراء #$invoiceLabel | المورد: $partyLabel",
                        timestamp = timestamp,
                        referenceType = ReturnTypes.PURCHASE_RETURN,
                        referenceId = firstReturnId
                    )
                )
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "مرتجع مشتريات",
                    actor = "المدير",
                    details = "مرتجع #$finalReturnNumber على فاتورة شراء #$invoiceLabel للمورد ($partyLabel) | $totalPieces حبة بقيمة ${FormatUtils.formatCurrency(totalReturnAmount)} ($settlementMethod)",
                    timestamp = timestamp
                )
            )

            CompletedReturnResult(
                returnType = ReturnTypes.PURCHASE_RETURN,
                returnNumber = finalReturnNumber,
                originalInvoiceNumber = invoiceLabel,
                partyName = partyLabel,
                settlementMethod = settlementMethod,
                totalReturnAmount = totalReturnAmount,
                cashAmount = cashAmount,
                transferAmount = transferAmount,
                debtReducedAmount = debtReducedAmount,
                surplusCreditAmount = surplusCreditAmount,
                exchangeAccountName = exchangeAccount?.name ?: "",
                totalPiecesReturned = totalPieces,
                records = insertedRecords,
                timestamp = timestamp
            )
        }
    }

    suspend fun deleteReturn(returnNumber: String) {
        runInTransaction {
            val records = dao.getReturnsByReturnNumber(returnNumber)
            if (records.isEmpty()) return@runInTransaction

            val first = records.first()
            val returnIds = records.map { it.id }
            val origLineIds = records.map { it.originalLineId }.distinct()
            val totalCash = records.sumOf { it.cashAmount }
            val totalTransfer = records.sumOf { it.transferAmount }
            val totalDebtReduced = records.sumOf { it.debtReducedAmount }
            val now = System.currentTimeMillis()

            if (first.returnType == ReturnTypes.SALE_RETURN) {
                // Returning items to stock had incremented inventory; deleting return must decrement stock safely
                for (r in records) {
                    val decremented = if (r.productId > 0L) dao.decrementStockSafe(r.productId, r.quantity, now) else 0
                    if (decremented == 0) {
                        throw IllegalStateException(
                            "لا يمكن حذف مرتجع المبيعات #$returnNumber لأن الكمية المُرجعة من (${r.productName}) تم بيعها مرة أخرى!"
                        )
                    }
                }
                dao.deleteCashMovementsForReturns(returnIds)
                reverseLinkedTransfersOrFallback(
                    refType = ReturnTypes.SALE_RETURN,
                    refIds = returnIds,
                    fallbackRefNumber = returnNumber,
                    fallbackAccountId = first.exchangeAccountId,
                    fallbackAmount = totalTransfer,
                    fallbackWasCredit = false,
                    reason = "إلغاء مرتجع مبيعات #$returnNumber",
                    timestamp = now
                )
                val linkedDebts = dao.getDebtsBySaleIds(origLineIds)
                restorePartyDebtsFromDeletedReturn(
                    debtType = "CUSTOMER",
                    partyName = first.partyName,
                    linkedDebts = linkedDebts,
                    amountToRestore = totalDebtReduced
                )
            } else {
                // Purchase return had brought cash/transfer IN and removed stock
                requireSufficientCash(totalCash)
                dao.deleteCashMovementsForReturns(returnIds)
                reverseLinkedTransfersOrFallback(
                    refType = ReturnTypes.PURCHASE_RETURN,
                    refIds = returnIds,
                    fallbackRefNumber = returnNumber,
                    fallbackAccountId = first.exchangeAccountId,
                    fallbackAmount = totalTransfer,
                    fallbackWasCredit = true,
                    reason = "إلغاء مرتجع مشتريات #$returnNumber",
                    timestamp = now
                )
                for (r in records) {
                    if (r.productId > 0L && dao.getProductById(r.productId) != null) {
                        dao.incrementStock(r.productId, r.quantity)
                    }
                }
                val linkedDebts = dao.getDebtsByPurchaseIds(origLineIds)
                restorePartyDebtsFromDeletedReturn(
                    debtType = "SUPPLIER",
                    partyName = first.partyName,
                    linkedDebts = linkedDebts,
                    amountToRestore = totalDebtReduced
                )
            }

            dao.deleteReturnsByReturnNumber(returnNumber)
            val labelAr = if (first.returnType == ReturnTypes.SALE_RETURN) "مرتجع مبيعات" else "مرتجع مشتريات"
            dao.insertAuditLog(
                AuditLogRecord(
                    action = "إلغاء مرتجع",
                    actor = "المدير",
                    details = "تم حذف وإلغاء $labelAr #$returnNumber وعكس كافة الأرصدة والمخزون",
                    timestamp = now
                )
            )
        }
    }
}
