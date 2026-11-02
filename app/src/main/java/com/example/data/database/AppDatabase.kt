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
        ExchangeTransfer::class
    ],
    version = 3,
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
                ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build()
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
    }
}
