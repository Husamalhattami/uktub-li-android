package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.StoreDao
import com.example.data.model.*

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
        PartyAccount::class
    ],
    version = 5,
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
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
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
    }
}
