package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.CashDrawerMovement
import com.example.data.model.ReturnRecord
import com.example.data.model.ReturnSettlementMethods
import com.example.data.model.ReturnTypes
import com.example.ui.StoreViewModel
import com.example.ui.util.PinSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val createdDbNames = mutableListOf<String>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        createdDbNames.clear()
    }

    @After
    fun tearDown() {
        createdDbNames.forEach { context.deleteDatabase(it) }
    }

    private fun createVersionedSeededDb(
        dbName: String,
        targetVersion: Int,
        legacyPin: String = "8492"
    ) {
        createdDbNames.add(dbName)
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(targetVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createV2SchemaAndSeed(db)
                if (targetVersion >= 3) upgrade2To3AndSeed(db)
                if (targetVersion >= 4) upgrade3To4AndSeed(db)
                if (targetVersion >= 5) upgrade4To5AndSeed(db)
                if (targetVersion >= 6) upgrade5To6AndSeed(db, legacyPin)
                if (targetVersion >= 7) upgrade6To7AndSeed(db)
                if (targetVersion >= 8) upgrade7To8AndSeed(db)
                if (targetVersion >= 9) upgrade8To9AndSeed(db)
                if (targetVersion >= 10) upgrade9To10AndSeed(db, legacyPin)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close()
        helper.close()
    }

    private fun createV2SchemaAndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `category` TEXT NOT NULL,
                `modelName` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `size` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `buyPrice` REAL NOT NULL,
                `sellPrice` REAL NOT NULL,
                `supplierName` TEXT NOT NULL,
                `purchaseDate` INTEGER NOT NULL,
                `invoiceNumber` TEXT NOT NULL,
                `barcode` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `lastSoldTimestamp` INTEGER NOT NULL,
                `unit` TEXT NOT NULL,
                `colorsCount` INTEGER NOT NULL,
                `colorDetails` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `productName` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `size` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitBuyPrice` REAL NOT NULL,
                `unitSellPrice` REAL NOT NULL,
                `discount` REAL NOT NULL,
                `totalAmount` REAL NOT NULL,
                `profit` REAL NOT NULL,
                `customerName` TEXT NOT NULL,
                `customerPhone` TEXT NOT NULL,
                `paymentType` TEXT NOT NULL,
                `cashAmount` REAL NOT NULL,
                `transferAmount` REAL NOT NULL,
                `exchangeAccountId` INTEGER NOT NULL,
                `exchangeAccountName` TEXT NOT NULL,
                `transferRef` TEXT NOT NULL,
                `employeeName` TEXT NOT NULL,
                `employeeId` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `shiftId` INTEGER NOT NULL,
                `unit` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchases` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `supplierName` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `modelName` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `size` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `unitBuyPrice` REAL NOT NULL,
                `unitSellPrice` REAL NOT NULL,
                `totalCost` REAL NOT NULL,
                `paidAmount` REAL NOT NULL,
                `remainingDebt` REAL NOT NULL,
                `invoiceNumber` TEXT NOT NULL,
                `paymentMethod` TEXT NOT NULL,
                `cashPaid` REAL NOT NULL,
                `transferPaid` REAL NOT NULL,
                `exchangeAccountId` INTEGER NOT NULL,
                `exchangeAccountName` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `unit` TEXT NOT NULL,
                `unitCount` REAL NOT NULL,
                `colorsCount` INTEGER NOT NULL,
                `notes` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `expenses` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `category` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `notes` TEXT NOT NULL,
                `receiptNumber` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `paidFromCash` INTEGER NOT NULL,
                `exchangeAccountId` INTEGER NOT NULL,
                `exchangeAccountName` TEXT NOT NULL,
                `shiftId` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `employees` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `baseSalary` REAL NOT NULL,
                `salaryType` TEXT NOT NULL,
                `commissionPercent` REAL NOT NULL,
                `active` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `employee_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `employeeId` INTEGER NOT NULL,
                `employeeName` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `notes` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `shiftId` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `debts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `partyName` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `originalAmount` REAL NOT NULL,
                `paidAmount` REAL NOT NULL,
                `remainingAmount` REAL NOT NULL,
                `details` TEXT NOT NULL,
                `referenceSaleId` INTEGER,
                `referencePurchaseId` INTEGER,
                `dueDate` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isSettled` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `debt_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `debtId` INTEGER NOT NULL,
                `partyName` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `notes` TEXT NOT NULL,
                `shiftId` INTEGER NOT NULL,
                `paymentMethod` TEXT NOT NULL,
                `exchangeAccountId` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `shifts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `openedAt` INTEGER NOT NULL,
                `closedAt` INTEGER,
                `openingBalance` REAL NOT NULL,
                `closingBalanceActual` REAL,
                `isOpen` INTEGER NOT NULL,
                `cashierName` TEXT NOT NULL,
                `notes` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cash_movements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `shiftId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `isIncome` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `details` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `referenceSaleId` INTEGER,
                `referencePurchaseId` INTEGER,
                `referenceExpenseId` INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `exchange_accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `accountNumber` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `balance` REAL NOT NULL,
                `type` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `exchange_transfers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `accountName` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `isCredit` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `referenceNumber` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())

        val ts = 1700000000000L
        db.execSQL("INSERT INTO products (id, category, modelName, color, size, quantity, buyPrice, sellPrice, supplierName, purchaseDate, invoiceNumber, barcode, notes, lastSoldTimestamp, unit, colorsCount, colorDetails) VALUES (1, 'أكوات', 'كوت شتوي أصلي', 'أسود', 'كبير', 15, 120.0, 200.0, 'معمل النجم', $ts, 'PUR-V2', '123456', 'صنف قديم', $ts, 'حبه', 1, 'أسود')")
        db.execSQL("INSERT INTO employees (id, name, phone, role, startDate, baseSalary, salaryType, commissionPercent, active) VALUES (1, 'سعيد الحداد', '777111222', 'بائع', $ts, 1500.0, 'شهري', 2.0, 1)")
        db.execSQL("INSERT INTO employee_transactions (id, employeeId, employeeName, type, amount, notes, timestamp, shiftId) VALUES (1, 1, 'سعيد الحداد', 'سلفة', 200.0, 'سلفة قديمة', $ts, 1)")
        db.execSQL("INSERT INTO exchange_accounts (id, name, accountNumber, phone, balance, type, notes, createdAt) VALUES (1, 'صرافة الكريمي', '300100', '770000111', 5000.0, 'صراف', 'حساب أساسي', $ts)")
        db.execSQL("INSERT INTO exchange_transfers (id, accountId, accountName, type, amount, isCredit, title, referenceNumber, notes, timestamp) VALUES (1, 1, 'صرافة الكريمي', 'إيداع من الكاش', 500.0, 1, 'تغذية', 'TR-V2', 'حوالة قديمة', $ts)")
        db.execSQL("INSERT INTO purchases (id, supplierName, category, modelName, color, size, quantity, unitBuyPrice, unitSellPrice, totalCost, paidAmount, remainingDebt, invoiceNumber, paymentMethod, cashPaid, transferPaid, exchangeAccountId, exchangeAccountName, timestamp, unit, unitCount, colorsCount, notes) VALUES (1, 'معمل النجم', 'أكوات', 'كوت شتوي أصلي', 'أسود', 'كبير', 15, 120.0, 200.0, 1800.0, 1000.0, 800.0, 'PUR-V2', 'مختلط', 1000.0, 0.0, 0, '', $ts, 'حبه', 15.0, 1, 'شراء سابق')")
        db.execSQL("INSERT INTO sales (id, productId, productName, category, color, size, quantity, unitBuyPrice, unitSellPrice, discount, totalAmount, profit, customerName, customerPhone, paymentType, cashAmount, transferAmount, exchangeAccountId, exchangeAccountName, transferRef, employeeName, employeeId, timestamp, shiftId, unit) VALUES (1, 1, 'كوت شتوي أصلي', 'أكوات', 'أسود', 'كبير', 2, 120.0, 200.0, 0.0, 400.0, 160.0, 'عميل قديم', '771234567', 'نقداً', 400.0, 0.0, 0, '', '', 'سعيد الحداد', 1, $ts, 1, 'حبه')")
        db.execSQL("INSERT INTO expenses (id, category, amount, notes, receiptNumber, timestamp, paidFromCash, exchangeAccountId, exchangeAccountName, shiftId) VALUES (1, 'كهرباء', 150.0, 'فاتورة كهرباء قديمة', 'EXP-V2', $ts, 1, 0, '', 1)")
        db.execSQL("INSERT INTO debts (id, type, partyName, phone, originalAmount, paidAmount, remainingAmount, details, referenceSaleId, referencePurchaseId, dueDate, createdAt, isSettled) VALUES (1, 'SUPPLIER', 'معمل النجم', '770999888', 800.0, 200.0, 600.0, 'متبقي فاتورة PUR-V2', NULL, 1, $ts, $ts, 0)")
        db.execSQL("INSERT INTO debt_payments (id, debtId, partyName, amount, timestamp, notes, shiftId, paymentMethod, exchangeAccountId) VALUES (1, 1, 'معمل النجم', 200.0, $ts, 'دفعة قديمة', 1, 'نقداً', 0)")
        db.execSQL("INSERT INTO shifts (id, openedAt, closedAt, openingBalance, closingBalanceActual, isOpen, cashierName, notes) VALUES (1, $ts, NULL, 1000.0, NULL, 1, 'المدير', 'وردية مفتوحة')")
        db.execSQL("INSERT INTO cash_movements (id, shiftId, type, amount, isIncome, title, details, timestamp, referenceSaleId, referencePurchaseId, referenceExpenseId) VALUES (1, 1, 'SALE_CASH', 400.0, 1, 'فاتورة بيع قديمة', 'حفظ نقدي', $ts, 1, NULL, NULL)")
    }

    private fun upgrade2To3AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employee_transactions ADD COLUMN paidFromCash INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE employee_transactions ADD COLUMN exchangeAccountId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE employee_transactions ADD COLUMN exchangeAccountName TEXT NOT NULL DEFAULT ''")
    }

    private fun upgrade3To4AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cash_movements ADD COLUMN referenceEmployeeTxId INTEGER")
    }

    private fun upgrade4To5AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `parties` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `initialBalance` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO parties (id, type, name, phone, address, notes, initialBalance, createdAt) VALUES (1, 'CUSTOMER', 'عميل قديم', '771234567', 'صنعاء', 'حساب محفوظ', 300.0, 1700000000000)")
    }

    private fun upgrade5To6AndSeed(db: SupportSQLiteDatabase, legacyPin: String) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `audit_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `action` TEXT NOT NULL,
                `actor` TEXT NOT NULL,
                `details` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `store_settings` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `shopNameAr` TEXT NOT NULL,
                `shopNameEn` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `taxNumber` TEXT NOT NULL,
                `invoiceHeader` TEXT NOT NULL,
                `invoiceFooter` TEXT NOT NULL,
                `showProfitInReports` INTEGER NOT NULL,
                `managerPin` TEXT NOT NULL,
                `isManagerRole` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO audit_logs (id, action, actor, details, timestamp) VALUES (1, 'تسجيل بيع', 'المدير', 'عملية قديمة', 1700000000000)")
        db.execSQL("""
            INSERT INTO store_settings (
                id, shopNameAr, shopNameEn, phone, address, currency, taxNumber, invoiceHeader, invoiceFooter, showProfitInReports, managerPin, isManagerRole
            ) VALUES (
                1, 'محل التراث للألبسة', 'Heritage Wear', '777444555', 'صنعاء', 'ريال', '101', 'محل التراث', 'شكراً لكم', 1, '$legacyPin', 1
            )
        """.trimIndent())
    }

    private fun upgrade6To7AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN debtAmount REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE sales ADD COLUMN invoiceNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE sales SET invoiceNumber = 'INV-LEGACY-7', debtAmount = 50.0 WHERE id = 1")
    }

    private fun upgrade7To8AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE debt_payments ADD COLUMN voucherNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE debt_payments SET voucherNumber = 'SND-OLD-8' WHERE id = 1")
    }

    private fun upgrade8To9AndSeed(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE store_settings ADD COLUMN logoPath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE store_settings ADD COLUMN isSetupCompleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE store_settings SET isSetupCompleted = 1 WHERE id = 1")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `store_config_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO store_config_items (id, type, name, createdAt) VALUES (1, 'PRODUCT_CATEGORY', 'أكوات ملكية', 1700000000000)")
    }

    private fun upgrade9To10AndSeed(db: SupportSQLiteDatabase, legacyPin: String) {
        AppDatabase.MIGRATION_9_10.migrate(db)
        assertNotNull(legacyPin)
    }

    @Test
    fun testMigrateAllPreviousVersions2Through10To11PreservesEveryTableAndRow() = runBlocking {
        for (startVersion in 2..10) {
            val dbName = "migration_test_v${startVersion}_to_11.db"
            val legacyPin = if (startVersion == 7) "1234" else "8492"
            createVersionedSeededDb(dbName, startVersion, legacyPin)

            val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                val dao = db.storeDao()

                val products = dao.getAllProducts().first()
                assertEquals("v$startVersion products count", 1, products.size)
                assertEquals("كوت شتوي أصلي", products.first().modelName)
                assertEquals(15, products.first().quantity)

                val sales = dao.getAllSales().first()
                assertEquals("v$startVersion sales count", 1, sales.size)
                assertEquals(400.0, sales.first().totalAmount, 0.01)
                if (startVersion >= 7) {
                    assertEquals("INV-LEGACY-7", sales.first().invoiceNumber)
                    assertEquals(50.0, sales.first().debtAmount, 0.01)
                }

                val purchases = dao.getAllPurchases().first()
                assertEquals("v$startVersion purchases count", 1, purchases.size)
                assertEquals("PUR-V2", purchases.first().invoiceNumber)

                val expenses = dao.getAllExpenses().first()
                assertEquals("v$startVersion expenses count", 1, expenses.size)
                assertEquals("EXP-V2", expenses.first().receiptNumber)

                val employees = dao.getAllEmployees().first()
                assertEquals("v$startVersion employees count", 1, employees.size)
                assertEquals("سعيد الحداد", employees.first().name)

                val empTxs = dao.getAllEmployeeTransactions().first()
                assertEquals("v$startVersion employee_transactions count", 1, empTxs.size)
                assertEquals(200.0, empTxs.first().amount, 0.01)

                val debts = dao.getAllDebts().first()
                assertEquals("v$startVersion debts count", 1, debts.size)
                assertEquals(1L, debts.first().referencePurchaseId)

                val payments = dao.getAllDebtPayments().first()
                assertEquals("v$startVersion debt_payments count", 1, payments.size)
                assertEquals(200.0, payments.first().amount, 0.01)
                if (startVersion >= 8) {
                    assertEquals("SND-OLD-8", payments.first().voucherNumber)
                }

                val shifts = dao.getAllShifts().first()
                assertEquals("v$startVersion shifts count", 1, shifts.size)
                assertEquals(1000.0, shifts.first().openingBalance, 0.01)

                val cashMovements = dao.getAllCashMovements().first()
                assertEquals("v$startVersion cash_movements count", 1, cashMovements.size)
                assertEquals(1L, cashMovements.first().referenceSaleId)
                assertNull("Existing cash movements should default referenceReturnId to null", cashMovements.first().referenceReturnId)

                val accounts = dao.getAllExchangeAccounts().first()
                assertEquals("v$startVersion exchange_accounts count", 1, accounts.size)
                assertEquals(5000.0, accounts.first().balance, 0.01)

                val transfers = dao.getAllExchangeTransfers().first()
                assertEquals("v$startVersion exchange_transfers count", 1, transfers.size)
                assertEquals("TR-V2", transfers.first().referenceNumber)

                if (startVersion >= 5) {
                    val parties = dao.getAllParties().first()
                    assertEquals("v$startVersion parties count", 1, parties.size)
                    assertEquals(300.0, parties.first().initialBalance, 0.01)
                }

                if (startVersion >= 6) {
                    val logs = dao.getAllAuditLogs().first()
                    assertEquals("v$startVersion audit_logs count", 1, logs.size)

                    val settings = dao.getStoreSettingsDirect()
                    assertNotNull("v$startVersion store_settings survived", settings)
                    assertEquals("محل التراث للألبسة", settings!!.shopNameAr)
                    assertEquals("", settings.managerPin)
                    if (legacyPin == "1234") {
                        assertTrue("Weak 1234 legacy PIN must require change", settings.mustChangePin)
                        assertEquals("", settings.managerPinHash)
                    } else {
                        assertFalse("Strong legacy PIN hashed without forced change", settings.mustChangePin)
                        assertTrue(PinSecurityManager.verifyHash("8492", settings.managerPinSalt, settings.managerPinHash))
                    }
                }

                if (startVersion >= 9) {
                    val configItems = dao.getAllConfigItems().first()
                    assertEquals("v$startVersion store_config_items count", 1, configItems.size)
                    assertEquals("أكوات ملكية", configItems.first().name)
                }

                // Verify v11 return_records table and cash_movements.referenceReturnId column are operational
                assertEquals(0, dao.getAllReturnsDirect().size)
                val insertedRetId = dao.insertReturn(
                    ReturnRecord(
                        returnType = ReturnTypes.SALE_RETURN,
                        returnNumber = "RS-MIG-$startVersion",
                        originalInvoiceNumber = "INV-LEGACY-7",
                        originalLineId = 1L,
                        productId = 1L,
                        productName = "كوت شتوي أصلي",
                        quantity = 1,
                        unitBuyPrice = 120.0,
                        unitSellPrice = 200.0,
                        totalAmount = 200.0,
                        profitAdjustment = 80.0,
                        partyName = "عميل قديم",
                        settlementMethod = ReturnSettlementMethods.CASH,
                        cashAmount = 200.0,
                        timestamp = 1700000050000L,
                        shiftId = 1L
                    )
                )
                assertTrue(insertedRetId > 0L)
                dao.insertCashMovement(
                    CashDrawerMovement(
                        shiftId = 1L,
                        type = "SALE_RETURN_CASH",
                        amount = 200.0,
                        isIncome = false,
                        title = "مرتجع اختبار هجرة",
                        details = "RS-MIG-$startVersion",
                        timestamp = 1700000050000L,
                        referenceReturnId = insertedRetId
                    )
                )
                val loadedReturns = dao.getAllReturnsDirect()
                assertEquals(1, loadedReturns.size)
                assertEquals("RS-MIG-$startVersion", loadedReturns.first().returnNumber)
                val linkedCash = dao.getAllCashMovementsDirect().find { it.referenceReturnId == insertedRetId }
                assertNotNull(linkedCash)
                assertEquals(200.0, linkedCash!!.amount, 0.01)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun testViewModelStartupDoesNotWipeMigratedDatabase() = runBlocking {
        // Seed existing database under real app DB name "hesabi_store.db" at version 7
        context.deleteDatabase("hesabi_store.db")
        context.getSharedPreferences("hesabi_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        createVersionedSeededDb("hesabi_store.db", targetVersion = 7, legacyPin = "7539")
        createdDbNames.add("hesabi_store.db")

        // Reset singleton via reflection if needed so this test opens fresh hesabi_store.db
        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        (instanceField.get(null) as? AppDatabase)?.close()
        instanceField.set(null, null)

        val vm = StoreViewModel(context.applicationContext as Application)
        val start = System.currentTimeMillis()
        while (!vm.isInitialized.value && System.currentTimeMillis() - start < 5000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("StoreViewModel should initialize", vm.isInitialized.value)

        val dao = AppDatabase.getInstance(context).storeDao()
        assertEquals("Products MUST NOT be wiped on startup", 1, dao.getAllProducts().first().size)
        assertEquals("Sales MUST NOT be wiped on startup", 1, dao.getAllSales().first().size)
        assertEquals("Purchases MUST NOT be wiped on startup", 1, dao.getAllPurchases().first().size)
        assertEquals("Expenses MUST NOT be wiped on startup", 1, dao.getAllExpenses().first().size)
        assertEquals("Debts MUST NOT be wiped on startup", 1, dao.getAllDebts().first().size)
        assertEquals("Accounts MUST NOT be wiped on startup", 1, dao.getAllExchangeAccounts().first().size)
        assertEquals("محل التراث للألبسة", dao.getStoreSettingsDirect()?.shopNameAr)

        // Cleanup singleton
        AppDatabase.getInstance(context).close()
        instanceField.set(null, null)
    }
}
