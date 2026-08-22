package com.example.daftarkash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StampType {
    DEBT_INVOICE,   // فاتورة سحب آجل
    PAYMENT_RECEIPT,// سند قبض وسداد
    VERIFIED_STATEMENT // كشف حساب معتمد
}

/**
 * Official Store Rubber Stamp / Seal (ختم رسمي تجاري لماركت أولاد ماهر)
 * Renders an authentic vintage-style circular merchant stamp with double rings, stars, and verification text.
 */
@Composable
fun OfficialStoreStamp(
    modifier: Modifier = Modifier,
    storeName: String = "ماركت أولاد ماهر",
    stampType: StampType = StampType.VERIFIED_STATEMENT,
    size: Dp = 110.dp,
    rotation: Float = -7f,
    dateString: String? = null
) {
    val stampColor = when (stampType) {
        StampType.DEBT_INVOICE -> Color(0xFFB91C1C) // Deep Crimson Red
        StampType.PAYMENT_RECEIPT -> Color(0xFF047857) // Deep Emerald Green
        StampType.VERIFIED_STATEMENT -> Color(0xFF1E40AF) // Deep Royal Blue
    }

    val mainTitle = when (stampType) {
        StampType.DEBT_INVOICE -> "فاتورة معتمدة"
        StampType.PAYMENT_RECEIPT -> "سند قبض مسدد"
        StampType.VERIFIED_STATEMENT -> "كشف حساب معتمد"
    }

    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Stamp Ring Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthOuter = 2.5f
            val strokeWidthInner = 1.2f
            val strokeWidthDashed = 1.0f
            val radius = size.toPx() / 2f

            // 1. Outer Solid Circle
            drawCircle(
                color = stampColor.copy(alpha = 0.85f),
                radius = radius - strokeWidthOuter,
                style = Stroke(width = strokeWidthOuter)
            )

            // 2. Middle Dashed Circle
            drawCircle(
                color = stampColor.copy(alpha = 0.65f),
                radius = radius - 7.dp.toPx(),
                style = Stroke(
                    width = strokeWidthDashed,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            )

            // 3. Inner Solid Circle
            drawCircle(
                color = stampColor.copy(alpha = 0.75f),
                radius = radius - 11.dp.toPx(),
                style = Stroke(width = strokeWidthInner)
            )
        }

        // Inner Content of the Official Stamp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top: Store Name
            Text(
                text = "★ $storeName ★",
                color = stampColor,
                fontWeight = FontWeight.Black,
                fontSize = if (size > 100.dp) 8.5.sp else 7.sp,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Center Badge: Main Status Box
            Surface(
                shape = CircleShape,
                color = stampColor.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, stampColor.copy(alpha = 0.6f)),
                modifier = Modifier.padding(vertical = 1.dp)
            ) {
                Text(
                    text = mainTitle,
                    color = stampColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (size > 100.dp) 9.5.sp else 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Sub: Commercial Seal / Date
            Text(
                text = dateString ?: "إدارة الحسابات والآجل",
                color = stampColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                fontSize = if (size > 100.dp) 7.5.sp else 6.5.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "OFFICIAL SEAL",
                color = stampColor.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 5.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
