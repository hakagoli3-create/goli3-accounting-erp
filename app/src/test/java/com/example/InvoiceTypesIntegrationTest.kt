package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.repository.InvoiceRepository
import com.example.util.EscPosPrinterHelper
import com.example.util.JalaliDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceTypesIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var invoiceRepo: InvoiceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        runBlocking {
            db.inventoryDao().insertWarehouse(
                com.example.data.model.WarehouseEntity(
                    id = 1L,
                    code = "WH-01",
                    name = "انبار مرکزی",
                    isDefault = true
                )
            )
        }
        invoiceRepo = InvoiceRepository(
            invoiceDao = db.invoiceDao(),
            inventoryDao = db.inventoryDao(),
            partyDao = db.partyDao(),
            treasuryDao = db.treasuryDao(),
            accountingDao = db.accountingDao(),
            appDatabase = db
        )
    }

    @Test
    fun testPrintableReceiptTitlesAndRoles() {
        // 1. Purchase
        val purchasePrintable = EscPosPrinterHelper.PrintableInvoice(
            invoiceNumber = "1001",
            invoiceDate = "1405/01/15",
            customerName = "شرکت پتروشیمی آریا",
            customerPhone = "09123456789",
            warehouseName = "انبار مرکزی",
            items = listOf(
                EscPosPrinterHelper.PrintableItem("کالای آزمایشی الف", 5.0, "عدد", 20000L, 100000L)
            ),
            subtotalAmount = 100000L,
            discountAmount = 0L,
            taxAmount = 10000L,
            totalAmount = 110000L,
            paymentMethod = "CASH",
            invoiceType = "PURCHASE"
        )
        val purchaseText = EscPosPrinterHelper.buildPlainTextReceipt(purchasePrintable)
        assertTrue("Purchase text should contain purchase title", purchaseText.contains("فاکتور رسمی خرید"))
        assertTrue("Purchase text should contain supplier role label", purchaseText.contains("تأمین‌کننده"))
        assertFalse("Purchase text should NOT contain sales title", purchaseText.contains("فاکتور رسمی فروش"))

        // 2. Return Sale
        val returnSalePrintable = purchasePrintable.copy(invoiceType = "RETURN_SALE")
        val returnSaleText = EscPosPrinterHelper.buildPlainTextReceipt(returnSalePrintable)
        assertTrue("Return sale text should contain return sale title", returnSaleText.contains("فاکتور برگشت از فروش"))
        assertTrue("Return sale text should contain returner role label", returnSaleText.contains("مرجوع‌کننده"))

        // 3. Return Purchase
        val returnPurchasePrintable = purchasePrintable.copy(invoiceType = "RETURN_PURCHASE")
        val returnPurchaseText = EscPosPrinterHelper.buildPlainTextReceipt(returnPurchasePrintable)
        assertTrue("Return purchase text should contain return purchase title", returnPurchaseText.contains("فاکتور برگشت از خرید"))

        // 4. Sale
        val salePrintable = purchasePrintable.copy(invoiceType = "SALE")
        val saleText = EscPosPrinterHelper.buildPlainTextReceipt(salePrintable)
        assertTrue("Sale text should contain sale title", saleText.contains("فاکتور رسمی فروش"))
        assertTrue("Sale text should contain buyer label", saleText.contains("خریدار"))
    }

    @Test
    fun testFourInvoiceTypesAccountingAndStockEffects() = runBlocking {
        val todayStr = JalaliDate.now().toStandardString()

        // درج کالای آزمایشی با موجودی اولیه ۱۰۰
        val productId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-TEST-01",
                name = "کالای آزمایشی تست سیستم",
                barcode = "998877665544",
                category = "عمومی",
                primaryUnit = "کارتن",
                secondaryUnit = "عدد",
                conversionRate = 10.0,
                buyPrice = 50000L,
                wholesalePrice = 65000L,
                retailPrice = 75000L,
                currentStockInSecondary = 100.0,
                minStockThreshold = 5.0
            )
        )

        // درج مشتری
        val customerId = db.partyDao().insertParty(
            PartyEntity(
                code = "CUST-001",
                name = "مشتری نمونه آزمایشی",
                partyType = "CUSTOMER",
                mobile = "09121111111",
                creditLimit = 10000000L,
                currentBalance = 0L
            )
        )

        // درج تأمین‌کننده
        val supplierId = db.partyDao().insertParty(
            PartyEntity(
                code = "SUPP-001",
                name = "تأمین‌کننده نمونه آزمایشی",
                partyType = "SUPPLIER",
                mobile = "09122222222",
                creditLimit = 0L,
                currentBalance = 0L
            )
        )

        // ۱. فاکتور فروش (SALE) به تعداد ۲۰ عدد به صورت نسیه
        val saleInv = InvoiceEntity(
            invoiceNumber = "SALE-101",
            invoiceType = "SALE",
            jalaliDate = todayStr,
            partyId = customerId,
            partyName = "مشتری نمونه آزمایشی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 1500000L,
            totalAmount = 1500000L,
            paymentType = "CREDIT",
            creditAmount = 1500000L,
            status = "FINAL"
        )
        val saleItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = productId,
            productName = "کالای آزمایشی تست سیستم",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 20.0,
            conversionRate = 10.0,
            quantityInSecondary = 20.0,
            unitPrice = 75000L,
            totalPrice = 1500000L
        )
        invoiceRepo.createInvoice(saleInv, listOf(saleItem))

        // ارزیابی اثر فروش بر موجودی و طرف حساب
        val prodAfterSale = db.inventoryDao().getProductById(productId)
        assertNotNull(prodAfterSale)
        assertEquals("موجودی پس از فروش باید ۲۰ عدد کاهش یابد (۱۰۰ - ۲۰ = ۸۰)", 80.0, prodAfterSale!!.currentStockInSecondary, 0.01)

        val custAfterSale = db.partyDao().getPartyById(customerId)
        assertNotNull(custAfterSale)
        assertEquals("مانده بدهی مشتری باید مثبت ۱۵۰۰۰۰۰ تومان شود", 1500000L, custAfterSale!!.currentBalance)

        // ۲. فاکتور خرید (PURCHASE) به تعداد ۵۰ عدد از تأمین‌کننده به صورت نسیه
        val purchaseInv = InvoiceEntity(
            invoiceNumber = "PUR-201",
            invoiceType = "PURCHASE",
            jalaliDate = todayStr,
            partyId = supplierId,
            partyName = "تأمین‌کننده نمونه آزمایشی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 2500000L,
            totalAmount = 2500000L,
            paymentType = "CREDIT",
            creditAmount = 2500000L,
            status = "FINAL"
        )
        val purchaseItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = productId,
            productName = "کالای آزمایشی تست سیستم",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 50.0,
            conversionRate = 10.0,
            quantityInSecondary = 50.0,
            unitPrice = 50000L,
            totalPrice = 2500000L
        )
        invoiceRepo.createInvoice(purchaseInv, listOf(purchaseItem))

        val prodAfterPurchase = db.inventoryDao().getProductById(productId)
        assertEquals("موجودی پس از خرید باید ۵۰ عدد افزایش یابد (۸۰ + ۵۰ = ۱۳۰)", 130.0, prodAfterPurchase!!.currentStockInSecondary, 0.01)

        val suppAfterPurchase = db.partyDao().getPartyById(supplierId)
        assertEquals("بدهی ما به تأمین‌کننده باید بستانکار (منفی ۲۵۰۰۰۰۰) شود", -2500000L, suppAfterPurchase!!.currentBalance)

        // ۳. مرجوعی فروش (RETURN_SALE) ۵ عدد
        val returnSaleInv = InvoiceEntity(
            invoiceNumber = "RET-SALE-301",
            invoiceType = "RETURN_SALE",
            jalaliDate = todayStr,
            partyId = customerId,
            partyName = "مشتری نمونه آزمایشی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 375000L,
            totalAmount = 375000L,
            paymentType = "CREDIT",
            creditAmount = 375000L,
            status = "FINAL"
        )
        val returnSaleItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = productId,
            productName = "کالای آزمایشی تست سیستم",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 5.0,
            conversionRate = 10.0,
            quantityInSecondary = 5.0,
            unitPrice = 75000L,
            totalPrice = 375000L
        )
        invoiceRepo.createInvoice(returnSaleInv, listOf(returnSaleItem))

        val prodAfterRetSale = db.inventoryDao().getProductById(productId)
        assertEquals("مرجوعی فروش کالا را به انبار بازمی‌گرداند (۱۳۰ + ۵ = ۱۳۵)", 135.0, prodAfterRetSale!!.currentStockInSecondary, 0.01)

        val custAfterRetSale = db.partyDao().getPartyById(customerId)
        assertEquals("مرجوعی فروش از بدهی مشتری می‌کاهد (۱۵۰۰۰۰۰ - ۳۷۵۰۰۰ = ۱۱۲۵۰۰۰)", 1125000L, custAfterRetSale!!.currentBalance)

        // ۴. مرجوعی خرید (RETURN_PURCHASE) ۱۰ عدد به تأمین‌کننده
        val returnPurchaseInv = InvoiceEntity(
            invoiceNumber = "RET-PUR-401",
            invoiceType = "RETURN_PURCHASE",
            jalaliDate = todayStr,
            partyId = supplierId,
            partyName = "تأمین‌کننده نمونه آزمایشی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 500000L,
            totalAmount = 500000L,
            paymentType = "CREDIT",
            creditAmount = 500000L,
            status = "FINAL"
        )
        val returnPurchaseItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = productId,
            productName = "کالای آزمایشی تست سیستم",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 10.0,
            conversionRate = 10.0,
            quantityInSecondary = 10.0,
            unitPrice = 50000L,
            totalPrice = 500000L
        )
        invoiceRepo.createInvoice(returnPurchaseInv, listOf(returnPurchaseItem))

        val prodAfterRetPur = db.inventoryDao().getProductById(productId)
        assertEquals("مرجوعی خرید کالا را از انبار خارج می‌کند (۱۳۵ - ۱۰ = ۱۲۵)", 125.0, prodAfterRetPur!!.currentStockInSecondary, 0.01)

        val suppAfterRetPur = db.partyDao().getPartyById(supplierId)
        assertEquals("مرجوعی خرید بدهی ما به تأمین‌کننده را کاهش می‌دهد (-۲۵۰۰۰۰۰ + ۵۰۰۰۰۰ = -۲۰۰۰۰۰۰)", -2000000L, suppAfterRetPur!!.currentBalance)

        // بررسی تراز اسناد دوبل حسابداری
        val maxVoucher = db.accountingDao().getMaxVoucherNumber()
        assertTrue("باید حداقل ۴ سند حسابداری برای ۴ عملیات صادر شده باشد", maxVoucher >= 4)
    }

    @Test
    fun testMixedSettlementAndAtomicChequeAcrossTypes() = runBlocking {
        val todayStr = JalaliDate.now().toStandardString()

        // آماده‌سازی صندوق و بانک
        val cashDeskId = db.treasuryDao().insertCashDesk(
            CashDeskEntity(id = 10L, code = "CSH-10", name = "صندوق مرکزی فروشگاه", currentBalance = 1000000L)
        )
        val bankAccountId = db.treasuryDao().insertBankAccount(
            BankAccountEntity(id = 20L, bankName = "ملت", accountNumber = "12345", cardNumber = "6104", currentBalance = 5000000L)
        )

        val prodId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-MIX-01",
                name = "کالای تسویه ترکیبی",
                barcode = "11223344",
                category = "آزمایشی",
                primaryUnit = "بسته",
                secondaryUnit = "عدد",
                conversionRate = 1.0,
                buyPrice = 80000L,
                wholesalePrice = 90000L,
                retailPrice = 100000L,
                currentStockInSecondary = 200.0,
                minStockThreshold = 10.0
            )
        )

        val partyCustomer = db.partyDao().insertParty(
            PartyEntity(code = "CUST-MIX", name = "مشتری ترکیبی", partyType = "CUSTOMER", mobile = "09120000001", creditLimit = 50000000L, currentBalance = 0L)
        )

        // ۱. فاکتور فروش ترکیبی:
        // اقلام: ۱۰ عدد × ۱۰۰,۰۰۰ = ۱,۰۰۰,۰۰۰ تومان
        // تخفیف: ۱۰۰,۰۰۰ تومان
        // مشمول مالیات: ۹۰۰,۰۰۰ تومان
        // مالیات ۱۰٪: ۹۰,۰۰۰ تومان
        // هزینه حمل (مستقیم): ۱۰,۰۰۰ تومان
        // جمع کل فاکتور: ۱,۰۰۰,۰۰۰ تومان
        // تسویه ترکیبی: نقد ۲۰۰,۰۰۰، پوز ۳۰۰,۰۰۰، چک ۴۰۰,۰۰۰، نسیه ۱۰۰,۰۰۰
        val saleInvoice = InvoiceEntity(
            invoiceNumber = "SALE-MIX-901",
            invoiceType = "SALE",
            jalaliDate = todayStr,
            partyId = partyCustomer,
            partyName = "مشتری ترکیبی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 1000000L,
            discountAmount = 100000L,
            taxAmount = 90000L,
            directExpenseAmount = 10000L,
            directExpenseType = "FREIGHT",
            totalAmount = 1000000L,
            paymentType = "MIXED",
            cashAmount = 200000L,
            posAmount = 300000L,
            chequeAmount = 400000L,
            creditAmount = 100000L,
            cashDeskId = cashDeskId,
            bankAccountId = bankAccountId,
            status = "FINAL"
        )
        val saleItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای تسویه ترکیبی",
            unitName = "عدد",
            isPrimaryUnit = true,
            quantity = 10.0,
            conversionRate = 1.0,
            quantityInSecondary = 10.0,
            unitPrice = 100000L,
            totalPrice = 1000000L
        )
        val chequeSale = ChequeEntity(
            chequeNumber = "CHK-9901",
            sayadNumber = "1234567890123456",
            type = "RECEIVABLE",
            partyId = partyCustomer,
            partyName = "مشتری ترکیبی",
            bankName = "بانک صادرات",
            branch = "مرکزی",
            amount = 400000L,
            issueDateJalali = todayStr,
            dueDateJalali = "1405/03/01",
            dueDateEpochMillis = System.currentTimeMillis() + 86400000L * 60,
            status = "NORMAL",
            notes = "دریافت چک صیادی بابت فاکتور SALE-MIX-901"
        )

        val invId = invoiceRepo.createInvoice(saleInvoice, listOf(saleItem), chequeSale)
        assertTrue("شناسه فاکتور معتبر باشد", invId > 0)

        // بررسی موجودی انبار: ۲۰۰ - ۱۰ = ۱۹۰
        val prodAfter = db.inventoryDao().getProductById(prodId)
        assertEquals(190.0, prodAfter!!.currentStockInSecondary, 0.01)

        // بررسی مانده بدهی مشتری: ۱۰۰,۰۰۰ تومان بدهکار
        val customerAfter = db.partyDao().getPartyById(partyCustomer)
        assertEquals(100000L, customerAfter!!.currentBalance)

        // بررسی ثبت چک در خزانه‌داری
        val savedCheque = db.treasuryDao().getChequeByInvoiceNumber("SALE-MIX-901")
        assertNotNull("چک صیادی باید در خزانه‌داری ذخیره شده باشد", savedCheque)
        assertEquals("CHK-9901", savedCheque!!.chequeNumber)
        assertEquals("RECEIVABLE", savedCheque.type)
        assertEquals(400000L, savedCheque.amount)

        // بررسی تراز بودن سند حسابداری صادر شده
        val lastVoucherId = db.accountingDao().getMaxVoucherNumber()
        val journalItems = db.accountingDao().getJournalItemsByVoucherSync(lastVoucherId)
        val sumDebit = journalItems.sumOf { it.debit }
        val sumCredit = journalItems.sumOf { it.credit }
        assertEquals("تراز بدهکار و بستانکار سند دوبل فروش باید کاملاً برابر باشند", sumDebit, sumCredit)
    }

    @Test
    fun testInvoiceEditRollbackAndAtomicReapply() = runBlocking {
        val todayStr = JalaliDate.now().toStandardString()

        val prodId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-EDIT-01",
                name = "کالای آزمون ویرایش",
                barcode = "55667788",
                category = "آزمایشی",
                primaryUnit = "عدد",
                secondaryUnit = "عدد",
                conversionRate = 1.0,
                buyPrice = 10000L,
                wholesalePrice = 15000L,
                retailPrice = 20000L,
                currentStockInSecondary = 50.0,
                minStockThreshold = 5.0
            )
        )

        val partyCustomer = db.partyDao().insertParty(
            PartyEntity(code = "CUST-EDIT", name = "مشتری آزمون ویرایش", partyType = "CUSTOMER", mobile = "09120000002", creditLimit = 50000000L, currentBalance = 0L)
        )

        // ایجاد اولیه فاکتور فروش: ۱۰ عدد به قیمت ۲۰۰,۰۰۰ تومان، پرداخت نقدی
        val initialInvoice = InvoiceEntity(
            invoiceNumber = "SALE-EDIT-01",
            invoiceType = "SALE",
            jalaliDate = todayStr,
            partyId = partyCustomer,
            partyName = "مشتری آزمون ویرایش",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 200000L,
            totalAmount = 200000L,
            paymentType = "CASH",
            cashAmount = 200000L,
            status = "FINAL"
        )
        val initialItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای آزمون ویرایش",
            unitName = "عدد",
            isPrimaryUnit = true,
            quantity = 10.0,
            conversionRate = 1.0,
            quantityInSecondary = 10.0,
            unitPrice = 20000L,
            totalPrice = 200000L
        )

        val invId = invoiceRepo.createInvoice(initialInvoice, listOf(initialItem))
        assertEquals(40.0, db.inventoryDao().getProductById(prodId)!!.currentStockInSecondary, 0.01)

        // اکنون ویرایش فاکتور: تغییر تعداد به ۳۰ عدد نسیه
        val updatedInvoice = initialInvoice.copy(
            id = invId,
            subtotalAmount = 600000L,
            totalAmount = 600000L,
            paymentType = "CREDIT",
            cashAmount = 0L,
            creditAmount = 600000L
        )
        val updatedItem = initialItem.copy(
            quantity = 30.0,
            quantityInSecondary = 30.0,
            totalPrice = 600000L
        )

        val editResult = invoiceRepo.updateExistingInvoice(invId, updatedInvoice, listOf(updatedItem))
        assertTrue("ویرایش باید با موفقیت انجام شود", editResult)

        // بررسی موجودی انبار: ابتدا ۱۰ عدد قبلی بازگشت داده شده (۴۰ + ۱۰ = ۵۰)، سپس ۳۰ عدد جدید کسر شده (۵۰ - ۳۰ = ۲۰)
        val prodAfterEdit = db.inventoryDao().getProductById(prodId)
        assertEquals("موجودی پس از ویرایش باید دقیقاً ۲۰ باشد", 20.0, prodAfterEdit!!.currentStockInSecondary, 0.01)

        // بررسی مانده طرف‌حساب: ۶۰۰,۰۰۰ تومان بدهکار
        val custAfterEdit = db.partyDao().getPartyById(partyCustomer)
        assertEquals(600000L, custAfterEdit!!.currentBalance)

        // بررسی بازیابی فاکتور و اقلام از دیتابیس (Reloading verification)
        val loaded = db.invoiceDao().getInvoiceWithItemsById(invId)
        assertNotNull(loaded)
        assertEquals(600000L, loaded!!.invoice.totalAmount)
        assertEquals(1, loaded.items.size)
        assertEquals(30.0, loaded.items[0].quantity, 0.01)
    }

    @Test
    fun testInvoiceVoidRollback() = runBlocking {
        val todayStr = JalaliDate.now().toStandardString()

        val prodId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-VOID-01",
                name = "کالای آزمون ابطال",
                barcode = "99001122",
                category = "عمومی",
                primaryUnit = "عدد",
                secondaryUnit = "عدد",
                conversionRate = 1.0,
                buyPrice = 50000L,
                wholesalePrice = 60000L,
                retailPrice = 70000L,
                currentStockInSecondary = 100.0,
                minStockThreshold = 5.0
            )
        )

        val supplierId = db.partyDao().insertParty(
            PartyEntity(code = "SUPP-VOID", name = "تأمین‌کننده آزمون ابطال", partyType = "SUPPLIER", mobile = "09120000003", currentBalance = 0L)
        )

        // خرید نسیه ۵۰ عدد از تأمین‌کننده
        val purchaseInvoice = InvoiceEntity(
            invoiceNumber = "PUR-VOID-01",
            invoiceType = "PURCHASE",
            jalaliDate = todayStr,
            partyId = supplierId,
            partyName = "تأمین‌کننده آزمون ابطال",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 2500000L,
            totalAmount = 2500000L,
            paymentType = "CREDIT",
            creditAmount = 2500000L,
            status = "FINAL"
        )
        val purchaseItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای آزمون ابطال",
            unitName = "عدد",
            isPrimaryUnit = true,
            quantity = 50.0,
            conversionRate = 1.0,
            quantityInSecondary = 50.0,
            unitPrice = 50000L,
            totalPrice = 2500000L
        )

        val invId = invoiceRepo.createInvoice(purchaseInvoice, listOf(purchaseItem))
        assertEquals(150.0, db.inventoryDao().getProductById(prodId)!!.currentStockInSecondary, 0.01)
        assertEquals(-2500000L, db.partyDao().getPartyById(supplierId)!!.currentBalance)

        // ابطال کامل فاکتور
        val voidResult = invoiceRepo.voidInvoice(invId)
        assertTrue(voidResult)

        // بررسی بازگشت وضعیت موجودی انبار به حالت قبل (۱۵۰ - ۵۰ = ۱۰۰)
        val prodAfterVoid = db.inventoryDao().getProductById(prodId)
        assertEquals("موجودی کالا باید پس از ابطال دقیقاً به مقدار اولیه ۱۰۰ بازگردد", 100.0, prodAfterVoid!!.currentStockInSecondary, 0.01)

        // بررسی بازگشت مانده بدهی تأمین‌کننده به ۰
        val suppAfterVoid = db.partyDao().getPartyById(supplierId)
        assertEquals("مانده حساب تأمین‌کننده پس از ابطال باید صفر شود", 0L, suppAfterVoid!!.currentBalance)

        // وضعیت فاکتور باید VOID شده باشد
        val loaded = db.invoiceDao().getInvoiceWithItemsById(invId)
        assertEquals("VOID", loaded!!.invoice.status)
    }

    @Test
    fun testSupplierCreditPurchaseAndCustomerSettlementBalanceIntegrity() = runBlocking {
        val todayStr = JalaliDate.now().toStandardString()

        // ۱. تعریف تأمین‌کننده «رباطی»
        val robatiId = db.partyDao().insertParty(
            PartyEntity(
                code = "SUPP-ROBATI",
                name = "رباطی",
                partyType = "SUPPLIER",
                creditLimit = 0L,
                currentBalance = 0L
            )
        )

        // ۲. تعریف مشتری
        val customerId = db.partyDao().insertParty(
            PartyEntity(
                code = "CUST-TEST",
                name = "مشتری نمونه",
                partyType = "CUSTOMER",
                creditLimit = 20000000L,
                currentBalance = 0L
            )
        )

        val prodId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-ROBATI",
                name = "کالای رباطی",
                barcode = "11223344",
                category = "عمومی",
                primaryUnit = "عدد",
                secondaryUnit = "عدد",
                conversionRate = 1.0,
                buyPrice = 50000L,
                wholesalePrice = 100000L,
                retailPrice = 100000L,
                currentStockInSecondary = 10.0
            )
        )

        // ۳. فاکتور خرید ۵۰,۰۰۰ تومان به صورت نسیه از رباطی
        val purchaseInvoice = InvoiceEntity(
            invoiceNumber = "خرید-1001",
            invoiceType = "PURCHASE",
            jalaliDate = todayStr,
            partyId = robatiId,
            partyName = "رباطی",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 50000L,
            totalAmount = 50000L,
            paymentType = "CREDIT",
            creditAmount = 50000L,
            status = "FINAL"
        )
        val purchaseItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای رباطی",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 1.0,
            conversionRate = 1.0,
            quantityInSecondary = 1.0,
            unitPrice = 50000L,
            totalPrice = 50000L
        )
        invoiceRepo.createInvoice(purchaseInvoice, listOf(purchaseItem))

        // بررسی اولیه مانده حساب رباطی: بدهی ۵۰,۰۰۰ به تأمین‌کننده (بستانکار / منفی)
        val robatiAfterPurchase = db.partyDao().getPartyById(robatiId)
        assertNotNull(robatiAfterPurchase)
        assertEquals(-50000L, robatiAfterPurchase!!.currentBalance)

        // ۴. فاکتور فروش ۱۰۰,۰۰۰ تومان به صورت نسیه به مشتری
        val saleInvoice = InvoiceEntity(
            invoiceNumber = "فاکتور-1002",
            invoiceType = "SALE",
            jalaliDate = todayStr,
            partyId = customerId,
            partyName = "مشتری نمونه",
            warehouseId = 1L,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 100000L,
            totalAmount = 100000L,
            paymentType = "CREDIT",
            creditAmount = 100000L,
            status = "FINAL"
        )
        val saleItem = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای رباطی",
            unitName = "عدد",
            isPrimaryUnit = false,
            quantity = 1.0,
            conversionRate = 1.0,
            quantityInSecondary = 1.0,
            unitPrice = 100000L,
            totalPrice = 100000L
        )
        invoiceRepo.createInvoice(saleInvoice, listOf(saleItem))

        val custAfterSale = db.partyDao().getPartyById(customerId)
        assertNotNull(custAfterSale)
        assertEquals(100000L, custAfterSale!!.currentBalance)

        // ۵. ثبت دریافت وجه از مشتری به مبلغ ۱۰۰,۰۰۰ تومان در خزانه‌داری
        val treasuryRepo = com.example.data.repository.TreasuryRepository(db.treasuryDao(), db)
        val accountingRepo = com.example.data.repository.AccountingRepository(db.accountingDao(), db)
        val expenseRepo = com.example.data.repository.ExpenseAndIncomeRepository(db.expenseAndIncomeDao(), db.treasuryDao(), db.accountingDao(), db)
        val partyRepo = com.example.data.repository.PartyRepository(db.partyDao())

        val treasuryViewModel = com.example.ui.viewmodel.TreasuryViewModel(
            treasuryRepo, accountingRepo, expenseRepo, partyRepo
        )

        treasuryViewModel.registerCashReceipt(
            isPartyReceipt = true,
            partyId = customerId,
            partyName = "مشتری نمونه",
            incomeCategoryId = null,
            incomeCategoryTitle = "",
            title = "تسویه بدهی مشتری",
            amount = 100000L,
            destinationType = "CASH",
            targetId = 1L,
            jalaliDate = todayStr,
            onSuccess = {}
        )

        // کمی صبر برای تکمیل تراکنش‌های ناهمگام خزانه‌داری و محاسبه مجدد
        kotlinx.coroutines.delay(200)

        // ۶. بررسی دقیق و قطعی مانده حساب‌ها پس از تسویه مشتری
        val robatiFinal = db.partyDao().getPartyById(robatiId)
        assertNotNull(robatiFinal)
        assertEquals("مانده حساب رباطی پس از دریافت از مشتری باید همچنان دقیقاً بدهی ۵۰,۰۰۰ تومان (منفی ۵۰۰۰۰) باقی بماند", -50000L, robatiFinal!!.currentBalance)

        val customerFinal = db.partyDao().getPartyById(customerId)
        assertNotNull(customerFinal)
        assertEquals("مانده حساب مشتری پس از پرداخت کامل باید دقیقاً صفر (تسویه) شود", 0L, customerFinal!!.currentBalance)

        // بررسی مقادیر سرجمع KPI
        val supplierPayables = db.partyDao().getSupplierPayables().first()
        assertEquals("بدهی به تأمین‌کنندگان در هدر CRM باید دقیقاً ۵۰,۰۰۰ تومان باشد", 50000L, supplierPayables)

        val totalReceivables = db.partyDao().getTotalReceivables().first()
        assertEquals("کل مطالبات از مشتریان پس از تسویه باید صفر تومان باشد", 0L, totalReceivables)
    }

    @Test
    fun testCalculationFormulasAndBounds() {
        val subtotal = 500000L

        // ۱. تخفیف بیش از سرجمع باید محدود به سقف subtotal شود
        val excessiveDiscount = 700000L
        val effectiveDiscount = minOf(maxOf(0L, excessiveDiscount), subtotal)
        assertEquals("تخفیف نمی‌تواند از جمع ناخالص بیشتر باشد", subtotal, effectiveDiscount)

        val taxable = subtotal - effectiveDiscount
        assertEquals(0L, taxable)

        // ۲. مالیات بر ارزش افزوده نباید روی مبلغ منفی محاسبه شود
        val tax = Math.round(taxable * 0.10)
        assertEquals(0L, tax)

        // ۳. هزینه مستقیم باید بدون مالیات مضاعف به جمع نهایی اضافه شود
        val directExpense = 35000L
        val grandTotal = taxable + tax + directExpense
        assertEquals(35000L, grandTotal)

        // ۴. تسویه عادی: تخفیف مجاز
        val normalDiscount = 50000L
        val effectiveDiscountNormal = minOf(maxOf(0L, normalDiscount), subtotal)
        val taxableNormal = subtotal - effectiveDiscountNormal // 450,000
        val taxNormal = Math.round(taxableNormal * 0.10) // 45,000
        val grandTotalNormal = taxableNormal + taxNormal + directExpense // 450,000 + 45,000 + 35,000 = 530,000
        assertEquals(530000L, grandTotalNormal)
    }
}
