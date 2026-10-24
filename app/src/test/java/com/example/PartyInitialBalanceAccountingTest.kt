package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.DatabaseSeeder
import com.example.data.model.PartyEntity
import com.example.data.repository.PartyRepository
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
class PartyInitialBalanceAccountingTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var partyRepo: PartyRepository

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        DatabaseSeeder.seedDefaultData(db)
        partyRepo = PartyRepository(
            dao = db.partyDao(),
            accountingDao = db.accountingDao(),
            appDatabase = db
        )
    }

    @Test
    fun testAddPartyWithDebitInitialBalanceCreatesAccountingVoucherAndUpdatesBalance() = runBlocking {
        // ۱. ایجاد مشتری با مانده اول دوره بدهکار (طلب ما = ۱۰ میلیون تومان)
        val customer = PartyEntity(
            code = "CUST-INIT-01",
            name = "رضا باقری (تست افتتاحیه بدهکار)",
            partyType = "CUSTOMER",
            initialBalance = 10_000_000L,
            initialBalanceType = "DEBIT",
            creditLimit = 50_000_000L
        )

        val partyId = partyRepo.insertParty(customer)
        assertTrue(partyId > 0)

        // بررسی ذخیره در جدول parties
        val savedParty = partyRepo.getPartyById(partyId)
        assertNotNull(savedParty)
        assertEquals(10_000_000L, savedParty!!.initialBalance)
        assertEquals("DEBIT", savedParty.initialBalanceType)
        // مانده جاری حساب باید بلافاصله ۱۰ میلیون تومان مثبت (طلب ما) شود
        assertEquals(10_000_000L, savedParty.currentBalance)

        // بررسی ایجاد سند حسابداری اتوماتیک
        val accountingDao = db.accountingDao()
        val vouchers = accountingDao.getJournalEntriesByDescriptionPattern("[افتتاحیه طرف‌حساب #$partyId]")
        assertEquals(1, vouchers.size)

        val voucher = vouchers[0]
        assertEquals("FINAL", voucher.status)
        assertTrue(voucher.isBalanced)

        // بررسی آرتیکل‌های سند
        val items = accountingDao.getJournalItemsByVoucherSync(voucher.id)
        assertEquals(2, items.size)

        val debtorItem = items.firstOrNull { it.subsidiaryCode == 10201 }
        val equityItem = items.firstOrNull { it.subsidiaryCode == 50101 }

        assertNotNull(debtorItem)
        assertNotNull(equityItem)

        // ردیف بدهکاران تجاری: بدهکار = ۱۰ میلیون تومان و متعلق به کد تفصیلی شخص
        assertEquals(partyId, debtorItem!!.partyId)
        assertEquals(10_000_000L, debtorItem.debit)
        assertEquals(0L, debtorItem.credit)

        // ردیف سرمایه اولیه/افتتاحیه: بستانکار = ۱۰ میلیون تومان
        assertEquals(0L, equityItem!!.debit)
        assertEquals(10_000_000L, equityItem.credit)

        // بررسی مطالبات کل در KPI صفحه CRM
        val totalRec = partyRepo.totalReceivables.first()
        assertTrue(totalRec >= 10_000_000L)
    }

    @Test
    fun testUpdatePartyToCreditInitialBalanceReplacesVoucherAndUpdatesPayables() = runBlocking {
        // ۱. ابتدا ایجاد تامین‌کننده
        val supplier = PartyEntity(
            code = "SUPP-INIT-01",
            name = "شرکت پایا (تست افتتاحیه)",
            partyType = "SUPPLIER",
            initialBalance = 5_000_000L,
            initialBalanceType = "DEBIT"
        )
        val partyId = partyRepo.insertParty(supplier)

        // ۲. سپس ویرایش شخص و تغییر مانده اول دوره به بستانکار (بدهی ما = ۲۰ میلیون تومان)
        val updatedSupplier = partyRepo.getPartyById(partyId)!!.copy(
            initialBalance = 20_000_000L,
            initialBalanceType = "CREDIT"
        )
        partyRepo.updateParty(updatedSupplier)

        // بررسی جایگزینی بدون تکرار سند (تنها یک سند افتتاحیه برای این شخص باید موجود باشد)
        val accountingDao = db.accountingDao()
        val vouchers = accountingDao.getJournalEntriesByDescriptionPattern("[افتتاحیه طرف‌حساب #$partyId]")
        assertEquals(1, vouchers.size)

        val items = accountingDao.getJournalItemsByVoucherSync(vouchers[0].id)
        assertEquals(2, items.size)

        val creditorItem = items.firstOrNull { it.subsidiaryCode == 30101 }
        val equityItem = items.firstOrNull { it.subsidiaryCode == 50101 }

        assertNotNull(creditorItem)
        assertNotNull(equityItem)

        // ردیف بستانکاران تجاری: بستانکار = ۲۰ میلیون تومان برای طرف‌حساب
        assertEquals(partyId, creditorItem!!.partyId)
        assertEquals(0L, creditorItem.debit)
        assertEquals(20_000_000L, creditorItem.credit)

        // ردیف سرمایه/افتتاحیه: بدهکار = ۲۰ میلیون تومان
        assertEquals(20_000_000L, equityItem!!.debit)
        assertEquals(0L, equityItem.credit)

        // بررسی مانده جاری شخص: باید منفی ۲۰ میلیون باشد (بستانکار / بدهی ما)
        val reloadedParty = partyRepo.getPartyById(partyId)
        assertNotNull(reloadedParty)
        assertEquals(-20_000_000L, reloadedParty!!.currentBalance)

        // بررسی کارد بدهی به تامین‌کنندگان در CRM
        val supplierPayables = partyRepo.supplierPayables.first()
        assertTrue(supplierPayables >= 20_000_000L)
    }

    @Test
    fun testDeletePartyCleansUpOpeningVoucher() = runBlocking {
        val party = PartyEntity(
            code = "PRT-DEL-01",
            name = "مشتری حذفی",
            partyType = "CUSTOMER",
            initialBalance = 7_000_000L,
            initialBalanceType = "DEBIT"
        )
        val partyId = partyRepo.insertParty(party)

        val accountingDao = db.accountingDao()
        val vouchersBefore = accountingDao.getJournalEntriesByDescriptionPattern("[افتتاحیه طرف‌حساب #$partyId]")
        assertEquals(1, vouchersBefore.size)

        // حذف شخص
        val saved = partyRepo.getPartyById(partyId)!!
        partyRepo.deleteParty(saved)

        // بررسی پاک‌سازی قطعی سند افتتاحیه
        val vouchersAfter = accountingDao.getJournalEntriesByDescriptionPattern("[افتتاحیه طرف‌حساب #$partyId]")
        assertEquals(0, vouchersAfter.size)
    }
}
