package com.example.daftarkash.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatementPdfHelper {

    private fun cleanTransactionDate(tx: Transaction): String {
        if (tx.timestamp > 100000000000L) {
            return try {
                val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar"))
                sdf.format(Date(tx.timestamp))
            } catch (e: Exception) {
                formatRawDateFallback(tx.date)
            }
        }
        return formatRawDateFallback(tx.date)
    }

    private fun formatRawDateFallback(raw: String): String {
        if (raw.isBlank()) return "-"
        if (raw.contains("GMT")) {
            val parts = raw.split("GMT")[0].trim()
            // e.g. "Tue Aug 18 2026 17:33:00"
            return parts.replace("Tue ", "").replace("Wed ", "").replace("Thu ", "")
                .replace("Fri ", "").replace("Sat ", "").replace("Sun ", "").replace("Mon ", "")
        }
        return if (raw.length > 22) raw.take(22) else raw
    }

    fun generateCustomerPdfReport(
        context: Context,
        customer: Customer,
        balance: Double,
        currency: String,
        storeName: String,
        storePhone: String,
        transactions: List<Transaction>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard width
        val pageHeight = 842 // A4 standard height

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 140, 95) // Brand green
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 41, 55)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        val cellCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        val boldRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        val tableLeft = 20f
        val tableRight = 575f

        // Column boundaries in RTL (Right to Left):
        // Col 1 (#): 575 -> 545
        // Col 2 (Date): 545 -> 420
        // Col 3 (Type): 420 -> 345
        // Col 4 (Desc): 345 -> 125
        // Col 5 (Amount): 125 -> 20
        val col1R = 575f
        val col1L = 545f

        val col2R = 545f
        val col2L = 420f

        val col3R = 420f
        val col3L = 345f

        val col4R = 345f
        val col4L = 125f

        val col5R = 125f
        val col5L = 20f

        val rowHeight = 22f
        val totalTransactions = transactions.size

        // Calculate totals across all transactions
        val totalDebts = transactions.filter { it.type == "DEBT" }.sumOf { it.amount }
        val totalPayments = transactions.filter { it.type == "PAYMENT" }.sumOf { it.amount }

        // Pagination calculation
        val rowsPerPageFirst = if (totalTransactions <= 20) 20 else 18
        val rowsPerPageOther = 24
        val totalPages = when {
            totalTransactions <= rowsPerPageFirst -> 1
            else -> 1 + Math.ceil((totalTransactions - rowsPerPageFirst).toDouble() / rowsPerPageOther).toInt()
        }

        var txIndex = 0
        var pageNumber = 1
        val dateNow = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar")).format(Date())

        while (txIndex < totalTransactions || (totalTransactions == 0 && pageNumber == 1)) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var currentY = 20f

            // 1. Top Header Banner
            val headerBg = Paint().apply { color = Color.rgb(243, 244, 246) }
            canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 56f, 6f, 6f, headerBg)
            canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 56f, 6f, 6f, borderPaint)

            canvas.drawText("📋 كشف حساب تفصيلي - $storeName", (pageWidth / 2).toFloat(), currentY + 24f, titlePaint)
            canvas.drawText("هاتف المحل: ${storePhone.ifBlank { "-" }}  |  تاريخ وتوقيت التقرير: $dateNow", (pageWidth / 2).toFloat(), currentY + 44f, subPaint)

            currentY += 66f

            // 2. Customer Info Box (On First Page Only)
            if (pageNumber == 1) {
                val custBoxHeight = 56f
                val custBg = Paint().apply { color = Color.rgb(255, 255, 255) }
                canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + custBoxHeight, 6f, 6f, custBg)
                canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + custBoxHeight, 6f, 6f, borderPaint)

                canvas.drawText("👤 اسم العميل: ${customer.name}", tableRight - 15f, currentY + 23f, boldRightPaint)
                canvas.drawText("📞 رقم الهاتف: ${if (customer.phone.isNotBlank()) customer.phone else "غير مسجل"}", tableRight - 15f, currentY + 43f, cellTextPaint)

                val isDebt = balance > 0
                val balanceBoxPaint = Paint().apply {
                    color = if (isDebt) Color.rgb(254, 242, 242) else Color.rgb(236, 253, 245)
                }
                val balanceBorder = Paint().apply {
                    color = if (isDebt) Color.rgb(252, 165, 165) else Color.rgb(167, 243, 208)
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                val balBadgeLeft = tableLeft + 12f
                val balBadgeRight = tableLeft + 195f
                canvas.drawRoundRect(balBadgeLeft, currentY + 10f, balBadgeRight, currentY + 46f, 6f, 6f, balanceBoxPaint)
                canvas.drawRoundRect(balBadgeLeft, currentY + 10f, balBadgeRight, currentY + 46f, 6f, 6f, balanceBorder)

                val balanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDebt) Color.rgb(220, 38, 38) else Color.rgb(16, 140, 95)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val balanceLabel = if (isDebt) "المطلوب سداده:" else "رصيد الحساب:"
                canvas.drawText("$balanceLabel ${DaftarKashViewModel.formatMoney(balance)} $currency", (balBadgeLeft + balBadgeRight) / 2, currentY + 33f, balanceTextPaint)

                currentY += custBoxHeight + 10f
            }

            // 3. Transactions Table Header
            val thBg = Paint().apply { color = Color.rgb(240, 253, 244) }
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + 24f, thBg)
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + 24f, borderPaint)

            canvas.drawLine(col1L, currentY, col1L, currentY + 24f, borderPaint)
            canvas.drawLine(col2L, currentY, col2L, currentY + 24f, borderPaint)
            canvas.drawLine(col3L, currentY, col3L, currentY + 24f, borderPaint)
            canvas.drawLine(col4L, currentY, col4L, currentY + 24f, borderPaint)

            canvas.drawText("م", (col1L + col1R) / 2, currentY + 16f, Paint(headerTextPaint).apply { textAlign = Paint.Align.CENTER })
            canvas.drawText("التاريخ والوقت", col2R - 8f, currentY + 16f, headerTextPaint)
            canvas.drawText("نوع الحركة", (col3L + col3R) / 2, currentY + 16f, Paint(headerTextPaint).apply { textAlign = Paint.Align.CENTER })
            canvas.drawText("البيان / الأصناف", col4R - 8f, currentY + 16f, headerTextPaint)
            canvas.drawText("المبلغ ($currency)", col5R - 10f, currentY + 16f, headerTextPaint)

            currentY += 24f

            // Table Rows
            val limit = if (pageNumber == 1) rowsPerPageFirst else rowsPerPageOther
            var countInPage = 0

            if (totalTransactions == 0) {
                val rowBg = Paint().apply { color = Color.rgb(255, 255, 255) }
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 30f, rowBg)
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 30f, borderPaint)
                canvas.drawText("لا توجد حركات مسجلة لهذا العميل حتى الآن", (pageWidth / 2).toFloat(), currentY + 18f, subPaint)
                currentY += 30f
            }

            while (txIndex < totalTransactions && countInPage < limit) {
                val tx = transactions[txIndex]
                val isEven = txIndex % 2 == 0
                if (isEven) {
                    val rowBg = Paint().apply { color = Color.rgb(249, 250, 251) }
                    canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, rowBg)
                }
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, borderPaint)

                canvas.drawLine(col1L, currentY, col1L, currentY + rowHeight, borderPaint)
                canvas.drawLine(col2L, currentY, col2L, currentY + rowHeight, borderPaint)
                canvas.drawLine(col3L, currentY, col3L, currentY + rowHeight, borderPaint)
                canvas.drawLine(col4L, currentY, col4L, currentY + rowHeight, borderPaint)

                val isDebtRow = tx.type == "DEBT"
                val typeText = if (isDebtRow) "سحب (+)" else "سداد (-)"
                val typeColor = if (isDebtRow) Color.rgb(185, 28, 28) else Color.rgb(21, 128, 61)

                // 1. Index
                canvas.drawText("${txIndex + 1}", (col1L + col1R) / 2, currentY + 15f, cellCenterPaint)

                // 2. Clean Date
                val cleanDate = cleanTransactionDate(tx)
                canvas.save()
                canvas.clipRect(col2L + 2f, currentY, col2R - 2f, currentY + rowHeight)
                canvas.drawText(cleanDate, col2R - 6f, currentY + 15f, cellTextPaint)
                canvas.restore()

                // 3. Movement Type Badge
                val badgeColor = if (isDebtRow) Color.rgb(254, 226, 226) else Color.rgb(220, 252, 231)
                val badgePaint = Paint().apply { color = badgeColor }
                val badgeL = col3L + 6f
                val badgeR = col3R - 6f
                canvas.drawRoundRect(badgeL, currentY + 3f, badgeR, currentY + rowHeight - 3f, 4f, 4f, badgePaint)

                val rowTypePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = typeColor
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(typeText, (badgeL + badgeR) / 2, currentY + 14.5f, rowTypePaint)

                // 4. Description
                val safeDesc = if (tx.description.isNotBlank()) tx.description else "-"
                canvas.save()
                canvas.clipRect(col4L + 4f, currentY, col4R - 4f, currentY + rowHeight)
                canvas.drawText(safeDesc, col4R - 6f, currentY + 15f, cellTextPaint)
                canvas.restore()

                // 5. Amount
                val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = typeColor
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                val amountText = DaftarKashViewModel.formatMoney(tx.amount)
                canvas.save()
                canvas.clipRect(col5L + 2f, currentY, col5R - 2f, currentY + rowHeight)
                canvas.drawText(amountText, col5R - 8f, currentY + 15f, amountPaint)
                canvas.restore()

                currentY += rowHeight
                txIndex++
                countInPage++
            }

            // Summary box on the last page
            if (pageNumber == totalPages) {
                currentY += 10f
                if (currentY + 52f < pageHeight - 30f) {
                    val sumBg = Paint().apply { color = Color.rgb(249, 250, 251) }
                    canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 52f, 6f, 6f, sumBg)
                    canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 52f, 6f, 6f, borderPaint)

                    val summaryTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(31, 41, 55)
                        textSize = 10f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                    }

                    canvas.drawText("إجمالي السحب (الديون): ${DaftarKashViewModel.formatMoney(totalDebts)} $currency", tableRight - 15f, currentY + 20f, summaryTitlePaint)
                    canvas.drawText("إجمالي السداد (المدفوع): ${DaftarKashViewModel.formatMoney(totalPayments)} $currency", tableRight - 15f, currentY + 38f, summaryTitlePaint)

                    val finalBalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (balance > 0) Color.rgb(220, 38, 38) else Color.rgb(16, 140, 95)
                        textSize = 12f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.LEFT
                    }
                    canvas.drawText("صافي الرصيد الحالي: ${DaftarKashViewModel.formatMoney(balance)} $currency", tableLeft + 15f, currentY + 30f, finalBalPaint)

                    // Draw Official Merchant Rubber Stamp (ختم المحل المعتمد)
                    drawOfficialRubberStamp(
                        canvas = canvas,
                        centerX = tableLeft + 190f,
                        centerY = currentY + 26f,
                        storeName = storeName,
                        stampType = if (balance > 0) "كشف حساب معتمد" else "حساب خالص معتمد",
                        dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")).format(Date()),
                        color = if (balance > 0) Color.rgb(185, 28, 28) else Color.rgb(16, 140, 95)
                    )
                }
            }

            // Footer Note with Pagination
            val footerText = "صفحة $pageNumber من $totalPages  •  تم استخراج هذا التقرير عبر تطبيق «دفتر كاش» - شكراً لتعاملكم معنا ✨"
            canvas.drawText(footerText, (pageWidth / 2).toFloat(), (pageHeight - 20).toFloat(), subPaint)

            pdfDocument.finishPage(page)
            pageNumber++
        }

        return try {
            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Statement_${customer.id}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun sharePdfFile(context: Context, file: File, customerName: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "كشف حساب - $customerName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة كشف الحساب (PDF)"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun buildFullStatementWhatsAppMessage(
        customer: Customer,
        balance: Double,
        currency: String,
        storeName: String,
        storePhone: String,
        transactions: List<Transaction>
    ): String {
        val sb = StringBuilder()
        sb.append("📋 *كشف حساب تفصيلي*\n")
        sb.append("🏪 *المحل:* $storeName\n")
        if (storePhone.isNotBlank()) sb.append("📞 *هاتف:* $storePhone\n")
        sb.append("👤 *العميل:* ${customer.name}\n")
        val dateNow = SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")).format(Date())
        sb.append("📅 *تاريخ الاستخراج:* $dateNow\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📊 *سجل العمليات والحركات الأخيرة:*\n")

        if (transactions.isEmpty()) {
            sb.append("• لا توجد حركات مسجلة حالياً.\n")
        } else {
            transactions.take(30).forEach { tx ->
                val sign = if (tx.type == "DEBT") "🔴 دين (+)" else "🟢 سداد (-)"
                val cleanDate = cleanTransactionDate(tx)
                val desc = if (tx.description.isNotBlank()) " | ${tx.description}" else ""
                sb.append("• $cleanDate : $sign *${DaftarKashViewModel.formatMoney(tx.amount)} $currency*$desc\n")
            }
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        if (balance > 0) {
            sb.append("💰 *إجمالي الرصيد المتبقي المطلوب:* *${DaftarKashViewModel.formatMoney(balance)} $currency*\n")
            sb.append("يرجى مراجعة الحساب والتكرم بالسداد في أقرب وقت. 🌹\n")
        } else if (balance == 0.0) {
            sb.append("✅ *الحساب خالص بالكامل (0 $currency)*. شكراً لالتزامك الدائم! ✨\n")
        } else {
            sb.append("ℹ️ *رصيد دائن لك:* *${DaftarKashViewModel.formatMoney(-balance)} $currency*\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("⚜️ *[ خَتْم الاعْتِمَاد الرَسْمِي : $storeName ]* ⚜️\n")
        sb.append("نسعد دائماً بخدمتكم! ✨")
        return sb.toString()
    }

    /**
     * Draws an authentic vintage Arabic circular rubber merchant seal stamp on a PDF canvas
     */
    fun drawOfficialRubberStamp(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        storeName: String,
        stampType: String,
        dateStr: String,
        color: Int = Color.rgb(185, 28, 28)
    ) {
        canvas.save()
        canvas.rotate(-6.5f, centerX, centerY)

        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            pathEffect = DashPathEffect(floatArrayOf(3.5f, 3.5f), 0f)
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }

        val radiusOuter = 26f
        val radiusDashed = 22f
        val radiusInner = 18f

        canvas.drawCircle(centerX, centerY, radiusOuter, outerPaint)
        canvas.drawCircle(centerX, centerY, radiusDashed, dashedPaint)
        canvas.drawCircle(centerX, centerY, radiusInner, innerPaint)

        // Store Name text
        val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 5.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("★ $storeName ★", centerX, centerY - 9f, titleTextPaint)

        // Status Badge Pill
        val pillBg = Paint().apply {
            this.color = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color))
        }
        canvas.drawRoundRect(centerX - 16f, centerY - 4f, centerX + 16f, centerY + 4f, 3f, 3f, pillBg)
        canvas.drawRoundRect(centerX - 16f, centerY - 4f, centerX + 16f, centerY + 4f, 3f, 3f, innerPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 5.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(stampType, centerX, centerY + 2.2f, badgeTextPaint)

        // Subtitle text
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 4.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dateStr, centerX, centerY + 9f, subTextPaint)
        canvas.drawText("OFFICIAL SEAL", centerX, centerY + 14f, subTextPaint)

        canvas.restore()
    }

    fun sendWhatsAppDirect(context: Context, phone: String, message: String) {
        var cleanPhone = phone.replace("[^0-9]".toRegex(), "")
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "20" + cleanPhone.substring(1)
        }

        try {
            val uri = if (cleanPhone.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${URLEncoder.encode(message, "UTF-8")}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(message, "UTF-8")}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(shareIntent, "إرسال كشف الحساب"))
        }
    }
}
