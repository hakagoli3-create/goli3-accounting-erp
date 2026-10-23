package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.dao.AccountingDao
import com.example.data.dao.PartyDao
import com.example.data.database.AppDatabase
import com.example.data.model.AccountGroupEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.JournalItemWithVoucher
import com.example.data.model.SubsidiaryAccountEntity
import com.example.data.model.TrialBalanceRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * مخزن مدیریت حسابداری مالی چندسطحی، اسناد دوبل و دفاتر حسابداری
 */
class AccountingRepository(
    private val dao: AccountingDao,
    private val appDatabase: AppDatabase? = null
) {

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (appDatabase != null) {
            appDatabase.withTransaction { block() }
        } else {
            block()
        }
    }

    val allGroups: Flow<List<AccountGroupEntity>> = dao.getAllAccountGroups()
    val allGenerals: Flow<List<GeneralAccountEntity>> = dao.getAllGeneralAccounts()
    val allSubsidiaries: Flow<List<SubsidiaryAccountEntity>> = dao.getAllSubsidiaryAccounts()
    val allJournalEntries: Flow<List<JournalEntryEntity>> = dao.getAllJournalEntries()
    val totalDebitSum: Flow<Long?> = dao.getTotalDebitSum()
    val totalCreditSum: Flow<Long?> = dao.getTotalCreditSum()
    val allJournalItemsWithVoucher: Flow<List<JournalItemWithVoucher>> = dao.getJournalItemsWithVoucher()

    fun getGeneralAccountsByGroup(groupCode: Int): Flow<List<GeneralAccountEntity>> =
        dao.getGeneralAccountsByGroup(groupCode)

    fun getSubsidiariesByGeneral(generalCode: Int): Flow<List<SubsidiaryAccountEntity>> =
        dao.getSubsidiaryAccountsByGeneral(generalCode)

    fun getJournalItems(voucherId: Long): Flow<List<JournalItemEntity>> =
        dao.getJournalItemsByVoucher(voucherId)

    suspend fun getJournalItemsSync(voucherId: Long): List<JournalItemEntity> =
        dao.getJournalItemsByVoucherSync(voucherId)

    fun getJournalItemsBySubsidiary(subsidiaryCode: Int): Flow<List<JournalItemEntity>> =
        dao.getJournalItemsBySubsidiary(subsidiaryCode)

    fun getJournalItemsWithVoucherBySubsidiary(subsidiaryCode: Int): Flow<List<JournalItemWithVoucher>> =
        dao.getJournalItemsWithVoucherBySubsidiary(subsidiaryCode)

    fun getJournalItemsWithVoucherByParty(partyId: Long): Flow<List<JournalItemWithVoucher>> =
        dao.getJournalItemsWithVoucherByParty(partyId)

    suspend fun getJournalItemsByVoucherSync(voucherId: Long): List<JournalItemEntity> =
        dao.getJournalItemsByVoucherSync(voucherId)

    suspend fun approveVoucher(voucherId: Long) = runInTransaction {
        val entry = dao.getJournalEntryById(voucherId)
        if (entry != null && entry.status == "VOID") {
            val items = dao.getJournalItemsByVoucherSync(voucherId)
            val partyDao = appDatabase?.partyDao()
            if (partyDao != null) {
                items.forEach { itm ->
                    if (itm.partyId != null) {
                        val net = itm.debit - itm.credit
                        if (net != 0L) {
                            partyDao.updatePartyBalance(itm.partyId, net)
                        }
                    }
                }
            }
        }
        dao.approveJournalEntry(voucherId)
    }

    suspend fun updateVoucherStatus(voucherId: Long, status: String) = runInTransaction {
        dao.updateJournalEntryStatus(voucherId, status)
    }

    suspend fun insertGeneralAccount(account: GeneralAccountEntity): Result<Long> = runCatching {
        runInTransaction {
            dao.insertGeneralAccount(account)
        }
    }

    suspend fun updateGeneralAccount(account: GeneralAccountEntity): Result<Unit> = runCatching {
        runInTransaction {
            dao.updateGeneralAccount(account)
        }
    }

    suspend fun deleteGeneralAccount(account: GeneralAccountEntity): Result<Unit> = runCatching {
        runInTransaction {
            dao.deleteGeneralAccount(account)
        }
    }

    suspend fun insertSubsidiaryAccount(account: SubsidiaryAccountEntity): Result<Long> = runCatching {
        runInTransaction {
            dao.insertSubsidiaryAccount(account)
        }
    }

    suspend fun updateSubsidiaryAccount(account: SubsidiaryAccountEntity): Result<Unit> = runCatching {
        runInTransaction {
            dao.updateSubsidiaryAccount(account)
        }
    }

    suspend fun deleteSubsidiaryAccount(account: SubsidiaryAccountEntity): Result<Unit> = runCatching {
        runInTransaction {
            dao.deleteSubsidiaryAccount(account)
        }
    }

    /**
     * ثبت سند دوبل حسابداری با اعتبارسنجی تراز بودن (مجموع بدهکار = مجموع بستانکار)
     * و اعمال اتوماتیک گردش و مانده حساب طرف‌حساب‌ها (حساب‌های تفصیلی)
     */
    suspend fun createJournalVoucher(
        entry: JournalEntryEntity,
        items: List<JournalItemEntity>
    ): Result<Long> {
        val totalDebit = items.sumOf { it.debit }
        val totalCredit = items.sumOf { it.credit }

        if (totalDebit != totalCredit) {
            return Result.failure(
                IllegalArgumentException("سند تراز نیست! مجموع بدهکار ($totalDebit) با مجموع بستانکار ($totalCredit) برابر نمی‌باشد.")
            )
        }

        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("سند باید حداقل شامل ۲ سطر آرتیکل باشد."))
        }

        val voucherId = runInTransaction {
            val vId = dao.createVoucherWithItems(entry.copy(isBalanced = true), items)
            // اعمال تغییرات مانده اشخاص متصل به آرتیکل‌های سند
            val partyDao = appDatabase?.partyDao()
            if (partyDao != null) {
                items.forEach { itm ->
                    if (itm.partyId != null) {
                        val net = itm.debit - itm.credit
                        if (net != 0L) {
                            partyDao.updatePartyBalance(itm.partyId, net)
                        }
                    }
                }
            }
            vId
        }
        return Result.success(voucherId)
    }

    /**
     * گزارش تراز آزمایشی معین (۴ ستونه / ۲ ستونه) بر مبنای اسناد معتبر و فعال (غیرباطل)
     */
     val trialBalance: Flow<List<TrialBalanceRow>> = combine(
         dao.getAllSubsidiaryAccounts(),
         dao.getAllGeneralAccounts(),
         dao.getAllAccountGroups(),
         dao.getActiveJournalItems()
     ) { subs, generals, groups, items ->
         val generalMap = generals.associateBy { it.code }
         val groupMap = groups.associateBy { it.code }

         subs.map { sub ->
             val gen = generalMap[sub.generalCode]
             val grp = gen?.let { groupMap[it.groupCode] }

             val subItems = items.filter { it.subsidiaryCode == sub.code }
             val totalDebit = subItems.sumOf { it.debit }
             val totalCredit = subItems.sumOf { it.credit }

             val balance = totalDebit - totalCredit
             val balanceDebit = if (balance > 0) balance else 0L
             val balanceCredit = if (balance < 0) -balance else 0L

             TrialBalanceRow(
                 subsidiaryCode = sub.code,
                 title = sub.title,
                 generalTitle = gen?.title ?: "نامشخص",
                 groupTitle = grp?.title ?: "نامشخص",
                 totalDebit = totalDebit,
                 totalCredit = totalCredit,
                 balanceDebit = balanceDebit,
                 balanceCredit = balanceCredit
             )
         }.filter { it.totalDebit > 0 || it.totalCredit > 0 }
     }

    /**
     * گزارش تراز آزمایشی در سطح حساب‌های کل بر مبنای اسناد فعال
     */
     val generalTrialBalance: Flow<List<com.example.data.model.GeneralTrialBalanceRow>> = combine(
         dao.getAllGeneralAccounts(),
         dao.getAllAccountGroups(),
         dao.getActiveJournalItems()
     ) { generals, groups, items ->
         val groupMap = groups.associateBy { it.code }

         generals.map { gen ->
             val grp = groupMap[gen.groupCode]
             val genItems = items.filter { it.generalCode == gen.code }
             val totalDebit = genItems.sumOf { it.debit }
             val totalCredit = genItems.sumOf { it.credit }

             val balance = totalDebit - totalCredit
             val balanceDebit = if (balance > 0) balance else 0L
             val balanceCredit = if (balance < 0) -balance else 0L

             com.example.data.model.GeneralTrialBalanceRow(
                 generalCode = gen.code,
                 title = gen.title,
                 groupTitle = grp?.title ?: "نامشخص",
                 totalDebit = totalDebit,
                 totalCredit = totalCredit,
                 balanceDebit = balanceDebit,
                 balanceCredit = balanceCredit
             )
         }.filter { it.totalDebit > 0 || it.totalCredit > 0 }
     }

    suspend fun voidVoucher(voucherId: Long) = runInTransaction {
        val entry = dao.getJournalEntryById(voucherId)
        if (entry != null && entry.status != "VOID") {
            val items = dao.getJournalItemsByVoucherSync(voucherId)
            val partyDao = appDatabase?.partyDao()
            if (partyDao != null) {
                items.forEach { itm ->
                    if (itm.partyId != null) {
                        val net = itm.debit - itm.credit
                        if (net != 0L) {
                            partyDao.updatePartyBalance(itm.partyId, -net)
                        }
                    }
                }
            }
            dao.voidJournalEntry(voucherId)
        }
    }

    suspend fun updateVoucher(entry: JournalEntryEntity) = runInTransaction {
        dao.updateJournalEntry(entry)
    }

    suspend fun deleteVoucher(voucherId: Long) = runInTransaction {
        val entry = dao.getJournalEntryById(voucherId)
        if (entry != null) {
            val items = dao.getJournalItemsByVoucherSync(voucherId)
            val partyDao = appDatabase?.partyDao()
            val expenseDao = appDatabase?.expenseAndIncomeDao()
            val treasuryDao = appDatabase?.treasuryDao()

            // ۱. پاکسازی رکوردهای وابسته به سند در جدول هزینه‌ها و درآمدها و برگشت مانده خزانه‌داری
            var hasHandledTreasury = false
            if (expenseDao != null && treasuryDao != null) {
                val linkedExpenses = expenseDao.getExpensesByVoucherId(voucherId)
                if (linkedExpenses.isNotEmpty()) {
                    hasHandledTreasury = true
                    linkedExpenses.forEach { exp ->
                        if (exp.paymentMethod == "CASH" && exp.cashDeskId != null) {
                            treasuryDao.updateCashBalance(exp.cashDeskId, exp.amount)
                        } else if (exp.paymentMethod == "BANK" && exp.bankAccountId != null) {
                            treasuryDao.updateBankBalance(exp.bankAccountId, exp.amount)
                        }
                        expenseDao.deleteExpense(exp)
                    }
                }

                val linkedIncomes = expenseDao.getIncomesByVoucherId(voucherId)
                if (linkedIncomes.isNotEmpty()) {
                    hasHandledTreasury = true
                    linkedIncomes.forEach { inc ->
                        if (inc.depositMethod == "CASH" && inc.cashDeskId != null) {
                            treasuryDao.updateCashBalance(inc.cashDeskId, -inc.amount)
                        } else if (inc.depositMethod == "BANK" && inc.bankAccountId != null) {
                            treasuryDao.updateBankBalance(inc.bankAccountId, -inc.amount)
                        }
                        expenseDao.deleteIncome(inc)
                    }
                }
            }

            // ۲. اگر سند انتقال یا مستقیم نقدی بوده و خارج از جدول هزینه/درآمد است، اثر خزانه‌داری آن معکوس می‌شود
            if (!hasHandledTreasury && entry.status != "VOID" && treasuryDao != null) {
                val cashItems = items.filter { it.subsidiaryCode == 10101 }
                val bankItems = items.filter { it.subsidiaryCode == 10102 }

                cashItems.forEach { ci ->
                    val delta = ci.debit - ci.credit
                    if (delta != 0L) {
                        val firstCash = treasuryDao.getFirstCashDesk()
                        if (firstCash != null) treasuryDao.updateCashBalance(firstCash.id, -delta)
                    }
                }

                bankItems.forEach { bi ->
                    val delta = bi.debit - bi.credit
                    if (delta != 0L) {
                        val firstBank = treasuryDao.getFirstBankAccount()
                        if (firstBank != null) treasuryDao.updateBankBalance(firstBank.id, -delta)
                    }
                }
            }

            // ۳. حذف قطعی سطرهای آرتیکل و هدر سند حسابداری
            dao.deleteJournalItemsByVoucher(voucherId)
            dao.deleteJournalEntry(entry)

            // ۴. محاسبه مجدد قطعی مانده حساب تمامی اشخاص بر مبنای اسناد رسمی معتبر بدون هیچ‌گونه خطا یا داده یتیم
            try {
                partyDao?.recalculateAllPartyBalances()
            } catch (e: Exception) {
                android.util.Log.e("AccountingRepository", "Error recalculating party balances after voucher deletion", e)
            }
        }
    }

    /**
     * ویرایش کامل سند حسابداری به همراه سطرهای آرتیکل و هماهنگ‌سازی اتمیک تمامی وابستگی‌ها و مانده‌ها
     */
    suspend fun updateFullJournalVoucher(
        voucherId: Long,
        updatedDescription: String,
        updatedDate: String,
        updatedItems: List<JournalItemEntity>
    ): Result<Unit> {
        val totalDebit = updatedItems.sumOf { it.debit }
        val totalCredit = updatedItems.sumOf { it.credit }

        if (totalDebit != totalCredit) {
            return Result.failure(
                IllegalArgumentException("سند تراز نیست! مجموع بدهکار ($totalDebit) با مجموع بستانکار ($totalCredit) برابر نمی‌باشد.")
            )
        }
        if (updatedItems.size < 2) {
            return Result.failure(IllegalArgumentException("سند باید حداقل شامل ۲ سطر آرتیکل باشد."))
        }

        return runCatching {
            runInTransaction {
                val existingEntry = dao.getJournalEntryById(voucherId)
                    ?: throw IllegalArgumentException("سند با شناسه $voucherId یافت نشد.")

                val oldItems = dao.getJournalItemsByVoucherSync(voucherId)
                val partyDao = appDatabase?.partyDao()

                // ۱. بازگشت اثر مالی قبلی بر مانده طرف‌حساب‌ها (در صورت فعال بودن سند)
                if (existingEntry.status != "VOID" && partyDao != null) {
                    oldItems.forEach { itm ->
                        if (itm.partyId != null) {
                            val oldNet = itm.debit - itm.credit
                            if (oldNet != 0L) {
                                partyDao.updatePartyBalance(itm.partyId, -oldNet)
                            }
                        }
                    }
                }

                // ۲. حذف آرتیکل‌های قدیمی و ثبت آرتیکل‌های جدید
                dao.deleteJournalItemsByVoucher(voucherId)
                val attachedItems = updatedItems.map { it.copy(id = 0L, voucherId = voucherId) }
                dao.insertJournalItems(attachedItems)

                // ۳. اعمال اثر مالی جدید بر مانده طرف‌حساب‌ها
                if (existingEntry.status != "VOID" && partyDao != null) {
                    attachedItems.forEach { itm ->
                        if (itm.partyId != null) {
                            val newNet = itm.debit - itm.credit
                            if (newNet != 0L) {
                                partyDao.updatePartyBalance(itm.partyId, newNet)
                            }
                        }
                    }
                }

                // ۴. به‌روزرسانی سربرگ سند
                dao.updateJournalEntry(
                    existingEntry.copy(
                        description = updatedDescription,
                        jalaliDate = updatedDate,
                        isBalanced = true
                    )
                )
            }
        }
    }

    suspend fun updateJournalItem(item: JournalItemEntity) = runInTransaction {
        dao.updateJournalItem(item)
    }

    suspend fun deleteJournalItem(itemId: Long) = runInTransaction {
        val item = dao.getJournalItemById(itemId)
        if (item != null) {
            dao.deleteJournalItem(item)
        }
    }

    /**
     * ثبت گردش حساب شخص در دفتر معین به صورت اتمیک همراه با به‌روزرسانی مانده طرف‌حساب
     */
    suspend fun addPartyTransaction(
        partyId: Long,
        description: String,
        debit: Long,
        credit: Long,
        jalaliDate: String,
        partyDao: PartyDao? = null
    ): Long = runInTransaction {
        val nextNumber = dao.getMaxVoucherNumber() + 1
        val voucher = JournalEntryEntity(
            voucherNumber = nextNumber,
            jalaliDate = jalaliDate,
            description = "سند معین طرف حساب: $description",
            isBalanced = true,
            status = "FINAL"
        )
        val voucherId = dao.insertJournalEntry(voucher)
        val items = mutableListOf<JournalItemEntity>()

        // ردیف معین شخص (حساب‌های دریافتنی)
        items.add(
            JournalItemEntity(
                voucherId = voucherId,
                generalCode = 102,
                subsidiaryCode = 10201,
                partyId = partyId,
                description = description,
                debit = debit,
                credit = credit
            )
        )

        // ردیف مقابل برای حفظ توازن دوبل (صندوق یا حساب جاری)
        items.add(
            JournalItemEntity(
                voucherId = voucherId,
                generalCode = 101,
                subsidiaryCode = 10101,
                partyId = null,
                description = "طرف مقابل سند معین: $description",
                debit = credit,
                credit = debit
            )
        )

        dao.insertJournalItems(items)

        if (partyDao != null) {
            val netEffect = debit - credit
            partyDao.updatePartyBalance(partyId, netEffect)
        }

        voucherId
    }

    suspend fun updatePartyTransaction(
        item: JournalItemEntity,
        newDescription: String,
        newDebit: Long,
        newCredit: Long,
        partyDao: PartyDao? = null
    ) = runInTransaction {
        val oldNet = item.debit - item.credit
        val newNet = newDebit - newCredit
        val diff = newNet - oldNet
        dao.updateJournalItem(item.copy(description = newDescription, debit = newDebit, credit = newCredit))

        // به‌روزرسانی آرتیکل متقابل جهت حفظ توازن دقیق دوبل حسابداری (Debit = Credit)
        val voucherId = item.voucherId
        val voucherItems = dao.getJournalItemsByVoucherSync(voucherId)
        if (voucherItems.size == 2) {
            val counterItem = voucherItems.firstOrNull { it.id != item.id }
            if (counterItem != null) {
                dao.updateJournalItem(
                    counterItem.copy(
                        description = "طرف مقابل سند معین: $newDescription",
                        debit = newCredit,
                        credit = newDebit
                    )
                )
            }
        }

        if (partyDao != null && item.partyId != null && diff != 0L) {
            partyDao.updatePartyBalance(item.partyId, diff)
        }
    }

    suspend fun deletePartyTransaction(
        item: JournalItemEntity,
        partyDao: PartyDao? = null
    ) = runInTransaction {
        val voucherId = item.voucherId
        val voucherItems = dao.getJournalItemsByVoucherSync(voucherId)
        if (voucherItems.size <= 2 && voucherItems.any { it.partyId == item.partyId }) {
            // حذف کامل سند و تمام آرتیکل‌های مرتبط با آن
            dao.deleteJournalItemsByVoucher(voucherId)
            dao.deleteJournalEntryById(voucherId)
        } else {
            dao.deleteJournalItem(item)
        }
        if (partyDao != null && item.partyId != null) {
            val reverse = -(item.debit - item.credit)
            partyDao.updatePartyBalance(item.partyId, reverse)
        }
    }

    fun getJournalItemsByParty(partyId: Long): Flow<List<JournalItemEntity>> =
        dao.getJournalItemsByParty(partyId)
}
