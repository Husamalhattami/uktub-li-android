package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.*
import com.example.data.repository.StoreBackupSnapshot
import com.example.data.repository.StoreRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class BackupPreview(
    val app: String,
    val version: Int,
    val timestamp: Long,
    val productsCount: Int,
    val salesCount: Int,
    val purchasesCount: Int,
    val expensesCount: Int,
    val employeesCount: Int,
    val employeeTransactionsCount: Int,
    val partiesCount: Int,
    val debtsCount: Int,
    val debtPaymentsCount: Int,
    val shiftsCount: Int,
    val cashMovementsCount: Int,
    val exchangeAccountsCount: Int,
    val exchangeTransfersCount: Int,
    val auditLogsCount: Int,
    val hasStoreSettings: Boolean,
    val configItemsCount: Int
) {
    val totalRecords: Int
        get() = productsCount + salesCount + purchasesCount + expensesCount +
            employeesCount + employeeTransactionsCount + partiesCount + debtsCount +
            debtPaymentsCount + shiftsCount + cashMovementsCount + exchangeAccountsCount +
            exchangeTransfersCount + auditLogsCount + (if (hasStoreSettings) 1 else 0) + configItemsCount
}

data class ParsedBackupData(
    val preview: BackupPreview,
    val snapshot: StoreBackupSnapshot
)

object BackupManager {

    const val CURRENT_BACKUP_VERSION = 10

    suspend fun createBackupJsonString(repository: StoreRepository): String {
        val snap = repository.exportFullBackupSnapshot()
        val root = JSONObject()
        root.put("app", "HesabiStore")
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        root.put("products", JSONArray().apply {
            snap.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("category", p.category)
                    put("modelName", p.modelName)
                    put("color", p.color)
                    put("size", p.size)
                    put("quantity", p.quantity)
                    put("buyPrice", p.buyPrice)
                    put("sellPrice", p.sellPrice)
                    put("supplierName", p.supplierName)
                    put("purchaseDate", p.purchaseDate)
                    put("invoiceNumber", p.invoiceNumber)
                    put("barcode", p.barcode)
                    put("notes", p.notes)
                    put("lastSoldTimestamp", p.lastSoldTimestamp)
                    put("unit", p.unit)
                    put("colorsCount", p.colorsCount)
                    put("colorDetails", p.colorDetails)
                })
            }
        })

        root.put("sales", JSONArray().apply {
            snap.sales.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("productId", s.productId)
                    put("productName", s.productName)
                    put("category", s.category)
                    put("color", s.color)
                    put("size", s.size)
                    put("quantity", s.quantity)
                    put("unitBuyPrice", s.unitBuyPrice)
                    put("unitSellPrice", s.unitSellPrice)
                    put("discount", s.discount)
                    put("totalAmount", s.totalAmount)
                    put("profit", s.profit)
                    put("customerName", s.customerName)
                    put("customerPhone", s.customerPhone)
                    put("paymentType", s.paymentType)
                    put("cashAmount", s.cashAmount)
                    put("transferAmount", s.transferAmount)
                    put("debtAmount", s.debtAmount)
                    put("exchangeAccountId", s.exchangeAccountId)
                    put("exchangeAccountName", s.exchangeAccountName)
                    put("transferRef", s.transferRef)
                    put("employeeName", s.employeeName)
                    put("employeeId", s.employeeId)
                    put("timestamp", s.timestamp)
                    put("shiftId", s.shiftId)
                    put("unit", s.unit)
                    put("invoiceNumber", s.invoiceNumber)
                })
            }
        })

        root.put("purchases", JSONArray().apply {
            snap.purchases.forEach { pur ->
                put(JSONObject().apply {
                    put("id", pur.id)
                    put("supplierName", pur.supplierName)
                    put("category", pur.category)
                    put("modelName", pur.modelName)
                    put("color", pur.color)
                    put("size", pur.size)
                    put("quantity", pur.quantity)
                    put("unitBuyPrice", pur.unitBuyPrice)
                    put("unitSellPrice", pur.unitSellPrice)
                    put("totalCost", pur.totalCost)
                    put("paidAmount", pur.paidAmount)
                    put("remainingDebt", pur.remainingDebt)
                    put("invoiceNumber", pur.invoiceNumber)
                    put("paymentMethod", pur.paymentMethod)
                    put("cashPaid", pur.cashPaid)
                    put("transferPaid", pur.transferPaid)
                    put("exchangeAccountId", pur.exchangeAccountId)
                    put("exchangeAccountName", pur.exchangeAccountName)
                    put("timestamp", pur.timestamp)
                    put("unit", pur.unit)
                    put("unitCount", pur.unitCount)
                    put("colorsCount", pur.colorsCount)
                    put("notes", pur.notes)
                })
            }
        })

        root.put("expenses", JSONArray().apply {
            snap.expenses.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("category", e.category)
                    put("amount", e.amount)
                    put("notes", e.notes)
                    put("receiptNumber", e.receiptNumber)
                    put("timestamp", e.timestamp)
                    put("paidFromCash", e.paidFromCash)
                    put("exchangeAccountId", e.exchangeAccountId)
                    put("exchangeAccountName", e.exchangeAccountName)
                    put("shiftId", e.shiftId)
                    put("linkedEmployeeTxId", e.linkedEmployeeTxId)
                })
            }
        })

        root.put("employees", JSONArray().apply {
            snap.employees.forEach { emp ->
                put(JSONObject().apply {
                    put("id", emp.id)
                    put("name", emp.name)
                    put("phone", emp.phone)
                    put("role", emp.role)
                    put("startDate", emp.startDate)
                    put("baseSalary", emp.baseSalary)
                    put("salaryType", emp.salaryType)
                    put("commissionPercent", emp.commissionPercent)
                    put("active", emp.active)
                })
            }
        })

        root.put("employeeTransactions", JSONArray().apply {
            snap.employeeTransactions.forEach { tx ->
                put(JSONObject().apply {
                    put("id", tx.id)
                    put("employeeId", tx.employeeId)
                    put("employeeName", tx.employeeName)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("notes", tx.notes)
                    put("timestamp", tx.timestamp)
                    put("shiftId", tx.shiftId)
                    put("paidFromCash", tx.paidFromCash)
                    put("exchangeAccountId", tx.exchangeAccountId)
                    put("exchangeAccountName", tx.exchangeAccountName)
                    put("linkedExpenseId", tx.linkedExpenseId)
                })
            }
        })

        root.put("parties", JSONArray().apply {
            snap.parties.forEach { party ->
                put(JSONObject().apply {
                    put("id", party.id)
                    put("type", party.type)
                    put("name", party.name)
                    put("phone", party.phone)
                    put("address", party.address)
                    put("notes", party.notes)
                    put("initialBalance", party.initialBalance)
                    put("createdAt", party.createdAt)
                    put("isArchived", party.isArchived)
                })
            }
        })

        root.put("debts", JSONArray().apply {
            snap.debts.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("type", d.type)
                    put("partyName", d.partyName)
                    put("phone", d.phone)
                    put("originalAmount", d.originalAmount)
                    put("paidAmount", d.paidAmount)
                    put("remainingAmount", d.remainingAmount)
                    put("details", d.details)
                    put("referenceSaleId", d.referenceSaleId ?: JSONObject.NULL)
                    put("referencePurchaseId", d.referencePurchaseId ?: JSONObject.NULL)
                    put("dueDate", d.dueDate)
                    put("createdAt", d.createdAt)
                    put("isSettled", d.isSettled)
                })
            }
        })

        root.put("debtPayments", JSONArray().apply {
            snap.debtPayments.forEach { dp ->
                put(JSONObject().apply {
                    put("id", dp.id)
                    put("debtId", dp.debtId)
                    put("partyName", dp.partyName)
                    put("amount", dp.amount)
                    put("timestamp", dp.timestamp)
                    put("notes", dp.notes)
                    put("shiftId", dp.shiftId)
                    put("paymentMethod", dp.paymentMethod)
                    put("exchangeAccountId", dp.exchangeAccountId)
                    put("voucherNumber", dp.voucherNumber)
                })
            }
        })

        root.put("shifts", JSONArray().apply {
            snap.shifts.forEach { sh ->
                put(JSONObject().apply {
                    put("id", sh.id)
                    put("openedAt", sh.openedAt)
                    put("closedAt", sh.closedAt ?: JSONObject.NULL)
                    put("openingBalance", sh.openingBalance)
                    put("closingBalanceActual", sh.closingBalanceActual ?: JSONObject.NULL)
                    put("isOpen", sh.isOpen)
                    put("cashierName", sh.cashierName)
                    put("notes", sh.notes)
                })
            }
        })

        root.put("cashMovements", JSONArray().apply {
            snap.cashMovements.forEach { cm ->
                put(JSONObject().apply {
                    put("id", cm.id)
                    put("shiftId", cm.shiftId)
                    put("type", cm.type)
                    put("amount", cm.amount)
                    put("isIncome", cm.isIncome)
                    put("title", cm.title)
                    put("details", cm.details)
                    put("timestamp", cm.timestamp)
                    put("referenceSaleId", cm.referenceSaleId ?: JSONObject.NULL)
                    put("referencePurchaseId", cm.referencePurchaseId ?: JSONObject.NULL)
                    put("referenceExpenseId", cm.referenceExpenseId ?: JSONObject.NULL)
                    put("referenceEmployeeTxId", cm.referenceEmployeeTxId ?: JSONObject.NULL)
                })
            }
        })

        root.put("exchangeAccounts", JSONArray().apply {
            snap.exchangeAccounts.forEach { acc ->
                put(JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("accountNumber", acc.accountNumber)
                    put("phone", acc.phone)
                    put("balance", acc.balance)
                    put("type", acc.type)
                    put("notes", acc.notes)
                    put("createdAt", acc.createdAt)
                    put("isArchived", acc.isArchived)
                })
            }
        })

        root.put("exchangeTransfers", JSONArray().apply {
            snap.exchangeTransfers.forEach { tr ->
                put(JSONObject().apply {
                    put("id", tr.id)
                    put("accountId", tr.accountId)
                    put("accountName", tr.accountName)
                    put("type", tr.type)
                    put("amount", tr.amount)
                    put("isCredit", tr.isCredit)
                    put("title", tr.title)
                    put("referenceNumber", tr.referenceNumber)
                    put("notes", tr.notes)
                    put("timestamp", tr.timestamp)
                    put("referenceType", tr.referenceType)
                    put("referenceId", tr.referenceId)
                    put("isReversed", tr.isReversed)
                    put("reversedByTransferId", tr.reversedByTransferId)
                    put("isReversalEntry", tr.isReversalEntry)
                })
            }
        })

        root.put("auditLogs", JSONArray().apply {
            snap.auditLogs.forEach { log ->
                put(JSONObject().apply {
                    put("id", log.id)
                    put("action", log.action)
                    put("actor", log.actor)
                    put("details", log.details)
                    put("timestamp", log.timestamp)
                })
            }
        })

        snap.storeSettings?.let { st ->
            root.put("storeSettings", JSONObject().apply {
                put("id", st.id)
                put("shopNameAr", st.shopNameAr)
                put("shopNameEn", st.shopNameEn)
                put("phone", st.phone)
                put("address", st.address)
                put("currency", st.currency)
                put("taxNumber", st.taxNumber)
                put("invoiceHeader", st.invoiceHeader)
                put("invoiceFooter", st.invoiceFooter)
                put("showProfitInReports", st.showProfitInReports)
                put("isManagerRole", st.isManagerRole)
                put("logoPath", st.logoPath)
                put("isSetupCompleted", st.isSetupCompleted)
                put("managerPinHash", st.managerPinHash)
                put("managerPinSalt", st.managerPinSalt)
                put("mustChangePin", st.mustChangePin)
            })
        } ?: root.put("storeSettings", JSONObject.NULL)

        root.put("storeConfigItems", JSONArray().apply {
            snap.configItems.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type)
                    put("name", item.name)
                    put("createdAt", item.createdAt)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun createBackupFile(context: Context, repository: StoreRepository): File {
        val json = createBackupJsonString(repository)
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val file = File(backupDir, "نسخة_احتياطية_${FormatUtils.formatDate(System.currentTimeMillis()).replace('/', '-')}.json")
        FileOutputStream(file).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun shareBackupFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - تطبيق حسابي للمحل")
                putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية كاملة لبيانات محل الملابس وتطبيق حسابي للمحل بتاريخ ${FormatUtils.formatDate(System.currentTimeMillis())}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة أو حفظ النسخة الاحتياطية"))
        } catch (e: Exception) {
            Toast.makeText(context, "فشل تصدير النسخة الاحتياطية: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requireFiniteDouble(obj: JSONObject, key: String, default: Double? = null, allowNegative: Boolean = false): Double {
        val value = if (obj.has(key) && !obj.isNull(key)) {
            obj.getDouble(key)
        } else {
            default ?: throw IllegalArgumentException("حقل مفقود: $key")
        }
        if (value.isNaN() || value.isInfinite()) {
            throw IllegalArgumentException("قيمة رقمية غير صالحة في الحقل: $key")
        }
        if (!allowNegative && value < -0.0001) {
            throw IllegalArgumentException("قيمة سالبة غير مسموحة في الحقل: $key")
        }
        return value
    }

    private fun optNullableLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.getLong(key)
        return if (v > 0L) v else null
    }

    private fun optNullableDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.getDouble(key)
        if (v.isNaN() || v.isInfinite()) throw IllegalArgumentException("قيمة رقمية غير صالحة في الحقل: $key")
        return v
    }

    fun validateAndParseBackup(jsonString: String): Result<ParsedBackupData> {
        return runCatching {
            val trimmed = jsonString.trim()
            if (trimmed.isBlank()) throw IllegalArgumentException("ملف النسخة الاحتياطية فارغ!")
            val root = JSONObject(trimmed)
            if (root.optString("app") != "HesabiStore") {
                throw IllegalArgumentException("ملف النسخة الاحتياطية لا يتبع تطبيق حسابي للمحل.")
            }
            val version = root.optInt("version", -1)
            if (version < 6) {
                throw IllegalArgumentException("إصدار النسخة الاحتياطية غير مدعوم ($version).")
            }

            val requiredCoreArrays = listOf(
                "products", "sales", "purchases", "expenses",
                "exchangeAccounts", "parties", "debts"
            )
            val v10ExtraArrays = listOf(
                "employees", "employeeTransactions", "debtPayments",
                "shifts", "cashMovements", "exchangeTransfers",
                "auditLogs", "storeConfigItems"
            )

            requiredCoreArrays.forEach { key ->
                if (!root.has(key) || root.isNull(key) || root.optJSONArray(key) == null) {
                    throw IllegalArgumentException("ملف النسخة الاحتياطية ناقص: القسم ($key) غير موجود أو تالف.")
                }
            }
            if (version >= 10) {
                v10ExtraArrays.forEach { key ->
                    if (!root.has(key) || root.isNull(key) || root.optJSONArray(key) == null) {
                        throw IllegalArgumentException("ملف النسخة الاحتياطية ناقص: القسم ($key) غير موجود أو تالف.")
                    }
                }
                if (!root.has("storeSettings")) {
                    throw IllegalArgumentException("ملف النسخة الاحتياطية ناقص: قسم إعدادات المحل غير موجود.")
                }
            }

            val products = mutableListOf<ProductItem>()
            val prodsArr = root.getJSONArray("products")
            for (i in 0 until prodsArr.length()) {
                val obj = prodsArr.getJSONObject(i)
                val qty = obj.getInt("quantity")
                if (qty < 0) throw IllegalArgumentException("كمية منتج سالبة في السجل #${i + 1}")
                products.add(
                    ProductItem(
                        id = obj.optLong("id", 0L),
                        category = obj.optString("category", "أخرى"),
                        modelName = obj.getString("modelName"),
                        color = obj.optString("color", "عام"),
                        size = obj.optString("size", "حر"),
                        quantity = qty,
                        buyPrice = requireFiniteDouble(obj, "buyPrice", 0.0),
                        sellPrice = requireFiniteDouble(obj, "sellPrice", 0.0),
                        supplierName = obj.optString("supplierName", ""),
                        purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                        invoiceNumber = obj.optString("invoiceNumber", ""),
                        barcode = obj.optString("barcode", ""),
                        notes = obj.optString("notes", ""),
                        lastSoldTimestamp = obj.optLong("lastSoldTimestamp", 0L),
                        unit = obj.optString("unit", ClothingUnits.PIECE),
                        colorsCount = obj.optInt("colorsCount", 1).coerceAtLeast(1),
                        colorDetails = obj.optString("colorDetails", "")
                    )
                )
            }

            val exchangeAccounts = mutableListOf<ExchangeAccount>()
            val accsArr = root.getJSONArray("exchangeAccounts")
            for (i in 0 until accsArr.length()) {
                val obj = accsArr.getJSONObject(i)
                exchangeAccounts.add(
                    ExchangeAccount(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        accountNumber = obj.optString("accountNumber", ""),
                        phone = obj.optString("phone", ""),
                        balance = requireFiniteDouble(obj, "balance", 0.0),
                        type = obj.optString("type", "صراف"),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isArchived = obj.optBoolean("isArchived", false)
                    )
                )
            }

            val parties = mutableListOf<PartyAccount>()
            val partiesArr = root.getJSONArray("parties")
            for (i in 0 until partiesArr.length()) {
                val obj = partiesArr.getJSONObject(i)
                parties.add(
                    PartyAccount(
                        id = obj.optLong("id", 0L),
                        type = obj.getString("type"),
                        name = obj.getString("name"),
                        phone = obj.optString("phone", ""),
                        address = obj.optString("address", ""),
                        notes = obj.optString("notes", ""),
                        initialBalance = requireFiniteDouble(obj, "initialBalance", 0.0, allowNegative = true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isArchived = obj.optBoolean("isArchived", false)
                    )
                )
            }

            val sales = mutableListOf<SaleTransaction>()
            val salesArr = root.getJSONArray("sales")
            for (i in 0 until salesArr.length()) {
                val obj = salesArr.getJSONObject(i)
                val qty = obj.getInt("quantity")
                if (qty <= 0) throw IllegalArgumentException("كمية بيع غير صالحة في السجل #${i + 1}")
                sales.add(
                    SaleTransaction(
                        id = obj.optLong("id", 0L),
                        productId = obj.optLong("productId", 0L),
                        productName = obj.optString("productName", ""),
                        category = obj.optString("category", ""),
                        color = obj.optString("color", ""),
                        size = obj.optString("size", ""),
                        quantity = qty,
                        unitBuyPrice = requireFiniteDouble(obj, "unitBuyPrice", 0.0),
                        unitSellPrice = requireFiniteDouble(obj, "unitSellPrice", 0.0),
                        discount = requireFiniteDouble(obj, "discount", 0.0),
                        totalAmount = requireFiniteDouble(obj, "totalAmount", 0.0),
                        profit = requireFiniteDouble(obj, "profit", 0.0, allowNegative = true),
                        customerName = obj.optString("customerName", "زبون نقدي"),
                        customerPhone = obj.optString("customerPhone", ""),
                        paymentType = obj.optString("paymentType", "نقداً"),
                        cashAmount = requireFiniteDouble(obj, "cashAmount", 0.0),
                        transferAmount = requireFiniteDouble(obj, "transferAmount", 0.0),
                        debtAmount = requireFiniteDouble(obj, "debtAmount", 0.0),
                        exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                        exchangeAccountName = obj.optString("exchangeAccountName", ""),
                        transferRef = obj.optString("transferRef", ""),
                        employeeName = obj.optString("employeeName", "صاحب المحل"),
                        employeeId = obj.optLong("employeeId", 0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        shiftId = obj.optLong("shiftId", 0L),
                        unit = obj.optString("unit", ClothingUnits.PIECE),
                        invoiceNumber = obj.optString("invoiceNumber", "")
                    )
                )
            }

            val purchases = mutableListOf<PurchaseRecord>()
            val purchasesArr = root.getJSONArray("purchases")
            for (i in 0 until purchasesArr.length()) {
                val obj = purchasesArr.getJSONObject(i)
                val qty = obj.getInt("quantity")
                if (qty <= 0) throw IllegalArgumentException("كمية شراء غير صالحة في السجل #${i + 1}")
                purchases.add(
                    PurchaseRecord(
                        id = obj.optLong("id", 0L),
                        supplierName = obj.optString("supplierName", "مورد"),
                        category = obj.optString("category", ""),
                        modelName = obj.optString("modelName", ""),
                        color = obj.optString("color", ""),
                        size = obj.optString("size", ""),
                        quantity = qty,
                        unitBuyPrice = requireFiniteDouble(obj, "unitBuyPrice", 0.0),
                        unitSellPrice = requireFiniteDouble(obj, "unitSellPrice", 0.0),
                        totalCost = requireFiniteDouble(obj, "totalCost", 0.0),
                        paidAmount = requireFiniteDouble(obj, "paidAmount", 0.0),
                        remainingDebt = requireFiniteDouble(obj, "remainingDebt", 0.0),
                        invoiceNumber = obj.optString("invoiceNumber", ""),
                        paymentMethod = obj.optString("paymentMethod", "نقداً من الصندوق"),
                        cashPaid = requireFiniteDouble(obj, "cashPaid", 0.0),
                        transferPaid = requireFiniteDouble(obj, "transferPaid", 0.0),
                        exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                        exchangeAccountName = obj.optString("exchangeAccountName", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        unit = obj.optString("unit", ClothingUnits.PIECE),
                        unitCount = requireFiniteDouble(obj, "unitCount", 1.0),
                        colorsCount = obj.optInt("colorsCount", 1).coerceAtLeast(1),
                        notes = obj.optString("notes", "")
                    )
                )
            }

            val expenses = mutableListOf<ExpenseRecord>()
            val expensesArr = root.getJSONArray("expenses")
            for (i in 0 until expensesArr.length()) {
                val obj = expensesArr.getJSONObject(i)
                expenses.add(
                    ExpenseRecord(
                        id = obj.optLong("id", 0L),
                        category = obj.optString("category", "مصروف"),
                        amount = requireFiniteDouble(obj, "amount", 0.0),
                        notes = obj.optString("notes", ""),
                        receiptNumber = obj.optString("receiptNumber", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        paidFromCash = obj.optBoolean("paidFromCash", true),
                        exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                        exchangeAccountName = obj.optString("exchangeAccountName", ""),
                        shiftId = obj.optLong("shiftId", 0L),
                        linkedEmployeeTxId = obj.optLong("linkedEmployeeTxId", 0L)
                    )
                )
            }

            val employees = mutableListOf<Employee>()
            root.optJSONArray("employees")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    employees.add(
                        Employee(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            phone = obj.optString("phone", ""),
                            role = obj.optString("role", "بائع"),
                            startDate = obj.optLong("startDate", System.currentTimeMillis()),
                            baseSalary = requireFiniteDouble(obj, "baseSalary", 0.0),
                            salaryType = obj.optString("salaryType", "شهري"),
                            commissionPercent = requireFiniteDouble(obj, "commissionPercent", 0.0),
                            active = obj.optBoolean("active", true)
                        )
                    )
                }
            }

            val employeeTransactions = mutableListOf<EmployeeTransaction>()
            root.optJSONArray("employeeTransactions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    employeeTransactions.add(
                        EmployeeTransaction(
                            id = obj.optLong("id", 0L),
                            employeeId = obj.getLong("employeeId"),
                            employeeName = obj.optString("employeeName", ""),
                            type = obj.getString("type"),
                            amount = requireFiniteDouble(obj, "amount", 0.0),
                            notes = obj.optString("notes", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            shiftId = obj.optLong("shiftId", 0L),
                            paidFromCash = obj.optBoolean("paidFromCash", true),
                            exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                            exchangeAccountName = obj.optString("exchangeAccountName", ""),
                            linkedExpenseId = obj.optLong("linkedExpenseId", 0L)
                        )
                    )
                }
            }

            val debts = mutableListOf<DebtRecord>()
            val debtsArr = root.getJSONArray("debts")
            for (i in 0 until debtsArr.length()) {
                val obj = debtsArr.getJSONObject(i)
                debts.add(
                    DebtRecord(
                        id = obj.optLong("id", 0L),
                        type = obj.getString("type"),
                        partyName = obj.optString("partyName", ""),
                        phone = obj.optString("phone", ""),
                        originalAmount = requireFiniteDouble(obj, "originalAmount", 0.0),
                        paidAmount = requireFiniteDouble(obj, "paidAmount", 0.0),
                        remainingAmount = requireFiniteDouble(obj, "remainingAmount", 0.0),
                        details = obj.optString("details", ""),
                        referenceSaleId = optNullableLong(obj, "referenceSaleId"),
                        referencePurchaseId = optNullableLong(obj, "referencePurchaseId"),
                        dueDate = obj.optLong("dueDate", 0L),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isSettled = obj.optBoolean("isSettled", false)
                    )
                )
            }

            val debtPayments = mutableListOf<DebtPayment>()
            root.optJSONArray("debtPayments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    debtPayments.add(
                        DebtPayment(
                            id = obj.optLong("id", 0L),
                            debtId = obj.optLong("debtId", 0L),
                            partyName = obj.optString("partyName", ""),
                            amount = requireFiniteDouble(obj, "amount", 0.0),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            notes = obj.optString("notes", ""),
                            shiftId = obj.optLong("shiftId", 0L),
                            paymentMethod = obj.optString("paymentMethod", "نقداً"),
                            exchangeAccountId = obj.optLong("exchangeAccountId", 0L),
                            voucherNumber = obj.optString("voucherNumber", "")
                        )
                    )
                }
            }

            val shifts = mutableListOf<CashDrawerShift>()
            root.optJSONArray("shifts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    shifts.add(
                        CashDrawerShift(
                            id = obj.optLong("id", 0L),
                            openedAt = obj.optLong("openedAt", System.currentTimeMillis()),
                            closedAt = optNullableLong(obj, "closedAt"),
                            openingBalance = requireFiniteDouble(obj, "openingBalance", 0.0),
                            closingBalanceActual = optNullableDouble(obj, "closingBalanceActual"),
                            isOpen = obj.optBoolean("isOpen", false),
                            cashierName = obj.optString("cashierName", "صاحب المحل"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
            }

            val cashMovements = mutableListOf<CashDrawerMovement>()
            root.optJSONArray("cashMovements")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    cashMovements.add(
                        CashDrawerMovement(
                            id = obj.optLong("id", 0L),
                            shiftId = obj.optLong("shiftId", 0L),
                            type = obj.getString("type"),
                            amount = requireFiniteDouble(obj, "amount", 0.0),
                            isIncome = obj.getBoolean("isIncome"),
                            title = obj.optString("title", ""),
                            details = obj.optString("details", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            referenceSaleId = optNullableLong(obj, "referenceSaleId"),
                            referencePurchaseId = optNullableLong(obj, "referencePurchaseId"),
                            referenceExpenseId = optNullableLong(obj, "referenceExpenseId"),
                            referenceEmployeeTxId = optNullableLong(obj, "referenceEmployeeTxId")
                        )
                    )
                }
            }

            val exchangeTransfers = mutableListOf<ExchangeTransfer>()
            root.optJSONArray("exchangeTransfers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    exchangeTransfers.add(
                        ExchangeTransfer(
                            id = obj.optLong("id", 0L),
                            accountId = obj.getLong("accountId"),
                            accountName = obj.optString("accountName", ""),
                            type = obj.getString("type"),
                            amount = requireFiniteDouble(obj, "amount", 0.0),
                            isCredit = obj.getBoolean("isCredit"),
                            title = obj.optString("title", ""),
                            referenceNumber = obj.optString("referenceNumber", ""),
                            notes = obj.optString("notes", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            referenceType = obj.optString("referenceType", ""),
                            referenceId = obj.optLong("referenceId", 0L),
                            isReversed = obj.optBoolean("isReversed", false),
                            reversedByTransferId = obj.optLong("reversedByTransferId", 0L),
                            isReversalEntry = obj.optBoolean("isReversalEntry", false)
                        )
                    )
                }
            }

            val auditLogs = mutableListOf<AuditLogRecord>()
            root.optJSONArray("auditLogs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    auditLogs.add(
                        AuditLogRecord(
                            id = obj.optLong("id", 0L),
                            action = obj.getString("action"),
                            actor = obj.optString("actor", "المدير"),
                            details = obj.optString("details", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            val storeSettings = if (root.has("storeSettings") && !root.isNull("storeSettings")) {
                val obj = root.getJSONObject("storeSettings")
                StoreSettings(
                    id = obj.optInt("id", 1),
                    shopNameAr = obj.optString("shopNameAr", "الجرادي للملابس الجاهزة"),
                    shopNameEn = obj.optString("shopNameEn", ""),
                    phone = obj.optString("phone", ""),
                    address = obj.optString("address", ""),
                    currency = obj.optString("currency", "ريال"),
                    taxNumber = obj.optString("taxNumber", ""),
                    invoiceHeader = obj.optString("invoiceHeader", ""),
                    invoiceFooter = obj.optString("invoiceFooter", ""),
                    showProfitInReports = obj.optBoolean("showProfitInReports", true),
                    managerPin = "",
                    isManagerRole = obj.optBoolean("isManagerRole", true),
                    logoPath = obj.optString("logoPath", ""),
                    isSetupCompleted = obj.optBoolean("isSetupCompleted", true),
                    managerPinHash = obj.optString("managerPinHash", ""),
                    managerPinSalt = obj.optString("managerPinSalt", ""),
                    mustChangePin = obj.optBoolean("mustChangePin", false)
                )
            } else null

            val configItems = mutableListOf<StoreConfigItem>()
            root.optJSONArray("storeConfigItems")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    configItems.add(
                        StoreConfigItem(
                            id = obj.optLong("id", 0L),
                            type = obj.getString("type"),
                            name = obj.getString("name"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Referential integrity checks for relational backup consistency
            if (version >= 10) {
                val debtIds = debts.map { it.id }.toSet()
                debtPayments.forEach { dp ->
                    if (dp.debtId > 0L && dp.debtId !in debtIds) {
                        throw IllegalArgumentException("ملف النسخة الاحتياطية غير مترابط: دفعة دين تشير إلى دين غير موجود (#${dp.debtId}).")
                    }
                }
                val empIds = employees.map { it.id }.toSet()
                employeeTransactions.forEach { tx ->
                    if (tx.employeeId > 0L && tx.employeeId !in empIds) {
                        throw IllegalArgumentException("ملف النسخة الاحتياطية غير مترابط: حركة عامل تشير إلى عامل غير موجود (#${tx.employeeId}).")
                    }
                }
                val accIds = exchangeAccounts.map { it.id }.toSet()
                exchangeTransfers.forEach { tr ->
                    if (tr.accountId > 0L && tr.accountId !in accIds) {
                        throw IllegalArgumentException("ملف النسخة الاحتياطية غير مترابط: تحويل صرافة يشير إلى حساب غير موجود (#${tr.accountId}).")
                    }
                }
            }

            val preview = BackupPreview(
                app = "HesabiStore",
                version = version,
                timestamp = root.optLong("timestamp", System.currentTimeMillis()),
                productsCount = products.size,
                salesCount = sales.size,
                purchasesCount = purchases.size,
                expensesCount = expenses.size,
                employeesCount = employees.size,
                employeeTransactionsCount = employeeTransactions.size,
                partiesCount = parties.size,
                debtsCount = debts.size,
                debtPaymentsCount = debtPayments.size,
                shiftsCount = shifts.size,
                cashMovementsCount = cashMovements.size,
                exchangeAccountsCount = exchangeAccounts.size,
                exchangeTransfersCount = exchangeTransfers.size,
                auditLogsCount = auditLogs.size,
                hasStoreSettings = storeSettings != null,
                configItemsCount = configItems.size
            )

            ParsedBackupData(
                preview = preview,
                snapshot = StoreBackupSnapshot(
                    products = products,
                    sales = sales,
                    purchases = purchases,
                    expenses = expenses,
                    employees = employees,
                    employeeTransactions = employeeTransactions,
                    parties = parties,
                    debts = debts,
                    debtPayments = debtPayments,
                    shifts = shifts,
                    cashMovements = cashMovements,
                    exchangeAccounts = exchangeAccounts,
                    exchangeTransfers = exchangeTransfers,
                    auditLogs = auditLogs,
                    storeSettings = storeSettings,
                    configItems = configItems
                )
            )
        }
    }

    suspend fun restoreFromJson(jsonString: String, repository: StoreRepository): Result<BackupPreview> {
        val parsed = validateAndParseBackup(jsonString)
        return parsed.mapCatching { data ->
            repository.restoreFullBackupSnapshot(data.snapshot)
            data.preview
        }
    }
}
