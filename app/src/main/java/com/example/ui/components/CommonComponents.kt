package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.AppScreen
import com.example.ui.CompletedSaleReceipt
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreTopBar(
    title: String,
    subtitle: String = "إدارة أسهل .. لمحل أكثر نجاحاً",
    showBackButton: Boolean = false,
    activeShift: CashDrawerShift? = null,
    onBackClick: () -> Unit = {},
    onShiftClick: () -> Unit = {},
    trailingAction: @Composable (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PdfHeaderNavy,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 6.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = PdfGoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF90CDF4),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            trailingAction?.invoke()
        }
    )
}

@Composable
fun QuickThousandAdder(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val quickAdders = listOf("+1,000", "+5,000", "+10,000", "+50,000", "+100,000")


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        quickAdders.forEach { label ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BrandBlueBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val toAdd = label.replace("+", "").replace(",", "").toDoubleOrNull() ?: 0.0
                        val currentNum = currentValue.replace(",", "").toDoubleOrNull() ?: 0.0
                        onValueChange((currentNum + toAdd).toLong().toString())
                    }
                    .testTag("quick_add_$label")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = contentColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    ),
                    maxLines = 1
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SaleReceiptDialog(
    receipt: CompletedSaleReceipt,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sale = receipt.sale

    val receiptText = buildString {
        appendLine("══════════════════════════")
        appendLine("      الجرادي للملابس الجاهزة     ")
        appendLine("          هاتف: 778383570         ")
        appendLine("       فاتورة بيع رقم #${sale.id}      ")
        appendLine("══════════════════════════")
        appendLine("التاريخ: ${receipt.dateStr}  ${receipt.timeStr}")
        appendLine("البائع: ${sale.employeeName}")
        if (sale.customerName.isNotBlank()) {
            appendLine("الزبون: ${sale.customerName}")
        }
        appendLine("طريقة الدفع: ${sale.paymentType}")
        appendLine("──────────────────────────")
        appendLine("القطعة: ${sale.productName}")
        appendLine("اللون: ${sale.color} | القياس: ${sale.size}")
        appendLine("الكمية: ${sale.quantity} × ${FormatUtils.formatCurrency(sale.unitSellPrice)}")
        if (sale.discount > 0) {
            appendLine("الخصم: ${FormatUtils.formatCurrency(sale.discount)}")
        }
        appendLine("──────────────────────────")
        appendLine("الإجمالي الصافي: ${FormatUtils.formatCurrency(sale.totalAmount)}")
        appendLine("══════════════════════════")
        appendLine("شكراً لزيارتكم! أهلاً وسهلاً بكم")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = FintechEmerald,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تمت عملية البيع بنجاح!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "فاتورة بيع رقم #${sale.id}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    ReceiptRow("الصنف:", sale.productName)
                    ReceiptRow("اللون والمقاس:", "${sale.color} - ${sale.size}")
                    ReceiptRow("الكمية:", "${sale.quantity} قطعة (${sale.unit})")
                    ReceiptRow("سعر الوحدة:", FormatUtils.formatCurrency(sale.unitSellPrice))
                    if (sale.discount > 0) {
                        ReceiptRow("الخصم الممنوح:", "- " + FormatUtils.formatCurrency(sale.discount), color = FintechRose)
                    }
                    ReceiptRow("طريقة الدفع:", sale.paymentType, isBold = true)
                    if (sale.customerName.isNotBlank()) {
                        ReceiptRow("الزبون:", sale.customerName)
                    }
                    if (sale.employeeName.isNotBlank()) {
                        ReceiptRow("البائع:", sale.employeeName)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المبلغ الإجمالي:",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = FormatUtils.formatCurrency(sale.totalAmount),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FintechEmerald
                            )
                        )
                    }

                    Surface(
                        color = FintechEmeraldBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الربح المحقق:",
                                style = MaterialTheme.typography.bodySmall.copy(color = FintechEmerald, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "+ " + FormatUtils.formatCurrency(sale.profit),
                                style = MaterialTheme.typography.bodySmall.copy(color = FintechEmerald, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, receiptText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفاتورة عبر"))
                },
                modifier = Modifier.testTag("share_receipt_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("مشاركة الفاتورة")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_receipt_button")
            ) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
private fun ReceiptRow(label: String, value: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
        )
    }
}

@Composable
fun MovementItemRow(movement: CashDrawerMovement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (movement.isIncome) FintechEmeraldBg else FintechRoseBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (movement.isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (movement.isIncome) FintechEmerald else FintechRose,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = movement.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val badgeLabel = when (movement.type) {
                            "SALE_CASH" -> "مبيعات كاش"
                            "PURCHASE_PAID" -> "مشتريات كاش"
                            "DEBT_COLLECTED" -> "قبض دين"
                            "SUPPLIER_PAID" -> "سداد مورد"
                            "SALARY_PAID" -> "صرف راتب"
                            "ADVANCE_PAID" -> "سلفة عامل"
                            "EXPENSE_PAID" -> "مصروف نقدي"
                            "WITHDRAWAL" -> "سحب المالك"
                            "CAPITAL_DEPOSIT" -> "إيداع سيولة"
                            "TRANSFER_TO_EXCHANGE" -> "تحويل للصراف"
                            else -> "حركة صندوق"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (movement.isIncome) FintechEmeraldBg else FintechRoseBg
                        ) {
                            Text(
                                text = badgeLabel,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (movement.isIncome) FintechEmerald else FintechRose,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    if (movement.details.isNotBlank()) {
                        Text(
                            text = movement.details,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary, fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = FormatUtils.formatDateTime(movement.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateTextMuted, fontSize = 10.sp)
                    )
                }
            }

            Text(
                text = (if (movement.isIncome) "+ " else "- ") + FormatUtils.formatCurrency(movement.amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (movement.isIncome) FintechEmerald else FintechRose
                )
            )
        }
    }
}

@Composable
fun PdfBottomNavigation(
    currentScreen: com.example.ui.AppScreen,
    onNavigate: (com.example.ui.AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = listOf(
                Triple("الرئيسية", Icons.Default.Home, com.example.ui.AppScreen.HOME),
                Triple("البيع", Icons.Default.ShoppingCart, com.example.ui.AppScreen.SALE),
                Triple("المخزون", Icons.Default.Inventory2, com.example.ui.AppScreen.INVENTORY),
                Triple("الحسابات", Icons.Default.AccountBalanceWallet, com.example.ui.AppScreen.CASH_DRAWER),
                Triple("التقارير", Icons.Default.BarChart, com.example.ui.AppScreen.REPORTS)
            )

            navItems.forEach { (label, icon, screen) ->
                val isSelected = currentScreen == screen || (screen == AppScreen.CASH_DRAWER && (currentScreen == AppScreen.EXCHANGE_ACCOUNTS || currentScreen == AppScreen.TRANSACTIONS_LOG || currentScreen == AppScreen.DEBTS))
                val tint = if (isSelected) PdfGoldDark else SlateTextSecondary
                val bg = if (isSelected) PdfGoldBg else Color.Transparent

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onNavigate(screen) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfNavigationRail(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        Triple("الرئيسية", Icons.Default.Home, AppScreen.HOME),
        Triple("البيع", Icons.Default.ShoppingCart, AppScreen.SALE),
        Triple("المخزون", Icons.Default.Inventory2, AppScreen.INVENTORY),
        Triple("الحسابات", Icons.Default.AccountBalanceWallet, AppScreen.CASH_DRAWER),
        Triple("التقارير", Icons.Default.BarChart, AppScreen.REPORTS)
    )

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
        containerColor = Color.White,
        contentColor = SlateTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEach { (label, icon, screen) ->
                val isSelected = currentScreen == screen || (screen == AppScreen.CASH_DRAWER && (currentScreen == AppScreen.EXCHANGE_ACCOUNTS || currentScreen == AppScreen.TRANSACTIONS_LOG || currentScreen == AppScreen.DEBTS))
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onNavigate(screen) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = PdfGoldDark,
                        selectedTextColor = PdfGoldDark,
                        indicatorColor = PdfGoldBg,
                        unselectedIconColor = SlateTextSecondary,
                        unselectedTextColor = SlateTextSecondary
                    )
                )
            }
        }
    }
}

@Composable
fun CollectionReceiptDialog(
    customerParties: List<PartyAccount>,
    customerDebts: List<DebtRecord>,
    exchangeAccounts: List<ExchangeAccount>,
    initialParty: PartyAccount? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        payerName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumber: String,
        paymentMethod: String,
        exchangeAccount: ExchangeAccount?,
        notes: String
    ) -> Unit
) {
    var selectedParty by remember { mutableStateOf(initialParty) }
    var payerName by remember { mutableStateOf(initialParty?.name ?: "") }
    var amountStr by remember { mutableStateOf("") }
    var voucherNumber by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقداً") }
    var selectedAccount by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var notes by remember { mutableStateOf("") }

    val matchedDebtTotal = remember(selectedParty, payerName, customerDebts) {
        val nameToMatch = (selectedParty?.name ?: payerName).trim()
        if (nameToMatch.isBlank()) 0.0
        else customerDebts
            .filter { it.type == "CUSTOMER" && !it.isSettled && it.partyName.trim().equals(nameToMatch, ignoreCase = true) }
            .sumOf { it.remainingAmount }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = FintechEmerald)
                Spacer(Modifier.width(8.dp))
                Text(
                    "تحصيل زلط (سند قبض)",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "اختر العميل أو اكتب الاسم (أو اتركه فارغاً للتحصيل العام):",
                    fontSize = 12.sp,
                    color = SlateTextSecondary
                )

                if (customerParties.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customerParties) { party ->
                            val isSelected = selectedParty?.id == party.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedParty = null
                                        payerName = ""
                                    } else {
                                        selectedParty = party
                                        payerName = party.name
                                    }
                                },
                                label = { Text(party.name, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = payerName,
                    onValueChange = {
                        payerName = it
                        if (selectedParty?.name != it) selectedParty = null
                    },
                    label = { Text("اسم العميل / الدافع (اختياري)") },
                    placeholder = { Text("مثال: أحمد التميمي أو تحصيل عام") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_payer_name_input")
                )

                if (matchedDebtTotal > 0.0) {
                    Surface(
                        color = FintechAmberBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الدين المستحق على العميل:", fontSize = 12.sp, color = FintechAmber)
                            Text(
                                FormatUtils.formatCurrency(matchedDebtTotal),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = FintechAmber
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("المبلغ المحصل *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_amount_input")
                )

                QuickThousandAdder(
                    currentValue = amountStr,
                    onValueChange = { amountStr = it }
                )

                OutlinedTextField(
                    value = voucherNumber,
                    onValueChange = { voucherNumber = it },
                    label = { Text("رقم السند (اختياري)") },
                    placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SND-0001)") },
                    supportingText = {
                        Text(
                            "إذا وُجد رقم سند أدخله هنا، وإذا تُرك فارغاً يتم توليد رقم سند تلقائي",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collection_voucher_number_input")
                )

                Text("جهة استلام المبلغ:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMethod == "نقداً",
                        onClick = { paymentMethod = "نقداً" },
                        label = { Text("نقداً للصندوق", fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f).testTag("collection_method_cash")
                    )
                    FilterChip(
                        selected = paymentMethod == "صرافة/محفظة",
                        onClick = { paymentMethod = "صرافة/محفظة" },
                        label = { Text("صرافة / محفظة", fontSize = 12.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f).testTag("collection_method_exchange")
                    )
                }

                if (paymentMethod == "صرافة/محفظة") {
                    if (exchangeAccounts.isEmpty()) {
                        Surface(
                            color = FintechRoseBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "لا توجد حسابات صرافة أو محافظ مضافة بعد — أضف حساب صرافة أولاً أو استلم نقداً.",
                                fontSize = 11.sp,
                                color = FintechRose,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text("اختر حساب الصرافة/المحفظة:", fontSize = 11.sp, color = SlateTextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(exchangeAccounts) { acc ->
                                FilterChip(
                                    selected = selectedAccount?.id == acc.id,
                                    onClick = { selectedAccount = acc },
                                    label = { Text(acc.name, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات / البيان (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amountVal = amountStr.toDoubleOrNull() ?: 0.0
            val canConfirm = amountVal > 0.0 && (paymentMethod == "نقداً" || selectedAccount != null)
            Button(
                onClick = {
                    onConfirm(
                        payerName,
                        selectedParty,
                        amountVal,
                        voucherNumber,
                        paymentMethod,
                        if (paymentMethod == "نقداً") null else selectedAccount,
                        notes
                    )
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald),
                modifier = Modifier.testTag("collection_confirm_button")
            ) {
                Text("تأكيد التحصيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun CollectionSuccessDialog(
    receipt: CollectionReceiptResult,
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("تم توثيق سند القبض بنجاح!", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = FintechEmeraldBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "رقم السند: #${receipt.voucherNumber}",
                            fontWeight = FontWeight.Bold,
                            color = FintechEmerald,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    HorizontalDivider(color = SlateBorder)
                    ReceiptRow("الجهة / الدافع:", receipt.payerName, isBold = true)
                    ReceiptRow("طريقة التحصيل:", if (receipt.paymentMethod == "نقداً") "نقداً للصندوق" else "صرافة (${receipt.exchangeAccountName})")
                    if (receipt.appliedToDebtAmount > 0.0) {
                        ReceiptRow("مسدد من الدين:", FormatUtils.formatCurrency(receipt.appliedToDebtAmount), color = FintechEmerald, isBold = true)
                    }
                    if (receipt.directCollectionAmount > 0.0) {
                        ReceiptRow("تحصيل مباشر:", FormatUtils.formatCurrency(receipt.directCollectionAmount))
                    }
                    if (receipt.remainingCustomerDebt > 0.0) {
                        ReceiptRow("المتبقي على العميل:", FormatUtils.formatCurrency(receipt.remainingCustomerDebt), color = FintechRose, isBold = true)
                    }
                    if (receipt.notes.isNotBlank()) {
                        ReceiptRow("البيان:", receipt.notes)
                    }
                    ReceiptRow("التاريخ:", FormatUtils.formatDateTime(receipt.timestamp))

                    HorizontalDivider(color = SlateBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المبلغ المحصل:", fontWeight = FontWeight.Bold)
                        Text(
                            FormatUtils.formatCurrency(receipt.amount),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = FintechEmerald)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val file = InvoiceHelper.generateCollectionVoucherPdf(context, receipt, settings)
                        InvoiceHelper.printPdfFile(context, file, "سند_قبض_${receipt.voucherNumber}")
                    }
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة")
                }
                Button(
                    onClick = {
                        val file = InvoiceHelper.generateCollectionVoucherPdf(context, receipt, settings)
                        InvoiceHelper.sharePdfFile(context, file, "مشاركة سند قبض #${receipt.voucherNumber}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة PDF")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_collection_receipt_button")) {
                Text("إغلاق")
            }
        }
    )
}
