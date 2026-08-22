package com.example.daftarkash.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daftarkash.ui.theme.DangerRed
import com.example.daftarkash.ui.theme.SuccessGreen
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import java.util.Locale

// دالة تحويل الأرقام المشرقية (٠-٩) إلى أرقام قياسية (0-9)
private fun normalizeArabicDigits(input: String): String {
    val easternArabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    var result = input
    for (i in easternArabic.indices) {
        result = result.replace(easternArabic[i], ('0' + i))
    }
    return result
}

// دالة ذكية للفرز بين الكميات (مثل: 2 علبة، 3 كيلو) والمبلغ الإجمالي (مثل: 85 جنيه)
private fun parseVoiceInput(rawText: String): Pair<String, String> {
    val normalized = normalizeArabicDigits(rawText).trim()
    if (normalized.isBlank()) return Pair("", "")

    // كلمات تدل على أن الرقم كمية وليس سعر
    val quantityUnits = listOf("كيلو", "كجم", "علبة", "علب", "باكو", "بواكي", "كيس", "أكياس", "كرتونة", "كراتين", "قطعة", "قطع", "زجاجة", "ازازة", "لتر", "لترات")

    // استخراج الأرقام المحتملة
    val numberRegex = Regex("""(?:\b|بـ|بقيمة|بمبلغ|حسابهم|إجمالي)?\s*(\d+(?:\.\d+)?)\s*(?:جنيه|جنية|ج\.م|ج|قرش)?""")
    val matches = numberRegex.findAll(normalized).toList()

    var extractedPrice: String? = null
    var matchToRemove: MatchResult? = null

    // نفحص الأرقام من الأخير للأول
    for (m in matches.reversed()) {
        val numStr = m.groupValues[1]
        val startIndex = m.range.first
        val endIndex = m.range.last

        // نفحص الكلمة التي تلي الرقم مباشرة
        val textAfter = normalized.substring(minOf(endIndex + 1, normalized.length)).trimStart()
        val isQuantity = quantityUnits.any { textAfter.startsWith(it) }

        if (!isQuantity) {
            extractedPrice = numStr
            matchToRemove = m
            break
        }
    }

    return if (extractedPrice != null && matchToRemove != null) {
        var cleanedDesc = normalized
            .removeRange(matchToRemove.range)
            .replace("جنيه", "")
            .replace("جنية", "")
            .replace("ج.م", "")
            .replace("بمبلغ", "")
            .replace("بقيمة", "")
            .replace("بـ", "")
            .replace("حسابهم", "")
            .replace("إجمالي", "")
            .trim()
            .trim(',', '+', '-', ' ')

        if (cleanedDesc.isBlank()) {
            cleanedDesc = "بضاعة"
        }
        Pair(cleanedDesc, extractedPrice)
    } else {
        Pair(normalized, "")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    initialType: String = "DEBT",
    initialAmount: String = "",
    initialDescription: String = "",
    customerId: Long,
    customerName: String,
    viewModel: DaftarKashViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var description by remember { mutableStateOf(initialDescription) }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var lastVoiceCalculation by remember { mutableStateOf<String?>(null) }
    var voiceCount by remember { mutableStateOf(0) }

    val currency by viewModel.currency.collectAsState()
    val isDebt = type == "DEBT"

    // حساب الرصيد الحالي للعميل لعرض المقارنة قبل وبعد
    val customersWithBalances by viewModel.customersWithBalances.collectAsState()
    val currentCustomer = customersWithBalances.find { it.customer.id == customerId }
    val currentBalance = currentCustomer?.balance ?: 0.0

    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val projectedBalance = if (isDebt) currentBalance + enteredAmount else currentBalance - enteredAmount

    // مشغل التعرف على الصوت
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                val (parsedDesc, parsedAmt) = parseVoiceInput(spoken)

                // 1. الدمج والإلحاق التراكمي للأصناف (Append)
                if (parsedDesc.isNotBlank()) {
                    description = if (description.isBlank() || description == "بضاعة") {
                        parsedDesc
                    } else {
                        "$description + $parsedDesc"
                    }
                }

                // 2. الجمع التراكمي للمبالغ
                if (parsedAmt.isNotBlank()) {
                    val newAmt = parsedAmt.toDoubleOrNull() ?: 0.0
                    val oldAmt = amountText.toDoubleOrNull() ?: 0.0
                    val combinedAmt = oldAmt + newAmt
                    amountText = if (combinedAmt % 1.0 == 0.0) combinedAmt.toInt().toString() else "%.2f".format(Locale.US, combinedAmt)

                    lastVoiceCalculation = if (oldAmt > 0.0) {
                        "%.2f + %.2f = %.2f %s".format(Locale.US, oldAmt, newAmt, combinedAmt, currency)
                    } else {
                        "%.2f %s".format(Locale.US, newAmt, currency)
                    }
                }

                voiceCount++
                Toast.makeText(context, "تمت الإضافة بالصوت 🎙️ (الصنف: $parsedDesc)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // إطلاق المايك بمهلة سكوت 7 ثوانٍ كاملة
    fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن براحتك (مثال: 2 كيلو سكر وشاي بـ 85 جنيه)...")
            // ضبط مهلة السكوت والانتظار لـ 7 ثوانٍ (7000ms)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 7000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "خاصية التعرف على الصوت غير متوفرة بجهازك", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isDebt) "تسجيل دين جديد 🔴" else "تسجيل سداد دفعة 🟢",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebt) DangerRed else SuccessGreen
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Text(
                    text = "العميل: $customerName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Tabs (DEBT / PAYMENT)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    TabButton(
                        label = "دين / سحب (+)",
                        isSelected = isDebt,
                        activeColor = DangerRed,
                        onClick = { type = "DEBT" },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        label = "سداد / دفعة (-)",
                        isSelected = !isDebt,
                        activeColor = SuccessGreen,
                        onClick = { type = "PAYMENT" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 🎙️ Smart Voice Action Button + 🗑️ Reset Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { launchVoiceRecognition() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDebt) DangerRed.copy(alpha = 0.12f) else SuccessGreen.copy(alpha = 0.12f),
                            contentColor = if (isDebt) DangerRed else SuccessGreen
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(
                                width = 1.2.dp,
                                color = if (isDebt) DangerRed.copy(alpha = 0.35f) else SuccessGreen.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("btn_voice_input")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "تسجيل صوتي",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (voiceCount > 0) "إضافة صنف آخر بالصوت 🎙️" else "إدخال صوتي ذكي (7 ثوانٍ) 🎙️",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (amountText.isNotBlank() || description.isNotBlank()) {
                        IconButton(
                            onClick = {
                                amountText = ""
                                description = ""
                                lastVoiceCalculation = null
                                voiceCount = 0
                                Toast.makeText(context, "تم مسح البيانات للبدء من جديد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "مسح الكل",
                                tint = DangerRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("المبلغ المطلوب ($currency)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDebt) DangerRed else SuccessGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    trailingIcon = {
                        IconButton(onClick = { launchVoiceRecognition() }) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "تحدث",
                                tint = if (isDebt) DangerRed else SuccessGreen
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_amount")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Amount Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 20, 50, 100, 200, 500).forEach { num ->
                        SuggestionChip(
                            onClick = {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                val next = current + num
                                amountText = if (next % 1.0 == 0.0) next.toInt().toString() else "%.2f".format(Locale.US, next)
                            },
                            label = { Text("+$num", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("البيان / تفاصيل البضاعة (يُدمج تلقائياً)") },
                    placeholder = { Text(if (isDebt) "مثال: 2 سكر + زيت + شاي" else "مثال: دفعة نقدية") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        IconButton(onClick = { launchVoiceRecognition() }) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "تحدث",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tx_desc")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Description Tags
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickTags = if (isDebt) {
                        listOf("سكر", "زيت", "شاي", "جبنة", "مكرونة", "سجاير", "طلبات منزل", "بضاعة عامة")
                    } else {
                        listOf("دفعة نقدية", "كاش", "فودافون كاش", "حساب أسبوعي", "سداد كامل")
                    }
                    quickTags.forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                description = if (description.isBlank() || description == "بضاعة") tag else "$description + $tag"
                            },
                            label = { Text(tag) }
                        )
                    }
                }

                // Payment Method Selector (if payment)
                if (!isDebt) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "طريقة السداد:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "CASH" to "كاش 💵",
                            "VODAFONE_CASH" to "محفظة 📱",
                            "BANK" to "بنكي 🏦",
                            "CARD" to "شبكة 💳"
                        ).forEach { (mCode, mLabel) ->
                            FilterChip(
                                selected = paymentMethod == mCode,
                                onClick = { paymentMethod = mCode },
                                label = { Text(mLabel, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 📋 LIVE REVIEW / CONFIRMATION SUMMARY CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDebt) DangerRed.copy(alpha = 0.08f) else SuccessGreen.copy(alpha = 0.08f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(
                                if (isDebt) DangerRed.copy(alpha = 0.4f) else SuccessGreen.copy(alpha = 0.4f),
                                if (isDebt) DangerRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isDebt) DangerRed else SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ملخص الفاتورة قبل الحفظ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDebt) DangerRed else SuccessGreen
                                )
                            }
                            if (voiceCount > 0) {
                                Text(
                                    text = "تم بالصوت ($voiceCount إضافة) 🎙️",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🛒 البضاعة: ${if (description.isBlank()) "بضاعة عامة" else description}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "المبلغ: %.2f %s".format(Locale.US, enteredAmount, currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDebt) DangerRed else SuccessGreen
                            )
                        }

                        if (lastVoiceCalculation != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "🧮 الحسبة الصوتية: $lastVoiceCalculation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "📊 حساب العميل: الحالي (%.2f) ➔ سيصبح (%.2f %s)".format(Locale.US, currentBalance, projectedBalance, currency),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Action Button with Dynamic Amount Preview
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            viewModel.addTransaction(
                                customerId = customerId,
                                type = type,
                                amount = amount,
                                description = if (description.isBlank()) "بضاعة" else description,
                                paymentMethod = if (isDebt) "CREDIT" else paymentMethod,
                                onSuccess = onDismiss
                            )
                        } else {
                            Toast.makeText(context, "من فضلك اكتب أو انطق المبلغ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDebt) DangerRed else SuccessGreen
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_save_transaction")
                ) {
                    Text(
                        text = if (enteredAmount > 0) {
                            if (isDebt) "💾 تأكيد وحفظ الدين (%.2f %s)".format(Locale.US, enteredAmount, currency)
                            else "💾 تأكيد تسجيل السداد (%.2f %s)".format(Locale.US, enteredAmount, currency)
                        } else {
                            if (isDebt) "💾 تأكيد وحفظ الدين" else "💾 تأكيد تسجيل السداد"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TabButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) activeColor else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
