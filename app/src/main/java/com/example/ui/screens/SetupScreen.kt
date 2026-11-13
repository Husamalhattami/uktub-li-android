package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StoreSettings
import com.example.ui.StoreViewModel
import com.example.ui.theme.*
import com.example.ui.util.LogoHelper
import com.example.ui.util.StoreLogoBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: StoreViewModel,
    onCancelToHomeIfAllowed: () -> Unit = {}
) {
    val context = LocalContext.current
    val existingSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val existingCatItems by viewModel.productCategoryItems.collectAsStateWithLifecycle()
    val existingSizeItems by viewModel.productSizeItems.collectAsStateWithLifecycle()
    val existingExpenseItems by viewModel.expenseCategoryItems.collectAsStateWithLifecycle()
    val existingAccountTypeItems by viewModel.accountTypeItems.collectAsStateWithLifecycle()
    val existingColorItems by viewModel.productColorItems.collectAsStateWithLifecycle()
    val existingRoleItems by viewModel.employeeRoleItems.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(1) }

    var shopNameAr by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.shopNameAr.orEmpty())
    }
    var shopNameEn by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.shopNameEn.orEmpty())
    }
    var phone by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.phone.orEmpty())
    }
    var address by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.address.orEmpty())
    }
    var currency by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.currency?.ifBlank { "ريال" } ?: "ريال")
    }
    var invoiceHeader by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.invoiceHeader.orEmpty())
    }
    var invoiceFooter by remember(existingSettings?.id) {
        mutableStateOf(
            existingSettings?.invoiceFooter?.ifBlank {
                "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."
            } ?: "البضاعة المباعة ترد وتستبدل خلال 3 أيام بشرط وجود الفاتورة وبحالتها الأصلية."
        )
    }
    var logoPath by remember(existingSettings?.id) {
        mutableStateOf(existingSettings?.logoPath.orEmpty())
    }

    val categoriesList = remember { mutableStateListOf<String>() }
    val sizesList = remember { mutableStateListOf<String>() }
    val expenseCategoriesList = remember { mutableStateListOf<String>() }
    val accountTypesList = remember { mutableStateListOf<String>() }
    val colorsList = remember { mutableStateListOf<String>() }
    val rolesList = remember { mutableStateListOf<String>() }

    var dictsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(
        existingCatItems,
        existingSizeItems,
        existingExpenseItems,
        existingAccountTypeItems,
        existingColorItems,
        existingRoleItems
    ) {
        if (!dictsLoaded) {
            categoriesList.clear()
            categoriesList.addAll(existingCatItems.map { it.name })

            sizesList.clear()
            sizesList.addAll(
                existingSizeItems.map { it.name }
                    .ifEmpty { listOf("كبير", "شبابي", "وسط", "صغير", "حر") }
            )

            expenseCategoriesList.clear()
            expenseCategoriesList.addAll(
                existingExpenseItems.map { it.name }
                    .ifEmpty {
                        listOf(
                            "صرفة يومية لعامل",
                            "إيجار المحل",
                            "الكهرباء",
                            "الماء",
                            "النقل والشحن",
                            "التغليف والأكياس",
                            "مصروف متفرقات"
                        )
                    }
            )

            accountTypesList.clear()
            accountTypesList.addAll(
                existingAccountTypeItems.map { it.name }
                    .ifEmpty { listOf("صراف", "محفظة", "حساب بنكي", "شركة حوالات") }
            )

            colorsList.clear()
            colorsList.addAll(
                existingColorItems.map { it.name }
                    .ifEmpty { listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "بني") }
            )

            rolesList.clear()
            rolesList.addAll(
                existingRoleItems.map { it.name }
                    .ifEmpty { listOf("بائع", "كاشير", "خياط", "محاسب") }
            )
            dictsLoaded = true
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = LogoHelper.saveLogoFromUri(context, uri, logoPath)
            if (savedPath != null) {
                logoPath = savedPath
                viewModel.showMessage("تم اختيار شعار المحل بنجاح")
            } else {
                viewModel.showMessage("تعذر قراءة الصورة المختارة")
            }
        }
    }

    val isReopenMode = existingSettings?.isSetupCompleted == true

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PdfHeaderNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    if (isReopenMode) {
                        IconButton(onClick = onCancelToHomeIfAllowed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "تهيئة وإعداد النظام",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (currentStep == 1) "الخطوة 1 من 2: بيانات المحل والشعار" else "الخطوة 2 من 2: الأقسام والمقاسات والقوائم",
                            fontSize = 12.sp,
                            color = Color(0xFF90CDF4)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 660.dp
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Welcome & Stepper Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PdfHeaderNavy)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0F1E36), Color(0xFF1D3557))
                                )
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StoreLogoBadge(
                                logoPath = logoPath,
                                size = 54.dp,
                                cornerRadius = 14.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shopNameAr.ifBlank { "مرحباً بك! لنبدأ تهيئة نظام محلك" },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "اضبط اسم المحل ورقم الهاتف والعنوان والشعار والأقسام والمقاسات لتظهر مباشرة في الفواتير والنظام.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SetupStepPill(
                                stepNum = 1,
                                label = "بيانات المحل والشعار",
                                active = currentStep == 1,
                                completed = currentStep > 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { currentStep = 1 }
                            )
                            SetupStepPill(
                                stepNum = 2,
                                label = "الأقسام والمقاسات والقوائم",
                                active = currentStep == 2,
                                completed = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (shopNameAr.isBlank()) {
                                            viewModel.showMessage("يرجى إدخال اسم المحل أولاً قبل المتابعة")
                                        } else {
                                            currentStep = 2
                                        }
                                    }
                            )
                        }
                    }
                }

                if (currentStep == 1) {
                    // STEP 1: Store Identity + Logo Picker
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "شعار المحل (اللوجو)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = PdfHeaderNavy
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                StoreLogoBadge(
                                    logoPath = logoPath,
                                    size = 76.dp,
                                    cornerRadius = 16.dp,
                                    containerColor = Color(0xFFE2E8F0),
                                    borderColor = Color(0xFFCBD5E1)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            logoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("setup_pick_logo_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (logoPath.isBlank()) "اختيار شعار المحل" else "تغيير الشعار",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (logoPath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                LogoHelper.deleteLogoFile(context, logoPath)
                                                logoPath = ""
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RubyExpense, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("إزالة الشعار", color = RubyExpense)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Text(
                                text = "البيانات الأساسية للنظام",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = PdfHeaderNavy
                            )

                            OutlinedTextField(
                                value = shopNameAr,
                                onValueChange = { shopNameAr = it },
                                label = { Text("اسم المحل / النظام (بالعربي) *") },
                                placeholder = { Text("مثال: الجرادي للملابس الجاهزة") },
                                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = PdfGoldDark) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_shop_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = shopNameEn,
                                onValueChange = { shopNameEn = it },
                                label = { Text("اسم المحل بالإنجليزية (اختياري)") },
                                placeholder = { Text("Example: Al-Asala Fashion") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (isWide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { phone = it },
                                        label = { Text("رقم هاتف المحل *") },
                                        placeholder = { Text("مثال: 777000000") },
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PdfGoldDark) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("setup_phone_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = currency,
                                        onValueChange = { currency = it },
                                        label = { Text("العملة") },
                                        modifier = Modifier.weight(0.6f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("رقم هاتف المحل *") },
                                    placeholder = { Text("مثال: 777000000") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PdfGoldDark) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("setup_phone_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = currency,
                                    onValueChange = { currency = it },
                                    label = { Text("العملة") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("عنوان المحل *") },
                                placeholder = { Text("مثال: صنعاء - شارع التحرير") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = PdfGoldDark) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_address_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = invoiceHeader,
                                onValueChange = { invoiceHeader = it },
                                label = { Text("ترويسة الفواتير / عبارة ترحيب (اختياري)") },
                                placeholder = { Text("مثال: جودة .. أناقة .. ثقة") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = invoiceFooter,
                                onValueChange = { invoiceFooter = it },
                                label = { Text("تذييل الفاتورة (سياسة الاستبدال والاسترجاع)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (shopNameAr.trim().isBlank()) {
                                        viewModel.showMessage("يرجى إدخال اسم المحل أو النظام")
                                    } else {
                                        currentStep = 2
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("setup_next_step_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PdfHeaderNavy)
                            ) {
                                Text("التالي: تهيئة الأقسام والمقاسات والقوائم", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                    }
                } else {
                    // STEP 2: Categories, Sizes, Expense Types, Account Types, Colors, Roles
                    SetupDictionaryCard(
                        title = "أقسام / أنواع المنتجات في المحل",
                        subtitle = "أضف أقسام محلك لتظهر في الشراء والبيع والمخزون (مثل: أكوات، دجلات، ثياب...)",
                        icon = Icons.Default.Category,
                        items = categoriesList,
                        suggestions = listOf("أكوات", "دجلات", "ثياب", "بشت", "جاكيتات", "شمغ", "أحذية", "إكسسوارات"),
                        inputPlaceholder = "أضف قسم جديد (مثال: أكوات)...",
                        tagPrefix = "setup_cat"
                    )

                    SetupDictionaryCard(
                        title = "المقاسات المعتمدة",
                        subtitle = "حدد المقاسات المتوفرة في محلك (مثل: كبير، شبابي، وسط، صغير...)",
                        icon = Icons.Default.Straighten,
                        items = sizesList,
                        suggestions = listOf("كبير", "شبابي", "وسط", "صغير", "حر", "S", "M", "L", "XL", "XXL"),
                        inputPlaceholder = "أضف مقاس جديد...",
                        tagPrefix = "setup_size"
                    )

                    SetupDictionaryCard(
                        title = "أنواع المصروفات",
                        subtitle = "قائمة بنود الصرفيات اليومية والتشغيلية",
                        icon = Icons.Default.ReceiptLong,
                        items = expenseCategoriesList,
                        suggestions = listOf("صرفة يومية لعامل", "إيجار المحل", "الكهرباء", "الماء", "النقل والشحن", "التغليف والأكياس", "الضيافة والشاي", "مصروف متفرقات"),
                        inputPlaceholder = "أضف نوع مصروف جديد...",
                        tagPrefix = "setup_expense"
                    )

                    SetupDictionaryCard(
                        title = "أنواع الحسابات المصرفية والصرافين",
                        subtitle = "تصنيفات الحسابات التي يتعامل معها المحل",
                        icon = Icons.Default.AccountBalance,
                        items = accountTypesList,
                        suggestions = listOf("صراف", "محفظة", "حساب بنكي", "شركة حوالات", "نقطة بيع"),
                        inputPlaceholder = "أضف نوع حساب جديد...",
                        tagPrefix = "setup_account_type"
                    )

                    SetupDictionaryCard(
                        title = "الألوان الشائعة للأصناف",
                        subtitle = "تظهر كاختيارات سريعة عند تسجيل فواتير الشراء",
                        icon = Icons.Default.Palette,
                        items = colorsList,
                        suggestions = listOf("أسود", "أبيض", "كحلي", "بيج", "رمادي", "بني", "زيتي", "سكري"),
                        inputPlaceholder = "أضف لوناً جديداً...",
                        tagPrefix = "setup_color"
                    )

                    SetupDictionaryCard(
                        title = "المسميات الوظيفية للعمال",
                        subtitle = "تستخدم عند إضافة موظفين وعمال المحل",
                        icon = Icons.Default.Badge,
                        items = rolesList,
                        suggestions = listOf("بائع", "كاشير", "خياط", "محاسب", "مدير فرع"),
                        inputPlaceholder = "أضف مسمى وظيفي...",
                        tagPrefix = "setup_role"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            modifier = Modifier
                                .weight(0.45f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("السابق", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (shopNameAr.trim().isBlank()) {
                                    currentStep = 1
                                    viewModel.showMessage("يرجى إدخال اسم المحل أولاً")
                                    return@Button
                                }
                                val finalSettings = StoreSettings(
                                    id = 1,
                                    shopNameAr = shopNameAr.trim(),
                                    shopNameEn = shopNameEn.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    currency = currency.trim().ifBlank { "ريال" },
                                    taxNumber = existingSettings?.taxNumber.orEmpty(),
                                    invoiceHeader = invoiceHeader.trim().ifBlank { shopNameAr.trim() },
                                    invoiceFooter = invoiceFooter.trim(),
                                    showProfitInReports = existingSettings?.showProfitInReports ?: true,
                                    managerPin = existingSettings?.managerPin ?: "1234",
                                    isManagerRole = existingSettings?.isManagerRole ?: true,
                                    logoPath = logoPath,
                                    isSetupCompleted = true
                                )
                                viewModel.completeInitialSetup(
                                    settings = finalSettings,
                                    categories = categoriesList.toList(),
                                    sizes = sizesList.toList(),
                                    expenseCategories = expenseCategoriesList.toList(),
                                    accountTypes = accountTypesList.toList(),
                                    colors = colorsList.toList(),
                                    roles = rolesList.toList()
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("setup_complete_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ التهيئة وبدء العمل", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SetupStepPill(
    stepNum: Int,
    label: String,
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        active -> PdfGoldDark
        completed -> EmeraldProfit
        else -> Color.White.copy(alpha = 0.12f)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                if (completed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                } else {
                    Text("$stepNum", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupDictionaryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: MutableList<String>,
    suggestions: List<String>,
    inputPlaceholder: String,
    tagPrefix: String
) {
    var newValue by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = PdfHeaderNavy)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PdfHeaderNavy)
                    Text(subtitle, fontSize = 11.sp, color = SlateMuted)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = "${items.size}",
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = { Text(inputPlaceholder, fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${tagPrefix}_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        val trimmed = newValue.trim()
                        if (trimmed.isNotBlank() && items.none { it.equals(trimmed, ignoreCase = true) }) {
                            items.add(trimmed)
                            newValue = ""
                        }
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("${tagPrefix}_add_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PdfGoldDark)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (items.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEach { itemName ->
                        InputChip(
                            selected = true,
                            onClick = { items.remove(itemName) },
                            label = { Text(itemName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "حذف $itemName",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            val missingSuggestions = suggestions.filter { sug ->
                items.none { it.equals(sug, ignoreCase = true) }
            }
            if (missingSuggestions.isNotEmpty()) {
                Text("مقترحات سريعة (اضغط للإضافة):", fontSize = 11.sp, color = SlateMuted)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    missingSuggestions.forEach { sug ->
                        AssistChip(
                            onClick = { items.add(sug) },
                            label = { Text("+ $sug", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    }
}
