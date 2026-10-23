package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.TreasuryCurrencyBalanceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotalAssetsByCurrencyLiveQueryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
    }

    @Test
    fun testLiveTotalAssetsByCurrencyIncludesCashBankAndForeignCurrencies() = runBlocking {
        val treasuryDao = db.treasuryDao()

        // پاک‌سازی جدول‌های نقدینگی برای اجرای ایزوله تست
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("DELETE FROM treasury_currency_balances")
        sqlite.execSQL("DELETE FROM cash_desks")
        sqlite.execSQL("DELETE FROM bank_accounts")

        // ۱. افزودن صندوق با موجودی اول‌دوره ۵,۰۰۰,۰۰۰ تومان
        val cashDeskId = treasuryDao.insertCashDesk(
            CashDeskEntity(
                code = "CSH-ASSET-01",
                name = "صندوق مرکزی دارایی",
                currentBalance = 5000000L
            )
        )

        // ۲. افزودن حساب بانکی با موجودی اول‌دوره ۲۰,۰۰۰,۰۰۰ تومان
        val bankAccountId = treasuryDao.insertBankAccount(
            BankAccountEntity(
                bankName = "بانک سامان",
                accountNumber = "998811",
                currentBalance = 20000000L
            )
        )

        // ۳. افزودن موجودی‌های ارزی در خزانه‌داری: ۱,۵۰۰ دلار و ۲۵۰ یورو
        val usdId = treasuryDao.insertOrUpdateCurrencyBalance(
            TreasuryCurrencyBalanceEntity(
                id = 0L,
                accountType = "CASH",
                accountId = cashDeskId,
                currencyCode = "USD",
                balance = 1500.0
            )
        )
        val eurId = treasuryDao.insertOrUpdateCurrencyBalance(
            TreasuryCurrencyBalanceEntity(
                id = 0L,
                accountType = "BANK",
                accountId = bankAccountId,
                currencyCode = "EUR",
                balance = 250.0
            )
        )
        // بررسی نتایج کوئری زنده
        val initialCash = treasuryDao.getTotalCashBalance().first() ?: 0L
        val initialBank = treasuryDao.getTotalBankBalance().first() ?: 0L
        val expectedTotalToman = (initialCash + initialBank).toDouble()

        val allBalances = treasuryDao.getAllCurrencyBalances().first()
        val assetsList = treasuryDao.observeTotalAssetsByCurrency().first()
        val assetsMap = assetsList.associate { it.currencyType.uppercase() to it.totalAmount }

        // بررسی همگامی کامل نقدینگی تومانی با بخش خزانه‌داری
        val tomanTotal = assetsMap["TOMAN"] ?: 0.0
        assertEquals(expectedTotalToman, tomanTotal, 0.01)

        // بررسی موجودی دلار
        val usdTotal = assetsMap["USD"] ?: 0.0
        assertEquals(1500.0, usdTotal, 0.01)

        // بررسی موجودی یورو
        val eurTotal = assetsMap["EUR"] ?: 0.0
        assertEquals(250.0, eurTotal, 0.01)

        // ۴. آزمایش جریان زنده (Live Flow): افزایش نقدینگی صندوق به میزان ۳,۰۰۰,۰۰۰ تومان
        treasuryDao.updateCashBalance(cashDeskId, 3000000L)
        val updatedAssetsList = treasuryDao.observeTotalAssetsByCurrency().first()
        val updatedAssetsMap = updatedAssetsList.associate { it.currencyType.uppercase() to it.totalAmount }

        val expectedUpdatedToman = expectedTotalToman + 3000000.0
        assertEquals(expectedUpdatedToman, updatedAssetsMap["TOMAN"] ?: 0.0, 0.01)
    }
}
