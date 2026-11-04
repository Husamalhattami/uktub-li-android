package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.StoreViewModel
import com.example.ui.components.SaleReceiptDialog
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

@Composable
fun MainAppContent(viewModel: StoreViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // System Back button handling
    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        viewModel.navigateBack()
    }

    // Show toast / snackbar on messages
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
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
                AppScreen.SETTINGS, AppScreen.AUDIT_LOGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }

    // Receipt Dialog when sale is completed
    completedReceipt?.let { receipt ->
        SaleReceiptDialog(
            receipt = receipt,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }
}
