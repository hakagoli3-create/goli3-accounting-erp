package com.example.data.repository

import com.example.data.dao.AccountingDao
import com.example.data.dao.PartyDao
import com.example.data.database.AppDatabase
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.PartyEntity
import com.example.util.JalaliDate
import kotlinx.coroutines.flow.Flow

class PartyRepository(
    val dao: PartyDao,
    val accountingDao: AccountingDao? = null,
    val appDatabase: AppDatabase? = null
) {

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties()
    val allCustomers: Flow<List<PartyEntity>> = dao.getAllCustomers()
    val allSuppliers: Flow<List<PartyEntity>> = dao.getAllSuppliers()
    val overCreditLimitCount: Flow<Int> = dao.getOverCreditLimitCount()
    val totalReceivables: Flow<Long> = dao.getTotalReceivables()
    val totalPayables: Flow<Long> = dao.getTotalPayables()
    val customerReceivables: Flow<Long> = dao.getCustomerReceivables()
    val supplierPayables: Flow<Long> = dao.getSupplierPayables()
    val customerPayables: Flow<Long> = dao.getCustomerPayables()
    val supplierReceivables: Flow<Long> = dao.getSupplierReceivables()

    fun searchParties(query: String): Flow<List<PartyEntity>> = dao.searchParties(query)

    suspend fun getPartyById(id: Long): PartyEntity? = dao.getPartyById(id)

    suspend fun insertParty(party: PartyEntity): Long {
        val insertedId = dao.insertParty(party)
        val partyWithId = if (party.id == 0L) party.copy(id = insertedId) else party
        syncOpeningBalanceVoucher(partyWithId)
        return insertedId
    }

    suspend fun updateParty(party: PartyEntity) {
        dao.updateParty(party)
        syncOpeningBalanceVoucher(party)
    }

    suspend fun deleteParty(party: PartyEntity) {
        clearOpeningBalanceVoucher(party.id)
        dao.deleteParty(party)
    }

    suspend fun adjustBalance(partyId: Long, delta: Long) =
        dao.updatePartyBalance(partyId, delta)

    suspend fun updatePartyBalance(partyId: Long, delta: Long) =
        dao.updatePartyBalance(partyId, delta)

    suspend fun sanitizePartyJournalItems() = dao.sanitizePartyJournalItems()

    suspend fun recalculatePartyBalance(partyId: Long) {
        dao.sanitizePartyJournalItems()
        dao.recalculatePartyBalance(partyId)
    }

    suspend fun recalculateAllPartyBalances() {
        dao.sanitizePartyJournalItems()
        dao.recalculateAllPartyBalances()
    }

    /**
     * همگام‌سازی و صدور اتوماتیک سند افتتاحیه برای مانده اول دوره طرف حساب
     * شناسه طرف‌حساب به صورت دقیق در تگ شرح سند ثبت می‌شود تا هنگام ویرایش یا حذف،
     * سند تکراری تولید نشود (Idempotency).
     */
    suspend fun syncOpeningBalanceVoucher(party: PartyEntity) {
        val accDao = accountingDao ?: return
        val pattern = "[افتتاحیه طرف‌حساب #${party.id}]"

        try {
            // ۱. پاک‌سازی قطعی اسناد افتتاحیه قبلی صادر شده برای این طرف‌حساب
            accDao.deleteJournalItemsByDescriptionPattern(pattern)
            accDao.deleteJournalEntriesByDescriptionPattern(pattern)

            // ۲. اگر مانده اول دوره مثبت ثبت شده باشد، سند افتتاحیه دوطرفه تراز صادر می‌شود
            if (party.initialBalance > 0L) {
                val isDebit = party.initialBalanceType.equals("DEBIT", ignoreCase = true)
                val todayStr = JalaliDate.now().toStandardString()
                val voucherDesc = "$pattern سند مانده اول دوره: ${party.name} (${party.code})"

                val items = if (isDebit) {
                    // بدهکار (طلب ما):
                    // بدهکار: حساب‌های دریافتنی تجاری - بدهکاران تجاری (تفصیلی شخص)
                    // بستانکار: سرمایه اولیه / تراز افتتاحیه موسسه
                    listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 102,
                            subsidiaryCode = 10201,
                            partyId = party.id,
                            description = "مانده اول دوره (طلب ما) - ${party.name}",
                            debit = party.initialBalance,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 501,
                            subsidiaryCode = 50101,
                            partyId = null,
                            description = "سرمایه اولیه / تراز افتتاحیه - مانده اول دوره ${party.name}",
                            debit = 0L,
                            credit = party.initialBalance
                        )
                    )
                } else {
                    // بستانکار (بدهی ما):
                    // بدهکار: سرمایه اولیه / تراز افتتاحیه موسسه
                    // بستانکار: حساب‌های پرداختنی تجاری - بستانکاران تجاری (تفصیلی شخص)
                    listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 501,
                            subsidiaryCode = 50101,
                            partyId = null,
                            description = "سرمایه اولیه / تراز افتتاحیه - مانده اول دوره ${party.name}",
                            debit = party.initialBalance,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 301,
                            subsidiaryCode = 30101,
                            partyId = party.id,
                            description = "مانده اول دوره (بدهی ما) - ${party.name}",
                            debit = 0L,
                            credit = party.initialBalance
                        )
                    )
                }

                val entry = JournalEntryEntity(
                    id = 0L,
                    voucherNumber = 0L,
                    jalaliDate = todayStr,
                    timestamp = System.currentTimeMillis(),
                    description = voucherDesc,
                    isBalanced = true,
                    status = "FINAL"
                )

                accDao.createVoucherWithItems(entry, items)
            }

            // ۳. به‌روزرسانی آنی و دقیق مانده حساب شخص در جدول parties بر مبنای دفتر معین
            recalculatePartyBalance(party.id)
        } catch (e: Exception) {
            android.util.Log.e("PartyRepository", "Error syncing opening balance voucher: ${e.message}", e)
        }
    }

    private suspend fun clearOpeningBalanceVoucher(partyId: Long) {
        val accDao = accountingDao ?: return
        val pattern = "[افتتاحیه طرف‌حساب #$partyId]"
        try {
            accDao.deleteJournalItemsByDescriptionPattern(pattern)
            accDao.deleteJournalEntriesByDescriptionPattern(pattern)
        } catch (e: Exception) {
            android.util.Log.e("PartyRepository", "Error clearing opening voucher: ${e.message}", e)
        }
    }
}
