package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.ProductEntity
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PartyRepository
import com.example.data.repository.TreasuryRepository
import com.example.ui.viewmodel.InvoiceViewModel
import com.example.util.InvoiceBarcodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarcodeScannerIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var invoiceRepo: InvoiceRepository
    private lateinit var partyRepo: PartyRepository
    private lateinit var treasuryRepo: TreasuryRepository
    private lateinit var viewModel: InvoiceViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)

        inventoryRepo = InventoryRepository(
            dao = db.inventoryDao(),
            appDatabase = db
        )
        invoiceRepo = InvoiceRepository(
            invoiceDao = db.invoiceDao(),
            inventoryDao = db.inventoryDao(),
            partyDao = db.partyDao(),
            treasuryDao = db.treasuryDao(),
            accountingDao = db.accountingDao(),
            appDatabase = db
        )
        partyRepo = PartyRepository(
            dao = db.partyDao(),
            accountingDao = db.accountingDao(),
            appDatabase = db
        )
        treasuryRepo = TreasuryRepository(
            dao = db.treasuryDao(),
            appDatabase = db
        )

        viewModel = InvoiceViewModel(
            invoiceRepo = invoiceRepo,
            inventoryRepo = inventoryRepo,
            partyRepo = partyRepo,
            treasuryRepo = treasuryRepo
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInvoiceBarcodeHelperDirectLookup() = runBlocking {
        val testBarcode = "6260123456789"
        val testCode = "PRD-BARCODE-99"
        val product = ProductEntity(
            code = testCode,
            barcode = testBarcode,
            name = "روغن زیتون فرابکر ۱ لیتری",
            category = "روغنیات",
            primaryUnit = "کارتن",
            secondaryUnit = "بطری",
            conversionRate = 6.0,
            buyPrice = 120_000L,
            retailPrice = 160_000L,
            wholesalePrice = 140_000L
        )

        db.inventoryDao().insertProduct(product)

        // ۱. جستجو بر اساس بارکد ملی/تجاری در دیتابیس
        val foundByBarcode = InvoiceBarcodeHelper.findProductByBarcode(testBarcode, inventoryRepo)
        assertNotNull("محصول باید با بارکد دقیق در دیتابیس پیدا شود", foundByBarcode)
        assertEquals("روغن زیتون فرابکر ۱ لیتری", foundByBarcode?.name)

        // ۲. جستجو بر اساس کد کالا
        val foundByCode = InvoiceBarcodeHelper.findProductByBarcode(testCode, inventoryRepo, listOf(product))
        assertNotNull("محصول باید با کد کالا نیز در صورت اسکن کد شناسایی شود", foundByCode)
        assertEquals(testBarcode, foundByCode?.barcode)

        // ۳. بارکد ناموجود
        val notFound = InvoiceBarcodeHelper.findProductByBarcode("9999999999999", inventoryRepo)
        assertNull("بارکد ناموجود باید مقدار null برگرداند", notFound)
    }

    @Test
    fun testScanBarcodeInSalesInvoiceIncrementsQuantityOrAddsRow() = runBlocking {
        val barcode = "6261112223334"
        val product = ProductEntity(
            code = "PRD-MILK-01",
            barcode = barcode,
            name = "شیر پرچرب پگاه ۱ لیتری",
            category = "لبنیات",
            primaryUnit = "کارتن",
            secondaryUnit = "پاکت",
            conversionRate = 12.0,
            buyPrice = 28_000L,
            retailPrice = 35_000L,
            wholesalePrice = 31_000L
        )
        db.inventoryDao().insertProduct(product)

        viewModel.setCurrentInvoiceType("SALE")
        viewModel.setPricingMode("RETAIL")
        viewModel.clearCart()

        // ۱. اولین اسکن بارکد: باید یک ردیف جدید به سبد فاکتور با تعداد ۱ اضافه کند
        viewModel.scanBarcode(barcode).join()

        val cartAfterFirstScan = viewModel.cartItems.value
        assertEquals("باید دقیقاً ۱ ردیف در سبد فاکتور موجود باشد", 1, cartAfterFirstScan.size)
        val item1 = cartAfterFirstScan.first()
        assertEquals("شیر پرچرب پگاه ۱ لیتری", item1.product.name)
        assertEquals(1.0, item1.quantity, 0.001)
        assertEquals(35_000L, item1.unitPrice)

        // ۲. دومین اسکن همان بارکد: نباید ردیف تکراری ایجاد شود، بلکه تعداد باید ۲ شود
        viewModel.scanBarcode(barcode).join()

        val cartAfterSecondScan = viewModel.cartItems.value
        assertEquals("همچنان باید دقیقاً ۱ ردیف باشد و ردیف تکراری ایجاد نشود", 1, cartAfterSecondScan.size)
        val item2 = cartAfterSecondScan.first()
        assertEquals("تعداد کالا در فاکتور باید ۲ واحد شده باشد", 2.0, item2.quantity, 0.001)

        // ۳. سومین اسکن همان بارکد: تعداد باید ۳ شود
        viewModel.scanBarcode(barcode).join()
        val cartAfterThirdScan = viewModel.cartItems.value
        assertEquals(1, cartAfterThirdScan.size)
        assertEquals(3.0, cartAfterThirdScan.first().quantity, 0.001)
    }

    @Test
    fun testScanBarcodeInPurchaseInvoiceAppliesBuyPrice() = runBlocking {
        val barcode = "6268889990001"
        val product = ProductEntity(
            code = "PRD-RICE-10",
            barcode = barcode,
            name = "برنج طارم هاشمی ممتاز ۱۰ کیلوگرمی",
            category = "غلات",
            primaryUnit = "کیسه",
            secondaryUnit = "کیلو",
            conversionRate = 10.0,
            buyPrice = 110_000L,
            retailPrice = 145_000L,
            wholesalePrice = 130_000L
        )
        db.inventoryDao().insertProduct(product)

        // فاکتور خرید
        viewModel.setCurrentInvoiceType("PURCHASE")
        viewModel.clearCart()

        viewModel.scanBarcode(barcode).join()

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        val item = cart.first()
        assertEquals("برنج طارم هاشمی ممتاز ۱۰ کیلوگرمی", item.product.name)
        assertEquals("در فاکتور خرید باید قیمت خرید منظور شود", 110_000L, item.unitPrice)
        assertEquals(1.0, item.quantity, 0.001)
    }

    @Test
    fun testScanUnknownBarcodeSetsAlertMessage() = runBlocking {
        viewModel.clearCart()
        viewModel.clearAlertMessage()

        val unknownBarcode = "0000000000000"
        viewModel.scanBarcode(unknownBarcode).join()

        val alert = viewModel.alertMessage.value
        assertNotNull("پیام خطا یا هشدار مناسب باید برای کاربر ثبت شود", alert)
        assertTrue("پیام باید حاوی عبارت بارکد یا انبار باشد", alert?.contains("انبار") == true || alert?.contains("یافت نشد") == true)

        val cart = viewModel.cartItems.value
        assertTrue("سبد فاکتور نباید برای بارکد ناموجود تغییر کند", cart.isEmpty())
    }
}
