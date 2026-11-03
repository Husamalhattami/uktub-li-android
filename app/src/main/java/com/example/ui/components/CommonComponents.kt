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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CashDrawerMovement
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
            containerColor = BrandNavy,
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
                        .padding(horizontal = 12.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BrandBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = Color.White,
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
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "نظام المحاسبة وإدارة المحل المبسط",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                )
            }
        },
        actions = {
            // Shift status pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (activeShift?.isOpen == true) FintechEmerald.copy(alpha = 0.18f) else FintechRose.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeShift?.isOpen == true) FintechEmeraldLight else FintechRoseLight
                ),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { onShiftClick() }
                    .testTag("shift_status_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (activeShift?.isOpen == true) FintechEmeraldLight else FintechRoseLight)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeShift?.isOpen == true) "وردية مفتوحة" else "وردية مغلقة",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
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
