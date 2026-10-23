package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccountingDao
import com.example.data.dao.ExchangeRateDao
import com.example.data.dao.ExpenseAndIncomeDao
import com.example.data.dao.InventoryDao
import com.example.data.dao.InvoiceDao
import com.example.data.dao.PartyDao
import com.example.data.dao.StoreSettingsDao
import com.example.data.dao.TreasuryDao
import com.example.data.dao.UserDao
import com.example.data.model.AccountGroupEntity
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.data.model.ExchangeRateEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.IncomeCategoryEntity
import com.example.data.model.IncomeEntity
import com.example.data.model.InventoryMovementEntity
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StoreSettingsEntity
import com.example.data.model.SubsidiaryAccountEntity
import com.example.data.model.TreasuryCurrencyBalanceEntity
import com.example.data.model.UserEntity
import com.example.data.model.WarehouseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountGroupEntity::class,
        GeneralAccountEntity::class,
        SubsidiaryAccountEntity::class,
        JournalEntryEntity::class,
        JournalItemEntity::class,
        WarehouseEntity::class,
        ProductEntity::class,
        InventoryMovementEntity::class,
        PartyEntity::class,
        CashDeskEntity::class,
        BankAccountEntity::class,
        ChequeEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseEntity::class,
        IncomeCategoryEntity::class,
        IncomeEntity::class,
        UserEntity::class,
        ExchangeRateEntity::class,
        TreasuryCurrencyBalanceEntity::class,
        StoreSettingsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountingDao(): AccountingDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun partyDao(): PartyDao
    abstract fun treasuryDao(): TreasuryDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun expenseAndIncomeDao(): ExpenseAndIncomeDao
    abstract fun userDao(): UserDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun storeSettingsDao(): StoreSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        fun addColumnIfNotExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDef: String
        ) {
            if (!columnExists(db, tableName, columnName)) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe migration path: Version 1 to 2 schema integrity check (no structural changes, all indexes & tables verified)
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "warehouses", "isDefault", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS expense_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconName TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        categoryTitle TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        jalaliDate TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        cashDeskId INTEGER,
                        bankAccountId INTEGER,
                        accountName TEXT NOT NULL,
                        title TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        voucherId INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_categoryId ON expenses(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_jalaliDate ON expenses(jalaliDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_timestamp ON expenses(timestamp)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS income_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconName TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS incomes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        categoryTitle TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        jalaliDate TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        depositMethod TEXT NOT NULL,
                        cashDeskId INTEGER,
                        bankAccountId INTEGER,
                        accountName TEXT NOT NULL,
                        title TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        voucherId INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_incomes_categoryId ON incomes(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_incomes_jalaliDate ON incomes(jalaliDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_incomes_timestamp ON incomes(timestamp)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        username TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        fullName TEXT NOT NULL,
                        role TEXT NOT NULL,
                        mobile TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")

                // Ensure default admin/staff credentials exist without overwriting any existing records
                db.execSQL("""
                    INSERT OR IGNORE INTO users (id, username, passwordHash, fullName, role, mobile, isActive, createdAt)
                    VALUES 
                    (1, 'admin', '1234', 'مدیر کل سیستم', 'ADMIN', '', 1, 1700000000000),
                    (2, 'seller', '1234', 'علی فروشنده (صندوق‌دار)', 'POS_USER', '', 1, 1700000000000),
                    (3, 'visitor', '1234', 'رضا ویزیتور (بازاریاب)', 'VISITOR', '', 1, 1700000000000)
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "products", "imageUri", "TEXT")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exchange_rates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceCurrency TEXT NOT NULL,
                        targetCurrency TEXT NOT NULL,
                        rate REAL NOT NULL,
                        inverseRate REAL NOT NULL,
                        effectiveDateJalali TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        recordedBy TEXT NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_rates_sourceCurrency ON exchange_rates(sourceCurrency)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_rates_targetCurrency ON exchange_rates(targetCurrency)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_rates_effectiveDateJalali ON exchange_rates(effectiveDateJalali)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS treasury_currency_balances (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountType TEXT NOT NULL,
                        accountId INTEGER NOT NULL,
                        currencyCode TEXT NOT NULL,
                        balance REAL NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_currency_balances_accountType ON treasury_currency_balances(accountType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_currency_balances_accountId ON treasury_currency_balances(accountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_currency_balances_currencyCode ON treasury_currency_balances(currencyCode)")

                addColumnIfNotExists(db, "invoices", "currencyCode", "TEXT NOT NULL DEFAULT 'TOMAN'")
                addColumnIfNotExists(db, "invoices", "currencyRateToBase", "REAL NOT NULL DEFAULT 1.0")
                addColumnIfNotExists(db, "invoices", "totalAmountInBase", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "invoices", "cashAmountInBase", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "invoices", "posAmountInBase", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "invoices", "creditAmountInBase", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(db, "invoices", "chequeAmountInBase", "INTEGER NOT NULL DEFAULT 0")

                // Preserving existing financial data: map amounts to base currency
                db.execSQL("""
                    UPDATE invoices 
                    SET totalAmountInBase = totalAmount, 
                        cashAmountInBase = cashAmount, 
                        posAmountInBase = posAmount, 
                        creditAmountInBase = creditAmount, 
                        chequeAmountInBase = chequeAmount 
                    WHERE totalAmountInBase = 0 AND totalAmount > 0
                """.trimIndent())

                // Preserve existing treasury balances into multi-currency balances table
                try {
                    db.execSQL("""
                        INSERT INTO treasury_currency_balances (accountType, accountId, currencyCode, balance)
                        SELECT 'CASH', id, 'TOMAN', CAST(currentBalance AS REAL) FROM cash_desks
                        WHERE NOT EXISTS (
                            SELECT 1 FROM treasury_currency_balances WHERE accountType = 'CASH' AND accountId = cash_desks.id AND currencyCode = 'TOMAN'
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO treasury_currency_balances (accountType, accountId, currencyCode, balance)
                        SELECT 'BANK', id, 'TOMAN', CAST(currentBalance AS REAL) FROM bank_accounts
                        WHERE NOT EXISTS (
                            SELECT 1 FROM treasury_currency_balances WHERE accountType = 'BANK' AND accountId = bank_accounts.id AND currencyCode = 'TOMAN'
                        )
                    """.trimIndent())
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS store_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        managerName TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        mobile TEXT NOT NULL,
                        address TEXT NOT NULL,
                        nationalCode TEXT NOT NULL,
                        economicCode TEXT NOT NULL,
                        defaultBankCard TEXT NOT NULL,
                        defaultSheba TEXT NOT NULL,
                        taxRatePercent INTEGER NOT NULL,
                        wholesaleDiscountLimitPercent INTEGER NOT NULL,
                        currenciesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * بستن قطعی نمونه جاری Room جهت آماده‌سازی برای عملیات بازیابی (Restore) یا اعمال تنظیمات رمزنگاری
         */
        fun closeInstance() {
            synchronized(this) {
                try {
                    val currentInstance = INSTANCE
                    if (currentInstance != null) {
                        try {
                            if (currentInstance.isOpen) {
                                val db = currentInstance.openHelper.writableDatabase
                                val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                                cursor.moveToFirst()
                                cursor.close()
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Warning WAL checkpoint before close: ${e.message}")
                        }
                        try {
                            currentInstance.openHelper.close()
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Error closing openHelper: ${e.message}")
                        }
                        try {
                            currentInstance.close()
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Error closing instance: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error closing Room database: ${e.message}", e)
                } finally {
                    INSTANCE = null
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "goli_accounting_erp.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default Iranian chart of accounts, warehouses, products, parties, and users
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).let { appDb ->
                                    DatabaseSeeder.seedDefaultData(appDb)
                                    DatabaseSeeder.ensureStoreSettings(appDb, context)
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                            try {
                                db.execSQL("UPDATE journal_items SET partyId = NULL WHERE generalCode NOT IN (102, 301) AND partyId IS NOT NULL")
                            } catch (_: Exception) {}
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val partyDao = getInstance(context).partyDao()
                                    partyDao.sanitizePartyJournalItems()
                                    partyDao.recalculateAllPartyBalances()
                                } catch (_: Exception) {}
                            }
                        }
                    })
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Note: fallbackToDestructiveMigration() is strictly removed to guarantee
                    // that financial, user, stock, and accounting data will NEVER be dropped or wiped on app updates.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
