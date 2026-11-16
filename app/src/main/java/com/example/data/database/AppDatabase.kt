package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.StoreDao
import com.example.data.model.*

import com.example.ui.util.PinSecurityManager

@Database(
    entities = [
        ProductItem::class,
        SaleTransaction::class,
        PurchaseRecord::class,
        ExpenseRecord::class,
        Employee::class,
        EmployeeTransaction::class,
        DebtRecord::class,
        DebtPayment::class,
        CashDrawerShift::class,
        CashDrawerMovement::class,
        ExchangeAccount::class,
        ExchangeTransfer::class,
        PartyAccount::class,
        AuditLogRecord::class,
        StoreSettings::class,
        StoreConfigItem::class,
        ReturnRecord::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hesabi_store.db"
                ).addMigrations(*ALL_MIGRATIONS).build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cash_movements ADD COLUMN referenceReturnId INTEGER")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `return_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `returnType` TEXT NOT NULL,
                        `returnNumber` TEXT NOT NULL,
                        `originalInvoiceNumber` TEXT NOT NULL,
                        `originalLineId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `size` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitBuyPrice` REAL NOT NULL,
                        `unitSellPrice` REAL NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `profitAdjustment` REAL NOT NULL,
                        `partyName` TEXT NOT NULL,
                        `partyPhone` TEXT NOT NULL,
                        `settlementMethod` TEXT NOT NULL,
                        `cashAmount` REAL NOT NULL,
                        `transferAmount` REAL NOT NULL,
                        `debtReducedAmount` REAL NOT NULL,
                        `surplusCreditAmount` REAL NOT NULL,
                        `exchangeAccountId` INTEGER NOT NULL,
                        `exchangeAccountName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `shiftId` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN linkedEmployeeTxId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE employee_transactions ADD COLUMN linkedExpenseId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exchange_accounts ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE parties ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exchange_transfers ADD COLUMN referenceType TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE exchange_transfers ADD COLUMN referenceId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exchange_transfers ADD COLUMN isReversed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exchange_transfers ADD COLUMN reversedByTransferId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE exchange_transfers ADD COLUMN isReversalEntry INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN managerPinHash TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN managerPinSalt TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN mustChangePin INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN pinFailedAttempts INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN pinLockUntilTimestamp INTEGER NOT NULL DEFAULT 0")

                database.query("SELECT id, managerPin FROM store_settings").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(0)
                        val legacyPin = cursor.getString(1)?.trim() ?: ""
                        if (legacyPin.isNotBlank() && PinSecurityManager.validatePinStrength(legacyPin) == null) {
                            val salt = PinSecurityManager.generateSalt()
                            val hash = PinSecurityManager.hashPin(legacyPin, salt)
                            database.execSQL(
                                "UPDATE store_settings SET managerPin = '', managerPinHash = ?, managerPinSalt = ?, mustChangePin = 0 WHERE id = ?",
                                arrayOf<Any>(hash, salt, id)
                            )
                        } else if (legacyPin.isNotBlank()) {
                            database.execSQL(
                                "UPDATE store_settings SET managerPin = '', managerPinHash = '', managerPinSalt = '', mustChangePin = 1 WHERE id = ?",
                                arrayOf<Any>(id)
                            )
                        }
                    }
                }
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE store_settings ADD COLUMN logoPath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN isSetupCompleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `store_config_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE debt_payments ADD COLUMN voucherNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sales ADD COLUMN debtAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE sales ADD COLUMN invoiceNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `action` TEXT NOT NULL,
                        `actor` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
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
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE employee_transactions ADD COLUMN paidFromCash INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE employee_transactions ADD COLUMN exchangeAccountId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE employee_transactions ADD COLUMN exchangeAccountName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cash_movements ADD COLUMN referenceEmployeeTxId INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
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
            }
        }

        val ALL_MIGRATIONS: Array<androidx.room.migration.Migration> by lazy {
            arrayOf(
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            )
        }
    }
}
