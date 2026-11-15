package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.PinVerificationResult
import com.example.data.repository.StoreRepository
import com.example.ui.util.BackupManager
import com.example.ui.util.ClothingSizes
import com.example.ui.util.ClothingUnits
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.PinSecurityManager
import com.example.ui.util.StatementHelper
import com.example.ui.util.StatementMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.OutputStream

@Implements(PdfDocument.Page::class)
class TestPdfDocumentPageShadow {
    var shadowCanvas: Canvas? = null
    var shadowInfo: PdfDocument.PageInfo? = null

    @Implementation
    fun getCanvas(): Canvas? = shadowCanvas

    @Implementation
    fun getInfo(): PdfDocument.PageInfo? = shadowInfo
}

@Implements(PdfDocument::class)
class TestPdfDocumentShadow {
    private var closed = false
    private var activePage: PdfDocument.Page? = null
    private var finishedPageCount = 0

    @Implementation
    fun __constructor__() {
        closed = false
        activePage = null
        finishedPageCount = 0
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        check(activePage == null) { "previous page not finished!" }
        val bmp = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val page = Shadow.newInstanceOf(PdfDocument.Page::class.java)
        val pageShadow = Shadow.extract<TestPdfDocumentPageShadow>(page)
        pageShadow.shadowCanvas = canvas
        pageShadow.shadowInfo = pageInfo
        activePage = page
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page?) {
        check(!closed) { "document is closed!" }
        check(activePage != null && activePage === page) { "page was not started!" }
        activePage = null
        finishedPageCount++
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        check(activePage == null) { "cannot write while a page is still open!" }
        val header = buildString {
            appendLine("%PDF-1.4")
            appendLine("% FinishedPages=$finishedPageCount")
            appendLine("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj")
            appendLine("2 0 obj<< /Type /Pages /Count $finishedPageCount >>endobj")
            appendLine("%%EOF")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
    }

    @Implementation
    fun close() {
        check(activePage == null) { "cannot close document with unfinished page!" }
        closed = true
    }
}

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
        repository.recordManualDeposit(5000.0, "رصيد افتتاحي للاختبار")
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
        repository.recordManualDeposit(5000.0, "رصيد افتتاحي للاختبار")
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

    @Test
    fun testBackdatedOperationsPersistCustomTimestamp() = runBlocking {
        val pastTs = System.currentTimeMillis() - (7L * 86_400_000L) // 7 days ago
        assertTrue(FormatUtils.isPastDate(pastTs))
        assertFalse(FormatUtils.isFutureDate(pastTs))
        assertFalse(FormatUtils.isToday(pastTs))

        val accId = db.storeDao().insertExchangeAccount(
            ExchangeAccount(name = "حساب الكريمي", balance = 50_000.0)
        )
        val empId = db.storeDao().insertEmployee(
            Employee(name = "أحمد", role = "بائع", baseSalary = 2000.0, commissionPercent = 5.0)
        )
        val employee = db.storeDao().getEmployeeById(empId)!!

        // 1. Backdated purchase (credit)
        repository.executeCartPurchase(
            items = listOf(
                CartPurchaseItem(
                    category = "أكوات",
                    modelName = "كوت تاريخ قديم",
                    color = "أسود",
                    size = "L",
                    unit = ClothingUnits.PIECE,
                    unitCount = 10.0,
                    buyPricePerUnit = 100.0,
                    sellPricePerUnit = 150.0
                )
            ),
            supplierName = "مورد الأمس",
            invoiceNumber = "PUR-PAST-1",
            paymentMethod = "آجل للمورد",
            cashPaidPart = 0.0,
            transferPaidPart = 0.0,
            debtPart = 1000.0,
            exchangeAccount = null,
            transferRef = "",
            notes = "شراء سابق",
            timestamp = pastTs
        )
        val purchases = db.storeDao().getAllPurchases().firstOrNull().orEmpty()
        assertEquals(1, purchases.size)
        assertEquals(pastTs, purchases.first().timestamp)

        val product = db.storeDao().getAllProducts().firstOrNull().orEmpty().first()
        assertEquals(pastTs, product.purchaseDate)

        // 2. Backdated credit sale
        repository.executeCartSale(
            items = listOf(CartSaleItem(product = product, quantity = 2, sellPrice = 150.0, unit = ClothingUnits.PIECE)),
            discount = 0.0,
            customerName = "عميل قديم",
            customerPhone = "777111222",
            paymentType = PaymentMethods.CREDIT,
            cashPart = 0.0,
            transferPart = 0.0,
            debtPart = 300.0,
            exchangeAccount = null,
            transferRef = "",
            employee = employee,
            customInvoiceNumber = "INV-PAST-1",
            timestamp = pastTs
        )
        val sales = db.storeDao().getAllSales().firstOrNull().orEmpty()
        assertEquals(1, sales.size)
        assertEquals(pastTs, sales.first().timestamp)

        // 3. Backdated manual deposit, withdrawal, and transfer
        repository.recordManualDeposit(5000.0, "تغذية سابقة", pastTs)
        repository.recordOwnerWithdrawal(200.0, "سحب سابق", pastTs)
        repository.transferBetweenCashAndExchange(accId, 500.0, true, "مناقلة سابقة", "TR-1", pastTs)

        // 4. Backdated expense & employee transaction
        repository.recordExpense("كهرباء", 150.0, "فاتورة سابقة", "EXP-1", paidFromCash = true, exchangeAccount = null, timestamp = pastTs)
        repository.recordEmployeeTransaction(empId, employee.name, "صرفة يومية", 100.0, "صرفة سابقة", paidFromCashDrawer = true, exchangeAccount = null, timestamp = pastTs)

        val expenses = db.storeDao().getAllExpenses().firstOrNull().orEmpty()
        assertEquals(pastTs, expenses.first().timestamp)
        val empTxs = db.storeDao().getAllEmployeeTransactions().firstOrNull().orEmpty()
        assertEquals(pastTs, empTxs.first().timestamp)

        // 5. Backdated collection receipt & supplier payment receipt
        val collectReceipt = repository.recordCollectionReceipt(
            payerName = "عميل قديم",
            party = null,
            amount = 100.0,
            voucherNumberInput = "",
            paymentMethod = "نقداً",
            exchangeAccount = null,
            notes = "تحصيل سابق",
            timestamp = pastTs
        )
        assertEquals(pastTs, collectReceipt.timestamp)

        val supplierReceipt = repository.recordSupplierPaymentReceipt(
            supplierName = "مورد الأمس",
            party = null,
            amount = 250.0,
            voucherNumberInput = "",
            paymentMethod = "نقداً",
            exchangeAccount = null,
            notes = "تسديد سابق",
            timestamp = pastTs
        )
        assertEquals(pastTs, supplierReceipt.timestamp)

        val payments = db.storeDao().getAllDebtPayments().firstOrNull().orEmpty()
        assertTrue(payments.isNotEmpty())
        assertTrue(payments.all { it.timestamp == pastTs })

        val movements = db.storeDao().getAllCashMovements().firstOrNull().orEmpty()
        assertTrue(movements.isNotEmpty())
        assertTrue(movements.all { it.timestamp == pastTs })
    }

    @Test
    fun testFullBackupValidateRejectCorruptAndRestorePreservesAll16TablesAndReports() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.saveStoreSettings(
            StoreSettings(id = 1, shopNameAr = "محل النور", phone = "777999000", isSetupCompleted = true)
        )
        repository.addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, "أكوات")
        repository.recordManualDeposit(4000.0, "رأس مال")
        val accId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "الكريمي", balance = 2000.0))
        val empId = db.storeDao().insertEmployee(Employee(name = "طارق", role = "بائع", baseSalary = 1200.0, commissionPercent = 5.0))
        val emp = db.storeDao().getEmployeeById(empId)!!
        val acc = db.storeDao().getExchangeAccountById(accId)!!
        repository.saveParty(PartyAccount(type = "CUSTOMER", name = "سالم الزبون", phone = "771000222"), initialDebt = 350.0)

        repository.executeCartPurchase(
            items = listOf(
                CartPurchaseItem("أكوات", "كوت فاخر", "أسود", "L", ClothingUnits.PIECE, 10.0, 100.0, 180.0)
            ),
            supplierName = "معمل التميز",
            invoiceNumber = "PUR-BKP-1",
            paymentMethod = "نقداً من الصندوق",
            cashPaidPart = 1000.0,
            transferPaidPart = 0.0,
            debtPart = 0.0,
            exchangeAccount = null,
            transferRef = "",
            notes = ""
        )
        val prod = db.storeDao().getAllProductsDirect().first()
        repository.executeCartSale(
            items = listOf(CartSaleItem(prod, quantity = 2, sellPrice = 180.0, unit = ClothingUnits.PIECE)),
            discount = 10.0,
            customerName = "سالم الزبون",
            customerPhone = "771000222",
            paymentType = PaymentMethods.CUSTOM_SPLIT,
            cashPart = 100.0,
            transferPart = 150.0,
            debtPart = 100.0,
            exchangeAccount = acc,
            transferRef = "TR-BKP",
            employee = emp,
            customInvoiceNumber = "INV-BKP-1"
        )
        repository.recordEmployeeTransaction(empId, emp.name, "صرفة يومية", 50.0, "غداء", paidFromCashDrawer = true)

        val beforeSales = db.storeDao().getAllSalesDirect()
        val beforePurchases = db.storeDao().getAllPurchasesDirect()
        val beforeDebts = db.storeDao().getAllDebtsDirect()
        val beforeCash = db.storeDao().getAvailableCashBalance()
        val beforeReport = StatementHelper.buildDailyReportSummary(
            periodTitle = "الكل",
            filteredSales = beforeSales,
            filteredPurchases = beforePurchases,
            filteredExpenses = db.storeDao().getAllExpensesDirect(),
            filteredEmployeeTx = db.storeDao().getAllEmployeeTransactionsDirect(),
            filteredPayments = db.storeDao().getAllDebtPaymentsDirect(),
            allDebts = beforeDebts,
            allProducts = db.storeDao().getAllProductsDirect(),
            currentCashBalance = beforeCash,
            currentExchangeBalance = db.storeDao().getAllExchangeAccounts().first().sumOf { it.balance }
        )

        val backupJson = BackupManager.createBackupJsonString(repository)
        val parsed = BackupManager.validateAndParseBackup(backupJson)
        assertTrue("Valid 16-table backup must parse cleanly", parsed.isSuccess)
        val preview = parsed.getOrThrow().preview
        assertEquals(1, preview.productsCount)
        assertEquals(1, preview.salesCount)
        assertEquals(1, preview.purchasesCount)
        assertEquals(1, preview.expensesCount)
        assertEquals(1, preview.employeeTransactionsCount)
        assertTrue(preview.configItemsCount >= 1)

        // Reject corrupt / incomplete / broken-FK backups without touching live DB
        assertTrue(BackupManager.validateAndParseBackup("{bad json").isFailure)
        assertTrue(BackupManager.validateAndParseBackup("""{"app":"OtherApp","version":10}""").isFailure)
        val orphanBackup = org.json.JSONObject(backupJson).apply {
            put("employeeTransactions", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("id", 99L)
                    put("employeeId", 9999L)
                    put("employeeName", "مجهول")
                    put("type", "سلفة")
                    put("amount", 10.0)
                })
            })
        }
        assertTrue("Orphan FK reference must be rejected", BackupManager.validateAndParseBackup(orphanBackup.toString()).isFailure)
        assertEquals("Live DB must remain intact after rejected backup", 1, db.storeDao().getAllSalesDirect().size)

        // Wipe everything, then restore and verify exact parity of rows, IDs, FKs & reports
        repository.clearEverythingCompletely(clearConfigDictionaries = true)
        assertEquals(0, db.storeDao().getAllProductsDirect().size)

        repository.restoreFullBackupSnapshot(parsed.getOrThrow().snapshot)
        assertEquals(prod.id, db.storeDao().getAllProductsDirect().first().id)
        assertEquals(beforeSales.first().id, db.storeDao().getAllSalesDirect().first().id)
        assertEquals(beforeDebts.map { it.referenceSaleId }.toSet(), db.storeDao().getAllDebtsDirect().map { it.referenceSaleId }.toSet())
        assertEquals(beforeCash, db.storeDao().getAvailableCashBalance(), 0.01)

        val afterReport = StatementHelper.buildDailyReportSummary(
            periodTitle = "الكل",
            filteredSales = db.storeDao().getAllSalesDirect(),
            filteredPurchases = db.storeDao().getAllPurchasesDirect(),
            filteredExpenses = db.storeDao().getAllExpensesDirect(),
            filteredEmployeeTx = db.storeDao().getAllEmployeeTransactionsDirect(),
            filteredPayments = db.storeDao().getAllDebtPaymentsDirect(),
            allDebts = db.storeDao().getAllDebtsDirect(),
            allProducts = db.storeDao().getAllProductsDirect(),
            currentCashBalance = db.storeDao().getAvailableCashBalance(),
            currentExchangeBalance = db.storeDao().getAllExchangeAccounts().first().sumOf { it.balance }
        )
        assertEquals(beforeReport.totalSales, afterReport.totalSales, 0.01)
        assertEquals(beforeReport.grossProfit, afterReport.grossProfit, 0.01)
        assertEquals(beforeReport.expensesTotal, afterReport.expensesTotal, 0.01)
        assertEquals(beforeReport.totalCustomerDebts, afterReport.totalCustomerDebts, 0.01)

        val dummyPdfCheck = context.packageName
        assertTrue(dummyPdfCheck.isNotBlank())
    }

    @Test
    fun testMultiItemSaleEditAndDeleteAndCreditPartialPaymentGuards() = runBlocking {
        repository.recordManualDeposit(2000.0, "رصيد")
        val p1Id = db.storeDao().insertProduct(ProductItem(category = "ثوب", modelName = "ثوب 1", color = "أبيض", size = "L", quantity = 10, buyPrice = 50.0, sellPrice = 100.0))
        val p2Id = db.storeDao().insertProduct(ProductItem(category = "شماغ", modelName = "شماغ 2", color = "أحمر", size = "58", quantity = 8, buyPrice = 40.0, sellPrice = 80.0))
        val accId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "صراف", balance = 500.0))
        val acc = db.storeDao().getExchangeAccountById(accId)!!
        val p1 = db.storeDao().getProductById(p1Id)!!
        val p2 = db.storeDao().getProductById(p2Id)!!

        // Credit + split multi-item sale: total = 2*100 + 2*80 = 360 (100 cash, 100 transfer, 160 debt)
        val invNum = repository.executeCartSale(
            items = listOf(CartSaleItem(p1, 2, 100.0), CartSaleItem(p2, 2, 80.0)),
            discount = 0.0,
            customerName = "ماجد",
            customerPhone = "777",
            paymentType = PaymentMethods.CUSTOM_SPLIT,
            cashPart = 100.0,
            transferPart = 100.0,
            debtPart = 160.0,
            exchangeAccount = acc,
            transferRef = "REF-1"
        )
        assertEquals(8, db.storeDao().getProductById(p1Id)!!.quantity)
        assertEquals(6, db.storeDao().getProductById(p2Id)!!.quantity)
        assertEquals(600.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)

        // Pay 80 on the resulting customer debt
        val saleDebt = db.storeDao().getAllDebtsDirect().first()
        repository.recordDebtPayment(saleDebt, 80.0, "سداد جزئي", "نقداً")
        val afterPayDebt = db.storeDao().getDebtById(saleDebt.id)!!
        assertEquals(80.0, afterPayDebt.remainingAmount, 0.01)

        // Attempt to edit invoice lowering debt below already-paid 80 -> MUST throw IllegalStateException
        val currentLines = db.storeDao().getSalesByInvoiceNumber(invNum)
        var blockedLowerDebt = false
        try {
            repository.updateSaleInvoice(
                originalInvoiceKey = invNum,
                updatedItems = currentLines,
                newInvoiceNumber = invNum,
                customerName = "ماجد",
                customerPhone = "777",
                paymentType = PaymentMethods.CUSTOM_SPLIT,
                cashAmount = 200.0,
                transferAmount = 120.0,
                debtAmount = 40.0,
                exchangeAccount = acc,
                transferRef = "REF-1",
                timestamp = currentLines.first().timestamp
            )
        } catch (e: IllegalStateException) {
            blockedLowerDebt = true
        }
        assertTrue("Must block reducing credit invoice debt below paid amount", blockedLowerDebt)

        // Delete sale with paid debt -> blocked until debt/payments are intact
        var blockedDeleteWithPaid = false
        try {
            repository.deleteSaleInvoice(invNum)
        } catch (e: IllegalStateException) {
            blockedDeleteWithPaid = true
        }
        assertTrue("Must prevent deleting credit sale with recorded partial payments", blockedDeleteWithPaid)

        // Valid atomic multi-item edit increasing P1 qty to 3 (total = 460 -> 160 cash, 100 transfer, 200 debt)
        val editedLines = listOf(
            currentLines[0].copy(quantity = 3, totalAmount = 300.0, profit = 150.0),
            currentLines[1]
        )
        repository.updateSaleInvoice(
            originalInvoiceKey = invNum,
            updatedItems = editedLines,
            newInvoiceNumber = invNum,
            customerName = "ماجد",
            customerPhone = "777",
            paymentType = PaymentMethods.CUSTOM_SPLIT,
            cashAmount = 160.0,
            transferAmount = 100.0,
            debtAmount = 200.0,
            exchangeAccount = acc,
            transferRef = "REF-1",
            timestamp = currentLines.first().timestamp
        )
        assertEquals(7, db.storeDao().getProductById(p1Id)!!.quantity)
        val updatedDebt = db.storeDao().getDebtById(saleDebt.id)!!
        assertEquals(200.0, updatedDebt.originalAmount, 0.01)
        assertEquals(80.0, updatedDebt.paidAmount, 0.01)
        assertEquals(120.0, updatedDebt.remainingAmount, 0.01)

        // Fresh unpaid multi-item sale -> delete atomically restores stock, cash & reverses transfer
        val inv2 = repository.executeCartSale(
            items = listOf(CartSaleItem(db.storeDao().getProductById(p1Id)!!, 1, 100.0), CartSaleItem(db.storeDao().getProductById(p2Id)!!, 1, 80.0)),
            discount = 0.0,
            customerName = "زبون",
            customerPhone = "",
            paymentType = PaymentMethods.SPLIT,
            cashPart = 90.0,
            transferPart = 90.0,
            debtPart = 0.0,
            exchangeAccount = acc
        )
        val accBeforeDelete = db.storeDao().getExchangeAccountById(accId)!!.balance
        repository.deleteSaleInvoice(inv2)
        assertEquals(7, db.storeDao().getProductById(p1Id)!!.quantity)
        assertEquals(6, db.storeDao().getProductById(p2Id)!!.quantity)
        assertEquals(accBeforeDelete - 90.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertTrue("Reversal entry must exist for deleted transfer", db.storeDao().getAllExchangeTransfersDirect().any { it.isReversed })
    }

    @Test
    fun testMultiItemPurchaseEditAndDeleteAsAtomicUnit() = runBlocking {
        repository.recordManualDeposit(5000.0, "رصيد")
        val accId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "صراف", balance = 3000.0))
        val acc = db.storeDao().getExchangeAccountById(accId)!!

        val purNum = repository.executeCartPurchase(
            items = listOf(
                CartPurchaseItem("أكوات", "كوت 1", "أسود", "L", ClothingUnits.PIECE, 5.0, 100.0, 160.0),
                CartPurchaseItem("دجلات", "دجلة 2", "رمادي", "M", ClothingUnits.PIECE, 4.0, 150.0, 220.0)
            ),
            supplierName = "مورد الأناقة",
            invoiceNumber = "PUR-UNI-1",
            paymentMethod = "مختلط (كاش + حوالة + آجل)",
            cashPaidPart = 500.0,
            transferPaidPart = 400.0,
            debtPart = 200.0, // total = 500 + 600 = 1100
            exchangeAccount = acc,
            transferRef = "P-REF",
            notes = ""
        )
        assertEquals(2600.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals(4500.0, db.storeDao().getAvailableCashBalance(), 0.01)

        // Edit multi-item purchase: increase line 1 from 5 to 6 pieces (+100 cost -> 600 cash, 400 transfer, 200 debt = 1200)
        val lines = db.storeDao().getPurchasesByInvoiceNumber(purNum)
        assertEquals(2, lines.size)
        val editedLines = listOf(
            lines[0].copy(quantity = 6, unitCount = 6.0, totalCost = 600.0),
            lines[1]
        )
        repository.updatePurchaseInvoice(
            originalInvoiceKey = purNum,
            updatedItems = editedLines,
            newInvoiceNumber = purNum,
            supplierName = "مورد الأناقة",
            paymentMethod = "مختلط (كاش + حوالة + آجل)",
            cashPaid = 600.0,
            transferPaid = 400.0,
            debtAmount = 200.0,
            exchangeAccount = acc,
            timestamp = lines.first().timestamp
        )
        assertEquals(4400.0, db.storeDao().getAvailableCashBalance(), 0.01)
        val p1 = db.storeDao().getAllProductsDirect().first { it.modelName == "كوت 1" }
        assertEquals(6, p1.quantity)

        // Delete entire purchase atomically -> restores cash, reverses transfer, removes debt & stock
        repository.deletePurchaseInvoice(purNum)
        assertEquals(5000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals(3000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals(0, db.storeDao().getAllPurchasesDirect().size)
        assertEquals(0, db.storeDao().getAllDebtsDirect().size)
    }

    @Test
    fun testEmployeeDailyExpenseBidirectionalSyncAndNoDoubleCountInReports() = runBlocking {
        repository.recordManualDeposit(3000.0, "رصيد")
        val empId = db.storeDao().insertEmployee(Employee(name = "عمر", role = "بائع", baseSalary = 1000.0))

        // Record daily employee expense -> creates BOTH EmployeeTransaction and linked ExpenseRecord
        val txId = repository.recordEmployeeTransaction(
            employeeId = empId,
            employeeName = "عمر",
            type = "صرفة يومية",
            amount = 75.0,
            notes = "مصروف غداء يومي",
            paidFromCashDrawer = true
        )
        val expenses = db.storeDao().getAllExpensesDirect()
        val empTxs = db.storeDao().getAllEmployeeTransactionsDirect()
        assertEquals(1, expenses.size)
        assertEquals(1, empTxs.size)
        assertEquals(txId, expenses.first().linkedEmployeeTxId)
        assertEquals(expenses.first().id, empTxs.first().linkedExpenseId)
        assertEquals(2925.0, db.storeDao().getAvailableCashBalance(), 0.01)

        // Report MUST count 75 ONCE (never 150 double-count!)
        val report = StatementHelper.buildDailyReportSummary(
            periodTitle = "اليوم",
            filteredSales = emptyList(),
            filteredPurchases = emptyList(),
            filteredExpenses = expenses,
            filteredEmployeeTx = empTxs,
            filteredPayments = emptyList(),
            allDebts = emptyList(),
            allProducts = emptyList(),
            currentCashBalance = db.storeDao().getAvailableCashBalance(),
            currentExchangeBalance = 0.0
        )
        assertEquals(75.0, report.expensesTotal, 0.01)
        assertEquals(0.0, report.salariesAndAdvancesTotal, 0.01)
        assertEquals(-75.0, report.netProfit, 0.01)

        // Deleting the expense cascades & removes linked EmployeeTransaction + restores cash
        repository.deleteExpense(expenses.first().id)
        assertEquals(0, db.storeDao().getAllExpensesDirect().size)
        assertEquals(0, db.storeDao().getAllEmployeeTransactionsDirect().size)
        assertEquals(3000.0, db.storeDao().getAvailableCashBalance(), 0.01)

        // Conversely, deleting EmployeeTransaction cascades & removes linked ExpenseRecord
        val tx2Id = repository.recordEmployeeTransaction(empId, "عمر", "صرفة يومية", 50.0, "مواصلات", paidFromCashDrawer = true)
        assertEquals(1, db.storeDao().getAllExpensesDirect().size)
        repository.deleteEmployeeTransaction(tx2Id)
        assertEquals(0, db.storeDao().getAllExpensesDirect().size)
        assertEquals(0, db.storeDao().getAllEmployeeTransactionsDirect().size)
        assertEquals(3000.0, db.storeDao().getAvailableCashBalance(), 0.01)
    }

    @Test
    fun testValidationRejectsMissingAccountBadSplitsOverdrawAndDuplicateNumbers() = runBlocking {
        repository.recordManualDeposit(1000.0, "رصيد")
        val accId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "بنك", balance = 400.0))
        val acc = db.storeDao().getExchangeAccountById(accId)!!
        val empId = db.storeDao().insertEmployee(Employee(name = "رامي", role = "بائع", baseSalary = 800.0))
        val prodId = db.storeDao().insertProduct(ProductItem(category = "ثوب", modelName = "ثوب", color = "أبيض", size = "L", quantity = 10, buyPrice = 50.0, sellPrice = 100.0))
        val prod = db.storeDao().getProductById(prodId)!!

        // 1. Reject non-cash expense and non-cash employee tx without exchange account
        var expRejected = false
        try {
            repository.recordExpense("كهرباء", 50.0, "تجربة", "EXP-NOACC", paidFromCash = false, exchangeAccount = null)
        } catch (e: IllegalArgumentException) {
            expRejected = true
        }
        assertTrue("Non-cash expense without account must throw", expRejected)

        var empTxRejected = false
        try {
            repository.recordEmployeeTransaction(empId, "رامي", "سلفة", 50.0, "سلفة", paidFromCashDrawer = false, exchangeAccount = null)
        } catch (e: IllegalArgumentException) {
            empTxRejected = true
        }
        assertTrue("Non-cash employee tx without account must throw", empTxRejected)

        // 2. Reject split payment != total with strict 0.01 precision (0.05 difference fails!)
        var saleSplitRejected = false
        try {
            repository.executeCartSale(
                items = listOf(CartSaleItem(prod, 1, 100.0)), // total = 100.0
                discount = 0.0,
                customerName = "عميل",
                customerPhone = "",
                paymentType = PaymentMethods.CUSTOM_SPLIT,
                cashPart = 50.0,
                transferPart = 49.95, // sum = 99.95, diff = 0.05 > 0.01!
                debtPart = 0.0,
                exchangeAccount = acc
            )
        } catch (e: IllegalArgumentException) {
            saleSplitRejected = true
        }
        assertTrue("Sale split differing by 0.05 must be rejected", saleSplitRejected)

        var purSplitRejected = false
        try {
            repository.executeCartPurchase(
                items = listOf(CartPurchaseItem("ثوب", "ثوب", "أبيض", "L", ClothingUnits.PIECE, 1.0, 100.0, 150.0)),
                supplierName = "مورد",
                invoiceNumber = "",
                paymentMethod = "مختلط (كاش + حوالة + آجل)",
                cashPaidPart = 50.0,
                transferPaidPart = 50.05, // diff = 0.05 > 0.01!
                debtPart = 0.0,
                exchangeAccount = acc,
                transferRef = "",
                notes = ""
            )
        } catch (e: IllegalArgumentException) {
            purSplitRejected = true
        }
        assertTrue("Purchase split differing by 0.05 must be rejected", purSplitRejected)

        // 3. Reject withdrawal, transfer, expense, and overpayment > balance/debt
        var overWithdraw = false
        try { repository.recordOwnerWithdrawal(1500.0, "سحب زائد") } catch (_: IllegalStateException) { overWithdraw = true }
        assertTrue(overWithdraw)

        var overTransfer = false
        try { repository.transferBetweenCashAndExchange(accId, 500.0, fromCashToExchange = false, notes = "زائد") } catch (_: IllegalStateException) { overTransfer = true }
        assertTrue(overTransfer)

        val dId = db.storeDao().insertDebt(DebtRecord(type = "CUSTOMER", partyName = "زبون", originalAmount = 200.0, remainingAmount = 200.0, dueDate = 0L, isSettled = false))
        val debtObj = db.storeDao().getDebtById(dId)!!
        var overPayDebt = false
        try { repository.recordDebtPayment(debtObj, 250.0, "زيادة", "نقداً") } catch (_: IllegalArgumentException) { overPayDebt = true }
        assertTrue("Payment exceeding debt remainingAmount must be rejected", overPayDebt)

        // 4. Reject duplicate manual invoice/voucher numbers & verify unique auto numbers after deletions
        val autoInv1 = repository.executeCartSale(listOf(CartSaleItem(prod, 1, 100.0)), 0.0, "عميل 1", "", PaymentMethods.CASH, customInvoiceNumber = "INV-DUP")
        assertEquals("INV-DUP", autoInv1)
        var dupSaleRejected = false
        try {
            repository.executeCartSale(listOf(CartSaleItem(prod, 1, 100.0)), 0.0, "عميل 2", "", PaymentMethods.CASH, customInvoiceNumber = "INV-DUP")
        } catch (e: IllegalArgumentException) {
            dupSaleRejected = true
        }
        assertTrue("Duplicate manual sale invoice number must fail", dupSaleRejected)

        repository.recordCollectionReceipt("عميل 1", null, 50.0, "SND-99", "نقداً")
        var dupVoucherRejected = false
        try {
            repository.recordCollectionReceipt("عميل 2", null, 50.0, "SND-99", "نقداً")
        } catch (e: IllegalArgumentException) {
            dupVoucherRejected = true
        }
        assertTrue("Duplicate manual voucher number must fail", dupVoucherRejected)

        val autoPur1 = repository.executeCartPurchase(listOf(CartPurchaseItem("ثوب", "صنف أ", "أبيض", "M", ClothingUnits.PIECE, 1.0, 50.0, 90.0)), "مورد", "", "نقداً من الصندوق", 50.0, 0.0, 0.0, null, "", "")
        val autoPur2 = repository.executeCartPurchase(listOf(CartPurchaseItem("ثوب", "صنف ب", "أسود", "L", ClothingUnits.PIECE, 1.0, 50.0, 90.0)), "مورد", "", "نقداً من الصندوق", 50.0, 0.0, 0.0, null, "", "")
        assertNotEquals(autoPur1, autoPur2)
        repository.deletePurchaseInvoice(autoPur1)
        val autoPur3 = repository.executeCartPurchase(listOf(CartPurchaseItem("ثوب", "صنف ج", "أزرق", "L", ClothingUnits.PIECE, 1.0, 50.0, 90.0)), "مورد", "", "نقداً من الصندوق", 50.0, 0.0, 0.0, null, "", "")
        assertNotEquals(autoPur2, autoPur3)
    }

    @Test
    @Config(sdk = [36], shadows = [TestPdfDocumentShadow::class, TestPdfDocumentPageShadow::class])
    fun testArchiveLinkedEntitiesStatementInitialBalanceReportsAndMultiPagePdf() = runBlocking {
        repository.recordManualDeposit(2000.0, "رصيد")
        val empId = db.storeDao().insertEmployee(Employee(name = "موظف مرتبط", role = "بائع", baseSalary = 1000.0))
        val unlinkedEmpId = db.storeDao().insertEmployee(Employee(name = "موظف حر", role = "بائع", baseSalary = 500.0))
        val accId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "صراف مرتبط", balance = 500.0))
        val unlinkedAccId = db.storeDao().insertExchangeAccount(ExchangeAccount(name = "صراف حر", balance = 0.0))
        val partyId = repository.saveParty(PartyAccount(type = "CUSTOMER", name = "عميل مرتبط"), initialDebt = 500.0)
        val unlinkedPartyId = repository.saveParty(PartyAccount(type = "SUPPLIER", name = "مورد حر"), initialDebt = 0.0)

        repository.recordEmployeeTransaction(empId, "موظف مرتبط", "سلفة", 100.0, "سلفة", paidFromCashDrawer = true)
        val acc = db.storeDao().getExchangeAccountById(accId)!!
        repository.recordCollectionReceipt("عميل مرتبط", null, 150.0, "", "صرافة/محفظة", acc, "دفعة من الرصيد الافتتاحي")

        // Archiving linked entities vs hard-deleting unlinked ones
        assertEquals(DeleteEntityResult.ARCHIVED, repository.deleteEmployee(empId))
        assertEquals(DeleteEntityResult.DELETED, repository.deleteEmployee(unlinkedEmpId))
        assertEquals(DeleteEntityResult.ARCHIVED, repository.deleteExchangeAccount(accId))
        assertEquals(DeleteEntityResult.DELETED, repository.deleteExchangeAccount(unlinkedAccId))
        assertEquals(DeleteEntityResult.ARCHIVED, repository.deleteParty(partyId))
        assertEquals(DeleteEntityResult.DELETED, repository.deleteParty(unlinkedPartyId))

        // Active lists exclude archived entities while statements preserve full history
        assertTrue(db.storeDao().getAllEmployees().first().none { it.id == empId })
        assertTrue(db.storeDao().getAllExchangeAccounts().first().none { it.id == accId })
        assertTrue(db.storeDao().getAllParties().first().none { it.id == partyId })

        val archivedParty = db.storeDao().getPartyById(partyId)!!
        assertTrue(archivedParty.isArchived)
        // Customer initialBalance = 500 -> single opening DebtRecord of 500 (no double-count) minus 150 payment = 350 net
        val stmt = StatementHelper.buildPartyStatement(
            party = archivedParty,
            sales = emptyList(),
            purchases = emptyList(),
            debts = db.storeDao().getAllDebtsDirect(),
            payments = db.storeDao().getAllDebtPaymentsDirect()
        )
        assertEquals(500.0, stmt.totalOnParty, 0.01)
        assertEquals(150.0, stmt.totalForParty, 0.01)
        assertEquals(350.0, stmt.netBalance, 0.01)

        // Multi-page PDF wrapping test (40 items + long Arabic text)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = StoreSettings(shopNameAr = "متجر النور", phone = "777000000")
        val longLines = (1..40).map { i ->
            InvoiceHelper.PdfSaleLineItem(
                name = "صنف ملابس فاخرة طويل جداً رقم $i مع تفاصيل إضافية لضمان التفاف النص تلقائياً على أكثر من سطر",
                details = "تفاصيل مقاسات وألوان متعددة وملاحظات طويلة للزبون رقم $i بدون أي قص أو تداخل",
                quantity = i,
                unitPrice = 125.5,
                total = i * 125.5,
                unit = ClothingUnits.PIECE
            )
        }
        val pdfFile = InvoiceHelper.generateSaleInvoicePdf(
            context = context,
            invoiceNumber = "INV-LONG-40",
            customerName = archivedParty.name,
            customerPhone = "777123456",
            employeeName = "موظف مرتبط",
            paymentType = PaymentMethods.CASH,
            items = longLines,
            subtotal = longLines.sumOf { it.total },
            discount = 50.0,
            netTotal = longLines.sumOf { it.total } - 50.0,
            cashPaid = longLines.sumOf { it.total } - 50.0,
            transferPaid = 0.0,
            debtAmount = 0.0,
            exchangeAccountName = "",
            transferRef = "",
            timestamp = System.currentTimeMillis(),
            settings = settings
        )
        assertTrue("Generated multi-page PDF must exist and be non-empty", pdfFile.exists() && pdfFile.length() > 50L)
        val salePdfContent = pdfFile.readText(Charsets.UTF_8)
        assertTrue(salePdfContent.startsWith("%PDF-"))
        assertTrue("40 long items must paginate into >= 2 pages", Regex("FinishedPages=(\\d+)").find(salePdfContent)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= 2 } == true)

        val stmtPdf = InvoiceHelper.generatePartyStatementPdf(context, stmt, StatementMode.DETAILED, settings)
        assertTrue(stmtPdf.exists() && stmtPdf.length() > 50L)

        val purchasePdfItems = (1..35).map { i ->
            CartPurchaseItem("أكوات", "صنف شراء طويل جداً رقم $i مع مواصفات كاملة للمستودع", "أسود", "XL", ClothingUnits.PIECE, 2.0, 80.0, 120.0)
        }
        val purchasePdf = InvoiceHelper.generateMultiItemPurchaseInvoicePdf(
            context = context,
            invoiceNumber = "PUR-LONG-35",
            supplierName = "مورد الأناقة",
            paymentMethod = "نقداً من الصندوق",
            exchangeAccountName = "",
            transferRef = "",
            items = purchasePdfItems,
            totalCost = purchasePdfItems.sumOf { it.lineCost },
            cashPaid = purchasePdfItems.sumOf { it.lineCost },
            transferPaid = 0.0,
            debtAmount = 0.0,
            timestamp = System.currentTimeMillis(),
            settings = settings
        )
        assertTrue(purchasePdf.exists() && purchasePdf.length() > 50L)
        val purchasePdfContent = purchasePdf.readText(Charsets.UTF_8)
        assertTrue("35 long purchase items must paginate into >= 2 pages", Regex("FinishedPages=(\\d+)").find(purchasePdfContent)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= 2 } == true)

        val activeTransfer = ExchangeTransfer(accountId = accId, accountName = "صراف مرتبط", type = "إيداع", amount = 150.0, isCredit = true, title = "تحصيل")
        val reversedTransfer = ExchangeTransfer(accountId = accId, accountName = "صراف مرتبط", type = "إيداع", amount = 999.0, isCredit = true, title = "حركة ملغاة", isReversed = true)
        val reversalTransfer = ExchangeTransfer(accountId = accId, accountName = "صراف مرتبط", type = "عكس", amount = 999.0, isCredit = false, title = "قيد عكسي", isReversalEntry = true)
        val dailyPdf = InvoiceHelper.generateDailyMovementPdf(
            context = context,
            movements = db.storeDao().getAllCashMovementsDirect(),
            transfers = listOf(activeTransfer, reversedTransfer, reversalTransfer),
            currentCash = db.storeDao().getAvailableCashBalance(),
            currentExchange = 650.0,
            periodTitle = "الكل",
            settings = settings
        )
        assertTrue(dailyPdf.exists() && dailyPdf.length() > 50L)

        val reportPdf = InvoiceHelper.generateFinancialReportPdf(
            context = context,
            periodTitle = "الكل",
            sales = emptyList(),
            expenses = db.storeDao().getAllExpensesDirect(),
            employeeTx = db.storeDao().getAllEmployeeTransactionsDirect(),
            purchases = emptyList(),
            settings = settings
        )
        assertTrue(reportPdf.exists() && reportPdf.length() > 50L)
    }

    @Test
    fun testManagerPinSaltedHashingStrengthValidationAndLockout() = runBlocking {
        // Weak PIN "1234" rejected
        assertNotNull(PinSecurityManager.validatePinStrength("1234"))
        assertNotNull(PinSecurityManager.validatePinStrength("1111"))
        assertNull(PinSecurityManager.validatePinStrength("8492"))

        // Set strong PIN and verify stored hashed (plaintext cleared)
        repository.updateManagerPin("8492")
        val settings = db.storeDao().getStoreSettingsDirect()!!
        assertEquals("", settings.managerPin)
        assertTrue(settings.managerPinHash.isNotBlank())
        assertTrue(settings.managerPinSalt.isNotBlank())
        assertFalse(settings.mustChangePin)

        // Wrong attempts 1..4 -> Invalid, 5th -> Locked
        for (attempt in 1..4) {
            val res = repository.verifyManagerPin("9999")
            assertTrue("Attempt $attempt should be Invalid", res is PinVerificationResult.Invalid)
        }
        val locked = repository.verifyManagerPin("9999")
        assertTrue("5th failed attempt must lock out", locked is PinVerificationResult.Locked)

        // Even correct PIN rejected while locked out
        assertTrue(repository.verifyManagerPin("8492") is PinVerificationResult.Locked)

        // Expire lock window and confirm correct PIN unlocks & succeeds
        db.storeDao().updateStoreSettings(db.storeDao().getStoreSettingsDirect()!!.copy(pinLockUntilTimestamp = System.currentTimeMillis() - 1000L))
        assertEquals(PinVerificationResult.Success, repository.verifyManagerPin("8492"))
    }

    @Test
    @Config(sdk = [36], shadows = [TestPdfDocumentShadow::class, TestPdfDocumentPageShadow::class])
    fun testOpeningBalancesCashExchangeInventoryPartiesAndEmployeesDoNotDeductCashOrDistortProfit() = runBlocking {
        val pastTs = System.currentTimeMillis() - (3L * 86_400_000L)

        // 1. Opening Cash Drawer Balance (additive then replace mode) + auto shift creation
        repository.setOpeningCashBalance(
            amount = 20_000.0,
            replaceExistingBalance = false,
            notes = "كاش افتتاحي بالدرج",
            timestamp = pastTs
        )
        assertNotNull("Open shift must be auto-created for opening cash", db.storeDao().getActiveShift().firstOrNull())
        assertEquals(20_000.0, db.storeDao().getAvailableCashBalance(), 0.01)

        repository.setOpeningCashBalance(
            amount = 25_000.0,
            replaceExistingBalance = true,
            notes = "اعتماد الرصيد الفعلي",
            timestamp = pastTs
        )
        assertEquals(25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)

        // 2. Opening Exchange Accounts & Wallets (ZERO cash deduction)
        val accId = repository.saveOpeningExchangeAccount(
            name = "الكريمي",
            type = "صراف",
            accountNumber = "123456",
            phone = "777000111",
            openingBalance = 15_000.0,
            notes = "رصيد الكريمي السابق",
            timestamp = pastTs
        )
        assertTrue(accId > 0L)
        // Update same account in replace mode
        repository.saveOpeningExchangeAccount(
            accountId = accId,
            name = "الكريمي",
            type = "صراف",
            openingBalance = 18_000.0,
            replaceExistingBalance = true,
            timestamp = pastTs
        )
        assertEquals(18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals("Cash must remain completely untouched by exchange opening balance", 25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertTrue(db.storeDao().getAllExchangeTransfersDirect().any { it.referenceType == "OPENING_BALANCE" && it.type == "رصيد افتتاحي" })

        // 3. Opening Inventory (batch with dozen + piece, ZERO cash/exchange/purchase/debt impact)
        val addedCount = repository.addOpeningInventoryItems(
            items = listOf(
                CartPurchaseItem(
                    category = "أكوات",
                    modelName = "كوت شتوي افتتاحي",
                    color = "أسود",
                    size = ClothingSizes.BIG,
                    unit = ClothingUnits.DOZEN,
                    unitCount = 2.0,
                    buyPricePerUnit = 12_000.0,
                    sellPricePerUnit = 18_000.0,
                    notes = "مخزن قديم"
                ),
                CartPurchaseItem(
                    category = "دجلات",
                    modelName = "دجلة افتتاحية",
                    color = "كحلي",
                    size = ClothingSizes.YOUTH,
                    unit = ClothingUnits.PIECE,
                    unitCount = 5.0,
                    buyPricePerUnit = 500.0,
                    sellPricePerUnit = 800.0
                )
            ),
            timestamp = pastTs
        )
        assertEquals(2, addedCount)
        val allProducts = db.storeDao().getAllProductsDirect()
        assertEquals(2, allProducts.size)
        val totalPieces = allProducts.sumOf { it.quantity }
        val totalInventoryCost = allProducts.sumOf { it.buyPrice * it.quantity }
        assertEquals(29, totalPieces)
        assertEquals(26_500.0, totalInventoryCost, 0.01)
        assertTrue(allProducts.all { it.invoiceNumber == "OPENING" })
        assertEquals("Cash untouched after opening inventory", 25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals("Exchange untouched after opening inventory", 18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals("Zero PurchaseRecords created for opening stock", 0, db.storeDao().getAllPurchasesDirect().size)
        assertEquals("Zero Debts created for opening stock", 0, db.storeDao().getAllDebtsDirect().size)

        // 4. Customer Opening Balances (both owed to store & surplus advance credit)
        val custOwedId = repository.recordPartyOpeningBalance(
            type = "CUSTOMER",
            name = "خالد الزبون",
            phone = "771111111",
            amount = 4_000.0,
            isNormalDebtDirection = true,
            notes = "دين قديم",
            timestamp = pastTs
        )
        val custSurplusId = repository.recordPartyOpeningBalance(
            type = "CUSTOMER",
            name = "سعيد فائض",
            phone = "772222222",
            amount = 1_200.0,
            isNormalDebtDirection = false,
            notes = "مبلغ مقدم له",
            timestamp = pastTs
        )

        val custOwedStmt = StatementHelper.buildPartyStatement(
            party = db.storeDao().getPartyById(custOwedId)!!,
            sales = emptyList(),
            purchases = emptyList(),
            debts = db.storeDao().getAllDebtsDirect(),
            payments = emptyList()
        )
        assertEquals(4_000.0, custOwedStmt.totalOnParty, 0.01)
        assertEquals(4_000.0, custOwedStmt.netBalance, 0.01)

        val custSurplusStmt = StatementHelper.buildPartyStatement(
            party = db.storeDao().getPartyById(custSurplusId)!!,
            sales = emptyList(),
            purchases = emptyList(),
            debts = db.storeDao().getAllDebtsDirect(),
            payments = emptyList()
        )
        assertEquals(1_200.0, custSurplusStmt.totalForParty, 0.01)
        assertEquals(-1_200.0, custSurplusStmt.netBalance, 0.01)

        // 5. Supplier Opening Balances (both owed by store & advance surplus with supplier)
        val supOwedId = repository.recordPartyOpeningBalance(
            type = "SUPPLIER",
            name = "معمل التميز",
            phone = "773333333",
            amount = 9_000.0,
            isNormalDebtDirection = true,
            notes = "رصيد سابق للمورد",
            timestamp = pastTs
        )
        val supSurplusId = repository.recordPartyOpeningBalance(
            type = "SUPPLIER",
            name = "مورد مقدم",
            phone = "774444444",
            amount = 2_500.0,
            isNormalDebtDirection = false,
            notes = "دفعة مقدمة سابقة عند المورد",
            timestamp = pastTs
        )

        val supOwedStmt = StatementHelper.buildPartyStatement(
            party = db.storeDao().getPartyById(supOwedId)!!,
            sales = emptyList(),
            purchases = emptyList(),
            debts = db.storeDao().getAllDebtsDirect(),
            payments = emptyList()
        )
        assertEquals(9_000.0, supOwedStmt.totalForParty, 0.01)
        assertEquals(9_000.0, supOwedStmt.netBalance, 0.01)

        val supSurplusStmt = StatementHelper.buildPartyStatement(
            party = db.storeDao().getPartyById(supSurplusId)!!,
            sales = emptyList(),
            purchases = emptyList(),
            debts = db.storeDao().getAllDebtsDirect(),
            payments = emptyList()
        )
        assertEquals(2_500.0, supSurplusStmt.totalOnParty, 0.01)
        assertEquals(-2_500.0, supSurplusStmt.netBalance, 0.01)

        assertEquals(0, db.storeDao().getAllSalesDirect().size)
        assertEquals(0, db.storeDao().getAllPurchasesDirect().size)
        assertEquals(0, db.storeDao().getAllDebtPaymentsDirect().size)

        // 6. Employees Opening Balances (both prior dues owed to employee & prior advances owed by employee)
        val empOwedId = repository.recordEmployeeOpeningBalance(
            name = "سالم البائع",
            phone = "775555555",
            role = "بائع",
            salaryType = "شهري",
            baseSalary = 2_000.0,
            openingAmount = 1_500.0,
            isOwedToEmployee = true,
            notes = "راتب متأخر",
            timestamp = pastTs
        )
        val empAdvanceId = repository.recordEmployeeOpeningBalance(
            name = "علي الموظف",
            phone = "776666666",
            role = "كاشير",
            salaryType = "شهري",
            baseSalary = 1_000.0,
            openingAmount = 400.0,
            isOwedToEmployee = false,
            notes = "سلفة قديمة",
            timestamp = pastTs
        )

        val allEmpTxs = db.storeDao().getAllEmployeeTransactionsDirect()
        assertEquals(2, allEmpTxs.size)
        assertTrue(allEmpTxs.all { !it.paidFromCash && it.exchangeAccountId == 0L })

        val empOwedSummary = StatementHelper.buildEmployeeStatement(
            employee = db.storeDao().getEmployeeById(empOwedId)!!,
            transactions = allEmpTxs,
            sales = emptyList()
        )
        assertEquals(1_500.0, empOwedSummary.openingCredit, 0.01)
        assertEquals(3_500.0, empOwedSummary.remainingBalance, 0.01)

        val empAdvanceSummary = StatementHelper.buildEmployeeStatement(
            employee = db.storeDao().getEmployeeById(empAdvanceId)!!,
            transactions = allEmpTxs,
            sales = emptyList()
        )
        assertEquals(400.0, empAdvanceSummary.openingDebit, 0.01)
        assertEquals(600.0, empAdvanceSummary.remainingBalance, 0.01)

        assertEquals("Cash untouched after employee opening balances", 25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals("Exchange untouched after employee opening balances", 18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals("Zero ExpenseRecords created for opening balances", 0, db.storeDao().getAllExpensesDirect().size)

        // 7. Financial P&L Report must NOT treat opening balances as daily sales, purchases, expenses, or payroll cost
        val reportSummary = StatementHelper.buildDailyReportSummary(
            periodTitle = "الكل",
            filteredSales = db.storeDao().getAllSalesDirect(),
            filteredPurchases = db.storeDao().getAllPurchasesDirect(),
            filteredExpenses = db.storeDao().getAllExpensesDirect(),
            filteredEmployeeTx = db.storeDao().getAllEmployeeTransactionsDirect(),
            filteredPayments = db.storeDao().getAllDebtPaymentsDirect(),
            allDebts = db.storeDao().getAllDebtsDirect(),
            allProducts = db.storeDao().getAllProductsDirect(),
            currentCashBalance = db.storeDao().getAvailableCashBalance(),
            currentExchangeBalance = db.storeDao().getExchangeAccountById(accId)!!.balance
        )
        assertEquals(0.0, reportSummary.totalSales, 0.01)
        assertEquals(0.0, reportSummary.totalPurchases, 0.01)
        assertEquals(0.0, reportSummary.expensesTotal, 0.01)
        assertEquals(0.0, reportSummary.salariesAndAdvancesTotal, 0.01)
        assertEquals(0.0, reportSummary.collectionsTotal, 0.01)
        assertEquals(0.0, reportSummary.supplierPaymentsTotal, 0.01)
        assertEquals(0.0, reportSummary.netProfit, 0.01)

        // 8. Editing & deleting non-monetary opening employee transaction must NOT touch cash or exchange
        val openingEmpTx = allEmpTxs.first { it.employeeId == empOwedId }
        repository.updateEmployeeTransaction(
            newTx = openingEmpTx.copy(
                type = "رصيد افتتاحي (له)",
                amount = 1_800.0,
                notes = "تعديل مستحقات سابقة",
                paidFromCash = false,
                exchangeAccountId = 0L,
                exchangeAccountName = "",
                timestamp = pastTs
            )
        )
        assertEquals(25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals(18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals(1_800.0, db.storeDao().getEmployeeTransactionById(openingEmpTx.id)!!.amount, 0.01)

        repository.deleteEmployeeTransaction(openingEmpTx.id)
        assertEquals(25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals(18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)

        // 9. Backup export/validate/restore round-trip preserves all opening balances cleanly
        val backupJson = BackupManager.createBackupJsonString(repository)
        val parsed = BackupManager.validateAndParseBackup(backupJson)
        assertTrue(parsed.isSuccess)
        repository.clearEverythingCompletely(clearConfigDictionaries = true)
        assertEquals(0, db.storeDao().getAllProductsDirect().size)
        repository.restoreFullBackupSnapshot(parsed.getOrThrow().snapshot)
        assertEquals(25_000.0, db.storeDao().getAvailableCashBalance(), 0.01)
        assertEquals(18_000.0, db.storeDao().getExchangeAccountById(accId)!!.balance, 0.01)
        assertEquals(2, db.storeDao().getAllProductsDirect().size)
        assertEquals(4, db.storeDao().getAllParties().first().size)
        assertEquals(2, db.storeDao().getAllEmployees().first().size)

        // Verify employee statement PDF generation works with opening credit/debit fields
        val context = ApplicationProvider.getApplicationContext<Context>()
        val empPdf = InvoiceHelper.generateEmployeeStatementPdf(
            context = context,
            summary = empOwedSummary,
            mode = StatementMode.DETAILED,
            settings = StoreSettings(shopNameAr = "متجر النور")
        )
        assertTrue(empPdf.exists() && empPdf.length() > 50L)
    }
}
