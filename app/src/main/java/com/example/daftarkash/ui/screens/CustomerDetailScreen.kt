package com.example.daftarkash.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.ui.components.CalculatorDialog
import com.example.daftarkash.ui.components.InvoiceDetailDialog
import com.example.daftarkash.ui.components.TransactionItemCard
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import com.example.daftarkash.util.StatementPdfHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    viewModel: DaftarKashViewModel,
    onBack: () -> Unit,
    onOpenAddTx: (type: String, amount: String, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customerDetail by viewModel.selectedCustomerDetail.collectAsState()
    val transactions by viewModel.selectedCustomerTransactions.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val storeName by viewModel.storeName.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var showStatementSheet by remember { mutableStateOf(false) }
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var selectedTransactionForDetail by remember { mutableStateOf<Transaction?>(null) }

    if (customerDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val customer = customerDetail!!.customer
    val balance = customerDetail!!.balance
    val isDebt = balance > 0.0

    // Helper to generate & share PDF directly
    val exportPdfAction: () -> Unit = {
        val pdfFile = StatementPdfHelper.generateCustomerPdfReport(
            context = context,
            customer = customer,
            balance = balance,
            currency = currency,
            storeName = storeName,
            storePhone = storePhone,
            transactions = transactions
        )
        if (pdfFile != null) {
            StatementPdfHelper.sharePdfFile(context, pdfFile, customer.name)
        } else {
            Toast.makeText(context, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "كشف حساب العميل",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_customer")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    // Calculator Icon
                    IconButton(
                        onClick = { showCalculatorDialog = true },
                        modifier = Modifier.testTag("btn_customer_calculator")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Calculate,
                            contentDescription = "الآلة الحاسبة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Direct PDF Export Icon in Top Bar
                    IconButton(
                        onClick = exportPdfAction,
                        modifier = Modifier.testTag("btn_top_pdf_export")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "تصدير PDF",
                            tint = DangerRed
                        )
                    }
                    // Edit Customer Info Action
                    IconButton(
                        onClick = { showEditCustomerDialog = true },
                        modifier = Modifier.testTag("btn_edit_customer")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "تعديل بيانات العميل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Delete Customer Action
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = "حذف العميل",
                            tint = DangerRed.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // Customer Header Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.1f),
                        spotColor = if (isDebt) DangerRed.copy(alpha = 0.25f) else SuccessGreen.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isDebt) DangerRed.copy(alpha = 0.25f) else SuccessGreen.copy(alpha = 0.25f)
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (customer.phone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (customer.phone.isNotBlank()) customer.phone else "بدون هاتف مسجل",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (customer.phone.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (customer.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = customer.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Balance Box
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isDebt) "المبلغ المطلوب (عليه):" else "الرصيد المتبقي:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${DaftarKashViewModel.formatMoney(balance)} $currency",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isDebt) DangerRed else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Clean Action Buttons Toolbar (No purchases/POS, focused on Debt, Payment, PDF, WhatsApp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Add Debt
                ToolActionButton(
                    icon = Icons.Default.Add,
                    label = "دين 🔴",
                    containerColor = DangerRed,
                    contentColor = Color.White,
                    onClick = { onOpenAddTx("DEBT", "", "") },
                    modifier = Modifier.weight(1f)
                )

                // 2. Add Payment
                ToolActionButton(
                    icon = Icons.Default.Remove,
                    label = "سداد 🟢",
                    containerColor = SuccessGreen,
                    contentColor = Color.White,
                    onClick = { onOpenAddTx("PAYMENT", "", "") },
                    modifier = Modifier.weight(1f)
                )

                // 3. PDF Statement Export
                ToolActionButton(
                    icon = Icons.Outlined.PictureAsPdf,
                    label = "كشف PDF",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    onClick = exportPdfAction,
                    modifier = Modifier.weight(1.1f)
                )

                // 4. WhatsApp statement
                ToolActionButton(
                    icon = Icons.Outlined.Chat,
                    label = "واتساب",
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White,
                    onClick = { showStatementSheet = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "سجل فواتير وحركات العميل",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Badge(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = "${transactions.size} حركة",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transactions Timeline
            if (transactions.isEmpty()) {
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
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد حركات أو فواتير مسجلة لهذا العميل.\nاضغط على «دين» أو «سداد» لإضافة حركة جديدة.",
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
                        items = transactions,
                        key = { it.id },
                        contentType = { "tx_card" }
                    ) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            currency = currency,
                            onClick = { selectedTransactionForDetail = tx },
                            onDelete = { viewModel.deleteTransaction(tx) }
                        )
                    }
                }
            }
        }
    }

    // Comprehensive Statement Modal BottomSheet
    if (showStatementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatementSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📋 إرسال كشف حساب العميل",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showStatementSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDebt) DangerRed.copy(alpha = 0.10f) else SuccessGreen.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDebt) DangerRed.copy(alpha = 0.3f) else SuccessGreen.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (customer.phone.isNotBlank()) customer.phone else "بدون هاتف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${DaftarKashViewModel.formatMoney(balance)} $currency",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDebt) DangerRed else SuccessGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "اختر طريقة المشاركة والتواصل:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 1: PDF Statement
                StatementOptionCard(
                    icon = Icons.Outlined.PictureAsPdf,
                    iconTint = DangerRed,
                    iconBg = DangerRed.copy(alpha = 0.15f),
                    title = "مشاركة كشف حساب رسمي كملف PDF",
                    subtitle = "توليد ملف PDF جاهز للطباعة والمشاركة المباشرة",
                    onClick = {
                        showStatementSheet = false
                        exportPdfAction()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: WhatsApp Full Detailed Statement
                StatementOptionCard(
                    icon = Icons.Outlined.Chat,
                    iconTint = Color(0xFF25D366),
                    iconBg = Color(0xFF25D366).copy(alpha = 0.15f),
                    title = "إرسال كشف حساب مفصل عبر واتساب",
                    subtitle = "تقرير نصي شامل بالحركات والمبلغ المتبقي للعميل",
                    onClick = {
                        showStatementSheet = false
                        val message = StatementPdfHelper.buildFullStatementWhatsAppMessage(
                            customer = customer,
                            balance = balance,
                            currency = currency,
                            storeName = storeName,
                            storePhone = storePhone,
                            transactions = transactions
                        )
                        StatementPdfHelper.sendWhatsAppDirect(context, customer.phone, message)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 3: Copy text to clipboard
                StatementOptionCard(
                    icon = Icons.Outlined.ContentCopy,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    title = "نسخ نص كشف الحساب للحافظة",
                    subtitle = "نسخ الرسالة لاستخدامها في أي تطبيق آخر",
                    onClick = {
                        showStatementSheet = false
                        val message = StatementPdfHelper.buildFullStatementWhatsAppMessage(
                            customer = customer,
                            balance = balance,
                            currency = currency,
                            storeName = storeName,
                            storePhone = storePhone,
                            transactions = transactions
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("كشف حساب", message)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "تم نسخ كشف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Edit Customer Dialog
    if (showEditCustomerDialog) {
        var editName by remember { mutableStateOf(customer.name) }
        var editPhone by remember { mutableStateOf(customer.phone) }
        var editNotes by remember { mutableStateOf(customer.notes) }

        AlertDialog(
            onDismissRequest = { showEditCustomerDialog = false },
            title = { Text("تعديل بيانات العميل") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("اسم العميل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("ملاحظات") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateCustomer(
                                customer.copy(
                                    name = editName.trim(),
                                    phone = editPhone.trim(),
                                    notes = editNotes.trim()
                                )
                            )
                            showEditCustomerDialog = false
                        }
                    }
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCustomerDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("حذف العميل وحركاته") },
            text = {
                Text("هل أنت متأكد من حذف العميل «${customer.name}» وجميع فواتيره وديونه المسجلة؟\nلا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        showDeleteConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("نعم، حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Calculator Dialog with Direct "تسجيل كدين"
    if (showCalculatorDialog) {
        CalculatorDialog(
            customerName = customer.name,
            onDismiss = { showCalculatorDialog = false },
            onUseAsDebt = { amount, note ->
                showCalculatorDialog = false
                val amtStr = if (amount > 0) {
                    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
                } else ""
                val autoNote = if (note.isNotBlank()) "حساب: $note" else ""
                onOpenAddTx("DEBT", amtStr, autoNote)
            }
        )
    }

    // Invoice / Transaction Detail Dialog (عرض الفاتورة عند الضغط عليها)
    selectedTransactionForDetail?.let { selectedTx ->
        InvoiceDetailDialog(
            transaction = selectedTx,
            customer = customer,
            currentCustomerBalance = balance,
            currency = currency,
            storeName = storeName,
            storePhone = storePhone,
            onDismiss = { selectedTransactionForDetail = null },
            onDeleteTransaction = {
                viewModel.deleteTransaction(selectedTx)
                selectedTransactionForDetail = null
            }
        )
    }
}

@Composable
fun ToolActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
        modifier = modifier.height(46.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatementOptionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
