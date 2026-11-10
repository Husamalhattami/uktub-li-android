package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // جاكت، كوت، دجلة، بشت، ثوب، قميص، بنطلون، بدلة...
    val modelName: String,
    val color: String, // اسم اللون أو تشكيلة ألوان الدرزن
    val size: String, // كبير، وسط، صغير، شبابي
    val quantity: Int, // إجمالي الحبات المتوفرة
    val buyPrice: Double, // سعر الشراء للحبة
    val sellPrice: Double, // سعر البيع للحبة
    val supplierName: String = "",
    val purchaseDate: Long = System.currentTimeMillis(),
    val invoiceNumber: String = "",
    val barcode: String = "",
    val notes: String = "",
    val lastSoldTimestamp: Long = 0,
    val unit: String = "حبه", // حبه، نص درزن، درزن، كورجه
    val colorsCount: Int = 1, // عدد الألوان (1، 2، 3...)
    val colorDetails: String = "" // تفصيل الألوان (مثال: 3 ألوان: أسود، كحلي، رصاصي)
)

@Entity(tableName = "sales")
data class SaleTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val category: String,
    val color: String,
    val size: String, // كبير، وسط، صغير، شبابي
    val quantity: Int, // عدد الحبات الفعلي المباع
    val unitBuyPrice: Double,
    val unitSellPrice: Double,
    val discount: Double = 0.0,
    val totalAmount: Double,
    val profit: Double,
    val customerName: String = "",
    val customerPhone: String = "",
    val paymentType: String = "نقداً", // "نقداً", "تحويل صرافة", "نصف كاش ونصف حوالة", "بالدين"
    val cashAmount: Double = 0.0,
    val transferAmount: Double = 0.0,
    val debtAmount: Double = 0.0,
    val exchangeAccountId: Long = 0,
    val exchangeAccountName: String = "",
    val transferRef: String = "",
    val employeeName: String = "",
    val employeeId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val shiftId: Long = 0,
    val unit: String = "حبه", // حبه، نص درزن، درزن، كورجه
    val invoiceNumber: String = ""
)

data class CartSaleItem(
    val product: ProductItem,
    val quantity: Int,
    val sellPrice: Double,
    val unit: String = "حبه"
)

data class CartPurchaseItem(
    val category: String,
    val modelName: String,
    val color: String,
    val size: String,
    val unit: String = "درزن", // حبه، نص درزن، درزن، كورجه
    val unitCount: Double = 1.0, // عدد الوحدات (مثلاً 2 درزن)
    val buyPricePerUnit: Double, // سعر الشراء للوحدة
    val sellPricePerUnit: Double, // سعر البيع للوحدة
    val barcode: String = "",
    val colorsCount: Int = 1,
    val colorDetails: String = "",
    val notes: String = ""
) {
    val multiplier: Int get() = when (unit) {
        "درزن" -> 12
        "نص درزن" -> 6
        "كورجه" -> 20
        else -> 1
    }
    val totalPieces: Int get() = (unitCount * multiplier).toInt().coerceAtLeast(1)
    val lineCost: Double get() = buyPricePerUnit * unitCount
    val buyPricePerPiece: Double get() = if (totalPieces > 0) lineCost / totalPieces else 0.0
    val sellPricePerPiece: Double get() = if (totalPieces > 0) (sellPricePerUnit * unitCount) / totalPieces else 0.0
}

@Entity(tableName = "purchases")
data class PurchaseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierName: String,
    val category: String,
    val modelName: String,
    val color: String,
    val size: String,
    val quantity: Int, // إجمالي عدد الحبات
    val unitBuyPrice: Double,
    val unitSellPrice: Double,
    val totalCost: Double,
    val paidAmount: Double,
    val remainingDebt: Double,
    val invoiceNumber: String = "",
    val paymentMethod: String = "نقداً من الصندوق", // "نقداً من الصندوق", "تحويل صرافة", "نصف كاش ونصف حوالة", "آجل للمورد"
    val cashPaid: Double = 0.0,
    val transferPaid: Double = 0.0,
    val exchangeAccountId: Long = 0,
    val exchangeAccountName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val unit: String = "حبه",
    val unitCount: Double = 1.0,
    val colorsCount: Int = 1,
    val notes: String = ""
)

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // إيجار، كهرباء، ماء، إنترنت، نقل، تغليف، تصليح، ضيافة، إعلان، متفرقات
    val amount: Double,
    val notes: String = "",
    val receiptNumber: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val paidFromCash: Boolean = true,
    val exchangeAccountId: Long = 0,
    val exchangeAccountName: String = "",
    val shiftId: Long = 0
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val role: String = "بائع", // بائع، مسؤول صالة، خياط، كاشير
    val startDate: Long = System.currentTimeMillis(),
    val baseSalary: Double,
    val salaryType: String = "شهري", // شهري أو يومي
    val commissionPercent: Double = 0.0, // نسبة عمولة من المبيعات مثلا 2.0%
    val active: Boolean = true
)

@Entity(tableName = "employee_transactions")
data class EmployeeTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val employeeName: String,
    val type: String, // "سلفة"، "خصم غياب"، "صرف راتب"، "مكافأة"
    val amount: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val shiftId: Long = 0,
    val paidFromCash: Boolean = true,
    val exchangeAccountId: Long = 0,
    val exchangeAccountName: String = ""
)

@Entity(tableName = "debts")
data class DebtRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "CUSTOMER" (زبون) or "SUPPLIER" (مورد)
    val partyName: String,
    val phone: String = "",
    val originalAmount: Double,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double,
    val details: String = "",
    val referenceSaleId: Long? = null,
    val referencePurchaseId: Long? = null,
    val dueDate: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false
)

@Entity(tableName = "debt_payments")
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val partyName: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val shiftId: Long = 0,
    val paymentMethod: String = "نقداً", // "نقداً" or "صرافة/محفظة"
    val exchangeAccountId: Long = 0
)

@Entity(tableName = "shifts")
data class CashDrawerShift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val openingBalance: Double = 0.0,
    val closingBalanceActual: Double? = null,
    val isOpen: Boolean = true,
    val cashierName: String = "صاحب المحل",
    val notes: String = ""
)

@Entity(tableName = "cash_movements")
data class CashDrawerMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long = 0,
    val type: String, // SALE_CASH (+), DEBT_COLLECTED (+), PURCHASE_PAID (-), EXPENSE_PAID (-), SALARY_PAID (-), ADVANCE_PAID (-), WITHDRAWAL (-), CAPITAL_DEPOSIT (+)
    val amount: Double, // positive number
    val isIncome: Boolean, // true = دخل للصندوق, false = خرج من الصندوق
    val title: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val referenceSaleId: Long? = null,
    val referencePurchaseId: Long? = null,
    val referenceExpenseId: Long? = null,
    val referenceEmployeeTxId: Long? = null
)

@Entity(tableName = "exchange_accounts")
data class ExchangeAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // اسم الصراف أو المحفظة (مثال: صرافة الكريمي، النجم، العمقي، محفظة جوالي، كاش)
    val accountNumber: String = "",
    val phone: String = "",
    val balance: Double = 0.0,
    val type: String = "صراف", // "صراف", "محفظة", "حساب بنكي"
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exchange_transfers")
data class ExchangeTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val accountName: String,
    val type: String, // "إيداع من الكاش", "سحب للكاش", "مبيعات تحويل", "مشتريات تحويل", "تعديل رصيد"
    val amount: Double,
    val isCredit: Boolean, // true = زيادة رصيد الصراف، false = سحب من الصراف
    val title: String,
    val referenceNumber: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "parties")
data class PartyAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "CUSTOMER" (عميل/زبون) or "SUPPLIER" (مورد/معمل)
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val initialBalance: Double = 0.0, // رصيد افتتاحي / مستحق سابق
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String, // "بيع", "شراء", "مصروف", "سداد دين", "حذف", "جرد وتصفير", "تعديل"
    val actor: String = "المدير",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "store_settings")
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val shopNameAr: String = "الجرادي للملابس الجاهزة",
    val shopNameEn: String = "Al-Jaradi Ready-to-Wear",
    val phone: String = "778383570",
    val address: String = "صنعاء - اليمن",
    val currency: String = "ريال",
    val taxNumber: String = "",
    val invoiceHeader: String = "الجرادي للملابس الجاهزة",
    val invoiceFooter: String = "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية.",
    val showProfitInReports: Boolean = true,
    val managerPin: String = "1234",
    val isManagerRole: Boolean = true
) {
    val storeName: String get() = shopNameAr
    val storeNameEn: String get() = shopNameEn
    val phoneNumber: String get() = phone
    val currencySymbol: String get() = currency
}

object PaymentMethods {
    const val CASH = "نقداً (كاش)"
    const val TRANSFER = "تحويل عبر الصراف"
    const val SPLIT = "نصف كاش ونصف حوالة"
    const val CREDIT = "آجل (دين)"
    const val CUSTOM_SPLIT = "مختلط (كاش + حوالة + آجل)"

    val ALL = listOf(CASH, TRANSFER, SPLIT, CREDIT, CUSTOM_SPLIT)
}

