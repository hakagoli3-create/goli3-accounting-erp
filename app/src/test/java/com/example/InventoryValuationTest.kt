package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.ProductEntity
import com.example.data.model.WarehouseEntity
import com.example.util.formatWithSeparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryValuationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
    }

    @Test
    fun testInventoryValuationFlowsReturnCorrectValues() = runBlocking {
        val inventoryDao = db.inventoryDao()

        // پاکسازی محصولات برای اجرای تست مستقل
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("DELETE FROM inventory_movements")
        sqlite.execSQL("DELETE FROM invoice_items")
        sqlite.execSQL("DELETE FROM products")

        // بررسی حالت اولیه خالی بودن انبار -> باید دقیقاً ۰.۰ برگرداند (به لطف COALESCE)
        val initialPurchaseVal = inventoryDao.getTotalInventoryPurchaseValue().first()
        val initialSellingVal = inventoryDao.getTotalInventorySellingValue().first()
        assertEquals(0.0, initialPurchaseVal, 0.001)
        assertEquals(0.0, initialSellingVal, 0.001)

        // ثبت انبار پیش‌فرض
        inventoryDao.insertWarehouse(
            WarehouseEntity(
                code = "WH-VAL-01",
                name = "انبار مرکزی ارزش‌گذاری",
                isDefault = true
            )
        )

        // کالا ۱: موجودی ۱۰ عدد، قیمت خرید ۵۰,۰۰۰ تومان، قیمت فروش ۷۰,۰۰۰ تومان
        // ارزش خرید: ۵۰۰,۰۰۰ تومان | ارزش فروش: ۷۰۰,۰۰۰ تومان
        inventoryDao.insertProduct(
            ProductEntity(
                code = "P-VAL-01",
                barcode = "111222333",
                name = "روغن زیتون فرابکر ۱ لیتری",
                category = "مواد غذایی",
                currentStockInSecondary = 10.0,
                buyPrice = 50000L,
                retailPrice = 70000L
            )
        )

        // کالا ۲: موجودی ۵ عدد، قیمت خرید ۱۰۰,۰۰۰ تومان، قیمت فروش ۱۴۰,۰۰۰ تومان
        // ارزش خرید: ۵۰۰,۰۰۰ تومان | ارزش فروش: ۷۰۰,۰۰۰ تومان
        inventoryDao.insertProduct(
            ProductEntity(
                code = "P-VAL-02",
                barcode = "444555666",
                name = "برنج هاشمی درجه یک",
                category = "غلات",
                currentStockInSecondary = 5.0,
                buyPrice = 100000L,
                retailPrice = 140000L
            )
        )

        // جمع کل ارزش خرید: 10*50000 + 5*100000 = 500000 + 500000 = 1,000,000 تومان
        // جمع کل ارزش فروش: 10*70000 + 5*140000 = 700000 + 700000 = 1,400,000 تومان
        val purchaseVal = inventoryDao.getTotalInventoryPurchaseValue().first()
        val sellingVal = inventoryDao.getTotalInventorySellingValue().first()

        assertEquals(1000000.0, purchaseVal, 0.01)
        assertEquals(1400000.0, sellingVal, 0.01)

        // تست فرمت‌بندی اعداد با formatWithSeparator()
        val formattedPurchase = purchaseVal.formatWithSeparator()
        val formattedSelling = sellingVal.formatWithSeparator()
        assertTrue(formattedPurchase.contains("۱٬۰۰۰٬۰۰۰") || formattedPurchase.contains("1,000,000") || formattedPurchase.contains("۱,۰۰۰,۰۰۰"))
        assertTrue(formattedSelling.contains("۱٬۴۰۰٬۰۰۰") || formattedSelling.contains("1,400,000") || formattedSelling.contains("۱,۴۰۰,۰۰۰"))

        // تست جریان‌های مستعار (Aliases)
        assertEquals(purchaseVal, inventoryDao.getInventoryPurchaseValue().first(), 0.01)
        assertEquals(sellingVal, inventoryDao.getTotalInventorySalesValue().first(), 0.01)
        assertEquals(sellingVal, inventoryDao.getInventorySalesValue().first(), 0.01)
        assertEquals(sellingVal, inventoryDao.getInventorySellingValue().first(), 0.01)
    }
}
