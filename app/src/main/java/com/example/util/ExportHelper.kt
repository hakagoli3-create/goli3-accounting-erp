package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.InvoiceWithItems
import com.example.data.model.StoreProfile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object ExportHelper {

    fun shareText(context: Context, text: String, title: String = "ارسال اطلاعات") {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, title)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val shareIntent = Intent.createChooser(sendIntent, title)
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطا در اشتراک‌گذاری فایل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * چاپ مستقیم با سرویس چاپ سیستم‌عامل اندروید (Android PrintManager)
     */
    fun printPdf(context: Context, pdfFile: File, jobName: String = "چاپ فاکتور رسمی") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
            if (printManager == null) {
                shareFile(context, pdfFile, "application/pdf", jobName)
                return
            }
            val printAdapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes?,
                    newAttributes: android.print.PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val pdi = android.print.PrintDocumentInfo.Builder(jobName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(pdi, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val input = java.io.FileInputStream(pdfFile)
                        val output = java.io.FileOutputStream(destination?.fileDescriptor)
                        val buf = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } > 0) {
                            output.write(buf, 0, bytesRead)
                        }
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        input.close()
                        output.close()
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
        } catch (e: Exception) {
            shareFile(context, pdfFile, "application/pdf", jobName)
        }
    }

    /**
     * تولید PDF استاندارد و رسمی برای فاکتور فروش
     */
    /**
     * تولید PDF استاندارد و رسمی برای فاکتور فروش، خرید و مرجوعی
     * همراه با جدول‌بندی خط‌کشی‌شده (Grid)، سربرگ منطقه آزاد انزلی، خلاصه مالی و کادرهای مهر و امضا
     */
    fun exportInvoicePdf(
        context: Context,
        invoiceWithItems: InvoiceWithItems,
        storeProfile: StoreProfile
    ): File? {
        return try {
            val invoice = invoiceWithItems.invoice
            val items = invoiceWithItems.items

            val storeName = storeProfile.name.ifBlank { "بازرگانی گلی" }
            val storeAddress = storeProfile.address.ifBlank { "منطقه آزاد انزلی، مجتمع تجاری ستاره شمال، غرفه ۵۴" }
            val storePhone = storeProfile.phone.ifBlank { "۰۱۳-۴۴۴۲۰۰۰۰" }
            val storeMobile = storeProfile.mobile.ifBlank { "۰۹۱۱-۰۰۰-۰۰۰۰" }
            val storeNational = storeProfile.nationalCode.ifBlank { "۱۴۰۰۸۵۴۳۲۱۰" }
            val storeEconomic = storeProfile.economicCode.ifBlank { "۴۱۱۶۸۵۹۰۳۲۱" }
            val defaultBankCard = storeProfile.defaultBankCard.ifBlank { "۶۰۳۷-۹۹۷۹-۰۰۰۰-۰۰۰۰" }
            val defaultSheba = storeProfile.defaultSheba.ifBlank { "IR000000000000000000000000" }

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
            }

            // ۱. سربرگ اصلی فاکتور (Header)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(26, 32, 44) // Matte Dark Slate / Navy
            canvas.drawRect(20f, 20f, (pageWidth - 20).toFloat(), 82f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 14f
            textPaint.isFakeBoldText = true
            val pdfDocTitle = when (invoice.invoiceType) {
                "PURCHASE" -> "صورتحساب رسمی خرید کالا و خدمات"
                "RETURN_SALE" -> "صورتحساب رسمی برگشت از فروش کالا و خدمات"
                "RETURN_PURCHASE" -> "صورتحساب رسمی برگشت از خرید کالا و خدمات"
                else -> "صورتحساب رسمی فروش کالا و خدمات"
            }
            drawTextRtl(canvas, textPaint, pdfDocTitle, (pageWidth - 35).toFloat(), 48f)

            textPaint.textSize = 9.5f
            textPaint.isFakeBoldText = false
            drawTextRtl(canvas, textPaint, "$storeName  |  منطقه آزاد تجاری - صنعتی انزلی", (pageWidth - 35).toFloat(), 68f)

            val formattedInvNum = NumberFormatUtils.formatInvoiceNumber(invoice.invoiceNumber)
            val metaText = "شماره: $formattedInvNum   |   تاریخ: ${invoice.jalaliDate}"
            textPaint.textSize = 9f
            canvas.drawText(metaText, 35f, 54f, textPaint)

            var y = 92f

            // ۲. کادر مشخصات فروشنده و خریدار (Seller & Buyer Information)
            paint.color = Color.rgb(248, 250, 252)
            paint.style = Paint.Style.FILL
            canvas.drawRect(20f, y, (pageWidth - 20).toFloat(), y + 68f, paint)

            paint.color = Color.rgb(205, 215, 228)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(20f, y, (pageWidth - 20).toFloat(), y + 68f, paint)
            paint.style = Paint.Style.FILL

            textPaint.color = Color.rgb(25, 30, 40)
            textPaint.textSize = 8.5f
            val buyerPayDesc = NumberFormatUtils.formatPaymentType(invoice.paymentType)

            if (invoice.invoiceType in listOf("PURCHASE", "RETURN_PURCHASE")) {
                textPaint.isFakeBoldText = true
                drawTextRtl(canvas, textPaint, "فروشنده (تأمین‌کننده): ${invoice.partyName}   |   تماس: ${invoice.partyPhone.ifEmpty { "---" }}", (pageWidth - 30).toFloat(), y + 18f)
                textPaint.isFakeBoldText = false
                drawTextRtl(canvas, textPaint, "خریدار: $storeName   |   تلفن: $storePhone   |   همراه: $storeMobile   |   شناسه ملی: $storeNational", (pageWidth - 30).toFloat(), y + 34f)
                textPaint.isFakeBoldText = true
                drawTextRtl(canvas, textPaint, "نشانی شرکت خریدار: $storeAddress   |   شیوه تسویه: $buyerPayDesc", (pageWidth - 30).toFloat(), y + 52f)
            } else {
                textPaint.isFakeBoldText = true
                drawTextRtl(canvas, textPaint, "فروشنده: $storeName   |   تلفن: $storePhone   |   همراه: $storeMobile   |   شناسه ملی: $storeNational", (pageWidth - 30).toFloat(), y + 18f)
                textPaint.isFakeBoldText = false
                drawTextRtl(canvas, textPaint, "نشانی فروشگاه: $storeAddress   |   کد اقتصادی: $storeEconomic", (pageWidth - 30).toFloat(), y + 34f)
                textPaint.isFakeBoldText = true
                val partyTitle = if (invoice.invoiceType == "RETURN_SALE") "خریدار / مرجوع‌کننده" else "خریدار / طرف‌حساب"
                drawTextRtl(canvas, textPaint, "$partyTitle: ${invoice.partyName}   |   تماس: ${invoice.partyPhone.ifEmpty { "نقدی" }}   |   شیوه تسویه: $buyerPayDesc", (pageWidth - 30).toFloat(), y + 52f)
            }

            y += 78f

            // ۳. جدول استاندارد اقلام با خط‌کشی منظم شبکه (Standardized Grid Table)
            // ستون‌ها از راست به چپ:
            // ردیف (35) [575 -> 540]
            // شرح کالا یا خدمات (215) [540 -> 325]
            // تعداد (55) [325 -> 270]
            // واحد (50) [270 -> 220]
            // فی تومان (95) [220 -> 125]
            // مبلغ کل تومان (105) [125 -> 20]
            val tableStartX = 20f
            val tableEndX = (pageWidth - 20).toFloat() // 575f
            val tableStartY = y
            val headerHeight = 22f
            val rowHeight = 20f
            val gridCols = floatArrayOf(575f, 540f, 325f, 270f, 220f, 125f, 20f)

            // رنگ پس‌زمینه هدر جدول
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(38, 50, 65)
            canvas.drawRect(tableStartX, tableStartY, tableEndX, tableStartY + headerHeight, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 8.5f
            textPaint.isFakeBoldText = true

            // متون هدر جدول
            drawTextRtl(canvas, textPaint, "ردیف", 562f, tableStartY + 15f)
            drawTextRtl(canvas, textPaint, "شرح کالا یا خدمات", 530f, tableStartY + 15f)
            drawTextRtl(canvas, textPaint, "تعداد", 305f, tableStartY + 15f)
            drawTextRtl(canvas, textPaint, "واحد", 252f, tableStartY + 15f)
            drawTextRtl(canvas, textPaint, "فی (تومان)", 210f, tableStartY + 15f)
            drawTextRtl(canvas, textPaint, "مبلغ کل (تومان)", 115f, tableStartY + 15f)

            var rowY = tableStartY + headerHeight
            textPaint.color = Color.rgb(20, 24, 30)
            textPaint.isFakeBoldText = false
            textPaint.textSize = 8f

            items.forEachIndexed { index, item ->
                val rowBg = if (index % 2 == 0) Color.WHITE else Color.rgb(246, 248, 252)
                paint.style = Paint.Style.FILL
                paint.color = rowBg
                canvas.drawRect(tableStartX, rowY, tableEndX, rowY + rowHeight, paint)

                // ردیف
                drawTextRtl(canvas, textPaint, NumberFormatUtils.toPersianDigits((index + 1).toString()), 562f, rowY + 14f)
                // نام کالا
                drawTextRtl(canvas, textPaint, item.productName.take(34), 530f, rowY + 14f)
                // تعداد
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString()
                drawTextRtl(canvas, textPaint, NumberFormatUtils.toPersianDigits(qtyStr), 305f, rowY + 14f)
                // واحد
                drawTextRtl(canvas, textPaint, item.unitName, 252f, rowY + 14f)
                // فی
                drawTextRtl(canvas, textPaint, NumberFormatUtils.formatPriceOnlyNumber(item.unitPrice), 210f, rowY + 14f)
                // مبلغ کل
                drawTextRtl(canvas, textPaint, NumberFormatUtils.formatPriceOnlyNumber(item.totalPrice), 115f, rowY + 14f)

                rowY += rowHeight
            }

            val tableEndY = rowY

            // ترسیم خط‌کشی‌های منظم جدول (Grid Lines)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.7f
            paint.color = Color.rgb(180, 195, 210)

            // کادر دور کل جدول
            canvas.drawRect(tableStartX, tableStartY, tableEndX, tableEndY, paint)

            // خطوط افقی بین ردیف‌ها
            var hLineY = tableStartY + headerHeight
            while (hLineY < tableEndY) {
                canvas.drawLine(tableStartX, hLineY, tableEndX, hLineY, paint)
                hLineY += rowHeight
            }

            // خطوط عمودی بین ستون‌های جدول
            for (colX in gridCols) {
                canvas.drawLine(colX, tableStartY, colX, tableEndY, paint)
            }

            // ۴. بخش محاسبات مالی و نحوه تسویه (Footer)
            y = tableEndY + 10f

            val summaryBoxRight = 575f
            val summaryBoxLeft = 295f
            val settlementBoxRight = 285f
            val settlementBoxLeft = 20f
            val footerBoxHeight = if (invoice.directExpenseAmount > 0) 96f else 82f

            // کادر خلاصه مالی (سمت راست)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(250, 251, 253)
            canvas.drawRect(summaryBoxLeft, y, summaryBoxRight, y + footerBoxHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            paint.color = Color.rgb(195, 208, 225)
            canvas.drawRect(summaryBoxLeft, y, summaryBoxRight, y + footerBoxHeight, paint)

            // متون خلاصه مالی
            paint.style = Paint.Style.FILL
            textPaint.textSize = 8.5f
            textPaint.color = Color.BLACK
            textPaint.isFakeBoldText = false

            var curY = y + 15f
            drawTextRtl(canvas, textPaint, "جمع اقلام: ${NumberFormatUtils.formatPrice(invoice.subtotalAmount)}", summaryBoxRight - 10f, curY)

            if (invoice.discountAmount > 0) {
                curY += 15f
                textPaint.color = Color.rgb(180, 40, 40)
                drawTextRtl(canvas, textPaint, "تخفیف فاکتور: - ${NumberFormatUtils.formatPrice(invoice.discountAmount)}", summaryBoxRight - 10f, curY)
                textPaint.color = Color.BLACK
            }
            if (invoice.taxAmount > 0) {
                curY += 15f
                drawTextRtl(canvas, textPaint, "مالیات ارزش افزوده (۱۰٪): + ${NumberFormatUtils.formatPrice(invoice.taxAmount)}", summaryBoxRight - 10f, curY)
            }
            if (invoice.directExpenseAmount > 0) {
                curY += 15f
                val expLabel = if (invoice.directExpenseType.isNotBlank()) invoice.directExpenseType else "کرایه حمل و باربری"
                drawTextRtl(canvas, textPaint, "هزینه مستقیم ($expLabel): + ${NumberFormatUtils.formatPrice(invoice.directExpenseAmount)}", summaryBoxRight - 10f, curY)
            }

            curY += 16f
            textPaint.isFakeBoldText = true
            textPaint.textSize = 9.5f
            textPaint.color = Color.rgb(160, 20, 20)
            drawTextRtl(canvas, textPaint, "مبلغ کل قابل پرداخت: ${NumberFormatUtils.formatPrice(invoice.totalAmount)}", summaryBoxRight - 10f, curY)

            curY += 14f
            textPaint.textSize = 8f
            textPaint.color = Color.rgb(30, 65, 120)
            drawTextRtl(canvas, textPaint, "مبلغ به حروف: ${NumberFormatUtils.numberToPersianWords(invoice.totalAmount)} تومان", summaryBoxRight - 10f, curY)

            // کادر شیوه تسویه و وضعیت حساب (سمت چپ)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(250, 251, 253)
            canvas.drawRect(settlementBoxLeft, y, settlementBoxRight, y + footerBoxHeight, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            paint.color = Color.rgb(195, 208, 225)
            canvas.drawRect(settlementBoxLeft, y, settlementBoxRight, y + footerBoxHeight, paint)

            paint.style = Paint.Style.FILL
            textPaint.textSize = 8f
            textPaint.color = Color.BLACK
            textPaint.isFakeBoldText = true

            val payModeDesc = when (invoice.paymentType) {
                "CASH" -> "تسویه کامل نقدی (صندوق)"
                "POS" -> "تسویه از طریق پوز / کارتخوان بانکی"
                "CREDIT" -> "فروش نسیه و بدهکار شدن طرف‌حساب"
                "CHEQUE" -> "تسویه با چک صیادی"
                "MIXED" -> "تسویه ترکیبی (نقد، پوز، چک یا نسیه)"
                else -> NumberFormatUtils.formatPaymentType(invoice.paymentType)
            }
            drawTextRtl(canvas, textPaint, "شیوه تسویه: $payModeDesc", settlementBoxRight - 8f, y + 16f)

            textPaint.isFakeBoldText = false
            val accountStatus = if (invoice.paymentType == "CREDIT") {
                "وضعیت حساب: مانده بدهکار طرف‌حساب در سیستم"
            } else {
                "وضعیت حساب: تسویه کامل فاکتور و بدون مانده بدهی"
            }
            drawTextRtl(canvas, textPaint, accountStatus, settlementBoxRight - 8f, y + 32f)
            drawTextRtl(canvas, textPaint, "شماره کارت واریز: $defaultBankCard", settlementBoxRight - 8f, y + 48f)
            drawTextRtl(canvas, textPaint, "شماره شبا: $defaultSheba", settlementBoxRight - 8f, y + 64f)

            // ۵. کادرهای رسمی مهر و امضای فروشنده و خریدار (Signatures & Stamp Boxes)
            y += footerBoxHeight + 12f
            val sigBoxWidth = 270f
            val sigBoxHeight = 65f

            // کادر مهر و امضای فروشنده (راست)
            val sellerSigLeft = (pageWidth - 20 - sigBoxWidth).toFloat() // 305f
            val sellerSigRight = (pageWidth - 20).toFloat() // 575f

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            paint.color = Color.rgb(180, 195, 215)
            canvas.drawRect(sellerSigLeft, y, sellerSigRight, y + sigBoxHeight, paint)

            textPaint.isFakeBoldText = true
            textPaint.textSize = 8.5f
            textPaint.color = Color.rgb(30, 40, 55)
            val sig1 = when (invoice.invoiceType) {
                "PURCHASE", "RETURN_PURCHASE" -> "مهر و امضای فروشنده (تأمین‌کننده)"
                else -> "مهر و امضای فروشنده ($storeName)"
            }
            drawTextRtl(canvas, textPaint, sig1, sellerSigRight - 10f, y + 18f)
            textPaint.isFakeBoldText = false
            textPaint.textSize = 7.5f
            textPaint.color = Color.rgb(100, 110, 125)
            drawTextRtl(canvas, textPaint, "صحت اقلام، مقادیر و مبالغ صورتحساب تأیید می‌شود.", sellerSigRight - 10f, y + 32f)

            // کادر امضا و تأیید خریدار (چپ)
            val buyerSigLeft = 20f
            val buyerSigRight = 20f + sigBoxWidth // 290f

            paint.style = Paint.Style.STROKE
            canvas.drawRect(buyerSigLeft, y, buyerSigRight, y + sigBoxHeight, paint)

            textPaint.isFakeBoldText = true
            textPaint.textSize = 8.5f
            textPaint.color = Color.rgb(30, 40, 55)
            val sig2 = when (invoice.invoiceType) {
                "PURCHASE", "RETURN_PURCHASE" -> "امضا و تایید تحویل خریدار ($storeName)"
                else -> "امضا و تایید تحویل خریدار (${invoice.partyName})"
            }
            drawTextRtl(canvas, textPaint, sig2, buyerSigRight - 10f, y + 18f)
            textPaint.isFakeBoldText = false
            textPaint.textSize = 7.5f
            textPaint.color = Color.rgb(100, 110, 125)
            drawTextRtl(canvas, textPaint, "اقلام فوق صحیح و سالم طبق صورتحساب تحویل گرفته شد.", buyerSigRight - 10f, y + 32f)

            pdfDocument.finishPage(page)

            val dir = File(context.cacheDir, "invoices")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Invoice_${invoice.invoiceNumber}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تولید فایل اکسل استاندارد (CSV با UTF-8 BOM برای نمایش صحیح فارسی در نرم‌افزار Excel)
     */
    fun exportInvoiceCsv(
        context: Context,
        invoiceWithItems: InvoiceWithItems,
        storeProfile: StoreProfile
    ): File? {
        return try {
            val invoice = invoiceWithItems.invoice
            val items = invoiceWithItems.items

            val dir = File(context.cacheDir, "exports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Invoice_${invoice.invoiceNumber}.csv")

            val sb = StringBuilder()
            // UTF-8 BOM
            sb.append('\uFEFF')
            val csvDocTitle = when (invoice.invoiceType) {
                "PURCHASE" -> "صورتحساب خرید کالا و خدمات"
                "RETURN_SALE" -> "صورتحساب برگشت از فروش کالا و خدمات"
                "RETURN_PURCHASE" -> "صورتحساب برگشت از خرید کالا و خدمات"
                else -> "صورتحساب فروش کالا و خدمات"
            }
            val partyLabel = when (invoice.invoiceType) {
                "PURCHASE", "RETURN_PURCHASE" -> "نام فروشنده (تأمین‌کننده)"
                else -> "نام خریدار"
            }
            sb.append("عنوان,$csvDocTitle ${storeProfile.name}\n")
            sb.append("شماره فاکتور,${invoice.invoiceNumber}\n")
            sb.append("تاریخ صدور,${invoice.jalaliDate}\n")
            sb.append("$partyLabel,${invoice.partyName}\n")
            sb.append("شماره تماس خریدار,${invoice.partyPhone}\n")
            sb.append("نوع تسویه,${NumberFormatUtils.formatPaymentType(invoice.paymentType)}\n")
            sb.append("\n")
            sb.append("ردیف,کد کالا,شرح کالا,تعداد,واحد,فی به تومان,تخفیف,مبلغ کل (تومان)\n")

            items.forEachIndexed { index, item ->
                sb.append("${index + 1},")
                sb.append("${item.barcode.ifEmpty { item.productId.toString() }},")
                sb.append("\"${item.productName.replace("\"", "\"\"")}\",")
                sb.append("${item.quantity},")
                sb.append("${item.unitName},")
                sb.append("${item.unitPrice},")
                sb.append("${item.discount},")
                sb.append("${item.totalPrice}\n")
            }

            sb.append("\n")
            sb.append("جمع اقلام,,,,,${invoice.subtotalAmount}\n")
            if (invoice.discountAmount > 0) sb.append("تخفیف کلی,,,,,${invoice.discountAmount}\n")
            if (invoice.taxAmount > 0) sb.append("مالیات ارزش افزوده,,,,,${invoice.taxAmount}\n")
            if (invoice.directExpenseAmount > 0) sb.append("هزینه مستقیم (${invoice.directExpenseType}),,,,,${invoice.directExpenseAmount}\n")
            sb.append("مبلغ نهایی قابل پرداخت,,,,,${invoice.totalAmount}\n")
            sb.append("پرداخت نقد,,,,,${invoice.cashAmount}\n")
            sb.append("پرداخت پوز,,,,,${invoice.posAmount}\n")
            sb.append("پرداخت چک,,,,,${invoice.chequeAmount}\n")
            sb.append("مانده نسیه,,,,,${invoice.creditAmount}\n")

            val fos = FileOutputStream(file)
            fos.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * خروجی گزارش مالی جامع (معین، تراز، اسناد، سود و زیان) به صورت اکسل
     */
    fun exportReportCsv(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File? {
        return try {
            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()
            val fileName = "Report_${System.currentTimeMillis()}.csv"
            val file = File(dir, fileName)

            val sb = StringBuilder()
            sb.append('\uFEFF') // UTF-8 BOM
            sb.append("گزارش:,$reportTitle\n")
            sb.append("تاریخ ایجاد:,${JalaliDate.now().format(includeTime = true)}\n\n")

            sb.append(headers.joinToString(separator = ",") { "\"${it.replace("\"", "\"\"")}\"" })
            sb.append("\n")

            for (row in rows) {
                sb.append(row.joinToString(separator = ",") { "\"${it.replace("\"", "\"\"")}\"" })
                sb.append("\n")
            }

            val fos = FileOutputStream(file)
            fos.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * خروجی گزارش مالی جامع به صورت PDF
     */
    fun exportReportPdf(
        context: Context,
        reportTitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
            }

            // Title Header
            paint.color = Color.rgb(30, 34, 42)
            canvas.drawRect(20f, 20f, (pageWidth - 20).toFloat(), 75f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 14f
            textPaint.isFakeBoldText = true
            drawTextRtl(canvas, textPaint, reportTitle, (pageWidth - 35).toFloat(), 45f)

            textPaint.textSize = 9f
            textPaint.isFakeBoldText = false
            drawTextRtl(canvas, textPaint, "تاریخ گزارش: ${JalaliDate.now().format(includeTime = true)}", (pageWidth - 35).toFloat(), 62f)

            var y = 95f

            // Table Header
            paint.color = Color.rgb(55, 65, 80)
            canvas.drawRect(20f, y, (pageWidth - 20).toFloat(), y + 22f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 8.5f
            textPaint.isFakeBoldText = true

            val colCount = headers.size
            val colWidth = (pageWidth - 40f) / colCount.toFloat()

            for (i in headers.indices) {
                val headerX = (pageWidth - 20f) - (i * colWidth) - 10f
                drawTextRtl(canvas, textPaint, headers[i], headerX, y + 15f)
            }

            y += 22f
            textPaint.color = Color.BLACK
            textPaint.isFakeBoldText = false
            textPaint.textSize = 8f

            val maxRowsOnPage = 32
            for ((rowIndex, row) in rows.take(maxRowsOnPage).withIndex()) {
                val rowBg = if (rowIndex % 2 == 0) Color.WHITE else Color.rgb(248, 246, 242)
                paint.color = rowBg
                canvas.drawRect(20f, y, (pageWidth - 20).toFloat(), y + 18f, paint)

                for (colIndex in row.indices) {
                    val cellX = (pageWidth - 20f) - (colIndex * colWidth) - 10f
                    val cellValue = row[colIndex]
                    drawTextRtl(canvas, textPaint, cellValue.take(24), cellX, y + 13f)
                }
                y += 18f
            }

            pdfDocument.finishPage(page)

            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Report_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawTextRtl(canvas: Canvas, paint: Paint, text: String, x: Float, y: Float) {
        val width = paint.measureText(text)
        canvas.drawText(text, x - width, y, paint)
    }
}
