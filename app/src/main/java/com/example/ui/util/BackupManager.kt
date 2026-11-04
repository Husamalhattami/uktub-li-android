package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.*
import com.example.data.repository.StoreRepository
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BackupManager {

    suspend fun createBackupFile(context: Context, repository: StoreRepository): File {
        val root = JSONObject()
        root.put("app", "HesabiStore")
        root.put("version", 6)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Products
        val products = repository.allProducts.firstOrNull() ?: emptyList()
        val prodsArray = JSONArray()
        products.forEach { p ->
            val obj = JSONObject().apply {
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
                put("unit", p.unit)
                put("colorsCount", p.colorsCount)
                put("colorDetails", p.colorDetails)
            }
            prodsArray.put(obj)
        }
        root.put("products", prodsArray)

        // 2. Sales
        val sales = repository.allSales.firstOrNull() ?: emptyList()
        val salesArray = JSONArray()
        sales.forEach { s ->
            val obj = JSONObject().apply {
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
                put("exchangeAccountId", s.exchangeAccountId)
                put("exchangeAccountName", s.exchangeAccountName)
                put("transferRef", s.transferRef)
                put("employeeName", s.employeeName)
                put("timestamp", s.timestamp)
                put("unit", s.unit)
            }
            salesArray.put(obj)
        }
        root.put("sales", salesArray)

        // 3. Purchases
        val purchases = repository.allPurchases.firstOrNull() ?: emptyList()
        val purchasesArray = JSONArray()
        purchases.forEach { pur ->
            val obj = JSONObject().apply {
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
            }
            purchasesArray.put(obj)
        }
        root.put("purchases", purchasesArray)

        // 4. Expenses
        val expenses = repository.allExpenses.firstOrNull() ?: emptyList()
        val expensesArray = JSONArray()
        expenses.forEach { e ->
            val obj = JSONObject().apply {
                put("id", e.id)
                put("category", e.category)
                put("amount", e.amount)
                put("notes", e.notes)
                put("receiptNumber", e.receiptNumber)
                put("timestamp", e.timestamp)
                put("paidFromCash", e.paidFromCash)
                put("exchangeAccountId", e.exchangeAccountId)
                put("exchangeAccountName", e.exchangeAccountName)
            }
            expensesArray.put(obj)
        }
        root.put("expenses", expensesArray)

        // 5. Exchange Accounts
        val exchangeAccounts = repository.allExchangeAccounts.firstOrNull() ?: emptyList()
        val accsArray = JSONArray()
        exchangeAccounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("id", acc.id)
                put("name", acc.name)
                put("accountNumber", acc.accountNumber)
                put("phone", acc.phone)
                put("balance", acc.balance)
                put("type", acc.type)
                put("notes", acc.notes)
            }
            accsArray.put(obj)
        }
        root.put("exchangeAccounts", accsArray)

        // 6. Parties
        val parties = repository.allParties.firstOrNull() ?: emptyList()
        val partiesArray = JSONArray()
        parties.forEach { party ->
            val obj = JSONObject().apply {
                put("id", party.id)
                put("type", party.type)
                put("name", party.name)
                put("phone", party.phone)
                put("address", party.address)
                put("notes", party.notes)
                put("initialBalance", party.initialBalance)
            }
            partiesArray.put(obj)
        }
        root.put("parties", partiesArray)

        // 7. Debts
        val debts = repository.allDebts.firstOrNull() ?: emptyList()
        val debtsArray = JSONArray()
        debts.forEach { d ->
            val obj = JSONObject().apply {
                put("id", d.id)
                put("type", d.type)
                put("partyName", d.partyName)
                put("phone", d.phone)
                put("originalAmount", d.originalAmount)
                put("paidAmount", d.paidAmount)
                put("remainingAmount", d.remainingAmount)
                put("details", d.details)
                put("createdAt", d.createdAt)
                put("isSettled", d.isSettled)
            }
            debtsArray.put(obj)
        }
        root.put("debts", debtsArray)

        // Write file
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val file = File(backupDir, "نسخة_احتياطية_${FormatUtils.formatDate(System.currentTimeMillis()).replace('/', '-')}.json")
        FileOutputStream(file).use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
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

    suspend fun restoreFromJson(jsonString: String, repository: StoreRepository): Boolean {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("app") || root.getString("app") != "HesabiStore") {
                return false
            }

            // Restore inside transactional flow
            repository.restoreFullData(
                productsJson = root.optJSONArray("products"),
                salesJson = root.optJSONArray("sales"),
                purchasesJson = root.optJSONArray("purchases"),
                expensesJson = root.optJSONArray("expenses"),
                accountsJson = root.optJSONArray("exchangeAccounts"),
                partiesJson = root.optJSONArray("parties"),
                debtsJson = root.optJSONArray("debts")
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
