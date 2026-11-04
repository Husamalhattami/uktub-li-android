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
}
