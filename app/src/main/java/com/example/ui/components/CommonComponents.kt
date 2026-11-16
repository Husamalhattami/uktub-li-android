package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.data.repository.PinVerificationResult
import com.example.ui.AppScreen
import com.example.ui.CompletedSaleReceipt
import com.example.ui.StoreViewModel
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils
import com.example.ui.util.InvoiceHelper
import com.example.ui.util.PinSecurityManager
import com.example.ui.util.StatementHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreTopBar(
    title: String,
    subtitle: String = "إدارة أسهل .. لمحل أكثر نجاحاً",
    showBackButton: Boolean = false,
    activeShift: CashDrawerShift? = null,
    logoPath: String = "",
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
                com.example.ui.util.StoreLogoBadge(
                    logoPath = logoPath,
                    size = 40.dp,
                    cornerRadius = 10.dp,
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDatePickerField(
    selectedTimestamp: Long,
    onDateSelected: (Long) -> Unit,
    label: String = "تاريخ العملية",
    modifier: Modifier = Modifier,
    testTag: String = "transaction_date_picker_field"
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val isPast = FormatUtils.isPastDate(selectedTimestamp)
    val isFuture = FormatUtils.isFutureDate(selectedTimestamp)
    val badgeText = when {
        isPast -> "تاريخ سابق"
        isFuture -> "تاريخ مستقبلي"
        else -> "اليوم"
    }
    val badgeBg = when {
        isPast -> FintechAmberBg
        isFuture -> BrandBlueBg
        else -> FintechEmeraldBg
    }
    val badgeFg = when {
        isPast -> FintechAmber
        isFuture -> BrandBlue
        else -> FintechEmerald
    }
    val borderColor = when {
        isPast -> FintechAmber.copy(alpha = 0.55f)
        isFuture -> BrandBlue.copy(alpha = 0.5f)
        else -> SlateBorder
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showDatePicker = true }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = SlateBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = label,
                        tint = badgeFg,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateTextSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatDate(selectedTimestamp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary,
                            fontSize = 14.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.EditCalendar,
                    contentDescription = "تغيير التاريخ",
                    tint = PdfHeaderNavy,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = FormatUtils.localTimestampToUtcPickerMillis(selectedTimestamp)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            onDateSelected(FormatUtils.mergeDateWithTimeOfDay(utcMillis, selectedTimestamp))
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy),
                    modifier = Modifier.testTag("${testTag}_confirm")
                ) {
                    Text("تأكيد التاريخ")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onDateSelected(System.currentTimeMillis())
                            showDatePicker = false
                        }
                    ) {
                        Text("اليوم", color = FintechEmerald, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("إلغاء")
                    }
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }
}

@Composable
fun storeDialogTextFieldColors(accentColor: Color = PdfHeaderNavy): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accentColor,
        unfocusedBorderColor = Color(0xFFCBD5E1),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color(0xFFF8FAFC),
        disabledContainerColor = Color(0xFFF1F5F9),
        focusedLabelColor = accentColor,
        unfocusedLabelColor = SlateTextSecondary,
        focusedTextColor = SlateTextPrimary,
        unfocusedTextColor = SlateTextPrimary,
        focusedPlaceholderColor = SlateTextMuted,
        unfocusedPlaceholderColor = SlateTextMuted,
        cursorColor = accentColor
    )
}

@Composable
fun DialogSectionHeader(
    title: String,
    icon: ImageVector? = null,
    accentColor: Color = PdfHeaderNavy
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
fun DialogSegmentedChoice(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedColor else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) selectedColor else Color(0xFFCBD5E1)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else SlateTextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else SlateTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DialogInfoBanner(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    icon: ImageVector = Icons.Default.Info
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun StoreOperationDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color = PdfGoldPrimary,
    confirmText: String? = null,
    confirmIcon: ImageVector? = Icons.Default.Check,
    confirmColor: Color = if (accentColor == PdfGoldPrimary) PdfHeaderNavy else accentColor,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "إلغاء",
    maxWidth: Dp = 520.dp,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .widthIn(max = maxWidth)
                .heightIn(max = 760.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = SlateCard,
            border = BorderStroke(1.dp, SlateBorder),
            shadowElevation = 14.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Branded Navy & Gold Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(PdfHeaderNavy, PdfHeaderNavyDark)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF93C5FD),
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Signature Gold-to-Accent Hairline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(PdfGoldPrimary, accentColor, PdfGoldPrimary.copy(alpha = 0.45f))
                            )
                        )
                )

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )

                // Pinned Footer
                if (footer != null || (onConfirm != null && !confirmText.isNullOrBlank())) {
                    HorizontalDivider(color = SlateBorder)
                    Surface(
                        color = SlateBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (footer != null) {
                                footer()
                            } else if (onConfirm != null && !confirmText.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val confirmBtnMod = Modifier
                                        .weight(1.4f)
                                        .heightIn(min = 48.dp)
                                        .let { if (confirmTestTag != null) it.testTag(confirmTestTag) else it }
                                    Button(
                                        onClick = onConfirm,
                                        enabled = confirmEnabled,
                                        shape = RoundedCornerShape(13.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = confirmColor,
                                            contentColor = Color.White,
                                            disabledContainerColor = SlateBorder,
                                            disabledContentColor = SlateTextMuted
                                        ),
                                        modifier = confirmBtnMod
                                    ) {
                                        if (confirmIcon != null) {
                                            Icon(
                                                imageVector = confirmIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = confirmText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onDismissRequest,
                                        shape = RoundedCornerShape(13.dp),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.White,
                                            contentColor = SlateTextSecondary
                                        ),
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = dismissText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
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
    settings: StoreSettings? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sale = receipt.sale
    val shopName = settings?.shopNameAr?.ifBlank { "متجري" } ?: "متجري"
    val shopPhone = settings?.phone?.trim().orEmpty()
    val shopAddress = settings?.address?.trim().orEmpty()
    val currency = settings?.currency?.ifBlank { "ريال" } ?: "ريال"
    val invLabel = sale.invoiceNumber.ifBlank { "#${sale.id}" }

    val receiptText = InvoiceHelper.generateThermalReceiptText(
        storeName = shopName,
        phoneNumber = shopPhone,
        address = shopAddress,
        invoiceId = sale.id,
        timestamp = sale.timestamp,
        customerName = sale.customerName,
        cashierName = sale.employeeName,
        items = listOf(
            "${sale.productName} (${sale.color} - ${sale.size})" to Triple(
                sale.quantity,
                sale.unitSellPrice,
                sale.totalAmount
            )
        ),
        totalAmount = sale.totalAmount,
        discount = sale.discount,
        paymentType = sale.paymentType,
        headerNote = settings?.invoiceHeader.orEmpty(),
        footerNote = settings?.invoiceFooter.orEmpty()
    )

    fun buildSingleSalePdf(): java.io.File = InvoiceHelper.generateSaleInvoicePdf(
        context = context,
        invoiceNumber = invLabel,
        customerName = sale.customerName,
        customerPhone = sale.customerPhone,
        employeeName = sale.employeeName,
        paymentType = sale.paymentType,
        items = listOf(
            InvoiceHelper.PdfSaleLineItem(
                name = sale.productName,
                details = listOfNotNull(
                    sale.category.takeIf { it.isNotBlank() },
                    sale.color.takeIf { it.isNotBlank() }?.let { "لون: $it" },
                    sale.size.takeIf { it.isNotBlank() }?.let { "مقاس: $it" }
                ).joinToString(" | "),
                quantity = sale.quantity,
                unitPrice = sale.unitSellPrice,
                total = sale.totalAmount,
                unit = sale.unit
            )
        ),
        subtotal = sale.unitSellPrice * sale.quantity,
        discount = sale.discount,
        netTotal = sale.totalAmount,
        cashPaid = sale.cashAmount,
        transferPaid = sale.transferAmount,
        debtAmount = sale.debtAmount,
        exchangeAccountName = sale.exchangeAccountName,
        transferRef = sale.transferRef,
        timestamp = sale.timestamp,
        settings = settings
    )

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "تمت عملية البيع بنجاح!",
        subtitle = "فاتورة بيع: $invLabel • ${receipt.dateStr}",
        icon = Icons.Default.CheckCircle,
        accentColor = FintechEmerald,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            InvoiceHelper.printPdfFile(context, buildSingleSalePdf(), "فاتورة_$invLabel")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("print_receipt_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            InvoiceHelper.sharePdfFile(context, buildSingleSalePdf(), "مشاركة فاتورة $invLabel")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_receipt_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = PdfHeaderNavy, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PdfHeaderNavy)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            StatementHelper.shareText(context, receiptText, "مشاركة فاتورة $invLabel")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("share_receipt_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechEmerald)
                    ) {
                        Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة واتساب / نص", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SlateTextSecondary),
                        modifier = Modifier
                            .weight(0.8f)
                            .heightIn(min = 48.dp)
                            .testTag("close_receipt_button")
                    ) {
                        Text("إغلاق", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Framed Invoice Header Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PdfHeaderNavy)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shopName,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (shopPhone.isNotBlank()) {
                                Text(
                                    text = "هاتف: $shopPhone",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PdfGoldPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "فاتورة $invLabel",
                                color = PdfGoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ReceiptRow("التاريخ والوقت:", "${receipt.dateStr} • ${receipt.timeStr}")
                    ReceiptRow("الزبون:", sale.customerName.ifBlank { "زبون نقدي" }, isBold = true)
                    if (sale.employeeName.isNotBlank()) {
                        ReceiptRow("البائع:", sale.employeeName)
                    }
                    ReceiptRow("طريقة الدفع:", sale.paymentType, isBold = true)

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Item Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SlateBg,
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sale.productName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SlateTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(sale.unitSellPrice * sale.quantity, currency),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SlateTextPrimary
                                )
                            }
                            Text(
                                text = "${sale.color} - ${sale.size} • ${sale.quantity} ${sale.unit} × ${FormatUtils.formatNumber(sale.unitSellPrice)}",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }

                    if (sale.discount > 0) {
                        ReceiptRow("الخصم الممنوح:", "- " + FormatUtils.formatCurrency(sale.discount, currency), color = FintechRose, isBold = true)
                    }
                    if (sale.cashAmount > 0.01 && (sale.transferAmount > 0.01 || sale.debtAmount > 0.01)) {
                        ReceiptRow("المدفوع كاش:", FormatUtils.formatCurrency(sale.cashAmount, currency))
                    }
                    if (sale.transferAmount > 0.01) {
                        ReceiptRow(
                            "تحويل (${sale.exchangeAccountName.ifBlank { "صراف" }}):",
                            FormatUtils.formatCurrency(sale.transferAmount, currency)
                        )
                    }
                    if (sale.debtAmount > 0.01) {
                        ReceiptRow("المبلغ الآجل (دين):", FormatUtils.formatCurrency(sale.debtAmount, currency), color = FintechRose, isBold = true)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FintechEmeraldBg,
                        border = BorderStroke(1.dp, FintechEmeraldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الصافي الإجمالي:",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = FintechEmerald)
                            )
                            Text(
                                text = FormatUtils.formatCurrency(sale.totalAmount, currency),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FintechEmerald
                                )
                            )
                        }
                    }

                    if (settings?.showProfitInReports != false) {
                        Surface(
                            color = SlateBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الربح التقديري:",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "+ " + FormatUtils.formatCurrency(sale.profit, currency),
                                    style = MaterialTheme.typography.bodySmall.copy(color = FintechEmerald, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReceiptRow(label: String, value: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
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
        notes: String,
        timestamp: Long
    ) -> Unit
) {
    var selectedParty by remember { mutableStateOf(initialParty) }
    var payerName by remember { mutableStateOf(initialParty?.name ?: "") }
    var amountStr by remember { mutableStateOf("") }
    var voucherNumber by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقداً") }
    var selectedAccount by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    var receiptTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val matchedDebtTotal = remember(selectedParty, payerName, customerDebts) {
        val nameToMatch = (selectedParty?.name ?: payerName).trim()
        if (nameToMatch.isBlank()) 0.0
        else customerDebts
            .filter { it.type == "CUSTOMER" && !it.isSettled && it.partyName.trim().equals(nameToMatch, ignoreCase = true) }
            .sumOf { it.remainingAmount }
    }

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
    val canConfirm = amountVal > 0.0 && (paymentMethod == "نقداً" || selectedAccount != null)

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "تحصيل زلط (سند قبض)",
        subtitle = "توثيق استلام نقدي أو حوالة من عميل أو تحصيل عام",
        icon = Icons.Default.Payments,
        accentColor = FintechEmerald,
        confirmText = "تأكيد التحصيل",
        confirmIcon = Icons.Default.CheckCircle,
        confirmColor = FintechEmerald,
        confirmEnabled = canConfirm,
        confirmTestTag = "collection_confirm_button",
        onConfirm = {
            onConfirm(
                payerName,
                selectedParty,
                amountVal,
                voucherNumber,
                paymentMethod,
                if (paymentMethod == "نقداً") null else selectedAccount,
                notes,
                receiptTimestamp
            )
        }
    ) {
        if (customerParties.isNotEmpty()) {
            DialogSectionHeader(
                title = "اختر العميل أو اكتب اسمه (أو اتركه فارغاً للتحصيل العام)",
                icon = Icons.Default.People,
                accentColor = FintechEmerald
            )
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
                        label = { Text(party.name, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FintechEmeraldBg,
                            selectedLabelColor = FintechEmerald
                        )
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
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(FintechEmerald),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("collection_payer_name_input")
        )

        if (matchedDebtTotal > 0.0) {
            DialogInfoBanner(
                title = "الدين المستحق على العميل:",
                value = FormatUtils.formatCurrency(matchedDebtTotal),
                containerColor = FintechAmberBg,
                contentColor = FintechAmber,
                borderColor = FintechAmberBorder,
                icon = Icons.Default.AccountBalanceWallet
            )
        }

        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = { Text("المبلغ المحصل *") },
            placeholder = { Text("أدخل المبلغ بالريال") },
            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(FintechEmerald),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("collection_amount_input")
        )

        if (matchedDebtTotal > 0.0 && amountVal > 0.0) {
            val afterDebt = (matchedDebtTotal - amountVal).coerceAtLeast(0.0)
            val surplus = (amountVal - matchedDebtTotal).coerceAtLeast(0.0)
            DialogInfoBanner(
                title = if (surplus > 0.01) "سداد كامل مع فائض:" else "المتبقي على العميل بعد التحصيل:",
                value = if (surplus > 0.01) "0 (فائض ${FormatUtils.formatCurrency(surplus)})" else FormatUtils.formatCurrency(afterDebt),
                containerColor = FintechEmeraldBg,
                contentColor = FintechEmerald,
                borderColor = FintechEmeraldBorder,
                icon = Icons.Default.Calculate
            )
        }

        OutlinedTextField(
            value = voucherNumber,
            onValueChange = { voucherNumber = it },
            label = { Text("رقم السند (اختياري)") },
            placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SND-0001)") },
            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfGoldDark, modifier = Modifier.size(18.dp)) },
            supportingText = {
                Text(
                    "إذا وُجد رقم سند أدخله هنا، وإذا تُرك فارغاً يتم توليد رقم سند تلقائي",
                    fontSize = 11.sp,
                    color = SlateTextSecondary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("collection_voucher_number_input")
        )

        DialogSectionHeader(
            title = "جهة استلام المبلغ:",
            icon = Icons.Default.AccountBalance,
            accentColor = PdfHeaderNavy
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogSegmentedChoice(
                selected = paymentMethod == "نقداً",
                label = "نقداً للصندوق",
                icon = Icons.Default.PointOfSale,
                selectedColor = FintechEmerald,
                onClick = { paymentMethod = "نقداً" },
                modifier = Modifier.weight(1f).testTag("collection_method_cash")
            )
            DialogSegmentedChoice(
                selected = paymentMethod == "صرافة/محفظة",
                label = "صرافة / محفظة",
                icon = Icons.Default.AccountBalance,
                selectedColor = FintechIndigo,
                onClick = { paymentMethod = "صرافة/محفظة" },
                modifier = Modifier.weight(1f).testTag("collection_method_exchange")
            )
        }

        if (paymentMethod == "صرافة/محفظة") {
            if (exchangeAccounts.isEmpty()) {
                DialogInfoBanner(
                    title = "لا توجد حسابات صرافة مضافة بعد — أضف حساباً أو استلم نقداً",
                    value = "",
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.Warning
                )
            } else {
                Text("اختر حساب الصرافة / المحفظة:", fontSize = 11.sp, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccount?.id == acc.id,
                            onClick = { selectedAccount = acc },
                            label = { Text(acc.name, fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechIndigoBg,
                                selectedLabelColor = FintechIndigo
                            )
                        )
                    }
                }
            }
        }

        TransactionDatePickerField(
            selectedTimestamp = receiptTimestamp,
            onDateSelected = { receiptTimestamp = it },
            label = "تاريخ السند / التحصيل",
            testTag = "collection_date_picker_field"
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات / البيان (اختياري)") },
            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CollectionSuccessDialog(
    receipt: CollectionReceiptResult,
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "تم توثيق سند القبض بنجاح!",
        subtitle = "رقم السند #${receipt.voucherNumber}",
        icon = Icons.Default.CheckCircle,
        accentColor = FintechEmerald,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = InvoiceHelper.generateCollectionVoucherPdf(context, receipt, settings)
                        InvoiceHelper.printPdfFile(context, file, "سند_قبض_${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PdfHeaderNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val file = InvoiceHelper.generateCollectionVoucherPdf(context, receipt, settings)
                        InvoiceHelper.sharePdfFile(context, file, "مشاركة سند قبض #${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
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
                        val txt = StatementHelper.formatCollectionVoucherText(receipt, settings)
                        StatementHelper.shareText(context, txt, "مشاركة سند قبض #${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, FintechEmeraldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FintechEmeraldBg, contentColor = FintechEmerald),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("share_collection_text_button")
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة واتساب", color = FintechEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SlateTextSecondary),
                    modifier = Modifier
                        .weight(0.8f)
                        .heightIn(min = 48.dp)
                        .testTag("close_collection_receipt_button")
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = FintechEmeraldBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FintechEmeraldBorder),
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
    }
}

@Composable
fun SupplierPaymentDialog(
    supplierParties: List<PartyAccount>,
    supplierDebts: List<DebtRecord>,
    exchangeAccounts: List<ExchangeAccount>,
    initialParty: PartyAccount? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        supplierName: String,
        party: PartyAccount?,
        amount: Double,
        voucherNumber: String,
        paymentMethod: String,
        exchangeAccount: ExchangeAccount?,
        notes: String,
        timestamp: Long
    ) -> Unit
) {
    var selectedParty by remember { mutableStateOf(initialParty) }
    var supplierName by remember { mutableStateOf(initialParty?.name ?: "") }
    var amountStr by remember { mutableStateOf("") }
    var voucherNumber by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقداً") }
    var selectedAccount by remember { mutableStateOf(exchangeAccounts.firstOrNull()) }
    var notes by remember { mutableStateOf("") }
    var paymentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val matchedDebtTotal = remember(selectedParty, supplierName, supplierDebts) {
        val nameToMatch = (selectedParty?.name ?: supplierName).trim()
        if (nameToMatch.isBlank()) 0.0
        else supplierDebts
            .filter { it.type == "SUPPLIER" && !it.isSettled && it.partyName.trim().equals(nameToMatch, ignoreCase = true) }
            .sumOf { it.remainingAmount }
    }

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
    val canConfirm = amountVal > 0.0 && supplierName.isNotBlank() && (paymentMethod == "نقداً" || selectedAccount != null)

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "تسديد لمورد (سند صرف)",
        subtitle = "تسديد مستحقات مورد أو تسجيل دفعة مقدمة",
        icon = Icons.Default.LocalShipping,
        accentColor = FintechRose,
        confirmText = "تأكيد الصرف والتسديد",
        confirmIcon = Icons.Default.CheckCircle,
        confirmColor = FintechRose,
        confirmEnabled = canConfirm,
        confirmTestTag = "supplier_pay_confirm_button",
        onConfirm = {
            onConfirm(
                supplierName,
                selectedParty,
                amountVal,
                voucherNumber,
                paymentMethod,
                if (paymentMethod == "نقداً") null else selectedAccount,
                notes,
                paymentTimestamp
            )
        }
    ) {
        if (supplierParties.isNotEmpty()) {
            DialogSectionHeader(
                title = "اختر المورد المسجل أو اكتب اسمه أدناه",
                icon = Icons.Default.Storefront,
                accentColor = FintechRose
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(supplierParties) { party ->
                    val isSelected = selectedParty?.id == party.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selectedParty = null
                                supplierName = ""
                            } else {
                                selectedParty = party
                                supplierName = party.name
                            }
                        },
                        label = { Text(party.name, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FintechRoseBg,
                            selectedLabelColor = FintechRose
                        )
                    )
                }
            }
        }

        OutlinedTextField(
            value = supplierName,
            onValueChange = {
                supplierName = it
                if (selectedParty?.name != it) selectedParty = null
            },
            label = { Text("اسم المورد *") },
            placeholder = { Text("مثال: شركة الأندلس للتجارة") },
            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = FintechRose, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(FintechRose),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supplier_pay_name_input")
        )

        if (matchedDebtTotal > 0.0) {
            DialogInfoBanner(
                title = "المستحق للمورد بذمتنا:",
                value = FormatUtils.formatCurrency(matchedDebtTotal),
                containerColor = FintechRoseBg,
                contentColor = FintechRose,
                borderColor = FintechRoseBorder,
                icon = Icons.Default.AccountBalanceWallet
            )
        }

        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = { Text("المبلغ المسدد *") },
            placeholder = { Text("أدخل المبلغ بالريال") },
            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = FintechRose, modifier = Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(FintechRose),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supplier_pay_amount_input")
        )

        if (matchedDebtTotal > 0.0 && amountVal > 0.0) {
            val remainingSupplierAfter = (matchedDebtTotal - amountVal).coerceAtLeast(0.0)
            DialogInfoBanner(
                title = "المتبقي للمورد بعد السداد:",
                value = FormatUtils.formatCurrency(remainingSupplierAfter),
                containerColor = FintechEmeraldBg,
                contentColor = FintechEmerald,
                borderColor = FintechEmeraldBorder,
                icon = Icons.Default.Calculate
            )
        }
        if (paymentMethod != "نقداً" && selectedAccount != null && amountVal > 0.0) {
            val accAfter = selectedAccount!!.balance - amountVal
            val sufficient = accAfter >= -0.001
            DialogInfoBanner(
                title = "رصيد (${selectedAccount!!.name}) بعد الصرف:",
                value = FormatUtils.formatCurrency(accAfter),
                containerColor = if (sufficient) FintechIndigoBg else FintechRoseBg,
                contentColor = if (sufficient) FintechIndigo else FintechRose,
                borderColor = if (sufficient) FintechIndigo.copy(alpha = 0.35f) else FintechRoseBorder,
                icon = if (sufficient) Icons.Default.AccountBalance else Icons.Default.Warning
            )
        }

        OutlinedTextField(
            value = voucherNumber,
            onValueChange = { voucherNumber = it },
            label = { Text("رقم السند (اختياري)") },
            placeholder = { Text("اتركه فارغاً للتوليد التلقائي (SRF-0001)") },
            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = PdfGoldDark, modifier = Modifier.size(18.dp)) },
            supportingText = {
                Text(
                    "إذا وُجد رقم سند صرف أدخله هنا، وإذا تُرك فارغاً يتم توليده تلقائياً",
                    fontSize = 11.sp,
                    color = SlateTextSecondary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supplier_pay_voucher_input")
        )

        DialogSectionHeader(
            title = "جهة الصرف والدفع:",
            icon = Icons.Default.AccountBalance,
            accentColor = PdfHeaderNavy
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogSegmentedChoice(
                selected = paymentMethod == "نقداً",
                label = "نقداً من الصندوق",
                icon = Icons.Default.PointOfSale,
                selectedColor = FintechRose,
                onClick = { paymentMethod = "نقداً" },
                modifier = Modifier.weight(1f).testTag("supplier_pay_method_cash")
            )
            DialogSegmentedChoice(
                selected = paymentMethod == "صرافة/محفظة",
                label = "صرافة / محفظة",
                icon = Icons.Default.AccountBalance,
                selectedColor = FintechIndigo,
                onClick = { paymentMethod = "صرافة/محفظة" },
                modifier = Modifier.weight(1f).testTag("supplier_pay_method_exchange")
            )
        }

        if (paymentMethod == "صرافة/محفظة") {
            if (exchangeAccounts.isEmpty()) {
                DialogInfoBanner(
                    title = "لا توجد حسابات صرافة مضافة بعد — أضف حساباً أو ادفع نقداً",
                    value = "",
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.Warning
                )
            } else {
                Text("اختر حساب الصرافة / المحفظة:", fontSize = 11.sp, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exchangeAccounts) { acc ->
                        FilterChip(
                            selected = selectedAccount?.id == acc.id,
                            onClick = { selectedAccount = acc },
                            label = { Text("${acc.name} (${FormatUtils.formatNumber(acc.balance)})", fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FintechIndigoBg,
                                selectedLabelColor = FintechIndigo
                            )
                        )
                    }
                }
            }
        }

        TransactionDatePickerField(
            selectedTimestamp = paymentTimestamp,
            onDateSelected = { paymentTimestamp = it },
            label = "تاريخ سند الصرف / السداد",
            testTag = "supplier_pay_date_picker_field"
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات / البيان (اختياري)") },
            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supplier_pay_notes_input")
        )
    }
}

@Composable
fun SupplierPaymentSuccessDialog(
    receipt: SupplierPaymentReceiptResult,
    settings: StoreSettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = "تم توثيق سند الصرف للمورد!",
        subtitle = "رقم السند #${receipt.voucherNumber}",
        icon = Icons.Default.CheckCircle,
        accentColor = FintechRose,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = InvoiceHelper.generateSupplierPaymentVoucherPdf(context, receipt, settings)
                        InvoiceHelper.printPdfFile(context, file, "سند_صرف_${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PdfHeaderNavy),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val file = InvoiceHelper.generateSupplierPaymentVoucherPdf(context, receipt, settings)
                        InvoiceHelper.sharePdfFile(context, file, "مشاركة سند صرف #${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
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
                        val txt = StatementHelper.formatSupplierPaymentVoucherText(receipt, settings)
                        StatementHelper.shareText(context, txt, "مشاركة سند صرف #${receipt.voucherNumber}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, FintechEmeraldBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FintechEmeraldBg, contentColor = FintechEmerald),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("share_supplier_payment_text_button")
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = FintechEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مشاركة واتساب", color = FintechEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SlateTextSecondary),
                    modifier = Modifier
                        .weight(0.8f)
                        .heightIn(min = 48.dp)
                        .testTag("close_supplier_payment_receipt_button")
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = FintechRoseBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, FintechRoseBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "رقم السند: #${receipt.voucherNumber}",
                        fontWeight = FontWeight.Bold,
                        color = FintechRose,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                HorizontalDivider(color = SlateBorder)
                ReceiptRow("صرفنا إلى المورد:", receipt.supplierName, isBold = true)
                ReceiptRow("طريقة الصرف:", if (receipt.paymentMethod == "نقداً") "نقداً من الصندوق" else "صرافة (${receipt.exchangeAccountName})")
                if (receipt.appliedToDebtAmount > 0.0) {
                    ReceiptRow("مسدد من الدين:", FormatUtils.formatCurrency(receipt.appliedToDebtAmount), color = FintechEmerald, isBold = true)
                }
                if (receipt.directPaymentAmount > 0.0) {
                    ReceiptRow("دفعة مباشرة / مقدمة:", FormatUtils.formatCurrency(receipt.directPaymentAmount))
                }
                ReceiptRow(
                    "المتبقي للمورد بذمتنا:",
                    FormatUtils.formatCurrency(receipt.remainingSupplierDebt),
                    color = if (receipt.remainingSupplierDebt > 0) FintechRose else FintechEmerald,
                    isBold = true
                )
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
                    Text("المبلغ المسدد:", fontWeight = FontWeight.Bold)
                    Text(
                        FormatUtils.formatCurrency(receipt.amount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = FintechRose)
                    )
                }
            }
        }
    }
}

@Composable
fun PinVerificationDialog(
    viewModel: StoreViewModel,
    title: String = "التحقق من رمز المدير (PIN)",
    subtitle: String = "هذه العملية تتطلب تأكيد صلاحية المدير",
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    StoreOperationDialog(
        onDismissRequest = onDismiss,
        title = if (isPinConfigured) title else "تعيين رمز المدير (PIN) أولاً",
        subtitle = if (isPinConfigured) subtitle else "يجب إنشاء رمز حماية قوي قبل تنفيذ العمليات الحساسة",
        icon = Icons.Default.Lock,
        accentColor = PdfHeaderNavy,
        confirmText = if (isPinConfigured) "تأكيد ومتابعة" else "حفظ الرمز والمتابعة",
        confirmIcon = Icons.Default.VerifiedUser,
        confirmColor = PdfHeaderNavy,
        confirmEnabled = !isBusy && pinInput.isNotBlank() && (isPinConfigured || confirmPinInput.isNotBlank()),
        confirmTestTag = "pin_verify_confirm_button",
        onConfirm = {
            if (isPinConfigured) {
                isBusy = true
                errorMsg = null
                viewModel.verifyManagerPin(pinInput) { result ->
                    isBusy = false
                    when (result) {
                        is PinVerificationResult.Success -> onVerified()
                        is PinVerificationResult.Invalid -> {
                            errorMsg = "رمز المدير غير صحيح! المحاولات المتبقية: ${result.remainingAttempts}"
                        }
                        is PinVerificationResult.Locked -> {
                            errorMsg = "تم إيقاف المحاولات مؤقتاً لمدة ${result.remainingSeconds} ثانية بسبب تكرار الخطأ"
                        }
                        PinVerificationResult.NotConfigured -> {
                            errorMsg = "لم يتم تعيين رمز مدير قوي بعد"
                        }
                    }
                }
            } else {
                val strengthErr = PinSecurityManager.validatePinStrength(pinInput)
                if (strengthErr != null) {
                    errorMsg = strengthErr
                } else if (pinInput.trim() != confirmPinInput.trim()) {
                    errorMsg = "الرمزان غير متطابقين"
                } else {
                    isBusy = true
                    errorMsg = null
                    viewModel.updateManagerPin(currentPin = null, newPin = pinInput.trim()) { ok, err ->
                        isBusy = false
                        if (ok) onVerified() else errorMsg = err
                    }
                }
            }
        }
    ) {
        if (!isPinConfigured) {
            DialogInfoBanner(
                title = "تأمين الحسابات",
                value = "اختر رمزاً مكوناً من 4 أرقام أو أكثر (يُمنع 1234 أو الأرقام المكررة)",
                containerColor = FintechAmberBg,
                contentColor = FintechAmber,
                borderColor = FintechAmberBorder,
                icon = Icons.Default.Shield
            )
        }

        OutlinedTextField(
            value = pinInput,
            onValueChange = {
                pinInput = it
                errorMsg = null
            },
            label = { Text(if (isPinConfigured) "أدخل رمز المدير (PIN)" else "رمز المدير الجديد (PIN) *") },
            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null, tint = PdfHeaderNavy) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = storeDialogTextFieldColors(PdfHeaderNavy),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pin_verify_input")
        )

        if (!isPinConfigured) {
            OutlinedTextField(
                value = confirmPinInput,
                onValueChange = {
                    confirmPinInput = it
                    errorMsg = null
                },
                label = { Text("تأكيد رمز المدير الجديد *") },
                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PdfHeaderNavy) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_verify_confirm_input")
            )
        }

        errorMsg?.let { err ->
            DialogInfoBanner(
                title = "خطأ في التحقق:",
                value = err,
                containerColor = FintechRoseBg,
                contentColor = FintechRose,
                borderColor = FintechRoseBorder,
                icon = Icons.Default.ErrorOutline
            )
        }
    }
}

