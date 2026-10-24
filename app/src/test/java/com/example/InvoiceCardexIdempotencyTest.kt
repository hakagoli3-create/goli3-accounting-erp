package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.InventoryMovementEntity
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.model.WarehouseEntity
import com.example.data.repository.InvoiceRepository
import com.example.util.JalaliDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceCardexIdempotencyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var invoiceRepo: InvoiceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        invoiceRepo = InvoiceRepository(db)

        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("DELETE FROM inventory_movements")
        sqlite.execSQL("DELETE FROM invoice_items")
        sqlite.execSQL("DELETE FROM invoices")
        sqlite.execSQL("DELETE FROM journal_items")
        sqlite.execSQL("DELETE FROM journal_entries")
        sqlite.execSQL("DELETE FROM products")
        sqlite.execSQL("DELETE FROM warehouses")
        sqlite.execSQL("DELETE FROM parties")
    }

    @Test
    fun testInvoiceCreationProducesSingleMovementAndNoRev() = runBlocking {
        val inventoryDao = db.inventoryDao()
        val partyDao = db.partyDao()

        // 1. ثبت انبار، طرف‌حساب و کالای تستی
        val warehouseId = inventoryDao.insertWarehouse(
            WarehouseEntity(code = "WH-01", name = "انبار مرکزی", isDefault = true)
        )
        val partyId = partyDao.insertParty(
            PartyEntity(code = "CUST-01", name = "مشتری نمونه", partyType = "CUSTOMER")
        )
        val productId = inventoryDao.insertProduct(
            ProductEntity(
                code = "PRD-01",
                barcode = "BAR-01",
                name = "کالای آزمایشی A",
                category = "عمومی",
                currentStockInSecondary = 100.0,
                buyPrice = 10000L,
                retailPrice = 15000L
            )
        )

        // 2. ثبت فاکتور فروش ۵ عدد از کالای A
        val invoiceNumber = "INV-SALE-101"
        val invoice = InvoiceEntity(
            invoiceNumber = invoiceNumber,
            invoiceType = "SALE",
            jalaliDate = JalaliDate.now().toStandardString(),
            timestamp = System.currentTimeMillis(),
            partyId = partyId,
            partyName = "مشتری نمونه",
            warehouseId = warehouseId,
            warehouseName = "انبار مرکزی",
            subtotalAmount = 75000L,
            totalAmount = 75000L,
            paymentType = "CASH",
            cashAmount = 75000L,
            status = "FINAL"
        )
        val items = listOf(
            InvoiceItemEntity(
                invoiceId = 0L,
                productId = productId,
                productName = "کالای آزمایشی A",
                quantity = 5.0,
                quantityInSecondary = 5.0,
                unitPrice = 15000L,
                totalPrice = 75000L
            )
        )

        val invoiceId = invoiceRepo.createInvoice(invoice, items)
        assertTrue(invoiceId > 0)

        // بررسی رخدادهای کاردکس
        val movements = inventoryDao.getMovementsByReferenceNumber(invoiceNumber)
        // ۱. باید دقیقاً ۱ رخداد خروج ثبت شده باشد
        assertEquals(1, movements.size)
        assertEquals(-5.0, movements[0].quantitySecondary, 0.001)

        // ۲. نباید هیچ رخداد REV ثبت شده باشد
        val revMovements = inventoryDao.getMovementsByExactReference("REV-$invoiceNumber")
        assertTrue("No REV movement should be created on normal invoice sale", revMovements.isEmpty())

        // موجودی کالا باید ۹۵ شده باشد (۱۰۰ - ۵)
        val updatedProduct = inventoryDao.getProductById(productId)!!
        assertEquals(95.0, updatedProduct.currentStockInSecondary, 0.001)
    }

    @Test
    fun testInvoiceEditingDoesNotCreateRevAndKeepsSingleMovement() = runBlocking {
        val inventoryDao = db.inventoryDao()
        val partyDao = db.partyDao()

        val warehouseId = inventoryDao.insertWarehouse(
            WarehouseEntity(code = "WH-02", name = "انبار مرکزی ۲", isDefault = true)
        )
        val partyId = partyDao.insertParty(
            PartyEntity(code = "SUPP-01", name = "تأمین‌کننده تست", partyType = "SUPPLIER")
        )
        val productId = inventoryDao.insertProduct(
            ProductEntity(
                code = "PRD-02",
                barcode = "BAR-02",
                name = "کالای آزمایشی B",
                category = "عمومی",
                currentStockInSecondary = 50.0,
                buyPrice = 20000L,
                retailPrice = 30000L
            )
        )

        // ۱. ثبت فاکتور خرید اولیه ۱۰ عدد
        val invoiceNumber = "INV-PUR-201"
        val purchaseInvoice = InvoiceEntity(
            invoiceNumber = invoiceNumber,
            invoiceType = "PURCHASE",
            jalaliDate = JalaliDate.now().toStandardString(),
            timestamp = System.currentTimeMillis(),
            partyId = partyId,
            partyName = "تأمین‌کننده تست",
            warehouseId = warehouseId,
            subtotalAmount = 200000L,
            totalAmount = 200000L,
            paymentType = "CASH",
            cashAmount = 200000L,
            status = "FINAL"
        )
        val initialItems = listOf(
            InvoiceItemEntity(
                invoiceId = 0L,
                productId = productId,
                productName = "کالای آزمایشی B",
                quantity = 10.0,
                quantityInSecondary = 10.0,
                unitPrice = 20000L,
                totalPrice = 200000L
            )
        )

        val invoiceId = invoiceRepo.createInvoice(purchaseInvoice, initialItems)
        val prodAfterBuy = inventoryDao.getProductById(productId)!!
        assertEquals(60.0, prodAfterBuy.currentStockInSecondary, 0.001)

        // ۲. ویرایش فاکتور: تغییر تعداد خرید از ۱۰ عدد به ۲۰ عدد
        val updatedItems = listOf(
            InvoiceItemEntity(
                invoiceId = invoiceId,
                productId = productId,
                productName = "کالای آزمایشی B",
                quantity = 20.0,
                quantityInSecondary = 20.0,
                unitPrice = 20000L,
                totalPrice = 400000L
            )
        )
        val updatedInvoice = purchaseInvoice.copy(
            id = invoiceId,
            subtotalAmount = 400000L,
            totalAmount = 400000L,
            cashAmount = 400000L
        )

        val updateSuccess = invoiceRepo.updateExistingInvoice(invoiceId, updatedInvoice, updatedItems)
        assertTrue(updateSuccess)

        // بررسی کاردکس پس از ویرایش
        val allMovements = inventoryDao.getMovementsByExactReference(invoiceNumber)
        // الف) باید فقط ۱ رکورد برای فاکتور وجود داشته باشد (بدون تکرار)
        assertEquals(1, allMovements.size)
        assertEquals(20.0, allMovements[0].quantitySecondary, 0.001)

        // ب) نباید هیچ رخداد REV ثبت شده باشد
        val revMovements = inventoryDao.getMovementsByExactReference("REV-$invoiceNumber")
        assertTrue("Editing invoice should NEVER create REV movement", revMovements.isEmpty())

        // ج) موجودی انبار باید دقیقاً ۷۰ باشد (۵۰ موجودی اولیه + ۲۰ خرید جدید)
        val prodAfterEdit = inventoryDao.getProductById(productId)!!
        assertEquals(70.0, prodAfterEdit.currentStockInSecondary, 0.001)
    }

    @Test
    fun testVoidInvoiceProducesRevAndReturnsStockCorrectly() = runBlocking {
        val inventoryDao = db.inventoryDao()
        val partyDao = db.partyDao()

        val warehouseId = inventoryDao.insertWarehouse(
            WarehouseEntity(code = "WH-03", name = "انبار", isDefault = true)
        )
        val partyId = partyDao.insertParty(
            PartyEntity(code = "CUST-03", name = "مشتری ابطال", partyType = "CUSTOMER")
        )
        val productId = inventoryDao.insertProduct(
            ProductEntity(
                code = "PRD-03",
                barcode = "BAR-03",
                name = "کالای آزمایشی C",
                category = "عمومی",
                currentStockInSecondary = 40.0,
                buyPrice = 5000L,
                retailPrice = 8000L
            )
        )

        // ۱. ثبت فاکتور فروش ۸ عدد
        val invoiceNumber = "INV-SALE-301"
        val invoice = InvoiceEntity(
            invoiceNumber = invoiceNumber,
            invoiceType = "SALE",
            jalaliDate = JalaliDate.now().toStandardString(),
            timestamp = System.currentTimeMillis(),
            partyId = partyId,
            partyName = "مشتری ابطال",
            warehouseId = warehouseId,
            subtotalAmount = 64000L,
            totalAmount = 64000L,
            paymentType = "CASH",
            cashAmount = 64000L,
            status = "FINAL"
        )
        val items = listOf(
            InvoiceItemEntity(
                invoiceId = 0L,
                productId = productId,
                productName = "کالای آزمایشی C",
                quantity = 8.0,
                quantityInSecondary = 8.0,
                unitPrice = 8000L,
                totalPrice = 64000L
            )
        )

        val invoiceId = invoiceRepo.createInvoice(invoice, items)
        assertEquals(32.0, inventoryDao.getProductById(productId)!!.currentStockInSecondary, 0.001)

        // ۲. کاربر دکمه ابطال را می‌زند (ابطال تعمدی)
        val voidResult = invoiceRepo.voidInvoice(invoiceId)
        assertTrue(voidResult)

        // الف) باید فقط یک رخداد REV ثبت شده باشد
        val revMovements = inventoryDao.getMovementsByExactReference("REV-$invoiceNumber")
        assertEquals(1, revMovements.size)
        assertEquals(8.0, revMovements[0].quantitySecondary, 0.001)

        // ب) موجودی کالا باید دقیقاً به ۴۰.۰ برگردد
        val prodAfterVoid = inventoryDao.getProductById(productId)!!
        assertEquals(40.0, prodAfterVoid.currentStockInSecondary, 0.001)

        // ج) اجرای دوباره voidInvoice نباید رخداد REV دوم ایجاد کند (Idempotent)
        invoiceRepo.voidInvoice(invoiceId)
        val revMovementsAfterRetry = inventoryDao.getMovementsByExactReference("REV-$invoiceNumber")
        assertEquals(1, revMovementsAfterRetry.size)
    }

    @Test
    fun testCleanupCorruptedMovementsAndReconcileStock() = runBlocking {
        val inventoryDao = db.inventoryDao()
        val invoiceDao = db.invoiceDao()

        val warehouseId = inventoryDao.insertWarehouse(
            WarehouseEntity(code = "WH-04", name = "انبار پاکسازی", isDefault = true)
        )
        val productId = inventoryDao.insertProduct(
            ProductEntity(
                code = "PRD-04",
                barcode = "BAR-04",
                name = "کالای آزمایشی D",
                category = "عمومی",
                currentStockInSecondary = 0.0,
                buyPrice = 1000L,
                retailPrice = 2000L
            )
        )

        // فرض: یک فاکتور فعال FINAL در سیستم با شماره INV-CLEAN-401 داریم
        val invoiceNumber = "INV-CLEAN-401"
        val invoiceId = invoiceDao.insertInvoice(
            InvoiceEntity(
                invoiceNumber = invoiceNumber,
                invoiceType = "SALE",
                jalaliDate = JalaliDate.now().toStandardString(),
                timestamp = System.currentTimeMillis(),
                warehouseId = warehouseId,
                subtotalAmount = 10000L,
                totalAmount = 10000L,
                status = "FINAL"
            )
        )

        // شبیه‌سازی باگ گذشته: درج رخدادهای تکراری و درج ناخواسته رخداد REV برای فاکتور فعال
        inventoryDao.insertMovement(
            InventoryMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                jalaliDate = "1403/08/01",
                documentType = "SALE",
                referenceNumber = invoiceNumber,
                quantitySecondary = -10.0,
                unitPrice = 2000L,
                description = "خروج تکراری ۱"
            )
        )
        inventoryDao.adjustStock(productId, -10.0)

        inventoryDao.insertMovement(
            InventoryMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                jalaliDate = "1403/08/01",
                documentType = "SALE",
                referenceNumber = invoiceNumber,
                quantitySecondary = -10.0,
                unitPrice = 2000L,
                description = "خروج تکراری ۲"
            )
        )
        inventoryDao.adjustStock(productId, -10.0)

        inventoryDao.insertMovement(
            InventoryMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                jalaliDate = "1403/08/01",
                documentType = "ROLLBACK_SALE",
                referenceNumber = "REV-$invoiceNumber",
                quantitySecondary = 10.0,
                unitPrice = 2000L,
                description = "ابطال ناخواسته"
            )
        )
        inventoryDao.adjustStock(productId, 10.0)

        // همچنین یک ورود اولیه موجودی ثبت می‌کنیم
        inventoryDao.insertMovement(
            InventoryMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                jalaliDate = "1403/08/01",
                documentType = "PURCHASE",
                referenceNumber = "INV-PUR-INIT",
                quantitySecondary = 100.0,
                unitPrice = 1000L,
                description = "خرید اولیه"
            )
        )
        inventoryDao.adjustStock(productId, 100.0)

        // اجرای عملیات جامع پاکسازی داده‌های معیوب
        val cleanedCount = invoiceRepo.cleanupCorruptedMovementsAndReconcileStock()
        assertTrue("Should have cleaned corrupted records", cleanedCount >= 2)

        // بررسی:
        // ۱. رخداد REV ناخواسته برای فاکتور غیر باطل پاک شده است
        val revs = inventoryDao.getMovementsByExactReference("REV-$invoiceNumber")
        assertTrue(revs.isEmpty())

        // ۲. فقط ۱ رکورد خروج برای فاکتور شماره INV-CLEAN-401 باقی مانده است
        val activeMovements = inventoryDao.getMovementsByExactReference(invoiceNumber)
        assertEquals(1, activeMovements.size)
        assertEquals(-10.0, activeMovements[0].quantitySecondary, 0.001)

        // ۳. موجودی کالا به صورت خودکار بازنگری شده است: ۱۰۰ (خرید) - ۱۰ (فروش) = ۹۰
        val reconciledProduct = inventoryDao.getProductById(productId)!!
        assertEquals(90.0, reconciledProduct.currentStockInSecondary, 0.001)
    }
}
