package com.example.util

import com.example.data.model.ProductEntity
import com.example.data.repository.InventoryRepository

/**
 * متد کمکی و ماژولار جهت جستجو و پردازش بارکد کالا در تمامی بخش‌های فاکتور
 * (فروش، خرید، مرجوعی فروش، مرجوعی خرید و صندوق POS)
 */
object InvoiceBarcodeHelper {

    sealed class BarcodeResult {
        data class Found(val product: ProductEntity, val isIncrement: Boolean) : BarcodeResult()
        data class NotFound(val barcode: String) : BarcodeResult()
        data class Error(val message: String) : BarcodeResult()
    }

    /**
     * جستجوی سریع محصول در دیتابیس بر اساس بارکد یا کد کالا
     */
    suspend fun findProductByBarcode(
        barcode: String,
        inventoryRepo: InventoryRepository,
        cachedProducts: List<ProductEntity> = emptyList()
    ): ProductEntity? {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return null

        // اول: جستجو در جدول محصولات دیتابیس با متد بهینه Room
        val fromDb = try {
            inventoryRepo.getProductByBarcode(cleanBarcode)
        } catch (e: Exception) {
            null
        }

        if (fromDb != null) return fromDb

        // دوم: تطبیق با کش کالاهای موجود یا کد کالا در صورت تفاوت حروف کوچک/بزرگ یا فیلد کد
        return cachedProducts.firstOrNull { prod ->
            prod.barcode.trim().equals(cleanBarcode, ignoreCase = true) ||
            prod.code.trim().equals(cleanBarcode, ignoreCase = true)
        }
    }
}
