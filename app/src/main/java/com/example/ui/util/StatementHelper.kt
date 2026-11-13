package com.example.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.model.*

enum class StatementMode {
    DETAILED, // كشف مفصل بجميع العمليات
    SUMMARY   // تقرير نهائي / ملخص الرصيد
}

data class PartyLedgerEntry(
    val timestamp: Long,
    val title: String,
    val subDetails: String,
    val debit: Double,  // عليه (العميل: مبيعات/ديون مسجلة عليه؛ المورد: مسدد له نقداً/تحويلاً/سندات صرف)
    val credit: Double, // له (العميل: دفعات مسددة/تحصيل؛ المورد: بضاعة موردة/ديون مستحقة له)
    val runningBalance: Double, // موجب = رصيد مستحق أساسي (على العميل / للمورد)، سالب = فائض مدفوع مقدماً
    val kind: String // "INVOICE", "DEBT", "PAYMENT"
)

data class PartyStatementSummary(
    val party: PartyAccount,
    val isCustomer: Boolean,
    val entries: List<PartyLedgerEntry>,
    val totalOnParty: Double,   // إجمالي ما عليه (Customer: عليه للمحل؛ Supplier: المسدد للمورد)
    val totalForParty: Double,  // إجمالي ما له (Customer: المسدد منه؛ Supplier: مستحق للمورد)
    val netBalance: Double,     // Customer: totalOnParty - totalForParty; Supplier: totalForParty - totalOnParty
    val invoicesCount: Int,
    val paymentsCount: Int,
    val unsettledDebtsCount: Int
)

data class EmployeeLedgerEntry(
    val timestamp: Long,
    val title: String,
    val type: String,
    val creditEarned: Double,
    val debitTaken: Double,
    val runningBalance: Double
)

data class EmployeeStatementSummary(
    val employee: Employee,
    val salesCount: Int,
    val totalSalesAmount: Double,
    val commissionEarned: Double,
    val bonuses: Double,
    val advances: Double,
    val dailyStipends: Double,
    val deductions: Double,
    val salaryPaid: Double,
    val totalEarned: Double,          // إجمالي ما له (راتب + عمولة + مكافآت)
    val totalDeductedAndPaid: Double, // إجمالي ما عليه / استلمه (سلف + صرفيات + خصومات + راتب مصروف)
    val remainingBalance: Double,     // صافي المتبقي له
    val entries: List<EmployeeLedgerEntry>
)

data class DailyReportSummary(
    val periodTitle: String,
    val generatedAt: Long,
    val salesCount: Int,
    val totalSales: Double,
    val cashSales: Double,
    val transferSales: Double,
    val debtSales: Double,
    val cogs: Double,
    val grossProfit: Double,
    val purchasesCount: Int,
    val totalPurchases: Double,
    val purchasesCash: Double,
    val purchasesTransfer: Double,
    val purchasesDebt: Double,
    val collectionsTotal: Double,
    val supplierPaymentsTotal: Double,
    val expensesTotal: Double,
    val salariesAndAdvancesTotal: Double,
    val netProfit: Double,
    val currentCashBalance: Double,
    val currentExchangeBalance: Double,
    val inventoryCapital: Double,
    val totalCustomerDebts: Double,
    val totalSupplierDebts: Double
)

object StatementHelper {

    fun buildPartyStatement(
        party: PartyAccount,
        sales: List<SaleTransaction>,
        purchases: List<PurchaseRecord>,
        debts: List<DebtRecord>,
        payments: List<DebtPayment>
    ): PartyStatementSummary {
        val isCustomer = party.type == "CUSTOMER"
        val normalizedName = party.name.trim()
        val rawRows = mutableListOf<PartyLedgerEntry>()

        var invoicesCount = 0

        if (isCustomer) {
            val matchingSales = sales.filter {
                it.customerName.trim().equals(normalizedName, ignoreCase = true)
            }
            val saleIdsSet = matchingSales.map { it.id }.toSet()

            // Group cart sales by invoiceNumber (when non-blank), otherwise individual sale id
            val groupedSales = matchingSales.groupBy { sale ->
                if (sale.invoiceNumber.isNotBlank()) "INV:${sale.invoiceNumber.trim()}" else "ID:${sale.id}"
            }

            groupedSales.values.forEach { group ->
                invoicesCount++
                val first = group.minByOrNull { it.timestamp } ?: group.first()
                val invLabel = if (first.invoiceNumber.isNotBlank()) "#${first.invoiceNumber}" else "#${first.id}"
                val itemsSummary = if (group.size == 1) {
                    "${first.productName} (${first.quantity} ${first.unit})"
                } else {
                    "${group.size} أصناف (${group.sumOf { it.quantity }} قطعة)"
                }
                val totalInv = group.sumOf { it.totalAmount }
                val paidUpfront = group.sumOf { it.cashAmount + it.transferAmount }
                val debtPart = group.sumOf { it.debtAmount }

                val details = buildString {
                    append(itemsSummary)
                    if (paidUpfront > 0.01 && debtPart > 0.01) {
                        append(" • مدفوع: ${FormatUtils.formatNumber(paidUpfront)} | آجل: ${FormatUtils.formatNumber(debtPart)}")
                    } else if (paidUpfront > 0.01) {
                        append(" • ${first.paymentType}")
                    } else {
                        append(" • آجل كامل")
                    }
                }

                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = first.timestamp,
                        title = "فاتورة بيع $invLabel",
                        subDetails = details,
                        debit = totalInv,
                        credit = paidUpfront,
                        runningBalance = 0.0,
                        kind = "INVOICE"
                    )
                )
            }

            val partyDebts = debts.filter {
                it.type == "CUSTOMER" && it.partyName.trim().equals(normalizedName, ignoreCase = true)
            }
            val partyDebtIds = partyDebts.map { it.id }.toSet()

            // Include only unlinked/opening debts (avoid double-counting debts created alongside a sale already listed above)
            partyDebts.filter { d ->
                d.referenceSaleId == null || d.referenceSaleId !in saleIdsSet
            }.forEach { d ->
                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = d.createdAt,
                        title = d.details.ifBlank { "رصيد / دين مسجل على العميل" },
                        subDetails = if (d.isSettled) "حالة السجل: مسدد" else "حالة السجل: مفتوح",
                        debit = d.originalAmount,
                        credit = 0.0,
                        runningBalance = 0.0,
                        kind = "DEBT"
                    )
                )
            }

            // Payments for this customer (linked to customer debts or unlinked collection receipts)
            val customerPayments = payments.filter { p ->
                p.partyName.trim().equals(normalizedName, ignoreCase = true) &&
                    (p.debtId in partyDebtIds ||
                        (p.debtId == 0L && !p.notes.contains("سند صرف") && !p.voucherNumber.startsWith("SRF")))
            }

            val groupedPayments = customerPayments.groupBy { p ->
                if (p.voucherNumber.isNotBlank()) "V:${p.voucherNumber.trim()}" else "P:${p.id}"
            }

            groupedPayments.values.forEach { group ->
                val first = group.first()
                val amount = group.sumOf { it.amount }
                val voucherTag = if (first.voucherNumber.isNotBlank()) "سند قبض #${first.voucherNumber}" else "سند قبض / سداد"
                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = first.timestamp,
                        title = voucherTag,
                        subDetails = buildString {
                            append("الطريقة: ${first.paymentMethod}")
                            val cleanNote = first.notes.replace(Regex("سند:\\s*#[^|]+\\|?"), "").trim()
                            if (cleanNote.isNotBlank()) append(" • $cleanNote")
                        },
                        debit = 0.0,
                        credit = amount,
                        runningBalance = 0.0,
                        kind = "PAYMENT"
                    )
                )
            }

            val sorted = rawRows.sortedBy { it.timestamp }
            var running = 0.0
            val withRunning = sorted.map { row ->
                running += (row.debit - row.credit)
                row.copy(runningBalance = running)
            }

            val totalOnParty = withRunning.sumOf { it.debit }
            val totalForParty = withRunning.sumOf { it.credit }
            val netBalance = totalOnParty - totalForParty

            return PartyStatementSummary(
                party = party,
                isCustomer = true,
                entries = withRunning,
                totalOnParty = totalOnParty,
                totalForParty = totalForParty,
                netBalance = netBalance,
                invoicesCount = invoicesCount,
                paymentsCount = groupedPayments.size,
                unsettledDebtsCount = partyDebts.count { !it.isSettled }
            )
        } else {
            // SUPPLIER
            val matchingPurchases = purchases.filter {
                it.supplierName.trim().equals(normalizedName, ignoreCase = true)
            }
            val purchaseIdsSet = matchingPurchases.map { it.id }.toSet()

            val groupedPurchases = matchingPurchases.groupBy { p ->
                if (p.invoiceNumber.isNotBlank()) "PUR:${p.invoiceNumber.trim()}" else "ID:${p.id}"
            }

            groupedPurchases.values.forEach { group ->
                invoicesCount++
                val first = group.minByOrNull { it.timestamp } ?: group.first()
                val invLabel = if (first.invoiceNumber.isNotBlank()) "#${first.invoiceNumber}" else "#${first.id}"
                val itemsSummary = if (group.size == 1) {
                    "${first.modelName} (${first.quantity} حبة)"
                } else {
                    "${group.size} أصناف (${group.sumOf { it.quantity }} حبة)"
                }
                val totalCost = group.sumOf { it.totalCost }
                val paidUpfront = group.sumOf { it.paidAmount }
                val debtPart = group.sumOf { it.remainingDebt }

                val details = buildString {
                    append(itemsSummary)
                    if (paidUpfront > 0.01 && debtPart > 0.01) {
                        append(" • مدفوع: ${FormatUtils.formatNumber(paidUpfront)} | آجل: ${FormatUtils.formatNumber(debtPart)}")
                    } else if (paidUpfront > 0.01) {
                        append(" • ${first.paymentMethod}")
                    } else {
                        append(" • توريد آجل")
                    }
                }

                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = first.timestamp,
                        title = "فاتورة شراء $invLabel",
                        subDetails = details,
                        debit = paidUpfront,  // المسدد للمورد عند الشراء
                        credit = totalCost,   // المستحق للمورد قيمة البضاعة
                        runningBalance = 0.0,
                        kind = "INVOICE"
                    )
                )
            }

            val partyDebts = debts.filter {
                it.type == "SUPPLIER" && it.partyName.trim().equals(normalizedName, ignoreCase = true)
            }
            val partyDebtIds = partyDebts.map { it.id }.toSet()

            partyDebts.filter { d ->
                d.referencePurchaseId == null || d.referencePurchaseId !in purchaseIdsSet
            }.forEach { d ->
                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = d.createdAt,
                        title = d.details.ifBlank { "رصيد / دين مسجل للمورد" },
                        subDetails = if (d.isSettled) "حالة السجل: مسدد" else "حالة السجل: مفتوح",
                        debit = 0.0,
                        credit = d.originalAmount,
                        runningBalance = 0.0,
                        kind = "DEBT"
                    )
                )
            }

            val supplierPayments = payments.filter { p ->
                p.partyName.trim().equals(normalizedName, ignoreCase = true) &&
                    (p.debtId in partyDebtIds ||
                        (p.debtId == 0L && (p.notes.contains("سند صرف") || p.notes.contains("مورد") || p.voucherNumber.startsWith("SRF"))))
            }

            val groupedPayments = supplierPayments.groupBy { p ->
                if (p.voucherNumber.isNotBlank()) "V:${p.voucherNumber.trim()}" else "P:${p.id}"
            }

            groupedPayments.values.forEach { group ->
                val first = group.first()
                val amount = group.sumOf { it.amount }
                val voucherTag = if (first.voucherNumber.isNotBlank()) "سند صرف #${first.voucherNumber}" else "سداد للمورد"
                rawRows.add(
                    PartyLedgerEntry(
                        timestamp = first.timestamp,
                        title = voucherTag,
                        subDetails = buildString {
                            append("الطريقة: ${first.paymentMethod}")
                            val cleanNote = first.notes
                                .replace(Regex("سند صرف لمورد:\\s*#[^|]+\\|?"), "")
                                .replace(Regex("سند:\\s*#[^|]+\\|?"), "")
                                .trim()
                            if (cleanNote.isNotBlank()) append(" • $cleanNote")
                        },
                        debit = amount, // ما دفعناه للمورد
                        credit = 0.0,
                        runningBalance = 0.0,
                        kind = "PAYMENT"
                    )
                )
            }

            val sorted = rawRows.sortedBy { it.timestamp }
            var running = 0.0
            val withRunning = sorted.map { row ->
                running += (row.credit - row.debit)
                row.copy(runningBalance = running)
            }

            val totalPaidToSupplier = withRunning.sumOf { it.debit }
            val totalForSupplier = withRunning.sumOf { it.credit }
            val netBalance = totalForSupplier - totalPaidToSupplier

            return PartyStatementSummary(
                party = party,
                isCustomer = false,
                entries = withRunning,
                totalOnParty = totalPaidToSupplier,
                totalForParty = totalForSupplier,
                netBalance = netBalance,
                invoicesCount = invoicesCount,
                paymentsCount = groupedPayments.size,
                unsettledDebtsCount = partyDebts.count { !it.isSettled }
            )
        }
    }

    fun buildEmployeeStatement(
        employee: Employee,
        transactions: List<EmployeeTransaction>,
        sales: List<SaleTransaction>
    ): EmployeeStatementSummary {
        val empTxs = transactions.filter { it.employeeId == employee.id }
        val empSales = sales.filter { it.employeeId == employee.id }
        val totalSalesAmount = empSales.sumOf { it.totalAmount }
        val commissionEarned = totalSalesAmount * (employee.commissionPercent / 100.0)

        val bonuses = empTxs.filter { it.type == "مكافأة" }.sumOf { it.amount }
        val advances = empTxs.filter { it.type == "سلفة" }.sumOf { it.amount }
        val dailyStipends = empTxs.filter { it.type == "صرفة يومية" }.sumOf { it.amount }
        val deductions = empTxs.filter { it.type == "خصم غياب" }.sumOf { it.amount }
        val salaryPaid = empTxs.filter { it.type == "صرف راتب" }.sumOf { it.amount }

        val totalEarned = employee.baseSalary + commissionEarned + bonuses
        val totalDeductedAndPaid = advances + dailyStipends + deductions + salaryPaid
        val remainingBalance = totalEarned - totalDeductedAndPaid

        val rawEntries = mutableListOf<EmployeeLedgerEntry>()
        if (employee.baseSalary > 0) {
            rawEntries.add(
                EmployeeLedgerEntry(
                    timestamp = employee.startDate.coerceAtMost(System.currentTimeMillis()),
                    title = "الراتب الأساسي (${employee.salaryType})",
                    type = "استحقاق راتب",
                    creditEarned = employee.baseSalary,
                    debitTaken = 0.0,
                    runningBalance = 0.0
                )
            )
        }
        if (commissionEarned > 0.01) {
            rawEntries.add(
                EmployeeLedgerEntry(
                    timestamp = empSales.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
                    title = "عمولة مبيعات (${employee.commissionPercent}% من ${FormatUtils.formatNumber(totalSalesAmount)})",
                    type = "عمولة مبيعات",
                    creditEarned = commissionEarned,
                    debitTaken = 0.0,
                    runningBalance = 0.0
                )
            )
        }

        empTxs.sortedBy { it.timestamp }.forEach { tx ->
            val isCredit = tx.type == "مكافأة"
            rawEntries.add(
                EmployeeLedgerEntry(
                    timestamp = tx.timestamp,
                    title = tx.notes.ifBlank { tx.type },
                    type = tx.type,
                    creditEarned = if (isCredit) tx.amount else 0.0,
                    debitTaken = if (isCredit) 0.0 else tx.amount,
                    runningBalance = 0.0
                )
            )
        }

        var running = 0.0
        val finalEntries = rawEntries.sortedBy { it.timestamp }.map { entry ->
            running += (entry.creditEarned - entry.debitTaken)
            entry.copy(runningBalance = running)
        }

        return EmployeeStatementSummary(
            employee = employee,
            salesCount = empSales.size,
            totalSalesAmount = totalSalesAmount,
            commissionEarned = commissionEarned,
            bonuses = bonuses,
            advances = advances,
            dailyStipends = dailyStipends,
            deductions = deductions,
            salaryPaid = salaryPaid,
            totalEarned = totalEarned,
            totalDeductedAndPaid = totalDeductedAndPaid,
            remainingBalance = remainingBalance,
            entries = finalEntries
        )
    }

    fun buildDailyReportSummary(
        periodTitle: String,
        filteredSales: List<SaleTransaction>,
        filteredPurchases: List<PurchaseRecord>,
        filteredExpenses: List<ExpenseRecord>,
        filteredEmployeeTx: List<EmployeeTransaction>,
        filteredPayments: List<DebtPayment>,
        allDebts: List<DebtRecord>,
        allProducts: List<ProductItem>,
        currentCashBalance: Double,
        currentExchangeBalance: Double
    ): DailyReportSummary {
        val totalSales = filteredSales.sumOf { it.totalAmount }
        val cashSales = filteredSales.sumOf { it.cashAmount }
        val transferSales = filteredSales.sumOf { it.transferAmount }
        val debtSales = filteredSales.sumOf { it.debtAmount }
        val cogs = filteredSales.sumOf { it.unitBuyPrice * it.quantity }
        val grossProfit = totalSales - cogs

        val totalPurchases = filteredPurchases.sumOf { it.totalCost }
        val purchasesCash = filteredPurchases.sumOf { it.cashPaid }
        val purchasesTransfer = filteredPurchases.sumOf { it.transferPaid }
        val purchasesDebt = filteredPurchases.sumOf { it.remainingDebt }

        val customerDebtIds = allDebts.filter { it.type == "CUSTOMER" }.map { it.id }.toSet()
        val supplierDebtIds = allDebts.filter { it.type == "SUPPLIER" }.map { it.id }.toSet()

        val collectionsTotal = filteredPayments.filter { p ->
            p.debtId in customerDebtIds ||
                (p.debtId == 0L && !p.notes.contains("سند صرف") && !p.voucherNumber.startsWith("SRF"))
        }.sumOf { it.amount }

        val supplierPaymentsTotal = filteredPayments.filter { p ->
            p.debtId in supplierDebtIds ||
                (p.debtId == 0L && (p.notes.contains("سند صرف") || p.notes.contains("مورد") || p.voucherNumber.startsWith("SRF")))
        }.sumOf { it.amount }

        val expensesTotal = filteredExpenses.sumOf { it.amount }
        val salariesAndAdvancesTotal = filteredEmployeeTx
            .filter { it.type == "صرف راتب" || it.type == "سلفة" || it.type == "مكافأة" }
            .sumOf { it.amount }

        val netProfit = grossProfit - expensesTotal - salariesAndAdvancesTotal
        val inventoryCapital = allProducts.sumOf { it.buyPrice * it.quantity }
        val totalCustomerDebts = allDebts.filter { it.type == "CUSTOMER" && !it.isSettled }.sumOf { it.remainingAmount }
        val totalSupplierDebts = allDebts.filter { it.type == "SUPPLIER" && !it.isSettled }.sumOf { it.remainingAmount }

        return DailyReportSummary(
            periodTitle = periodTitle,
            generatedAt = System.currentTimeMillis(),
            salesCount = filteredSales.map { if (it.invoiceNumber.isNotBlank()) it.invoiceNumber else "S-${it.id}" }.distinct().size,
            totalSales = totalSales,
            cashSales = cashSales,
            transferSales = transferSales,
            debtSales = debtSales,
            cogs = cogs,
            grossProfit = grossProfit,
            purchasesCount = filteredPurchases.map { if (it.invoiceNumber.isNotBlank()) it.invoiceNumber else "P-${it.id}" }.distinct().size,
            totalPurchases = totalPurchases,
            purchasesCash = purchasesCash,
            purchasesTransfer = purchasesTransfer,
            purchasesDebt = purchasesDebt,
            collectionsTotal = collectionsTotal,
            supplierPaymentsTotal = supplierPaymentsTotal,
            expensesTotal = expensesTotal,
            salariesAndAdvancesTotal = salariesAndAdvancesTotal,
            netProfit = netProfit,
            currentCashBalance = currentCashBalance,
            currentExchangeBalance = currentExchangeBalance,
            inventoryCapital = inventoryCapital,
            totalCustomerDebts = totalCustomerDebts,
            totalSupplierDebts = totalSupplierDebts
        )
    }

    // ====================================================
    // WhatsApp & Social Media Formatted Text Generators
    // ====================================================

    fun formatPartyStatementText(
        summary: PartyStatementSummary,
        mode: StatementMode,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val phone = settings?.phone.orEmpty()
        val address = settings?.address.orEmpty()
        val nowStr = FormatUtils.formatDateTime(System.currentTimeMillis())

        return buildString {
            appendLine("📋 *${if (mode == StatementMode.DETAILED) "كشف حساب مفصل" else "تقرير رصيد نهائي"}*")
            appendLine("🏪 *$storeName*")
            if (address.isNotBlank() || phone.isNotBlank()) {
                appendLine(listOfNotNull(address.takeIf { it.isNotBlank() }, phone.takeIf { it.isNotBlank() }?.let { "📞 $it" }).joinToString(" | "))
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine(if (summary.isCustomer) "👤 العميل: *${summary.party.name}*" else "🚚 المورد: *${summary.party.name}*")
            if (summary.party.phone.isNotBlank()) appendLine("📱 الهاتف: ${summary.party.phone}")
            appendLine("📅 تاريخ الكشف: $nowStr")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")

            if (summary.isCustomer) {
                appendLine("🔴 *كم عليه (إجمالي الفواتير والديون):* ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}")
                appendLine("🟢 *كم له (إجمالي المدفوع والمحصل):* ${FormatUtils.formatCurrency(summary.totalForParty, currency)}")
                appendLine("──────────────────────")
                when {
                    summary.netBalance > 0.01 -> appendLine("⚖️ *كم باقي (مستحق عليه للمحل): ${FormatUtils.formatCurrency(summary.netBalance, currency)}*")
                    summary.netBalance < -0.01 -> appendLine("💎 *له رصيد زائد لدى المحل: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}*")
                    else -> appendLine("✅ *الحساب خالص: 0 $currency*")
                }
            } else {
                appendLine("🔵 *كم له (إجمالي التوريدات والديون للمورد):* ${FormatUtils.formatCurrency(summary.totalForParty, currency)}")
                appendLine("🟢 *كم عليه (إجمالي ما تم سداده له):* ${FormatUtils.formatCurrency(summary.totalOnParty, currency)}")
                appendLine("──────────────────────")
                when {
                    summary.netBalance > 0.01 -> appendLine("⚖️ *كم باقي للمورد بذمتنا: ${FormatUtils.formatCurrency(summary.netBalance, currency)}*")
                    summary.netBalance < -0.01 -> appendLine("💎 *رصيد مدفوع مقدماً لدى المورد: ${FormatUtils.formatCurrency(-summary.netBalance, currency)}*")
                    else -> appendLine("✅ *حساب المورد خالص: 0 $currency*")
                }
            }

            if (mode == StatementMode.DETAILED && summary.entries.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📑 *تفاصيل جميع العمليات (${summary.entries.size}):*")
                summary.entries.forEachIndexed { idx, entry ->
                    val dateStr = FormatUtils.formatDate(entry.timestamp)
                    appendLine("${idx + 1}. [$dateStr] ${entry.title}")
                    if (entry.subDetails.isNotBlank()) appendLine("   ↳ ${entry.subDetails}")
                    val amountsLine = buildList {
                        if (summary.isCustomer) {
                            if (entry.debit > 0.01) add("عليه: ${FormatUtils.formatNumber(entry.debit)}")
                            if (entry.credit > 0.01) add("له: ${FormatUtils.formatNumber(entry.credit)}")
                            val balText = when {
                                entry.runningBalance > 0.01 -> "عليه ${FormatUtils.formatCurrency(entry.runningBalance, currency)}"
                                entry.runningBalance < -0.01 -> "له ${FormatUtils.formatCurrency(-entry.runningBalance, currency)}"
                                else -> "خالص"
                            }
                            add("الرصيد: $balText")
                        } else {
                            if (entry.credit > 0.01) add("له: ${FormatUtils.formatNumber(entry.credit)}")
                            if (entry.debit > 0.01) add("عليه/مدفوع: ${FormatUtils.formatNumber(entry.debit)}")
                            val balText = when {
                                entry.runningBalance > 0.01 -> "باقي له ${FormatUtils.formatCurrency(entry.runningBalance, currency)}"
                                entry.runningBalance < -0.01 -> "مقدم ${FormatUtils.formatCurrency(-entry.runningBalance, currency)}"
                                else -> "خالص"
                            }
                            add("الرصيد: $balText")
                        }
                    }.joinToString(" | ")
                    appendLine("   $amountsLine")
                }
            } else {
                appendLine("──────────────────────")
                appendLine("📊 عدد الفواتير: ${summary.invoicesCount} | عدد السندات/الدفعات: ${summary.paymentsCount}")
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("شكراً لتعاملكم معنا • $storeName")
        }
    }

    fun formatEmployeeStatementText(
        summary: EmployeeStatementSummary,
        mode: StatementMode,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val emp = summary.employee

        return buildString {
            appendLine("👷 *${if (mode == StatementMode.DETAILED) "كشف حساب عامل مفصل" else "ملخص مستحقات عامل"}*")
            appendLine("🏪 *$storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("👤 العامل: *${emp.name}* (${emp.role})")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🟢 *كم له (إجمالي المستحقات):* ${FormatUtils.formatCurrency(summary.totalEarned, currency)}")
            appendLine("   • الراتب الأساسي: ${FormatUtils.formatCurrency(emp.baseSalary, currency)}")
            if (summary.commissionEarned > 0.01) {
                appendLine("   • عمولة المبيعات (${emp.commissionPercent}%): ${FormatUtils.formatCurrency(summary.commissionEarned, currency)}")
            }
            if (summary.bonuses > 0.01) {
                appendLine("   • المكافآت: ${FormatUtils.formatCurrency(summary.bonuses, currency)}")
            }
            appendLine("🔴 *كم عليه (المستلم والخصومات):* ${FormatUtils.formatCurrency(summary.totalDeductedAndPaid, currency)}")
            if (summary.advances > 0.01) appendLine("   • السلف: ${FormatUtils.formatCurrency(summary.advances, currency)}")
            if (summary.dailyStipends > 0.01) appendLine("   • الصرفيات اليومية: ${FormatUtils.formatCurrency(summary.dailyStipends, currency)}")
            if (summary.deductions > 0.01) appendLine("   • خصومات الغياب: ${FormatUtils.formatCurrency(summary.deductions, currency)}")
            if (summary.salaryPaid > 0.01) appendLine("   • الرواتب المصروفة: ${FormatUtils.formatCurrency(summary.salaryPaid, currency)}")
            appendLine("──────────────────────")
            when {
                summary.remainingBalance > 0.01 -> appendLine("⚖️ *كم باقي له بذمة المحل: ${FormatUtils.formatCurrency(summary.remainingBalance, currency)}*")
                summary.remainingBalance < -0.01 -> appendLine("⚠️ *عليه رصيد زائد مسحوب: ${FormatUtils.formatCurrency(-summary.remainingBalance, currency)}*")
                else -> appendLine("✅ *الحساب خالص: 0 $currency*")
            }

            if (mode == StatementMode.DETAILED && summary.entries.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📑 *تفاصيل الحركات (${summary.entries.size}):*")
                summary.entries.forEachIndexed { idx, entry ->
                    val dateStr = FormatUtils.formatDate(entry.timestamp)
                    val amt = if (entry.creditEarned > 0) "+${FormatUtils.formatNumber(entry.creditEarned)} (له)" else "-${FormatUtils.formatNumber(entry.debitTaken)} (عليه)"
                    appendLine("${idx + 1}. [$dateStr] ${entry.type}: ${entry.title} | $amt | الرصيد: ${FormatUtils.formatCurrency(entry.runningBalance, currency)}")
                }
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("إجمالي مبيعات العامل: ${FormatUtils.formatCurrency(summary.totalSalesAmount, currency)} (${summary.salesCount} عملية)")
        }
    }

    fun formatDailyReportText(
        report: DailyReportSummary,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"

        return buildString {
            appendLine("📊 *التقرير المالي الشامل — $storeName*")
            appendLine("🗓️ الفترة: *${report.periodTitle}* | ${FormatUtils.formatDateTime(report.generatedAt)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🛒 *المبيعات (${report.salesCount} فاتورة):* ${FormatUtils.formatCurrency(report.totalSales, currency)}")
            appendLine("   • نقداً (كاش): ${FormatUtils.formatCurrency(report.cashSales, currency)}")
            appendLine("   • تحويل صرافة: ${FormatUtils.formatCurrency(report.transferSales, currency)}")
            appendLine("   • آجل (بالدين): ${FormatUtils.formatCurrency(report.debtSales, currency)}")
            appendLine("📦 *المشتريات (${report.purchasesCount} فاتورة):* ${FormatUtils.formatCurrency(report.totalPurchases, currency)}")
            appendLine("   • كاش: ${FormatUtils.formatCurrency(report.purchasesCash, currency)} | صرافة: ${FormatUtils.formatCurrency(report.purchasesTransfer, currency)} | آجل: ${FormatUtils.formatCurrency(report.purchasesDebt, currency)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💚 *التحصيلات من العملاء (سندات قبض):* ${FormatUtils.formatCurrency(report.collectionsTotal, currency)}")
            appendLine("🚚 *المدفوعات للموردين (سندات صرف):* ${FormatUtils.formatCurrency(report.supplierPaymentsTotal, currency)}")
            appendLine("🧾 *المصروفات التشغيلية:* ${FormatUtils.formatCurrency(report.expensesTotal, currency)}")
            appendLine("👷 *الرواتب والسلف:* ${FormatUtils.formatCurrency(report.salariesAndAdvancesTotal, currency)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📈 مجمل الربح التجاري: ${FormatUtils.formatCurrency(report.grossProfit, currency)}")
            appendLine("🌟 *صافي الربح الفعلي: ${FormatUtils.formatCurrency(report.netProfit, currency)}*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💵 *كاش الصندوق الحالي:* ${FormatUtils.formatCurrency(report.currentCashBalance, currency)}")
            appendLine("🏦 *أرصدة الصرافين والمحافظ:* ${FormatUtils.formatCurrency(report.currentExchangeBalance, currency)}")
            appendLine("📦 *قيمة المخزون (التكلفة):* ${FormatUtils.formatCurrency(report.inventoryCapital, currency)}")
            appendLine("💰 *لنا عند العملاء (ديون):* ${FormatUtils.formatCurrency(report.totalCustomerDebts, currency)}")
            appendLine("🧾 *علينا للموردين (ديون):* ${FormatUtils.formatCurrency(report.totalSupplierDebts, currency)}")
        }
    }

    fun formatCollectionVoucherText(
        receipt: CollectionReceiptResult,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        return buildString {
            appendLine("✅ *سند قبض وتحصيل زلط — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧾 رقم السند: *#${receipt.voucherNumber}*")
            appendLine("👤 استلمنا من: *${receipt.payerName}*")
            appendLine("💵 المبلغ المستلم: *${FormatUtils.formatCurrency(receipt.amount, currency)}*")
            appendLine("💳 الطريقة: ${if (receipt.paymentMethod == "نقداً") "نقداً للصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})"}")
            if (receipt.appliedToDebtAmount > 0.01) {
                appendLine("✔️ مسدد من الدين: ${FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency)}")
            }
            if (receipt.directCollectionAmount > 0.01) {
                appendLine("➕ تحصيل مباشر / رصيد زائد: ${FormatUtils.formatCurrency(receipt.directCollectionAmount, currency)}")
            }
            appendLine("⚖️ المتبقي على العميل بعد السداد: *${FormatUtils.formatCurrency(receipt.remainingCustomerDebt, currency)}*")
            if (receipt.notes.isNotBlank()) appendLine("📝 البيان: ${receipt.notes}")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(receipt.timestamp)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("شكراً لتعاملكم مع $storeName")
        }
    }

    fun formatSupplierPaymentVoucherText(
        receipt: SupplierPaymentReceiptResult,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        return buildString {
            appendLine("💸 *سند صرف وتسديد لمورد — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🧾 رقم السند: *#${receipt.voucherNumber}*")
            appendLine("🚚 صرفنا إلى المورد: *${receipt.supplierName}*")
            appendLine("💵 المبلغ المسدد: *${FormatUtils.formatCurrency(receipt.amount, currency)}*")
            appendLine("💳 طريقة الصرف: ${if (receipt.paymentMethod == "نقداً") "نقداً من الصندوق" else "صرافة / محفظة (${receipt.exchangeAccountName})"}")
            if (receipt.appliedToDebtAmount > 0.01) {
                appendLine("✔️ مسدد من الحساب الآجل: ${FormatUtils.formatCurrency(receipt.appliedToDebtAmount, currency)}")
            }
            if (receipt.directPaymentAmount > 0.01) {
                appendLine("➕ دفعة مباشرة / مقدمة: ${FormatUtils.formatCurrency(receipt.directPaymentAmount, currency)}")
            }
            appendLine("⚖️ المتبقي للمورد بذمتنا بعد السداد: *${FormatUtils.formatCurrency(receipt.remainingSupplierDebt, currency)}*")
            if (receipt.notes.isNotBlank()) appendLine("📝 البيان: ${receipt.notes}")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(receipt.timestamp)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("$storeName")
        }
    }

    fun formatSaleRecordText(sale: SaleTransaction, settings: StoreSettings?): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val inv = if (sale.invoiceNumber.isNotBlank()) "#${sale.invoiceNumber}" else "#${sale.id}"
        return buildString {
            appendLine("🧾 *فاتورة مبيعات $inv — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("👤 العميل: *${sale.customerName}*")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(sale.timestamp)}")
            appendLine("🧥 الصنف: ${sale.productName} (${sale.category} | ${sale.size} | ${sale.color})")
            appendLine("🔢 الكمية: ${sale.quantity} حبة (${sale.unit})")
            if (sale.discount > 0.01) appendLine("🏷️ الخصم: ${FormatUtils.formatCurrency(sale.discount, currency)}")
            appendLine("💵 *الإجمالي: ${FormatUtils.formatCurrency(sale.totalAmount, currency)}*")
            appendLine("💳 طريقة الدفع: ${sale.paymentType}")
            if (sale.cashAmount > 0.01) appendLine("   • كاش: ${FormatUtils.formatCurrency(sale.cashAmount, currency)}")
            if (sale.transferAmount > 0.01) appendLine("   • تحويل (${sale.exchangeAccountName}): ${FormatUtils.formatCurrency(sale.transferAmount, currency)}")
            if (sale.debtAmount > 0.01) appendLine("   • آجل (متبقي عليه): ${FormatUtils.formatCurrency(sale.debtAmount, currency)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("شكراً لزيارتكم • $storeName")
        }
    }

    fun formatPurchaseRecordText(purchase: PurchaseRecord, settings: StoreSettings?): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        val inv = if (purchase.invoiceNumber.isNotBlank()) "#${purchase.invoiceNumber}" else "#${purchase.id}"
        return buildString {
            appendLine("📦 *فاتورة مشتريات $inv — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🚚 المورد: *${purchase.supplierName}*")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(purchase.timestamp)}")
            appendLine("🧥 الصنف: ${purchase.modelName} (${purchase.category} | ${purchase.size} | ${purchase.color})")
            appendLine("🔢 الكمية: ${purchase.quantity} حبة (${purchase.unit})")
            appendLine("💵 *إجمالي التكلفة: ${FormatUtils.formatCurrency(purchase.totalCost, currency)}*")
            appendLine("🟢 المدفوع: ${FormatUtils.formatCurrency(purchase.paidAmount, currency)} (${purchase.paymentMethod})")
            if (purchase.remainingDebt > 0.01) {
                appendLine("⚖️ المتبقي للمورد: *${FormatUtils.formatCurrency(purchase.remainingDebt, currency)}*")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    fun formatMultiItemSaleText(
        invoiceNumber: String,
        customerName: String,
        paymentType: String,
        items: List<CartSaleItem>,
        discount: Double,
        netTotal: Double,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccountName: String,
        timestamp: Long,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        return buildString {
            appendLine("🧾 *فاتورة مبيعات #$invoiceNumber — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("👤 العميل: *${customerName.ifBlank { "زبون نقدي" }}*")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(timestamp)}")
            appendLine("──────────────────────")
            items.forEachIndexed { idx, line ->
                val lineAmount = line.quantity * line.sellPrice
                appendLine("${idx + 1}. ${line.product.modelName} (${line.quantity} ${line.unit}) = ${FormatUtils.formatCurrency(lineAmount, currency)}")
            }
            appendLine("──────────────────────")
            if (discount > 0.01) appendLine("🏷️ الخصم: - ${FormatUtils.formatCurrency(discount, currency)}")
            appendLine("💵 *الصافي: ${FormatUtils.formatCurrency(netTotal, currency)}*")
            appendLine("💳 الدفع: $paymentType")
            if (cashPaid > 0.01) appendLine("   • كاش: ${FormatUtils.formatCurrency(cashPaid, currency)}")
            if (transferPaid > 0.01) appendLine("   • تحويل ($exchangeAccountName): ${FormatUtils.formatCurrency(transferPaid, currency)}")
            if (debtAmount > 0.01) appendLine("   • آجل (متبقي على العميل): ${FormatUtils.formatCurrency(debtAmount, currency)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("شكراً لزيارتكم • $storeName")
        }
    }

    fun formatMultiItemPurchaseText(
        invoiceNumber: String,
        supplierName: String,
        paymentMethod: String,
        itemsCount: Int,
        totalCost: Double,
        cashPaid: Double,
        transferPaid: Double,
        debtAmount: Double,
        exchangeAccountName: String,
        transferRef: String,
        timestamp: Long,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        return buildString {
            appendLine("📦 *فاتورة مشتريات #$invoiceNumber — $storeName*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🚚 المورد: *${supplierName.ifBlank { "مورد عام" }}*")
            appendLine("📅 التاريخ: ${FormatUtils.formatDateTime(timestamp)}")
            appendLine("📋 عدد الأصناف: $itemsCount أصناف")
            appendLine("💵 *إجمالي الفاتورة: ${FormatUtils.formatCurrency(totalCost, currency)}*")
            appendLine("💳 طريقة الدفع: $paymentMethod")
            if (cashPaid > 0.01) appendLine("   • كاش من الصندوق: ${FormatUtils.formatCurrency(cashPaid, currency)}")
            if (transferPaid > 0.01) appendLine("   • تحويل ($exchangeAccountName): ${FormatUtils.formatCurrency(transferPaid, currency)}")
            if (transferRef.isNotBlank()) appendLine("   • رقم الحوالة: #$transferRef")
            if (debtAmount > 0.01) appendLine("⚖️ المتبقي للمورد: *${FormatUtils.formatCurrency(debtAmount, currency)}*")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    fun formatCashMovementSummaryText(
        periodTitle: String,
        cashBalance: Double,
        exchangeBalance: Double,
        incomeTotal: Double,
        expenseTotal: Double,
        recentLines: List<String>,
        settings: StoreSettings?
    ): String {
        val storeName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
        val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
        return buildString {
            appendLine("💼 *ملخص حركة الصندوق والحسابات — $storeName*")
            appendLine("🗓️ الفترة: $periodTitle | ${FormatUtils.formatDateTime(System.currentTimeMillis())}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💵 رصيد الصندوق (كاش): *${FormatUtils.formatCurrency(cashBalance, currency)}*")
            appendLine("🏦 أرصدة الصرافين والمحافظ: *${FormatUtils.formatCurrency(exchangeBalance, currency)}*")
            appendLine("💰 إجمالي السيولة: *${FormatUtils.formatCurrency(cashBalance + exchangeBalance, currency)}*")
            appendLine("──────────────────────")
            appendLine("🟢 إجمالي المقبوضات ($periodTitle): ${FormatUtils.formatCurrency(incomeTotal, currency)}")
            appendLine("🔴 إجمالي المدفوعات ($periodTitle): ${FormatUtils.formatCurrency(expenseTotal, currency)}")
            if (recentLines.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📑 أبرز الحركات:")
                recentLines.take(15).forEach { appendLine("• $it") }
            }
        }
    }

    fun shareText(context: Context, text: String, title: String = "مشاركة عبر واتساب أو تطبيقات التواصل") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيقات المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "كشف الحساب") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "تم نسخ النص بنجاح — يمكنك لصقه في واتساب", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر النسخ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
