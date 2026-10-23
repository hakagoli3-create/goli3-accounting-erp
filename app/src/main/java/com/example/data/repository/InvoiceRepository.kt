package com.example.data.repository

import com.example.data.dao.AccountingDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.InvoiceDao
import com.example.data.dao.PartyDao
import com.example.data.dao.TreasuryDao
import com.example.data.model.InventoryMovementEntity
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.InvoiceWithItems
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.util.JalaliDate
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import kotlinx.coroutines.flow.Flow

class InvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val inventoryDao: InventoryDao,
    private val partyDao: PartyDao,
    private val treasuryDao: TreasuryDao,
    private val accountingDao: AccountingDao,
    private val appDatabase: AppDatabase? = null
) {

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (appDatabase != null) {
            appDatabase.withTransaction { block() }
        } else {
            block()
        }
    }

    val allInvoicesWithItems: Flow<List<InvoiceWithItems>> = invoiceDao.getAllInvoicesWithItems()
    val totalSalesSum: Flow<Long?> = invoiceDao.getTotalSalesSum()

    fun getTodaySalesSum(todayJalali: String): Flow<Long?> = invoiceDao.getTodaySalesSum(todayJalali)
    fun getTodayInvoiceCount(todayJalali: String): Flow<Int> = invoiceDao.getTodayInvoiceCount(todayJalali)
    fun getInvoicesForParty(partyId: Long): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesForParty(partyId)

    suspend fun getInvoiceWithItemsById(id: Long): InvoiceWithItems? =
        invoiceDao.getInvoiceWithItemsById(id)

    /**
     * ثبت کامل فاکتور به همراه آپدیت انبارداری، خزانه‌داری، مانده حساب مشتری، و صدور سند اتوماتیک دوبل حسابداری
     * با تضمین اتمیک بودن کلیه عملیات چندجدولی در یک تراکنش واحد
     */
    /**
     * اعتبارسنجی و تضمین انبار مقصد معتبر جهت رعایت قطعی قیود کلید خارجی
     */
    suspend fun resolveAndEnsureWarehouse(requestedWarehouseId: Long): Long {
        if (requestedWarehouseId > 0) {
            val wh = inventoryDao.getWarehouseById(requestedWarehouseId)
            if (wh != null) return wh.id
        }
        val defaultWh = inventoryDao.getDefaultWarehouse()
        if (defaultWh != null) return defaultWh.id

        val anyWh = inventoryDao.getFirstWarehouse()
        if (anyWh != null) return anyWh.id

        return try {
            inventoryDao.insertWarehouse(
                com.example.data.model.WarehouseEntity(
                    code = "WH-01",
                    name = "انبار مرکزی",
                    isDefault = true
                )
            )
        } catch (e: Exception) {
            inventoryDao.getFirstWarehouse()?.id ?: 1L
        }
    }

    /**
     * اعتبارسنجی و حل تک‌تک اقلام فاکتور تا تضمین شود تمام شناسه‌های کالا در جدول products وجود دارند
     */
    suspend fun resolveAndEnsureProducts(
        items: List<InvoiceItemEntity>,
        invoiceType: String
    ): List<InvoiceItemEntity> {
        return items.map { item ->
            val resolvedProductId = resolveSingleProduct(item, invoiceType)
            item.copy(id = 0L, productId = resolvedProductId)
        }
    }

    suspend fun resolveSingleProduct(item: InvoiceItemEntity, invoiceType: String): Long {
        if (item.productId > 0) {
            val existing = inventoryDao.getProductById(item.productId)
            if (existing != null) return existing.id
        }
        if (item.barcode.isNotBlank()) {
            val byBarcode = inventoryDao.getProductByBarcode(item.barcode.trim())
            if (byBarcode != null) return byBarcode.id
        }
        val byName = inventoryDao.getProductByName(item.productName.trim())
        if (byName != null) return byName.id

        // در صورتی که کالا هنوز در دیتابیس ثبت نشده باشد (مثلاً کالای سریع یا شناسه صفر)،
        // کالا ابتدا در جدول products ثبت می‌شود تا کلید خارجی در inventory_movements نقض نگردد.
        val codeGen = if (item.barcode.isNotBlank()) "PRD-${item.barcode.trim()}" else "PRD-${System.currentTimeMillis() % 100000}"
        val isPurchase = invoiceType in listOf("PURCHASE", "RETURN_PURCHASE")
        val buyPrice = if (isPurchase) item.unitPrice else (item.unitPrice * 0.75).toLong()

        val newProduct = com.example.data.model.ProductEntity(
            code = codeGen,
            barcode = item.barcode.trim(),
            name = item.productName.ifBlank { "کالای عمومی" },
            category = "عمومی",
            primaryUnit = if (item.isPrimaryUnit) item.unitName else "کارتن",
            secondaryUnit = if (!item.isPrimaryUnit) item.unitName else "عدد",
            conversionRate = if (item.conversionRate > 0) item.conversionRate else 1.0,
            buyPrice = maxOf(0L, buyPrice),
            retailPrice = maxOf(0L, item.unitPrice),
            wholesalePrice = maxOf(0L, item.unitPrice),
            currentStockInSecondary = 0.0
        )
        return inventoryDao.insertProduct(newProduct)
    }

    /**
     * ثبت کامل فاکتور به همراه آپدیت انبارداری، خزانه‌داری، مانده حساب مشتری، و صدور سند اتوماتیک دوبل حسابداری
     * با تضمین اتمیک بودن کلیه عملیات چندجدولی در یک تراکنش واحد و رعایت قیود کلید خارجی
     */
    suspend fun createInvoice(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        cheque: com.example.data.model.ChequeEntity? = null
    ): Long = runInTransaction {
        try {
            val validWarehouseId = resolveAndEnsureWarehouse(invoice.warehouseId)
            val resolvedItems = resolveAndEnsureProducts(items, invoice.invoiceType)
            val readyInvoice = invoice.copy(id = 0L, warehouseId = validWarehouseId)

            val invoiceId = invoiceDao.createInvoiceWithItems(readyInvoice, resolvedItems)

            // اگر پیش‌فاکتور (PROFORMA) باشد، از انبار کسر نمی‌شود و سند قطعی حسابداری صادر نمی‌گردد
            if (readyInvoice.status == "PROFORMA") {
                return@runInTransaction invoiceId
            }

            applyInvoiceEffects(readyInvoice.copy(id = invoiceId), resolvedItems, cheque, validWarehouseId)
            invoiceId
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error creating invoice: ${e.message}", e)
            throw e
        }
    }

    /**
     * اعمال اثرات انبارداری، حسابداری، و خزانه‌داری یک فاکتور قطعی
     */
    private suspend fun applyInvoiceEffects(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>,
        cheque: com.example.data.model.ChequeEntity? = null,
        warehouseIdOverride: Long? = null
    ) {
        val validWarehouseId = warehouseIdOverride ?: resolveAndEnsureWarehouse(invoice.warehouseId)

        // 1. به روزرسانی کاردکس و موجودی انبار برای هر قلم
        for (item in items) {
            val stockDelta = when (invoice.invoiceType) {
                "SALE", "POS_SALE" -> -item.quantityInSecondary // خروج از انبار
                "PURCHASE" -> item.quantityInSecondary // ورود به انبار
                "RETURN_SALE" -> item.quantityInSecondary // برگشت از فروش: بازگشت به انبار
                "RETURN_PURCHASE" -> -item.quantityInSecondary // برگشت از خرید: خروج از انبار
                else -> -item.quantityInSecondary
            }

            val validProductId = if (item.productId > 0 && inventoryDao.getProductById(item.productId) != null) {
                item.productId
            } else {
                resolveSingleProduct(item, invoice.invoiceType)
            }

            inventoryDao.insertMovement(
                InventoryMovementEntity(
                    productId = validProductId,
                    warehouseId = validWarehouseId,
                    jalaliDate = invoice.jalaliDate,
                    documentType = invoice.invoiceType,
                    referenceNumber = invoice.invoiceNumber,
                    quantitySecondary = stockDelta,
                    unitPrice = item.unitPrice,
                    description = "فاکتور ${invoice.invoiceNumber} (${invoice.invoiceType}) - ${item.productName}"
                )
            )
            inventoryDao.adjustStock(validProductId, stockDelta)
        }

        // 2. مقادیر بر مبنای نرخ تسعیر ذخیره‌شده فاکتور با دقت محاسباتی بالا (BigDecimal)
        val rate = if (invoice.currencyRateToBase > 0) invoice.currencyRateToBase else 1.0
        fun toBase(amount: Long): Long {
            if (rate == 1.0 || amount == 0L) return amount
            return java.math.BigDecimal(amount)
                .multiply(java.math.BigDecimal.valueOf(rate))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toLong()
        }

        val creditAmount = if (invoice.paymentType == "CREDIT") invoice.totalAmount else invoice.creditAmount
        val totalInBase = if (rate == 1.0 || invoice.totalAmountInBase <= 0L) toBase(invoice.totalAmount) else invoice.totalAmountInBase
        var creditInBase = if (rate == 1.0 || invoice.creditAmountInBase <= 0L) toBase(creditAmount) else invoice.creditAmountInBase
        var cashInBase = if (rate == 1.0 || invoice.cashAmountInBase <= 0L) toBase(invoice.cashAmount) else invoice.cashAmountInBase
        var posInBase = if (rate == 1.0 || invoice.posAmountInBase <= 0L) toBase(invoice.posAmount) else invoice.posAmountInBase
        var chequeInBase = if (rate == 1.0 || invoice.chequeAmountInBase <= 0L) toBase(invoice.chequeAmount) else invoice.chequeAmountInBase

        // در صورتی که مبلغ کل از مجموع پرداختی نقدی/بانکی/چک بیشتر باشد و نسیه صفر باشد، مانده تسویه‌نشده خودکار نسیه است
        val immediatePaidInBase = cashInBase + posInBase + chequeInBase
        if (creditInBase == 0L && immediatePaidInBase < totalInBase) {
            creditInBase = totalInBase - immediatePaidInBase
        }

        // توازن دقیق ریاضی مجموع اجزای پرداخت با مبلغ کل فاکتور در ارز پایه (رفع خطای گردکردن تسعیر)
        val paymentSum = cashInBase + posInBase + chequeInBase + creditInBase
        val roundingDiff = totalInBase - paymentSum
        if (roundingDiff != 0L) {
            when {
                creditInBase > 0 -> creditInBase += roundingDiff
                posInBase > 0 -> posInBase += roundingDiff
                cashInBase > 0 -> cashInBase += roundingDiff
                chequeInBase > 0 -> chequeInBase += roundingDiff
                else -> creditInBase += roundingDiff
            }
        }

        val currencyNote = if (invoice.currencyCode != "TOMAN" && rate != 1.0) {
            " (${invoice.totalAmount} ${invoice.currencyCode} - نرخ تسعیر: ${rate.toLong()} تومان)"
        } else ""

        // به روزرسانی مانده حساب طرف حساب (مشتری یا تأمین‌کننده)
        if (invoice.partyId != null && creditInBase > 0) {
            val factor = when (invoice.invoiceType) {
                "SALE", "POS_SALE" -> 1L // بدهکار شدن مشتری
                "PURCHASE" -> -1L // بستانکار شدن تامین‌کننده (بدهی ما به تامین‌کننده)
                "RETURN_SALE" -> -1L // کاهش بدهی مشتری
                "RETURN_PURCHASE" -> 1L // کاهش بدهی ما به تامین‌کننده
                else -> 1L
            }
            partyDao.updatePartyBalance(invoice.partyId, factor * creditInBase)
        }

        // 3. به روزرسانی خزانه‌داری (صندوق و پوز) به تفکیک ارز
        val treasuryFactor = when (invoice.invoiceType) {
            "SALE", "POS_SALE", "RETURN_PURCHASE" -> 1L
            "PURCHASE", "RETURN_SALE" -> -1L
            else -> 1L
        }
        if (invoice.cashAmount > 0) {
            treasuryDao.updateCashBalance(invoice.cashDeskId, treasuryFactor * cashInBase)
            val currBal = treasuryDao.getCurrencyBalance("CASH", invoice.cashDeskId, invoice.currencyCode)
            val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.cashAmount)
            treasuryDao.insertOrUpdateCurrencyBalance(
                com.example.data.model.TreasuryCurrencyBalanceEntity(
                    id = currBal?.id ?: 0L,
                    accountType = "CASH",
                    accountId = invoice.cashDeskId,
                    currencyCode = invoice.currencyCode,
                    balance = newBal
                )
            )
        }
        if (invoice.posAmount > 0) {
            treasuryDao.updateBankBalance(invoice.bankAccountId, treasuryFactor * posInBase)
            val currBal = treasuryDao.getCurrencyBalance("BANK", invoice.bankAccountId, invoice.currencyCode)
            val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.posAmount)
            treasuryDao.insertOrUpdateCurrencyBalance(
                com.example.data.model.TreasuryCurrencyBalanceEntity(
                    id = currBal?.id ?: 0L,
                    accountType = "BANK",
                    accountId = invoice.bankAccountId,
                    currencyCode = invoice.currencyCode,
                    balance = newBal
                )
            )
        }

        // 4. ثبت چک صیادی در خزانه‌داری در صورت وجود در همان تراکنش واحد
        if (cheque != null && invoice.chequeAmount > 0) {
            try {
                treasuryDao.insertCheque(cheque.copy(amount = invoice.chequeAmount))
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error inserting cheque in invoice effects", e)
            }
        }

        // 5. صدور خودکار سند دوبل حسابداری متناسب با نوع فاکتور در ارز پایه
        val journalItems = mutableListOf<JournalItemEntity>()

        when (invoice.invoiceType) {
            "SALE", "POS_SALE" -> {
                // بدهکاران
                if (cashInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "تسویه نقدی فاکتور فروش ${invoice.invoiceNumber}$currencyNote", debit = cashInBase, credit = 0L
                        )
                    )
                }
                if (posInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "تسویه کارتخوان فاکتور فروش ${invoice.invoiceNumber}$currencyNote", debit = posInBase, credit = 0L
                        )
                    )
                }
                if (creditInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10201, partyId = invoice.partyId,
                            description = "فروش نسیه به ${invoice.partyName} - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = creditInBase, credit = 0L
                        )
                    )
                }
                if (chequeInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10202, partyId = invoice.partyId,
                            description = "دریافت چک بابت فاکتور فروش ${invoice.invoiceNumber}$currencyNote", debit = chequeInBase, credit = 0L
                        )
                    )
                }
                // بستانکار: فروش کالا و خدمات
                journalItems.add(
                    JournalItemEntity(
                        voucherId = 0L, generalCode = 601, subsidiaryCode = 60101, partyId = invoice.partyId,
                        description = "درآمد حاصل از فروش کالا - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = totalInBase
                    )
                )

                // ثبت دقیق بهای تمام شده کالای فروش رفته (COGS) و کاهش موجودی کالا بر مبنای قیمت خرید در انبار
                var cogsInBase = 0L
                for (item in items) {
                    val prod = if (item.productId > 0) inventoryDao.getProductById(item.productId) else null
                    val costPrice = prod?.buyPrice ?: 0L
                    if (costPrice > 0L) {
                        val itemCost = (item.quantityInSecondary * costPrice).toLong()
                        cogsInBase += toBase(itemCost)
                    }
                }
                if (cogsInBase > 0L) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 701, subsidiaryCode = 70101, partyId = null,
                            description = "بهای تمام شده کالای فروش رفته - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = cogsInBase, credit = 0L
                        )
                    )
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = null,
                            description = "کاهش موجودی کالا بابت بهای تمام شده فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = cogsInBase
                        )
                    )
                }
            }
            "PURCHASE" -> {
                // بدهکار: خرید کالا و انبار
                journalItems.add(
                    JournalItemEntity(
                        voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = invoice.partyId,
                        description = "خرید کالا از ${invoice.partyName} - فاکتور خرید ${invoice.invoiceNumber}$currencyNote", debit = totalInBase, credit = 0L
                    )
                )
                // بستانکاران
                if (cashInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "پرداخت نقدی فاکتور خرید ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = cashInBase
                        )
                    )
                }
                if (posInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "پرداخت بانکی/کارت فاکتور خرید ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = posInBase
                        )
                    )
                }
                if (creditInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30101, partyId = invoice.partyId,
                            description = "خرید نسیه از ${invoice.partyName} - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = creditInBase
                        )
                    )
                }
                if (chequeInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30102, partyId = invoice.partyId,
                            description = "صدور چک بابت خرید فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = chequeInBase
                        )
                    )
                }
            }
            "RETURN_SALE" -> {
                // برگشت از فروش: بدهکار شدن حساب برگشت از فروش
                journalItems.add(
                    JournalItemEntity(
                        voucherId = 0L, generalCode = 601, subsidiaryCode = 60102, partyId = invoice.partyId,
                        description = "برگشت کالا از فروش مشتری ${invoice.partyName} - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = totalInBase, credit = 0L
                    )
                )
                // بستانکاران: عودت وجه
                if (cashInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "استرداد وجه نقد بابت مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = cashInBase
                        )
                    )
                }
                if (posInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "استرداد واریز بانکی بابت مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = posInBase
                        )
                    )
                }
                if (creditInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10201, partyId = invoice.partyId,
                            description = "بستانکار کردن حساب مشتری بابت مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = creditInBase
                        )
                    )
                }
                if (chequeInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10202, partyId = invoice.partyId,
                            description = "استرداد چک بابت مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = chequeInBase
                        )
                    )
                }

                // تعدیل بهای تمام شده کالای فروش رفته و بازگشت موجودی انبار برای اقلام مرجوعی
                var returnCogsInBase = 0L
                for (item in items) {
                    val prod = if (item.productId > 0) inventoryDao.getProductById(item.productId) else null
                    val costPrice = prod?.buyPrice ?: 0L
                    if (costPrice > 0L) {
                        val itemCost = (item.quantityInSecondary * costPrice).toLong()
                        returnCogsInBase += toBase(itemCost)
                    }
                }
                if (returnCogsInBase > 0L) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = null,
                            description = "ورود به انبار بابت مرجوعی کالا - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = returnCogsInBase, credit = 0L
                        )
                    )
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 701, subsidiaryCode = 70101, partyId = null,
                            description = "کاهش بهای تمام شده کالای فروش رفته بابت مرجوعی فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = returnCogsInBase
                        )
                    )
                }
            }
            "RETURN_PURCHASE" -> {
                // برگشت از خرید: بدهکار شدن صندوق/بانک یا کاهش بدهی به تأمین‌کننده
                if (cashInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "دریافت وجه نقد بابت مرجوعی خرید ${invoice.invoiceNumber}", debit = cashInBase, credit = 0L
                        )
                    )
                }
                if (posInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "واریز بانکی بابت مرجوعی خرید ${invoice.invoiceNumber}", debit = posInBase, credit = 0L
                        )
                    )
                }
                if (creditInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30101, partyId = invoice.partyId,
                            description = "کاهش بدهی به تأمین‌کننده بابت مرجوعی خرید ${invoice.invoiceNumber}", debit = creditInBase, credit = 0L
                        )
                    )
                }
                if (chequeInBase > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30102, partyId = invoice.partyId,
                            description = "استرداد چک بابت مرجوعی خرید ${invoice.invoiceNumber}", debit = chequeInBase, credit = 0L
                        )
                    )
                }
                // بستانکار: برگشت از خرید
                journalItems.add(
                    JournalItemEntity(
                        voucherId = 0L, generalCode = 103, subsidiaryCode = 10302, partyId = invoice.partyId,
                        description = "برگشت از خرید به تأمین‌کننده ${invoice.partyName} - فاکتور ${invoice.invoiceNumber}$currencyNote", debit = 0L, credit = totalInBase
                    )
                )
            }
        }

        val totalDebit = journalItems.sumOf { it.debit }
        val totalCredit = journalItems.sumOf { it.credit }
        if (totalDebit == totalCredit && journalItems.isNotEmpty()) {
            val nextVoucherNumber = accountingDao.getMaxVoucherNumber() + 1
            accountingDao.createVoucherWithItems(
                JournalEntryEntity(
                    voucherNumber = nextVoucherNumber,
                    jalaliDate = invoice.jalaliDate,
                    description = "سند خودکار ${invoice.invoiceNumber} (${invoice.invoiceType})",
                    isBalanced = true,
                    status = "FINAL"
                ),
                journalItems
            )
        }
    }

    /**
     * معکوس کردن کلیه اثرات فاکتور (انبار، طرف‌حساب، صندوق/بانک و صدور سند معکوس)
     */
    private suspend fun rollbackInvoiceEffects(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        // 1. معکوس کردن کاردکس انبار با اعتبارسنجی انبار و کالا جهت جلوگیری از خطای Foreign Key
        val targetWarehouseId = resolveAndEnsureWarehouse(invoice.warehouseId)

        for (item in items) {
            val reverseStock = when (invoice.invoiceType) {
                "SALE", "POS_SALE" -> item.quantityInSecondary // برگشت به انبار
                "PURCHASE" -> -item.quantityInSecondary // کسر از انبار
                "RETURN_SALE" -> -item.quantityInSecondary // کسر از انبار
                "RETURN_PURCHASE" -> item.quantityInSecondary // برگشت به انبار
                else -> item.quantityInSecondary
            }
            try {
                val productExists = inventoryDao.getProductById(item.productId) != null
                if (productExists) {
                    inventoryDao.insertMovement(
                        InventoryMovementEntity(
                            productId = item.productId,
                            warehouseId = targetWarehouseId,
                            jalaliDate = JalaliDate.now().toStandardString(),
                            documentType = "ROLLBACK_${invoice.invoiceType}",
                            referenceNumber = "REV-${invoice.invoiceNumber}",
                            quantitySecondary = reverseStock,
                            unitPrice = item.unitPrice,
                            description = "برگشت اثر فاکتور ${invoice.invoiceNumber} - ${item.productName}"
                        )
                    )
                    inventoryDao.adjustStock(item.productId, reverseStock)
                }
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error reversing stock for product ${item.productId}", e)
            }
        }

        // 2. معکوس کردن مانده حساب طرف‌حساب با در نظر گرفتن نرخ تسعیر با دقت بالا
        val rate = if (invoice.currencyRateToBase > 0) invoice.currencyRateToBase else 1.0
        fun toBase(amount: Long): Long {
            if (rate == 1.0 || amount == 0L) return amount
            return java.math.BigDecimal(amount)
                .multiply(java.math.BigDecimal.valueOf(rate))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toLong()
        }
        val toBaseCredit = if (invoice.paymentType == "CREDIT") invoice.totalAmount else invoice.creditAmount
        val totalInBase = if (rate == 1.0 || invoice.totalAmountInBase <= 0L) toBase(invoice.totalAmount) else invoice.totalAmountInBase
        var creditInBase = if (rate == 1.0 || invoice.creditAmountInBase <= 0L) toBase(toBaseCredit) else invoice.creditAmountInBase
        var cashInBase = if (rate == 1.0 || invoice.cashAmountInBase <= 0L) toBase(invoice.cashAmount) else invoice.cashAmountInBase
        var posInBase = if (rate == 1.0 || invoice.posAmountInBase <= 0L) toBase(invoice.posAmount) else invoice.posAmountInBase
        var chequeInBase = if (rate == 1.0 || invoice.chequeAmountInBase <= 0L) toBase(invoice.chequeAmount) else invoice.chequeAmountInBase

        val immediatePaidInBase = cashInBase + posInBase + chequeInBase
        if (creditInBase == 0L && immediatePaidInBase < totalInBase) {
            creditInBase = totalInBase - immediatePaidInBase
        }

        val paymentSum = cashInBase + posInBase + chequeInBase + creditInBase
        val roundingDiff = totalInBase - paymentSum
        if (roundingDiff != 0L) {
            when {
                creditInBase > 0 -> creditInBase += roundingDiff
                posInBase > 0 -> posInBase += roundingDiff
                cashInBase > 0 -> cashInBase += roundingDiff
                chequeInBase > 0 -> chequeInBase += roundingDiff
                else -> creditInBase += roundingDiff
            }
        }

        try {
            if (invoice.partyId != null && creditInBase > 0) {
                val factor = when (invoice.invoiceType) {
                    "SALE", "POS_SALE" -> -1L
                    "PURCHASE" -> 1L
                    "RETURN_SALE" -> 1L
                    "RETURN_PURCHASE" -> -1L
                    else -> -1L
                }
                partyDao.updatePartyBalance(invoice.partyId, factor * creditInBase)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error reversing party balance", e)
        }

        // 3. معکوس کردن خزانه‌داری به تفکیک ارز
        try {
            val treasuryFactor = when (invoice.invoiceType) {
                "SALE", "POS_SALE", "RETURN_PURCHASE" -> -1L
                "PURCHASE", "RETURN_SALE" -> 1L
                else -> -1L
            }
            if (invoice.cashAmount > 0) {
                treasuryDao.updateCashBalance(invoice.cashDeskId, treasuryFactor * cashInBase)
                val currBal = treasuryDao.getCurrencyBalance("CASH", invoice.cashDeskId, invoice.currencyCode)
                val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.cashAmount)
                treasuryDao.insertOrUpdateCurrencyBalance(
                    com.example.data.model.TreasuryCurrencyBalanceEntity(
                        id = currBal?.id ?: 0L,
                        accountType = "CASH",
                        accountId = invoice.cashDeskId,
                        currencyCode = invoice.currencyCode,
                        balance = newBal
                    )
                )
            }
            if (invoice.posAmount > 0) {
                treasuryDao.updateBankBalance(invoice.bankAccountId, treasuryFactor * posInBase)
                val currBal = treasuryDao.getCurrencyBalance("BANK", invoice.bankAccountId, invoice.currencyCode)
                val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.posAmount)
                treasuryDao.insertOrUpdateCurrencyBalance(
                    com.example.data.model.TreasuryCurrencyBalanceEntity(
                        id = currBal?.id ?: 0L,
                        accountType = "BANK",
                        accountId = invoice.bankAccountId,
                        currencyCode = invoice.currencyCode,
                        balance = newBal
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error reversing treasury balance", e)
        }

        // 4. حذف چک‌های صیادی مربوط به این فاکتور
        try {
            treasuryDao.deleteChequesByInvoiceNumber(invoice.invoiceNumber)
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error deleting cheques in rollback", e)
        }

        // 5. ثبت سند معکوس در حسابداری
        try {
            val totalInBase = if (invoice.totalAmountInBase > 0) invoice.totalAmountInBase else toBase(invoice.totalAmount)
            val chequeInBase = if (invoice.chequeAmountInBase > 0) invoice.chequeAmountInBase else toBase(invoice.chequeAmount)
            val rollbackItems = mutableListOf<JournalItemEntity>()

            when (invoice.invoiceType) {
                "SALE", "POS_SALE" -> {
                    rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 601, subsidiaryCode = 60101, partyId = invoice.partyId,
                            description = "ابطال درآمد فروش - فاکتور ${invoice.invoiceNumber}", debit = totalInBase, credit = 0L
                        )
                    )
                    if (cashInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "برگشت نقد فاکتور باطل شده ${invoice.invoiceNumber}", debit = 0L, credit = cashInBase
                        )
                    )
                    if (posInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "برگشت واریز پوز فاکتور باطل شده ${invoice.invoiceNumber}", debit = 0L, credit = posInBase
                        )
                    )
                    if (creditInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10201, partyId = invoice.partyId,
                            description = "ابطال بدهکاری مشتری - فاکتور باطل شده ${invoice.invoiceNumber}", debit = 0L, credit = creditInBase
                        )
                    )
                    if (chequeInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10202, partyId = invoice.partyId,
                            description = "برگشت چک دریافتی فاکتور باطل شده ${invoice.invoiceNumber}", debit = 0L, credit = chequeInBase
                        )
                    )

                    // ابطال بهای تمام شده و برگشت موجودی انبار برای فاکتور فروش
                    var cogsRollbackInBase = 0L
                    for (item in items) {
                        val prod = if (item.productId > 0) inventoryDao.getProductById(item.productId) else null
                        val costPrice = prod?.buyPrice ?: 0L
                        if (costPrice > 0L) {
                            val itemCost = (item.quantityInSecondary * costPrice).toLong()
                            cogsRollbackInBase += toBase(itemCost)
                        }
                    }
                    if (cogsRollbackInBase > 0L) {
                        rollbackItems.add(
                            JournalItemEntity(
                                voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = null,
                                description = "برگشت موجودی کالا بابت ابطال فاکتور فروش ${invoice.invoiceNumber}", debit = cogsRollbackInBase, credit = 0L
                            )
                        )
                        rollbackItems.add(
                            JournalItemEntity(
                                voucherId = 0L, generalCode = 701, subsidiaryCode = 70101, partyId = null,
                                description = "ابطال بهای تمام شده کالای فروش رفته - فاکتور ${invoice.invoiceNumber}", debit = 0L, credit = cogsRollbackInBase
                            )
                        )
                    }
                }
                "PURCHASE" -> {
                    if (cashInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "برگشت وجه نقد پرداختی خرید باطل شده ${invoice.invoiceNumber}", debit = cashInBase, credit = 0L
                        )
                    )
                    if (posInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "برگشت پرداخت بانکی خرید باطل شده ${invoice.invoiceNumber}", debit = posInBase, credit = 0L
                        )
                    )
                    if (creditInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30101, partyId = invoice.partyId,
                            description = "ابطال بدهی به تأمین‌کننده بابت خرید باطل شده ${invoice.invoiceNumber}", debit = creditInBase, credit = 0L
                        )
                    )
                    if (chequeInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30102, partyId = invoice.partyId,
                            description = "ابطال چک صادره خرید باطل شده ${invoice.invoiceNumber}", debit = chequeInBase, credit = 0L
                        )
                    )
                    rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = invoice.partyId,
                            description = "ابطال ورود کالا/خرید - فاکتور ${invoice.invoiceNumber}", debit = 0L, credit = totalInBase
                        )
                    )
                }
                "RETURN_SALE" -> {
                    if (cashInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "برگشت استرداد نقد مرجوعی فروش ${invoice.invoiceNumber}", debit = cashInBase, credit = 0L
                        )
                    )
                    if (posInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "برگشت استرداد پوز مرجوعی فروش ${invoice.invoiceNumber}", debit = posInBase, credit = 0L
                        )
                    )
                    if (creditInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10201, partyId = invoice.partyId,
                            description = "برگشت کاهش حساب مرجوعی فروش ${invoice.invoiceNumber}", debit = creditInBase, credit = 0L
                        )
                    )
                    if (chequeInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 102, subsidiaryCode = 10202, partyId = invoice.partyId,
                            description = "برگشت چک استردادی مرجوعی فروش ${invoice.invoiceNumber}", debit = chequeInBase, credit = 0L
                        )
                    )
                    rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 601, subsidiaryCode = 60102, partyId = invoice.partyId,
                            description = "ابطال مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = totalInBase
                        )
                    )

                    // ابطال کاهش بهای تمام شده مرجوعی
                    var returnCogsRollbackInBase = 0L
                    for (item in items) {
                        val prod = if (item.productId > 0) inventoryDao.getProductById(item.productId) else null
                        val costPrice = prod?.buyPrice ?: 0L
                        if (costPrice > 0L) {
                            val itemCost = (item.quantityInSecondary * costPrice).toLong()
                            returnCogsRollbackInBase += toBase(itemCost)
                        }
                    }
                    if (returnCogsRollbackInBase > 0L) {
                        rollbackItems.add(
                            JournalItemEntity(
                                voucherId = 0L, generalCode = 701, subsidiaryCode = 70101, partyId = null,
                                description = "برگشت بهای تمام شده بابت ابطال مرجوعی فروش ${invoice.invoiceNumber}", debit = returnCogsRollbackInBase, credit = 0L
                            )
                        )
                        rollbackItems.add(
                            JournalItemEntity(
                                voucherId = 0L, generalCode = 103, subsidiaryCode = 10301, partyId = null,
                                description = "خروج از موجودی کالا بابت ابطال مرجوعی فروش ${invoice.invoiceNumber}", debit = 0L, credit = returnCogsRollbackInBase
                            )
                        )
                    }
                }
                "RETURN_PURCHASE" -> {
                    rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 103, subsidiaryCode = 10302, partyId = invoice.partyId,
                            description = "ابطال مرجوعی خرید ${invoice.invoiceNumber}", debit = totalInBase, credit = 0L
                        )
                    )
                    if (cashInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10101, partyId = null,
                            description = "برگشت دریافت نقدی مرجوعی خرید ${invoice.invoiceNumber}", debit = 0L, credit = cashInBase
                        )
                    )
                    if (posInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 101, subsidiaryCode = 10102, partyId = null,
                            description = "برگشت دریافت بانکی مرجوعی خرید ${invoice.invoiceNumber}", debit = 0L, credit = posInBase
                        )
                    )
                    if (creditInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30101, partyId = invoice.partyId,
                            description = "برگشت کاهش بدهی مرجوعی خرید ${invoice.invoiceNumber}", debit = 0L, credit = creditInBase
                        )
                    )
                    if (chequeInBase > 0) rollbackItems.add(
                        JournalItemEntity(
                            voucherId = 0L, generalCode = 301, subsidiaryCode = 30102, partyId = invoice.partyId,
                            description = "برگشت چک مرجوعی خرید ${invoice.invoiceNumber}", debit = 0L, credit = chequeInBase
                        )
                    )
                }
            }

            val totalDebit = rollbackItems.sumOf { it.debit }
            val totalCredit = rollbackItems.sumOf { it.credit }
            if (totalDebit == totalCredit && rollbackItems.isNotEmpty()) {
                val nextVoucher = accountingDao.getMaxVoucherNumber() + 1
                accountingDao.createVoucherWithItems(
                    JournalEntryEntity(
                        voucherNumber = nextVoucher,
                        jalaliDate = JalaliDate.now().toStandardString(),
                        description = "سند ابطال/برگشت فاکتور شماره ${invoice.invoiceNumber} (${invoice.invoiceType})",
                        isBalanced = true,
                        status = "FINAL"
                    ),
                    rollbackItems
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error creating rollback accounting voucher", e)
        }
    }

    /**
     * ویرایش کامل فاکتور موجود: برگشت اثرات قبلی و اعمال تغییرات جدید در یک تراکنش واحد اتمیک
     */
    suspend fun updateExistingInvoice(
        invoiceId: Long,
        updatedInvoice: InvoiceEntity,
        newItems: List<InvoiceItemEntity>,
        cheque: com.example.data.model.ChequeEntity? = null
    ): Boolean = runInTransaction {
        try {
            val oldInvoiceWithItems = invoiceDao.getInvoiceWithItemsById(invoiceId) ?: return@runInTransaction false
            val oldInvoice = oldInvoiceWithItems.invoice

            // اگر فاکتور قبلاً قطعی بود، اثرات گذشته را برگشت می‌زنیم
            if (oldInvoice.status == "FINAL") {
                rollbackInvoiceEffects(oldInvoice, oldInvoiceWithItems.items)
            }

            // حذف قطعی چک‌های قبلی این فاکتور جهت جلوگیری از تکرار چک
            try {
                treasuryDao.deleteChequesByInvoiceNumber(oldInvoice.invoiceNumber)
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error clearing existing cheques on update", e)
            }

            val validWarehouseId = resolveAndEnsureWarehouse(updatedInvoice.warehouseId)
            val resolvedItems = resolveAndEnsureProducts(newItems, updatedInvoice.invoiceType)

            // ثبت اطلاعات جدید فاکتور
            val readyInvoice = updatedInvoice.copy(id = invoiceId, warehouseId = validWarehouseId)
            invoiceDao.updateInvoice(readyInvoice)
            invoiceDao.deleteInvoiceItemsForInvoice(invoiceId)
            val attachedItems = resolvedItems.map { it.copy(id = 0L, invoiceId = invoiceId) }
            invoiceDao.insertInvoiceItems(attachedItems)

            // اعمال مجدد اثرات فاکتور در صورتی که قطعی باشد
            if (readyInvoice.status == "FINAL") {
                applyInvoiceEffects(readyInvoice, attachedItems, cheque, validWarehouseId)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error updating existing invoice", e)
            throw e // Throw so transaction rolls back completely on any failure
        }
    }

    /**
     * تبدیل سریع پیش‌فاکتور به فاکتور فروش قطعی در یک تراکنش اتمیک
     */
    suspend fun convertProformaToFinal(invoiceId: Long): Boolean = runInTransaction {
        try {
            val invoiceWithItems = invoiceDao.getInvoiceWithItemsById(invoiceId) ?: return@runInTransaction false
            val invoice = invoiceWithItems.invoice
            val validWarehouseId = resolveAndEnsureWarehouse(invoice.warehouseId)
            val resolvedItems = resolveAndEnsureProducts(invoiceWithItems.items, invoice.invoiceType)
            val updatedInvoice = invoice.copy(status = "FINAL", warehouseId = validWarehouseId)
            invoiceDao.updateInvoice(updatedInvoice)
            applyInvoiceEffects(updatedInvoice, resolvedItems, null, validWarehouseId)
            true
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error converting proforma to final", e)
            throw e
        }
    }

    /**
     * ابطال تمامی اثرات فاکتور و علامت‌گذاری به عنوان باطل شده (VOID) در یک تراکنش اتمیک
     */
    suspend fun voidInvoice(invoiceId: Long): Boolean = runInTransaction {
        try {
            val invoiceWithItems = invoiceDao.getInvoiceWithItemsById(invoiceId) ?: return@runInTransaction false
            val invoice = invoiceWithItems.invoice
            if (invoice.status == "VOID") return@runInTransaction false

            if (invoice.status == "FINAL") {
                rollbackInvoiceEffects(invoice, invoiceWithItems.items)
            }

            invoiceDao.setInvoiceStatus(invoiceId, "VOID")
            true
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Error voiding invoice", e)
            throw e
        }
    }

    suspend fun deleteInvoice(invoiceId: Long): Boolean = runInTransaction {
        try {
            val invoiceWithItems = invoiceDao.getInvoiceWithItemsById(invoiceId) ?: return@runInTransaction false
            val invoice = invoiceWithItems.invoice
            val items = invoiceWithItems.items

            // ۱. اگر فاکتور قطعی است، موجودی انبار و مانده‌های خزانه‌داری را معکوس می‌کنیم
            if (invoice.status == "FINAL") {
                // الف) معکوس کردن موجودی کالا در انبار
                for (item in items) {
                    val reverseStock = when (invoice.invoiceType) {
                        "SALE", "POS_SALE" -> item.quantityInSecondary // بازگشت به موجودی
                        "PURCHASE" -> -item.quantityInSecondary // کسر از موجودی
                        "RETURN_SALE" -> -item.quantityInSecondary // کسر از موجودی
                        "RETURN_PURCHASE" -> item.quantityInSecondary // بازگشت به موجودی
                        else -> item.quantityInSecondary
                    }
                    if (item.productId > 0) {
                        try {
                            inventoryDao.adjustStock(item.productId, reverseStock)
                        } catch (e: Exception) {
                            android.util.Log.e("InvoiceRepository", "Error adjusting stock on delete invoice: ${item.productId}", e)
                        }
                    }
                }

                // ب) محاسبه مبالغ پایه با نرخ تسعیر
                val rate = if (invoice.currencyRateToBase > 0) invoice.currencyRateToBase else 1.0
                fun toBase(amount: Long): Long {
                    if (rate == 1.0 || amount == 0L) return amount
                    return java.math.BigDecimal(amount)
                        .multiply(java.math.BigDecimal.valueOf(rate))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .toLong()
                }

                val cashInBase = if (rate == 1.0 || invoice.cashAmountInBase <= 0L) toBase(invoice.cashAmount) else invoice.cashAmountInBase
                val posInBase = if (rate == 1.0 || invoice.posAmountInBase <= 0L) toBase(invoice.posAmount) else invoice.posAmountInBase

                val treasuryFactor = when (invoice.invoiceType) {
                    "SALE", "POS_SALE", "RETURN_PURCHASE" -> -1L
                    "PURCHASE", "RETURN_SALE" -> 1L
                    else -> -1L
                }

                // ج) معکوس کردن خزانه‌داری (صندوق و پوز)
                if (invoice.cashAmount > 0) {
                    try {
                        treasuryDao.updateCashBalance(invoice.cashDeskId, treasuryFactor * cashInBase)
                        val currBal = treasuryDao.getCurrencyBalance("CASH", invoice.cashDeskId, invoice.currencyCode)
                        val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.cashAmount)
                        treasuryDao.insertOrUpdateCurrencyBalance(
                            com.example.data.model.TreasuryCurrencyBalanceEntity(
                                id = currBal?.id ?: 0L,
                                accountType = "CASH",
                                accountId = invoice.cashDeskId,
                                currencyCode = invoice.currencyCode,
                                balance = newBal
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("InvoiceRepository", "Error reversing cash balance on delete", e)
                    }
                }

                if (invoice.posAmount > 0) {
                    try {
                        treasuryDao.updateBankBalance(invoice.bankAccountId, treasuryFactor * posInBase)
                        val currBal = treasuryDao.getCurrencyBalance("BANK", invoice.bankAccountId, invoice.currencyCode)
                        val newBal = (currBal?.balance ?: 0.0) + (treasuryFactor * invoice.posAmount)
                        treasuryDao.insertOrUpdateCurrencyBalance(
                            com.example.data.model.TreasuryCurrencyBalanceEntity(
                                id = currBal?.id ?: 0L,
                                accountType = "BANK",
                                accountId = invoice.bankAccountId,
                                currencyCode = invoice.currencyCode,
                                balance = newBal
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("InvoiceRepository", "Error reversing bank balance on delete", e)
                    }
                }
            }

            // ۲. پاکسازی قطعی تمام گردش‌های کاردکس انبار مربوط به این فاکتور (کاردکس بدون رکورد یتیم)
            try {
                inventoryDao.deleteMovementsByReference(invoice.invoiceNumber)
                inventoryDao.deleteMovementsByReference("REV-${invoice.invoiceNumber}")
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error cleaning kardex movements for deleted invoice ${invoice.invoiceNumber}", e)
            }

            // ۳. پاکسازی چک‌های صیادی وابسته به فاکتور
            try {
                treasuryDao.deleteChequesByInvoiceNumber(invoice.invoiceNumber)
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error cleaning cheques for deleted invoice ${invoice.invoiceNumber}", e)
            }

            // ۴. پاکسازی کامل اسناد حسابداری دوبل و کلیه آرتیکل‌های وابسته (بدون رکورد یتیم در دفاتر روزنامه و کل)
            try {
                accountingDao.deleteJournalItemsByDescriptionPattern(invoice.invoiceNumber)
                accountingDao.deleteJournalEntriesByDescriptionPattern(invoice.invoiceNumber)
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error removing journal vouchers for deleted invoice ${invoice.invoiceNumber}", e)
            }

            // ۵. حذف ردیف‌های اقلام فاکتور
            try {
                invoiceDao.deleteInvoiceItemsForInvoice(invoiceId)
            } catch (e: Exception) {
                android.util.Log.e("InvoiceRepository", "Error deleting invoice items", e)
            }

            // ۶. حذف قطعی سربرگ فاکتور
            val rowsDeleted = try {
                invoiceDao.deleteInvoiceById(invoiceId)
            } catch (e: Exception) {
                invoiceDao.deleteInvoice(invoice)
                1
            }

            // ۷. محاسبه مجدد و قطعی مانده حساب طرف‌حساب از روی دفاتر حسابداری فعال (Zero Drift)
            if (invoice.partyId != null) {
                try {
                    partyDao.recalculatePartyBalance(invoice.partyId)
                } catch (e: Exception) {
                    android.util.Log.e("InvoiceRepository", "Error recalculating party balance after delete invoice", e)
                }
            }

            rowsDeleted > 0
        } catch (e: Exception) {
            android.util.Log.e("InvoiceRepository", "Fatal error in deleteInvoice", e)
            throw e
        }
    }

    suspend fun generateNextInvoiceNumber(invoiceType: String = "SALE"): String {
        val prefix = when (invoiceType) {
            "PURCHASE" -> "خرید"
            "RETURN_SALE" -> "مرجوعی-فروش"
            "RETURN_PURCHASE" -> "مرجوعی-خرید"
            else -> "فاکتور"
        }
        val maxId = invoiceDao.getMaxInvoiceId()
        var candidateNum = "$prefix-${1000 + maxId + 1}"
        var counter = 1
        while (invoiceDao.getInvoiceByNumber(candidateNum) != null) {
            candidateNum = "$prefix-${1000 + maxId + 1 + counter}"
            counter++
        }
        return candidateNum
    }
}
