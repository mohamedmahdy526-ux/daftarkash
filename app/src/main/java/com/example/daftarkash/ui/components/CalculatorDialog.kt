package com.example.daftarkash.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.daftarkash.ui.theme.DangerRed
import java.text.DecimalFormat

@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    customerName: String? = null,
    onUseAsDebt: ((amount: Double, note: String) -> Unit)? = null
) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }

    val formatter = remember { DecimalFormat("#,###.##") }

    fun calculate(op1: Double, op2: Double, op: String): Double {
        return when (op) {
            "+" -> op1 + op2
            "-" -> op1 - op2
            "×" -> op1 * op2
            "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
            else -> op2
        }
    }

    fun onNumberClick(num: String) {
        if (isNewNumber) {
            display = num
            isNewNumber = false
        } else {
            if (display == "0") {
                display = num
            } else if (display.length < 12) {
                display += num
            }
        }
    }

    fun onDotClick() {
        if (isNewNumber) {
            display = "0."
            isNewNumber = false
        } else if (!display.contains(".")) {
            display += "."
        }
    }

    fun onOperatorClick(op: String) {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        if (operand1 == null) {
            operand1 = currentVal
            expression = "${formatter.format(currentVal)} $op"
        } else if (pendingOp != null && !isNewNumber) {
            val result = calculate(operand1!!, currentVal, pendingOp!!)
            operand1 = result
            display = if (result.isNaN()) "خطأ" else formatter.format(result)
            expression = "${formatter.format(result)} $op"
        } else {
            expression = "${formatter.format(operand1!!)} $op"
        }
        pendingOp = op
        isNewNumber = true
    }

    fun onPercentClick() {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        val percentVal = if (operand1 != null) {
            (operand1!! * currentVal) / 100.0
        } else {
            currentVal / 100.0
        }
        display = formatter.format(percentVal)
        isNewNumber = true
    }

    fun onEqualClick() {
        val currentVal = display.toDoubleOrNull() ?: 0.0
        if (operand1 != null && pendingOp != null) {
            val result = calculate(operand1!!, currentVal, pendingOp!!)
            expression = "${formatter.format(operand1!!)} $pendingOp ${formatter.format(currentVal)} ="
            display = if (result.isNaN()) "خطأ" else formatter.format(result)
            operand1 = null
            pendingOp = null
            isNewNumber = true
        }
    }

    fun onClearClick() {
        display = "0"
        expression = ""
        operand1 = null
        pendingOp = null
        isNewNumber = true
    }

    fun onBackspaceClick() {
        if (!isNewNumber && display.isNotEmpty() && display != "0" && display != "خطأ") {
            display = display.dropLast(1)
            if (display.isEmpty() || display == "-") {
                display = "0"
                isNewNumber = true
            }
        }
    }

    fun getCurrentComputedAmount(): Double {
        val currentVal = display.replace(",", "").toDoubleOrNull() ?: 0.0
        return if (operand1 != null && pendingOp != null) {
            val res = calculate(operand1!!, currentVal, pendingOp!!)
            if (!res.isNaN() && res > 0) res else 0.0
        } else {
            if (currentVal > 0) currentVal else 0.0
        }
    }

    fun getCalculationNote(): String {
        return if (expression.isNotBlank()) {
            val currentVal = display.toDoubleOrNull() ?: 0.0
            if (operand1 != null && pendingOp != null) {
                "${expression} ${formatter.format(currentVal)}"
            } else {
                expression.replace(" =", "")
            }
        } else ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("calculator_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "الآلة الحاسبة السريعة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!customerName.isNullOrBlank()) {
                                Text(
                                    text = "العميل: $customerName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display Area
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = expression.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = display,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 30.sp,
                            maxLines = 1
                        )
                    }
                }

                // Calculator Keypad Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: C, ⌫, %, ÷
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcButton(text = "C", type = ButtonType.CLEAR, modifier = Modifier.weight(1f), onClick = { onClearClick() })
                        CalcButton(text = "⌫", type = ButtonType.BACKSPACE, modifier = Modifier.weight(1f), onClick = { onBackspaceClick() })
                        CalcButton(text = "%", type = ButtonType.OPERATOR, modifier = Modifier.weight(1f), onClick = { onPercentClick() })
                        CalcButton(text = "÷", type = ButtonType.OPERATOR, modifier = Modifier.weight(1f), onClick = { onOperatorClick("÷") })
                    }

                    // Row 2: 7, 8, 9, ×
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcButton(text = "7", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("7") })
                        CalcButton(text = "8", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("8") })
                        CalcButton(text = "9", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("9") })
                        CalcButton(text = "×", type = ButtonType.OPERATOR, modifier = Modifier.weight(1f), onClick = { onOperatorClick("×") })
                    }

                    // Row 3: 4, 5, 6, -
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcButton(text = "4", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("4") })
                        CalcButton(text = "5", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("5") })
                        CalcButton(text = "6", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("6") })
                        CalcButton(text = "-", type = ButtonType.OPERATOR, modifier = Modifier.weight(1f), onClick = { onOperatorClick("-") })
                    }

                    // Row 4: 1, 2, 3, +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcButton(text = "1", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("1") })
                        CalcButton(text = "2", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("2") })
                        CalcButton(text = "3", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("3") })
                        CalcButton(text = "+", type = ButtonType.OPERATOR, modifier = Modifier.weight(1f), onClick = { onOperatorClick("+") })
                    }

                    // Row 5: 0, 00, ., =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CalcButton(text = "0", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("0") })
                        CalcButton(text = "00", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onNumberClick("00") })
                        CalcButton(text = ".", type = ButtonType.NUMBER, modifier = Modifier.weight(1f), onClick = { onDotClick() })
                        CalcButton(text = "=", type = ButtonType.EQUAL, modifier = Modifier.weight(1f), onClick = { onEqualClick() })
                    }
                }

                // Compact Button for "تسجيل كدين" only
                if (onUseAsDebt != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val amount = getCurrentComputedAmount()
                            val note = getCalculationNote()
                            onUseAsDebt(amount, note)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_calc_record_debt"),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تسجيل الناتج كدين 🔴",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

enum class ButtonType {
    NUMBER, OPERATOR, EQUAL, CLEAR, BACKSPACE
}

@Composable
private fun CalcButton(
    text: String,
    type: ButtonType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = when (type) {
        ButtonType.EQUAL -> MaterialTheme.colorScheme.primary
        ButtonType.OPERATOR -> MaterialTheme.colorScheme.primaryContainer
        ButtonType.CLEAR, ButtonType.BACKSPACE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ButtonType.NUMBER -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (type) {
        ButtonType.EQUAL -> MaterialTheme.colorScheme.onPrimary
        ButtonType.OPERATOR -> MaterialTheme.colorScheme.onPrimaryContainer
        ButtonType.CLEAR, ButtonType.BACKSPACE -> MaterialTheme.colorScheme.onErrorContainer
        ButtonType.NUMBER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (type == ButtonType.OPERATOR || type == ButtonType.EQUAL) 18.sp else 16.sp
            )
        }
    }
}
