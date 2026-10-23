package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IncomeEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.repository.AccountingRepository
import com.example.data.repository.ExpenseAndIncomeRepository
import com.example.data.repository.TreasuryRepository
import com.example.util.ExportHelper
import com.example.util.JalaliDate
import com.example.util.NumberFormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * مدل داده‌ای تراکنش نقدینگی و گردش حساب‌های خزانه
 */
data class TreasuryTransaction(
    val id: String,
    val type: TransactionType,
    val title: String,
    val category: String,
    val amount: Long,
    val jalaliDate: String,
    val timestamp: Long,
    val accountName: String,
    val method: String, // "CASH" or "BANK"
    val referenceNumber: String = "",
    val notes: String = ""
)

enum class TransactionType(val persianLabel: String) {
    INFLOW("دریافت / واریز"),
    OUTFLOW("پرداخت / برداشت"),
    TRANSFER("انتقال وجه")
}

enum class TreasuryDateFilter(val title: String) {
    ALL("همه تاریخ‌ها"),
    TODAY("امروز"),
    LAST_7_DAYS("۷ روز اخیر"),
    THIS_MONTH("ماه جاری")
}

class TreasuryViewModel(
    private val repository: TreasuryRepository,
    private val accountingRepo: AccountingRepository,
    private val expenseRepo: ExpenseAndIncomeRepository,
    private val partyRepo: com.example.data.repository.PartyRepository
) : ViewModel() {

    val allParties: StateFlow<List<com.example.data.model.PartyEntity>> = partyRepo.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenseCategories: StateFlow<List<com.example.data.model.ExpenseCategoryEntity>> = expenseRepo.allExpenseCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allIncomeCategories: StateFlow<List<com.example.data.model.IncomeCategoryEntity>> = expenseRepo.allIncomeCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCashDesks: StateFlow<List<CashDeskEntity>> = repository.allCashDesks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBankAccounts: StateFlow<List<BankAccountEntity>> = repository.allBankAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCheques: StateFlow<List<ChequeEntity>> = repository.allCheques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivableCheques: StateFlow<List<ChequeEntity>> = repository.receivableCheques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payableCheques: StateFlow<List<ChequeEntity>> = repository.payableCheques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val sevenDaysAhead = JalaliDate.now().addDays(7).toEpochMillis()
    val dueAlertCheques: StateFlow<List<ChequeEntity>> = repository.getDueOrOverdueCheques(sevenDaysAhead)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCashBalance: StateFlow<Long?> = repository.totalCashBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalBankBalance: StateFlow<Long?> = repository.totalBankBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val allCurrencyBalances: StateFlow<List<com.example.data.model.TreasuryCurrencyBalanceEntity>> = repository.allCurrencyBalances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // فیلترهای فهرست تراکنش‌ها و گردش حساب
    private val _transactionSearchQuery = MutableStateFlow("")
    val transactionSearchQuery: StateFlow<String> = _transactionSearchQuery.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow(TreasuryDateFilter.ALL)
    val selectedDateFilter: StateFlow<TreasuryDateFilter> = _selectedDateFilter.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null)
    val selectedTypeFilter: StateFlow<TransactionType?> = _selectedTypeFilter.asStateFlow()

    /**
     * تجمیع واقعی تمام تراکنش‌های مالی، دریافتی‌ها، پرداختی‌ها و اسناد انتقال از دیتابیس
     */
    val allTransactions: StateFlow<List<TreasuryTransaction>> = combine(
        expenseRepo.allExpenses,
        expenseRepo.allIncomes,
        accountingRepo.allJournalEntries
    ) { expenses, incomes, vouchers ->
        val list = mutableListOf<TreasuryTransaction>()

        incomes.forEach { inc ->
            list.add(
                TreasuryTransaction(
                    id = "INC-${inc.id}",
                    type = TransactionType.INFLOW,
                    title = inc.title.ifBlank { "دریافت وجه / درآمد" },
                    category = inc.categoryTitle.ifBlank { "درآمد عملیاتی" },
                    amount = inc.amount,
                    jalaliDate = inc.jalaliDate,
                    timestamp = inc.timestamp,
                    accountName = inc.accountName.ifBlank { if (inc.depositMethod == "CASH") "صندوق نقدی" else "حساب بانکی" },
                    method = inc.depositMethod,
                    referenceNumber = inc.voucherId?.let { "سند #$it" } ?: "",
                    notes = inc.notes
                )
            )
        }

        expenses.forEach { exp ->
            list.add(
                TreasuryTransaction(
                    id = "EXP-${exp.id}",
                    type = TransactionType.OUTFLOW,
                    title = exp.title.ifBlank { "پرداخت وجه / هزینه" },
                    category = exp.categoryTitle.ifBlank { "هزینه جاری" },
                    amount = exp.amount,
                    jalaliDate = exp.jalaliDate,
                    timestamp = exp.timestamp,
                    accountName = exp.accountName.ifBlank { if (exp.paymentMethod == "CASH") "صندوق نقدی" else "حساب بانکی" },
                    method = exp.paymentMethod,
                    referenceNumber = exp.voucherId?.let { "سند #$it" } ?: "",
                    notes = exp.notes
                )
            )
        }

        vouchers.filter { it.description.contains("انتقال") || it.description.contains("سرمایه") }.forEach { v ->
            val isCapital = v.description.contains("سرمایه")
            list.add(
                TreasuryTransaction(
                    id = "VOUCH-${v.id}",
                    type = if (isCapital) TransactionType.INFLOW else TransactionType.TRANSFER,
                    title = v.description,
                    category = if (isCapital) "سرمایه و آورده" else "انتقال بین حساب‌ها",
                    amount = 0L,
                    jalaliDate = v.jalaliDate,
                    timestamp = v.timestamp,
                    accountName = if (isCapital) "آورده نقدی شرکا" else "حساب‌های خزانه",
                    method = "INTERNAL",
                    referenceNumber = "سند #${v.voucherNumber}",
                    notes = v.description
                )
            )
        }

        list.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * تراکنش‌های فیلتر شده بر اساس جستجو، فیلتر تاریخ شمسی و نوع تراکنش
     */
    val filteredTransactions: StateFlow<List<TreasuryTransaction>> = combine(
        allTransactions,
        _transactionSearchQuery,
        _selectedDateFilter,
        _selectedTypeFilter
    ) { transactions, query, dateFilter, typeFilter ->
        val today = JalaliDate.now()
        val todayStr = today.toStandardString()
        val sevenDaysAgo = today.addDays(-7).toStandardString()
        val currentMonthPrefix = "${today.year}/${today.month.toString().padStart(2, '0')}"

        transactions.filter { tx ->
            // فیلتر نوع
            val matchesType = typeFilter == null || tx.type == typeFilter

            // فیلتر تاریخ شمسی
            val matchesDate = when (dateFilter) {
                TreasuryDateFilter.ALL -> true
                TreasuryDateFilter.TODAY -> tx.jalaliDate == todayStr
                TreasuryDateFilter.LAST_7_DAYS -> tx.jalaliDate >= sevenDaysAgo
                TreasuryDateFilter.THIS_MONTH -> tx.jalaliDate.startsWith(currentMonthPrefix)
            }

            // فیلتر جستجو
            val q = query.trim()
            val matchesQuery = q.isBlank() ||
                    tx.title.contains(q, ignoreCase = true) ||
                    tx.category.contains(q, ignoreCase = true) ||
                    tx.accountName.contains(q, ignoreCase = true) ||
                    tx.referenceNumber.contains(q, ignoreCase = true) ||
                    tx.jalaliDate.contains(q) ||
                    tx.notes.contains(q, ignoreCase = true)

            matchesType && matchesDate && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTransactionSearchQuery(query: String) {
        _transactionSearchQuery.value = query
    }

    fun setDateFilter(filter: TreasuryDateFilter) {
        _selectedDateFilter.value = filter
    }

    fun setTypeFilter(type: TransactionType?) {
        _selectedTypeFilter.value = type
    }

    data class ChequeRassResult(
        val totalAmount: Long,
        val chequeCount: Int,
        val weightedDays: Int,
        val rassDateJalali: String,
        val averageAmount: Long
    )

    fun calculateChequeRass(cheques: List<ChequeEntity>): ChequeRassResult? {
        if (cheques.isEmpty()) return null
        val totalAmount = cheques.sumOf { it.amount }
        if (totalAmount <= 0) return null

        var weightedDaysSum = 0.0
        cheques.forEach { ch ->
            val days = ch.daysUntilDue().coerceAtLeast(0)
            weightedDaysSum += (ch.amount.toDouble() * days.toDouble())
        }
        val avgDays = (weightedDaysSum / totalAmount.toDouble()).toInt()
        val rassDate = JalaliDate.now().addDays(avgDays).toStandardString()

        return ChequeRassResult(
            totalAmount = totalAmount,
            chequeCount = cheques.size,
            weightedDays = avgDays,
            rassDateJalali = rassDate,
            averageAmount = totalAmount / cheques.size
        )
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun addCheque(
        chequeNumber: String,
        sayadNumber: String,
        type: String,
        partyName: String,
        bankName: String,
        branch: String,
        amount: Long,
        issueDate: String,
        dueDate: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val dueJalali = JalaliDate.parse(dueDate)
            val cheque = ChequeEntity(
                chequeNumber = chequeNumber,
                sayadNumber = sayadNumber,
                type = type,
                partyName = partyName,
                bankName = bankName,
                branch = branch,
                amount = amount,
                issueDateJalali = issueDate,
                dueDateJalali = dueDate,
                dueDateEpochMillis = dueJalali.toEpochMillis(),
                status = "NORMAL",
                notes = notes
            )
            repository.insertCheque(cheque)
            _statusMessage.value = "چک صیادی با موفقیت در سامانه ثبت شد."
            onSuccess()
        }
    }

    fun updateChequeStatus(cheque: ChequeEntity, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateCheque(cheque.copy(status = newStatus))
                _statusMessage.value = "وضعیت چک به‌روزرسانی شد."
            } catch (e: Exception) {
                _statusMessage.value = "خطا در تغییر وضعیت چک: ${e.localizedMessage}"
            }
        }
    }

    fun updateCheque(cheque: ChequeEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateCheque(cheque)
                _statusMessage.value = "اطلاعات چک با موفقیت ویرایش شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ویرایش چک: ${e.localizedMessage}"
            }
        }
    }

    fun deleteCheque(cheque: ChequeEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteChequeCascading(cheque)
                _statusMessage.value = "چک صیادی و کلیه اسناد و وابستگی‌های آن با موفقیت پاکسازی شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در حذف چک: ${e.localizedMessage}"
            }
        }
    }

    /**
     * حذف اتمیک و زنجیره‌ای هر نوع تراکنش خزانه‌داری (دریافت، پرداخت، انتقال) به همراه پاکسازی کلیه اسناد دوبل و مانده‌ها
     */
    fun deleteTreasuryTransaction(transaction: TreasuryTransaction, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                when {
                    transaction.id.startsWith("INC-") -> {
                        val id = transaction.id.removePrefix("INC-").toLongOrNull()
                        if (id != null) {
                            val inc = expenseRepo.getIncomeById(id)
                            if (inc != null) {
                                expenseRepo.deleteIncome(inc)
                            }
                        }
                    }
                    transaction.id.startsWith("EXP-") -> {
                        val id = transaction.id.removePrefix("EXP-").toLongOrNull()
                        if (id != null) {
                            val exp = expenseRepo.getExpenseById(id)
                            if (exp != null) {
                                expenseRepo.deleteExpense(exp)
                            }
                        }
                    }
                    transaction.id.startsWith("VOUCH-") -> {
                        val id = transaction.id.removePrefix("VOUCH-").toLongOrNull()
                        if (id != null) {
                            accountingRepo.deleteVoucher(id)
                        }
                    }
                }
                partyRepo.recalculateAllPartyBalances()
                _statusMessage.value = "تراکنش خزانه‌داری با موفقیت به صورت اتمیک و زنجیره‌ای پاکسازی شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در حذف تراکنش: ${e.localizedMessage}"
            }
        }
    }

    fun quickAddParty(party: com.example.data.model.PartyEntity, onSuccess: (com.example.data.model.PartyEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val code = if (party.code.isBlank()) "PRT-${System.currentTimeMillis() % 100000}" else party.code
                val toInsert = party.copy(code = code)
                val id = partyRepo.insertParty(toInsert)
                val created = toInsert.copy(id = id)
                _statusMessage.value = "طرف‌حساب جدید «${created.name}» با موفقیت تعریف و انتخاب شد."
                onSuccess(created)
            } catch (e: Exception) {
                _statusMessage.value = "خطا در تعریف طرف‌حساب: ${e.localizedMessage}"
            }
        }
    }

    fun quickAddExpenseCategory(title: String, description: String = "", onSuccess: (com.example.data.model.ExpenseCategoryEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val code = "EXP-${System.currentTimeMillis() % 10000}"
                val cat = com.example.data.model.ExpenseCategoryEntity(code = code, title = title.trim(), description = description.trim())
                val id = expenseRepo.insertExpenseCategory(cat)
                val created = cat.copy(id = id)
                _statusMessage.value = "سرفصل هزینه «${created.title}» افزوده شد."
                onSuccess(created)
            } catch (e: Exception) {
                _statusMessage.value = "خطا در افزودن سرفصل هزینه: ${e.localizedMessage}"
            }
        }
    }

    fun quickAddIncomeCategory(title: String, description: String = "", onSuccess: (com.example.data.model.IncomeCategoryEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val code = "INC-${System.currentTimeMillis() % 10000}"
                val cat = com.example.data.model.IncomeCategoryEntity(code = code, title = title.trim(), description = description.trim())
                val id = expenseRepo.insertIncomeCategory(cat)
                val created = cat.copy(id = id)
                _statusMessage.value = "سرفصل درآمد «${created.title}» افزوده شد."
                onSuccess(created)
            } catch (e: Exception) {
                _statusMessage.value = "خطا در افزودن سرفصل درآمد: ${e.localizedMessage}"
            }
        }
    }

    fun registerCashReceipt(
        isPartyReceipt: Boolean,
        partyId: Long?,
        partyName: String,
        incomeCategoryId: Long?,
        incomeCategoryTitle: String,
        title: String,
        amount: Long,
        destinationType: String, // "CASH" or "BANK"
        targetId: Long,
        jalaliDate: String,
        trackingNumber: String = "",
        notes: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _statusMessage.value = "مبلغ دریافتی باید بیشتر از صفر باشد."
                return@launch
            }
            try {
                val destName = if (destinationType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == targetId }?.name ?: "صندوق نقدی"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == targetId }?.bankName ?: "حساب بانکی"
                }

                val destSubCode = if (destinationType == "CASH") 10101 else 10102
                val trackingText = if (trackingNumber.isNotBlank()) " | پیگیری: $trackingNumber" else ""
                val notesText = if (notes.isNotBlank()) " ($notes)" else ""
                val todayDate = if (jalaliDate.isNotBlank()) jalaliDate else JalaliDate.now().toStandardString()

                if (isPartyReceipt && partyId != null) {
                    val voucherDescription = "دریافت وجه از طرف‌حساب $partyName و واریز به $destName$trackingText$notesText"
                    val journalItems = listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = destSubCode,
                            partyId = null,
                            description = "واریز وجه به $destName بابت تسویه/دریافت از $partyName$trackingText",
                            debit = amount,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 102,
                            subsidiaryCode = 10201,
                            partyId = partyId,
                            description = "تسویه/دریافت وجه توسط $partyName به $destName$trackingText",
                            debit = 0L,
                            credit = amount
                        )
                    )

                    val voucherResult = accountingRepo.createJournalVoucher(
                        JournalEntryEntity(
                            voucherNumber = 0L,
                            jalaliDate = todayDate,
                            description = voucherDescription,
                            isBalanced = true,
                            status = "FINAL"
                        ),
                        journalItems
                    )
                    val voucherId = voucherResult.getOrNull()

                    if (destinationType == "CASH") {
                        repository.updateCashBalance(targetId, amount)
                    } else {
                        repository.updateBankBalance(targetId, amount)
                    }

                    partyRepo.recalculatePartyBalance(partyId)
                    partyRepo.recalculateAllPartyBalances()

                    expenseRepo.insertDirectIncome(
                        IncomeEntity(
                            categoryId = incomeCategoryId ?: 1L,
                            categoryTitle = "دریافت از طرف‌حساب: $partyName",
                            amount = amount,
                            jalaliDate = todayDate,
                            timestamp = System.currentTimeMillis(),
                            depositMethod = destinationType,
                            cashDeskId = if (destinationType == "CASH") targetId else null,
                            bankAccountId = if (destinationType == "BANK") targetId else null,
                            accountName = destName,
                            title = title.ifBlank { "دریافت وجه از $partyName" },
                            notes = "$voucherDescription ${if (trackingNumber.isNotBlank()) " | فیش: $trackingNumber" else ""}".trim(),
                            voucherId = voucherId
                        )
                    )

                    _statusMessage.value = "دریافت وجه مبلغ ${NumberFormatUtils.formatPrice(amount)} از «$partyName» با موفقیت ثبت شد."
                } else {
                    val catTitle = incomeCategoryTitle.ifBlank { "درآمدهای متفرقه" }
                    val incEntity = IncomeEntity(
                        categoryId = incomeCategoryId ?: 1L,
                        categoryTitle = catTitle,
                        amount = amount,
                        jalaliDate = todayDate,
                        timestamp = System.currentTimeMillis(),
                        depositMethod = destinationType,
                        cashDeskId = if (destinationType == "CASH") targetId else null,
                        bankAccountId = if (destinationType == "BANK") targetId else null,
                        accountName = destName,
                        title = title.ifBlank { "دریافت درآمد $catTitle" },
                        notes = "$notes $trackingText".trim()
                    )
                    expenseRepo.createIncome(incEntity)
                    _statusMessage.value = "درآمد «${incEntity.title}» به مبلغ ${NumberFormatUtils.formatPrice(amount)} با موفقیت ثبت شد."
                }
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت دریافت وجه: ${e.localizedMessage}"
            }
        }
    }

    fun registerCashPayment(
        isPartyPayment: Boolean,
        partyId: Long?,
        partyName: String,
        expenseCategoryId: Long?,
        expenseCategoryTitle: String,
        title: String,
        amount: Long,
        sourceType: String, // "CASH" or "BANK"
        sourceId: Long,
        jalaliDate: String,
        trackingNumber: String = "",
        notes: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _statusMessage.value = "مبلغ پرداختی باید بیشتر از صفر باشد."
                return@launch
            }
            try {
                val srcName = if (sourceType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == sourceId }?.name ?: "صندوق نقدی"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == sourceId }?.bankName ?: "حساب بانکی"
                }

                val srcSubCode = if (sourceType == "CASH") 10101 else 10102
                val trackingText = if (trackingNumber.isNotBlank()) " | پیگیری: $trackingNumber" else ""
                val notesText = if (notes.isNotBlank()) " ($notes)" else ""
                val todayDate = if (jalaliDate.isNotBlank()) jalaliDate else JalaliDate.now().toStandardString()

                if (isPartyPayment && partyId != null) {
                    val voucherDescription = "پرداخت وجه به طرف‌حساب $partyName از $srcName$trackingText$notesText"
                    val journalItems = listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 301,
                            subsidiaryCode = 30101,
                            partyId = partyId,
                            description = "پرداخت وجه به $partyName از $srcName$trackingText",
                            debit = amount,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = srcSubCode,
                            partyId = null,
                            description = "خروج وجه از $srcName بابت پرداخت به $partyName$trackingText",
                            debit = 0L,
                            credit = amount
                        )
                    )

                    val voucherResult = accountingRepo.createJournalVoucher(
                        JournalEntryEntity(
                            voucherNumber = 0L,
                            jalaliDate = todayDate,
                            description = voucherDescription,
                            isBalanced = true,
                            status = "FINAL"
                        ),
                        journalItems
                    )
                    val voucherId = voucherResult.getOrNull()

                    if (sourceType == "CASH") {
                        repository.updateCashBalance(sourceId, -amount)
                    } else {
                        repository.updateBankBalance(sourceId, -amount)
                    }

                    partyRepo.recalculatePartyBalance(partyId)
                    partyRepo.recalculateAllPartyBalances()

                    expenseRepo.insertDirectExpense(
                        ExpenseEntity(
                            categoryId = expenseCategoryId ?: 1L,
                            categoryTitle = "پرداخت به طرف‌حساب: $partyName",
                            amount = amount,
                            jalaliDate = todayDate,
                            timestamp = System.currentTimeMillis(),
                            paymentMethod = sourceType,
                            cashDeskId = if (sourceType == "CASH") sourceId else null,
                            bankAccountId = if (sourceType == "BANK") sourceId else null,
                            accountName = srcName,
                            title = title.ifBlank { "پرداخت وجه به $partyName" },
                            notes = "$voucherDescription ${if (trackingNumber.isNotBlank()) " | ارجاع: $trackingNumber" else ""}".trim(),
                            voucherId = voucherId
                        )
                    )

                    _statusMessage.value = "پرداخت وجه مبلغ ${NumberFormatUtils.formatPrice(amount)} به «$partyName» با موفقیت انجام شد."
                } else {
                    val catTitle = expenseCategoryTitle.ifBlank { "هزینه عمومی" }
                    val expEntity = ExpenseEntity(
                        categoryId = expenseCategoryId ?: 1L,
                        categoryTitle = catTitle,
                        amount = amount,
                        jalaliDate = todayDate,
                        timestamp = System.currentTimeMillis(),
                        paymentMethod = sourceType,
                        cashDeskId = if (sourceType == "CASH") sourceId else null,
                        bankAccountId = if (sourceType == "BANK") sourceId else null,
                        accountName = srcName,
                        title = title.ifBlank { "پرداخت هزینه $catTitle" },
                        notes = "$notes $trackingText".trim()
                    )
                    expenseRepo.createExpense(expEntity)
                    _statusMessage.value = "پرداخت هزینه «${expEntity.title}» به مبلغ ${NumberFormatUtils.formatPrice(amount)} با موفقیت ثبت شد."
                }
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت پرداخت وجه: ${e.localizedMessage}"
            }
        }
    }

    fun registerChequeWithAccounting(
        chequeNumber: String,
        sayadNumber: String,
        type: String, // "RECEIVABLE" or "PAYABLE"
        partyId: Long?,
        partyName: String,
        bankName: String,
        branch: String,
        amount: Long,
        issueDate: String,
        dueDate: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0 || chequeNumber.isBlank() || bankName.isBlank()) {
                _statusMessage.value = "لطفاً شماره چک، بانک و مبلغ را به درستی وارد نمایید."
                return@launch
            }
            try {
                val dueJalali = JalaliDate.parse(dueDate)
                val cheque = ChequeEntity(
                    chequeNumber = chequeNumber.trim(),
                    sayadNumber = sayadNumber.trim(),
                    type = type,
                    partyId = partyId,
                    partyName = partyName.trim(),
                    bankName = bankName.trim(),
                    branch = branch.trim(),
                    amount = amount,
                    issueDateJalali = issueDate,
                    dueDateJalali = dueDate,
                    dueDateEpochMillis = dueJalali.toEpochMillis(),
                    status = "NORMAL",
                    notes = notes
                )
                repository.insertCheque(cheque)

                if (partyId != null) {
                    if (type == "RECEIVABLE") {
                        val items = listOf(
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 102,
                                subsidiaryCode = 10202,
                                partyId = null,
                                description = "دریافت چک صیادی شماره $chequeNumber بانک $bankName سررسید $dueDate از $partyName",
                                debit = amount,
                                credit = 0L
                            ),
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 102,
                                subsidiaryCode = 10201,
                                partyId = partyId,
                                description = "تسویه با چک صیادی شماره $chequeNumber سررسید $dueDate توسط $partyName",
                                debit = 0L,
                                credit = amount
                            )
                        )
                        accountingRepo.createJournalVoucher(
                            JournalEntryEntity(
                                voucherNumber = 0L,
                                jalaliDate = issueDate,
                                description = "سند دریافت چک صیادی شماره $chequeNumber از $partyName",
                                isBalanced = true,
                                status = "FINAL"
                            ),
                            items
                        )
                        partyRepo.recalculatePartyBalance(partyId)
                        partyRepo.recalculateAllPartyBalances()
                    } else {
                        val items = listOf(
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 301,
                                subsidiaryCode = 30101,
                                partyId = partyId,
                                description = "پرداخت با چک صیادی شماره $chequeNumber سررسید $dueDate در وجه $partyName",
                                debit = amount,
                                credit = 0L
                            ),
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 301,
                                subsidiaryCode = 30102,
                                partyId = null,
                                description = "صدور چک صیادی پرداختنی شماره $chequeNumber سررسید $dueDate در وجه $partyName",
                                debit = 0L,
                                credit = amount
                            )
                        )
                        accountingRepo.createJournalVoucher(
                            JournalEntryEntity(
                                voucherNumber = 0L,
                                jalaliDate = issueDate,
                                description = "سند صدور چک صیادی پرداختنی شماره $chequeNumber در وجه $partyName",
                                isBalanced = true,
                                status = "FINAL"
                            ),
                            items
                        )
                        partyRepo.recalculatePartyBalance(partyId)
                        partyRepo.recalculateAllPartyBalances()
                    }
                }

                _statusMessage.value = "چک صیادی شماره $chequeNumber با موفقیت ثبت و اسناد حسابداری معین اعمال شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت چک صیادی: ${e.localizedMessage}"
            }
        }
    }

    fun clearChequeWithAccounting(
        cheque: ChequeEntity,
        targetAccountType: String, // "BANK" or "CASH"
        targetAccountId: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val accName = if (targetAccountType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == targetAccountId }?.name ?: "صندوق"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == targetAccountId }?.bankName ?: "بانک"
                }
                val accSubCode = if (targetAccountType == "CASH") 10101 else 10102
                val todayStr = JalaliDate.now().toStandardString()

                if (cheque.type == "RECEIVABLE") {
                    val items = listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = accSubCode,
                            partyId = null,
                            description = "واریز وصولی چک شماره ${cheque.chequeNumber} از ${cheque.partyName} به $accName",
                            debit = cheque.amount,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 102,
                            subsidiaryCode = 10202,
                            partyId = cheque.partyId,
                            description = "وصول و خارج شدن چک دریافتی شماره ${cheque.chequeNumber} از اسناد در جریان",
                            debit = 0L,
                            credit = cheque.amount
                        )
                    )
                    accountingRepo.createJournalVoucher(
                        JournalEntryEntity(
                            voucherNumber = 0L,
                            jalaliDate = todayStr,
                            description = "سند وصول چک صیادی شماره ${cheque.chequeNumber} و واریز به $accName",
                            isBalanced = true,
                            status = "FINAL"
                        ),
                        items
                    )
                    if (targetAccountType == "CASH") {
                        repository.updateCashBalance(targetAccountId, cheque.amount)
                    } else {
                        repository.updateBankBalance(targetAccountId, cheque.amount)
                    }
                } else {
                    val items = listOf(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 301,
                            subsidiaryCode = 30102,
                            partyId = cheque.partyId,
                            description = "تسویه و پاس شدن چک پرداختی شماره ${cheque.chequeNumber} در وجه ${cheque.partyName}",
                            debit = cheque.amount,
                            credit = 0L
                        ),
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 101,
                            subsidiaryCode = accSubCode,
                            partyId = null,
                            description = "برداشت از $accName بابت پاس شدن چک شماره ${cheque.chequeNumber}",
                            debit = 0L,
                            credit = cheque.amount
                        )
                    )
                    accountingRepo.createJournalVoucher(
                        JournalEntryEntity(
                            voucherNumber = 0L,
                            jalaliDate = todayStr,
                            description = "سند پاس شدن چک صیادی پرداختی شماره ${cheque.chequeNumber} از $accName",
                            isBalanced = true,
                            status = "FINAL"
                        ),
                        items
                    )
                    if (targetAccountType == "CASH") {
                        repository.updateCashBalance(targetAccountId, -cheque.amount)
                    } else {
                        repository.updateBankBalance(targetAccountId, -cheque.amount)
                    }
                }

                repository.updateCheque(cheque.copy(status = "PASSED"))
                _statusMessage.value = "چک شماره ${cheque.chequeNumber} با موفقیت وصول شد و موجودی $accName به‌روز گردید."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در وصول چک: ${e.localizedMessage}"
            }
        }
    }

    fun bounceChequeWithAccounting(
        cheque: ChequeEntity,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val todayStr = JalaliDate.now().toStandardString()
                if (cheque.partyId != null) {
                    if (cheque.type == "RECEIVABLE") {
                        val items = listOf(
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 102,
                                subsidiaryCode = 10201,
                                partyId = cheque.partyId,
                                description = "برگشت چک صیادی شماره ${cheque.chequeNumber} و احیای بدهی ${cheque.partyName}",
                                debit = cheque.amount,
                                credit = 0L
                            ),
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 102,
                                subsidiaryCode = 10202,
                                partyId = null,
                                description = "خروج چک برگشتی شماره ${cheque.chequeNumber} از اسناد در جریان",
                                debit = 0L,
                                credit = cheque.amount
                            )
                        )
                        accountingRepo.createJournalVoucher(
                            JournalEntryEntity(
                                voucherNumber = 0L,
                                jalaliDate = todayStr,
                                description = "سند برگشت چک صیادی شماره ${cheque.chequeNumber} طرف‌حساب ${cheque.partyName}",
                                isBalanced = true,
                                status = "FINAL"
                            ),
                            items
                        )
                        partyRepo.recalculatePartyBalance(cheque.partyId)
                        partyRepo.recalculateAllPartyBalances()
                    } else {
                        val items = listOf(
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 301,
                                subsidiaryCode = 30102,
                                partyId = null,
                                description = "ابطال چک پرداختی شماره ${cheque.chequeNumber}",
                                debit = cheque.amount,
                                credit = 0L
                            ),
                            JournalItemEntity(
                                voucherId = 0L,
                                generalCode = 301,
                                subsidiaryCode = 30101,
                                partyId = cheque.partyId,
                                description = "احیای بستانکاری ${cheque.partyName} بابت ابطال چک شماره ${cheque.chequeNumber}",
                                debit = 0L,
                                credit = cheque.amount
                            )
                        )
                        accountingRepo.createJournalVoucher(
                            JournalEntryEntity(
                                voucherNumber = 0L,
                                jalaliDate = todayStr,
                                description = "سند ابطال چک صیادی پرداختی شماره ${cheque.chequeNumber} در وجه ${cheque.partyName}",
                                isBalanced = true,
                                status = "FINAL"
                            ),
                            items
                        )
                        partyRepo.recalculatePartyBalance(cheque.partyId)
                        partyRepo.recalculateAllPartyBalances()
                    }
                }

                repository.updateCheque(cheque.copy(status = "BOUNCED"))
                _statusMessage.value = "وضعیت چک شماره ${cheque.chequeNumber} به «برگشت خورده» تغییر یافت و حساب طرف‌حساب به‌روز شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت برگشت چک: ${e.localizedMessage}"
            }
        }
    }

    fun addCashDesk(name: String, code: String, keeper: String, initialBalance: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertCashDesk(
                    CashDeskEntity(
                        code = code,
                        name = name,
                        keeperName = keeper,
                        currentBalance = initialBalance
                    )
                )
                _statusMessage.value = "صندوق جدید $name با موفقیت تعریف شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در افزودن صندوق: ${e.localizedMessage}"
            }
        }
    }

    fun updateCashDesk(cashDesk: CashDeskEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateCashDesk(cashDesk)
                _statusMessage.value = "اطلاعات صندوق به‌روزرسانی شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ویرایش صندوق: ${e.localizedMessage}"
            }
        }
    }

    fun deleteCashDesk(cashDesk: CashDeskEntity) {
        viewModelScope.launch {
            try {
                repository.deleteCashDesk(cashDesk)
                _statusMessage.value = "صندوق ${cashDesk.name} حذف شد."
            } catch (e: Exception) {
                _statusMessage.value = "امکان حذف این صندوق به دلیل داشتن تراکنش‌های مالی وجود ندارد."
            }
        }
    }

    fun addBankAccount(
        bankName: String,
        accountNumber: String,
        cardNumber: String,
        sheba: String,
        branch: String,
        initialBalance: Long,
        hasPos: Boolean,
        posTerminalId: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.insertBankAccount(
                    BankAccountEntity(
                        bankName = bankName,
                        accountNumber = accountNumber,
                        cardNumber = cardNumber,
                        shebaNumber = sheba,
                        branch = branch,
                        currentBalance = initialBalance,
                        hasPos = hasPos,
                        posTerminalId = posTerminalId
                    )
                )
                _statusMessage.value = "حساب بانکی $bankName با موفقیت تعریف شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت حساب بانکی: ${e.localizedMessage}"
            }
        }
    }

    fun updateBankAccount(account: BankAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateBankAccount(account)
                _statusMessage.value = "اطلاعات حساب بانکی به‌روزرسانی شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ویرایش حساب بانکی: ${e.localizedMessage}"
            }
        }
    }

    fun deleteBankAccount(account: BankAccountEntity) {
        viewModelScope.launch {
            try {
                repository.deleteBankAccount(account)
                _statusMessage.value = "حساب بانکی ${account.bankName} حذف شد."
            } catch (e: Exception) {
                _statusMessage.value = "امکان حذف این حساب بانکی به دلیل داشتن تراکنش وجود ندارد."
            }
        }
    }

    /**
     * ثبت عملیات دریافت وجه / درآمد واقعی در دیتابیس
     */
    fun registerIncome(
        title: String,
        amount: Long,
        destinationType: String, // "CASH" or "BANK"
        targetId: Long,
        category: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _statusMessage.value = "مبلغ درآمد باید بیشتر از صفر باشد."
                return@launch
            }
            try {
                val accName = if (destinationType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == targetId }?.name ?: "صندوق نقدی"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == targetId }?.bankName ?: "حساب بانکی"
                }

                val incomeEntity = IncomeEntity(
                    categoryId = 1L,
                    categoryTitle = category.ifBlank { "درآمد متفرقه" },
                    amount = amount,
                    jalaliDate = JalaliDate.now().toStandardString(),
                    timestamp = System.currentTimeMillis(),
                    depositMethod = destinationType,
                    cashDeskId = if (destinationType == "CASH") targetId else null,
                    bankAccountId = if (destinationType == "BANK") targetId else null,
                    accountName = accName,
                    title = title,
                    notes = notes
                )
                expenseRepo.createIncome(incomeEntity)
                _statusMessage.value = "دریافت وجه «$title» به مبلغ ${NumberFormatUtils.formatPrice(amount)} ثبت شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت دریافت وجه: ${e.localizedMessage}"
            }
        }
    }

    /**
     * ثبت هزینه / پرداخت واقعی در دیتابیس
     */
    fun registerExpense(
        title: String,
        amount: Long,
        sourceType: String, // "CASH" or "BANK"
        sourceId: Long,
        category: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _statusMessage.value = "مبلغ هزینه باید بیشتر از صفر باشد."
                return@launch
            }
            try {
                val accName = if (sourceType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == sourceId }?.name ?: "صندوق نقدی"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == sourceId }?.bankName ?: "حساب بانکی"
                }

                val expenseEntity = ExpenseEntity(
                    categoryId = 1L,
                    categoryTitle = category.ifBlank { "هزینه عمومی" },
                    amount = amount,
                    jalaliDate = JalaliDate.now().toStandardString(),
                    timestamp = System.currentTimeMillis(),
                    paymentMethod = sourceType,
                    cashDeskId = if (sourceType == "CASH") sourceId else null,
                    bankAccountId = if (sourceType == "BANK") sourceId else null,
                    accountName = accName,
                    title = title,
                    notes = notes
                )
                expenseRepo.createExpense(expenseEntity)
                _statusMessage.value = "پرداخت هزینه «$title» به مبلغ ${NumberFormatUtils.formatPrice(amount)} ثبت شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در ثبت پرداخت هزینه: ${e.localizedMessage}"
            }
        }
    }

    /**
     * ثبت سرمایه اولیه و آورده نقدی شرکا
     */
    fun registerCapitalInvestment(
        investorName: String,
        amount: Long,
        destinationType: String,
        targetId: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            if (destinationType == "CASH") {
                repository.updateCashBalance(targetId, amount)
            } else {
                repository.updateBankBalance(targetId, amount)
            }
            val today = JalaliDate.now()
            val subCode = if (destinationType == "CASH") 10101 else 10102
            val journalItems = listOf(
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 101,
                    subsidiaryCode = subCode,
                    description = "آورده نقدی سرمایه توسط $investorName",
                    debit = amount,
                    credit = 0L
                ),
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 501,
                    subsidiaryCode = 50101,
                    description = "افزایش سرمایه و سهم‌الشرکه $investorName",
                    debit = 0L,
                    credit = amount
                )
            )
            accountingRepo.createJournalVoucher(
                JournalEntryEntity(
                    voucherNumber = 0L,
                    jalaliDate = today.toStandardString(),
                    description = "سند ثبت سرمایه اولیه / آورده شرکا: $investorName",
                    isBalanced = true,
                    status = "FINAL"
                ),
                journalItems
            )
            _statusMessage.value = "ثبت آورده سرمایه «$investorName» با موفقیت انجام شد."
            onSuccess()
        }
    }

    /**
     * انتقال وجه بین صندوق و حساب بانکی
     */
    fun transferFunds(
        fromType: String, // "CASH" or "BANK"
        fromId: Long,
        toType: String, // "CASH" or "BANK"
        toId: Long,
        amount: Long,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _statusMessage.value = "مبلغ انتقال باید بیشتر از صفر باشد."
                return@launch
            }
            try {
                repository.transferBetweenAccounts(fromType, fromId, toType, toId, amount)

                val fromName = if (fromType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == fromId }?.name ?: "صندوق"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == fromId }?.bankName ?: "بانک"
                }

                val toName = if (toType == "CASH") {
                    allCashDesks.value.firstOrNull { it.id == toId }?.name ?: "صندوق"
                } else {
                    allBankAccounts.value.firstOrNull { it.id == toId }?.bankName ?: "بانک"
                }

                val today = JalaliDate.now()
                val fromSub = if (fromType == "CASH") 10101 else 10102
                val toSub = if (toType == "CASH") 10101 else 10102

                val journalItems = listOf(
                    JournalItemEntity(
                        voucherId = 0L,
                        generalCode = 101,
                        subsidiaryCode = toSub,
                        description = "انتقال وجه وارده به $toName بابت جابجایی از $fromName",
                        debit = amount,
                        credit = 0L
                    ),
                    JournalItemEntity(
                        voucherId = 0L,
                        generalCode = 101,
                        subsidiaryCode = fromSub,
                        description = "خروج وجه از $fromName بابت انتقال به $toName",
                        debit = 0L,
                        credit = amount
                    )
                )
                accountingRepo.createJournalVoucher(
                    JournalEntryEntity(
                        voucherNumber = 0L,
                        jalaliDate = today.toStandardString(),
                        description = "انتقال وجه بین حساب‌ها: از $fromName به $toName ${notes.ifBlank { "" }}",
                        isBalanced = true,
                        status = "FINAL"
                    ),
                    journalItems
                )
                _statusMessage.value = "انتقال وجه مبلغ ${NumberFormatUtils.formatPrice(amount)} با موفقیت انجام شد."
                onSuccess()
            } catch (e: Exception) {
                _statusMessage.value = "خطا در انتقال وجه: ${e.localizedMessage}"
            }
        }
    }

    /**
     * انتقال پیشرفته و حرفه‌ای بین حساب‌ها و صندوق‌ها با پشتیبانی از چندارزی و کارمزد
     */
    fun transferFundsMultiCurrency(
        fromType: String, // "CASH" or "BANK"
        fromId: Long,
        fromCurrency: String,
        fromAmount: Double,
        toType: String, // "CASH" or "BANK"
        toId: Long,
        toCurrency: String,
        toAmount: Double,
        exchangeRate: Double,
        feeAmount: Long = 0L,
        trackingNumber: String = "",
        notes: String = "",
        rateToBaseFrom: Double = 1.0,
        rateToBaseTo: Double = 1.0,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            if (fromAmount <= 0 || toAmount <= 0) {
                _statusMessage.value = "مبالغ انتقال باید بزرگتر از صفر باشند."
                return@launch
            }
            if (fromType == toType && fromId == toId && fromCurrency == toCurrency) {
                _statusMessage.value = "حساب مبدأ و مقصد و ارز انتخابی نمی‌توانند کاملاً یکسان باشند."
                return@launch
            }

            // کسر از مبدأ به ارز مبدأ
            repository.adjustCurrencyBalance(
                accountType = fromType,
                accountId = fromId,
                currencyCode = fromCurrency,
                delta = -fromAmount,
                rateToBase = rateToBaseFrom
            )

            // کسر کارمزد انتقال در صورت وجود از حساب مبدأ
            if (feeAmount > 0) {
                if (fromType == "CASH") repository.updateCashBalance(fromId, -feeAmount)
                else repository.updateBankBalance(fromId, -feeAmount)
            }

            // واریز به مقصد به ارز مقصد
            repository.adjustCurrencyBalance(
                accountType = toType,
                accountId = toId,
                currencyCode = toCurrency,
                delta = toAmount,
                rateToBase = rateToBaseTo
            )

            val fromName = if (fromType == "CASH") {
                allCashDesks.value.firstOrNull { it.id == fromId }?.name ?: "صندوق مبدأ"
            } else {
                allBankAccounts.value.firstOrNull { it.id == fromId }?.bankName ?: "بانک مبدأ"
            }

            val toName = if (toType == "CASH") {
                allCashDesks.value.firstOrNull { it.id == toId }?.name ?: "صندوق مقصد"
            } else {
                allBankAccounts.value.firstOrNull { it.id == toId }?.bankName ?: "بانک مقصد"
            }

            val today = JalaliDate.now()
            val fromSub = if (fromType == "CASH") 10101 else 10102
            val toSub = if (toType == "CASH") 10101 else 10102

            val baseAmountFrom = (fromAmount * rateToBaseFrom).toLong()
            val baseAmountTo = (toAmount * rateToBaseTo).toLong()

            val trackingText = if (trackingNumber.isNotBlank()) " | پیگیری: $trackingNumber" else ""
            val rateText = if (fromCurrency != toCurrency) " (تبدیل $fromAmount $fromCurrency به $toAmount $toCurrency با نرخ $exchangeRate)" else " ($fromAmount $fromCurrency)"
            val feeText = if (feeAmount > 0) " | کارمزد: ${NumberFormatUtils.formatPrice(feeAmount)}" else ""

            val journalItems = mutableListOf<JournalItemEntity>()

            // بدهکار: ورود به حساب مقصد
            journalItems.add(
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 101,
                    subsidiaryCode = toSub,
                    description = "واریز به $toName بابت انتقال از $fromName$rateText$trackingText",
                    debit = baseAmountTo,
                    credit = 0L
                )
            )

            // کارمزد در صورت وجود
            if (feeAmount > 0) {
                journalItems.add(
                    JournalItemEntity(
                        voucherId = 0L,
                        generalCode = 801,
                        subsidiaryCode = 80102,
                        description = "هزینه کارمزد انتقال بین حساب‌ها$trackingText",
                        debit = feeAmount,
                        credit = 0L
                    )
                )
            }

            // بستانکار: خروج از حساب مبدأ
            journalItems.add(
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = 101,
                    subsidiaryCode = fromSub,
                    description = "برداشت از $fromName بابت انتقال به $toName$rateText$trackingText$feeText",
                    debit = 0L,
                    credit = baseAmountFrom + feeAmount
                )
            )

            // ثبت تفاوت تسعیر ارز در صورت لزوم جهت تراز ماندن سند
            val diff = (baseAmountFrom + feeAmount) - (baseAmountTo + feeAmount)
            if (diff != 0L) {
                if (diff > 0) {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 801,
                            subsidiaryCode = 80102,
                            description = "تفاوت تسعیر در حواله ارزی",
                            debit = diff,
                            credit = 0L
                        )
                    )
                } else {
                    journalItems.add(
                        JournalItemEntity(
                            voucherId = 0L,
                            generalCode = 601,
                            subsidiaryCode = 60101,
                            description = "سود ناشی از تسعیر ارز در حواله",
                            debit = 0L,
                            credit = -diff
                        )
                    )
                }
            }

            accountingRepo.createJournalVoucher(
                JournalEntryEntity(
                    voucherNumber = 0L,
                    jalaliDate = today.toStandardString(),
                    description = "انتقال وجه حرفه‌ای: از $fromName به $toName$rateText$trackingText ${notes.ifBlank { "" }}",
                    isBalanced = true,
                    status = "FINAL"
                ),
                journalItems
            )

            _statusMessage.value = "انتقال وجه از «$fromName» به «$toName» با موفقیت انجام شد."
            onSuccess()
        }
    }

    fun exportTreasuryPdf(context: Context) {
        val headers = listOf("ردیف", "عنوان منبع / حساب", "نوع", "مشخصات / شماره", "موجودی (تومان)")
        val rows = mutableListOf<List<String>>()

        var r = 1
        allCashDesks.value.forEach { c ->
            rows.add(listOf(r++.toString(), c.name, "صندوق نقدی", "مسئول: ${c.keeperName}", NumberFormatUtils.formatPrice(c.currentBalance)))
        }
        allBankAccounts.value.forEach { b ->
            rows.add(listOf(r++.toString(), b.bankName, "حساب بانکی", "کارت: ${b.cardNumber}", NumberFormatUtils.formatPrice(b.currentBalance)))
        }
        val file = ExportHelper.exportReportPdf(context, "گزارش موجودی نقد، بانک و صندوق‌ها", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "گزارش خزانه‌داری PDF")
        }
    }

    fun exportTreasuryCsv(context: Context) {
        val headers = listOf("ردیف", "عنوان", "نوع", "شماره حساب/کارت", "موجودی (تومان)")
        val rows = mutableListOf<List<String>>()
        var r = 1
        allCashDesks.value.forEach { c ->
            rows.add(listOf(r++.toString(), c.name, "صندوق", c.code, c.currentBalance.toString()))
        }
        allBankAccounts.value.forEach { b ->
            rows.add(listOf(r++.toString(), b.bankName, "بانک", b.cardNumber.ifEmpty { b.accountNumber }, b.currentBalance.toString()))
        }
        val file = ExportHelper.exportReportCsv(context, "گزارش نقدینگی خزانه‌داری", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل خزانه‌داری")
        }
    }
}
