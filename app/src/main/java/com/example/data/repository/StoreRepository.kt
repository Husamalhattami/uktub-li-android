package com.example.data.repository

import com.example.data.dao.StoreDao
import com.example.data.model.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

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
    val allEmployeeTransactions: Flow<List<EmployeeTransaction>> = dao.getAllEmployeeTransactions()
    val allDebts: Flow<List<DebtRecord>> = dao.getAllDebts()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()
    val activeShift: Flow<CashDrawerShift?> = dao.getActiveShift()
    val allShifts: Flow<List<CashDrawerShift>> = dao.getAllShifts()
    val allCashMovements: Flow<List<CashDrawerMovement>> = dao.getAllCashMovements()
    val allExchangeAccounts: Flow<List<ExchangeAccount>> = dao.getAllExchangeAccounts()
    val allExchangeTransfers: Flow<List<ExchangeTransfer>> = dao.getAllExchangeTransfers()
    val allParties: Flow<List<PartyAccount>> = dao.getAllParties()
    val allAuditLogs: Flow<List<AuditLogRecord>> = dao.getAllAuditLogs()
    val storeSettings: Flow<StoreSettings?> = dao.getStoreSettings()
    val allConfigItems: Flow<List<StoreConfigItem>> = dao.getAllConfigItems()

    suspend fun getStoreSettingsDirect(): StoreSettings? = dao.getStoreSettingsDirect()

    suspend fun logAudit(action: String, actor: String = "المدير", details: String) {
        dao.insertAuditLog(AuditLogRecord(action = action, actor = actor, details = details, timestamp = System.currentTimeMillis()))
    }

    suspend fun saveStoreSettings(settings: StoreSettings) {
        val existing = dao.getStoreSettingsDirect()
        if (existing == null) {
            dao.insertStoreSettings(settings)
        } else {
            dao.updateStoreSettings(settings)
        }
        logAudit("تحديث الإعدادات", "المدير", "تم تحديث بيانات المحل والشعار ورقم الهاتف وإعدادات الفاتورة")
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
        customInvoiceNumber: String = ""
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
                val half = netTotal / 2.0
                actualCash = half
                actualTransfer = netTotal - half
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
                if (kotlin.math.abs(sumParts - netTotal) > 1.0) {
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
            val now = System.currentTimeMillis()
            val invoiceNumber = customInvoiceNumber.trim().ifBlank { "INV-${now % 1000000}" }
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val finalCustomer = customerName.ifBlank { if (actualDebt > 0) "عميل آجل" else "زبون نقدي" }

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
                        referenceNumber = transferRef,
                        notes = "فاتورة بيع #${invoiceNumber} | العميل: $finalCustomer",
                        timestamp = now
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
        transferRef: String = ""
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
            if (kotlin.math.abs((cashPart + transferPart) - totalAmount) > 1.0) {
                throw IllegalArgumentException("مجموع الدفع النقدي ($cashPart) والتحويل ($transferPart) يجب أن يعادل إجمالي الفاتورة ($totalAmount ريال).")
            }
        }

        return runInTransaction {
            val freshProduct = dao.getProductById(product.id)
                ?: throw IllegalStateException("الصنف المطلوب بيعه غير موجود في المخزون!")

            if (freshProduct.quantity < totalPieces) {
                throw IllegalStateException("الكمية المطلوبة ($totalPieces حبة) غير متوفرة! المتبقي في المخزن فقط (${freshProduct.quantity} حبة).")
            }

            val updatedRows = dao.decrementStockSafe(product.id, totalPieces, System.currentTimeMillis())
            if (updatedRows == 0) {
                throw IllegalStateException("تعذر خصم الكمية من المخزون بسبب عدم كفاية الرصيد أو حدوث حركة متزامنة!")
            }

            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = System.currentTimeMillis()

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
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: "",
                transferRef = transferRef,
                employeeName = employee?.name ?: "صاحب المحل",
                employeeId = employee?.id ?: 0L,
                timestamp = now,
                shiftId = shiftId,
                unit = unit
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
                        referenceNumber = transferRef,
                        notes = "فاتورة بيع #${saleId} | الزبون: ${customerName.ifBlank { "تحويل" }}",
                        timestamp = now
                    )
                )
            }

            if (paymentType == "آجل (دين)" || paymentType == "بالدين") {
                val debt = DebtRecord(
                    type = "CUSTOMER",
                    partyName = customerName.ifBlank { "زبون آجل" },
                    phone = customerPhone,
                    originalAmount = totalAmount,
                    paidAmount = 0.0,
                    remainingAmount = totalAmount,
                    details = "فاتورة بيع آجل: ${product.modelName} ($unitDesc)",
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
                    details = "فاتورة بيع #${saleId}: ${product.modelName} ($unitDesc) بقيمة ${FormatUtils.formatCurrency(totalAmount)} ($paymentType)",
                    timestamp = now
                )
            )

            sale.copy(id = saleId)
        }
    }

    // --- Delete / Reverse Sale ---
    suspend fun deleteSale(saleId: Long) {
        runInTransaction {
            val sale = dao.getSaleById(saleId) ?: return@runInTransaction

            // 1. Safely restore stock back
            dao.incrementStock(sale.productId, sale.quantity)

            // 2. Reverse cash movements
            dao.deleteCashMovementsForSale(saleId)

            // 3. Reverse exchange transfer if any
            if (sale.transferAmount > 0 && sale.exchangeAccountId > 0) {
                dao.updateExchangeBalance(sale.exchangeAccountId, -sale.transferAmount)
            }

            // 4. Reverse debt if this was debt sale
            val debt = dao.getDebtBySaleId(saleId)
            if (debt != null) {
                dao.deleteDebt(debt.id)
            }

            // 5. Delete sale
            dao.deleteSale(saleId)

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "إلغاء بيع",
                    actor = "المدير",
                    details = "تم إلغاء فاتورة البيع #${sale.id} وإرجاع (${sale.quantity}) حبة لمخزون (${sale.productName})",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateSale(newSale: SaleTransaction) {
        runInTransaction {
            val oldSale = dao.getSaleById(newSale.id) ?: throw IllegalArgumentException("فاتورة البيع غير موجودة!")

            // 1. Stock adjustments
            val qtyDiff = newSale.quantity - oldSale.quantity
            if (qtyDiff > 0) {
                val updated = dao.decrementStockSafe(newSale.productId, qtyDiff, System.currentTimeMillis())
                if (updated == 0) {
                    throw IllegalStateException("لا تتوفر كمية كافية في المخزون لإجراء هذا التعديل!")
                }
            } else if (qtyDiff < 0) {
                dao.incrementStock(newSale.productId, -qtyDiff)
            }

            // 2. Cash adjustment
            val cashDiff = newSale.cashAmount - oldSale.cashAmount
            if (cashDiff != 0.0) {
                val now = System.currentTimeMillis()
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = newSale.shiftId,
                        type = "SALE_CASH",
                        amount = kotlin.math.abs(cashDiff),
                        isIncome = cashDiff > 0,
                        title = "تعديل مبيعات فاتورة #${newSale.id}: ${newSale.productName}",
                        details = if (cashDiff > 0) "إضافة فرق نقدي للصندوق" else "استرجاع فرق نقدي للعميل",
                        timestamp = now,
                        referenceSaleId = newSale.id
                    )
                )
            }

            // 3. Transfer adjustment
            val transferDiff = newSale.transferAmount - oldSale.transferAmount
            if (transferDiff != 0.0 && newSale.exchangeAccountId > 0) {
                dao.updateExchangeBalance(newSale.exchangeAccountId, transferDiff)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = newSale.exchangeAccountId,
                        accountName = newSale.exchangeAccountName,
                        type = "مبيعات تحويل",
                        amount = kotlin.math.abs(transferDiff),
                        isCredit = transferDiff > 0,
                        title = "تعديل تحويل فاتورة #${newSale.id}",
                        notes = "تعديل الفاتورة",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // 4. Debt adjustment
            val oldDebt = dao.getDebtBySaleId(newSale.id)
            val newDebtAmount = newSale.debtAmount
            if (newDebtAmount > 0) {
                if (oldDebt != null) {
                    val debtDiff = newDebtAmount - oldSale.debtAmount
                    dao.updateDebt(
                        oldDebt.copy(
                            partyName = newSale.customerName,
                            phone = newSale.customerPhone,
                            originalAmount = oldDebt.originalAmount + debtDiff,
                            remainingAmount = (oldDebt.remainingAmount + debtDiff).coerceAtLeast(0.0),
                            isSettled = (oldDebt.remainingAmount + debtDiff) <= 0.01
                        )
                    )
                } else {
                    dao.insertDebt(
                        DebtRecord(
                            type = "CUSTOMER",
                            partyName = newSale.customerName.ifBlank { "عميل آجل" },
                            phone = newSale.customerPhone,
                            originalAmount = newDebtAmount,
                            remainingAmount = newDebtAmount,
                            details = "دين معدل من فاتورة #${newSale.id}",
                            referenceSaleId = newSale.id
                        )
                    )
                }
            } else if (oldDebt != null) {
                dao.deleteDebt(oldDebt.id)
            }

            // 5. Update sale record
            dao.updateSale(newSale)

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تعديل بيع",
                    actor = "المدير",
                    details = "تم تعديل فاتورة البيع #${newSale.id} للعميل (${newSale.customerName}) بمبلغ (${newSale.totalAmount})",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
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
        notes: String = ""
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
                val half = totalCost / 2.0
                actualCash = half
                actualTransfer = totalCost - half
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
                if (kotlin.math.abs(sumParts - totalCost) > 1.0) {
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
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = System.currentTimeMillis()
            val finalInvoiceNumber = invoiceNumber.trim().ifBlank { "PUR-${now % 1000000}" }
            val finalSupplier = supplierName.trim().ifBlank { "مورد عام" }

            var firstPurchaseId = 0L

            // 1. Process each item: insert purchase record & update/create inventory product
            val allExistingProducts = dao.getAllProducts().firstOrNull() ?: emptyList()
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
                val existing = allExistingProducts.find {
                    it.modelName.trim().equals(item.modelName.trim(), ignoreCase = true) &&
                    it.color.trim().equals(item.color.trim(), ignoreCase = true) &&
                    it.size.trim().equals(item.size.trim(), ignoreCase = true)
                }

                if (existing != null) {
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
                    dao.insertProduct(newProduct)
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
                        referenceNumber = transferRef,
                        notes = "فاتورة مشتريات #$finalInvoiceNumber | المورد: $finalSupplier",
                        timestamp = now
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
        exchangeAccount: ExchangeAccount? = null
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
        if (paidAmount > totalCost) {
            throw IllegalArgumentException("المبلغ المدفوع (${FormatUtils.formatCurrency(paidAmount)}) لا يمكن أن يتجاوز إجمالي الفاتورة (${FormatUtils.formatCurrency(totalCost)}).")
        }

        if (paymentMethod == "تحويل عبر الصرافة" || paymentMethod == "تحويل صرافة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة المدفوع منها قبل تأكيد الشراء.")
            }
        }
        if (paymentMethod == "نصف كاش ونصف حوالة") {
            if (exchangeAccount == null) {
                throw IllegalArgumentException("يجب اختيار حساب الصراف أو المحفظة للدفعة المحولة.")
            }
            if (kotlin.math.abs((cashPaidPart + transferPaidPart) - paidAmount) > 1.0) {
                throw IllegalArgumentException("مجموع الدفع النقدي ($cashPaidPart) والتحويل ($transferPaidPart) يجب أن يعادل المبلغ المدفوع ($paidAmount).")
            }
        }

        return runInTransaction {
            val activeShiftObj = dao.getActiveShift().firstOrNull()
            val shiftId = activeShiftObj?.id ?: 0L
            val now = System.currentTimeMillis()

            val multiplier = ClothingUnits.getMultiplier(unit)
            val totalPieces = (unitCount * multiplier).toInt().coerceAtLeast(1)
            val remainingDebt = totalCost - paidAmount

            val buyPricePerPiece = totalCost / totalPieces
            val sellPricePerPiece = (sellPricePerUnit * unitCount) / totalPieces

            val finalCashPaid = when (paymentMethod) {
                "نقداً من الصندوق", "نقداً (كاش)" -> paidAmount
                "نصف كاش ونصف حوالة" -> cashPaidPart
                else -> 0.0
            }
            val finalTransferPaid = when (paymentMethod) {
                "تحويل عبر الصرافة", "تحويل صرافة" -> paidAmount
                "نصف كاش ونصف حوالة" -> transferPaidPart
                else -> 0.0
            }

            val purchase = PurchaseRecord(
                supplierName = supplierName.ifBlank { "مورد عام" },
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
                invoiceNumber = invoiceNumber,
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
            val allProds = dao.getAllProducts().firstOrNull() ?: emptyList()
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
                    supplierName = supplierName.ifBlank { existing.supplierName },
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
                    supplierName = supplierName.ifBlank { "مورد عام" },
                    purchaseDate = now,
                    invoiceNumber = invoiceNumber,
                    barcode = barcode,
                    notes = notes,
                    unit = unit,
                    colorsCount = colorsCount,
                    colorDetails = colorDetails
                )
                dao.insertProduct(newProduct)
            }

            val unitDesc = if (unit == ClothingUnits.PIECE) "$totalPieces حبه" else "${unitCount.toInt()} $unit ($totalPieces حبه)"

            // 1. If cash paid -> Deduct from cash drawer
            if (finalCashPaid > 0) {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "PURCHASE_PAID",
                        amount = finalCashPaid,
                        isIncome = false,
                        title = "شراء بضاعة: $modelName ($unitDesc)",
                        details = "المورد: $supplierName | فاتورة: $invoiceNumber${if (finalTransferPaid > 0) " (جزء نقدي)" else ""}",
                        timestamp = now,
                        referencePurchaseId = purchaseId
                    )
                )
            }

            // 2. If transfer paid -> Deduct from exchange account
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
                        referenceNumber = invoiceNumber,
                        notes = "فاتورة شراء #${purchaseId} | المورد: $supplierName",
                        timestamp = now
                    )
                )
            }

            // 3. If remaining debt -> Record supplier debt
            if (remainingDebt > 0) {
                dao.insertDebt(
                    DebtRecord(
                        type = "SUPPLIER",
                        partyName = supplierName.ifBlank { "مورد" },
                        phone = "",
                        originalAmount = totalCost,
                        paidAmount = paidAmount,
                        remainingAmount = remainingDebt,
                        details = "متبقي شراء بضاعة: $modelName ($unitDesc) فاتورة #$invoiceNumber",
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
                    details = "فاتورة شراء #${purchaseId}: $modelName ($unitDesc) من $supplierName بإجمالي ${FormatUtils.formatCurrency(totalCost)}",
                    timestamp = now
                )
            )

            purchaseId
        }
    }

    // --- Delete / Reverse Purchase (Safeguarded against sold stock) ---
    suspend fun deletePurchase(purchaseId: Long) {
        runInTransaction {
            val purchase = dao.getPurchaseById(purchaseId) ?: return@runInTransaction

            // 1. Find product and verify stock has not already been sold
            val allProds = dao.getAllProducts().firstOrNull() ?: emptyList()
            val prod = allProds.find {
                it.modelName.trim().equals(purchase.modelName.trim(), ignoreCase = true) &&
                        it.color.trim().equals(purchase.color.trim(), ignoreCase = true) &&
                        it.size.trim().equals(purchase.size.trim(), ignoreCase = true)
            }
            if (prod != null) {
                if (prod.quantity < purchase.quantity) {
                    throw IllegalStateException(
                        "لا يمكن حذف فاتورة الشراء هذه لأن جزءاً من البضاعة تم بيعه بالفعل! " +
                        "(المتبقي في المخزن ${prod.quantity} حبة بينما الفاتورة تحتوي على ${purchase.quantity} حبة). " +
                        "يرجى تسوية المخزون عبر الجرد بدلاً من حذف الفاتورة."
                    )
                }
                val newQty = prod.quantity - purchase.quantity
                dao.updateStock(prod.id, newQty, System.currentTimeMillis())
            }

            // 2. Refund cash movements
            dao.deleteCashMovementsForPurchase(purchaseId)

            // 3. Refund exchange account if transfer was used
            if (purchase.transferPaid > 0 && purchase.exchangeAccountId > 0) {
                dao.updateExchangeBalance(purchase.exchangeAccountId, purchase.transferPaid)
            }

            // 4. Delete supplier debt if linked
            val debt = dao.getDebtByPurchaseId(purchaseId)
            if (debt != null) {
                dao.deleteDebt(debt.id)
            }

            // 5. Delete purchase record
            dao.deletePurchase(purchaseId)

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "إلغاء مشتريات",
                    actor = "المدير",
                    details = "تم إلغاء فاتورة الشراء #${purchase.id} للمنتج (${purchase.modelName}) واسترداد المبالغ المدفوعة.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updatePurchase(newPurchase: PurchaseRecord) {
        runInTransaction {
            val oldPurchase = dao.getPurchaseById(newPurchase.id) ?: throw IllegalArgumentException("فاتورة الشراء غير موجودة!")

            // 1. Stock adjustments
            val qtyDiff = newPurchase.quantity - oldPurchase.quantity
            val allProds = dao.getAllProducts().firstOrNull() ?: emptyList()
            val prod = allProds.find {
                it.modelName.trim().equals(oldPurchase.modelName.trim(), ignoreCase = true) &&
                        it.color.trim().equals(oldPurchase.color.trim(), ignoreCase = true) &&
                        it.size.trim().equals(oldPurchase.size.trim(), ignoreCase = true)
            }
            if (prod != null) {
                if (qtyDiff < 0 && prod.quantity < kotlin.math.abs(qtyDiff)) {
                    throw IllegalStateException("لا يمكن إنقاص كمية الشراء لأن جزءاً من البضاعة تم بيعه بالفعل من المخزن!")
                }
                dao.updateStock(prod.id, (prod.quantity + qtyDiff).coerceAtLeast(0), System.currentTimeMillis())
            }

            // 2. Cash adjustments
            val cashDiff = newPurchase.cashPaid - oldPurchase.cashPaid
            if (cashDiff != 0.0) {
                val now = System.currentTimeMillis()
                dao.insertCashMovement(
                    CashDrawerMovement(
                        type = "PURCHASE_PAID",
                        amount = kotlin.math.abs(cashDiff),
                        isIncome = cashDiff < 0,
                        title = "تعديل مشتريات: ${newPurchase.modelName}",
                        details = if (cashDiff > 0) "خصم فرق نقدي من الصندوق" else "استرجاع فرق نقدي للصندوق",
                        timestamp = now,
                        referencePurchaseId = newPurchase.id
                    )
                )
            }

            // 3. Transfer adjustments
            val transferDiff = newPurchase.transferPaid - oldPurchase.transferPaid
            if (transferDiff != 0.0 && newPurchase.exchangeAccountId > 0) {
                dao.updateExchangeBalance(newPurchase.exchangeAccountId, -transferDiff)
                dao.insertExchangeTransfer(
                    ExchangeTransfer(
                        accountId = newPurchase.exchangeAccountId,
                        accountName = newPurchase.exchangeAccountName,
                        type = "مشتريات تحويل",
                        amount = kotlin.math.abs(transferDiff),
                        isCredit = transferDiff < 0,
                        title = "تعديل تحويل مشتريات #${newPurchase.id}",
                        notes = "تعديل الفاتورة",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // 4. Supplier debt adjustment
            val oldDebt = dao.getDebtByPurchaseId(newPurchase.id)
            if (newPurchase.remainingDebt > 0) {
                if (oldDebt != null) {
                    val debtDiff = newPurchase.remainingDebt - oldPurchase.remainingDebt
                    dao.updateDebt(
                        oldDebt.copy(
                            partyName = newPurchase.supplierName,
                            originalAmount = oldDebt.originalAmount + debtDiff,
                            remainingAmount = (oldDebt.remainingAmount + debtDiff).coerceAtLeast(0.0),
                            isSettled = (oldDebt.remainingAmount + debtDiff) <= 0.01
                        )
                    )
                } else {
                    dao.insertDebt(
                        DebtRecord(
                            type = "SUPPLIER",
                            partyName = newPurchase.supplierName,
                            originalAmount = newPurchase.remainingDebt,
                            remainingAmount = newPurchase.remainingDebt,
                            details = "دين مورد معدل من فاتورة #${newPurchase.id}",
                            referencePurchaseId = newPurchase.id
                        )
                    )
                }
            } else if (oldDebt != null) {
                dao.deleteDebt(oldDebt.id)
            }

            // 5. Update purchase record
            dao.updatePurchase(newPurchase)

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "تعديل مشتريات",
                    actor = "المدير",
                    details = "تم تعديل فاتورة الشراء #${newPurchase.id} للمورد (${newPurchase.supplierName})",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Expenses ---
    suspend fun recordExpense(
        category: String,
        amount: Double,
        notes: String,
        receiptNumber: String,
        paidFromCash: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        employee: Employee? = null
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

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
            exchangeAccountId = exchangeAccount?.id ?: 0L,
            exchangeAccountName = exchangeAccount?.name ?: "",
            shiftId = shiftId
        )
        val id = dao.insertExpense(expense)

        // If this expense is for an employee daily stipend, record in employee statement
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
                exchangeAccountId = exchangeAccount?.id ?: 0L,
                exchangeAccountName = exchangeAccount?.name ?: ""
            )
            dao.insertEmployeeTransaction(empTx)
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
                    referenceExpenseId = id
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
                    timestamp = now
                )
            )
        }
        return id
    }

    suspend fun updateExpense(newExpense: ExpenseRecord) {
        val oldExpense = dao.getExpenseById(newExpense.id) ?: run {
            dao.updateExpense(newExpense)
            return
        }

        // Revert old financial impact
        if (oldExpense.paidFromCash) {
            dao.deleteCashMovementsForExpense(oldExpense.id)
        } else if (oldExpense.exchangeAccountId > 0) {
            dao.updateExchangeBalance(oldExpense.exchangeAccountId, oldExpense.amount)
        }

        // Apply new financial impact
        if (newExpense.paidFromCash) {
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val isStipend = newExpense.category == "صرفة يومية لعامل" || newExpense.category.contains("صرفة")
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = if (isStipend) "SALARY_PAID" else "EXPENSE_PAID",
                    amount = newExpense.amount,
                    isIncome = false,
                    title = "مصروف: ${newExpense.category}",
                    details = newExpense.notes.ifBlank { "وصل: ${newExpense.receiptNumber}" },
                    timestamp = newExpense.timestamp,
                    referenceExpenseId = newExpense.id
                )
            )
        } else if (newExpense.exchangeAccountId > 0) {
            dao.updateExchangeBalance(newExpense.exchangeAccountId, -newExpense.amount)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = newExpense.exchangeAccountId,
                    accountName = newExpense.exchangeAccountName,
                    type = "سحب مصروف",
                    amount = newExpense.amount,
                    isCredit = false,
                    title = "مصروف من الصراف: ${newExpense.category}",
                    referenceNumber = newExpense.receiptNumber,
                    notes = newExpense.notes,
                    timestamp = newExpense.timestamp
                )
            )
        }

        dao.updateExpense(newExpense)
    }

    suspend fun deleteExpense(id: Long) {
        val exp = dao.getExpenseById(id)
        dao.deleteCashMovementsForExpense(id)
        if (exp != null && exp.exchangeAccountId > 0) {
            dao.updateExchangeBalance(exp.exchangeAccountId, exp.amount)
        }
        dao.deleteExpense(id)
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

    suspend fun deleteEmployee(employeeId: Long) {
        dao.deleteEmployee(employeeId)
    }

    suspend fun updateEmployeeTransaction(newTx: EmployeeTransaction) {
        val oldTx = dao.getEmployeeTransactionById(newTx.id) ?: run {
            dao.updateEmployeeTransaction(newTx)
            return
        }

        // Revert old impact
        if (oldTx.paidFromCash) {
            dao.deleteCashMovementsForEmployeeTx(oldTx.id)
        } else if (oldTx.exchangeAccountId > 0) {
            dao.updateExchangeBalance(oldTx.exchangeAccountId, oldTx.amount)
        }

        // Apply new impact
        if (newTx.paidFromCash) {
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val isSalary = newTx.type == "صرف راتب"
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = if (isSalary) "SALARY_PAID" else "ADVANCE_PAID",
                    amount = newTx.amount,
                    isIncome = false,
                    title = "${newTx.type}: ${newTx.employeeName}",
                    details = newTx.notes,
                    timestamp = newTx.timestamp,
                    referenceEmployeeTxId = newTx.id
                )
            )
        } else if (newTx.exchangeAccountId > 0) {
            dao.updateExchangeBalance(newTx.exchangeAccountId, -newTx.amount)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = newTx.exchangeAccountId,
                    accountName = newTx.exchangeAccountName,
                    type = "سحب لموظف",
                    amount = newTx.amount,
                    isCredit = false,
                    title = "${newTx.type}: ${newTx.employeeName}",
                    referenceNumber = "",
                    notes = newTx.notes,
                    timestamp = newTx.timestamp
                )
            )
        }

        dao.updateEmployeeTransaction(newTx)
    }

    suspend fun deleteEmployeeTransaction(txId: Long) {
        val tx = dao.getEmployeeTransactionById(txId)
        dao.deleteCashMovementsForEmployeeTx(txId)
        if (tx != null && !tx.paidFromCash && tx.exchangeAccountId > 0) {
            dao.updateExchangeBalance(tx.exchangeAccountId, tx.amount)
        }
        dao.deleteEmployeeTransaction(txId)
    }

    suspend fun recordEmployeeTransaction(
        employeeId: Long,
        employeeName: String,
        type: String,
        amount: Double,
        notes: String,
        paidFromCashDrawer: Boolean,
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = ""
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val tx = EmployeeTransaction(
            employeeId = employeeId,
            employeeName = employeeName,
            type = type,
            amount = amount,
            notes = if (referenceNumber.isNotBlank()) "مرجع الحوالة: $referenceNumber | $notes" else notes,
            timestamp = now,
            shiftId = shiftId,
            paidFromCash = paidFromCashDrawer,
            exchangeAccountId = exchangeAccount?.id ?: 0L,
            exchangeAccountName = exchangeAccount?.name ?: ""
        )
        val id = dao.insertEmployeeTransaction(tx)

        if (paidFromCashDrawer && (type == "سلفة" || type == "صرف راتب" || type == "مكافأة")) {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "SALARY_PAID",
                    amount = amount,
                    isIncome = false,
                    title = "$type للعامل: $employeeName",
                    details = notes,
                    timestamp = now,
                    referenceEmployeeTxId = id
                )
            )
        } else if (exchangeAccount != null && (type == "سلفة" || type == "صرف راتب" || type == "مكافأة")) {
            dao.updateExchangeBalance(exchangeAccount.id, -amount)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = exchangeAccount.id,
                    accountName = exchangeAccount.name,
                    type = type,
                    amount = amount,
                    isCredit = false,
                    title = "$type للعامل: $employeeName",
                    referenceNumber = referenceNumber,
                    notes = notes,
                    timestamp = now
                )
            )
        }
        return id
    }

    suspend fun resolveVoucherNumber(input: String, prefix: String = "SND"): String {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) return trimmed

        val existingPayments = dao.getAllDebtPayments().firstOrNull().orEmpty()
        val existingTransfers = dao.getAllExchangeTransfers().firstOrNull().orEmpty()
        val existingNumbers = buildSet {
            existingPayments.forEach { if (it.voucherNumber.isNotBlank()) add(it.voucherNumber.trim()) }
            existingTransfers.forEach { if (it.referenceNumber.isNotBlank()) add(it.referenceNumber.trim()) }
        }
        val regex = Regex("^${Regex.escape(prefix)}-(\\d+)$")
        val maxSuffix = existingNumbers.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0
        var candidateNum = maxOf(maxSuffix + 1, existingPayments.size + 1)
        while (true) {
            val candidate = "$prefix-${candidateNum.toString().padStart(4, '0')}"
            if (candidate !in existingNumbers) return candidate
            candidateNum++
        }
    }

    // --- Debts & Collections ---
    suspend fun recordDebtPayment(
        debt: DebtRecord,
        paymentAmount: Double,
        notes: String,
        paymentMethod: String = "نقداً", // "نقداً" or "صرافة/محفظة"
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = ""
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()
        val finalVoucher = resolveVoucherNumber(referenceNumber, "SND")

        val newPaid = debt.paidAmount + paymentAmount
        val newRemaining = (debt.originalAmount - newPaid).coerceAtLeast(0.0)
        val isSettled = newRemaining <= 0.001

        dao.updateDebt(
            debt.copy(
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
            debtId = debt.id,
            partyName = debt.partyName,
            amount = paymentAmount,
            timestamp = now,
            notes = paymentNotes,
            shiftId = shiftId,
            paymentMethod = paymentMethod,
            exchangeAccountId = exchangeAccount?.id ?: 0L,
            voucherNumber = finalVoucher
        )
        val paymentId = dao.insertDebtPayment(payment)

        if (debt.type == "CUSTOMER") {
            if (paymentMethod == "نقداً") {
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = shiftId,
                        type = "DEBT_COLLECTED",
                        amount = paymentAmount,
                        isIncome = true,
                        title = "تحصيل زلط (سند #$finalVoucher): ${debt.partyName}",
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
                        title = "تحصيل دين عبر الصراف (سند #$finalVoucher): ${debt.partyName}",
                        referenceNumber = finalVoucher,
                        notes = paymentNotes,
                        timestamp = now
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
                        title = "تسديد دين للمورد (سند #$finalVoucher): ${debt.partyName}",
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
                        title = "تسديد دين للمورد عبر الصراف (سند #$finalVoucher): ${debt.partyName}",
                        referenceNumber = finalVoucher,
                        notes = paymentNotes,
                        timestamp = now
                    )
                )
            }
        }

        return paymentId
    }

    suspend fun addNewDebt(debt: DebtRecord): Long = dao.insertDebt(debt)
    suspend fun updateDebt(debt: DebtRecord) = dao.updateDebt(debt)
    suspend fun deleteDebt(id: Long) = dao.deleteDebt(id)

    // --- Parties Management (Customers & Suppliers) ---
    suspend fun saveParty(party: PartyAccount, initialDebt: Double = 0.0, debtNotes: String = ""): Long {
        val now = System.currentTimeMillis()
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

    suspend fun deleteParty(id: Long) {
        dao.deleteParty(id)
    }

    suspend fun recordPartyPayment(
        partyName: String,
        partyType: String,
        totalPayment: Double,
        notes: String,
        paymentMethod: String = "نقداً",
        exchangeAccount: ExchangeAccount? = null,
        referenceNumber: String = ""
    ): Boolean {
        if (totalPayment <= 0) return false
        val allDebtsList = dao.getAllDebts().firstOrNull() ?: emptyList()
        val partyDebts = allDebtsList.filter {
            it.partyName.trim().equals(partyName.trim(), ignoreCase = true) &&
            it.type == partyType &&
            !it.isSettled
        }.sortedBy { it.createdAt }

        val finalVoucher = resolveVoucherNumber(referenceNumber, "SND")
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
                referenceNumber = finalVoucher
            )
            remainingToApply -= paymentForThisDebt
        }
        return true
    }

    suspend fun recordCollectionReceipt(
        payerName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumberInput: String,
        paymentMethod: String, // "نقداً" or "صرافة/محفظة"
        exchangeAccount: ExchangeAccount? = null,
        notes: String = ""
    ): CollectionReceiptResult {
        require(amount > 0.0) { "يرجى إدخال مبلغ التحصيل" }
        require(paymentMethod == "نقداً" || exchangeAccount != null) { "اختر حساب الصرافة أو المحفظة" }

        return runInTransaction {
            val now = System.currentTimeMillis()
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val finalVoucher = resolveVoucherNumber(voucherNumberInput, "SND")
            val rawPayer = (party?.name ?: payerName).trim()
            val finalPayer = rawPayer.ifBlank { "تحصيل نقدي عام" }

            var appliedToDebt = 0.0
            var remainingToCollect = amount

            if (rawPayer.isNotBlank()) {
                val allDebtsList = dao.getAllDebts().firstOrNull().orEmpty()
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
                            referenceNumber = finalVoucher
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
                dao.insertDebtPayment(
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
                            timestamp = now
                        )
                    )
                }
            }

            val updatedDebtsList = dao.getAllDebts().firstOrNull().orEmpty()
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
        notes: String = ""
    ): SupplierPaymentReceiptResult {
        require(amount > 0.0) { "يرجى إدخال مبلغ السداد" }
        require(paymentMethod == "نقداً" || exchangeAccount != null) { "اختر حساب الصرافة أو المحفظة" }

        return runInTransaction {
            val now = System.currentTimeMillis()
            val shiftId = dao.getActiveShift().firstOrNull()?.id ?: 0L
            val finalVoucher = resolveVoucherNumber(voucherNumberInput, "SRF")
            val rawSupplier = (party?.name ?: supplierName).trim()
            val finalSupplier = rawSupplier.ifBlank { "مورد عام" }

            var appliedToDebt = 0.0
            var remainingToPay = amount

            if (rawSupplier.isNotBlank()) {
                val allDebtsList = dao.getAllDebts().firstOrNull().orEmpty()
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
                            referenceNumber = finalVoucher
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
                dao.insertDebtPayment(
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
                            timestamp = now
                        )
                    )
                }
            }

            val updatedDebtsList = dao.getAllDebts().firstOrNull().orEmpty()
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
        return if (account.id == 0L) {
            dao.insertExchangeAccount(account)
        } else {
            dao.updateExchangeAccount(account)
            account.id
        }
    }

    suspend fun deleteExchangeAccount(id: Long) {
        dao.deleteExchangeAccount(id)
    }

    suspend fun transferBetweenCashAndExchange(
        accountId: Long,
        amount: Double,
        fromCashToExchange: Boolean,
        notes: String,
        referenceNumber: String = ""
    ) {
        val account = dao.getExchangeAccountById(accountId) ?: return
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val fullNotes = if (referenceNumber.isNotBlank()) "رقم السند/الحوالة: $referenceNumber | $notes" else notes

        if (fromCashToExchange) {
            // Deduct from cash drawer -> Add to exchange account
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
                    timestamp = now
                )
            )
        } else {
            // Withdraw from exchange account -> Add to cash drawer
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
                    timestamp = now
                )
            )
        }
    }

    // --- Shifts & Drawer ---
    suspend fun openShift(cashierName: String, openingBalance: Double, notes: String): Long {
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

    suspend fun recordOwnerWithdrawal(amount: Double, reason: String) {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

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

    suspend fun recordManualDeposit(amount: Double, reason: String) {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

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

    suspend fun clearEverythingCompletely(clearConfigDictionaries: Boolean = false) {
        clearTransactionsOnly()
        dao.deleteAllProducts()
        dao.deleteAllEmployees()
        dao.deleteAllExchangeAccounts()
        dao.deleteAllParties()
        dao.deleteAllAuditLogs()
        if (clearConfigDictionaries) {
            dao.deleteAllConfigItems()
        }
    }

    suspend fun executeStoreFullAudit(
        actualDrawerCash: Double,
        exchangeBalances: Map<Long, Double>,
        resetTransactions: Boolean,
        notes: String
    ) {
        val now = System.currentTimeMillis()
        if (resetTransactions) {
            clearTransactionsOnly()
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

        // Update all exchange account balances
        exchangeBalances.forEach { (accountId, balance) ->
            dao.setExchangeBalance(accountId, balance)
            val acc = dao.getExchangeAccountById(accountId)
            dao.insertExchangeTransfer(
                ExchangeTransfer(
                    accountId = accountId,
                    accountName = acc?.name ?: "حساب صراف",
                    type = "تعديل رصيد",
                    amount = balance,
                    isCredit = true,
                    title = "رصيد معتمد من جرد المحل",
                    referenceNumber = "AUDIT",
                    notes = notes,
                    timestamp = now
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
        runInTransaction {
            // 1. Wipe existing data
            clearEverythingCompletely()

            // 2. Restore Products
            if (productsJson != null) {
                for (i in 0 until productsJson.length()) {
                    val obj = productsJson.getJSONObject(i)
                    dao.insertProduct(
                        ProductItem(
                            category = obj.optString("category", "أخرى"),
                            modelName = obj.optString("modelName", "صنف"),
                            color = obj.optString("color", "عام"),
                            size = obj.optString("size", "حر"),
                            quantity = obj.optInt("quantity", 0),
                            buyPrice = obj.optDouble("buyPrice", 0.0),
                            sellPrice = obj.optDouble("sellPrice", 0.0),
                            supplierName = obj.optString("supplierName", ""),
                            purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                            invoiceNumber = obj.optString("invoiceNumber", ""),
                            barcode = obj.optString("barcode", ""),
                            notes = obj.optString("notes", ""),
                            unit = obj.optString("unit", ClothingUnits.PIECE),
                            colorsCount = obj.optInt("colorsCount", 1),
                            colorDetails = obj.optString("colorDetails", "")
                        )
                    )
                }
            }

            // 3. Restore Exchange Accounts
            if (accountsJson != null) {
                for (i in 0 until accountsJson.length()) {
                    val obj = accountsJson.getJSONObject(i)
                    dao.insertExchangeAccount(
                        ExchangeAccount(
                            name = obj.optString("name", "حساب"),
                            accountNumber = obj.optString("accountNumber", ""),
                            phone = obj.optString("phone", ""),
                            balance = obj.optDouble("balance", 0.0),
                            type = obj.optString("type", "صراف"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
            }

            // 4. Restore Parties
            if (partiesJson != null) {
                for (i in 0 until partiesJson.length()) {
                    val obj = partiesJson.getJSONObject(i)
                    dao.insertParty(
                        PartyAccount(
                            type = obj.optString("type", "CUSTOMER"),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            notes = obj.optString("notes", ""),
                            initialBalance = obj.optDouble("initialBalance", 0.0)
                        )
                    )
                }
            }

            // 5. Restore Debts
            if (debtsJson != null) {
                for (i in 0 until debtsJson.length()) {
                    val obj = debtsJson.getJSONObject(i)
                    dao.insertDebt(
                        DebtRecord(
                            type = obj.optString("type", "CUSTOMER"),
                            partyName = obj.optString("partyName", ""),
                            phone = obj.optString("phone", ""),
                            originalAmount = obj.optDouble("originalAmount", 0.0),
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            remainingAmount = obj.optDouble("remainingAmount", 0.0),
                            details = obj.optString("details", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            isSettled = obj.optBoolean("isSettled", false)
                        )
                    )
                }
            }

            // 6. Restore Sales
            if (salesJson != null) {
                for (i in 0 until salesJson.length()) {
                    val obj = salesJson.getJSONObject(i)
                    dao.insertSale(
                        SaleTransaction(
                            productId = obj.optLong("productId", 0L),
                            productName = obj.optString("productName", ""),
                            category = obj.optString("category", ""),
                            color = obj.optString("color", ""),
                            size = obj.optString("size", ""),
                            quantity = obj.optInt("quantity", 1),
                            unitBuyPrice = obj.optDouble("unitBuyPrice", 0.0),
                            unitSellPrice = obj.optDouble("unitSellPrice", 0.0),
                            discount = obj.optDouble("discount", 0.0),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            profit = obj.optDouble("profit", 0.0),
                            customerName = obj.optString("customerName", "زبون نقدي"),
                            customerPhone = obj.optString("customerPhone", ""),
                            paymentType = obj.optString("paymentType", "نقداً"),
                            cashAmount = obj.optDouble("cashAmount", 0.0),
                            transferAmount = obj.optDouble("transferAmount", 0.0),
                            exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                            exchangeAccountName = obj.optString("exchangeAccountName", ""),
                            transferRef = obj.optString("transferRef", ""),
                            employeeName = obj.optString("employeeName", "صاحب المحل"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            unit = obj.optString("unit", ClothingUnits.PIECE)
                        )
                    )
                }
            }

            // 7. Restore Purchases
            if (purchasesJson != null) {
                for (i in 0 until purchasesJson.length()) {
                    val obj = purchasesJson.getJSONObject(i)
                    dao.insertPurchase(
                        PurchaseRecord(
                            supplierName = obj.optString("supplierName", "مورد"),
                            category = obj.optString("category", ""),
                            modelName = obj.optString("modelName", ""),
                            color = obj.optString("color", ""),
                            size = obj.optString("size", ""),
                            quantity = obj.optInt("quantity", 1),
                            unitBuyPrice = obj.optDouble("unitBuyPrice", 0.0),
                            unitSellPrice = obj.optDouble("unitSellPrice", 0.0),
                            totalCost = obj.optDouble("totalCost", 0.0),
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            remainingDebt = obj.optDouble("remainingDebt", 0.0),
                            invoiceNumber = obj.optString("invoiceNumber", ""),
                            paymentMethod = obj.optString("paymentMethod", "نقداً من الصندوق"),
                            cashPaid = obj.optDouble("cashPaid", 0.0),
                            transferPaid = obj.optDouble("transferPaid", 0.0),
                            exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                            exchangeAccountName = obj.optString("exchangeAccountName", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            unit = obj.optString("unit", ClothingUnits.PIECE),
                            unitCount = obj.optDouble("unitCount", 1.0)
                        )
                    )
                }
            }

            // 8. Restore Expenses
            if (expensesJson != null) {
                for (i in 0 until expensesJson.length()) {
                    val obj = expensesJson.getJSONObject(i)
                    dao.insertExpense(
                        ExpenseRecord(
                            category = obj.optString("category", "مصروف"),
                            amount = obj.optDouble("amount", 0.0),
                            notes = obj.optString("notes", ""),
                            receiptNumber = obj.optString("receiptNumber", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            paidFromCash = obj.optBoolean("paidFromCash", true),
                            exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                            exchangeAccountName = obj.optString("exchangeAccountName", "")
                        )
                    )
                }
            }

            dao.insertAuditLog(
                AuditLogRecord(
                    action = "استعادة نسخة احتياطية",
                    actor = "المدير",
                    details = "تمت استعادة البيانات بنجاح من ملف النسخة الاحتياطية",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
