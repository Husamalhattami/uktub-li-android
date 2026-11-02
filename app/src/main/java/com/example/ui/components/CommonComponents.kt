package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashDrawerShift
import com.example.ui.CompletedSaleReceipt
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreTopBar(
    title: String,
    showBackButton: Boolean = false,
    activeShift: CashDrawerShift? = null,
    onBackClick: () -> Unit = {},
    onShiftClick: () -> Unit = {},
    trailingAction: @Composable (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
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
                        contentDescription = "رجوع"
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LuxuryGoldLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MidnightNavy,
                        modifier = Modifier.size(22.dp)
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
                        color = Color.White
                    )
                )
                Text(
                    text = "دفتر المحل والمخزون الذكي",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        },
        actions = {
            // Shift status badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (activeShift?.isOpen == true) EmeraldProfit.copy(alpha = 0.2f) else RubyExpense.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeShift?.isOpen == true) EmeraldProfit else RubyExpense
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onShiftClick() }
                    .testTag("shift_status_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (activeShift?.isOpen == true) EmeraldProfit else RubyExpense)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeShift?.isOpen == true) "وردية مفتوحة" else "وردية مغلقة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
            trailingAction?.invoke()
        }
    )
}

@Composable
fun QuickThousandAdder(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val quickAdders = listOf("+10", "+50", "+100", "+200", "+500")
        quickAdders.forEach { label ->
            OutlinedButton(
                onClick = {
                    val toAdd = label.replace("+", "").replace(",", "").toDoubleOrNull() ?: 0.0
                    val currentNum = currentValue.replace(",", "").toDoubleOrNull() ?: 0.0
                    onValueChange((currentNum + toAdd).toLong().toString())
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("quick_add_$label"),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = contentColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = contentColor.copy(alpha = 0.7f)
                        )
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
        appendLine("      محل الملابس الرجالية     ")
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
                    tint = EmeraldProfit,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تم البيع بنجاح!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "فاتورة بيع #${sale.id}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    ReceiptRow("الصنف:", sale.productName)
                    ReceiptRow("اللون والقياس:", "${sale.color} - ${sale.size}")
                    ReceiptRow("الكمية:", "${sale.quantity} قطعة")
                    ReceiptRow("سعر الوحدة:", FormatUtils.formatCurrency(sale.unitSellPrice))
                    if (sale.discount > 0) {
                        ReceiptRow("الخصم:", "- " + FormatUtils.formatCurrency(sale.discount), color = RubyExpense)
                    }
                    ReceiptRow("طريقة الدفع:", sale.paymentType, isBold = true)
                    if (sale.customerName.isNotBlank()) {
                        ReceiptRow("الزبون:", sale.customerName)
                    }
                    ReceiptRow("البائع:", sale.employeeName)

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(10.dp))

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
                                color = EmeraldProfit
                            )
                        )
                    }

                    // Profit calculated automatically
                    Surface(
                        color = EmeraldLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الربح المحقق في هذه العملية:",
                                style = MaterialTheme.typography.bodySmall.copy(color = EmeraldProfit, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "+ " + FormatUtils.formatCurrency(sale.profit),
                                style = MaterialTheme.typography.bodySmall.copy(color = EmeraldProfit, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.testTag("share_receipt_button")
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
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
        )
    }
}

@Composable
fun MovementItemRow(movement: com.example.data.model.CashDrawerMovement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        .background(if (movement.isIncome) EmeraldLight else RubyLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (movement.isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (movement.isIncome) EmeraldProfit else RubyExpense,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = movement.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (movement.details.isNotBlank()) {
                        Text(
                            text = movement.details,
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )
                    }
                    Text(
                        text = FormatUtils.formatDateTime(movement.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted, fontSize = 10.sp)
                    )
                }
            }

            Text(
                text = (if (movement.isIncome) "+ " else "- ") + FormatUtils.formatCurrency(movement.amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (movement.isIncome) EmeraldProfit else RubyExpense
                )
            )
        }
    }
}

