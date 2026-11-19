package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.FintechRose
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.CollectionSuccessDialog
import com.example.ui.components.CompletedReturnDialog
import com.example.ui.components.PdfBottomNavigation
import com.example.ui.components.PdfNavigationRail
import com.example.ui.components.SaleReceiptDialog
import com.example.ui.components.SupplierPaymentSuccessDialog
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

private val MAIN_NAV_SCREENS = setOf(
    AppScreen.HOME,
    AppScreen.SALE,
    AppScreen.INVENTORY,
    AppScreen.CASH_DRAWER,
    AppScreen.REPORTS,
    AppScreen.EXCHANGE_ACCOUNTS,
    AppScreen.DEBTS
)

@Composable
fun MainAppContent(viewModel: StoreViewModel) {
    val isInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val canNavigateBack by viewModel.canNavigateBack.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()
    val completedCollectionReceipt by viewModel.completedCollectionReceipt.collectAsStateWithLifecycle()
    val completedSupplierPaymentReceipt by viewModel.completedSupplierPaymentReceipt.collectAsStateWithLifecycle()
    val completedReturnResult by viewModel.completedReturnResult.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val errorDialogMessage by viewModel.errorDialogMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (!isInitialized) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    // System Back button handling (preserves exact page-by-page backstack)
    BackHandler(enabled = canNavigateBack) {
        viewModel.navigateBack()
    }

    // Show toast / snackbar on messages
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val showMainNav = currentScreen in MAIN_NAV_SCREENS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showMainNav && !useNavigationRail) {
                    PdfBottomNavigation(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (showMainNav && useNavigationRail) {
                    PdfNavigationRail(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }

                AnimatedContent(
                    targetState = currentScreen,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentKey = { it },
                    transitionSpec = {
                        fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 75)
                        ) togetherWith fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 45)
                        )
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        AppScreen.SETUP -> SetupScreen(
                            viewModel = viewModel,
                            onCancelToHomeIfAllowed = { viewModel.navigateBack() }
                        )
                        AppScreen.HOME -> HomeScreen(
                            viewModel = viewModel,
                            onNavigate = { viewModel.navigateTo(it) }
                        )
                        AppScreen.SALE -> SaleScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.PURCHASE -> PurchaseScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.INVENTORY -> InventoryScreen(
                            viewModel = viewModel,
                            onNavigate = { viewModel.navigateTo(it) },
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.EXPENSES -> ExpensesScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.EMPLOYEES -> EmployeesScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.DEBTS -> DebtsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.REPORTS -> ReportsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.CASH_DRAWER -> CashDrawerScreen(
                            viewModel = viewModel,
                            onNavigate = { viewModel.navigateTo(it) },
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.EXCHANGE_ACCOUNTS -> ExchangeAccountsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.TRANSACTIONS_LOG -> TransactionsManagerScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.STORE_AUDIT -> StoreAuditScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.OPENING_BALANCES -> OpeningBalancesScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.RETURNS -> ReturnsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        AppScreen.SETTINGS, AppScreen.AUDIT_LOGS -> SettingsScreen(
                            viewModel = viewModel,
                            initialSection = if (screen == AppScreen.AUDIT_LOGS) SettingsSection.AUDIT_TRAIL else SettingsSection.STORE_INFO,
                            onOpenOpeningBalances = { viewModel.navigateTo(AppScreen.OPENING_BALANCES) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                }
            }
        }
    }

    // Receipt Dialog when sale is completed
    completedReceipt?.let { receipt ->
        SaleReceiptDialog(
            receipt = receipt,
            settings = storeSettings,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }

    // Collection Receipt Dialog when money collection is completed
    completedCollectionReceipt?.let { receipt ->
        CollectionSuccessDialog(
            receipt = receipt,
            settings = storeSettings,
            onDismiss = { viewModel.dismissCollectionReceipt() }
        )
    }

    // Supplier Payment Receipt Dialog when supplier payment is completed
    completedSupplierPaymentReceipt?.let { receipt ->
        SupplierPaymentSuccessDialog(
            receipt = receipt,
            settings = storeSettings,
            onDismiss = { viewModel.dismissSupplierPaymentReceipt() }
        )
    }

    // Completed Sale/Purchase Return Receipt Dialog
    completedReturnResult?.let { result ->
        CompletedReturnDialog(
            result = result,
            settings = storeSettings,
            onDismiss = { viewModel.dismissReturnReceipt() }
        )
    }

    // Persistent Error Dialog for critical rejections and accounting guards
    errorDialogMessage?.let { errMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorDialog() },
            modifier = Modifier.testTag("global_error_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "خطأ",
                    tint = FintechRose
                )
            },
            title = {
                Text(
                    text = "تنبيه — تعذر إتمام العملية",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = errMsg)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissErrorDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRose),
                    modifier = Modifier.testTag("dismiss_error_dialog_button")
                ) {
                    Text("موافق", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
