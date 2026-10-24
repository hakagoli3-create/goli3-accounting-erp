package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.CashDeskEntity
import com.example.data.model.IncomeCategoryEntity
import com.example.data.model.IncomeEntity
import com.example.data.repository.ExpenseAndIncomeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignCurrencyIncomeIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseAndIncomeRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        repository = ExpenseAndIncomeRepository(
            expenseAndIncomeDao = db.expenseAndIncomeDao(),
            accountingDao = db.accountingDao(),
            treasuryDao = db.treasuryDao(),
            appDatabase = db
        )
    }

    @Test
    fun testForeignCurrencyIncomeUpdatesTreasuryCurrencyBalancesAndKeepsTomanLiquidityIsolated() = runBlocking {
        val treasuryDao = db.treasuryDao()
        val expenseAndIncomeDao = db.expenseAndIncomeDao()

        // Clean tables for isolated execution
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("DELETE FROM incomes")
        sqlite.execSQL("DELETE FROM income_categories")
        sqlite.execSQL("DELETE FROM treasury_currency_balances")
        sqlite.execSQL("DELETE FROM cash_desks")
        sqlite.execSQL("DELETE FROM bank_accounts")
        sqlite.execSQL("DELETE FROM journal_entries")

        // 1. Create a cash desk with id = 999 to guarantee isolation from background seeder
        val cashDeskId = treasuryDao.insertCashDesk(
            CashDeskEntity(
                id = 999L,
                code = "CSH-TEST-99",
                name = "صندوق مرکزی ارزی",
                currentBalance = 10_000_000L
            )
        )
        // Ensure starting balance is 10,000,000
        val desk = treasuryDao.getAllCashDesks().first().find { it.id == cashDeskId }
        val startingBalance = desk?.currentBalance ?: 0L
        if (startingBalance != 10_000_000L) {
            treasuryDao.updateCashBalance(cashDeskId, 10_000_000L - startingBalance)
        }
        val initialCashDesk = treasuryDao.getAllCashDesks().first().find { it.id == cashDeskId }
        assertNotNull(initialCashDesk)
        assertEquals(10_000_000L, initialCashDesk!!.currentBalance)

        // 2. Create an income category
        val categoryId = expenseAndIncomeDao.insertIncomeCategory(
            IncomeCategoryEntity(
                id = 999L,
                code = "INC-CURR",
                title = "درآمد ارزی و صادراتی",
                description = "درآمدهای حاصل از ارائه خدمات بین‌الملل"
            )
        )

        // 3. Register a foreign currency income: 500 USD at 92,000 Toman rate = 46,000,000 Toman equivalent
        val incomeEntity = IncomeEntity(
            title = "وصول حق‌الزحمه پروژه خارجی",
            amount = 46_000_000L,
            categoryId = categoryId,
            categoryTitle = "درآمد ارزی و صادراتی",
            depositMethod = "CASH",
            cashDeskId = cashDeskId,
            bankAccountId = null,
            accountName = "صندوق مرکزی ارزی",
            jalaliDate = "1405/01/15",
            notes = "دریافت اسکناس نقدی 500 دلار",
            currencyCode = "USD",
            currencyAmount = 500.0,
            exchangeRate = 92_000.0
        )

        val createdIncomeId = repository.createIncome(incomeEntity)
        assertTrue(createdIncomeId > 0)

        // Verify stored income properties
        val savedIncome = repository.getIncomeById(createdIncomeId)
        assertNotNull(savedIncome)
        assertEquals("USD", savedIncome!!.currencyCode)
        assertEquals(500.0, savedIncome.currencyAmount, 0.001)
        assertEquals(92_000.0, savedIncome.exchangeRate, 0.001)
        assertEquals(46_000_000L, savedIncome.amount)
        assertTrue(savedIncome.isForeignCurrency)

        // 4. CRITICAL: Verify pure Rial liquidity isolation
        // The cash desk Toman currentBalance must remain strictly untouched (10,000,000 L)
        val cashDeskAfterIncome = treasuryDao.getAllCashDesks().first().find { it.id == cashDeskId }
        assertNotNull(cashDeskAfterIncome)
        assertEquals("Cash desk Toman liquidity must remain pure and untouched", 10_000_000L, cashDeskAfterIncome!!.currentBalance)

        // 5. Verify Treasury currency balance for USD has been created/updated with +500.0
        val usdBalance = treasuryDao.getCurrencyBalance("CASH", cashDeskId, "USD")
        assertNotNull("USD balance must exist in treasury_currency_balances", usdBalance)
        assertEquals(500.0, usdBalance!!.balance, 0.001)

        // 6. Verify Dashboard / Treasury live asset query reflects the 500 USD
        val assetsByCurrency = treasuryDao.observeTotalAssetsByCurrency().first()
        val usdAsset = assetsByCurrency.find { it.currencyType == "USD" }
        assertNotNull("USD must appear in assetsByCurrency", usdAsset)
        assertEquals(500.0, usdAsset!!.totalAmount, 0.001)

        // 7. Verify Updating the Income: e.g. edit currency amount from 500 to 750 USD
        val updatedIncome = savedIncome.copy(
            currencyAmount = 750.0,
            amount = 69_000_000L,
            notes = "اصلاح به 750 دلار"
        )
        repository.updateIncome(savedIncome, updatedIncome)

        val usdBalanceAfterEdit = treasuryDao.getCurrencyBalance("CASH", cashDeskId, "USD")
        assertNotNull(usdBalanceAfterEdit)
        assertEquals(750.0, usdBalanceAfterEdit!!.balance, 0.001)
        assertEquals(10_000_000L, treasuryDao.getAllCashDesks().first().find { it.id == cashDeskId }!!.currentBalance)

        // 8. Verify Deleting the Income reverts the currency balance
        repository.deleteIncome(updatedIncome)
        val usdBalanceAfterDelete = treasuryDao.getCurrencyBalance("CASH", cashDeskId, "USD")
        val remainingUsd = usdBalanceAfterDelete?.balance ?: 0.0
        assertEquals(0.0, remainingUsd, 0.001)
        assertEquals(10_000_000L, treasuryDao.getAllCashDesks().first().find { it.id == cashDeskId }!!.currentBalance)
    }
}
