package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.StatementHelper
import kotlin.math.abs

data class ReturnableInvoiceLine(
    val lineId: Long,
    val productName: String,
    val category: String,
    val color: String,
    val size: String,
    val unit: String,
    val originalQty: Int,
    val alreadyReturnedQty: Int,
    val exactInvoiceUnitPrice: Double,
    val origLineTotal: Double,
    val alreadyReturnedAmount: Double,
    val currentStockQty: Int? = null
) {
    val remainingQty: Int get() = (originalQty - alreadyReturnedQty).coerceAtLeast(0)
    fun maxReturnable(isPurchaseReturn: Boolean): Int {
        return if (isPurchaseReturn && currentStockQty != null) {
            minOf(remainingQty, currentStockQty).coerceAtLeast(0)
        } else {
            remainingQty
        }
    }
}

@Composable
fun InvoiceReturnOperationDialog(
    returnType: String,
    invoiceLabel: String,
    invoiceTimestamp: Long,
    partyName: String,
    partyOpenDebt: Double,
    lines: List<ReturnableInvoiceLine>,
    cashBalance: Double,
    exchangeAccounts: List<ExchangeAccount>,
    onDismiss: () -> Unit,
    onConfirm: (
        requests: List<ReturnLineRequest>,
        returnNumberInput: String,
        settlementMethod: String,
        cashAmount: Double,
        transferAmount: Double,
        debtReducedAmount: Double,
        surplusCreditAmount: Double,
        exchangeAccount: ExchangeAccount?,
        notes: String,
        timestamp: Long
    ) -> Unit
) {
    val isSaleReturn = returnType == ReturnTypes.SALE_RETURN
    val accentColor = if (isSaleReturn) FintechAmber else BrandBlue
    val hasNamedParty = partyName.isNotBlank() &&
        partyName.trim() != "زبون نقدي" &&
        partyName.trim() != "عميل عام" &&
        partyName.trim() != "مورد عام"

    val returnQtyMap = remember(invoiceLabel, returnType) {
        mutableStateMapOf<Long, Int>().apply {
            lines.forEach { put(it.lineId, 0) }
        }
    }

    val totalPiecesToReturn = lines.sumOf { (returnQtyMap[it.lineId] ?: 0).coerceAtLeast(0) }
    val totalReturnAmount = lines.sumOf { line ->
        val q = (returnQtyMap[line.lineId] ?: 0).coerceAtLeast(0)
        when {
            q <= 0 -> 0.0
            q == line.remainingQty -> (line.origLineTotal - line.alreadyReturnedAmount).coerceAtLeast(0.0)
            else -> line.exactInvoiceUnitPrice * q
        }
    }

    var selectedMode by remember(invoiceLabel, returnType) {
        mutableStateOf(
            if (partyOpenDebt > 0.01 && hasNamedParty) ReturnSettlementMethods.DEBT_DEDUCTION
            else ReturnSettlementMethods.CASH
        )
    }
    var excessMode by remember(invoiceLabel, returnType) { mutableStateOf(ReturnSettlementMethods.CASH) }
    var selectedAccount by remember(exchangeAccounts) { mutableStateOf(exchangeAccounts.firstOrNull()) }

    var cashPartStr by remember(invoiceLabel) { mutableStateOf("") }
    var transferPartStr by remember(invoiceLabel) { mutableStateOf("") }
    var debtPartStr by remember(invoiceLabel) { mutableStateOf("") }
    var surplusPartStr by remember(invoiceLabel) { mutableStateOf("") }

    var returnNumberInput by remember(invoiceLabel) { mutableStateOf("") }
    var notes by remember(invoiceLabel) { mutableStateOf("") }
    var returnTimestamp by remember(invoiceLabel) { mutableLongStateOf(System.currentTimeMillis()) }

    val settlementAmounts = remember(
        selectedMode,
        excessMode,
        totalReturnAmount,
        partyOpenDebt,
        cashPartStr,
        transferPartStr,
        debtPartStr,
        surplusPartStr
    ) {
        when (selectedMode) {
            ReturnSettlementMethods.CASH -> listOf(totalReturnAmount, 0.0, 0.0, 0.0)
            ReturnSettlementMethods.TRANSFER -> listOf(0.0, totalReturnAmount, 0.0, 0.0)
            ReturnSettlementMethods.PARTY_CREDIT -> listOf(0.0, 0.0, 0.0, totalReturnAmount)
            ReturnSettlementMethods.DEBT_DEDUCTION -> {
                val applyDebt = minOf(totalReturnAmount, partyOpenDebt.coerceAtLeast(0.0))
                val excess = (totalReturnAmount - applyDebt).coerceAtLeast(0.0)
                when (excessMode) {
                    ReturnSettlementMethods.CASH -> listOf(excess, 0.0, applyDebt, 0.0)
                    ReturnSettlementMethods.TRANSFER -> listOf(0.0, excess, applyDebt, 0.0)
                    else -> listOf(0.0, 0.0, applyDebt, excess)
                }
            }
            else -> listOf(
                (cashPartStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                (transferPartStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                (debtPartStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                (surplusPartStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            )
        }
    }

    val cashAmount = settlementAmounts[0]
    val transferAmount = settlementAmounts[1]
    val debtReducedAmount = settlementAmounts[2]
    val surplusCreditAmount = settlementAmounts[3]
    val settlementSum = cashAmount + transferAmount + debtReducedAmount + surplusCreditAmount
    val splitDiff = totalReturnAmount - settlementSum

    val validationError: String? = when {
        totalPiecesToReturn <= 0 -> "حدد الكمية المرجعة لصنف واحد على الأقل من الفاتورة"
        abs(splitDiff) > 0.01 -> "مجموع التسوية لا يطابق إجمالي المرتجع"
        debtReducedAmount > partyOpenDebt + 0.01 -> "خصم الدين يتجاوز الدين المفتوح (${FormatUtils.formatCurrency(partyOpenDebt)})"
        (debtReducedAmount > 0.01 || surplusCreditAmount > 0.01) && !hasNamedParty ->
            "لا يمكن تسجيل خصم دين أو رصيد دائن بدون حساب مسمى"
        transferAmount > 0.01 && selectedAccount == null ->
            "اختر حساب الصراف أو المحفظة لتنفيذ التحويل"
        isSaleReturn && cashAmount > cashBalance + 0.01 ->
            "رصيد الصندوق النقدي (${FormatUtils.formatCurrency(cashBalance)}) لا يكفي لرد المبلغ نقداً"
        isSaleReturn && transferAmount > 0.01 && selectedAccount != null && transferAmount > selectedAccount!!.balance + 0.01 ->
            "رصيد حساب (${selectedAccount!!.name}) لا يكفي لرد المبلغ عبر الصراف"
        else -> null
    }

    val effectiveMethodLabel = when {
        selectedMode == ReturnSettlementMethods.DEBT_DEDUCTION && (totalReturnAmount - debtReducedAmount) > 0.01 ->
            "خصم من الدين + ${if (excessMode == ReturnSettlementMethods.CASH) "رد نقدي للفائض" else if (excessMode == ReturnSettlementMethods.TRANSFER) "حوالة للفائض" else "رصيد دائن"}"
        else -> selectedMode
    }

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = if (isSaleReturn) "مرتجع مبيعات — فاتورة #$invoiceLabel" else "مرتجع مشتريات — فاتورة #$invoiceLabel",
        subtitle = "${if (isSaleReturn) "العميل" else "المورد"}: ${partyName.ifBlank { if (isSaleReturn) "زبون نقدي" else "مورد عام" }} • ${FormatUtils.formatDate(invoiceTimestamp)}",
        icon = Icons.AutoMirrored.Filled.Undo,
        accentColor = accentColor,
        confirmText = "اعتماد المرتجع (${FormatUtils.formatNumber(totalReturnAmount)})",
        confirmIcon = Icons.Default.CheckCircle,
        confirmColor = accentColor,
        confirmEnabled = validationError == null,
        confirmTestTag = "confirm_return_button",
        onConfirm = {
            val reqs = lines.mapNotNull { line ->
                val q = returnQtyMap[line.lineId] ?: 0
                if (q > 0) ReturnLineRequest(line.lineId, q) else null
            }
            onConfirm(
                reqs,
                returnNumberInput.trim(),
                effectiveMethodLabel,
                cashAmount,
                transferAmount,
                debtReducedAmount,
                surplusCreditAmount,
                selectedAccount,
                notes.trim(),
                returnTimestamp
            )
        }
    ) {
        DialogInfoBanner(
            title = if (isSaleReturn) "الاعتماد على سعر البيع الصافي المسجل بالفاتورة" else "الاعتماد على سعر الشراء الصافي المسجل بالفاتورة",
            value = "",
            containerColor = if (isSaleReturn) FintechAmberBg else BrandBlueBg,
            contentColor = accentColor,
            borderColor = if (isSaleReturn) FintechAmberBorder else BrandBlue.copy(alpha = 0.35f),
            icon = Icons.Default.Verified
        )

        if (partyOpenDebt > 0.01) {
            DialogInfoBanner(
                title = if (isSaleReturn) "الدين المفتوح على العميل:" else "الدين المفتوح للمورد:",
                value = FormatUtils.formatCurrency(partyOpenDebt),
                containerColor = FintechIndigoBg,
                contentColor = FintechIndigo,
                borderColor = FintechIndigo.copy(alpha = 0.3f),
                icon = Icons.Default.AccountBalanceWallet
            )
        }

        DialogSectionHeader(
            title = "أصناف الفاتورة الأصلية (${lines.size}):",
            icon = Icons.Default.Inventory2,
            accentColor = accentColor
        )

        lines.forEach { line ->
            val currentQty = returnQtyMap[line.lineId] ?: 0
            val maxAllowed = line.maxReturnable(!isSaleReturn)
            val lineSubtotal = when {
                currentQty <= 0 -> 0.0
                currentQty == line.remainingQty -> (line.origLineTotal - line.alreadyReturnedAmount).coerceAtLeast(0.0)
                else -> line.exactInvoiceUnitPrice * currentQty
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (currentQty > 0) accentColor.copy(alpha = 0.06f) else SlateBg,
                border = BorderStroke(
                    width = if (currentQty > 0) 1.5.dp else 1.dp,
                    color = if (currentQty > 0) accentColor.copy(alpha = 0.45f) else SlateBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.productName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SlateTextPrimary
                            )
                            Text(
                                text = listOfNotNull(
                                    line.category.takeIf { it.isNotBlank() },
                                    line.size.takeIf { it.isNotBlank() }?.let { "مقاس $it" },
                                    line.color.takeIf { it.isNotBlank() }
                                ).joinToString(" • "),
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PdfHeaderNavy.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "سعر الفاتورة: ${FormatUtils.formatCurrency(line.exactInvoiceUnitPrice)} / حبة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PdfHeaderNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "بالفاتورة: ${line.originalQty} حبة • مُرجع سابقاً: ${line.alreadyReturnedQty} • قابل للإرجاع: ${line.remainingQty}",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                            if (!isSaleReturn && line.currentStockQty != null) {
                                Text(
                                    text = "المتاح حالياً بالمخزن: ${line.currentStockQty} حبة",
                                    fontSize = 11.sp,
                                    color = if (line.currentStockQty < line.remainingQty) FintechRose else FintechEmerald,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { if (currentQty > 0) returnQtyMap[line.lineId] = currentQty - 1 },
                                enabled = currentQty > 0,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("return_qty_minus_${line.lineId}")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "إنقاص الكمية")
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, SlateBorder),
                                modifier = Modifier.widthIn(min = 56.dp)
                            ) {
                                Text(
                                    text = "$currentQty",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (currentQty > 0) accentColor else SlateTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { if (currentQty < maxAllowed) returnQtyMap[line.lineId] = currentQty + 1 },
                                enabled = currentQty < maxAllowed,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("return_qty_plus_${line.lineId}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة الكمية")
                            }
                            TextButton(
                                onClick = { returnQtyMap[line.lineId] = if (currentQty == maxAllowed) 0 else maxAllowed },
                                enabled = maxAllowed > 0,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("return_qty_all_${line.lineId}")
                            ) {
                                Text(
                                    text = if (currentQty == maxAllowed && maxAllowed > 0) "تصفير" else "الكل ($maxAllowed)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }

                        if (currentQty > 0) {
                            Text(
                                text = FormatUtils.formatCurrency(lineSubtotal),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }

        DialogInfoBanner(
            title = "إجمالي قيمة المرتجع ($totalPiecesToReturn حبة):",
            value = FormatUtils.formatCurrency(totalReturnAmount),
            containerColor = if (totalPiecesToReturn > 0) accentColor.copy(alpha = 0.1f) else SlateBg,
            contentColor = if (totalPiecesToReturn > 0) accentColor else SlateTextSecondary,
            borderColor = if (totalPiecesToReturn > 0) accentColor.copy(alpha = 0.35f) else SlateBorder,
            icon = Icons.Default.Calculate
        )

        DialogSectionHeader(
            title = if (isSaleReturn) "طريقة تسوية وإرجاع المبلغ للعميل:" else "طريقة استرداد / تسوية المبلغ مع المورد:",
            icon = Icons.Default.Payments,
            accentColor = PdfHeaderNavy
        )

        val settlementChoices = buildList {
            if (partyOpenDebt > 0.01 && hasNamedParty) add(ReturnSettlementMethods.DEBT_DEDUCTION)
            add(ReturnSettlementMethods.CASH)
            add(ReturnSettlementMethods.TRANSFER)
            if (hasNamedParty) add(ReturnSettlementMethods.PARTY_CREDIT)
            add(ReturnSettlementMethods.MIXED)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(settlementChoices) { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode, fontSize = 12.sp, fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("return_settlement_chip_$mode")
                )
            }
        }

        if (selectedMode == ReturnSettlementMethods.DEBT_DEDUCTION && totalReturnAmount > partyOpenDebt + 0.01) {
            val excessAmt = totalReturnAmount - partyOpenDebt
            DialogInfoBanner(
                title = "يتبقى فائض بعد تصفير الدين (${FormatUtils.formatCurrency(excessAmt)}):",
                value = "اختر تصريف الفائض",
                containerColor = FintechAmberBg,
                contentColor = FintechAmber,
                borderColor = FintechAmberBorder,
                icon = Icons.Default.Info
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val excessOptions = buildList {
                    add(ReturnSettlementMethods.CASH)
                    add(ReturnSettlementMethods.TRANSFER)
                    if (hasNamedParty) add(ReturnSettlementMethods.PARTY_CREDIT)
                }
                items(excessOptions) { opt ->
                    FilterChip(
                        selected = excessMode == opt,
                        onClick = { excessMode = opt },
                        label = { Text(opt, fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (selectedMode == ReturnSettlementMethods.MIXED) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cashPartStr,
                    onValueChange = { cashPartStr = it },
                    label = { Text("كاش") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = storeDialogTextFieldColors(FintechEmerald),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("return_mixed_cash_input")
                )
                OutlinedTextField(
                    value = transferPartStr,
                    onValueChange = { transferPartStr = it },
                    label = { Text("صرافة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = storeDialogTextFieldColors(FintechIndigo),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("return_mixed_transfer_input")
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = debtPartStr,
                    onValueChange = { debtPartStr = it },
                    label = { Text("خصم دين") },
                    enabled = hasNamedParty && partyOpenDebt > 0.01,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = storeDialogTextFieldColors(accentColor),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("return_mixed_debt_input")
                )
                OutlinedTextField(
                    value = surplusPartStr,
                    onValueChange = { surplusPartStr = it },
                    label = { Text("رصيد دائن") },
                    enabled = hasNamedParty,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("return_mixed_surplus_input")
                )
            }
            val exactSplit = abs(splitDiff) <= 0.01
            DialogInfoBanner(
                title = if (exactSplit) "التسوية المختلطة مطابقة:" else "الفارق المتبقي للتوزيع:",
                value = if (exactSplit) FormatUtils.formatCurrency(totalReturnAmount) else FormatUtils.formatCurrency(splitDiff),
                containerColor = if (exactSplit) FintechEmeraldBg else FintechRoseBg,
                contentColor = if (exactSplit) FintechEmerald else FintechRose,
                borderColor = if (exactSplit) FintechEmeraldBorder else FintechRoseBorder,
                icon = if (exactSplit) Icons.Default.CheckCircle else Icons.Default.Warning
            )
        }

        if (transferAmount > 0.01 || selectedMode == ReturnSettlementMethods.TRANSFER) {
            if (exchangeAccounts.isEmpty()) {
                DialogInfoBanner(
                    title = "لا توجد حسابات صرافة مضافة",
                    value = "اختر طريقة أخرى",
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.Warning
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccount?.id == acc.id,
                            onClick = { selectedAccount = acc },
                            label = { Text("${acc.name} (${FormatUtils.formatNumber(acc.balance)})", fontSize = 12.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = returnNumberInput,
            onValueChange = { returnNumberInput = it },
            label = { Text("رقم إشعار المرتجع (اختياري)") },
            placeholder = { Text(if (isSaleReturn) "تلقائي: RS-0001" else "تلقائي: RP-0001") },
            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfGoldDark) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("return_number_input")
        )

        TransactionDatePickerField(
            selectedTimestamp = returnTimestamp,
            onDateSelected = { returnTimestamp = it },
            label = "تاريخ المرتجع",
            testTag = "return_date_picker"
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("سبب المرتجع / ملاحظات (اختياري)") },
            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = SlateTextSecondary) },
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("return_notes_input")
        )

        validationError?.takeIf { totalPiecesToReturn > 0 }?.let { err ->
            DialogInfoBanner(
                title = "تنبيه:",
                value = err,
                containerColor = FintechRoseBg,
                contentColor = FintechRose,
                borderColor = FintechRoseBorder,
                icon = Icons.Default.ErrorOutline
            )
        }
    }
}

@Composable
fun CompletedReturnDialog(
    result: CompletedReturnResult,
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isSaleReturn = result.returnType == ReturnTypes.SALE_RETURN
    val accentColor = if (isSaleReturn) FintechAmber else BrandBlue

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = if (isSaleReturn) "تم توثيق مرتجع المبيعات!" else "تم توثيق مرتجع المشتريات!",
        subtitle = "إشعار #${result.returnNumber} • فاتورة #${result.originalInvoiceNumber}",
        icon = Icons.Default.CheckCircle,
        accentColor = accentColor,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val pdf = InvoiceHelper.generateReturnReceiptPdf(context, result.records, settings)
                        InvoiceHelper.printPdfFile(context, pdf, "مرتجع_${result.returnNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PdfHeaderNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("print_return_pdf_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val pdf = InvoiceHelper.generateReturnReceiptPdf(context, result.records, settings)
                        InvoiceHelper.sharePdfFile(context, pdf, "مشاركة إشعار مرتجع #${result.returnNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_return_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة PDF", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val txt = StatementHelper.formatReturnReceiptText(result.records, settings)
                        StatementHelper.shareText(context, txt, "مشاركة مرتجع #${result.returnNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, FintechEmeraldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FintechEmeraldBg, contentColor = FintechEmerald),
                    modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("share_return_whatsapp_button")
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("واتساب", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val txt = StatementHelper.formatReturnReceiptText(result.records, settings)
                        StatementHelper.copyToClipboard(context, txt, "مرتجع #${result.returnNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PdfHeaderNavy),
                    modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SlateTextSecondary),
                    modifier = Modifier.weight(0.8f).heightIn(min = 48.dp).testTag("close_return_receipt_button")
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SlateBg,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "رقم المرتجع: #${result.returnNumber}  |  الفاتورة الأصلية: #${result.originalInvoiceNumber}",
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp)
                    )
                }

                HorizontalDivider(color = SlateBorder)
                ReceiptRow(if (isSaleReturn) "العميل:" else "المورد:", result.partyName, isBold = true)
                ReceiptRow("الكمية المرجعة:", "${result.totalPiecesReturned} حبة (${result.records.size} أصناف)")
                ReceiptRow("طريقة التسوية:", result.settlementMethod)
                if (result.debtReducedAmount > 0.01) {
                    ReceiptRow("خصم من الدين:", FormatUtils.formatCurrency(result.debtReducedAmount), color = FintechEmerald, isBold = true)
                }
                if (result.cashAmount > 0.01) {
                    ReceiptRow(if (isSaleReturn) "مبلغ نقدي مُرجع:" else "مبلغ نقدي مستلم:", FormatUtils.formatCurrency(result.cashAmount))
                }
                if (result.transferAmount > 0.01) {
                    ReceiptRow("حوالة (${result.exchangeAccountName}):", FormatUtils.formatCurrency(result.transferAmount))
                }
                if (result.surplusCreditAmount > 0.01) {
                    ReceiptRow("رصيد دائن بالحساب:", FormatUtils.formatCurrency(result.surplusCreditAmount), color = BrandBlue)
                }
                ReceiptRow("التاريخ:", FormatUtils.formatDateTime(result.timestamp))

                HorizontalDivider(color = SlateBorder)
                result.records.forEach { rec ->
                    val unitPrice = if (isSaleReturn) rec.unitSellPrice else rec.unitBuyPrice
                    Text(
                        text = "• ${rec.productName}: ${rec.quantity} حبة × ${FormatUtils.formatNumber(unitPrice)} = ${FormatUtils.formatCurrency(rec.totalAmount)}",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = SlateBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إجمالي المرتجع:", fontWeight = FontWeight.Bold)
                    Text(
                        text = FormatUtils.formatCurrency(result.totalReturnAmount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = accentColor)
                    )
                }
            }
        }
    }
}
