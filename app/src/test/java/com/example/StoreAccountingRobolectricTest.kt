package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
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
}
