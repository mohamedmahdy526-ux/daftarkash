package com.example.daftarkash.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daftarkash.ui.components.ExportReportsDialog
import com.example.daftarkash.ui.theme.*
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DaftarKashViewModel,
    onNavigateToScriptCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeName by viewModel.storeName.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val googleScriptUrl by viewModel.googleScriptUrl.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val activeBrand by viewModel.brandTheme.collectAsState()
    val isSoundEnabled by viewModel.isSoundEffectsEnabled.collectAsState()
    val syncStatus by viewModel.cloudSyncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val activeFontScale by viewModel.fontScaleRaw.collectAsState()

    val customersWithBalances by viewModel.customersWithBalances.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val metrics by viewModel.ledgerMetrics.collectAsState()

    var nameInput by remember(storeName) { mutableStateOf(storeName) }
    var phoneInput by remember(storePhone) { mutableStateOf(storePhone) }
    var currencyInput by remember(currency) { mutableStateOf(currency) }
    var scriptUrlInput by remember(googleScriptUrl) { mutableStateOf(googleScriptUrl) }

    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp)
    ) {
        Text(
            text = "⚙️ إعدادات المحل والنسخ السحابي",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 1. Store Profile Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "بيانات المحل",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("اسم المحل / البقالة") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("رقم هاتف المحل") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currencyInput,
                    onValueChange = { currencyInput = it },
                    label = { Text("رمز العملة (مثال: ج.م، ريال، د.ك)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.saveStoreSettings(nameInput, phoneInput, currencyInput, scriptUrlInput)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_store_settings")
                ) {
                    Text("حفظ بيانات المحل ✅", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Google Sheets Cloud Sync Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المزامنة الحية مع Google Sheets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Badge(
                        containerColor = if (syncStatus == "synced") SuccessGreen.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                        contentColor = if (syncStatus == "synced") SuccessGreen else DangerRed
                    ) {
                        Text(
                            text = if (syncStatus == "synced") "مزامنة تلقائية نشطة 🟢" else if (syncStatus == "syncing") "جاري المزامنة... ⏳" else "أوفلاين 📡",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "⚡ المزامنة تعمل تلقائياً وفورياً في الخلفية: أي تعديل في التطبيق يرفع للشيت فوراً، وأي تعديل في الشيت يظهر في التطبيق تلقائياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (lastSyncTime.isNotBlank()) {
                    Text(
                        text = "آخر تحديث تلقائي: $lastSyncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scriptUrlInput,
                    onValueChange = { scriptUrlInput = it },
                    label = { Text("رابط Google Apps Script Webhook URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.saveStoreSettings(nameInput, phoneInput, currencyInput, scriptUrlInput)
                        viewModel.manualFullTwoWaySync()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_sheet_url")
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ الرابط وبدء المزامنة فوراً 💾", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToScriptCode,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("عرض ونسخ كود Apps Script المحدث 📋", fontSize = 12.sp)
                }
            }
        }

        // 3. Reports & Excel/PDF Exports Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تقارير وتصدير الحسابات (Excel & PDF) 📊",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تصدير شيت إكسيل (.csv) تفصيلي يضم أرصدة كل العملاء وسجل جميع الفواتير، أو إنشاء تقرير PDF رسمي شامل لجميع العملاء.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_settings_export_reports")
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فتح نافذة التصدير والتقارير 📑", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Unified Identity & Appearance Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "المظهر والنظام",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "الوضع الليلي (Dark Mode)", fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isDark) "مفعل (خلفية كحلية عميقة هادئة)" else "مفعل الوضع النهاري الفاتح",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.toggleDarkMode() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Sound Effects Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSoundEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                            contentDescription = null,
                            tint = if (isSoundEnabled) SuccessGreen else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "المؤثرات والأصوات التفاعلية 🔊", fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isSoundEnabled) "مفعلة (صوت الكاش، بيب الباركود، صوت تسجيل الدين)" else "الأصوات مكتومة",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isSoundEnabled,
                        onCheckedChange = { viewModel.toggleSoundEffects() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "حجم خط النصوص:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("sm" to "صغير", "md" to "متوسط", "lg" to "كبير", "xl" to "ضخم").forEach { (scaleKey, scaleLabel) ->
                        val isCurrent = activeFontScale == scaleKey
                        Button(
                            onClick = { viewModel.setFontScale(scaleKey) },
                            shape = RoundedCornerShape(8.dp),
                            colors = if (isCurrent) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = scaleLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 5. About & App Info Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "دفتر كاش 📒🛒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "الإصدار 1.0 • حفظ محلي فوري ومزامنة سحابية",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showExportDialog) {
        ExportReportsDialog(
            storeName = storeName,
            storePhone = storePhone,
            currency = currency,
            customersWithBalances = customersWithBalances,
            allTransactions = allTransactions,
            metrics = metrics,
            onDismiss = { showExportDialog = false }
        )
    }
}
