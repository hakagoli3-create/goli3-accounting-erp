package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AccountingRepository
import com.example.data.repository.ExpenseAndIncomeRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PartyRepository
import com.example.data.repository.TreasuryRepository
import com.example.data.model.CurrencyAssetDto
import com.example.util.JalaliDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val todaySalesAmount: Long = 0L,
    val todayInvoicesCount: Int = 0,
    val totalSalesAmount: Long = 0L,
    val totalCashBalance: Long = 0L,
    val totalBankBalance: Long = 0L,
    val totalReceivables: Long = 0L,
    val totalPayables: Long = 0L,
    val customerReceivables: Long = 0L,
    val supplierPayables: Long = 0L,
    val todayExpenses: Long = 0L,
    val totalExpenses: Long = 0L,
    val lowStockCount: Int = 0,
    val dueChequesAlertCount: Int = 0,
    val overCreditPartiesCount: Int = 0,
    val inventoryPurchaseValue: Double = 0.0,
    val inventorySalesValue: Double = 0.0,
    val currentShamsiDate: String = JalaliDate.now().format(),
    val currentShamsiFullDate: String = JalaliDate.now().getFormattedFullDate(),
    val isLoading: Boolean = false
)

private data class SalesStats(val todaySales: Long, val todayInvoices: Int, val totalSales: Long)
private data class TreasuryStats(val cash: Long, val bank: Long)
private data class AlertStats(val lowStock: Int, val dueCheques: Int, val overCredit: Int)
private data class PartyStats(
    val receivables: Long,
    val payables: Long,
    val customerReceivables: Long,
    val supplierPayables: Long
)
private data class ExpenseStats(val todayExpense: Long, val totalExpense: Long)
private data class InventoryValuationStats(val purchaseValue: Double, val salesValue: Double)

class DashboardViewModel(
    private val invoiceRepo: InvoiceRepository,
    private val treasuryRepo: TreasuryRepository,
    private val inventoryRepo: InventoryRepository,
    private val partyRepo: PartyRepository,
    private val accountingRepo: AccountingRepository,
    private val expenseRepo: ExpenseAndIncomeRepository
) : ViewModel() {

    private val todayStr = JalaliDate.now().toStandardString()

    /**
     * جریان داده ارزش کل دارایی‌های انبار بر مبنای قیمت خرید (ریال/تومان)
     * با استفاده از stateIn با چرخه حیات ایمن
     */
    val inventoryPurchaseValue: StateFlow<Double> = inventoryRepo.totalInventoryPurchaseValue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    /**
     * جریان داده ارزش کل دارایی‌های انبار بر مبنای قیمت فروش (ریال/تومان)
     * با استفاده از stateIn با چرخه حیات ایمن
     */
    val inventorySalesValue: StateFlow<Double> = inventoryRepo.totalInventorySalesValue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private val salesStats = combine(
        invoiceRepo.getTodaySalesSum(todayStr),
        invoiceRepo.getTodayInvoiceCount(todayStr),
        invoiceRepo.totalSalesSum
    ) { salesToday, invCount, totalSales ->
        SalesStats(salesToday ?: 0L, invCount, totalSales ?: 0L)
    }

    private val treasuryStats = combine(
        treasuryRepo.totalCashBalance,
        treasuryRepo.totalBankBalance
    ) { cash, bank ->
        TreasuryStats(cash ?: 0L, bank ?: 0L)
    }

    private val alertStats = combine(
        inventoryRepo.lowStockCount,
        treasuryRepo.getDueAlertsCount(),
        partyRepo.overCreditLimitCount
    ) { lowStock, dueCheques, overCredit ->
        AlertStats(lowStock, dueCheques, overCredit)
    }

    private val partyStats = combine(
        partyRepo.totalReceivables,
        partyRepo.totalPayables,
        partyRepo.customerReceivables,
        partyRepo.supplierPayables
    ) { receivables, payables, custRec, suppPay ->
        PartyStats(receivables, payables, custRec, suppPay)
    }

    private val expenseStats = combine(
        expenseRepo.getTodayExpenses(todayStr),
        expenseRepo.totalExpenses
    ) { todayExp, totalExp ->
        ExpenseStats(todayExp, totalExp ?: 0L)
    }

    private val inventoryValuationStats = combine(
        inventoryRepo.totalInventoryPurchaseValue,
        inventoryRepo.totalInventorySalesValue
    ) { purchase, sales ->
        InventoryValuationStats(purchase, sales)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        salesStats,
        treasuryStats,
        alertStats,
        partyStats,
        combine(expenseStats, inventoryValuationStats) { exp, inv -> Pair(exp, inv) }
    ) { sales, treasury, alerts, party, (expenses, inventory) ->
        DashboardUiState(
            todaySalesAmount = sales.todaySales,
            todayInvoicesCount = sales.todayInvoices,
            totalSalesAmount = sales.totalSales,
            totalCashBalance = treasury.cash,
            totalBankBalance = treasury.bank,
            totalReceivables = party.receivables,
            totalPayables = party.payables,
            customerReceivables = party.customerReceivables,
            supplierPayables = party.supplierPayables,
            todayExpenses = expenses.todayExpense,
            totalExpenses = expenses.totalExpense,
            lowStockCount = alerts.lowStock,
            dueChequesAlertCount = alerts.dueCheques,
            overCreditPartiesCount = alerts.overCredit,
            inventoryPurchaseValue = inventory.purchaseValue,
            inventorySalesValue = inventory.salesValue,
            currentShamsiDate = JalaliDate.now().format(),
            currentShamsiFullDate = JalaliDate.now().getFormattedFullDate(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    /**
     * گزارش کل دارایی‌ها به تفکیک ارز (تومان، دلار، یورو، لیر و سایر ارزهای ثبت‌شده)
     * به صورت جریان زنده و همگام با بخش خزانه‌داری و نقدینگی کل برنامه
     */
    val assetsByCurrency: StateFlow<List<CurrencyAssetDto>> = treasuryRepo.totalAssetsByCurrency.map { assetsList ->
        val defaultCurrencies = listOf("TOMAN", "USD", "EUR", "TRY")
        val currencyMap = mutableMapOf<String, Double>()

        for (asset in assetsList) {
            val code = when (asset.currencyType.trim().uppercase()) {
                "تومان", "TOMAN" -> "TOMAN"
                "دلار", "USD" -> "USD"
                "یورو", "EUR" -> "EUR"
                "لیر", "TRY" -> "TRY"
                "درهم", "AED" -> "AED"
                "دینار", "IQD" -> "IQD"
                "ریال", "IRR" -> "IRR"
                else -> asset.currencyType.trim().uppercase()
            }
            currencyMap[code] = (currencyMap[code] ?: 0.0) + asset.totalAmount
        }

        // تضمین حضور ارزهای اصلی (تومان، دلار، یورو و لیر)
        for (code in defaultCurrencies) {
            if (!currencyMap.containsKey(code)) {
                currencyMap[code] = 0.0
            }
        }

        val orderedCodes = listOf("TOMAN", "USD", "EUR", "TRY") + currencyMap.keys.filter { it !in listOf("TOMAN", "USD", "EUR", "TRY") }
        orderedCodes.map { code ->
            CurrencyAssetDto(
                currencyType = when (code) {
                    "TOMAN" -> "تومان"
                    "USD" -> "دلار"
                    "EUR" -> "یورو"
                    "TRY" -> "لیر"
                    "AED" -> "درهم"
                    "IQD" -> "دینار"
                    "IRR" -> "ریال"
                    else -> code
                },
                totalAmount = currencyMap[code] ?: 0.0
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

