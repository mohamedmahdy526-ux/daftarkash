package com.example.daftarkash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.daftarkash.data.db.AppDatabase
import com.example.daftarkash.data.repository.DaftarKashRepository
import com.example.daftarkash.ui.components.CalculatorDialog
import com.example.daftarkash.ui.screens.*
import com.example.daftarkash.ui.theme.DaftarKashTheme
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { DaftarKashRepository(database) }

    private val viewModel: DaftarKashViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DaftarKashViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val brandTheme by viewModel.brandTheme.collectAsState()
            val fontScale by viewModel.fontScale.collectAsState()

            DaftarKashTheme(
                darkTheme = isDark,
                brandTheme = brandTheme,
                fontScale = fontScale
            ) {
                DaftarKashMainApp(viewModel = viewModel)
            }
        }
    }
}

enum class MainTab(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LEDGER("ledger", "الدفتر", Icons.Outlined.People, Icons.Filled.People),
    INVOICES("invoices", "سجل الفواتير", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    SETTINGS("settings", "الإعدادات", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarKashMainApp(viewModel: DaftarKashViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(MainTab.LEDGER) }
    var currentScreen by remember { mutableStateOf<String>("root") } // "root", "customer_detail", "script_code"

    val storeName by viewModel.storeName.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val syncStatus by viewModel.cloudSyncStatus.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val selectedCustomerDetail by viewModel.selectedCustomerDetail.collectAsState()

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showAddTxParams by remember { mutableStateOf<AddTxParams?>(null) }
    var showCalculatorDialog by remember { mutableStateOf(false) }

    // Device / System Back Button Handler
    BackHandler(enabled = currentScreen != "root" || currentTab != MainTab.LEDGER) {
        if (currentScreen != "root") {
            currentScreen = "root"
        } else if (currentTab != MainTab.LEDGER) {
            currentTab = MainTab.LEDGER
        }
    }

    // Toast/Snackbar listener
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentScreen == "root") {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "شعار التطبيق",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = storeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreen)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "حفظ محلي فوري",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Cloud Sync Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { viewModel.manualCloudSync() }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (syncStatus == "synced") SuccessGreen else if (syncStatus == "syncing") MaterialTheme.colorScheme.primary else DangerRed)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (syncStatus == "synced") "متزامن ☁️" else if (syncStatus == "syncing") "جاري الحفظ... ⏳" else "أوفلاين 📡",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Calculator Button
                        IconButton(
                            onClick = { showCalculatorDialog = true },
                            modifier = Modifier.testTag("btn_calculator")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Calculate,
                                contentDescription = "الآلة الحاسبة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Theme Toggle
                        IconButton(onClick = { viewModel.toggleDarkMode() }) {
                            Icon(
                                imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "تبديل الوضع الليلي / النهاري"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen == "root") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 4.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MainTab.values().forEach { tab ->
                            val selected = currentTab == tab

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentTab = tab
                                        currentScreen = "root"
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("nav_${tab.route}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                             else Color.Transparent
                                        )
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == "customer_detail" || targetState == "script_code") {
                        (slideInHorizontally(animationSpec = tween(160)) { fullWidth -> fullWidth / 4 } + fadeIn(animationSpec = tween(140)))
                            .togetherWith(fadeOut(animationSpec = tween(100)))
                    } else {
                        fadeIn(animationSpec = tween(140))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(140)) { fullWidth -> -fullWidth / 4 } + fadeOut(animationSpec = tween(100)))
                    }
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "customer_detail" -> {
                        CustomerDetailScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "root" },
                            onOpenAddTx = { txType, amt, note ->
                                showAddTxParams = AddTxParams(type = txType, amount = amt, note = note)
                            }
                        )
                    }
                    "script_code" -> {
                        ScriptCodeScreen(
                            onBack = { currentScreen = "root" },
                            onCopied = {
                                viewModel.manualCloudSync()
                            }
                        )
                    }
                    else -> {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(140),
                            label = "TabCrossfade"
                        ) { tab ->
                            when (tab) {
                                MainTab.LEDGER -> {
                                    LedgerScreen(
                                        viewModel = viewModel,
                                        onNavigateToCustomer = { customerId ->
                                            viewModel.selectCustomer(customerId)
                                            currentScreen = "customer_detail"
                                        },
                                        onOpenAddCustomer = { showAddCustomerDialog = true }
                                    )
                                }
                                MainTab.INVOICES -> {
                                    InvoicesScreen(
                                        viewModel = viewModel,
                                        onNavigateToCustomer = { customerId ->
                                            viewModel.selectCustomer(customerId)
                                            currentScreen = "customer_detail"
                                        }
                                    )
                                }
                                MainTab.SETTINGS -> {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToScriptCode = { currentScreen = "script_code" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            viewModel = viewModel,
            onDismiss = { showAddCustomerDialog = false },
            onCustomerCreated = { newCustomerId ->
                viewModel.selectCustomer(newCustomerId)
                currentScreen = "customer_detail"
            }
        )
    }

    // Add Transaction Dialog
    showAddTxParams?.let { params ->
        selectedCustomerDetail?.let { custDetail ->
            AddTransactionDialog(
                initialType = params.type,
                initialAmount = params.amount,
                initialDescription = params.note,
                customerId = custDetail.customer.id,
                customerName = custDetail.customer.name,
                viewModel = viewModel,
                onDismiss = { showAddTxParams = null }
            )
        }
    }

    // Calculator Dialog
    if (showCalculatorDialog) {
        CalculatorDialog(
            onDismiss = { showCalculatorDialog = false }
        )
    }
}

data class AddTxParams(
    val type: String = "DEBT",
    val amount: String = "",
    val note: String = ""
)
