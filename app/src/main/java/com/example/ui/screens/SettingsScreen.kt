package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AuditLogRecord
import com.example.data.model.StoreConfigItem
import com.example.data.model.StoreConfigTypes
import com.example.data.model.StoreSettings
import com.example.ui.StoreViewModel
import com.example.ui.components.DialogInfoBanner
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.StoreOperationDialog
import com.example.ui.components.StoreTopBar
import com.example.ui.components.storeDialogTextFieldColors
import com.example.ui.theme.*
import com.example.ui.util.BackupManager
import com.example.ui.util.BackupPreview
import com.example.ui.util.FormatUtils
import com.example.ui.util.LogoHelper
import com.example.ui.util.PinSecurityManager
import com.example.ui.util.StoreLogoBadge
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: StoreViewModel,
    onOpenOpeningBalances: () -> Unit = { viewModel.navigateTo(com.example.ui.AppScreen.OPENING_BALANCES) },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val settingsState by viewModel.storeSettings.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val allConfigItems by viewModel.allConfigItems.collectAsStateWithLifecycle()

    var storeName by remember(settingsState) { mutableStateOf(settingsState?.storeName ?: "") }
    var storeNameEn by remember(settingsState) { mutableStateOf(settingsState?.storeNameEn ?: "") }
    var phone by remember(settingsState) { mutableStateOf(settingsState?.phoneNumber ?: "") }
    var address by remember(settingsState) { mutableStateOf(settingsState?.address ?: "") }
    var currency by remember(settingsState) { mutableStateOf(settingsState?.currencySymbol ?: "ريال") }
    var taxNumber by remember(settingsState) { mutableStateOf(settingsState?.taxNumber ?: "") }
    var invoiceHeader by remember(settingsState) { mutableStateOf(settingsState?.invoiceHeader ?: "") }
    var invoiceFooter by remember(settingsState) { mutableStateOf(settingsState?.invoiceFooter ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية") }
    var logoPath by remember(settingsState) { mutableStateOf(settingsState?.logoPath ?: "") }
    val hasConfiguredPin = PinSecurityManager.isPinConfigured(settingsState)

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = LogoHelper.saveLogoFromUri(context, uri, logoPath)
            if (savedPath != null) {
                logoPath = savedPath
                val current = settingsState ?: StoreSettings()
                viewModel.saveSettings(current.copy(logoPath = savedPath, isSetupCompleted = true))
            } else {
                viewModel.showMessage("تعذر حفظ صورة الشعار")
            }
        }
    }

    var selectedConfigType by remember { mutableStateOf(StoreConfigTypes.PRODUCT_CATEGORY) }
    var newConfigItemName by remember { mutableStateOf("") }
    var configItemToEdit by remember { mutableStateOf<StoreConfigItem?>(null) }
    var configItemToDelete by remember { mutableStateOf<StoreConfigItem?>(null) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var backupPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var backupValidationError by remember { mutableStateOf<String?>(null) }
    var isCreatingBackup by remember { mutableStateOf(false) }
    var showWipeAllDialog by remember { mutableStateOf(false) }
    var showWipeTransactionsDialog by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPinTitle by remember { mutableStateOf("التحقق من رمز المدير (PIN)") }

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
                            text = "شعار وهوية المحل",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StoreLogoBadge(
                                logoPath = logoPath,
                                size = 64.dp,
                                cornerRadius = 14.dp,
                                containerColor = Color(0xFFE2E8F0),
                                borderColor = Color(0xFFCBD5E1)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_pick_logo_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (logoPath.isBlank()) "اختيار شعار المحل" else "تغيير الشعار")
                                }
                                if (logoPath.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            LogoHelper.deleteLogoFile(context, logoPath)
                                            logoPath = ""
                                            val current = settingsState ?: StoreSettings()
                                            viewModel.saveSettings(current.copy(logoPath = "", isSetupCompleted = true))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("إزالة الشعار", color = RubyExpense, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

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

                        DialogInfoBanner(
                            title = if (hasConfiguredPin) "أمان المدير (PIN):" else "تنبيه أمان:",
                            value = if (hasConfiguredPin) "رمز الحماية مفعّل ومشفّر" else "لم يتم تعيين رمز مدير قوي بعد",
                            containerColor = if (hasConfiguredPin) FintechEmeraldBg else FintechAmberBg,
                            contentColor = if (hasConfiguredPin) FintechEmerald else FintechAmber,
                            borderColor = if (hasConfiguredPin) FintechEmeraldBorder else FintechAmberBorder,
                            icon = if (hasConfiguredPin) Icons.Default.VerifiedUser else Icons.Default.WarningAmber
                        )

                        OutlinedButton(
                            onClick = { showChangePinDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_change_pin_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LockReset, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (hasConfiguredPin) "تغيير رمز المدير (PIN)" else "تعيين رمز المدير (PIN)",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val updated = (settingsState ?: StoreSettings()).copy(
                                    id = 1,
                                    shopNameAr = storeName.trim().ifBlank { "متجري" },
                                    shopNameEn = storeNameEn.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    currency = currency.trim().ifBlank { "ريال" },
                                    taxNumber = taxNumber.trim(),
                                    invoiceHeader = invoiceHeader.trim(),
                                    invoiceFooter = invoiceFooter.trim(),
                                    managerPin = "",
                                    logoPath = logoPath,
                                    isSetupCompleted = true
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

                        OutlinedButton(
                            onClick = { viewModel.openSetupWizard() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reopen_setup_wizard_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فتح معالج التهيئة الشامل للنظام", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onOpenOpeningBalances,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_opening_balances_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = PdfHeaderNavy)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("معالج الأرصدة الافتتاحية للمحل (صندوق، بضاعة، أطراف)", fontWeight = FontWeight.Bold, color = PdfHeaderNavy)
                        }
                    }
                }
            }

            // 2.5 System Dictionaries Management Card (Categories, Sizes, Expenses, Account Types, Colors, Roles)
            item {
                val dictSections = listOf(
                    StoreConfigTypes.PRODUCT_CATEGORY to "الأقسام",
                    StoreConfigTypes.PRODUCT_SIZE to "المقاسات",
                    StoreConfigTypes.EXPENSE_CATEGORY to "المصروفات",
                    StoreConfigTypes.ACCOUNT_TYPE to "الحسابات المصرفية",
                    StoreConfigTypes.PRODUCT_COLOR to "الألوان",
                    StoreConfigTypes.EMPLOYEE_ROLE to "الوظائف"
                )
                val currentItems = remember(allConfigItems, selectedConfigType) {
                    allConfigItems.filter { it.type == selectedConfigType }
                }
                val sectionPlaceholder = when (selectedConfigType) {
                    StoreConfigTypes.PRODUCT_CATEGORY -> "أضف قسماً جديداً (أكوات، دجلات، ثياب...)"
                    StoreConfigTypes.PRODUCT_SIZE -> "أضف مقاساً (كبير، شبابي، صغير...)"
                    StoreConfigTypes.EXPENSE_CATEGORY -> "أضف نوع مصروف (كهرباء، إيجار...)"
                    StoreConfigTypes.ACCOUNT_TYPE -> "أضف نوع حساب (صراف، محفظة، بنك...)"
                    StoreConfigTypes.PRODUCT_COLOR -> "أضف لوناً (أسود، أبيض، كحلي...)"
                    else -> "أضف مسمى وظيفي (بائع، كاشير، خياط...)"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("system_dictionaries_card"),
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إدارة أقسام المحل والمقاسات والمصروفات والحسابات",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "إضافة وتعديل وحذف القوائم التي يعتمدها النظام في الشراء والبيع والمصروفات",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SlateMuted)
                                )
                            }
                            TextButton(
                                onClick = { viewModel.restoreRecommendedDictionaries(includeSampleCategories = false) }
                            ) {
                                Text("افتراضيات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dictSections) { (typeKey, label) ->
                                val isSelected = selectedConfigType == typeKey
                                val count = allConfigItems.count { it.type == typeKey }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedConfigType = typeKey
                                        newConfigItemName = ""
                                    },
                                    label = {
                                        Text(
                                            "$label ($count)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.testTag("dict_tab_$typeKey")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newConfigItemName,
                                onValueChange = { newConfigItemName = it },
                                placeholder = { Text(sectionPlaceholder, fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_config_item_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = newConfigItemName.trim()
                                    if (trimmed.isNotBlank()) {
                                        viewModel.addConfigItem(selectedConfigType, trimmed)
                                        newConfigItemName = ""
                                    }
                                },
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("add_config_item_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedConfigType == StoreConfigTypes.PRODUCT_CATEGORY) {
                            val quickCats = listOf("أكوات", "دجلات", "ثياب", "بشت", "جاكيتات", "شمغ")
                                .filter { q -> currentItems.none { it.name.equals(q, ignoreCase = true) } }
                            if (quickCats.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(quickCats) { sug ->
                                        AssistChip(
                                            onClick = { viewModel.addConfigItem(StoreConfigTypes.PRODUCT_CATEGORY, sug) },
                                            label = { Text("+ $sug", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        if (currentItems.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC)
                            ) {
                                Text(
                                    text = "لا توجد عناصر مضافة في هذا القسم بعد — اكتب الاسم أعلاه واضغط إضافة.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                currentItems.forEach { item ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = SlateHeader,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row {
                                                IconButton(
                                                    onClick = { configItemToEdit = item },
                                                    modifier = Modifier.testTag("edit_config_${item.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "تعديل ${item.name}",
                                                        tint = BrandBlue,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { configItemToDelete = item },
                                                    modifier = Modifier.testTag("delete_config_${item.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "حذف ${item.name}",
                                                        tint = RubyExpense,
                                                        modifier = Modifier.size(20.dp)
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

    // Change / Set Manager PIN Dialog
    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var pinDialogError by remember { mutableStateOf<String?>(null) }

        StoreOperationDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = if (hasConfiguredPin) "تغيير رمز المدير (PIN)" else "تعيين رمز المدير (PIN)",
            subtitle = "يُحفظ الرمز مشفراً لحماية العمليات الحساسة",
            icon = Icons.Default.Lock,
            accentColor = PdfHeaderNavy,
            confirmText = "حفظ الرمز",
            confirmIcon = Icons.Default.Check,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_change_pin_button",
            onConfirm = {
                val strengthErr = PinSecurityManager.validatePinStrength(newPin)
                if (strengthErr != null) {
                    pinDialogError = strengthErr
                    return@StoreOperationDialog
                }
                if (newPin.trim() != confirmPin.trim()) {
                    pinDialogError = "الرمز الجديد وتأكيده غير متطابقين"
                    return@StoreOperationDialog
                }
                viewModel.updateManagerPin(
                    currentPin = if (hasConfiguredPin) currentPin else null,
                    newPin = newPin.trim()
                ) { ok, err ->
                    if (ok) {
                        showChangePinDialog = false
                    } else {
                        pinDialogError = err ?: "تعذر تحديث رمز المدير"
                    }
                }
            }
        ) {
            if (hasConfiguredPin) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it; pinDialogError = null },
                    label = { Text("الرمز الحالي *") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = storeDialogTextFieldColors(PdfHeaderNavy),
                    modifier = Modifier.fillMaxWidth().testTag("current_pin_input")
                )
            }
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it; pinDialogError = null },
                label = { Text("الرمز الجديد (4 أرقام فأكثر) *") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it; pinDialogError = null },
                label = { Text("تأكيد الرمز الجديد *") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_input")
            )
            pinDialogError?.let { err ->
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

    // Restore Dialog with Validation & Record Counts Preview
    if (showRestoreDialog) {
        var restoreWordConfirm by remember { mutableStateOf("") }
        val preview = backupPreview
        val canRestore = preview != null && restoreWordConfirm.trim() == "استعادة" && restoreJsonInput.isNotBlank()
        StoreOperationDialog(
            onDismissRequest = {
                showRestoreDialog = false
                backupPreview = null
                backupValidationError = null
            },
            title = "استعادة البيانات من نسخة احتياطية",
            subtitle = "فحص ومعاينة السجلات قبل الاستبدال",
            icon = Icons.Default.Restore,
            accentColor = RubyExpense,
            confirmText = "تنفيذ الاستعادة",
            confirmIcon = Icons.Default.RestorePage,
            confirmColor = RubyExpense,
            confirmEnabled = canRestore,
            confirmTestTag = "execute_restore_backup_button",
            onConfirm = {
                val jsonSnapshot = restoreJsonInput
                pendingPinTitle = "التحقق قبل استعادة النسخة الاحتياطية"
                pendingPinAction = {
                    viewModel.restoreBackup(jsonSnapshot) { res ->
                        if (res.isSuccess) {
                            showRestoreDialog = false
                            backupPreview = null
                            backupValidationError = null
                        }
                    }
                }
            }
        ) {
            Text(
                text = "الصق محتوى ملف النسخة الاحتياطية (JSON) أدناه ثم اضغط «فحص النسخة» قبل التنفيذ:",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )
            OutlinedTextField(
                value = restoreJsonInput,
                onValueChange = {
                    restoreJsonInput = it
                    backupPreview = null
                    backupValidationError = null
                },
                label = { Text("بيانات النسخة الاحتياطية JSON") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .testTag("restore_json_input"),
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy)
            )

            OutlinedButton(
                onClick = {
                    val res = viewModel.validateBackupPreview(restoreJsonInput)
                    if (res.isSuccess) {
                        backupPreview = res.getOrNull()
                        backupValidationError = null
                    } else {
                        backupPreview = null
                        backupValidationError = res.exceptionOrNull()?.message ?: "ملف النسخة الاحتياطية غير صالح"
                    }
                },
                enabled = restoreJsonInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("validate_backup_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("فحص النسخة وعرض عدد السجلات", fontWeight = FontWeight.Bold)
            }

            backupValidationError?.let { err ->
                DialogInfoBanner(
                    title = "ملف مرفوض:",
                    value = err,
                    containerColor = FintechRoseBg,
                    contentColor = FintechRose,
                    borderColor = FintechRoseBorder,
                    icon = Icons.Default.ErrorOutline
                )
            }

            preview?.let { p ->
                DialogInfoBanner(
                    title = "نسخة سليمة (الإصدار ${p.version}):",
                    value = "إجمالي السجلات: ${p.totalRecords}",
                    containerColor = FintechEmeraldBg,
                    contentColor = FintechEmerald,
                    borderColor = FintechEmeraldBorder,
                    icon = Icons.Default.CheckCircle
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SlateBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• المنتجات: ${p.productsCount} | المبيعات: ${p.salesCount} | المشتريات: ${p.purchasesCount}\n" +
                            "• المصروفات: ${p.expensesCount} | العمال: ${p.employeesCount} | حركات العمال: ${p.employeeTransactionsCount}\n" +
                            "• العملاء والموردين: ${p.partiesCount} | الديون: ${p.debtsCount} | الدفعات: ${p.debtPaymentsCount}\n" +
                            "• الورديات: ${p.shiftsCount} | الصندوق: ${p.cashMovementsCount} | الصرافون: ${p.exchangeAccountsCount}\n" +
                            "• التحويلات: ${p.exchangeTransfersCount} | التدقيق: ${p.auditLogsCount} | القوائم: ${p.configItemsCount}",
                        fontSize = 11.sp,
                        color = SlateTextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                DialogInfoBanner(
                    title = "تنبيه الاستعادة",
                    value = "ستستبدل البيانات الحالية بعد التحقق. اكتب «استعادة» للتأكيد:",
                    containerColor = RubyLight,
                    contentColor = RubyExpense,
                    borderColor = Color(0xFFFECACA),
                    icon = Icons.Default.WarningAmber
                )
                OutlinedTextField(
                    value = restoreWordConfirm,
                    onValueChange = { restoreWordConfirm = it },
                    placeholder = { Text("اكتب استعادة") },
                    modifier = Modifier.fillMaxWidth().testTag("restore_confirm_word_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = storeDialogTextFieldColors(RubyExpense)
                )
            }
        }
    }

    if (showWipeAllDialog) {
        StoreOperationDialog(
            onDismissRequest = { showWipeAllDialog = false },
            title = "تأكيد تهيئة التطبيق بالكامل",
            subtitle = "حذف وإفراغ جميع قواعد البيانات",
            icon = Icons.Default.DeleteSweep,
            accentColor = RubyExpense,
            confirmText = "نعم، إفراغ الكل",
            confirmIcon = Icons.Default.DeleteForever,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_wipe_all_button",
            onConfirm = {
                showWipeAllDialog = false
                pendingPinTitle = "تأكيد تهيئة وإفراغ جميع البيانات"
                pendingPinAction = {
                    viewModel.clearData(transactionsOnly = false)
                }
            }
        ) {
            DialogInfoBanner(
                title = "تحذير نهائي",
                value = "سيتم حذف وإفراغ جميع البيانات المخزنة في قواعد البيانات بشكل نهائي.",
                containerColor = RubyLight,
                contentColor = RubyExpense,
                borderColor = Color(0xFFFECACA),
                icon = Icons.Default.WarningAmber
            )
            Text("• جميع المنتجات والأصناف والأنواع\n• المبيعات والمشتريات والمصروفات\n• الصندوق والورديات والصرافين والمحافظ\n• العملاء والموردين والديون وسندات التحصيل\n• العمال وسجل التدقيق", fontSize = 12.sp, color = SlateMuted)
            Text("هل أنت متأكد من تهيئة التطبيق وإفراغ كل شيء؟", fontWeight = FontWeight.Bold)
        }
    }

    if (showWipeTransactionsDialog) {
        StoreOperationDialog(
            onDismissRequest = { showWipeTransactionsDialog = false },
            title = "تأكيد تصفير الحركات والفواتير",
            subtitle = "تنظيف السجل مع حفظ البيانات الأساسية",
            icon = Icons.Default.RestartAlt,
            accentColor = RubyExpense,
            confirmText = "تصفير الحركات",
            confirmIcon = Icons.Default.DeleteSweep,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_wipe_transactions_button",
            onConfirm = {
                showWipeTransactionsDialog = false
                pendingPinTitle = "تأكيد تصفير الحركات والفواتير"
                pendingPinAction = {
                    viewModel.clearData(transactionsOnly = true)
                }
            }
        ) {
            DialogInfoBanner(
                title = "نطاق التصفير",
                value = "سيتم حذف جميع المبيعات والمشتريات والمصروفات والديون وحركات الصندوق، مع الإبقاء على قائمة المنتجات والأطراف والحسابات.",
                containerColor = RubyLight,
                contentColor = RubyExpense,
                borderColor = Color(0xFFFECACA),
                icon = Icons.Default.Info
            )
        }
    }

    pendingPinAction?.let { action ->
        PinVerificationDialog(
            viewModel = viewModel,
            title = pendingPinTitle,
            onDismiss = { pendingPinAction = null },
            onVerified = {
                pendingPinAction = null
                action()
            }
        )
    }

    // Edit Dictionary Item Dialog
    configItemToEdit?.let { item ->
        var editedName by remember(item.id) { mutableStateOf(item.name) }
        StoreOperationDialog(
            onDismissRequest = { configItemToEdit = null },
            title = "تعديل العنصر",
            subtitle = item.name,
            icon = Icons.Default.Edit,
            accentColor = PdfGoldPrimary,
            confirmText = "حفظ التعديل",
            confirmIcon = Icons.Default.Check,
            confirmColor = PdfHeaderNavy,
            confirmTestTag = "confirm_edit_config_btn",
            onConfirm = {
                viewModel.updateConfigItem(item, editedName) {
                    configItemToEdit = null
                }
            }
        ) {
            Text(
                text = "سيتم تحديث الاسم في القائمة وكذلك في السجلات المرتبطة به تلقائياً:",
                fontSize = 12.sp,
                color = SlateMuted
            )
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("الاسم الجديد") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = storeDialogTextFieldColors(PdfHeaderNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_config_name_input")
            )
        }
    }

    // Delete Dictionary Item Dialog
    configItemToDelete?.let { item ->
        StoreOperationDialog(
            onDismissRequest = { configItemToDelete = null },
            title = "تأكيد الحذف",
            subtitle = item.name,
            icon = Icons.Default.DeleteForever,
            accentColor = RubyExpense,
            confirmText = "حذف",
            confirmIcon = Icons.Default.Delete,
            confirmColor = RubyExpense,
            confirmTestTag = "confirm_delete_config_btn",
            onConfirm = {
                viewModel.deleteConfigItem(item)
                configItemToDelete = null
            }
        ) {
            Text("هل تريد حذف «${item.name}» من قوائم النظام؟", fontWeight = FontWeight.Bold)
            Text("لن يتم حذف الفواتير أو المنتجات السابقة، فقط سيُزال من اختيارات الإضافة.", fontSize = 12.sp, color = SlateMuted)
        }
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
