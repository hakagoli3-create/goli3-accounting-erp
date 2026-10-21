package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignKeyDiagnosticTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var invoiceRepo: InvoiceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
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
    fun testCreateInvoiceWithEmptyWarehouseTable() = runBlocking {
        // Clear all warehouses to simulate database where warehouse with id=1 does not exist
        val sqliteDb = db.openHelper.writableDatabase
        sqliteDb.execSQL("DELETE FROM inventory_movements")
        sqliteDb.execSQL("DELETE FROM warehouses")

        // Verify warehouses is empty
        val countCursor = sqliteDb.query("SELECT COUNT(*) FROM warehouses")
        countCursor.moveToFirst()
        assertEquals(0, countCursor.getInt(0))
        countCursor.close()

        val prodId = db.inventoryDao().insertProduct(
            ProductEntity(
                code = "PRD-TEST-WH",
                name = "کالای آزمون انبار خالی",
                barcode = "998877",
                category = "عمومی",
                retailPrice = 15000L,
                currentStockInSecondary = 10.0
            )
        )

        val inv = InvoiceEntity(
            invoiceNumber = "TEST-EMPTY-WH-01",
            invoiceType = "SALE",
            jalaliDate = "1405/01/01",
            warehouseId = 1L, // requesting 1L when warehouses table is completely empty!
            totalAmount = 15000L,
            paymentType = "CASH",
            cashAmount = 15000L,
            status = "FINAL"
        )
        val item = InvoiceItemEntity(
            invoiceId = 0L,
            productId = prodId,
            productName = "کالای آزمون انبار خالی",
            quantity = 1.0,
            quantityInSecondary = 1.0,
            unitPrice = 15000L,
            totalPrice = 15000L
        )

        try {
            val id = invoiceRepo.createInvoice(inv, listOf(item))
            println("Successfully created invoice with id $id when warehouse table was empty!")
        } catch (e: Exception) {
            println("FAILED with exception: ${e::class.java.name}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
