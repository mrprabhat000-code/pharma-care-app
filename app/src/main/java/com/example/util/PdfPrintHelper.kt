package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import com.example.data.Medicine
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfPrintHelper {

    fun printMedicineBill(
        context: Context,
        medicine: Medicine,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "${medicine.name.replace(" ", "_")}_Bill"
        
        printManager.print(jobName, object : PrintDocumentAdapter() {
            private var pdfDocument: PdfDocument? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Prepare metadata
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }

                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 points
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                try {
                    drawBill(
                        context,
                        canvas,
                        medicine,
                        shopName,
                        operatorName,
                        operatorPhone,
                        licenceNumber,
                        shopAddress
                    )
                } catch (e: Exception) {
                    pdf.close()
                    callback.onWriteFailed(e.message)
                    return
                }

                pdf.finishPage(page)

                try {
                    val outputStream = FileOutputStream(destination.fileDescriptor)
                    pdf.writeTo(outputStream)
                    outputStream.close()
                } catch (e: IOException) {
                    callback.onWriteFailed(e.message)
                    return
                } finally {
                    pdf.close()
                }

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawBill(
        context: Context,
        canvas: Canvas,
        medicine: Medicine,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 150, 136) // Medical teal
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subTitlePaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val headingPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
        }
        val boldValuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val successPaint = Paint().apply {
            color = Color.rgb(46, 125, 50) // Material Green
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val printDate = df.format(Date())

        var y = 40f
        val xStart = 40f
        val xVal = 200f
        val pageWidth = 595f
        val xEnd = pageWidth - 40f

        // Draw Header Border / Line
        val borderPaint = Paint().apply {
            color = Color.rgb(0, 150, 136)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw Header
        val displayShopTitle = if (shopName.trim().isNotEmpty()) "${shopName.trim().uppercase(Locale.getDefault())}" else "PHARMA CARE INVOICE"
        canvas.drawText(displayShopTitle, xStart, y, titlePaint)
        y += 20f
        canvas.drawText("Professional Medical Inventory Management System", xStart, y, subTitlePaint)
        y += 25f

        // Thin line
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(xStart, y, xEnd, y, linePaint)
        y += 20f

        // Invoice Metadata
        canvas.drawText("Invoice Number:", xStart, y, textPaint)
        canvas.drawText("PC-${System.currentTimeMillis() % 1000000}", xVal, y, boldValuePaint)
        
        val dateX = pageWidth - 200f
        canvas.drawText("Date & Time: ${printDate}", dateX, y, textPaint)
        y += 20f

        canvas.drawText("Store Operator:", xStart, y, textPaint)
        canvas.drawText("$operatorName ($operatorPhone)", xVal, y, boldValuePaint)
        y += 20f

        if (licenceNumber.isNotEmpty()) {
            canvas.drawText("Drug Licence No:", xStart, y, textPaint)
            canvas.drawText(licenceNumber, xVal, y, boldValuePaint)
            y += 20f
        }

        canvas.drawText("Billing Status:", xStart, y, textPaint)
        canvas.drawText("PAID / SAVED", xVal, y, successPaint)
        y += 20f

        canvas.drawText("Shop Address:", xStart, y, textPaint)
        var displayAddress = shopAddress
        if (displayAddress.length > 55) {
            displayAddress = displayAddress.take(52) + "..."
        }
        canvas.drawText(displayAddress, xVal, y, boldValuePaint)
        y += 30f

        // Item Header Row
        canvas.drawRect(xStart, y - 18f, xEnd, y + 6f, Paint().apply {
            color = Color.rgb(240, 244, 244)
        })
        canvas.drawText("PARTICULARS", xStart + 10f, y, headingPaint)
        canvas.drawText("VALUES / DETAILS", xVal + 10f, y, headingPaint)
        y += 25f

        // Item Fields Map helper logic
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val typeRepresent = when {
            medicine.photoUri.startsWith("content://") -> "Custom Image Photo Selected"
            medicine.photoUri.isNotEmpty() -> medicine.photoUri.replace("med_icon_", "").uppercase(Locale.getDefault())
            else -> "Standard Medication Icon"
        }

        val medicineFields = listOf(
            "Medicine Name" to medicine.name,
            "Item Category / Icon" to typeRepresent,
            "Batch Number" to medicine.batchNumber.ifEmpty { "N/A" },
            "Stock Level (As Saved)" to "${medicine.stockQty} Pcs",
            "Manufacturer Unit Cost (Buy)" to "₹${String.format(Locale.getDefault(), "%,.2f", medicine.buyPrice)}",
            "Store Selling Price" to "₹${String.format(Locale.getDefault(), "%,.2f", medicine.sellPrice)}",
            "Max Retail Price (MRP)" to "₹${String.format(Locale.getDefault(), "%,.2f", medicine.mrp)}",
            "Expiry Date" to sdf.format(Date(medicine.expiryTimestamp)),
            "Total Invested Capital" to "₹${String.format(Locale.getDefault(), "%,.2f", medicine.buyPrice * medicine.stockQty)}",
            "Estimated Sales Value" to "₹${String.format(Locale.getDefault(), "%,.2f", medicine.sellPrice * medicine.stockQty)}"
        )

        for ((label, value) in medicineFields) {
            // Draw field divider line
            canvas.drawLine(xStart, y + 4f, xEnd, y + 4f, linePaint)
            
            canvas.drawText(label, xStart + 10f, y, textPaint)
            canvas.drawText(value, xVal + 10f, y, boldValuePaint)
            y += 26f
        }

        y += 20f
        
        // Footer Notes & Signature Box
        canvas.drawLine(xStart, y, xEnd, y, borderPaint)
        y += 25f

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("Terms & Conditions:", xStart, y, headingPaint)
        y += 18f
        canvas.drawText("1. This document is system-generated based on the digital inventory record.", xStart, y, footerPaint)
        y += 14f
        canvas.drawText("2. Expiry dates must be carefully counter-checked at point of sale.", xStart, y, footerPaint)
        y += 14f
        canvas.drawText("3. Printed details are accurate representations of the saved database state.", xStart, y, footerPaint)

        // Draw signature line on bottom-right
        val sigY = y + 30f
        val sigX = xEnd - 180f
        canvas.drawLine(sigX, sigY, xEnd, sigY, linePaint)
        canvas.drawText("Authorized Seal / Sign", sigX + 18f, sigY + 15f, Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
    }

    fun printMultiMedicineBill(
        context: Context,
        medicines: List<Medicine>,
        quantities: Map<Int, Int>,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "PharmaCare_Combined_Bill_${System.currentTimeMillis() % 10000}"
        
        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Prepare metadata
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }

                val pdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 points
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                try {
                    drawMultiBill(
                        context,
                        canvas,
                        medicines,
                        quantities,
                        shopName,
                        operatorName,
                        operatorPhone,
                        licenceNumber,
                        shopAddress
                    )
                } catch (e: Exception) {
                    pdf.close()
                    callback.onWriteFailed(e.message)
                    return
                }

                pdf.finishPage(page)

                try {
                    val outputStream = FileOutputStream(destination.fileDescriptor)
                    pdf.writeTo(outputStream)
                    outputStream.close()
                } catch (e: IOException) {
                    callback.onWriteFailed(e.message)
                    return
                } finally {
                    pdf.close()
                }

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawMultiBill(
        context: Context,
        canvas: Canvas,
        medicines: List<Medicine>,
        quantities: Map<Int, Int>,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 150, 136) // Medical teal
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subTitlePaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val headingPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val boldValuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val successPaint = Paint().apply {
            color = Color.rgb(46, 125, 50) // Material Green
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val printDate = df.format(Date())

        var y = 40f
        val xStart = 40f
        val pageWidth = 595f
        val xEnd = pageWidth - 40f

        // Draw Header Border / Line
        val borderPaint = Paint().apply {
            color = Color.rgb(0, 150, 136)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw Header
        val displayShopTitle = if (shopName.trim().isNotEmpty()) "${shopName.trim().uppercase(Locale.getDefault())}" else "PHARMA CARE INVOICE"
        canvas.drawText(displayShopTitle, xStart, y, titlePaint)
        y += 20f
        canvas.drawText("Professional Medical Inventory Management System - Combined Bill", xStart, y, subTitlePaint)
        y += 25f

        // Thin line
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(xStart, y, xEnd, y, linePaint)
        y += 20f

        // Invoice Metadata
        canvas.drawText("Invoice Number:", xStart, y, textPaint)
        canvas.drawText("PC-MULT-${System.currentTimeMillis() % 1000000}", xStart + 110f, y, boldValuePaint)
        
        val dateX = pageWidth - 220f
        canvas.drawText("Date & Time: ${printDate}", dateX, y, textPaint)
        y += 20f

        canvas.drawText("Store Operator:", xStart, y, textPaint)
        canvas.drawText("$operatorName ($operatorPhone)", xStart + 110f, y, boldValuePaint)
        y += 20f

        if (licenceNumber.isNotEmpty()) {
            canvas.drawText("Drug Licence No:", xStart, y, textPaint)
            canvas.drawText(licenceNumber, xStart + 110f, y, boldValuePaint)
            y += 20f
        }

        canvas.drawText("Billing Status:", xStart, y, textPaint)
        canvas.drawText("PAID / SAVED", xStart + 110f, y, successPaint)
        y += 20f

        canvas.drawText("Shop Address:", xStart, y, textPaint)
        var displayAddress = shopAddress
        if (displayAddress.length > 55) {
            displayAddress = displayAddress.take(52) + "..."
        }
        canvas.drawText(displayAddress, xStart + 110f, y, boldValuePaint)
        y += 30f

        // Create table header rect
        canvas.drawRect(xStart, y - 18f, xEnd, y + 6f, Paint().apply {
            color = Color.rgb(240, 244, 244)
        })

        // Draw table column headers
        val firstColX = xStart + 2f       // S.No
        val secondColX = xStart + 22f     // Particulars
        val thirdColX = xStart + 155f     // Batch No.
        val fourthColX = xStart + 230f    // Expiry
        val mrpColX = xStart + 310f       // MRP
        val fifthColX = xStart + 375f     // Rate
        val sixthColX = xStart + 438f     // Qty
        val seventhColX = xStart + 472f   // Total Amount

        val tableHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableTextPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val tableBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("S.N.", firstColX, y, tableHeaderPaint)
        canvas.drawText("PARTICULARS", secondColX, y, tableHeaderPaint)
        canvas.drawText("BATCH", thirdColX, y, tableHeaderPaint)
        canvas.drawText("EXPIRY", fourthColX, y, tableHeaderPaint)
        canvas.drawText("MRP", mrpColX, y, tableHeaderPaint)
        canvas.drawText("RATE", fifthColX, y, tableHeaderPaint)
        canvas.drawText("QTY", sixthColX, y, tableHeaderPaint)
        canvas.drawText("AMOUNT", seventhColX, y, tableHeaderPaint)
        
        y += 25f

        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        var grandTotal = 0.0

        medicines.forEachIndexed { idx, med ->
            canvas.drawLine(xStart, y - 16f, xEnd, y - 16f, linePaint)

            val qty = quantities[med.id] ?: 1
            val unitPrice = med.buyPrice
            val totalAmount = unitPrice * qty
            grandTotal += totalAmount

            canvas.drawText((idx + 1).toString(), firstColX, y, tableTextPaint)
            
            // Truncate name if it exceeds the column width
            var displayName = med.name
            if (displayName.length > 20) {
                displayName = displayName.take(17) + "..."
            }
            canvas.drawText(displayName, secondColX, y, tableBoldPaint)
            
            val batchStr = med.batchNumber.ifEmpty { "N/A" }
            val displayBatch = if (batchStr.length > 8) batchStr.take(6) + ".." else batchStr
            canvas.drawText(displayBatch, thirdColX, y, tableTextPaint)
            
            val expiryStr = sdf.format(Date(med.expiryTimestamp))
            canvas.drawText(expiryStr, fourthColX, y, tableTextPaint)
            
            canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", med.mrp)}", mrpColX, y, tableTextPaint)
            canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", unitPrice)}", fifthColX, y, tableTextPaint)
            canvas.drawText(qty.toString(), sixthColX, y, tableBoldPaint)
            canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", totalAmount)}", seventhColX, y, tableBoldPaint)

            y += 24f
        }

        canvas.drawLine(xStart, y - 16f, xEnd, y - 16f, linePaint)
        y += 10f

        // Draw Summary Card
        canvas.drawRect(pageWidth - 280f, y, xEnd, y + 54f, Paint().apply {
            color = Color.rgb(244, 252, 252)
        })
        canvas.drawText("GRAND TOTAL:", pageWidth - 270f, y + 22f, headingPaint)
        canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", grandTotal)}", pageWidth - 140f, y + 22f, Paint().apply {
            color = Color.rgb(0, 150, 136)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        canvas.drawText("Invoiced Items: ${medicines.size} • Total Units: ${quantities.values.sum()}", pageWidth - 270f, y + 42f, textPaint)

        y += 85f

        // Footer Notes & Signature Box
        canvas.drawLine(xStart, y, xEnd, y, borderPaint)
        y += 25f

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("Terms & Conditions:", xStart, y, headingPaint)
        y += 18f
        canvas.drawText("1. This document is system-generated based on the digital inventory record.", xStart, y, footerPaint)
        y += 14f
        canvas.drawText("2. Expiry dates must be carefully checked before selling medicines.", xStart, y, footerPaint)
        y += 14f
        canvas.drawText("3. Printed details represent the inventory system state at billing time.", xStart, y, footerPaint)

        // Draw signature line on bottom-right
        val sY = y + 30f
        val sX = xEnd - 180f
        canvas.drawLine(sX, sY, xEnd, sY, linePaint)
        canvas.drawText("Authorized Seal / Sign", sX + 18f, sY + 15f, Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
    }

    fun printInventoryBackupPdf(
        context: Context,
        medicines: List<Medicine>,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "PharmaCare_Inventory_Backup_${System.currentTimeMillis() % 10000}"
        
        printManager.print(jobName, object : PrintDocumentAdapter() {
            private val arrangedMedicines = medicines.sortedBy { it.name.lowercase(Locale.getDefault()) }
            private val pageLists = mutableListOf<List<Medicine>>()
            private var totalPagesCount = 1

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }

                // Partition medicines into pages
                pageLists.clear()
                var currentIndex = 0
                val itemsPage1 = 20
                val itemsPageN = 26

                while (currentIndex < arrangedMedicines.size) {
                    val limit = if (pageLists.isEmpty()) itemsPage1 else itemsPageN
                    val nextSlice = arrangedMedicines.subList(currentIndex, Math.min(currentIndex + limit, arrangedMedicines.size))
                    pageLists.add(nextSlice)
                    currentIndex += nextSlice.size
                }

                // Calculate if summary needs an extra page
                val lastPageIndex = pageLists.size - 1
                val lastPageSize = if (lastPageIndex >= 0) pageLists[lastPageIndex].size else 0
                val lastPageStartY = if (lastPageIndex == 0) 225f else 130f
                val lastPageEndY = lastPageStartY + (lastPageSize * 22f)
                val summaryHeight = 110f
                val bottomMarginY = 842f - 50f

                if (lastPageEndY + summaryHeight > bottomMarginY) {
                    totalPagesCount = pageLists.size + 1
                } else {
                    totalPagesCount = Math.max(1, pageLists.size)
                }

                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(totalPagesCount)
                    .build()

                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }

                val pdf = PdfDocument()

                try {
                    for (pageNumber in 1..totalPagesCount) {
                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas

                        drawBackupPage(
                            context,
                            canvas,
                            pageNumber,
                            pageLists,
                            totalPagesCount,
                            shopName,
                            operatorName,
                            operatorPhone,
                            licenceNumber,
                            shopAddress
                        )

                        pdf.finishPage(page)
                    }

                    val outputStream = FileOutputStream(destination.fileDescriptor)
                    pdf.writeTo(outputStream)
                    outputStream.close()
                } catch (e: Exception) {
                    pdf.close()
                    callback.onWriteFailed(e.message)
                    return
                } finally {
                    pdf.close()
                }

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawBackupPage(
        context: Context,
        canvas: Canvas,
        pageNumber: Int,
        pageLists: List<List<Medicine>>,
        totalPagesCount: Int,
        shopName: String,
        operatorName: String,
        operatorPhone: String,
        licenceNumber: String,
        shopAddress: String
    ) {
        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.rgb(0, 150, 136) // Medical teal
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subTitlePaint = Paint().apply {
            color = Color.rgb(100, 110, 120)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val headingPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
        }
        val boldValuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY;
            strokeWidth = 1f
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(0, 150, 136)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        val printDate = df.format(Date())

        val xStart = 35f
        val xEnd = 560f
        var y = 45f

        // Draw page footer on every page
        val footerText = "${if (shopName.isNotEmpty()) shopName else "PharmaCare"} Offline Backup Report • Page $pageNumber of $totalPagesCount • Generated $printDate"
        canvas.drawText(footerText, xStart, 810f, Paint().apply {
            color = Color.GRAY
            textSize = 8f
        })
        canvas.drawLine(xStart, 798f, xEnd, 798f, linePaint)

        // Calculate metrics
        val totalMedicines = pageLists.flatten()
        val totalStockQty = totalMedicines.sumOf { it.stockQty }
        val grandTotalInvested = totalMedicines.sumOf { it.buyPrice * it.stockQty }
        val grandTotalEstimatedSales = totalMedicines.sumOf { it.sellPrice * it.stockQty }

        if (pageNumber == 1) {
            // Large Premium Header
            val displayBackupTitle = if (shopName.trim().isNotEmpty()) "${shopName.trim().uppercase(Locale.getDefault())} DATABASE BACKUP REPORT" else "PHARMACARE DATABASE BACKUP REPORT"
            canvas.drawText(displayBackupTitle, xStart, y, titlePaint)
            y += 16f
            canvas.drawText("Complete Offline Medicine Asset Registry Ledger & System State Backup", xStart, y, subTitlePaint)
            y += 20f

            canvas.drawLine(xStart, y, xEnd, y, borderPaint)
            y += 18f

            // Metadata Columns
            canvas.drawText("Backup ID: PC-BAK-${System.currentTimeMillis() / 1000}", xStart, y, textPaint)
            canvas.drawText("Registered Store Owner: $operatorName", xStart + 260f, y, textPaint)
            y += 14f
            canvas.drawText("Items Count: ${totalMedicines.size}", xStart, y, textPaint)
            canvas.drawText("Support Hotline: $operatorPhone", xStart + 260f, y, textPaint)
            y += 14f
            canvas.drawText("Drug Licence: ${licenceNumber.ifEmpty { "N/A" }}", xStart, y, textPaint)
            canvas.drawText("Storage Status: VERIFIED SAFE & SAVED", xStart + 260f, y, Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            y += 14f
            var displayAddress = shopAddress
            if (displayAddress.length > 90) {
                displayAddress = displayAddress.take(87) + "..."
            }
            canvas.drawText("Shop Address: $displayAddress", xStart, y, textPaint)

            y += 14f
            canvas.drawLine(xStart, y, xEnd, y, linePaint)

            // Visual Summary blocks on page 1
            y += 12f
            val blockWidth = (xEnd - xStart - 20f) / 3f

            // Block 1
            canvas.drawRect(xStart, y, xStart + blockWidth, y + 36f, Paint().apply { color = Color.rgb(244, 252, 252) })
            canvas.drawText("INVESTED VALUE (BUY)", xStart + 8f, y + 14f, Paint().apply { color = Color.GRAY; textSize = 7f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", grandTotalInvested)}", xStart + 8f, y + 28f, Paint().apply { color = Color.rgb(0, 150, 136); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

            // Block 2
            val b2Start = xStart + blockWidth + 10f
            canvas.drawRect(b2Start, y, b2Start + blockWidth, y + 36f, Paint().apply { color = Color.rgb(255, 253, 243) })
            canvas.drawText("ESTIMATED SALES (SELL)", b2Start + 8f, y + 14f, Paint().apply { color = Color.GRAY; textSize = 7f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            canvas.drawText("₹${String.format(Locale.getDefault(), "%,.2f", grandTotalEstimatedSales)}", b2Start + 8f, y + 28f, Paint().apply { color = Color.rgb(197, 160, 0); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

            // Block 3
            val b3Start = b2Start + blockWidth + 10f
            canvas.drawRect(b3Start, y, xEnd, y + 36f, Paint().apply { color = Color.rgb(244, 248, 244) })
            canvas.drawText("TOTAL INVENTORY STOCK", b3Start + 8f, y + 14f, Paint().apply { color = Color.GRAY; textSize = 7f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            canvas.drawText("$totalStockQty Pcs", b3Start + 8f, y + 28f, Paint().apply { color = Color.rgb(46, 125, 50); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

            y += 50f
        } else {
            // Slim Header on Page 2+
            val displayTitleCont = if (shopName.isNotEmpty()) "${shopName.uppercase(Locale.getDefault())} DATABASE REGISTER (CONTINUED)" else "PHARMACARE DATABASE BACKUP REGISTER (CONTINUED)"
            canvas.drawText(displayTitleCont, xStart, y, Paint().apply {
                color = Color.rgb(0, 150, 136)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            y += 12f
            canvas.drawText("Page $pageNumber Registry Ledger of Medicines Inventory Backup", xStart, y, subTitlePaint)
            y += 16f
            canvas.drawLine(xStart, y, xEnd, y, linePaint)
            y += 15f
        }

        // Draw Table Header Row (if page contains medicine table rows)
        val hasTableRows = pageNumber <= pageLists.size
        if (hasTableRows) {
            canvas.drawRect(xStart, y - 14f, xEnd, y + 6f, Paint().apply {
                color = Color.rgb(240, 244, 244)
            })

            val xSNo = 38f
            val xName = 65f
            val xBatch = 185f
            val xExpiry = 245f
            val xStock = 310f
            val xBuy = 355f
            val xSell = 410f
            val xMRP = 465f
            val xTotal = 520f

            canvas.drawText("S.N.", xSNo, y, headingPaint)
            canvas.drawText("MEDICINE NAME", xName, y, headingPaint)
            canvas.drawText("BATCH", xBatch, y, headingPaint)
            canvas.drawText("EXPIRY", xExpiry, y, headingPaint)
            canvas.drawText("STOCK", xStock, y, headingPaint)
            canvas.drawText("BUY (₹)", xBuy, y, headingPaint)
            canvas.drawText("SELL (₹)", xSell, y, headingPaint)
            canvas.drawText("MRP (₹)", xMRP, y, headingPaint)
            canvas.drawText("TOT INVEST", xTotal, y, headingPaint)

            y += 22f

            // Draw medicine items for this page
            val items = pageLists[pageNumber - 1]
            val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

            // S.No starts accumulated based on previous pages
            var sNoAccumulator = 1
            for (p in 0 until (pageNumber - 1)) {
                sNoAccumulator += pageLists[p].size
            }

            for (med in items) {
                canvas.drawLine(xStart, y - 14f, xEnd, y - 14f, linePaint)

                // S.No
                canvas.drawText(sNoAccumulator.toString(), xSNo, y, textPaint)
                sNoAccumulator++

                // Name
                var displayName = med.name
                if (displayName.length > 22) {
                    displayName = displayName.take(19) + "..."
                }
                canvas.drawText(displayName, xName, y, boldValuePaint)

                // Batch
                val batchStr = med.batchNumber.ifEmpty { "N/A" }
                val displayBatch = if (batchStr.length > 10) batchStr.take(8) + ".." else batchStr
                canvas.drawText(displayBatch, xBatch, y, textPaint)

                // Expiry
                val expiryStr = sdf.format(Date(med.expiryTimestamp))
                canvas.drawText(expiryStr, xExpiry, y, textPaint)

                // Stock
                canvas.drawText(med.stockQty.toString(), xStock, y, boldValuePaint)

                // Prices
                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", med.buyPrice), xBuy, y, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", med.sellPrice), xSell, y, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", med.mrp), xMRP, y, textPaint)

                // Total Invested Value for this item
                val totalValue = med.buyPrice * med.stockQty
                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", totalValue), xTotal, y, boldValuePaint)

                y += 22f
            }
            canvas.drawLine(xStart, y - 14f, xEnd, y - 14f, linePaint)
        }

        // Draw final summary and signature on the last page
        val isLastPage = pageNumber == totalPagesCount
        if (isLastPage) {
            y += 10f

            // Grand Summary Card
            canvas.drawRect(xStart, y, xEnd, y + 68f, Paint().apply {
                color = Color.rgb(244, 252, 252)
                style = Paint.Style.FILL
            })
            canvas.drawRect(xStart, y, xEnd, y + 68f, Paint().apply {
                color = Color.rgb(0, 150, 136)
                strokeWidth = 1f
                style = Paint.Style.STROKE
            })

            canvas.drawText("GRAND AUDIT SUMMARY REPORT", xStart + 15f, y + 20f, headingPaint)
            
            val infoText = "Total Asset Portfolio Capital: ₹${String.format(Locale.getDefault(), "%,.2f", grandTotalInvested)}  •  Potential Sales Revenue: ₹${String.format(Locale.getDefault(), "%,.2f", grandTotalEstimatedSales)}"
            canvas.drawText(infoText, xStart + 15f, y + 36f, textPaint)

            val grandTotalProfit = grandTotalEstimatedSales - grandTotalInvested
            val profitText = "Expected Max Net Profit Margin: ₹${String.format(Locale.getDefault(), "%,.2f", grandTotalProfit)}  •  Total Units: $totalStockQty Medicines"
            canvas.drawText(profitText, xStart + 15f, y + 52f, Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })

            y += 90f

            // Signatures
            val sigY = y + 25f
            val sigX1 = xStart + 20f
            val sigX2 = xEnd - 180f

            canvas.drawLine(sigX1, sigY, sigX1 + 160f, sigY, linePaint)
            canvas.drawText("Inventory Auditor Sign", sigX1 + 18f, sigY + 14f, Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })

            canvas.drawLine(sigX2, sigY, xEnd, sigY, linePaint)
            canvas.drawText("Authorized Seal & Stamp", sigX2 + 18f, sigY + 14f, Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
        }
    }
}

