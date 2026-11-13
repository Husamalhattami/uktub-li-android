package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExchangeAccount
import com.example.data.model.ProductItem
import com.example.ui.StoreViewModel
import com.example.ui.components.QuickThousandAdder
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.FormatUtils

@Composable
fun StoreAuditScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val currentCashBalance by viewModel.currentCashBalance.collectAsStateWithLifecycle()
    val exchangeAccounts by viewModel.exchangeAccounts.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    // State for Cash Drawer Audit
    var actualCashDrawerStr by remember(currentCashBalance) {
        mutableStateOf(currentCashBalance.toInt().coerceAtLeast(0).toString())
    }

    // State for Exchange Accounts Audit (AccountId -> String value)
    val exchangeBalancesState = remember(exchangeAccounts) {
        mutableStateMapOf<Long, String>().apply {
            exchangeAccounts.forEach { acc ->
                put(acc.id, acc.balance.toInt().toString())
            }
        }
    }

    // Reset transactions toggle
    var resetTransactionsFromZero by remember { mutableStateOf(true) }
    var auditNotes by remember { mutableStateOf("جرد فعلي شامل لبداية النظام") }

    // Dialog confirmation state
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmWordInput by remember { mutableStateOf("") }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    // Product edit in audit
    var productToAdjust by remember { mutableStateOf<ProductItem?>(null) }
    var newQtyStr by remember { mutableStateOf("") }

    val actualCash = actualCashDrawerStr.toDoubleOrNull() ?: 0.0
    val totalExchangeBalances = exchangeAccounts.sumOf { acc ->
        exchangeBalancesState[acc.id]?.toDoubleOrNull() ?: 0.0
    }
    val totalStoreCapital = actualCash + totalExchangeBalances

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "معالج جرد المحل وبداية الحسابات",
                showBackButton = true,
                activeShift = activeShift,
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Header Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(LuxuryGold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = LuxuryGoldLight,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "جرد المحل الشامل والبدء من الصفر",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "عدّ نقدية الصندوق، وأرصدة الصرافين، والمخزون واعتماد البداية",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إجمالي السيولة المالية بعد الجرد:",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalStoreCapital),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        color = LuxuryGoldLight,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldProfit.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "كاش: ${FormatUtils.formatCurrency(actualCash)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Step 1: نقدية الصندوق (الدرج)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = EmeraldProfit)
                            Text(
                                text = "1. نقدية الصندوق (الكاش بالدرج)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الرصيد الحالي بالدفتر: ${FormatUtils.formatCurrency(currentCashBalance)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                        }

                        OutlinedTextField(
                            value = actualCashDrawerStr,
                            onValueChange = { actualCashDrawerStr = it },
                            label = { Text("الرصيد الفعلي بعد عدّ الكاش باليد (ريال)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audit_actual_cash_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        QuickThousandAdder(
                            currentValue = actualCashDrawerStr,
                            onValueChange = { actualCashDrawerStr = it }
                        )

                        Text(
                            text = "هذا المبلغ سيعتمد كرصيد بداية رسمي للصندوق النقدي.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                        )
                    }
                }
            }

            // Step 2: أرصدة الصرافين والمحافظ
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF0D9488))
                                Text(
                                    text = "2. أرصدة الصرافين والمحافظ الإلكترونية",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            IconButton(onClick = { showAddAccountDialog = true }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "إضافة صراف", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(
                            text = "سجل المبلغ الموجود حالياً في حساب كل صراف أو محفظة (الكريمي، النجم، العمقي...):",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )

                        if (exchangeAccounts.isEmpty()) {
                            Text(
                                text = "لم تقم بإضافة حسابات صرافة بعد. اضغط (+) لإضافة الكريمي أو غيره.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted)
                            )
                        } else {
                            exchangeAccounts.forEach { acc ->
                                val currentVal = exchangeBalancesState[acc.id] ?: "0"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = acc.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "رصيده القديم بالدفتر: ${FormatUtils.formatCurrency(acc.balance)}",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0D9488).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = acc.type,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFF0D9488),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = currentVal,
                                            onValueChange = { exchangeBalancesState[acc.id] = it },
                                            label = { Text("الرصيد الفعلي الآن بحساب ${acc.name} (ريال)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("audit_acc_balance_${acc.id}"),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        QuickThousandAdder(
                                            currentValue = currentVal,
                                            onValueChange = { exchangeBalancesState[acc.id] = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: جرد بضاعة المحل (المخزون الفعلي)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Checkroom, contentDescription = null, tint = LuxuryGold)
                            Text(
                                text = "3. ملخص بضاعة ومخزون المحل",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        val totalPieces = products.sumOf { it.quantity }
                        val totalInvValue = products.sumOf { it.buyPrice * it.quantity }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي القطع بالمحل:", style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted))
                            Text("$totalPieces حبه (${products.size} أصناف)", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("قيمة البضاعة بسعر الشراء:", style = MaterialTheme.typography.bodyMedium.copy(color = SlateMuted))
                            Text(FormatUtils.formatCurrency(totalInvValue), fontWeight = FontWeight.Bold, color = LuxuryGold)
                        }

                        Text(
                            text = "الأصناف الحالية المسجلة (يمكنك تعديل كمية أي صنف ليطابق الجرد الفعلي):",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                        )

                        products.take(6).forEach { prod ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.modelName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "${prod.category} | ${prod.size} | ${prod.color}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted, fontSize = 11.sp)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${prod.quantity} حبه",
                                            fontWeight = FontWeight.Bold,
                                            color = if (prod.quantity <= 3) RubyExpense else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                productToAdjust = prod
                                                newQtyStr = prod.quantity.toString()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل كمية الجرد", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (products.size > 6) {
                            Text(
                                text = "+ ويوجد ${products.size - 6} أصناف أخرى بالمخزون",
                                style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                            )
                        }
                    }
                }
            }

            // Step 4: خيار البدء من الصفر
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بدء النظام من الصفر بعد الجرد",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (resetTransactionsFromZero) RubyExpense else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "تصفير وحذف العمليات السابقة والبدء برصيد افتتاحي نظيف",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                                )
                            }
                            Switch(
                                checked = resetTransactionsFromZero,
                                onCheckedChange = { resetTransactionsFromZero = it },
                                modifier = Modifier.testTag("toggle_reset_from_zero")
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (resetTransactionsFromZero) RubyLight else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (resetTransactionsFromZero) {
                                    "سيتم مسح حركات البيع والشراء والمصروفات القديمة التجريبية، واعتماد الأرصدة والمخزون أعلاه كأرصدة افتتاحية رسمية للمحل."
                                } else {
                                    "سيتم الاحتفاظ بالعمليات السابقة مع إضافة قيود تسوية للأرصدة الفعلية الجديدة."
                                },
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (resetTransactionsFromZero) RubyExpense else SlateMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        OutlinedTextField(
                            value = auditNotes,
                            onValueChange = { auditNotes = it },
                            label = { Text("ملاحظات الجرد / بيان الافتتاح") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Confirm Button
            item {
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_audit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (resetTransactionsFromZero) EmeraldProfit else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (resetTransactionsFromZero) "اعتماد الجرد وبدء النظام من الصفر" else "اعتماد وتسوية الأرصدة بالجرد",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        }
    }

    // Confirmation Alert Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = if (resetTransactionsFromZero) Icons.Default.RestartAlt else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (resetTransactionsFromZero) RubyExpense else EmeraldProfit,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (resetTransactionsFromZero) "تأكيد اعتماد الجرد وبدء النظام من الصفر" else "تأكيد تسوية الأرصدة",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ملخص الأرصدة الافتتاحية المعتمدة للجرد:", fontWeight = FontWeight.Bold)
                    Text("• كاش الصندوق (الدرج): ${FormatUtils.formatCurrency(actualCash)}")
                    exchangeAccounts.forEach { acc ->
                        val b = exchangeBalancesState[acc.id]?.toDoubleOrNull() ?: 0.0
                        Text("• حساب ${acc.name}: ${FormatUtils.formatCurrency(b)}")
                    }
                    Text("• إجمالي السيولة النقدية: ${FormatUtils.formatCurrency(totalStoreCapital)}", fontWeight = FontWeight.Bold, color = EmeraldProfit)

                    if (resetTransactionsFromZero) {
                        Surface(
                            color = RubyLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "تنبيه هام: سيتم مسح فواتير البيع والشراء والمصروفات والديون السابقة للبدء بسجل نظيف تماماً من الصفر.",
                                color = RubyExpense,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Text(
                            text = "للتأكيد النهائي، يرجى كتابة كلمة «تصفير» في المربع أدناه:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = RubyExpense)
                        )

                        OutlinedTextField(
                            value = confirmWordInput,
                            onValueChange = { confirmWordInput = it },
                            placeholder = { Text("اكتب تصفير") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audit_confirm_word_input")
                        )
                    }
                }
            },
            confirmButton = {
                val isWordValid = !resetTransactionsFromZero || (confirmWordInput.trim() == "تصفير" || confirmWordInput.trim() == "حذف")
                Button(
                    onClick = {
                        val exchangeMap = exchangeAccounts.associate { acc ->
                            acc.id to (exchangeBalancesState[acc.id]?.toDoubleOrNull() ?: 0.0)
                        }
                        viewModel.executeStoreFullAudit(
                            actualDrawerCash = actualCash,
                            exchangeBalances = exchangeMap,
                            resetTransactions = resetTransactionsFromZero,
                            notes = auditNotes,
                            onSuccess = {
                                showConfirmDialog = false
                                onBack()
                            }
                        )
                    },
                    enabled = isWordValid,
                    colors = ButtonDefaults.buttonColors(containerColor = if (resetTransactionsFromZero) RubyExpense else EmeraldProfit),
                    modifier = Modifier.testTag("confirm_finalize_audit_button")
                ) {
                    Text(if (resetTransactionsFromZero) "نعم، اعتمد وابدأ من الصفر" else "نعم، اعتمد الأرصدة")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }) {
                    Text("إلغاء وتعديل")
                }
            }
        )
    }

    // Adjust Product Stock Dialog
    productToAdjust?.let { prod ->
        AlertDialog(
            onDismissRequest = { productToAdjust = null },
            title = { Text("تعديل كمية الجرد للصنف", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(prod.modelName, fontWeight = FontWeight.Bold)
                    Text("الكمية الحالية بالدفتر: ${prod.quantity} حبه", color = SlateMuted)
                    OutlinedTextField(
                        value = newQtyStr,
                        onValueChange = { newQtyStr = it },
                        label = { Text("الكمية الفعلية بعد العد (حبه)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newQtyStr.toIntOrNull() ?: prod.quantity
                        viewModel.saveProduct(prod.copy(quantity = qty)) {
                            productToAdjust = null
                        }
                    }
                ) {
                    Text("تحديث الكمية")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { productToAdjust = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Exchange Account Dialog
    if (showAddAccountDialog) {
        val dynamicAccountTypes by viewModel.accountTypes.collectAsStateWithLifecycle()
        var newAccName by remember { mutableStateOf("") }
        var newAccType by remember { mutableStateOf(dynamicAccountTypes.firstOrNull() ?: "صراف") }
        var newAccNumber by remember { mutableStateOf("") }
        var newAccBalanceStr by remember { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("إضافة حساب صراف أو محفظة جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newAccName,
                        onValueChange = { newAccName = it },
                        label = { Text("اسم الصراف أو المحفظة (الكريمي، النجم، جوالي...)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dynamicAccountTypes) { typeName ->
                            FilterChip(
                                selected = newAccType == typeName,
                                onClick = { newAccType = typeName },
                                label = { Text(typeName, maxLines = 1) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newAccNumber,
                        onValueChange = { newAccNumber = it },
                        label = { Text("رقم الحساب / الهاتف (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newAccBalanceStr,
                        onValueChange = { newAccBalanceStr = it },
                        label = { Text("الرصيد الفعلي الحالي (ريال)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = newAccBalanceStr.toDoubleOrNull() ?: 0.0
                        viewModel.saveExchangeAccount(
                            ExchangeAccount(
                                name = newAccName,
                                type = newAccType,
                                accountNumber = newAccNumber,
                                balance = bal
                            )
                        ) {
                            showAddAccountDialog = false
                        }
                    }
                ) {
                    Text("حفظ الحساب")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddAccountDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
