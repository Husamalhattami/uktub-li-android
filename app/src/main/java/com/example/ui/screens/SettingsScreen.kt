package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AuditLogRecord
import com.example.data.model.StoreSettings
import com.example.ui.StoreViewModel
import com.example.ui.components.StoreTopBar
import com.example.ui.theme.*
import com.example.ui.util.BackupManager
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: StoreViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val settingsState by viewModel.storeSettings.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    var storeName by remember(settingsState) { mutableStateOf(settingsState?.storeName ?: "الجرادي للملابس الجاهزة") }
    var storeNameEn by remember(settingsState) { mutableStateOf(settingsState?.storeNameEn ?: "Al-Jaradi Ready-to-Wear") }
    var phone by remember(settingsState) { mutableStateOf(settingsState?.phoneNumber ?: "778383570") }
    var address by remember(settingsState) { mutableStateOf(settingsState?.address ?: "صنعاء - اليمن") }
    var currency by remember(settingsState) { mutableStateOf(settingsState?.currencySymbol ?: "ريال") }
    var taxNumber by remember(settingsState) { mutableStateOf(settingsState?.taxNumber ?: "") }
    var invoiceHeader by remember(settingsState) { mutableStateOf(settingsState?.invoiceHeader ?: "الجرادي للملابس الجاهزة") }
    var invoiceFooter by remember(settingsState) { mutableStateOf(settingsState?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية") }
    var managerPin by remember(settingsState) { mutableStateOf(settingsState?.managerPin ?: "1234") }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var isCreatingBackup by remember { mutableStateOf(false) }
    var showWipeAllDialog by remember { mutableStateOf(false) }
    var showWipeTransactionsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StoreTopBar(
                title = "إعدادات المحل والنسخ الاحتياطي",
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
                    .fillMaxHeight()
                    .widthIn(max = 860.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. Header Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = LuxuryGoldLight,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "بيانات المحل والفاتورة الرسمية",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "تخصيص اسم المتجر، الترويسة، سياسة التبديل، والنسخ الاحتياطي",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }

            // 2. Store Profile Card
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
                        Text(
                            text = "بيانات المنشأة التجارية",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("اسم المحل (بالعربي)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_store_name_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = storeNameEn,
                            onValueChange = { storeNameEn = it },
                            label = { Text("اسم المحل بالإنجليزية (اختياري)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("رقم الهاتف") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currency,
                                onValueChange = { currency = it },
                                label = { Text("رمز العملة") },
                                modifier = Modifier.weight(0.7f),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("العنوان / الموقع") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = taxNumber,
                            onValueChange = { taxNumber = it },
                            label = { Text("السجل التجاري / الرقم الضريبي (اختياري)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = invoiceHeader,
                            onValueChange = { invoiceHeader = it },
                            label = { Text("ترويسة الفاتورة (ترحيب)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = invoiceFooter,
                            onValueChange = { invoiceFooter = it },
                            label = { Text("تذييل الفاتورة (سياسة الاستبدال والاسترجاع)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        OutlinedTextField(
                            value = managerPin,
                            onValueChange = { managerPin = it },
                            label = { Text("رمز PIN للمدير") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val updated = StoreSettings(
                                    id = 1,
                                    shopNameAr = storeName.trim().ifBlank { "الجرادي للملابس الجاهزة" },
                                    shopNameEn = storeNameEn.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    currency = currency.trim().ifBlank { "ريال" },
                                    taxNumber = taxNumber.trim(),
                                    invoiceHeader = invoiceHeader.trim(),
                                    invoiceFooter = invoiceFooter.trim(),
                                    managerPin = managerPin.trim().ifBlank { "1234" }
                                )
                                viewModel.saveSettings(updated)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_settings_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ التعديلات", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Backup and Restore Card
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
                        Text(
                            text = "النسخ الاحتياطي واستعادة البيانات",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "احفظ نسخة من فواتيرك، الأصناف، والديون لمشاركتها أو استعادتها في أي وقت بأمان تام.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isCreatingBackup = true
                                        try {
                                            val file = BackupManager.createBackupFile(context, viewModel.getRepository())
                                            BackupManager.shareBackupFile(context, file)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل إنشاء النسخة: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isCreatingBackup = false
                                        }
                                    }
                                },
                                enabled = !isCreatingBackup,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_backup_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                            ) {
                                if (isCreatingBackup) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تصدير ومشاركة", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("import_backup_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استعادة نسخة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3.5 App Initialization & Wipe Database Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBFB)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = RubyExpense)
                            Text(
                                text = "تهيئة التطبيق وإفراغ قواعد البيانات",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RubyExpense
                                )
                            )
                        }
                        Text(
                            text = "استخدم هذه الخيارات لإفراغ التطبيق تماماً من المنتجات والأنواع السابقة أو تصفير الحركات والبدء ببيانات جديدة نظيفة.",
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                        )

                        Button(
                            onClick = { showWipeAllDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wipe_all_data_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تهيئة كاملة: إفراغ جميع المنتجات والأنواع والبيانات", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showWipeTransactionsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("wipe_transactions_only_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = RubyExpense)
                            Spacer(Modifier.width(8.dp))
                            Text("تصفير الحركات والفواتير فقط (الإبقاء على المنتجات)", color = RubyExpense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. Audit Trail Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل التدقيق ومراقبة العمليات (Audit Trail)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateHeader
                        )
                    )
                    Text(
                        text = "${auditLogs.size} عملية موثقة",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                }
            }

            // 5. Audit Log Items
            if (auditLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "لا توجد حركات مسجلة في سجل التدقيق حتى الآن.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            } else {
                items(auditLogs.take(20), key = { it.id }) { log ->
                    AuditLogRow(log = log)
                }
            }
        }
        }
    }

    // Restore Dialog
    if (showRestoreDialog) {
        var restoreWordConfirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("استعادة البيانات من نسخة احتياطية", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "الصق محتوى ملف النسخة الاحتياطية (JSON) أدناه:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        label = { Text("بيانات النسخة الاحتياطية JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8
                    )
                    Text(
                        text = "تنبيه: ستستبدل الاستعادة البيانات الحالية بالنسخة. اكتب «استعادة» للتأكيد:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RubyExpense,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    OutlinedTextField(
                        value = restoreWordConfirm,
                        onValueChange = { restoreWordConfirm = it },
                        placeholder = { Text("اكتب استعادة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val canRestore = restoreWordConfirm.trim() == "استعادة" && restoreJsonInput.isNotBlank()
                Button(
                    onClick = {
                        viewModel.restoreBackup(restoreJsonInput) { ok ->
                            if (ok) {
                                showRestoreDialog = false
                            }
                        }
                    },
                    enabled = canRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense)
                ) {
                    Text("تنفيذ الاستعادة")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showWipeAllDialog) {
        AlertDialog(
            onDismissRequest = { showWipeAllDialog = false },
            title = { Text("تأكيد تهيئة التطبيق بالكامل", fontWeight = FontWeight.Bold, color = RubyExpense) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("سيتم حذف وإفراغ جميع البيانات المخزنة في قواعد البيانات بشكل نهائي، بما في ذلك:")
                    Text("• جميع المنتجات والأصناف والأنواع\n• المبيعات والمشتريات والمصروفات\n• الصندوق والورديات والصرافين والمحافظ\n• العملاء والموردين والديون وسندات التحصيل\n• العمال وسجل التدقيق", fontSize = 12.sp, color = SlateMuted)
                    Text("هل أنت متأكد من تهيئة التطبيق وإفراغ كل شيء؟", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearData(transactionsOnly = false) {
                            showWipeAllDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_wipe_all_button")
                ) {
                    Text("نعم، إفراغ الكل")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWipeAllDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showWipeTransactionsDialog) {
        AlertDialog(
            onDismissRequest = { showWipeTransactionsDialog = false },
            title = { Text("تأكيد تصفير الحركات والفواتير", fontWeight = FontWeight.Bold, color = RubyExpense) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("سيتم حذف جميع المبيعات والمشتريات والمصروفات والديون وحركات الصندوق، مع الإبقاء على قائمة المنتجات والأطراف والحسابات.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearData(transactionsOnly = true) {
                            showWipeTransactionsDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RubyExpense),
                    modifier = Modifier.testTag("confirm_wipe_transactions_button")
                ) {
                    Text("تصفير الحركات")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWipeTransactionsDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AuditLogRow(log: AuditLogRecord) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconColor = when {
                log.action.contains("بيع") -> EmeraldProfit
                log.action.contains("شراء") -> OceanDeep
                log.action.contains("إلغاء") || log.action.contains("حذف") -> RubyExpense
                else -> LuxuryGold
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        log.action.contains("بيع") -> Icons.Default.PointOfSale
                        log.action.contains("شراء") -> Icons.Default.ShoppingCart
                        log.action.contains("إلغاء") || log.action.contains("حذف") -> Icons.Default.DeleteOutline
                        else -> Icons.Default.HistoryEdu
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.action,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = FormatUtils.formatTime(log.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                    )
                }
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.bodySmall.copy(color = SlateHeader),
                    maxLines = 2
                )
                Text(
                    text = "المنفذ: ${log.actor} • ${FormatUtils.formatDate(log.timestamp)}",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMuted)
                )
            }
        }
    }
}
