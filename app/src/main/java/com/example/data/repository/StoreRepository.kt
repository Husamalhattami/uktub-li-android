package com.example.data.repository

import com.example.data.dao.StoreDao
import com.example.data.model.*
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class StoreRepository(private val dao: StoreDao) {

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

    suspend fun seedInitialDataIfEmpty() {
        val existing = dao.getAllProducts().firstOrNull()
        if (existing.isNullOrEmpty()) {
            val now = System.currentTimeMillis()

            // Seed menswear products in Riyals with proper sizes (كبير، وسط، صغير، شبابي)
            // and units (حبه، نص درزن، درزن، كورجه) and color assortments
            val p1 = ProductItem(
                category = "بشت",
                modelName = "بشت ملكي صيفي فاخر",
                color = "3 ألوان: أسود، كحلي، بيج",
                size = ClothingSizes.BIG,
                quantity = 24, // 2 درزن = 24 حبة
                buyPrice = 280.0,
                sellPrice = 450.0,
                supplierName = "معمل الأصالة للأقمشة",
                purchaseDate = now - 86400000L * 3,
                invoiceNumber = "INV-101",
                barcode = "BSH-001",
                notes = "تشكيلة درزن ملكي صيفي",
                unit = ClothingUnits.DOZEN,
                colorsCount = 3,
                colorDetails = "أسود، كحلي، بيج"
            )
            val p2 = ProductItem(
                category = "كوت",
                modelName = "كوت رجالي صوف كلاسيك",
                color = "لونين: كحلي، رصاصي",
                size = ClothingSizes.MEDIUM,
                quantity = 12, // 1 درزن = 12 حبة
                buyPrice = 160.0,
                sellPrice = 280.0,
                supplierName = "شركة النخبة للاستيراد",
                purchaseDate = now - 86400000L * 5,
                invoiceNumber = "INV-102",
                barcode = "COT-002",
                notes = "درزن مشكل ألوان كوت شتوي",
                unit = ClothingUnits.DOZEN,
                colorsCount = 2,
                colorDetails = "كحلي، رصاصي"
            )
            val p3 = ProductItem(
                category = "جاكت",
                modelName = "جاكيت سبورت كاجوال",
                color = "3 ألوان: بيج، زيتي، أسود",
                size = ClothingSizes.YOUTH,
                quantity = 6, // نص درزن = 6 حبات
                buyPrice = 90.0,
                sellPrice = 160.0,
                supplierName = "معرض أزياء الشباب",
                purchaseDate = now - 86400000L * 2,
                invoiceNumber = "INV-103",
                barcode = "JCK-003",
                notes = "نص درزن شبابي حديث",
                unit = ClothingUnits.HALF_DOZEN,
                colorsCount = 3,
                colorDetails = "بيج، زيتي، أسود"
            )
            val p4 = ProductItem(
                category = "ثوب",
                modelName = "ثوب كويتي مطرز راقي",
                color = "لون واحد: أبيض ناصع",
                size = ClothingSizes.BIG,
                quantity = 20, // 1 كورجة = 20 حبة
                buyPrice = 65.0,
                sellPrice = 120.0,
                supplierName = "وكالة أقمشة الخليج",
                purchaseDate = now - 86400000L * 4,
                invoiceNumber = "INV-104",
                barcode = "THB-004",
                notes = "كورجة أثواب أبيض ناصع",
                unit = ClothingUnits.KORJAH,
                colorsCount = 1,
                colorDetails = "أبيض ناصع"
            )
            val p5 = ProductItem(
                category = "دشداشة",
                modelName = "دشداشة شتوية دافئة",
                color = "لونين: رمادي، سكري",
                size = ClothingSizes.SMALL,
                quantity = 3, // قارب على النفاد
                buyPrice = 75.0,
                sellPrice = 135.0,
                supplierName = "معمل الفرسان للملبوسات",
                purchaseDate = now - 86400000L * 10,
                invoiceNumber = "INV-105",
                barcode = "DSH-005",
                notes = "متبقي 3 حبات فقط - قارب على النفاد",
                unit = ClothingUnits.PIECE,
                colorsCount = 2,
                colorDetails = "رمادي، سكري"
            )

            dao.insertProduct(p1)
            dao.insertProduct(p2)
            dao.insertProduct(p3)
            dao.insertProduct(p4)
            dao.insertProduct(p5)

            // Employees with realistic Riyal salaries
            val emp1 = Employee(
                name = "أبو فهد",
                phone = "0501234567",
                role = "بائع صالة رئيسي",
                baseSalary = 3500.0,
                salaryType = "شهري",
                commissionPercent = 2.0
            )
            val emp2 = Employee(
                name = "وسام الكرخي",
                phone = "0559876543",
                role = "مساعد مبيعات وصندوق",
                baseSalary = 2800.0,
                salaryType = "شهري",
                commissionPercent = 1.5
            )
            val e1Id = dao.insertEmployee(emp1)
            val e2Id = dao.insertEmployee(emp2)

            // Initial open shift in Riyals
            val shift = CashDrawerShift(
                openedAt = now - 3600000L * 4,
                openingBalance = 1500.0,
                isOpen = true,
                cashierName = "صاحب المحل",
                notes = "افتتاح الوردية الصباحية"
            )
            val shiftId = dao.insertShift(shift)

            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "CAPITAL_DEPOSIT",
                    amount = 1500.0,
                    isIncome = true,
                    title = "رصيد افتتاح الوردية",
                    details = "بداية اليوم",
                    timestamp = shift.openedAt
                )
            )

            // Demo sale
            val demoSale = SaleTransaction(
                productId = 4,
                productName = "ثوب كويتي مطرز راقي",
                category = "ثوب",
                color = "لون واحد: أبيض ناصع",
                size = ClothingSizes.BIG,
                quantity = 2, // 2 حبة
                unitBuyPrice = 65.0,
                unitSellPrice = 120.0,
                discount = 10.0,
                totalAmount = 230.0, // (120 * 2) - 10
                profit = 100.0, // 230 - (65 * 2) = 100
                customerName = "عبد الله السالم",
                customerPhone = "0541239876",
                paymentType = "نقداً",
                employeeName = "أبو فهد",
                employeeId = e1Id,
                timestamp = now - 3600000L * 2,
                shiftId = shiftId,
                unit = ClothingUnits.PIECE
            )
            dao.insertSale(demoSale)
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "SALE_CASH",
                    amount = 230.0,
                    isIncome = true,
                    title = "بيع نقدي: ثوب كويتي مطرز راقي (2 حبه)",
                    details = "الزبون: عبد الله السالم",
                    timestamp = demoSale.timestamp
                )
            )

            // Sample expense in Riyals
            val demoExpense = ExpenseRecord(
                category = "الضيافة والشاي",
                amount = 45.0,
                notes = "شاي وضيافة زبائن المعرض",
                receiptNumber = "EXP-01",
                timestamp = now - 3600000L,
                paidFromCash = true,
                shiftId = shiftId
            )
            dao.insertExpense(demoExpense)
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "EXPENSE_PAID",
                    amount = 45.0,
                    isIncome = false,
                    title = "مصروف: الضيافة والشاي",
                    details = "شاي وضيافة زبائن المعرض",
                    timestamp = demoExpense.timestamp
                )
            )

            // Sample customer debt in Riyals
            val demoDebt = DebtRecord(
                type = "CUSTOMER",
                partyName = "أحمد التميمي",
                phone = "0561122334",
                originalAmount = 320.0,
                paidAmount = 120.0,
                remainingAmount = 200.0,
                details = "شراء جاكيت سبورت - دفعة 120 ريال والمتبقي 200 ريال",
                dueDate = now + 86400000L * 5,
                createdAt = now - 86400000L,
                isSettled = false
            )
            dao.insertDebt(demoDebt)
        }
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

    // --- Sale Execution ---
    suspend fun executeSale(
        product: ProductItem,
        quantity: Int, // Number of chosen units (e.g. 1 حبة or 1 درزن)
        sellPrice: Double, // Selling price for this unit
        discount: Double,
        customerName: String,
        customerPhone: String,
        paymentType: String,
        employee: Employee?,
        unit: String = ClothingUnits.PIECE
    ): SaleTransaction {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val multiplier = ClothingUnits.getMultiplier(unit)
        val totalPieces = quantity * multiplier

        val totalAmount = (sellPrice * quantity) - discount
        val costOfGoods = product.buyPrice * totalPieces
        val profit = totalAmount - costOfGoods

        // 1. Record Sale
        val sale = SaleTransaction(
            productId = product.id,
            productName = product.modelName,
            category = product.category,
            color = product.color,
            size = product.size,
            quantity = totalPieces,
            unitBuyPrice = product.buyPrice,
            unitSellPrice = sellPrice / multiplier,
            discount = discount,
            totalAmount = totalAmount,
            profit = profit,
            customerName = customerName.ifBlank { "زبون نقدي" },
            customerPhone = customerPhone,
            paymentType = paymentType,
            employeeName = employee?.name ?: "صاحب المحل",
            employeeId = employee?.id ?: 0L,
            timestamp = now,
            shiftId = shiftId,
            unit = unit
        )
        val saleId = dao.insertSale(sale)

        // 2. Deduct Inventory Stock in Pieces
        val newStock = (product.quantity - totalPieces).coerceAtLeast(0)
        dao.updateStock(product.id, newStock, now)

        // 3. Handle Payment Method
        val unitDesc = if (unit == ClothingUnits.PIECE) "$quantity حبه" else "$quantity $unit ($totalPieces حبه)"
        if (paymentType == "نقداً") {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "SALE_CASH",
                    amount = totalAmount,
                    isIncome = true,
                    title = "مبيعات نقدية: ${product.modelName} ($unitDesc)",
                    details = "الزبون: ${customerName.ifBlank { "نقدي" }} | البائع: ${employee?.name ?: "المالك"}",
                    timestamp = now
                )
            )
        } else {
            // بالدين
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

        return sale.copy(id = saleId)
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
        colorDetails: String
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val multiplier = ClothingUnits.getMultiplier(unit)
        val totalPieces = (unitCount * multiplier).toInt().coerceAtLeast(1)

        val totalCost = buyPricePerUnit * unitCount
        val remainingDebt = (totalCost - paidAmount).coerceAtLeast(0.0)

        val buyPricePerPiece = totalCost / totalPieces
        val sellPricePerPiece = (sellPricePerUnit * unitCount) / totalPieces

        // 1. Record purchase
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
            timestamp = now,
            unit = unit,
            unitCount = unitCount,
            colorsCount = colorsCount
        )
        val purchaseId = dao.insertPurchase(purchase)

        // 2. Add or update product stock
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

        // 3. If paid from cash drawer
        val unitDesc = if (unit == ClothingUnits.PIECE) "$totalPieces حبه" else "${unitCount.toInt()} $unit ($totalPieces حبه)"
        if (paidAmount > 0 && paymentMethod.contains("الصندوق")) {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "PURCHASE_PAID",
                    amount = paidAmount,
                    isIncome = false,
                    title = "شراء بضاعة: $modelName ($unitDesc)",
                    details = "المورد: $supplierName | فاتورة: $invoiceNumber",
                    timestamp = now
                )
            )
        }

        // 4. If there's remaining debt to supplier
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

        return purchaseId
    }

    // --- Expenses ---
    suspend fun recordExpense(
        category: String,
        amount: Double,
        notes: String,
        receiptNumber: String,
        paidFromCash: Boolean
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val expense = ExpenseRecord(
            category = category,
            amount = amount,
            notes = notes,
            receiptNumber = receiptNumber,
            timestamp = now,
            paidFromCash = paidFromCash,
            shiftId = shiftId
        )
        val id = dao.insertExpense(expense)

        if (paidFromCash) {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "EXPENSE_PAID",
                    amount = amount,
                    isIncome = false,
                    title = "مصروف: $category",
                    details = notes.ifBlank { "وصل: $receiptNumber" },
                    timestamp = now
                )
            )
        }
        return id
    }

    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)

    // --- Employees & Payroll ---
    suspend fun saveEmployee(employee: Employee): Long {
        return if (employee.id == 0L) {
            dao.insertEmployee(employee)
        } else {
            dao.updateEmployee(employee)
            employee.id
        }
    }

    suspend fun recordEmployeeTransaction(
        employeeId: Long,
        employeeName: String,
        type: String,
        amount: Double,
        notes: String,
        paidFromCashDrawer: Boolean
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

        val tx = EmployeeTransaction(
            employeeId = employeeId,
            employeeName = employeeName,
            type = type,
            amount = amount,
            notes = notes,
            timestamp = now,
            shiftId = shiftId
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
                    timestamp = now
                )
            )
        }
        return id
    }

    // --- Debts & Collections ---
    suspend fun recordDebtPayment(
        debt: DebtRecord,
        paymentAmount: Double,
        notes: String
    ): Long {
        val activeShiftObj = dao.getActiveShift().firstOrNull()
        val shiftId = activeShiftObj?.id ?: 0L
        val now = System.currentTimeMillis()

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

        val payment = DebtPayment(
            debtId = debt.id,
            partyName = debt.partyName,
            amount = paymentAmount,
            timestamp = now,
            notes = notes,
            shiftId = shiftId
        )
        val paymentId = dao.insertDebtPayment(payment)

        if (debt.type == "CUSTOMER") {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "DEBT_COLLECTED",
                    amount = paymentAmount,
                    isIncome = true,
                    title = "تحصيل دين من الزبون: ${debt.partyName}",
                    details = "المتبقي بعد الدفع: ${newRemaining.toLong()} ريال",
                    timestamp = now
                )
            )
        } else {
            dao.insertCashMovement(
                CashDrawerMovement(
                    shiftId = shiftId,
                    type = "SUPPLIER_PAID",
                    amount = paymentAmount,
                    isIncome = false,
                    title = "تسديد دين للمورد: ${debt.partyName}",
                    details = "المتبقي للمورد: ${newRemaining.toLong()} ريال",
                    timestamp = now
                )
            )
        }

        return paymentId
    }

    suspend fun addNewDebt(debt: DebtRecord): Long = dao.insertDebt(debt)
    suspend fun deleteDebt(id: Long) = dao.deleteDebt(id)

    // --- Cash Drawer & Shift Management ---
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
}
