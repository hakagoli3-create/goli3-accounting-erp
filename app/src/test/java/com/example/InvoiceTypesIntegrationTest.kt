package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.repository.InvoiceRepository
import com.example.util.EscPosPrinterHelper
import com.example.util.JalaliDate
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
}
