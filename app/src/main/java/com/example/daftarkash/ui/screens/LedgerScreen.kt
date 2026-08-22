package com.example.daftarkash.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.daftarkash.ui.components.CustomerCard
import com.example.daftarkash.ui.components.ExportReportsDialog
import com.example.daftarkash.ui.components.MetricBannerCard
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel

@Composable
fun LedgerScreen(
    viewModel: DaftarKashViewModel,
    onNavigateToCustomer: (Long) -> Unit,
    onOpenAddCustomer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics by viewModel.ledgerMetrics.collectAsState()
    val customers by viewModel.customersWithBalances.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val currentFilter by viewModel.currentFilter.collectAsState()
    val searchQuery by viewModel.customerSearchQuery.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val storeName by viewModel.storeName.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        // 3-Card Metrics Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBannerCard(
                title = "فلوس السوق",
                value = DaftarKashViewModel.formatMoney(metrics.totalMarketDebt),
                unit = currency,
                icon = Icons.Outlined.Paid,
                accentColor = DangerRed,
                modifier = Modifier.weight(1f)
            )
            MetricBannerCard(
                title = "تحصيل اليوم",
                value = DaftarKashViewModel.formatMoney(metrics.todayCollections),
                unit = currency,
                icon = Icons.Outlined.AccountBalanceWallet,
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            MetricBannerCard(
                title = "المدينون",
                value = metrics.debtorsCount.toString(),
                unit = "عميل",
                icon = Icons.Outlined.People,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search Bar with Add Customer Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setCustomerSearchQuery(it) },
                placeholder = { Text("ابحث باسم العميل أو الهاتف...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setCustomerSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح البحث",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("customer_search_input")
            )

            FilledIconButton(
                onClick = onOpenAddCustomer,
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("btn_add_customer_top")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة عميل جديد",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = currentFilter == "all",
                onClick = { viewModel.setFilter("all") },
                label = { Text("الكل (${metrics.totalCustomersCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("filter_chip_all")
            )
            FilterChip(
                selected = currentFilter == "has_debt",
                onClick = { viewModel.setFilter("has_debt") },
                label = { Text("عليهم فلوس (${metrics.debtorsCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                    selectedLabelColor = DangerRed
                ),
                modifier = Modifier.testTag("filter_chip_debt")
            )
            FilterChip(
                selected = currentFilter == "settled",
                onClick = { viewModel.setFilter("settled") },
                label = { Text("خالصين (${metrics.settledCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                    selectedLabelColor = SuccessGreen
                ),
                modifier = Modifier.testTag("filter_chip_settled")
            )
            FilterChip(
                selected = currentFilter == "top_debt",
                onClick = { viewModel.setFilter("top_debt") },
                label = { Text("الأعلى ديناً ⬇️") },
                modifier = Modifier.testTag("filter_chip_top_debt")
            )

            // Export Reports Action Chip
            SuggestionChip(
                onClick = { showExportDialog = true },
                label = { Text("تصدير Excel / PDF 📊", fontWeight = FontWeight.Bold) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    labelColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.testTag("chip_export_reports")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Customer List
        if (customers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.PersonOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "لا يوجد عملاء مطابقين للبحث أو التصفية",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = customers,
                    key = { it.customer.id },
                    contentType = { "customer_card" }
                ) { customerWithBalance ->
                    CustomerCard(
                        customerWithBalance = customerWithBalance,
                        currency = currency,
                        onClick = { onNavigateToCustomer(customerWithBalance.customer.id) }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportReportsDialog(
            storeName = storeName,
            storePhone = storePhone,
            currency = currency,
            customersWithBalances = customers,
            allTransactions = allTransactions,
            metrics = metrics,
            onDismiss = { showExportDialog = false }
        )
    }
}
