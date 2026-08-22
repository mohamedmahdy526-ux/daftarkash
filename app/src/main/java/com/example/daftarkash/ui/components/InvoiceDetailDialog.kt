package com.example.daftarkash.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.DangerRedDark
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.theme.SuccessGreenDark
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import com.example.daftarkash.util.StatementPdfHelper
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InvoiceDetailDialog(
    transaction: Transaction,
    customer: Customer?,
    currentCustomerBalance: Double? = null,
    currency: String,
    storeName: String = "ماركت أولاد ماهر",
    storePhone: String = "",
    onDismiss: () -> Unit,
    onDeleteTransaction: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isDebt = transaction.type == "DEBT"
    val formattedDate = remember(transaction.timestamp, transaction.date) {
        if (transaction.timestamp > 100000000000L) {
            try {
                SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar")).format(Date(transaction.timestamp))
            } catch (e: Exception) {
                transaction.date
            }
        } else {
            transaction.date
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("invoice_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Top Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDebt) DangerRed.copy(alpha = 0.14f) else SuccessGreen.copy(alpha = 0.14f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDebt) Icons.Outlined.ReceiptLong else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (isDebt) DangerRed else SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isDebt) "فاتورة سحب بضاعة" else "سند قبض وسداد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "رقم الفاتورة: #${transaction.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer Info Card
                if (customer != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "العميل: ${customer.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (customer.phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = customer.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (customer.phone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "اتصال",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Main Amount Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDebt) Color(0xFFFFF1F2) else Color(0xFFECFDF5),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isDebt) DangerRed.copy(alpha = 0.35f) else SuccessGreen.copy(alpha = 0.35f)
                    ),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isDebt) "قيمة الفاتورة المطلوبة (دين)" else "المبلغ المدفوع (سداد)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDebt) DangerRedDark else SuccessGreenDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${if (isDebt) "+" else "-"}${DaftarKashViewModel.formatMoney(transaction.amount)} $currency",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDebt) DangerRed else SuccessGreen,
                            fontSize = 28.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Invoice Details Breakdown
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Description / Notes / Items
                        if (transaction.description.isNotBlank()) {
                            Text(
                                text = "📝 بيان الفاتورة / تفاصيل الحساب:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = transaction.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Date & Time
                        DetailRow(
                            label = "📅 تاريخ ووقت الحركة:",
                            value = formattedDate
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Transaction type / payment method
                        if (!isDebt) {
                            val methodText = when (transaction.paymentMethod) {
                                "VODAFONE_CASH" -> "محفظة إلكترونية (فودافون كاش)"
                                "BANK" -> "تحويل بنكي / إنستاباي"
                                "CARD" -> "بطاقة بنكية / شبكة"
                                else -> "كاش نقدياً 💵"
                            }
                            DetailRow(label = "💳 طريقة السداد:", value = methodText)
                        } else {
                            DetailRow(label = "📌 نوع المعاملة:", value = "آجل على الحساب (دين 🔴)")
                        }

                        // Overall balance context if available
                        if (currentCustomerBalance != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val isOverallDebt = currentCustomerBalance > 0
                            DetailRow(
                                label = "💰 إجمالي رصيد العميل الحالي:",
                                value = "${DaftarKashViewModel.formatMoney(currentCustomerBalance)} $currency",
                                valueColor = if (isOverallDebt) DangerRed else SuccessGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Official Store Rubber Stamp (ختم المحل المعتمد)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OfficialStoreStamp(
                                storeName = storeName,
                                stampType = if (isDebt) StampType.DEBT_INVOICE else StampType.PAYMENT_RECEIPT,
                                size = 115.dp,
                                rotation = -6f,
                                dateString = formattedDate.take(10)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: WhatsApp Share + Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WhatsApp Share Button
                    Button(
                        onClick = {
                            if (customer != null) {
                                val message = buildSingleInvoiceMessage(
                                    customer = customer,
                                    transaction = transaction,
                                    formattedDate = formattedDate,
                                    currentBalance = currentCustomerBalance,
                                    currency = currency,
                                    storeName = storeName,
                                    storePhone = storePhone
                                )
                                StatementPdfHelper.sendWhatsAppDirect(context, customer.phone, message)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("btn_share_invoice_whatsapp"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Chat,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إرسال للعميل (واتساب)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    // Delete Button
                    if (onDeleteTransaction != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("btn_delete_invoice"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "حذف",
                                tint = DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف الفاتورة") },
            text = {
                Text("هل أنت متأكد من حذف هذه الفاتورة بمبلغ ${DaftarKashViewModel.formatMoney(transaction.amount)} $currency؟\nسيتم تحديث رصيد العميل فوراً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDismiss()
                        onDeleteTransaction?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontSize = 12.sp,
            textAlign = TextAlign.End
        )
    }
}

private fun buildSingleInvoiceMessage(
    customer: Customer,
    transaction: Transaction,
    formattedDate: String,
    currentBalance: Double?,
    currency: String,
    storeName: String,
    storePhone: String
): String {
    val isDebt = transaction.type == "DEBT"
    val sb = StringBuilder()
    sb.append(if (isDebt) "🧾 *فاتورة سحب بضاعة (دين)*\n" else "🟢 *سند استلام وسداد دفعة*\n")
    sb.append("🏪 *المحل:* $storeName\n")
    if (storePhone.isNotBlank()) sb.append("📞 *هاتف:* $storePhone\n")
    sb.append("👤 *العميل:* ${customer.name}\n")
    sb.append("🔢 *رقم الفاتورة:* #${transaction.id}\n")
    sb.append("📅 *التاريخ:* $formattedDate\n")
    sb.append("━━━━━━━━━━━━━━━━━━━━\n")
    sb.append(if (isDebt) "💰 *قيمة الفاتورة:* " else "💵 *المبلغ المسدد:* ")
    sb.append("*${DaftarKashViewModel.formatMoney(transaction.amount)} $currency*\n")
    
    if (transaction.description.isNotBlank()) {
        sb.append("📝 *بيان الفاتورة / الأصناف:* ${transaction.description}\n")
    }
    
    if (!isDebt) {
        val methodText = when (transaction.paymentMethod) {
            "VODAFONE_CASH" -> "محفظة إلكترونية (فودافون كاش)"
            "BANK" -> "تحويل بنكي / إنستاباي"
            "CARD" -> "بطاقة بنكية"
            else -> "نقداً (كاش) 💵"
        }
        sb.append("💳 *طريقة السداد:* $methodText\n")
    }
    
    if (currentBalance != null) {
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        if (currentBalance > 0) {
            sb.append("📊 *إجمالي الرصيد المتبقي المطلوب بعد هذه الحركة:* *${DaftarKashViewModel.formatMoney(currentBalance)} $currency*\n")
        } else if (currentBalance == 0.0) {
            sb.append("✅ *الحساب خالص بالكامل (0 $currency)*. شكراً لتعاملكم الراقي! ✨\n")
        }
    }
    
    sb.append("━━━━━━━━━━━━━━━━━━━━\n")
    sb.append("⚜️ *[ خَتْم الاعْتِمَاد الرَسْمِي : $storeName ]* ⚜️\n")
    sb.append("نسعد دائماً بخدمتكم! 🌹")
    return sb.toString()
}
