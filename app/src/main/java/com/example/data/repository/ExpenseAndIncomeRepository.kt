package com.example.data.repository

import com.example.data.dao.AccountingDao
import com.example.data.dao.ExpenseAndIncomeDao
import com.example.data.dao.TreasuryDao
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IncomeCategoryEntity
import com.example.data.model.IncomeEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.util.NumberFormatUtils
import kotlinx.coroutines.flow.Flow

class ExpenseAndIncomeRepository(
    private val expenseAndIncomeDao: ExpenseAndIncomeDao,
    private val treasuryDao: TreasuryDao,
    private val accountingDao: AccountingDao,
    private val appDatabase: AppDatabase? = null
) {

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (appDatabase != null) {
            appDatabase.withTransaction { block() }
        } else {
            block()
        }
    }

    // --- Expense Categories ---
    val allExpenseCategories: Flow<List<ExpenseCategoryEntity>> =
        expenseAndIncomeDao.getAllExpenseCategories()

    suspend fun insertExpenseCategory(category: ExpenseCategoryEntity): Long =
        expenseAndIncomeDao.insertExpenseCategory(category)

    suspend fun updateExpenseCategory(category: ExpenseCategoryEntity) =
        expenseAndIncomeDao.updateExpenseCategory(category)

    suspend fun deleteExpenseCategory(category: ExpenseCategoryEntity) =
        expenseAndIncomeDao.deleteExpenseCategory(category)

    // --- Expenses ---
    val allExpenses: Flow<List<ExpenseEntity>> =
        expenseAndIncomeDao.getAllExpenses()

    val totalExpenses: Flow<Long?> =
        expenseAndIncomeDao.getTotalExpenses()

    fun getTodayExpenses(today: String): Flow<Long> =
        expenseAndIncomeDao.getTodayExpenses(today)

    fun getExpensesBetweenDates(startDate: String, endDate: String): Flow<List<ExpenseEntity>> =
        expenseAndIncomeDao.getExpensesBetweenDates(startDate, endDate)

    suspend fun createExpense(expense: ExpenseEntity): Long = runInTransaction {
        // 1. Create a double-entry accounting journal voucher
        val voucherId = try {
            val entry = JournalEntryEntity(
                voucherNumber = 0L,
                jalaliDate = expense.jalaliDate,
                timestamp = expense.timestamp,
                description = "هزینه: ${expense.categoryTitle} - ${expense.title}",
                isBalanced = true,
                status = "FINAL"
            )
            // Subsidiary accounts:
            // 80101 (هزینه عمومی) -> Debit (بدهکار)
            // 10101 (صندوق) or 10102 (بانک) -> Credit (بستانکار)
            val creditSubCode = if (expense.paymentMethod == "CASH") 10101 else 10102
            val creditGenCode = 101

            val items = listOf(
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 801,
                    subsidiaryCode = 80101,
                    description = "هزینه ${expense.categoryTitle} - ${expense.title}",
                    debit = expense.amount,
                    credit = 0L
                ),
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = creditGenCode,
                    subsidiaryCode = creditSubCode,
                    description = "پرداخت بابت ${expense.title} از ${expense.accountName}",
                    debit = 0L,
                    credit = expense.amount
                )
            )
            accountingDao.createVoucherWithItems(entry, items)
        } catch (e: Exception) {
            null
        }

        // 2. Adjust Treasury Balance (Subtract amount)
        if (expense.paymentMethod == "CASH" && expense.cashDeskId != null) {
            treasuryDao.updateCashBalance(expense.cashDeskId, -expense.amount)
        } else if (expense.paymentMethod == "BANK" && expense.bankAccountId != null) {
            treasuryDao.updateBankBalance(expense.bankAccountId, -expense.amount)
        }

        // 3. Save Expense Entity
        expenseAndIncomeDao.insertExpense(expense.copy(voucherId = voucherId))
    }

    suspend fun updateExpense(oldExpense: ExpenseEntity, newExpense: ExpenseEntity) = runInTransaction {
        // Revert old treasury balance effect
        if (oldExpense.paymentMethod == "CASH" && oldExpense.cashDeskId != null) {
            treasuryDao.updateCashBalance(oldExpense.cashDeskId, oldExpense.amount)
        } else if (oldExpense.paymentMethod == "BANK" && oldExpense.bankAccountId != null) {
            treasuryDao.updateBankBalance(oldExpense.bankAccountId, oldExpense.amount)
        }

        // Apply new treasury balance effect
        if (newExpense.paymentMethod == "CASH" && newExpense.cashDeskId != null) {
            treasuryDao.updateCashBalance(newExpense.cashDeskId, -newExpense.amount)
        } else if (newExpense.paymentMethod == "BANK" && newExpense.bankAccountId != null) {
            treasuryDao.updateBankBalance(newExpense.bankAccountId, -newExpense.amount)
        }

        // Update connected journal voucher if it exists
        if (oldExpense.voucherId != null) {
            try {
                accountingDao.deleteJournalItemsByVoucher(oldExpense.voucherId)
                val creditSubCode = if (newExpense.paymentMethod == "CASH") 10101 else 10102
                val items = listOf(
                    JournalItemEntity(
                        voucherId = oldExpense.voucherId,
                        generalCode = 801,
                        subsidiaryCode = 80101,
                        description = "هزینه ${newExpense.categoryTitle} - ${newExpense.title}",
                        debit = newExpense.amount,
                        credit = 0L
                    ),
                    JournalItemEntity(
                        voucherId = oldExpense.voucherId,
                        generalCode = 101,
                        subsidiaryCode = creditSubCode,
                        description = "پرداخت بابت ${newExpense.title} از ${newExpense.accountName}",
                        debit = 0L,
                        credit = newExpense.amount
                    )
                )
                accountingDao.insertJournalItems(items)
            } catch (e: Exception) {
                // Ignore if journal update fails
            }
        }

        expenseAndIncomeDao.updateExpense(newExpense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = runInTransaction {
        // ۱. بازگشت مانده خزانه‌داری
        if (expense.paymentMethod == "CASH" && expense.cashDeskId != null) {
            treasuryDao.updateCashBalance(expense.cashDeskId, expense.amount)
        } else if (expense.paymentMethod == "BANK" && expense.bankAccountId != null) {
            treasuryDao.updateBankBalance(expense.bankAccountId, expense.amount)
        }

        // ۲. حذف کامل سند حسابداری متصل و تمام آرتیکل‌های آن (حذف قطعی رکوردهای یتیم)
        var affectedPartyId: Long? = null
        if (expense.voucherId != null) {
            try {
                val items = accountingDao.getJournalItemsByVoucherSync(expense.voucherId)
                affectedPartyId = items.firstOrNull { it.partyId != null }?.partyId
                accountingDao.deleteJournalItemsByVoucher(expense.voucherId)
                accountingDao.deleteJournalEntryById(expense.voucherId)
            } catch (e: Exception) {
                android.util.Log.e("ExpenseAndIncomeRepo", "Error deleting voucher ${expense.voucherId} for expense", e)
            }
        } else if (expense.title.isNotBlank()) {
            try {
                accountingDao.deleteJournalItemsByDescriptionPattern(expense.title)
                accountingDao.deleteJournalEntriesByDescriptionPattern(expense.title)
            } catch (e: Exception) {
                // Ignore pattern cleanup
            }
        }

        // ۳. حذف رکورد هزینه
        expenseAndIncomeDao.deleteExpense(expense)

        // ۴. محاسبه مجدد و قطعی مانده حساب تمامی طرف‌حساب‌ها
        try {
            if (affectedPartyId != null) {
                appDatabase?.partyDao()?.recalculatePartyBalance(affectedPartyId!!)
            }
            appDatabase?.partyDao()?.recalculateAllPartyBalances()
        } catch (e: Exception) {
            android.util.Log.e("ExpenseAndIncomeRepo", "Error recalculating party balances after deleteExpense", e)
        }
    }

    suspend fun insertDirectExpense(expense: ExpenseEntity): Long =
        expenseAndIncomeDao.insertExpense(expense)

    suspend fun getExpenseById(id: Long): ExpenseEntity? =
        expenseAndIncomeDao.getExpenseById(id)

    suspend fun insertDirectIncome(income: IncomeEntity): Long =
        expenseAndIncomeDao.insertIncome(income)

    suspend fun getIncomeById(id: Long): IncomeEntity? =
        expenseAndIncomeDao.getIncomeById(id)

    // --- Income Categories ---
    val allIncomeCategories: Flow<List<IncomeCategoryEntity>> =
        expenseAndIncomeDao.getAllIncomeCategories()

    suspend fun insertIncomeCategory(category: IncomeCategoryEntity): Long =
        expenseAndIncomeDao.insertIncomeCategory(category)

    suspend fun updateIncomeCategory(category: IncomeCategoryEntity) =
        expenseAndIncomeDao.updateIncomeCategory(category)

    suspend fun deleteIncomeCategory(category: IncomeCategoryEntity) =
        expenseAndIncomeDao.deleteIncomeCategory(category)

    // --- Incomes ---
    val allIncomes: Flow<List<IncomeEntity>> =
        expenseAndIncomeDao.getAllIncomes()

    val totalIncomes: Flow<Long?> =
        expenseAndIncomeDao.getTotalIncomes()

    fun getIncomesBetweenDates(startDate: String, endDate: String): Flow<List<IncomeEntity>> =
        expenseAndIncomeDao.getIncomesBetweenDates(startDate, endDate)

    suspend fun createIncome(income: IncomeEntity): Long = runInTransaction {
        val isForeign = income.isForeignCurrency
        val voucherDesc = if (isForeign) {
            val sym = when (income.currencyCode.trim().uppercase()) {
                "USD" -> "$"
                "EUR" -> "€"
                "TRY" -> "₺"
                "AED" -> "درهم"
                "IQD" -> "دینار"
                else -> income.currencyCode
            }
            "درآمد ارزی (${NumberFormatUtils.toPersianDigits(if (income.currencyAmount % 1.0 == 0.0) income.currencyAmount.toLong().toString() else income.currencyAmount.toString())} $sym با نرخ ${NumberFormatUtils.formatPriceOnlyNumber(income.exchangeRate.toLong())}): ${income.categoryTitle} - ${income.title}"
        } else {
            "درآمد متفرقه: ${income.categoryTitle} - ${income.title}"
        }

        // 1. Create a double-entry accounting journal voucher
        val voucherId = try {
            val entry = JournalEntryEntity(
                voucherNumber = 0L,
                jalaliDate = income.jalaliDate,
                timestamp = income.timestamp,
                description = voucherDesc,
                isBalanced = true,
                status = "FINAL"
            )
            // Debit: 10101 (صندوق) or 10102 (بانک)
            // Credit: 60101 (فروش و درآمدهای موسسه)
            val debitSubCode = if (income.depositMethod == "CASH") 10101 else 10102
            val debitGenCode = 101

            val items = listOf(
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = debitGenCode,
                    subsidiaryCode = debitSubCode,
                    description = "واریز درآمد به ${income.accountName}${if (isForeign) " (معادل ریالی $voucherDesc)" else ""}",
                    debit = income.amount,
                    credit = 0L
                ),
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 601,
                    subsidiaryCode = 60101,
                    description = voucherDesc,
                    debit = 0L,
                    credit = income.amount
                )
            )
            accountingDao.createVoucherWithItems(entry, items)
        } catch (e: Exception) {
            null
        }

        // 2. Adjust Treasury Balance
        if (isForeign) {
            // درآمد ارزی: ثبت مستقیم در موجودی تفکیکی ارزی خزانه‌داری بدون تغییر نقدینگی ریالی پایه
            val targetId = if (income.depositMethod == "CASH") income.cashDeskId else income.bankAccountId
            if (targetId != null) {
                treasuryDao.adjustCurrencyBalanceOnly(
                    accountType = income.depositMethod,
                    accountId = targetId,
                    currencyCode = income.currencyCode.trim().uppercase(),
                    delta = income.currencyAmount
                )
            }
        } else {
            // درآمد ریالی/تومانی پایه: به‌روزرسانی نقدینگی صندوق یا بانک
            if (income.depositMethod == "CASH" && income.cashDeskId != null) {
                treasuryDao.updateCashBalance(income.cashDeskId, income.amount)
            } else if (income.depositMethod == "BANK" && income.bankAccountId != null) {
                treasuryDao.updateBankBalance(income.bankAccountId, income.amount)
            }
        }

        // 3. Save Income Entity
        expenseAndIncomeDao.insertIncome(income.copy(voucherId = voucherId))
    }

    suspend fun updateIncome(oldIncome: IncomeEntity, newIncome: IncomeEntity) = runInTransaction {
        val oldIsForeign = oldIncome.isForeignCurrency
        val newIsForeign = newIncome.isForeignCurrency

        // ۱. بازگشت اثر مانده قدیم خزانه‌داری
        if (oldIsForeign) {
            val oldTargetId = if (oldIncome.depositMethod == "CASH") oldIncome.cashDeskId else oldIncome.bankAccountId
            if (oldTargetId != null) {
                treasuryDao.adjustCurrencyBalanceOnly(
                    accountType = oldIncome.depositMethod,
                    accountId = oldTargetId,
                    currencyCode = oldIncome.currencyCode.trim().uppercase(),
                    delta = -oldIncome.currencyAmount
                )
            }
        } else {
            if (oldIncome.depositMethod == "CASH" && oldIncome.cashDeskId != null) {
                treasuryDao.updateCashBalance(oldIncome.cashDeskId, -oldIncome.amount)
            } else if (oldIncome.depositMethod == "BANK" && oldIncome.bankAccountId != null) {
                treasuryDao.updateBankBalance(oldIncome.bankAccountId, -oldIncome.amount)
            }
        }

        // ۲. اعمال اثر مانده جدید خزانه‌داری
        if (newIsForeign) {
            val newTargetId = if (newIncome.depositMethod == "CASH") newIncome.cashDeskId else newIncome.bankAccountId
            if (newTargetId != null) {
                treasuryDao.adjustCurrencyBalanceOnly(
                    accountType = newIncome.depositMethod,
                    accountId = newTargetId,
                    currencyCode = newIncome.currencyCode.trim().uppercase(),
                    delta = newIncome.currencyAmount
                )
            }
        } else {
            if (newIncome.depositMethod == "CASH" && newIncome.cashDeskId != null) {
                treasuryDao.updateCashBalance(newIncome.cashDeskId, newIncome.amount)
            } else if (newIncome.depositMethod == "BANK" && newIncome.bankAccountId != null) {
                treasuryDao.updateBankBalance(newIncome.bankAccountId, newIncome.amount)
            }
        }

        // ۳. به‌روزرسانی سند حسابداری متصل
        if (oldIncome.voucherId != null) {
            try {
                accountingDao.deleteJournalItemsByVoucher(oldIncome.voucherId)
                val debitSubCode = if (newIncome.depositMethod == "CASH") 10101 else 10102
                val voucherDesc = if (newIsForeign) {
                    val sym = when (newIncome.currencyCode.trim().uppercase()) {
                        "USD" -> "$"
                        "EUR" -> "€"
                        "TRY" -> "₺"
                        "AED" -> "درهم"
                        "IQD" -> "دینار"
                        else -> newIncome.currencyCode
                    }
                    "درآمد ارزی (${NumberFormatUtils.toPersianDigits(if (newIncome.currencyAmount % 1.0 == 0.0) newIncome.currencyAmount.toLong().toString() else newIncome.currencyAmount.toString())} $sym با نرخ ${NumberFormatUtils.formatPriceOnlyNumber(newIncome.exchangeRate.toLong())}): ${newIncome.categoryTitle} - ${newIncome.title}"
                } else {
                    "درآمد متفرقه: ${newIncome.categoryTitle} - ${newIncome.title}"
                }
                val items = listOf(
                    JournalItemEntity(
                        voucherId = oldIncome.voucherId,
                        generalCode = 101,
                        subsidiaryCode = debitSubCode,
                        description = "واریز درآمد به ${newIncome.accountName}",
                        debit = newIncome.amount,
                        credit = 0L
                    ),
                    JournalItemEntity(
                        voucherId = oldIncome.voucherId,
                        generalCode = 601,
                        subsidiaryCode = 60101,
                        description = voucherDesc,
                        debit = 0L,
                        credit = newIncome.amount
                    )
                )
                accountingDao.insertJournalItems(items)
            } catch (e: Exception) {
                // Ignore
            }
        }

        expenseAndIncomeDao.updateIncome(newIncome)
    }

    suspend fun deleteIncome(income: IncomeEntity) = runInTransaction {
        val isForeign = income.isForeignCurrency

        // ۱. بازگشت مانده خزانه‌داری
        if (isForeign) {
            val targetId = if (income.depositMethod == "CASH") income.cashDeskId else income.bankAccountId
            if (targetId != null) {
                treasuryDao.adjustCurrencyBalanceOnly(
                    accountType = income.depositMethod,
                    accountId = targetId,
                    currencyCode = income.currencyCode.trim().uppercase(),
                    delta = -income.currencyAmount
                )
            }
        } else {
            if (income.depositMethod == "CASH" && income.cashDeskId != null) {
                treasuryDao.updateCashBalance(income.cashDeskId, -income.amount)
            } else if (income.depositMethod == "BANK" && income.bankAccountId != null) {
                treasuryDao.updateBankBalance(income.bankAccountId, -income.amount)
            }
        }

        // ۲. حذف کامل سند حسابداری متصل و تمام آرتیکل‌های آن (حذف قطعی رکوردهای یتیم)
        var affectedPartyId: Long? = null
        if (income.voucherId != null) {
            try {
                val items = accountingDao.getJournalItemsByVoucherSync(income.voucherId)
                affectedPartyId = items.firstOrNull { it.partyId != null }?.partyId
                accountingDao.deleteJournalItemsByVoucher(income.voucherId)
                accountingDao.deleteJournalEntryById(income.voucherId)
            } catch (e: Exception) {
                android.util.Log.e("ExpenseAndIncomeRepo", "Error deleting voucher ${income.voucherId} for income", e)
            }
        } else if (income.title.isNotBlank()) {
            try {
                accountingDao.deleteJournalItemsByDescriptionPattern(income.title)
                accountingDao.deleteJournalEntriesByDescriptionPattern(income.title)
            } catch (e: Exception) {
                // Ignore pattern cleanup
            }
        }

        // ۳. حذف رکورد درآمد
        expenseAndIncomeDao.deleteIncome(income)

        // ۴. محاسبه مجدد و قطعی مانده حساب تمامی طرف‌حساب‌ها
        try {
            if (affectedPartyId != null) {
                appDatabase?.partyDao()?.recalculatePartyBalance(affectedPartyId!!)
            }
            appDatabase?.partyDao()?.recalculateAllPartyBalances()
        } catch (e: Exception) {
            android.util.Log.e("ExpenseAndIncomeRepo", "Error recalculating party balances after deleteIncome", e)
        }
    }
}

