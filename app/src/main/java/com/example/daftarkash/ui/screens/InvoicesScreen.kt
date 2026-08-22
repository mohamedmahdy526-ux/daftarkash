package com.example.daftarkash.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.ui.components.ExportReportsDialog
import com.example.daftarkash.ui.components.InvoiceDetailDialog
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import com.example.daftarkash.util.StatementPdfHelper
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke

data class InvoiceItemDisplay(
    val transaction: Transaction,
    val customer: Customer?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    viewModel: DaftarKashViewModel,
    onNavigateToCustomer: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by viewModel.allCustomers.collectAsState(initial = emptyList())
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val customersWithBalances by viewModel.customersWithBalances.collectAsState()
    val metrics by viewModel.ledgerMetrics.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val storeName by viewModel.storeName.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // ALL, DEBT, PAYMENT, TODAY
    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceItemDisplay?>(null) }
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val customerMap = remember(customers) { customers.associateBy { it.id } }

    val invoiceItems = remember(transactions, customerMap, searchQuery, filterType) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        transactions.mapNotNull { tx ->
            val cust = customerMap[tx.customerId]
            InvoiceItemDisplay(tx, cust)
        }.filter { item ->
            val matchesFilter = when (filterType) {
                "DEBT" -> item.transaction.type == "DEBT"
                "PAYMENT" -> item.transaction.type == "PAYMENT"
                "TODAY" -> item.transaction.timestamp >= startOfToday
                else -> true
            }

            val query = searchQuery.trim()
            val matchesSearch = if (query.isBlank()) true else {
                val qNorm = DaftarKashViewModel.normalizeArabicText(query)
                val qRaw = query.lowercase()
                val custName = item.customer?.name ?: ""
                val custPhone = item.customer?.phone ?: ""
                val desc = item.transaction.description
                val amountStr = item.transaction.amount.toString()

                DaftarKashViewModel.normalizeArabicText(custName).contains(qNorm) ||
                custName.lowercase().contains(qRaw) ||
                DaftarKashViewModel.normalizeArabicText(desc).contains(qNorm) ||
                desc.lowercase().contains(qRaw) ||
                custPhone.contains(qRaw) ||
                amountStr.contains(qRaw)
            }

            matchesFilter && matchesSearch
        }
    }

    // Totals for filtered list
    val totalDebts = remember(transactions) {
        transactions.filter { it.type == "DEBT" }.sumOf { it.amount }
    }
    val totalPayments = remember(transactions) {
        transactions.filter { it.type == "PAYMENT" }.sumOf { it.amount }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        // 1. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث باسم العميل، الوصف، أو رقم الفاتورة...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "مسح",
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
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("invoices_search_input")
        )

        // 2. Summary Metrics Strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Debts
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "إجمالي الديون/الفواتير",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${DaftarKashViewModel.formatMoney(totalDebts)} $currency",
                        style = MaterialTheme.typography.titleMedium,
                        color = DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Total Payments
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "إجمالي التحصيل والسداد",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${DaftarKashViewModel.formatMoney(totalPayments)} $currency",
                        style = MaterialTheme.typography.titleMedium,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterType == "ALL",
                onClick = { filterType = "ALL" },
                label = { Text("الكل (${transactions.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = filterType == "DEBT",
                onClick = { filterType = "DEBT" },
                label = { Text("🔴 ديون وفواتير", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = filterType == "PAYMENT",
                onClick = { filterType = "PAYMENT" },
                label = { Text("🟢 دفعات وسداد", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )
            FilterChip(
                selected = filterType == "TODAY",
                onClick = { filterType = "TODAY" },
                label = { Text("📅 اليوم", fontSize = 11.sp) },
                shape = RoundedCornerShape(8.dp)
            )

            // Export Action Chip
            SuggestionChip(
                onClick = { showExportDialog = true },
                label = { Text("تصدير شيت Excel / PDF 📊", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
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
                modifier = Modifier.testTag("invoices_btn_export_chip")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. Invoices List
        if (invoiceItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "لا توجد فواتير أو حركات مطابقة للبحث"
                        else "لا توجد فواتير مسجلة حتى الآن.\nيمكنك تسجيل دين أو سداد من شاشة الدفتر.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = invoiceItems,
                    key = { it.transaction.id },
                    contentType = { "invoice_card" }
                ) { item ->
                    InvoiceRowCard(
                        item = item,
                        currency = currency,
                        onClick = { selectedInvoiceForDetail = item },
                        onNavigateToCustomer = {
                            item.customer?.let { c -> onNavigateToCustomer(c.id) }
                        },
                        onDelete = { txToDelete = item.transaction }
                    )
                }
            }
        }
    }

    // Invoice Details & Receipt Modal Dialog
    selectedInvoiceForDetail?.let { detail ->
        val custBal = detail.customer?.let { c ->
            customersWithBalances.find { it.customer.id == c.id }?.balance
        }
        InvoiceDetailDialog(
            transaction = detail.transaction,
            customer = detail.customer,
            currentCustomerBalance = custBal,
            currency = currency,
            storeName = storeName,
            storePhone = storePhone,
            onDismiss = { selectedInvoiceForDetail = null },
            onDeleteTransaction = {
                viewModel.deleteTransaction(detail.transaction)
                selectedInvoiceForDetail = null
            }
        )
    }

    // Delete Transaction Confirm Dialog
    txToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = { Text("تأكيد حذف العملية") },
            text = {
                Text("هل أنت متأكد من حذف هذه الحركة بمبلغ ${tx.amount} $currency؟\nسيتم تحديث رصيد العميل تلقائياً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        txToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showExportDialog) {
        ExportReportsDialog(
            storeName = storeName,
            storePhone = storePhone,
            currency = currency,
            customersWithBalances = customersWithBalances,
            allTransactions = transactions,
            metrics = metrics,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun InvoiceRowCard(
    item: InvoiceItemDisplay,
    currency: String,
    onClick: () -> Unit,
    onNavigateToCustomer: () -> Unit,
    onDelete: () -> Unit
) {
    val tx = item.transaction
    val cust = item.customer
    val isDebt = tx.type == "DEBT"

    val dateFormatted = remember(tx.timestamp, tx.date) {
        if (tx.timestamp > 100000000000L) {
            try {
                SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.forLanguageTag("ar")).format(Date(tx.timestamp))
            } catch (e: Exception) {
                tx.date
            }
        } else {
            tx.date
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("invoice_card_${tx.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Right Side: Type indicator Icon + Customer & Desc Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDebt) DangerRed.copy(alpha = 0.12f)
                            else SuccessGreen.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDebt) Icons.Default.Add else Icons.Default.Remove,
                        contentDescription = null,
                        tint = if (isDebt) DangerRed else SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cust?.name ?: "عميل غير محدد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDebt) DangerRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isDebt) "دين 🔴" else "سداد 🟢",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDebt) DangerRed else SuccessGreen,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (tx.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tx.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Left Side: Amount + Quick options
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${if (isDebt) "+" else "-"}${DaftarKashViewModel.formatMoney(tx.amount)} $currency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebt) DangerRed else SuccessGreen
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row {
                    IconButton(
                        onClick = onNavigateToCustomer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "كشف الحساب",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "حذف",
                            tint = DangerRed.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
