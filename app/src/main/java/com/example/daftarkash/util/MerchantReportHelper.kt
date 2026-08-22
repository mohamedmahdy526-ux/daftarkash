package com.example.daftarkash.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.daftarkash.data.model.Customer
import com.example.daftarkash.data.model.Transaction
import com.example.daftarkash.data.repository.CustomerWithBalance
import com.example.daftarkash.data.repository.LedgerMetrics
import com.example.daftarkash.ui.viewmodel.DaftarKashViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object MerchantReportHelper {

    /**
     * Escapes CSV cell contents according to RFC 4180
     */
    private fun escapeCsv(value: String): String {
        var str = value.replace("\r", " ").replace("\n", " ")
        if (str.contains(",") || str.contains("\"") || str.contains(";")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }

    /**
     * Generates and shares a comprehensive Excel-compatible CSV file with UTF-8 BOM
     */
    fun exportDetailedMerchantExcel(
        context: Context,
        storeName: String,
        storePhone: String,
        currency: String,
        customersWithBalances: List<CustomerWithBalance>,
        allTransactions: List<Transaction>,
        metrics: LedgerMetrics
    ): File? {
        return try {
            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val dateFormatted = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar")).format(Date())
            val file = File(dir, "DaftarKash_Merchant_Report_$timestampStr.csv")

            val fos = FileOutputStream(file)
            val writer = OutputStreamWriter(fos, StandardCharsets.UTF_8)

            // Write UTF-8 BOM for flawless Arabic rendering in Microsoft Excel & Google Sheets
            fos.write(0xEF)
            fos.write(0xBB)
            fos.write(0xBF)

            val customerMap = customersWithBalances.associateBy { it.customer.id }

            // 1. Report Master Header
            writer.write("📊 شيت الحسابات والتقرير التفصيلي للتاجر - دفتر كاش\n")
            writer.write("اسم المحل / التاجر:,${escapeCsv(storeName)}\n")
            writer.write("الختم والاعتماد الرسمي:,[ ⚜️ خَتْم الاعْتِمَاد المَرْكَزِي: ${escapeCsv(storeName)} - إدارة الحسابات المالية ⚜️ ]\n")
            writer.write("هاتف المحل:,${escapeCsv(storePhone.ifBlank { "-" })}\n")
            writer.write("تاريخ وتوقيت الاستخراج:,${escapeCsv(dateFormatted)}\n")
            writer.write("العملة المعتمدة:,${escapeCsv(currency)}\n")
            writer.write("\n")

            // 2. Key Financial Metrics Section
            writer.write("📈 الملخص المالي العام للمحل:\n")
            writer.write("إجمالي ديون السوق (فلوس السوق):,${DaftarKashViewModel.formatMoney(metrics.totalMarketDebt)} $currency\n")
            writer.write("إجمالي تحصيلات اليوم:,${DaftarKashViewModel.formatMoney(metrics.todayCollections)} $currency\n")
            writer.write("إجمالي عدد العملاء:,${metrics.totalCustomersCount}\n")
            writer.write("عدد العملاء المدينين (عليهم فلوس):,${metrics.debtorsCount}\n")
            writer.write("عدد العملاء الخالصين:,${metrics.settledCount}\n")
            writer.write("\n")

            // 3. Section 1: All Customers Balances Table
            writer.write("👥 جدول كشف أرصدة جميع العملاء:\n")
            writer.write("م,كود العميل,اسم العميل,رقم الهاتف,حالة الحساب,الرصيد الحالي المستحق ($currency),ملاحظات العميل\n")

            customersWithBalances.forEachIndexed { index, item ->
                val c = item.customer
                val balance = item.balance
                val statusText = when {
                    balance > 0 -> "مدين (عليه دين)"
                    balance == 0.0 -> "خالص بالكامل"
                    else -> "دائن (له رصيد)"
                }
                val row = listOf(
                    (index + 1).toString(),
                    "#${c.id}",
                    escapeCsv(c.name),
                    escapeCsv(c.phone.ifBlank { "-" }),
                    statusText,
                    DaftarKashViewModel.formatMoney(balance),
                    escapeCsv(c.notes.ifBlank { "-" })
                ).joinToString(",")
                writer.write(row + "\n")
            }

            writer.write("\n")

            // 4. Section 2: Complete Transaction Log / Invoices Breakdown
            writer.write("🧾 سجل الفواتير والحركات التفصيلية بالكامل:\n")
            writer.write("رقم الحركة,اسم العميل,رقم هاتف العميل,تاريخ ووقت الحركة,نوع الحركة,المبلغ ($currency),طريقة السداد,بيان وتفاصيل الأصناف\n")

            val sortedTx = allTransactions.sortedByDescending { it.timestamp }
            sortedTx.forEach { tx ->
                val custWithBal = customerMap[tx.customerId]
                val custName = custWithBal?.customer?.name ?: "عميل رقم #${tx.customerId}"
                val custPhone = custWithBal?.customer?.phone ?: "-"
                
                val txType = if (tx.type == "DEBT") "دين / سحب بضاعة" else "سداد / تحصيل دفعة"
                val txDate = if (tx.timestamp > 100000000000L) {
                    try {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")).format(Date(tx.timestamp))
                    } catch (e: Exception) {
                        tx.date
                    }
                } else {
                    tx.date
                }

                val paymentMethod = when (tx.paymentMethod) {
                    "VODAFONE_CASH" -> "محفظة إلكترونية (فودافون كاش)"
                    "BANK" -> "تحويل بنكي / إنستاباي"
                    "CARD" -> "بطاقة بنكية"
                    else -> if (tx.type == "DEBT") "آجل على الحساب" else "كاش نقدياً"
                }

                val row = listOf(
                    "#${tx.id}",
                    escapeCsv(custName),
                    escapeCsv(custPhone),
                    escapeCsv(txDate),
                    txType,
                    DaftarKashViewModel.formatMoney(tx.amount),
                    escapeCsv(paymentMethod),
                    escapeCsv(tx.description.ifBlank { "-" })
                ).joinToString(",")
                writer.write(row + "\n")
            }

            writer.flush()
            writer.close()
            fos.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a multi-page Master PDF report containing all customers with financial KPI summary
     */
    fun generateAllCustomersPdfReport(
        context: Context,
        storeName: String,
        storePhone: String,
        currency: String,
        customersWithBalances: List<CustomerWithBalance>,
        metrics: LedgerMetrics
    ): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard width
        val pageHeight = 842 // A4 standard height
        val tableLeft = 25f
        val tableRight = 570f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 140, 95) // Primary Green
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }
        val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(107, 114, 128)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 255, 255)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val tableHeaderCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 255, 255)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cellNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val cellPhonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        val cellDebtAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38) // Red
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val cellSettledAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 140, 95) // Green
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val cellStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(156, 163, 175)
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }

        val rowsPerPageFirst = 18
        val rowsPerPageOther = 25
        val totalCustomers = customersWithBalances.size

        var customerIndex = 0
        var pageNumber = 1
        val totalPages = if (totalCustomers <= rowsPerPageFirst) 1 else {
            1 + Math.ceil((totalCustomers - rowsPerPageFirst).toDouble() / rowsPerPageOther).toInt()
        }

        val dateNow = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar")).format(Date())

        while (customerIndex < totalCustomers || (totalCustomers == 0 && pageNumber == 1)) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var currentY = 20f

            // Top Header
            val headerBg = Paint().apply { color = Color.rgb(243, 244, 246) }
            canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 56f, 6f, 6f, headerBg)
            canvas.drawRoundRect(tableLeft, currentY, tableRight, currentY + 56f, 6f, 6f, borderPaint)

            canvas.drawText("📋 كشف حساب عام لجميع العملاء - $storeName", (pageWidth / 2).toFloat() + 15f, currentY + 24f, titlePaint)
            canvas.drawText("هاتف المحل: ${storePhone.ifBlank { "-" }}  |  تاريخ التقرير: $dateNow  |  العملة: $currency", (pageWidth / 2).toFloat() + 15f, currentY + 44f, subPaint)

            // Draw Official Merchant Seal Stamp on the top banner
            StatementPdfHelper.drawOfficialRubberStamp(
                canvas = canvas,
                centerX = tableLeft + 35f,
                centerY = currentY + 28f,
                storeName = storeName,
                stampType = "كشف معتمد",
                dateStr = dateNow.take(10),
                color = Color.rgb(16, 140, 95)
            )

            currentY += 66f

            // On the First Page: Render 4 KPI Metric Cards
            if (pageNumber == 1) {
                val cardWidth = (tableRight - tableLeft - 18f) / 4f
                val cardHeight = 44f

                // Card 1: فلوس السوق (Total Market Debt)
                val c1Left = tableLeft
                val bg1 = Paint().apply { color = Color.rgb(254, 242, 242) }
                canvas.drawRoundRect(c1Left, currentY, c1Left + cardWidth, currentY + cardHeight, 5f, 5f, bg1)
                canvas.drawRoundRect(c1Left, currentY, c1Left + cardWidth, currentY + cardHeight, 5f, 5f, borderPaint)
                canvas.drawText("إجمالي ديون السوق", c1Left + cardWidth / 2, currentY + 16f, cardTitlePaint)
                val redValuePaint = Paint(cardValuePaint).apply { color = Color.rgb(220, 38, 38) }
                canvas.drawText("${DaftarKashViewModel.formatMoney(metrics.totalMarketDebt)} $currency", c1Left + cardWidth / 2, currentY + 34f, redValuePaint)

                // Card 2: تحصيل اليوم
                val c2Left = c1Left + cardWidth + 6f
                val bg2 = Paint().apply { color = Color.rgb(236, 253, 245) }
                canvas.drawRoundRect(c2Left, currentY, c2Left + cardWidth, currentY + cardHeight, 5f, 5f, bg2)
                canvas.drawRoundRect(c2Left, currentY, c2Left + cardWidth, currentY + cardHeight, 5f, 5f, borderPaint)
                canvas.drawText("تحصيل اليوم", c2Left + cardWidth / 2, currentY + 16f, cardTitlePaint)
                val greenValuePaint = Paint(cardValuePaint).apply { color = Color.rgb(16, 140, 95) }
                canvas.drawText("${DaftarKashViewModel.formatMoney(metrics.todayCollections)} $currency", c2Left + cardWidth / 2, currentY + 34f, greenValuePaint)

                // Card 3: عدد المدينين
                val c3Left = c2Left + cardWidth + 6f
                val bg3 = Paint().apply { color = Color.rgb(255, 251, 235) }
                canvas.drawRoundRect(c3Left, currentY, c3Left + cardWidth, currentY + cardHeight, 5f, 5f, bg3)
                canvas.drawRoundRect(c3Left, currentY, c3Left + cardWidth, currentY + cardHeight, 5f, 5f, borderPaint)
                canvas.drawText("العملاء المدينين", c3Left + cardWidth / 2, currentY + 16f, cardTitlePaint)
                val yellowValuePaint = Paint(cardValuePaint).apply { color = Color.rgb(217, 119, 6) }
                canvas.drawText("${metrics.debtorsCount} عميل", c3Left + cardWidth / 2, currentY + 34f, yellowValuePaint)

                // Card 4: إجمالي العملاء
                val c4Left = c3Left + cardWidth + 6f
                val bg4 = Paint().apply { color = Color.rgb(243, 244, 246) }
                canvas.drawRoundRect(c4Left, currentY, c4Left + cardWidth, currentY + cardHeight, 5f, 5f, bg4)
                canvas.drawRoundRect(c4Left, currentY, c4Left + cardWidth, currentY + cardHeight, 5f, 5f, borderPaint)
                canvas.drawText("إجمالي العملاء", c4Left + cardWidth / 2, currentY + 16f, cardTitlePaint)
                canvas.drawText("${metrics.totalCustomersCount} عميل", c4Left + cardWidth / 2, currentY + 34f, cardValuePaint)

                currentY += 54f
            }

            // Table Header Bar (Dark Emerald)
            val theadHeight = 24f
            val theadBg = Paint().apply { color = Color.rgb(16, 140, 95) }
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + theadHeight, theadBg)

            // Column Widths:
            // Right to Left:
            // Col 1 (Idx): 30pt (from tableRight)
            // Col 2 (Name): 180pt
            // Col 3 (Phone): 110pt
            // Col 4 (Status): 90pt
            // Col 5 (Balance): 135pt
            val colIdxRight = tableRight
            val colNameRight = tableRight - 35f
            val colPhoneRight = colNameRight - 175f
            val colStatusRight = colPhoneRight - 110f
            val colBalanceRight = colStatusRight - 85f

            canvas.drawText("م", colIdxRight - 18f, currentY + 16f, tableHeaderCenterPaint)
            canvas.drawText("اسم العميل", colNameRight - 10f, currentY + 16f, tableHeaderPaint)
            canvas.drawText("رقم الهاتف", colPhoneRight - 55f, currentY + 16f, tableHeaderCenterPaint)
            canvas.drawText("الحالة", colStatusRight - 42f, currentY + 16f, tableHeaderCenterPaint)
            canvas.drawText("الرصيد المستحق", colBalanceRight - 10f, currentY + 16f, tableHeaderPaint)

            currentY += theadHeight

            // Table Rows
            val limit = if (pageNumber == 1) rowsPerPageFirst else rowsPerPageOther
            var countInPage = 0
            val rowHeight = 22f

            val rowWhite = Paint().apply { color = Color.rgb(255, 255, 255) }
            val rowAlternate = Paint().apply { color = Color.rgb(249, 250, 251) }

            if (totalCustomers == 0) {
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 30f, rowWhite)
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 30f, borderPaint)
                canvas.drawText("لا يوجد عملاء مسجلين في الدفتر حالياً", (pageWidth / 2).toFloat(), currentY + 18f, subPaint)
                currentY += 30f
            }

            while (customerIndex < totalCustomers && countInPage < limit) {
                val item = customersWithBalances[customerIndex]
                val bg = if (customerIndex % 2 == 0) rowWhite else rowAlternate
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, bg)
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowHeight, borderPaint)

                // Index
                canvas.drawText("${customerIndex + 1}", colIdxRight - 18f, currentY + 15f, cellPhonePaint)

                // Customer Name (clipped if too long)
                var custName = item.customer.name
                if (custName.length > 25) custName = custName.take(23) + ".."
                canvas.drawText(custName, colNameRight - 10f, currentY + 15f, cellNamePaint)

                // Phone
                val phone = if (item.customer.phone.isNotBlank()) item.customer.phone else "-"
                canvas.drawText(phone, colPhoneRight - 55f, currentY + 15f, cellPhonePaint)

                // Status Badge text
                val bal = item.balance
                val statusText = when {
                    bal > 0 -> "مدين 🔴"
                    bal == 0.0 -> "خالص 🟢"
                    else -> "دائن 🔵"
                }
                cellStatusPaint.color = when {
                    bal > 0 -> Color.rgb(220, 38, 38)
                    bal == 0.0 -> Color.rgb(16, 140, 95)
                    else -> Color.rgb(37, 99, 235)
                }
                canvas.drawText(statusText, colStatusRight - 42f, currentY + 15f, cellStatusPaint)

                // Balance Amount
                val amountText = "${DaftarKashViewModel.formatMoney(bal)} $currency"
                val paintToUse = if (bal > 0) cellDebtAmountPaint else cellSettledAmountPaint
                canvas.drawText(amountText, colBalanceRight - 10f, currentY + 15f, paintToUse)

                currentY += rowHeight
                customerIndex++
                countInPage++
            }

            // Footer of Page
            canvas.drawText(
                "صفحة $pageNumber من $totalPages  •  تم استخراج هذا التقرير عبر تطبيق دفتر كاش لإدارة الحسابات",
                (pageWidth / 2).toFloat(),
                pageHeight - 20f,
                footerPaint
            )

            pdfDocument.finishPage(page)
            pageNumber++
        }

        return try {
            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val file = File(dir, "DaftarKash_Master_Report_$timestampStr.pdf")

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

    /**
     * Shares any file using the Android Intent chooser
     */
    fun shareReportFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates formatted WhatsApp text summary of the merchant ledger
     */
    fun buildMerchantSummaryWhatsAppMessage(
        storeName: String,
        storePhone: String,
        currency: String,
        metrics: LedgerMetrics,
        customersWithBalances: List<CustomerWithBalance>
    ): String {
        val sb = StringBuilder()
        val dateNow = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.forLanguageTag("ar")).format(Date())
        sb.append("📊 *تقرير ملخص حسابات المحل - دفتر كاش*\n")
        sb.append("🏪 *المحل:* $storeName\n")
        if (storePhone.isNotBlank()) sb.append("📞 *هاتف:* $storePhone\n")
        sb.append("📅 *التاريخ:* $dateNow\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("💰 *إجمالي ديون السوق (فلوس السوق):* *${DaftarKashViewModel.formatMoney(metrics.totalMarketDebt)} $currency*\n")
        sb.append("💵 *تحصيل اليوم:* *${DaftarKashViewModel.formatMoney(metrics.todayCollections)} $currency*\n")
        sb.append("👥 *عدد العملاء المدينين:* *${metrics.debtorsCount}* عميل\n")
        sb.append("✅ *عدد العملاء الخالصين:* *${metrics.settledCount}* عميل\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        
        val debtors = customersWithBalances.filter { it.balance > 0 }.sortedByDescending { it.balance }
        if (debtors.isNotEmpty()) {
            sb.append("🔴 *أعلى المدينين في السوق:*\n")
            debtors.take(15).forEachIndexed { i, item ->
                sb.append("${i + 1}. ${item.customer.name}: *${DaftarKashViewModel.formatMoney(item.balance)} $currency*\n")
            }
            if (debtors.size > 15) {
                sb.append("... وغيرهم من العملاء (${debtors.size - 15} آخرين)\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        }
        sb.append("⚜️ *[ خَتْم الاعْتِمَاد الرَسْمِي : $storeName ]* ⚜️\n")
        sb.append("✨ تم التصدير عبر تطبيق دفتر كاش")
        return sb.toString()
    }
}
