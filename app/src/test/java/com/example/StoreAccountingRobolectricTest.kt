package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.StatementHelper
import com.example.ui.util.StatementMode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreAccountingRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository(db.storeDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExecuteSalePreventsNegativeStock() = runBlocking {
        // Seed 1 product with 5 pieces
        val product = ProductItem(
            category = "ثوب",
            modelName = "ثوب كويتي",
            color = "أبيض",
            size = ClothingSizes.BIG,
            quantity = 5,
            buyPrice = 50.0,
            sellPrice = 100.0
        )
        val pId = db.storeDao().insertProduct(product)
        val freshProduct = product.copy(id = pId)

        // Try to sell 10 pieces -> should throw exception
        var exceptionThrown = false
        try {
            repository.executeSale(
                product = freshProduct,
                quantity = 10,
                sellPrice = 100.0,
                discount = 0.0,
                customerName = "زبون تجريبي",
                customerPhone = "0500000000",
                paymentType = "نقداً",
                employee = null,
                unit = ClothingUnits.PIECE
            )
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue("يجب منع البيع إذا كانت الكمية المطلوبة تتجاوز رصيد المخزن", exceptionThrown)

        // Stock must remain intact (5 pieces)
        val currentProd = db.storeDao().getProductById(pId)
        assertNotNull(currentProd)
        assertEquals(5, currentProd?.quantity)
    }

    @Test
    fun testExecuteSalePreventsZeroPriceAndExcessDiscount() = runBlocking {
        val product = ProductItem(
            category = "شماغ",
            modelName = "شماغ أحمر",
            color = "أحمر",
            size = "58",
            quantity = 10,
            buyPrice = 30.0,
            sellPrice = 60.0
        )
        val pId = db.storeDao().insertProduct(product)
        val freshProduct = product.copy(id = pId)

        // 1. Zero Price
        var zeroPriceCaught = false
        try {
            repository.executeSale(
                product = freshProduct,
                quantity = 2,
                sellPrice = 0.0,
                discount = 0.0,
                customerName = "زبون",
                customerPhone = "",
                paymentType = "نقداً",
                employee = null
            )
        } catch (e: IllegalArgumentException) {
            zeroPriceCaught = true
        }
        assertTrue("يجب رفض البيع بسعر صفر أو سالب", zeroPriceCaught)

        // 2. Excess Discount
        var excessDiscountCaught = false
        try {
            repository.executeSale(
                product = freshProduct,
                quantity = 1,
                sellPrice = 60.0,
                discount = 80.0, // Discount > Total
                customerName = "زبون",
                customerPhone = "",
                paymentType = "نقداً",
                employee = null
            )
        } catch (e: IllegalArgumentException) {
            excessDiscountCaught = true
        }
        assertTrue("يجب رفض الخصم الذي يتجاوز إجمالي الفاتورة", excessDiscountCaught)
    }

    @Test
    fun testSuccessfulSaleAndStockDeductionAndAuditLog() = runBlocking {
        val product = ProductItem(
            category = "جاكيت",
            modelName = "جاكيت رسمي",
            color = "كحلي",
            size = "L",
            quantity = 8,
            buyPrice = 100.0,
            sellPrice = 180.0
        )
        val pId = db.storeDao().insertProduct(product)
        val freshProduct = product.copy(id = pId)

        val sale = repository.executeSale(
            product = freshProduct,
            quantity = 3,
            sellPrice = 180.0,
            discount = 40.0,
            customerName = "زبون راقي",
            customerPhone = "0551122334",
            paymentType = "نقداً",
            employee = null
        )

        // Verification
        assertEquals(500.0, sale.totalAmount, 0.01) // 3*180 = 540 - 40 = 500
        val updatedProd = db.storeDao().getProductById(pId)
        assertEquals(5, updatedProd?.quantity) // 8 - 3 = 5

        // Check Audit Logs
        val logs = db.storeDao().getAllAuditLogs().firstOrNull() ?: emptyList()
        assertTrue("يجب توثيق حركة البيع في سجل التدقيق", logs.any { it.action == "تسجيل بيع" })
    }

    @Test
    fun testDeletePurchasePreventsDeletionIfGoodsAlreadySold() = runBlocking {
        // 1. Record a purchase of 10 shirts
        val purchaseId = repository.recordPurchase(
            supplierName = "مورد رئيسي",
            category = "قميص",
            modelName = "قميص صيفي",
            color = "أبيض",
            size = "M",
            unit = ClothingUnits.PIECE,
            unitCount = 10.0,
            buyPricePerUnit = 25.0,
            sellPricePerUnit = 50.0,
            paidAmount = 250.0,
            invoiceNumber = "PUR-101",
            paymentMethod = "نقداً من الصندوق",
            barcode = "",
            notes = "",
            colorsCount = 1,
            colorDetails = ""
        )

        val prods = db.storeDao().getAllProducts().firstOrNull() ?: emptyList()
        val createdProd = prods.first { it.modelName == "قميص صيفي" }
        assertEquals(10, createdProd.quantity)

        // 2. Sell 4 pieces (leaving 6 in stock)
        repository.executeSale(
            product = createdProd,
            quantity = 4,
            sellPrice = 50.0,
            discount = 0.0,
            customerName = "زبون صيفي",
            customerPhone = "",
            paymentType = "نقداً",
            employee = null
        )

        // 3. Try to delete purchase of 10 pieces while only 6 remain
        var deletionBlocked = false
        try {
            repository.deletePurchase(purchaseId)
        } catch (e: IllegalStateException) {
            deletionBlocked = true
        }

        assertTrue("يجب منع حذف فاتورة الشراء إذا تم بيع جزء من البضاعة حفاظاً على سلامة الحسابات", deletionBlocked)
    }

    @Test
    fun testExecuteCartSaleMultiItemAtomicSuccess() = runBlocking {
        // 1. Create two products
        val p1Id = db.storeDao().insertProduct(
            ProductItem(
                category = "كوت",
                modelName = "كوت شتوي",
                color = "كحلي",
                size = "L",
                quantity = 10,
                buyPrice = 100.0,
                sellPrice = 200.0
            )
        )
        val p2Id = db.storeDao().insertProduct(
            ProductItem(
                category = "بشت",
                modelName = "بشت صيفي",
                color = "أسود",
                size = "XL",
                quantity = 5,
                buyPrice = 150.0,
                sellPrice = 300.0
            )
        )

        // 2. Create exchange account
        val accId = db.storeDao().insertExchangeAccount(
            ExchangeAccount(
                name = "صرافة الكريمي",
                balance = 1000.0
            )
        )
        val exchangeAccount = db.storeDao().getExchangeAccountById(accId)

        // Cart: 2 coats (400) + 1 bisht (300) = 700. Discount = 50 -> Net = 650.
        // Triple Split: 200 Cash + 250 Transfer + 200 Debt = 650
        val cartItems = listOf(
            CartSaleItem(product = db.storeDao().getProductById(p1Id)!!, quantity = 2, sellPrice = 200.0),
            CartSaleItem(product = db.storeDao().getProductById(p2Id)!!, quantity = 1, sellPrice = 300.0)
        )

        val invoiceNumber = repository.executeCartSale(
            items = cartItems,
            discount = 50.0,
            customerName = "الشيخ فهد",
            customerPhone = "777123456",
            paymentType = PaymentMethods.CUSTOM_SPLIT,
            cashPart = 200.0,
            transferPart = 250.0,
            debtPart = 200.0,
            exchangeAccount = exchangeAccount
        )

        assertTrue(invoiceNumber.startsWith("INV-"))

        // Verify stock deductions
        val updatedP1 = db.storeDao().getProductById(p1Id)
        val updatedP2 = db.storeDao().getProductById(p2Id)
        assertEquals(8, updatedP1?.quantity) // 10 - 2
        assertEquals(4, updatedP2?.quantity) // 5 - 1

        // Verify sales share the SAME invoice number
        val sales = db.storeDao().getAllSales().firstOrNull() ?: emptyList()
        assertEquals(2, sales.size)
        assertEquals(invoiceNumber, sales[0].invoiceNumber)
        assertEquals(invoiceNumber, sales[1].invoiceNumber)

        // Verify Cash Drawer Movement recorded ONCE for total cash (200.0)
        val movements = db.storeDao().getAllCashMovements().firstOrNull() ?: emptyList()
        val saleCashMovements = movements.filter { it.type == "SALE_CASH" }
        assertEquals(1, saleCashMovements.size)
        assertEquals(200.0, saleCashMovements[0].amount, 0.01)

        // Verify Exchange Transfer recorded ONCE for total transfer (250.0)
        val transfers = db.storeDao().getAllExchangeTransfers().firstOrNull() ?: emptyList()
        assertEquals(1, transfers.size)
        assertEquals(250.0, transfers[0].amount, 0.01)
        val updatedAcc = db.storeDao().getExchangeAccountById(accId)
        assertEquals(1250.0, updatedAcc?.balance ?: 0.0, 0.01)

        // Verify Debt recorded ONCE for total debt (200.0)
        val debts = db.storeDao().getAllDebts().firstOrNull() ?: emptyList()
        assertEquals(1, debts.size)
        assertEquals(200.0, debts[0].remainingAmount, 0.01)
        assertEquals("الشيخ فهد", debts[0].partyName)
    }

    @Test
    fun testExecuteCartSaleRollbackWhenOneItemHasInsufficientStock() = runBlocking {
        // Product 1 has plenty (10)
        val p1Id = db.storeDao().insertProduct(
            ProductItem(
                category = "كوت",
                modelName = "كوت متوفر",
                color = "كحلي",
                size = "L",
                quantity = 10,
                buyPrice = 100.0,
                sellPrice = 200.0
            )
        )
        // Product 2 has only 1 piece
        val p2Id = db.storeDao().insertProduct(
            ProductItem(
                category = "بشت",
                modelName = "بشت شحيح",
                color = "أسود",
                size = "XL",
                quantity = 1,
                buyPrice = 150.0,
                sellPrice = 300.0
            )
        )

        // Cart requests 2 of P1 and 5 of P2 (P2 will fail)
        val cartItems = listOf(
            CartSaleItem(product = db.storeDao().getProductById(p1Id)!!, quantity = 2, sellPrice = 200.0),
            CartSaleItem(product = db.storeDao().getProductById(p2Id)!!, quantity = 5, sellPrice = 300.0)
        )

        var failed = false
        try {
            repository.executeCartSale(
                items = cartItems,
                discount = 0.0,
                customerName = "زبون تجريبي",
                customerPhone = "",
                paymentType = PaymentMethods.CASH
            )
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message?.contains("بشت شحيح") == true)
        }

        assertTrue("يجب أن تفشل العملية بالكامل إذا كان صنف واحد غير متوفر بالمخزن", failed)

        // Product 1 MUST NOT be deducted! Stock must remain 10
        val freshP1 = db.storeDao().getProductById(p1Id)
        assertEquals(10, freshP1?.quantity)

        // No sales recorded
        val sales = db.storeDao().getAllSales().firstOrNull() ?: emptyList()
        assertEquals(0, sales.size)

        // No cash movements recorded
        val movements = db.storeDao().getAllCashMovements().firstOrNull() ?: emptyList()
        assertEquals(0, movements.size)
    }

    @Test
    fun testResolveVoucherNumberPreservesManualAndGeneratesSequential() = runBlocking {
        // Explicit voucher number preserved verbatim
        assertEquals("V-500", repository.resolveVoucherNumber("  V-500  "))

        // Blank input generates sequential SND-0001
        val auto1 = repository.resolveVoucherNumber("")
        assertEquals("SND-0001", auto1)

        // Insert a payment with SND-0001, next blank call must generate SND-0002 without colliding
        db.storeDao().insertDebtPayment(
            DebtPayment(
                debtId = 0L,
                partyName = "عميل تجربة",
                amount = 100.0,
                timestamp = System.currentTimeMillis(),
                notes = "دفعة أولى",
                paymentMethod = "نقداً",
                voucherNumber = auto1
            )
        )
        val auto2 = repository.resolveVoucherNumber("   ")
        assertEquals("SND-0002", auto2)
    }

    @Test
    fun testRecordCollectionReceiptCashAppliesToCustomerDebtAndRemainder() = runBlocking {
        // Customer owes 300
        db.storeDao().insertDebt(
            DebtRecord(
                type = "CUSTOMER",
                partyName = "أحمد علي",
                phone = "777000111",
                originalAmount = 300.0,
                paidAmount = 0.0,
                remainingAmount = 300.0,
                details = "فاتورة آجل",
                dueDate = System.currentTimeMillis() + 86400000L,
                createdAt = System.currentTimeMillis(),
                isSettled = false
            )
        )

        // Collect 450 in cash with blank voucher -> should auto-generate SND-0001, settle 300 debt, record 150 direct credit
        val receipt = repository.recordCollectionReceipt(
            payerName = "أحمد علي",
            party = null,
            amount = 450.0,
            voucherNumberInput = "",
            paymentMethod = "نقداً",
            exchangeAccount = null,
            notes = "سداد كامل مع دفعة مقدمة"
        )

        assertEquals("SND-0001", receipt.voucherNumber)
        assertEquals(300.0, receipt.appliedToDebtAmount, 0.01)
        assertEquals(150.0, receipt.directCollectionAmount, 0.01)
        assertEquals(0.0, receipt.remainingCustomerDebt, 0.01)

        val debts = db.storeDao().getAllDebts().firstOrNull().orEmpty()
        assertTrue(debts.first().isSettled)

        val cashIn = db.storeDao().getAllCashMovements().firstOrNull().orEmpty()
            .filter { it.type == "DEBT_COLLECTED" && it.isIncome }
            .sumOf { it.amount }
        assertEquals(450.0, cashIn, 0.01)
    }

    @Test
    fun testRecordCollectionReceiptExchangeWithCustomVoucher() = runBlocking {
        val accId = db.storeDao().insertExchangeAccount(
            ExchangeAccount(name = "صرافة النجم", balance = 500.0)
        )
        val account = db.storeDao().getExchangeAccountById(accId)!!

        val receipt = repository.recordCollectionReceipt(
            payerName = "تحصيل عام",
            party = null,
            amount = 250.0,
            voucherNumberInput = "SND-CUSTOM-9",
            paymentMethod = "صرافة/محفظة",
            exchangeAccount = account,
            notes = "إيداع مباشر"
        )

        assertEquals("SND-CUSTOM-9", receipt.voucherNumber)
        assertEquals("صرافة النجم", receipt.exchangeAccountName)

        val updatedAcc = db.storeDao().getExchangeAccountById(accId)!!
        assertEquals(750.0, updatedAcc.balance, 0.01)

        val transfers = db.storeDao().getAllExchangeTransfers().firstOrNull().orEmpty()
        assertEquals(1, transfers.size)
        assertEquals("SND-CUSTOM-9", transfers[0].referenceNumber)
        assertEquals(250.0, transfers[0].amount, 0.01)
    }

    @Test
    fun testClearEverythingCompletelyAndNoSeededDemoData() = runBlocking {
        // Seed initial data must remain a no-op (app starts clean with zero demo records)
        repository.seedInitialDataIfEmpty()
        assertEquals(0, db.storeDao().getAllProducts().firstOrNull().orEmpty().size)
        assertEquals(0, db.storeDao().getAllExchangeAccounts().firstOrNull().orEmpty().size)

        // Insert sample items and wipe completely
        db.storeDao().insertProduct(
            ProductItem(
                category = "ملابس",
                modelName = "صنف مؤقت",
                color = "أبيض",
                size = "L",
                quantity = 5,
                buyPrice = 10.0,
                sellPrice = 20.0
            )
        )
        db.storeDao().insertExchangeAccount(ExchangeAccount(name = "صراف مؤقت", balance = 100.0))
        db.storeDao().insertParty(PartyAccount(name = "عميل مؤقت", type = "CUSTOMER"))
        db.storeDao().insertAuditLog(
            AuditLogRecord(action = "اختبار", details = "سجل مؤقت", timestamp = System.currentTimeMillis())
        )

        repository.clearEverythingCompletely()

        assertEquals(0, db.storeDao().getAllProducts().firstOrNull().orEmpty().size)
        assertEquals(0, db.storeDao().getAllExchangeAccounts().firstOrNull().orEmpty().size)
        assertEquals(0, db.storeDao().getAllParties().firstOrNull().orEmpty().size)
        assertEquals(0, db.storeDao().getAllAuditLogs().firstOrNull().orEmpty().size)
        assertEquals(0, db.storeDao().getAllSales().firstOrNull().orEmpty().size)
    }

    @Test
    fun testStoreSetupSettingsAndConfigDictionariesCrud() = runBlocking {
        // 1. Save initial setup settings with logoPath & isSetupCompleted = true
        val customSettings = StoreSettings(
            id = 1,
            shopNameAr = "أزياء التاج الملكي",
            phone = "777555444",
            address = "تعز - شارع جمال",
            logoPath = "/data/user/0/com.example/files/store_logos/logo.png",
            isSetupCompleted = true
        )
        repository.saveStoreSettings(customSettings)

        val loadedSettings = repository.getStoreSettingsDirect()
        assertNotNull(loadedSettings)
        assertEquals("أزياء التاج الملكي", loadedSettings?.shopNameAr)
        assertEquals("777555444", loadedSettings?.phone)
        assertEquals("تعز - شارع جمال", loadedSettings?.address)
        assertTrue(loadedSettings?.isSetupCompleted == true)
        assertEquals("/data/user/0/com.example/files/store_logos/logo.png", loadedSettings?.logoPath)

        // 2. Add product category & size, insert product using them, then rename category & size
        assertTrue(repository.addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, "أكوات"))
        assertFalse(repository.addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, "أكوات")) // duplicate ignored
        assertTrue(repository.addConfigItem(StoreConfigTypes.PRODUCT_SIZE, "شبابي"))

        val prodId = db.storeDao().insertProduct(
            ProductItem(
                category = "أكوات",
                modelName = "كوت جوخ",
                color = "أسود",
                size = "شبابي",
                quantity = 4,
                buyPrice = 100.0,
                sellPrice = 180.0
            )
        )

        val catItem = db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.PRODUCT_CATEGORY).first()
        assertTrue(repository.updateConfigItem(catItem, "أكوات فاخرة"))

        val sizeItem = db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.PRODUCT_SIZE).first()
        assertTrue(repository.updateConfigItem(sizeItem, "شبابي كبير"))

        val updatedProduct = db.storeDao().getProductById(prodId)
        assertEquals("أكوات فاخرة", updatedProduct?.category)
        assertEquals("شبابي كبير", updatedProduct?.size)

        // 3. Delete config item
        repository.deleteConfigItem(catItem.id)
        assertEquals(0, db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.PRODUCT_CATEGORY).size)

        // 4. Seed recommended dictionaries
        repository.seedRecommendedDictionaries(includeSampleCategories = true)
        assertTrue(db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.EXPENSE_CATEGORY).isNotEmpty())
        assertTrue(db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.ACCOUNT_TYPE).isNotEmpty())
        assertTrue(db.storeDao().getConfigItemsByTypeDirect(StoreConfigTypes.PRODUCT_CATEGORY).any { it.name == "دجلات" })

        // 5. Full wipe with clearConfigDictionaries = true clears all dictionaries too
        repository.clearEverythingCompletely(clearConfigDictionaries = true)
        assertEquals(0, repository.getConfigItemsCount())
    }

    @Test
    fun testRecordSupplierPaymentReceiptCashAndExchangeWithAutoAndManualVoucher() = runBlocking {
        // 1. Create two open debts for supplier (FIFO test)
        val now = System.currentTimeMillis()
        db.storeDao().insertDebt(
            DebtRecord(
                type = "SUPPLIER",
                partyName = "مصنع النخبة",
                originalAmount = 400.0,
                paidAmount = 0.0,
                remainingAmount = 400.0,
                details = "فاتورة توريد 1",
                dueDate = now + 86400000L,
                createdAt = now - 5000L,
                isSettled = false
            )
        )
        db.storeDao().insertDebt(
            DebtRecord(
                type = "SUPPLIER",
                partyName = "مصنع النخبة",
                originalAmount = 300.0,
                paidAmount = 0.0,
                remainingAmount = 300.0,
                details = "فاتورة توريد 2",
                dueDate = now + 86400000L,
                createdAt = now - 1000L,
                isSettled = false
            )
        )

        // 2. Pay 500 cash with blank voucher -> auto SRF-0001, settles 400 first + 100 from second
        val cashReceipt = repository.recordSupplierPaymentReceipt(
            supplierName = "مصنع النخبة",
            party = null,
            amount = 500.0,
            voucherNumberInput = "",
            paymentMethod = "نقداً",
            exchangeAccount = null,
            notes = "دفعة أولى للمصنع"
        )

        assertEquals("SRF-0001", cashReceipt.voucherNumber)
        assertEquals(500.0, cashReceipt.appliedToDebtAmount, 0.01)
        assertEquals(0.0, cashReceipt.directPaymentAmount, 0.01)
        assertEquals(200.0, cashReceipt.remainingSupplierDebt, 0.01)

        val cashOut = db.storeDao().getAllCashMovements().firstOrNull().orEmpty()
            .filter { it.type == "SUPPLIER_PAID" && !it.isIncome }
            .sumOf { it.amount }
        assertEquals(500.0, cashOut, 0.01)

        // 3. Pay remaining 200 + 150 advance (total 350) via exchange account with custom voucher
        val accId = db.storeDao().insertExchangeAccount(
            ExchangeAccount(name = "حساب البسيري", balance = 1000.0)
        )
        val account = db.storeDao().getExchangeAccountById(accId)!!

        val exchangeReceipt = repository.recordSupplierPaymentReceipt(
            supplierName = "مصنع النخبة",
            party = null,
            amount = 350.0,
            voucherNumberInput = "SRF-M-77",
            paymentMethod = "صرافة/محفظة",
            exchangeAccount = account,
            notes = "تصفية الحساب ودفعة مقدمة"
        )

        assertEquals("SRF-M-77", exchangeReceipt.voucherNumber)
        assertEquals(200.0, exchangeReceipt.appliedToDebtAmount, 0.01)
        assertEquals(150.0, exchangeReceipt.directPaymentAmount, 0.01)
        assertEquals(0.0, exchangeReceipt.remainingSupplierDebt, 0.01)

        val updatedAcc = db.storeDao().getExchangeAccountById(accId)!!
        assertEquals(650.0, updatedAcc.balance, 0.01)
    }

    @Test
    fun testPartyAndEmployeeStatementsDeduplicateAndFormatCorrectly() {
        val now = System.currentTimeMillis()
        val customer = PartyAccount(id = 1L, name = "سالم محمد", type = "CUSTOMER", phone = "777888999")
        val sales = listOf(
            SaleTransaction(
                id = 10L,
                productId = 1L,
                invoiceNumber = "INV-10",
                customerName = "سالم محمد",
                productName = "كوت شتوي",
                category = "أكوات",
                color = "أسود",
                size = "L",
                quantity = 2,
                unitBuyPrice = 50.0,
                unitSellPrice = 100.0,
                totalAmount = 200.0,
                cashAmount = 50.0,
                transferAmount = 0.0,
                debtAmount = 150.0,
                profit = 100.0,
                paymentType = "مختلط",
                timestamp = now - 4000L
            ),
            SaleTransaction(
                id = 11L,
                productId = 2L,
                invoiceNumber = "INV-10",
                customerName = "سالم محمد",
                productName = "دجلة صوف",
                category = "دجلات",
                color = "بني",
                size = "L",
                quantity = 1,
                unitBuyPrice = 40.0,
                unitSellPrice = 100.0,
                totalAmount = 100.0,
                cashAmount = 50.0,
                transferAmount = 0.0,
                debtAmount = 50.0,
                profit = 60.0,
                paymentType = "مختلط",
                timestamp = now - 3900L
            )
        )
        // Linked debt created alongside sale id 10 must NOT double-count against INV-10!
        // Unlinked opening debt (referenceSaleId = null) MUST be included.
        val debts = listOf(
            DebtRecord(
                id = 20L,
                type = "CUSTOMER",
                partyName = "سالم محمد",
                originalAmount = 200.0,
                paidAmount = 100.0,
                remainingAmount = 100.0,
                details = "دين فاتورة INV-10",
                dueDate = now,
                createdAt = now - 3800L,
                isSettled = false,
                referenceSaleId = 10L
            ),
            DebtRecord(
                id = 21L,
                type = "CUSTOMER",
                partyName = "سالم محمد",
                originalAmount = 100.0,
                paidAmount = 0.0,
                remainingAmount = 100.0,
                details = "رصيد افتتاحي قديم",
                dueDate = now,
                createdAt = now - 10000L,
                isSettled = false,
                referenceSaleId = null
            )
        )
        val payments = listOf(
            DebtPayment(
                id = 30L,
                debtId = 20L,
                partyName = "سالم محمد",
                amount = 100.0,
                timestamp = now - 1000L,
                notes = "سند قبض #SND-0001",
                paymentMethod = "نقداً",
                voucherNumber = "SND-0001"
            )
        )

        val summary = StatementHelper.buildPartyStatement(customer, sales, emptyList(), debts, payments)
        // Total debit (عليه): 300 (INV-10) + 100 (opening debt) = 400 (NOT 600!)
        // Total credit (له): 100 (upfront cash) + 100 (SND-0001) = 200
        // Net remaining (باقي): 400 - 200 = 200
        assertEquals(400.0, summary.totalOnParty, 0.01)
        assertEquals(200.0, summary.totalForParty, 0.01)
        assertEquals(200.0, summary.netBalance, 0.01)
        assertEquals(1, summary.invoicesCount)
        assertEquals(1, summary.paymentsCount)

        val detailedText = StatementHelper.formatPartyStatementText(summary, StatementMode.DETAILED, null)
        assertTrue(detailedText.contains("سالم محمد"))
        assertTrue(detailedText.contains("كم عليه"))
        assertTrue(detailedText.contains("كم له"))
        assertTrue(detailedText.contains("كم باقي"))
        assertTrue(detailedText.contains("INV-10"))

        val emp = Employee(id = 5L, name = "خالد البائع", role = "بائع", baseSalary = 1000.0, commissionPercent = 10.0)
        val empSales = listOf(
            SaleTransaction(
                productId = 1L,
                productName = "كوت",
                category = "أكوات",
                color = "أسود",
                size = "L",
                quantity = 1,
                unitBuyPrice = 300.0,
                unitSellPrice = 500.0,
                totalAmount = 500.0,
                profit = 200.0,
                employeeId = 5L,
                employeeName = "خالد البائع"
            )
        )
        val empTxs = listOf(
            EmployeeTransaction(employeeId = 5L, employeeName = "خالد البائع", type = "مكافأة", amount = 100.0, timestamp = now - 2000L),
            EmployeeTransaction(employeeId = 5L, employeeName = "خالد البائع", type = "سلفة", amount = 250.0, timestamp = now - 1000L)
        )
        val empSummary = StatementHelper.buildEmployeeStatement(emp, empTxs, empSales)
        // Earned: 1000 + 50 (10% of 500) + 100 bonus = 1150; Taken: 250; Remaining: 900
        assertEquals(1150.0, empSummary.totalEarned, 0.01)
        assertEquals(250.0, empSummary.totalDeductedAndPaid, 0.01)
        assertEquals(900.0, empSummary.remainingBalance, 0.01)

        val empText = StatementHelper.formatEmployeeStatementText(empSummary, StatementMode.SUMMARY, null)
        assertTrue(empText.contains("خالد البائع"))
        assertTrue(empText.contains("كم باقي"))
    }

    @Test
    fun testSupplierLedger35EntriesAndDailyReportAndVoucherFormatters() {
        val now = System.currentTimeMillis()
        val supplier = PartyAccount(id = 2L, name = "شركة القمة للمنسوجات", type = "SUPPLIER", phone = "733112233")

        // Generate 35 purchase invoices -> verifies multi-entry supplier accounting & running balance
        val purchases = (1..35).map { idx ->
            PurchaseRecord(
                id = idx.toLong(),
                supplierName = supplier.name,
                category = "أكوات",
                modelName = "موديل رقم $idx",
                color = "أسود",
                size = "كبير",
                unit = ClothingUnits.DOZEN,
                unitCount = 1.0,
                quantity = 12,
                unitBuyPrice = 100.0,
                unitSellPrice = 150.0,
                totalCost = 1200.0,
                paidAmount = 200.0,
                remainingDebt = 1000.0,
                invoiceNumber = "PUR-$idx",
                paymentMethod = "مختلط",
                cashPaid = 200.0,
                timestamp = now + idx * 1000L
            )
        }
        val summary = StatementHelper.buildPartyStatement(supplier, emptyList(), purchases, emptyList(), emptyList())
        assertEquals(35, summary.invoicesCount)
        assertEquals(35, summary.entries.size)
        assertEquals(35 * 1200.0, summary.totalForParty, 0.01)
        assertEquals(35 * 200.0, summary.totalOnParty, 0.01)
        assertEquals(35 * 1000.0, summary.netBalance, 0.01)
        assertEquals(35 * 1000.0, summary.entries.last().runningBalance, 0.01)

        val settings = StoreSettings(shopNameAr = "متجر النور", phone = "777000000", address = "صنعاء")
        val detailedMsg = StatementHelper.formatPartyStatementText(summary, StatementMode.DETAILED, settings)
        val summaryMsg = StatementHelper.formatPartyStatementText(summary, StatementMode.SUMMARY, settings)
        assertTrue(detailedMsg.contains("PUR-35"))
        assertTrue(detailedMsg.contains("كم له"))
        assertTrue(summaryMsg.contains("تقرير رصيد نهائي"))

        val voucherText = StatementHelper.formatSupplierPaymentVoucherText(
            receipt = SupplierPaymentReceiptResult(
                voucherNumber = "SRF-0001",
                supplierName = supplier.name,
                amount = 5000.0,
                paymentMethod = "نقداً",
                exchangeAccountName = "",
                appliedToDebtAmount = 5000.0,
                directPaymentAmount = 0.0,
                remainingSupplierDebt = 30000.0,
                notes = "سداد دفعة",
                timestamp = now
            ),
            settings = settings
        )
        assertTrue(voucherText.contains("SRF-0001"))
        assertTrue(voucherText.contains(supplier.name))

        val reportSummary = StatementHelper.buildDailyReportSummary(
            periodTitle = "اليوم",
            filteredSales = emptyList(),
            filteredPurchases = purchases,
            filteredExpenses = listOf(ExpenseRecord(category = "إيجار", amount = 500.0, notes = "إيجار", timestamp = now)),
            filteredEmployeeTx = emptyList(),
            filteredPayments = listOf(
                DebtPayment(debtId = 1L, partyName = "عميل", amount = 300.0, timestamp = now, voucherNumber = "SND-0001"),
                DebtPayment(debtId = 0L, partyName = supplier.name, amount = 400.0, timestamp = now, notes = "سند صرف لمورد: #SRF-0001", voucherNumber = "SRF-0001")
            ),
            allDebts = listOf(
                DebtRecord(id = 1L, type = "CUSTOMER", partyName = "عميل", originalAmount = 1000.0, paidAmount = 300.0, remainingAmount = 700.0, details = "", dueDate = now, createdAt = now, isSettled = false)
            ),
            allProducts = emptyList(),
            currentCashBalance = 2500.0,
            currentExchangeBalance = 1200.0
        )
        assertEquals(300.0, reportSummary.collectionsTotal, 0.01)
        assertEquals(400.0, reportSummary.supplierPaymentsTotal, 0.01)
        assertEquals(500.0, reportSummary.expensesTotal, 0.01)
        val reportText = StatementHelper.formatDailyReportText(reportSummary, settings)
        assertTrue(reportText.contains("متجر النور"))
        assertTrue(reportText.contains("التحصيلات من العملاء"))
        assertTrue(reportText.contains("المدفوعات للموردين"))
    }
}
