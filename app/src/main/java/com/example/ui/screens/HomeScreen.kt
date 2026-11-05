package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CashDrawerMovement
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun HomeScreen(
    viewModel: StoreViewModel,
    onNavigate: (AppScreen) -> Unit
) {
    val cashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val totalExchangeBalance by viewModel.totalExchangeBalance.collectAsStateWithLifecycle()
    val inventoryValue by viewModel.inventoryCostValue.collectAsStateWithLifecycle()
    val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()

    val totalLiquidity = cashBalance + totalExchangeBalance + inventoryValue

    Scaffold(
        topBar = {
            StoreTopBar(
                title = storeSettings?.storeName ?: "حسابي للمحل",
                subtitle = "إدارة أسهل .. لمحل أكثر نجاحاً",
                trailingAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { /* Notification modal or shortcut */ },
                            modifier = Modifier.testTag("btn_notifications_topbar")
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "الإشعارات",
                                    tint = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(PdfGoldPrimary)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onNavigate(AppScreen.SETTINGS) },
                            modifier = Modifier.testTag("btn_settings_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. Hero Liquidity Card (Page 1 in PDF)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PdfHeaderNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A63))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0F1E36),
                                        Color(0xFF162947)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Row: إجمالي السيولة + Chart Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "إجمالي السيولة",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF90CDF4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Large Number: 125,750 ريال
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = FormatUtils.formatNumber(totalLiquidity),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 32.sp
                                    )
                                )
                                Text(
                                    text = storeSettings?.currencySymbol ?: "ريال",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF90CDF4),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Bottom Two Blocks: رصيد الصندوق | قيمة المخزون
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0A1526).copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Right: رصيد الصندوق
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onNavigate(AppScreen.CASH_DRAWER) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                        .background(Color(0xFF0D9488).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFF2DD4BF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "رصيد الصندوق",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(cashBalance),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(30.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                // Left: قيمة المخزون
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                        .clickable { onNavigate(AppScreen.INVENTORY) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "قيمة المخزون",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(inventoryValue),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. The 4 Fast Action Cards (Page 1 in PDF)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1: شراء بضاعة | بيع جديد
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PdfActionCard(
                            title = "شراء بضاعة",
                            subtitle = "تسجيل فاتورة شراء",
                            icon = Icons.Default.AddShoppingCart,
                            iconColor = Color(0xFF0F766E),
                            iconBg = Color(0xFFCCFBF1),
                            cardBg = PdfTealCard,
                            borderColor = Color(0xFF99F6E4),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_purchase"),
                            onClick = { onNavigate(AppScreen.PURCHASE) }
                        )

                        PdfActionCard(
                            title = "بيع جديد",
                            subtitle = "إصدار فاتورة بيع",
                            icon = Icons.Default.AddShoppingCart,
                            iconColor = Color(0xFF15803D),
                            iconBg = Color(0xFFDCFCE7),
                            cardBg = PdfMintCard,
                            borderColor = Color(0xFFBBF7D0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_sale"),
                            onClick = { onNavigate(AppScreen.SALE) }
                        )
                    }

                    // Row 2: تحصيل دين | تسجيل مصروف
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PdfActionCard(
                            title = "تحصيل دين",
                            subtitle = "استلام مبلغ من عميل",
                            icon = Icons.Default.PriceCheck,
                            iconColor = Color(0xFF166534),
                            iconBg = Color(0xFFDCFCE7),
                            cardBg = PdfGreenCard,
                            borderColor = Color(0xFFBBF7D0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_debt"),
                            onClick = { onNavigate(AppScreen.DEBTS) }
                        )

                        PdfActionCard(
                            title = "تسجيل مصروف",
                            subtitle = "إضافة مصروف جديد",
                            icon = Icons.Default.ReceiptLong,
                            iconColor = Color(0xFF991B1B),
                            iconBg = Color(0xFFFEE2E2),
                            cardBg = PdfRoseCard,
                            borderColor = Color(0xFFFECACA),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_action_expense"),
                            onClick = { onNavigate(AppScreen.EXPENSES) }
                        )
                    }
                }
            }

            // ==========================================
            // 3. Section: آخر الحركات (Page 1 in PDF)
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "آخر الحركات",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 16.sp
                            )
                        )
                    }

                    Text(
                        text = "عرض الكل  ›",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clickable { onNavigate(AppScreen.TRANSACTIONS_LOG) }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
            }

            // Real Movement List
            if (cashMovements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = "لا توجد حركات مالية مسجلة بعد.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(cashMovements.take(6), key = { it.id }) { movement ->
                    PdfMovementRow(movement = movement)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ==========================================
// Sub-Components Matching PDF Exactly
// ==========================================

@Composable
fun PdfActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    cardBg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(105.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PdfMovementRow(movement: CashDrawerMovement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circle icon
                val (icon, iconColor, iconBg) = when {
                    movement.type.contains("SALE") -> Triple(Icons.Default.ShoppingCart, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    movement.type.contains("PURCHASE") -> Triple(Icons.Default.Inventory2, Color(0xFF2563EB), Color(0xFFDBEAFE))
                    movement.type.contains("EXPENSE") -> Triple(Icons.Default.ReceiptLong, Color(0xFFDC2626), Color(0xFFFEE2E2))
                    movement.isIncome -> Triple(Icons.Default.PriceCheck, Color(0xFF16A34A), Color(0xFFDCFCE7))
                    else -> Triple(Icons.Default.ArrowOutward, Color(0xFFDC2626), Color(0xFFFEE2E2))
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = movement.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = FormatUtils.formatDate(movement.timestamp) + " " + FormatUtils.formatTime(movement.timestamp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Text(
                text = (if (movement.isIncome) "+" else "-") + FormatUtils.formatCurrency(movement.amount),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (movement.isIncome) Color(0xFF16A34A) else Color(0xFFDC2626),
                    fontSize = 15.sp
                )
            )
        }
    }
}
