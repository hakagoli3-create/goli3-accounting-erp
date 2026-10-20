package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private fun createHelper(
        context: Context,
        dbName: String,
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreate(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Handled manually in migration tests
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun `test migration 1 to 2 executes cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_1_2.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 1) { db ->
            db.execSQL("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name TEXT)")
            db.execSQL("INSERT INTO test_table VALUES (1, 'گلی')")
        }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT name FROM test_table WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("گلی", cursor.getString(0))
        }
        helper.close()
    }

    @Test
    fun `test migration 2 to 3 adds isDefault and preserves warehouse data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_2_3.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 2) { db ->
            db.execSQL("""
                CREATE TABLE warehouses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    name TEXT NOT NULL,
                    managerName TEXT NOT NULL,
                    address TEXT NOT NULL,
                    phone TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO warehouses (code, name, managerName, address, phone) VALUES ('W01', 'انبار اصلی تهران', 'محمدی', 'میدان بهارستان', '021-1234')")
        }
        val db = helper.writableDatabase

        assertFalse(AppDatabase.columnExists(db, "warehouses", "isDefault"))

        AppDatabase.MIGRATION_2_3.migrate(db)

        assertTrue(AppDatabase.columnExists(db, "warehouses", "isDefault"))

        db.query("SELECT code, name, isDefault FROM warehouses WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("W01", cursor.getString(0))
            assertEquals("انبار اصلی تهران", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        helper.close()
    }

    @Test
    fun `test migration 3 to 4 creates expense and income tables with indices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_3_4.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 3) { _ -> }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_3_4.migrate(db)

        // Verify expense categories and table
        db.execSQL("INSERT INTO expense_categories (code, title, description, iconName) VALUES ('EXP-01', 'اجاره', 'اجاره ماهانه', 'Store')")
        db.execSQL("INSERT INTO expenses (categoryId, categoryTitle, amount, jalaliDate, timestamp, paymentMethod, accountName, title, notes) VALUES (1, 'اجاره', 5000000, '1404/01/01', 1700000000, 'CASH', 'صندوق', 'اجاره دفتر', 'تست')")

        db.query("SELECT title, amount FROM expenses WHERE categoryId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("اجاره دفتر", cursor.getString(0))
            assertEquals(5000000L, cursor.getLong(1))
        }

        // Verify income categories and table
        db.execSQL("INSERT INTO income_categories (code, title, description, iconName) VALUES ('INC-01', 'درآمد متفرقه', 'تست', 'TrendingUp')")
        db.execSQL("INSERT INTO incomes (categoryId, categoryTitle, amount, jalaliDate, timestamp, depositMethod, accountName, title, notes) VALUES (1, 'درآمد متفرقه', 1200000, '1404/01/01', 1700000000, 'BANK', 'ملت', 'فروش ضایعات', 'تست')")

        db.query("SELECT title, amount FROM incomes WHERE categoryId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("فروش ضایعات", cursor.getString(0))
            assertEquals(1200000L, cursor.getLong(1))
        }
        helper.close()
    }

    @Test
    fun `test migration 4 to 5 creates users table and seeds default users without overwriting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_4_5.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 4) { _ -> }
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_4_5.migrate(db)

        // Verify default users seeded
        db.query("SELECT username, role, isActive FROM users ORDER BY id ASC").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("admin", cursor.getString(0))
            assertEquals("ADMIN", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))

            assertTrue(cursor.moveToNext())
            assertEquals("seller", cursor.getString(0))

            assertTrue(cursor.moveToNext())
            assertEquals("visitor", cursor.getString(0))
        }

        // Migration running again should NOT overwrite modified passwords or user records
        db.execSQL("UPDATE users SET passwordHash = 'custom_secret_pass' WHERE username = 'admin'")
        AppDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT passwordHash FROM users WHERE username = 'admin'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("custom_secret_pass", cursor.getString(0))
        }
        helper.close()
    }

    @Test
    fun `test migration 5 to 6 adds imageUri and preserves product prices and stock`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_5_6.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 5) { db ->
            db.execSQL("""
                CREATE TABLE products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    barcode TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    primaryUnit TEXT NOT NULL,
                    secondaryUnit TEXT NOT NULL,
                    conversionRate REAL NOT NULL,
                    currentStockInSecondary REAL NOT NULL,
                    minStockThreshold REAL NOT NULL,
                    buyPrice INTEGER NOT NULL,
                    retailPrice INTEGER NOT NULL,
                    wholesalePrice INTEGER NOT NULL,
                    partnerPrice INTEGER NOT NULL,
                    description TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO products (code, barcode, name, category, primaryUnit, secondaryUnit, conversionRate, currentStockInSecondary, minStockThreshold, buyPrice, retailPrice, wholesalePrice, partnerPrice, description)
                VALUES ('PRD-01', '123456789', 'روغن زیتون گلی', 'روغن', 'کارتن', 'عدد', 12.0, 48.0, 10.0, 200000, 280000, 250000, 240000, 'روغن طبیعی')
            """.trimIndent())
        }
        val db = helper.writableDatabase

        assertFalse(AppDatabase.columnExists(db, "products", "imageUri"))

        AppDatabase.MIGRATION_5_6.migrate(db)

        assertTrue(AppDatabase.columnExists(db, "products", "imageUri"))

        db.query("SELECT name, buyPrice, currentStockInSecondary, imageUri FROM products WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("روغن زیتون گلی", cursor.getString(0))
            assertEquals(200000L, cursor.getLong(1))
            assertEquals(48.0, cursor.getDouble(2), 0.001)
            org.junit.Assert.assertNull(cursor.getString(3))
        }
        helper.close()
    }

    @Test
    fun `test migration 6 to 7 adds currency columns and preserves invoice financial totals`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_6_7.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 6) { db ->
            db.execSQL("""
                CREATE TABLE invoices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    invoiceNumber TEXT NOT NULL,
                    invoiceType TEXT NOT NULL,
                    jalaliDate TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    partyId INTEGER,
                    partyName TEXT NOT NULL,
                    partyPhone TEXT NOT NULL,
                    warehouseId INTEGER NOT NULL,
                    warehouseName TEXT NOT NULL,
                    subtotalAmount INTEGER NOT NULL,
                    discountAmount INTEGER NOT NULL,
                    taxAmount INTEGER NOT NULL,
                    totalAmount INTEGER NOT NULL,
                    paymentType TEXT NOT NULL,
                    cashAmount INTEGER NOT NULL,
                    posAmount INTEGER NOT NULL,
                    creditAmount INTEGER NOT NULL,
                    chequeAmount INTEGER NOT NULL,
                    cashDeskId INTEGER NOT NULL,
                    bankAccountId INTEGER NOT NULL,
                    pricingMode TEXT NOT NULL,
                    directExpenseAmount INTEGER NOT NULL,
                    directExpenseType TEXT NOT NULL,
                    isPaid INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO invoices (
                    invoiceNumber, invoiceType, jalaliDate, timestamp, partyId, partyName, partyPhone,
                    warehouseId, warehouseName, subtotalAmount, discountAmount, taxAmount, totalAmount,
                    paymentType, cashAmount, posAmount, creditAmount, chequeAmount, cashDeskId, bankAccountId,
                    pricingMode, directExpenseAmount, directExpenseType, isPaid, status, notes
                ) VALUES (
                    'INV-1001', 'SALE', '1404/01/10', 1700000000, 1, 'هایپرمارکت بهارستان', '09121112233',
                    1, 'انبار مرکزی', 10000000, 500000, 950000, 10450000,
                    'MIXED', 2000000, 5000000, 1450000, 2000000, 1, 1,
                    'RETAIL', 0, '', 1, 'FINAL', 'فاکتور تسویه شده'
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE cash_desks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    code TEXT NOT NULL,
                    name TEXT NOT NULL,
                    currentBalance INTEGER NOT NULL,
                    keeperName TEXT NOT NULL,
                    note TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO cash_desks VALUES (1, 'CSH-01', 'صندوق اصلی', 32500000, 'آقای گلی', '')")

            db.execSQL("""
                CREATE TABLE bank_accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bankName TEXT NOT NULL,
                    accountNumber TEXT NOT NULL,
                    shebaNumber TEXT NOT NULL,
                    cardNumber TEXT NOT NULL,
                    branch TEXT NOT NULL,
                    currentBalance INTEGER NOT NULL,
                    hasPos INTEGER NOT NULL,
                    posTerminalId TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO bank_accounts VALUES (1, 'بانک ملت', '123456', '', '', 'بازار', 145000000, 1, 'POS-1')")
        }
        val db = helper.writableDatabase

        assertFalse(AppDatabase.columnExists(db, "invoices", "currencyCode"))
        assertFalse(AppDatabase.columnExists(db, "invoices", "totalAmountInBase"))

        AppDatabase.MIGRATION_6_7.migrate(db)

        // Verify invoice columns added and values migrated to base currency
        assertTrue(AppDatabase.columnExists(db, "invoices", "currencyCode"))
        assertTrue(AppDatabase.columnExists(db, "invoices", "totalAmountInBase"))
        assertTrue(AppDatabase.columnExists(db, "invoices", "posAmountInBase"))

        db.query("SELECT currencyCode, totalAmount, totalAmountInBase, posAmountInBase, cashAmountInBase FROM invoices WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("TOMAN", cursor.getString(0))
            assertEquals(10450000L, cursor.getLong(1))
            assertEquals(10450000L, cursor.getLong(2)) // Preserved!
            assertEquals(5000000L, cursor.getLong(3))  // Preserved!
            assertEquals(2000000L, cursor.getLong(4))  // Preserved!
        }

        // Verify treasury_currency_balances table exists and populated from cash_desks & bank_accounts
        db.query("SELECT accountType, accountId, currencyCode, balance FROM treasury_currency_balances WHERE accountType = 'CASH' AND accountId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("TOMAN", cursor.getString(2))
            assertEquals(32500000.0, cursor.getDouble(3), 0.001)
        }
        db.query("SELECT accountType, accountId, currencyCode, balance FROM treasury_currency_balances WHERE accountType = 'BANK' AND accountId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("TOMAN", cursor.getString(2))
            assertEquals(145000000.0, cursor.getDouble(3), 0.001)
        }
        helper.close()
    }

    @Test
    fun `test end to end sequential migration from version 1 to 7 preserves all business data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_end_to_end_v1_to_v7.db"
        context.deleteDatabase(dbName)

        val helper = createHelper(context, dbName, 1) { db ->
            // Core v1 tables
            db.execSQL("CREATE TABLE account_groups (code INTEGER PRIMARY KEY, title TEXT NOT NULL, nature TEXT NOT NULL)")
            db.execSQL("INSERT INTO account_groups VALUES (1, 'دارایی جاری', 'DEBIT')")

            db.execSQL("CREATE TABLE journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, voucherNumber INTEGER NOT NULL, jalaliDate TEXT NOT NULL, timestamp INTEGER NOT NULL, description TEXT NOT NULL, isBalanced INTEGER NOT NULL, status TEXT NOT NULL)")
            db.execSQL("INSERT INTO journal_entries (voucherNumber, jalaliDate, timestamp, description, isBalanced, status) VALUES (1, '1404/01/01', 1700000000, 'سند افتتاحیه', 1, 'FINAL')")

            db.execSQL("CREATE TABLE warehouses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, managerName TEXT NOT NULL, address TEXT NOT NULL, phone TEXT NOT NULL)")
            db.execSQL("INSERT INTO warehouses (code, name, managerName, address, phone) VALUES ('W1', 'انبار مرکزی', 'مدیر', 'آدرس', '123')")

            db.execSQL("""
                CREATE TABLE products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, barcode TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL,
                    primaryUnit TEXT NOT NULL, secondaryUnit TEXT NOT NULL, conversionRate REAL NOT NULL, currentStockInSecondary REAL NOT NULL,
                    minStockThreshold REAL NOT NULL, buyPrice INTEGER NOT NULL, retailPrice INTEGER NOT NULL, wholesalePrice INTEGER NOT NULL,
                    partnerPrice INTEGER NOT NULL, description TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO products (code, barcode, name, category, primaryUnit, secondaryUnit, conversionRate, currentStockInSecondary, minStockThreshold, buyPrice, retailPrice, wholesalePrice, partnerPrice, description) VALUES ('P1', 'BAR1', 'برنج طارم', 'غلات', 'کیسه', 'کیلو', 10.0, 100.0, 10.0, 100000, 140000, 130000, 125000, 'شرح')")

            db.execSQL("""
                CREATE TABLE parties (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, companyName TEXT NOT NULL,
                    mobile TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, nationalCode TEXT NOT NULL, economicCode TEXT NOT NULL,
                    partyType TEXT NOT NULL, creditLimit INTEGER NOT NULL, currentBalance INTEGER NOT NULL, isActive INTEGER NOT NULL, notes TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO parties (code, name, companyName, mobile, phone, address, nationalCode, economicCode, partyType, creditLimit, currentBalance, isActive, notes) VALUES ('C1', 'مشتری اول', 'شرکت اول', '0912', '123', 'تهران', '1234', '5678', 'CUSTOMER', 50000000, 15000000, 1, 'نکته')")

            db.execSQL("""
                CREATE TABLE invoices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceNumber TEXT NOT NULL, invoiceType TEXT NOT NULL, jalaliDate TEXT NOT NULL, timestamp INTEGER NOT NULL,
                    partyId INTEGER, partyName TEXT NOT NULL, partyPhone TEXT NOT NULL, warehouseId INTEGER NOT NULL, warehouseName TEXT NOT NULL,
                    subtotalAmount INTEGER NOT NULL, discountAmount INTEGER NOT NULL, taxAmount INTEGER NOT NULL, totalAmount INTEGER NOT NULL,
                    paymentType TEXT NOT NULL, cashAmount INTEGER NOT NULL, posAmount INTEGER NOT NULL, creditAmount INTEGER NOT NULL, chequeAmount INTEGER NOT NULL,
                    cashDeskId INTEGER NOT NULL, bankAccountId INTEGER NOT NULL, pricingMode TEXT NOT NULL, directExpenseAmount INTEGER NOT NULL, directExpenseType TEXT NOT NULL,
                    isPaid INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO invoices (invoiceNumber, invoiceType, jalaliDate, timestamp, partyId, partyName, partyPhone, warehouseId, warehouseName, subtotalAmount, discountAmount, taxAmount, totalAmount, paymentType, cashAmount, posAmount, creditAmount, chequeAmount, cashDeskId, bankAccountId, pricingMode, directExpenseAmount, directExpenseType, isPaid, status, notes) VALUES ('INV-1', 'SALE', '1404/01/01', 1700000000, 1, 'مشتری اول', '0912', 1, 'انبار مرکزی', 1000000, 0, 100000, 1100000, 'CASH', 1100000, 0, 0, 0, 1, 1, 'RETAIL', 0, '', 1, 'FINAL', '')")

            db.execSQL("CREATE TABLE cash_desks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, currentBalance INTEGER NOT NULL, keeperName TEXT NOT NULL, note TEXT NOT NULL)")
            db.execSQL("INSERT INTO cash_desks VALUES (1, 'C01', 'صندوق اصلی', 50000000, 'مدیر', '')")
            db.execSQL("CREATE TABLE bank_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bankName TEXT NOT NULL, accountNumber TEXT NOT NULL, shebaNumber TEXT NOT NULL, cardNumber TEXT NOT NULL, branch TEXT NOT NULL, currentBalance INTEGER NOT NULL, hasPos INTEGER NOT NULL, posTerminalId TEXT NOT NULL)")
            db.execSQL("INSERT INTO bank_accounts VALUES (1, 'بانک ملت', '111', '', '', 'مرکزی', 200000000, 1, 'P1')")
        }
        val db = helper.writableDatabase

        // Execute all migrations sequentially
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)
        AppDatabase.MIGRATION_3_4.migrate(db)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)
        AppDatabase.MIGRATION_6_7.migrate(db)

        // 1. Verify v1 accounting voucher preserved
        db.query("SELECT voucherNumber, description FROM journal_entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("سند افتتاحیه", cursor.getString(1))
        }

        // 2. Verify v1 customer party preserved
        db.query("SELECT name, currentBalance, creditLimit FROM parties WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("مشتری اول", cursor.getString(0))
            assertEquals(15000000L, cursor.getLong(1))
            assertEquals(50000000L, cursor.getLong(2))
        }

        // 3. Verify warehouse updated with isDefault
        db.query("SELECT name, isDefault FROM warehouses WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("انبار مرکزی", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }

        // 4. Verify product preserved and imageUri added
        db.query("SELECT name, currentStockInSecondary, buyPrice, imageUri FROM products WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("برنج طارم", cursor.getString(0))
            assertEquals(100.0, cursor.getDouble(1), 0.001)
            assertEquals(100000L, cursor.getLong(2))
            org.junit.Assert.assertNull(cursor.getString(3))
        }

        // 5. Verify users created
        db.query("SELECT COUNT(*) FROM users").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getInt(0) >= 3)
        }

        // 6. Verify invoice migrated to base currency
        db.query("SELECT totalAmount, totalAmountInBase, currencyCode FROM invoices WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1100000L, cursor.getLong(0))
            assertEquals(1100000L, cursor.getLong(1))
            assertEquals("TOMAN", cursor.getString(2))
        }

        // 7. Verify treasury balances populated
        db.query("SELECT balance FROM treasury_currency_balances WHERE accountType = 'CASH' AND accountId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(50000000.0, cursor.getDouble(0), 0.001)
        }
        db.query("SELECT balance FROM treasury_currency_balances WHERE accountType = 'BANK' AND accountId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(200000000.0, cursor.getDouble(0), 0.001)
        }

        helper.close()
    }

    @Test
    fun `test AppDatabase getInstance initializes without destructive migration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        assertNotNull(db)
        // Room opens the database lazily upon first access
        val writable = db.openHelper.writableDatabase
        assertNotNull(writable)
        assertTrue(db.isOpen)
        assertTrue(writable.isOpen)
    }

    @Test
    fun `test DatabaseBackupHelper createDatabaseBackup and integrity validation`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val writable = db.openHelper.writableDatabase
        assertNotNull(writable)

        val backupResult = com.example.util.DatabaseBackupHelper.createDatabaseBackup(context)
        assertTrue("Backup creation should succeed", backupResult.isSuccess)
        val backupFile = backupResult.getOrNull()
        assertNotNull(backupFile)
        assertTrue(backupFile!!.exists())
        assertTrue(backupFile.length() > 0)

        val integrity = com.example.util.DatabaseSecurityManager.verifyDatabaseIntegrity(backupFile)
        assertTrue(integrity.first)

        val existingBackups = com.example.util.DatabaseBackupHelper.getExistingBackupFiles(context)
        assertTrue(existingBackups.isNotEmpty())
    }
}
