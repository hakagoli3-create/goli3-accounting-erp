package com.example.data.repository

import com.example.data.dao.TreasuryDao
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.util.JalaliDate
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import kotlinx.coroutines.flow.Flow

class TreasuryRepository(
    private val dao: TreasuryDao,
    private val appDatabase: AppDatabase? = null
) {

    val allCashDesks: Flow<List<CashDeskEntity>> = dao.getAllCashDesks()
    val allBankAccounts: Flow<List<BankAccountEntity>> = dao.getAllBankAccounts()
    val totalCashBalance: Flow<Long?> = dao.getTotalCashBalance()
    val totalBankBalance: Flow<Long?> = dao.getTotalBankBalance()

    val allCheques: Flow<List<ChequeEntity>> = dao.getAllCheques()
    val receivableCheques: Flow<List<ChequeEntity>> = dao.getChequesByType("RECEIVABLE")
    val payableCheques: Flow<List<ChequeEntity>> = dao.getChequesByType("PAYABLE")
    val activeCheques: Flow<List<ChequeEntity>> = dao.getActivePendingCheques()

    fun getDueOrOverdueCheques(cutoffEpoch: Long): Flow<List<ChequeEntity>> =
        dao.getDueOrOverdueCheques(cutoffEpoch)

    fun getDueAlertsCount(): Flow<Int> {
        val sevenDaysAhead = JalaliDate.now().addDays(7).toEpochMillis()
        return dao.getDueOrOverdueChequesCount(sevenDaysAhead)
    }

    suspend fun insertCashDesk(cashDesk: CashDeskEntity): Long = dao.insertCashDesk(cashDesk)
    suspend fun updateCashDesk(cashDesk: CashDeskEntity) = dao.updateCashDesk(cashDesk)
    suspend fun deleteCashDesk(cashDesk: CashDeskEntity) = dao.deleteCashDesk(cashDesk)
    suspend fun updateCashBalance(id: Long, delta: Long) = dao.updateCashBalance(id, delta)

    suspend fun insertBankAccount(account: BankAccountEntity): Long = dao.insertBankAccount(account)
    suspend fun updateBankAccount(account: BankAccountEntity) = dao.updateBankAccount(account)
    suspend fun deleteBankAccount(account: BankAccountEntity) = dao.deleteBankAccount(account)
    suspend fun updateBankBalance(id: Long, delta: Long) = dao.updateBankBalance(id, delta)

    suspend fun insertCheque(cheque: ChequeEntity): Long = dao.insertCheque(cheque)
    suspend fun updateCheque(cheque: ChequeEntity) = dao.updateCheque(cheque)
    suspend fun deleteCheque(cheque: ChequeEntity) = deleteChequeCascading(cheque)

    suspend fun deleteChequeCascading(cheque: ChequeEntity) {
        if (appDatabase != null) {
            appDatabase.withTransaction {
                val accountingDao = appDatabase.accountingDao()
                val partyDao = appDatabase.partyDao()

                // ۱. پاکسازی کلیه اسناد حسابداری ثبت اولیه، وصول یا برگشت این چک
                val matchingEntries = accountingDao.getJournalEntriesByDescriptionPattern(cheque.chequeNumber)
                matchingEntries.forEach { entry ->
                    accountingDao.deleteJournalItemsByVoucher(entry.id)
                    accountingDao.deleteJournalEntry(entry)
                }

                // ۲. در صورتی که چک قبلاً وصول/پاس شده بود، موجودی بانک یا صندوق را معکوس می‌کنیم
                if (cheque.status == "PASSED") {
                    if (cheque.type == "RECEIVABLE") {
                        val firstBank = dao.getFirstBankAccount()
                        if (firstBank != null) {
                            dao.updateBankBalance(firstBank.id, -cheque.amount)
                        }
                    } else {
                        val firstBank = dao.getFirstBankAccount()
                        if (firstBank != null) {
                            dao.updateBankBalance(firstBank.id, cheque.amount)
                        }
                    }
                }

                // ۳. حذف قطعی چک از جدول cheques
                dao.deleteCheque(cheque)

                // ۴. محاسبه مجدد قطعی مانده حساب طرف‌حساب و کل اشخاص در CRM
                try {
                    if (cheque.partyId != null) {
                        partyDao.recalculatePartyBalance(cheque.partyId)
                    }
                    partyDao.recalculateAllPartyBalances()
                } catch (e: Exception) {
                    android.util.Log.e("TreasuryRepo", "Error recalculating party balances after deleteCheque", e)
                }
            }
        } else {
            dao.deleteCheque(cheque)
        }
    }

    suspend fun transferBetweenAccounts(
        fromType: String,
        fromId: Long,
        toType: String,
        toId: Long,
        amount: Long,
        fromName: String? = null,
        toName: String? = null,
        notes: String = "",
        accountingDao: com.example.data.dao.AccountingDao? = null
    ) {
        if (appDatabase != null) {
            appDatabase.withTransaction {
                dao.transferBetweenAccounts(fromType, fromId, toType, toId, amount)
                if (accountingDao != null && fromName != null && toName != null) {
                    val today = JalaliDate.now()
                    val fromSub = if (fromType == "CASH") 10101 else 10102
                    val toSub = if (toType == "CASH") 10101 else 10102
                    val journalItems = listOf(
                        com.example.data.model.JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = toSub,
                            description = "انتقال وجه وارده به $toName بابت جابجایی از $fromName",
                            debit = amount,
                            credit = 0L
                        ),
                        com.example.data.model.JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = fromSub,
                            description = "خروج وجه از $fromName بابت انتقال به $toName",
                            debit = 0L,
                            credit = amount
                        )
                    )
                    val nextVoucherNumber = accountingDao.getMaxVoucherNumber() + 1
                    accountingDao.createVoucherWithItems(
                        com.example.data.model.JournalEntryEntity(
                            voucherNumber = nextVoucherNumber,
                            jalaliDate = today.toStandardString(),
                            description = "انتقال وجه بین حساب‌ها: از $fromName به $toName ${notes.ifBlank { "" }}".trim(),
                            isBalanced = true,
                            status = "FINAL"
                        ),
                        journalItems
                    )
                }
            }
        } else {
            dao.transferBetweenAccounts(fromType, fromId, toType, toId, amount)
        }
    }

    // Multi-Currency Balances
    val allCurrencyBalances: Flow<List<com.example.data.model.TreasuryCurrencyBalanceEntity>> = dao.getAllCurrencyBalances()

    fun getBalancesForAccount(accountType: String, accountId: Long): Flow<List<com.example.data.model.TreasuryCurrencyBalanceEntity>> =
        dao.getBalancesForAccount(accountType, accountId)

    /**
     * به روزرسانی اتمیک موجودی ارزی و پایه صندوق یا حساب بانکی در یک تراکنش یکپارچه
     */
    suspend fun adjustCurrencyBalance(
        accountType: String, // "CASH" or "BANK"
        accountId: Long,
        currencyCode: String,
        delta: Double,
        rateToBase: Double = 1.0
    ) {
        val existing = dao.getCurrencyBalance(accountType, accountId, currencyCode)
        val newBalance = (existing?.balance ?: 0.0) + delta
        val deltaInBase = (delta * rateToBase).toLong()

        dao.adjustCurrencyAndBaseBalance(
            balanceEntity = com.example.data.model.TreasuryCurrencyBalanceEntity(
                id = existing?.id ?: 0L,
                accountType = accountType,
                accountId = accountId,
                currencyCode = currencyCode,
                balance = newBalance
            ),
            accountType = accountType,
            accountId = accountId,
            deltaInBase = deltaInBase
        )
    }
}

