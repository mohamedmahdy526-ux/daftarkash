package com.example.daftarkash.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.data.repository.CustomerWithBalance
import com.example.daftarkash.data.repository.LedgerMetrics
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.DangerRedDark
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.theme.SuccessGreenDark
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import com.example.daftarkash.util.MerchantReportHelper
import com.example.daftarkash.util.StatementPdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportReportsDialog(
    storeName: String,
    storePhone: String,
    currency: String,
    customersWithBalances: List<CustomerWithBalance>,
    allTransactions: List<Transaction>,
    metrics: LedgerMetrics,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var currentActionName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
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
                .testTag("export_reports_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تصدير التقارير والشيتات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "شيتات Excel و تقارير PDF لجميع العملاء",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
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

                // Summary Stats Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "فلوس السوق",
                                style = MaterialTheme.typography.bodySmall,
                                color = DangerRedDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${DaftarKashViewModel.formatMoney(metrics.totalMarketDebt)} $currency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = DangerRed
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "إجمالي العملاء",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${metrics.totalCustomersCount} عميل",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "إجمالي الفواتير",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${allTransactions.size} حركة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isGenerating) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = currentActionName.ifBlank { "جاري إنشاء وتجهيز الملف..." },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 1. Excel Export Option Card
                ExportOptionCard(
                    title = "تصدير شيت إكسيل تفصيلي (Excel / .CSV)",
                    description = "شيت شامل لأرصدة جميع العملاء وسجل جميع الفواتير والحركات التفصيلية، جاهز للفتح فوراً في Excel و Google Sheets مع دعم اللغة العربية.",
                    badge = "شيت Excel 📗",
                    badgeColor = SuccessGreen,
                    icon = Icons.Outlined.TableChart,
                    enabled = !isGenerating,
                    testTag = "btn_export_excel",
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            currentActionName = "جاري إنشاء شيت Excel التفصيلي..."
                            val file = withContext(Dispatchers.IO) {
                                MerchantReportHelper.exportDetailedMerchantExcel(
                                    context = context,
                                    storeName = storeName,
                                    storePhone = storePhone,
                                    currency = currency,
                                    customersWithBalances = customersWithBalances,
                                    allTransactions = allTransactions,
                                    metrics = metrics
                                )
                            }
                            isGenerating = false
                            if (file != null && file.exists()) {
                                MerchantReportHelper.shareReportFile(
                                    context = context,
                                    file = file,
                                    mimeType = "text/csv",
                                    chooserTitle = "شيت إكسيل حسابات التاجر - $storeName"
                                )
                                onDismiss()
                            } else {
                                Toast.makeText(context, "حدث خطأ أثناء تصدير شيت الإكسيل", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. All Customers Master PDF Report
                ExportOptionCard(
                    title = "تصدير تقرير شامل PDF لجميع العملاء",
                    description = "ملف PDF منسق وجاهز للطباعة أو الإرسال، يضم ملخص كشف الحساب لجميع العملاء وأرصدتهم الحالية ومؤشرات الديون.",
                    badge = "ملف PDF 📕",
                    badgeColor = DangerRed,
                    icon = Icons.Outlined.PictureAsPdf,
                    enabled = !isGenerating,
                    testTag = "btn_export_all_pdf",
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            currentActionName = "جاري رسم وتجهيز تقرير PDF الشامل..."
                            val file = withContext(Dispatchers.IO) {
                                MerchantReportHelper.generateAllCustomersPdfReport(
                                    context = context,
                                    storeName = storeName,
                                    storePhone = storePhone,
                                    currency = currency,
                                    customersWithBalances = customersWithBalances,
                                    metrics = metrics
                                )
                            }
                            isGenerating = false
                            if (file != null && file.exists()) {
                                MerchantReportHelper.shareReportFile(
                                    context = context,
                                    file = file,
                                    mimeType = "application/pdf",
                                    chooserTitle = "تقرير كشف حساب عام PDF - $storeName"
                                )
                                onDismiss()
                            } else {
                                Toast.makeText(context, "حدث خطأ أثناء إنشاء ملف PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Quick WhatsApp Merchant Summary Message
                ExportOptionCard(
                    title = "مشاركة ملخص الحسابات عبر واتساب",
                    description = "إرسال نص منسق وجاهز عبر تطبيق واتساب يحتوي على إجمالي ديون السوق وقائمة بأعلى العملاء المدينين.",
                    badge = "واتساب 📲",
                    badgeColor = Color(0xFF25D366),
                    icon = Icons.Outlined.Chat,
                    enabled = !isGenerating,
                    testTag = "btn_export_whatsapp_summary",
                    onClick = {
                        val message = MerchantReportHelper.buildMerchantSummaryWhatsAppMessage(
                            storeName = storeName,
                            storePhone = storePhone,
                            currency = currency,
                            metrics = metrics,
                            customersWithBalances = customersWithBalances
                        )
                        StatementPdfHelper.sendWhatsAppDirect(context, storePhone, message)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    description: String,
    badge: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, badgeColor.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تصدير ومشاركة الآن ⬅️",
                    style = MaterialTheme.typography.bodySmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
