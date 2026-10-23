package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountGroupEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.SubsidiaryAccountEntity
import com.example.data.model.TrialBalanceRow
import com.example.data.repository.AccountingRepository
import com.example.util.ExportHelper
import com.example.util.JalaliDate
import com.example.util.NumberFormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.data.model.CurrencyAssetDto
import com.example.data.model.InvoiceWithItems
import com.example.data.model.JournalItemWithVoucher
import com.example.data.model.OfficialJournalRow
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SixColumnTrialBalanceRow
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PartyRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoucherItemDraft(
    val subsidiaryCode: Int,
    val subsidiaryTitle: String,
    val generalCode: Int,
    val partyId: Long? = null,
    val partyName: String? = null,
    val description: String,
    val debit: Long,
    val credit: Long
)

/**
 * صورت سود و زیان چندمرحله‌ای استاندارد حسابداری ایران
 */
data class ProfitLossStatement(
    val totalRevenue: Long = 0L,                     // ناخالص درآمدهای فروش کالا و خدمات (کد ۶۰۱۰۱)
    val salesReturnsAndDiscounts: Long = 0L,         // برگشت از فروش و تخفیفات نقدی (کد ۶۰۱۰۲)
    val netSalesRevenue: Long = 0L,                  // فروش خالص = ناخالص فروش منهای برگشت از فروش
    val costOfGoodsSold: Long = 0L,                  // بهای تمام شده کالای فروش رفته (کد ۷۰۱۰۱)
    val grossProfit: Long = 0L,                      // سود (زیان) ناخالص = فروش خالص منهای بهای تمام شده
    val operatingExpenses: Long = 0L,                // هزینه‌های اداری، عمومی، فروش و تشکیلاتی (کد ۸۰۱۰۱ و ۸۰۱۰۲)
    val operatingProfit: Long = 0L,                  // سود (زیان) عملیاتی = سود ناخالص منهای هزینه‌های عمومی
    val otherIncomes: Long = 0L,                     // سایر درآمدها و سودهای غیرعملیاتی
    val otherExpenses: Long = 0L,                    // سایر هزینه‌های غیرعملیاتی / مالی
    val netProfit: Long = 0L,                        // سود (زیان) خالص دوره = سود عملیاتی + سایر درآمدها - سایر هزینه‌ها
    val grossMarginPercent: Double = 0.0,            // درصد حاشیه سود ناخالص
    val netMarginPercent: Double = 0.0,              // درصد حاشیه سود خالص
    val expenseToRevenuePercent: Double = 0.0,        // نسبت هزینه‌ها به درآمد
    val revenueBreakdown: List<Pair<String, Long>> = emptyList(),
    val expenseBreakdown: List<Pair<String, Long>> = emptyList()
)

class AccountingViewModel(
    private val repository: AccountingRepository,
    private val partyRepo: PartyRepository,
    private val invoiceRepo: InvoiceRepository,
    private val inventoryRepo: InventoryRepository? = null
) : ViewModel() {

    val allProducts: StateFlow<List<ProductEntity>> = (inventoryRepo?.allProducts ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroups: StateFlow<List<AccountGroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGenerals: StateFlow<List<GeneralAccountEntity>> = repository.allGenerals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubsidiaries: StateFlow<List<SubsidiaryAccountEntity>> = repository.allSubsidiaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allJournalEntries: StateFlow<List<JournalEntryEntity>> = repository.allJournalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allJournalItemsWithVoucher: StateFlow<List<JournalItemWithVoucher>> = repository.allJournalItemsWithVoucher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allParties: StateFlow<List<PartyEntity>> = partyRepo.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoices: StateFlow<List<InvoiceWithItems>> = invoiceRepo.allInvoicesWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trialBalance: StateFlow<List<TrialBalanceRow>> = repository.trialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assetsByCurrency: StateFlow<List<CurrencyAssetDto>> = repository.totalAssetsByCurrency.map { assetsList ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalTrialBalance: StateFlow<List<com.example.data.model.GeneralTrialBalanceRow>> = repository.generalTrialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // تراز آزمایشی ۶ ستونه جامع
    val sixColumnTrialBalance: StateFlow<List<SixColumnTrialBalanceRow>> = combine(
        repository.allSubsidiaries,
        repository.allGenerals,
        repository.allGroups,
        repository.allJournalItemsWithVoucher
    ) { subs, generals, groups, itemsWithVoucher ->
        val generalMap = generals.associateBy { it.code }
        val groupMap = groups.associateBy { it.code }

        subs.map { sub ->
            val gen = generalMap[sub.generalCode]
            val grp = gen?.let { groupMap[it.groupCode] }

            val subItems = itemsWithVoucher.filter { it.subsidiaryCode == sub.code && it.voucherStatus != "VOID" }
            val periodDebit = subItems.sumOf { it.debit }
            val periodCredit = subItems.sumOf { it.credit }

            val finalNet = periodDebit - periodCredit
            val finalDebit = if (finalNet > 0) finalNet else 0L
            val finalCredit = if (finalNet < 0) -finalNet else 0L

            SixColumnTrialBalanceRow(
                code = sub.code,
                title = sub.title,
                generalTitle = gen?.title ?: "کل",
                groupTitle = grp?.title ?: "گروه",
                openingDebit = 0L,
                openingCredit = 0L,
                periodDebit = periodDebit,
                periodCredit = periodCredit,
                finalDebit = finalDebit,
                finalCredit = finalCredit
            )
        }.filter { it.periodDebit > 0 || it.periodCredit > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ردیف‌های دفتر روزنامه رسمی (ترتیب تاریخی و شماره سند)
    val officialJournalRows: StateFlow<List<OfficialJournalRow>> = combine(
        repository.allJournalEntries,
        repository.allJournalItemsWithVoucher,
        repository.allSubsidiaries,
        partyRepo.allParties
    ) { _, items, subs, parties ->
        val subMap = subs.associateBy { it.code }
        val partyMap = parties.associateBy { it.id }

        items.filter { it.voucherStatus != "VOID" }
            .sortedWith(compareBy<JournalItemWithVoucher> { it.voucherNumber }.thenBy { it.id })
            .mapIndexed { index, itm ->
                val sub = subMap[itm.subsidiaryCode]
                val party = itm.partyId?.let { partyMap[it] }
                OfficialJournalRow(
                    voucherId = itm.voucherId,
                    voucherNumber = itm.voucherNumber,
                    jalaliDate = itm.jalaliDate,
                    voucherDescription = itm.voucherDescription,
                    rowNumber = index + 1,
                    accountCode = itm.subsidiaryCode,
                    accountTitle = sub?.title ?: "معین ${itm.subsidiaryCode}",
                    partyName = party?.name,
                    lineDescription = itm.description,
                    debit = itm.debit,
                    credit = itm.credit
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // محاسبه دقیق و چندمرحله‌ای صورت سود و زیان استاندارد حسابداری با اتصال بهای تمام شده خرید (COGS) و فاکتورها
    val profitLoss: StateFlow<ProfitLossStatement> = combine(
        trialBalance,
        allInvoices,
        allProducts
    ) { rows, invoices, products ->
        var grossSalesFromVouchers = 0L
        var salesReturnsFromVouchers = 0L
        var cogsFromVouchers = 0L
        var opExpenses = 0L
        var otherInc = 0L
        var otherExp = 0L
        val revList = mutableListOf<Pair<String, Long>>()
        val expList = mutableListOf<Pair<String, Long>>()

        for (row in rows) {
            val groupCode = row.subsidiaryCode / 10000 // مثلا 60101 -> گروه ۶
            when (groupCode) {
                6 -> {
                    if (row.subsidiaryCode == 60102) {
                        // برگشت از فروش و تخفیفات نقدی
                        val ret = row.totalDebit - row.totalCredit
                        if (ret > 0) salesReturnsFromVouchers += ret
                    } else if (row.subsidiaryCode == 60101) {
                        val amount = row.totalCredit - row.totalDebit
                        if (amount > 0) {
                            grossSalesFromVouchers += amount
                            revList.add(row.title to amount)
                        }
                    } else {
                        val amount = row.totalCredit - row.totalDebit
                        if (amount > 0) {
                            otherInc += amount
                            revList.add("سایر درآمدها: ${row.title}" to amount)
                        }
                    }
                }
                7 -> {
                    val amount = row.totalDebit - row.totalCredit
                    if (amount > 0) {
                        cogsFromVouchers += amount
                        expList.add("بهای تمام شده: ${row.title}" to amount)
                    }
                }
                8 -> {
                    val amount = row.totalDebit - row.totalCredit
                    if (amount > 0) {
                        opExpenses += amount
                        expList.add(row.title to amount)
                    }
                }
            }
        }

        // محاسبه مستقیم بهای تمام شده کالای فروش رفته (COGS) بر مبنای اقلام فاکتورهای فروش و قیمت خرید (buyPrice) در انبار
        val productMap = products.associateBy { it.id }
        val validInvoices = invoices.filter { it.invoice.status != "VOID" && it.invoice.status != "PROFORMA" }

        var invoiceSalesGross = 0L
        var invoiceSalesReturns = 0L
        var invoiceCogs = 0L

        for (inv in validInvoices) {
            val rate = if (inv.invoice.currencyRateToBase > 0) inv.invoice.currencyRateToBase else 1.0
            fun toBase(amount: Long): Long {
                if (rate == 1.0 || amount == 0L) return amount
                return java.math.BigDecimal(amount)
                    .multiply(java.math.BigDecimal.valueOf(rate))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .toLong()
            }

            when (inv.invoice.invoiceType) {
                "SALE", "POS_SALE" -> {
                    val invTotal = if (inv.invoice.totalAmountInBase > 0) inv.invoice.totalAmountInBase else toBase(inv.invoice.totalAmount)
                    invoiceSalesGross += invTotal

                    for (item in inv.items) {
                        val p = productMap[item.productId]
                        // Null Safety: اگر قیمت خرید کالا در انبار ثبت نشده بود، مقدار پیش‌فرض صفر لحاظ می‌شود
                        val buyPrice = p?.buyPrice ?: 0L
                        if (buyPrice > 0L) {
                            val lineCost = (item.quantityInSecondary * buyPrice).toLong()
                            invoiceCogs += toBase(lineCost)
                        }
                    }
                }
                "RETURN_SALE" -> {
                    val invTotal = if (inv.invoice.totalAmountInBase > 0) inv.invoice.totalAmountInBase else toBase(inv.invoice.totalAmount)
                    invoiceSalesReturns += invTotal

                    for (item in inv.items) {
                        val p = productMap[item.productId]
                        val buyPrice = p?.buyPrice ?: 0L
                        if (buyPrice > 0L) {
                            val lineCost = (item.quantityInSecondary * buyPrice).toLong()
                            invoiceCogs -= toBase(lineCost)
                        }
                    }
                }
            }
        }

        if (invoiceCogs < 0L) invoiceCogs = 0L

        // تلفیق جامع اسناد حسابداری و فاکتورهای قطعی:
        val totalRevenue = if (grossSalesFromVouchers > 0) grossSalesFromVouchers else invoiceSalesGross
        val salesReturns = if (salesReturnsFromVouchers > 0) salesReturnsFromVouchers else invoiceSalesReturns

        if (revList.isEmpty() && totalRevenue > 0L) {
            revList.add("فروش نقدی و اعتباری کالا" to totalRevenue)
        }

        // بهای تمام شده نهایی: در صورت وجود سند معین ۷۰۱۰۱ از آن استفاده می‌شود؛ در غیر این‌صورت از فاکتور+انبار
        val finalCogs = when {
            cogsFromVouchers > 0L && invoiceCogs > 0L -> maxOf(cogsFromVouchers, invoiceCogs)
            cogsFromVouchers > 0L -> cogsFromVouchers
            else -> invoiceCogs
        }

        if (finalCogs > 0L && expList.none { it.first.contains("بهای تمام شده") }) {
            expList.add(0, "بهای تمام شده کالای فروش‌رفته (COGS)" to finalCogs)
        }

        val netSales = maxOf(0L, totalRevenue - salesReturns)
        val gross = netSales - finalCogs
        val operating = gross - opExpenses
        val net = operating + otherInc - otherExp

        val grossMargin = if (netSales > 0) (gross.toDouble() / netSales.toDouble()) * 100.0 else 0.0
        val netMargin = if (netSales > 0) (net.toDouble() / netSales.toDouble()) * 100.0 else 0.0
        val expToRev = if (netSales > 0) (opExpenses.toDouble() / netSales.toDouble()) * 100.0 else 0.0

        ProfitLossStatement(
            totalRevenue = totalRevenue,
            salesReturnsAndDiscounts = salesReturns,
            netSalesRevenue = netSales,
            costOfGoodsSold = finalCogs,
            grossProfit = gross,
            operatingExpenses = opExpenses,
            operatingProfit = operating,
            otherIncomes = otherInc,
            otherExpenses = otherExp,
            netProfit = net,
            grossMarginPercent = Math.round(grossMargin * 10.0) / 10.0,
            netMarginPercent = Math.round(netMargin * 10.0) / 10.0,
            expenseToRevenuePercent = Math.round(expToRev * 10.0) / 10.0,
            revenueBreakdown = revList,
            expenseBreakdown = expList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfitLossStatement())

    // State for General & Subsidiary Account Ledger (دفتر معین حساب‌های کل و معین)
    private val _selectedSubsidiaryCodeForLedger = MutableStateFlow<Int?>(null)
    val selectedSubsidiaryCodeForLedger: StateFlow<Int?> = _selectedSubsidiaryCodeForLedger.asStateFlow()

    val selectedSubsidiaryForLedger: StateFlow<SubsidiaryAccountEntity?> = combine(allSubsidiaries, _selectedSubsidiaryCodeForLedger) { subs, code ->
        if (code != null) subs.find { it.code == code } else subs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subsidiaryLedgerEntries: StateFlow<List<JournalItemEntity>> = _selectedSubsidiaryCodeForLedger.flatMapLatest { code ->
        val effectiveCode = code ?: allSubsidiaries.value.firstOrNull()?.code
        if (effectiveCode != null) repository.getJournalItemsBySubsidiary(effectiveCode) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subsidiaryLedgerWithVouchers: StateFlow<List<JournalItemWithVoucher>> = _selectedSubsidiaryCodeForLedger.flatMapLatest { code ->
        val effectiveCode = code ?: allSubsidiaries.value.firstOrNull()?.code
        if (effectiveCode != null) repository.getJournalItemsWithVoucherBySubsidiary(effectiveCode) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State for Party Subsidiary Ledger (دفتر معین اشخاص / تفصیلی)
    private val _selectedPartyIdForLedger = MutableStateFlow<Long?>(null)
    val selectedPartyIdForLedger: StateFlow<Long?> = _selectedPartyIdForLedger.asStateFlow()

    val selectedPartyForLedger: StateFlow<PartyEntity?> = combine(allParties, _selectedPartyIdForLedger) { parties, id ->
        if (id != null) parties.find { it.id == id } else parties.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val partyLedgerEntries: StateFlow<List<JournalItemEntity>> = _selectedPartyIdForLedger.flatMapLatest { id ->
        val effectiveId = id ?: allParties.value.firstOrNull()?.id
        if (effectiveId != null) repository.getJournalItemsByParty(effectiveId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val partyLedgerWithVouchers: StateFlow<List<JournalItemWithVoucher>> = _selectedPartyIdForLedger.flatMapLatest { id ->
        val effectiveId = id ?: allParties.value.firstOrNull()?.id
        if (effectiveId != null) repository.getJournalItemsWithVoucherByParty(effectiveId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State for creating a new Journal Voucher
    private val _draftVoucherDescription = MutableStateFlow("")
    val draftVoucherDescription: StateFlow<String> = _draftVoucherDescription.asStateFlow()

    private val _draftVoucherDate = MutableStateFlow(JalaliDate.now().toStandardString())
    val draftVoucherDate: StateFlow<String> = _draftVoucherDate.asStateFlow()

    private val _draftVoucherType = MutableStateFlow("GENERAL")
    val draftVoucherType: StateFlow<String> = _draftVoucherType.asStateFlow()

    private val _draftItems = MutableStateFlow<List<VoucherItemDraft>>(emptyList())
    val draftItems: StateFlow<List<VoucherItemDraft>> = _draftItems.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun selectSubsidiaryForLedger(code: Int?) {
        _selectedSubsidiaryCodeForLedger.value = code
    }

    fun selectPartyForLedger(partyId: Long?) {
        _selectedPartyIdForLedger.value = partyId
    }

    fun addPartyTransaction(
        partyId: Long,
        description: String,
        debit: Long,
        credit: Long,
        jalaliDate: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addPartyTransaction(partyId, description, debit, credit, jalaliDate, partyRepo.dao)
            _operationMessage.value = "گردش حساب با موفقیت در دفتر معین ثبت شد."
            onSuccess()
        }
    }

    fun updatePartyTransaction(
        item: JournalItemEntity,
        newDescription: String,
        newDebit: Long,
        newCredit: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updatePartyTransaction(item, newDescription, newDebit, newCredit, partyRepo.dao)
            _operationMessage.value = "ردیف دفتر معین با موفقیت ویرایش گردید."
            onSuccess()
        }
    }

    fun deletePartyTransaction(
        item: JournalItemEntity,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.deletePartyTransaction(item, partyRepo.dao)
            _operationMessage.value = "ردیف دفتر معین حذف شد."
            onSuccess()
        }
    }

    fun voidInvoice(invoiceId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val res = invoiceRepo.voidInvoice(invoiceId)
                if (res) {
                    _operationMessage.value = "فاکتور با موفقیت باطل شد و اثرات آن معکوس گردید."
                    onSuccess()
                } else {
                    _operationMessage.value = "امکان ابطال این فاکتور وجود ندارد."
                }
            } catch (e: Exception) {
                _operationMessage.value = "امکان ابطال این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
            }
        }
    }

    fun deleteInvoice(invoiceId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val res = invoiceRepo.deleteInvoice(invoiceId)
                if (res) {
                    _operationMessage.value = "فاکتور با موفقیت حذف گردید."
                    onSuccess()
                } else {
                    _operationMessage.value = "امکان حذف این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
                }
            } catch (e: Exception) {
                _operationMessage.value = "امکان حذف این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
            }
        }
    }

    fun exportPartyLedgerPdf(context: Context, party: PartyEntity, items: List<JournalItemEntity>) {
        val headers = listOf("ردیف", "شماره سند", "شرح گردش حساب", "بدهکار (تومان)", "بستانکار (تومان)", "مانده")
        var running = 0L
        val rows = items.mapIndexed { idx, itm ->
            running += (itm.debit - itm.credit)
            listOf(
                (idx + 1).toString(),
                "سند ${itm.voucherId}",
                itm.description,
                NumberFormatUtils.formatPrice(itm.debit),
                NumberFormatUtils.formatPrice(itm.credit),
                NumberFormatUtils.formatPrice(running)
            )
        }
        val f = ExportHelper.exportReportPdf(context, "صورت‌حساب دفتر معین ${party.name}", headers, rows)
        if (f != null) ExportHelper.shareFile(context, f, "application/pdf", "دفتر معین ${party.name} PDF")
    }

    fun exportPartyLedgerCsv(context: Context, party: PartyEntity, items: List<JournalItemEntity>) {
        val headers = listOf("ردیف", "شماره سند", "شرح گردش", "بدهکار", "بستانکار", "مانده")
        var running = 0L
        val rows = items.mapIndexed { idx, itm ->
            running += (itm.debit - itm.credit)
            listOf(
                (idx + 1).toString(),
                itm.voucherId.toString(),
                itm.description,
                itm.debit.toString(),
                itm.credit.toString(),
                running.toString()
            )
        }
        val f = ExportHelper.exportReportCsv(context, "معین_${party.name}", headers, rows)
        if (f != null) ExportHelper.shareFile(context, f, "text/csv", "خروجی اکسل معین ${party.name}")
    }

    fun voidVoucher(voucher: JournalEntryEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.voidVoucher(voucher.id)
            _operationMessage.value = "سند شماره ${voucher.voucherNumber} با موفقیت باطل شد."
            onSuccess()
        }
    }

    fun updateVoucher(voucher: JournalEntryEntity, newDesc: String, newDate: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateVoucher(voucher.copy(description = newDesc, jalaliDate = newDate))
            _operationMessage.value = "سند شماره ${voucher.voucherNumber} ویرایش شد."
            onSuccess()
        }
    }

    fun updateFullJournalVoucher(
        voucher: JournalEntryEntity,
        newDesc: String,
        newDate: String,
        newItems: List<VoucherItemDraft>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val totalDebit = newItems.sumOf { it.debit }
            val totalCredit = newItems.sumOf { it.credit }
            if (newItems.size < 2) {
                _operationMessage.value = "سند حسابداری باید حداقل دارای ۲ سطر آرتیکل باشد."
                return@launch
            }
            if (totalDebit != totalCredit) {
                _operationMessage.value = "سند تراز نیست! اختلاف: ${Math.abs(totalDebit - totalCredit)} تومان"
                return@launch
            }

            val items = newItems.map {
                JournalItemEntity(
                    voucherId = voucher.id,
                    generalCode = it.generalCode,
                    subsidiaryCode = it.subsidiaryCode,
                    partyId = it.partyId,
                    description = it.description.ifBlank { newDesc.ifBlank { voucher.description } },
                    debit = it.debit,
                    credit = it.credit
                )
            }

            val res = repository.updateFullJournalVoucher(voucher.id, newDesc.ifBlank { voucher.description }, newDate, items)
            if (res.isSuccess) {
                _operationMessage.value = "سند شماره ${voucher.voucherNumber} و آرتیکل‌ها با موفقیت ویرایش شدند و تمامی مانده‌ها به‌روز گردیدند."
                onSuccess()
            } else {
                _operationMessage.value = res.exceptionOrNull()?.message ?: "خطا در ویرایش سند"
            }
        }
    }

    fun deleteVoucher(voucherId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteVoucher(voucherId)
            _operationMessage.value = "سند مالی با موفقیت حذف گردید."
            onSuccess()
        }
    }

    fun updateDraftDescription(desc: String) {
        _draftVoucherDescription.value = desc
    }

    fun updateDraftDate(date: String) {
        _draftVoucherDate.value = date
    }

    fun updateDraftType(type: String) {
        _draftVoucherType.value = type
    }

    fun addDraftItem(item: VoucherItemDraft) {
        _draftItems.value = _draftItems.value + item
    }

    fun removeDraftItem(index: Int) {
        val current = _draftItems.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _draftItems.value = current
        }
    }

    fun clearDraft() {
        _draftItems.value = emptyList()
        _draftVoucherDescription.value = ""
        _draftVoucherType.value = "GENERAL"
        _draftVoucherDate.value = JalaliDate.now().toStandardString()
    }

    fun applyPreset(presetType: String) {
        when (presetType) {
            "CASH_TO_BANK" -> {
                _draftVoucherType.value = "SETTLEMENT"
                _draftVoucherDescription.value = "انتقال وجه نقد از صندوق به حساب جاری بانک"
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 10102,
                        subsidiaryTitle = "حساب‌های بانکی و پوز POS",
                        generalCode = 101,
                        description = "واریز وجه نقد به حساب بانکی",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 10101,
                        subsidiaryTitle = "صندوق‌های فروشگاه و دخل",
                        generalCode = 101,
                        description = "خروج وجه نقد از صندوق جهت واریز به بانک",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
            "BANK_TO_CASH" -> {
                _draftVoucherType.value = "SETTLEMENT"
                _draftVoucherDescription.value = "برداشت نقدی از حساب بانکی به صندوق فروشگاه"
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 10101,
                        subsidiaryTitle = "صندوق‌های فروشگاه و دخل",
                        generalCode = 101,
                        description = "ورود وجه نقد برداشتی از بانک به صندوق",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 10102,
                        subsidiaryTitle = "حساب‌های بانکی و پوز POS",
                        generalCode = 101,
                        description = "برداشت از حساب بانکی",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
            "EXPENSE_CASH" -> {
                _draftVoucherType.value = "GENERAL"
                _draftVoucherDescription.value = "پرداخت نقدی هزینه‌های عمومی و ملزومات"
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 80102,
                        subsidiaryTitle = "کرایه حمل، ملزومات و بسته‌بندی",
                        generalCode = 801,
                        description = "هزینه خرید ملزومات مصرفی",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 10101,
                        subsidiaryTitle = "صندوق‌های فروشگاه و دخل",
                        generalCode = 101,
                        description = "پرداخت نقدی از صندوق",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
            "PARTY_RECEIVE" -> {
                _draftVoucherType.value = "SETTLEMENT"
                _draftVoucherDescription.value = "دریافت وجه از طرف‌حساب بابت تسویه مطالبات"
                val p = allParties.value.firstOrNull()
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 10102,
                        subsidiaryTitle = "حساب‌های بانکی و پوز POS",
                        generalCode = 101,
                        description = "واریز بانکی توسط مشتری",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 10201,
                        subsidiaryTitle = "بدهکاران تجاری (مشتریان نسیه)",
                        generalCode = 102,
                        partyId = p?.id,
                        partyName = p?.name,
                        description = "بستانکار شدن حساب مشتری بابت تسویه",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
            "PARTY_PAY" -> {
                _draftVoucherType.value = "SETTLEMENT"
                _draftVoucherDescription.value = "پرداخت به تامین‌کننده بابت تسویه بدهی تجاری"
                val p = allParties.value.firstOrNull()
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 30101,
                        subsidiaryTitle = "بستانکاران تجاری (تامین‌کنندگان کالا)",
                        generalCode = 301,
                        partyId = p?.id,
                        partyName = p?.name,
                        description = "بدهکار شدن حساب تامین‌کننده",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 10102,
                        subsidiaryTitle = "حساب‌های بانکی و پوز POS",
                        generalCode = 101,
                        description = "پرداخت از حساب بانکی به تامین‌کننده",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
            "CAPITAL_INJECTION" -> {
                _draftVoucherType.value = "OPENING"
                _draftVoucherDescription.value = "افزایش سرمایه و آورده نقدی شرکا به شرکت"
                _draftItems.value = listOf(
                    VoucherItemDraft(
                        subsidiaryCode = 10102,
                        subsidiaryTitle = "حساب‌های بانکی و پوز POS",
                        generalCode = 101,
                        description = "واریز آورده نقدی به حساب بانک",
                        debit = 0L,
                        credit = 0L
                    ),
                    VoucherItemDraft(
                        subsidiaryCode = 50101,
                        subsidiaryTitle = "سرمایه ثبت شده شرکت",
                        generalCode = 501,
                        description = "سرمایه اولیه / آورده سهامداران",
                        debit = 0L,
                        credit = 0L
                    )
                )
            }
        }
    }

    fun autoBalanceDraft(selectedSub: SubsidiaryAccountEntity?) {
        val items = _draftItems.value
        val totalDebit = items.sumOf { it.debit }
        val totalCredit = items.sumOf { it.credit }
        val diff = totalDebit - totalCredit

        if (diff == 0L) {
            _operationMessage.value = "سند در حال حاضر کاملاً تراز است."
            return
        }

        val sub = selectedSub
            ?: allSubsidiaries.value.firstOrNull { it.code == if (diff > 0) 10101 else 10102 }
            ?: allSubsidiaries.value.firstOrNull()

        if (sub == null) {
            _operationMessage.value = "لطفاً ابتدا یک حساب معین انتخاب کنید."
            return
        }

        if (diff > 0) {
            val balancingItem = VoucherItemDraft(
                subsidiaryCode = sub.code,
                subsidiaryTitle = sub.title,
                generalCode = sub.generalCode,
                description = "موازنه تراز سند حسابداری",
                debit = 0L,
                credit = diff
            )
            addDraftItem(balancingItem)
            _operationMessage.value = "سطر موازنه با مبلغ ${NumberFormatUtils.formatPrice(diff)} بستانکار اضافه شد."
        } else {
            val balancingItem = VoucherItemDraft(
                subsidiaryCode = sub.code,
                subsidiaryTitle = sub.title,
                generalCode = sub.generalCode,
                description = "موازنه تراز سند حسابداری",
                debit = -diff,
                credit = 0L
            )
            addDraftItem(balancingItem)
            _operationMessage.value = "سطر موازنه با مبلغ ${NumberFormatUtils.formatPrice(-diff)} بدهکار اضافه شد."
        }
    }

    fun approveVoucher(voucher: JournalEntryEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.approveVoucher(voucher.id)
            _operationMessage.value = "سند شماره ${voucher.voucherNumber} با موفقیت تایید و قطعی شد."
            onSuccess()
        }
    }

    fun clearMessage() {
        _operationMessage.value = null
    }

    fun submitJournalVoucher(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val items = _draftItems.value
            val totalDebit = items.sumOf { it.debit }
            val totalCredit = items.sumOf { it.credit }

            if (items.size < 2) {
                _operationMessage.value = "سند حسابداری باید حداقل دارای ۲ سطر باشد."
                return@launch
            }

            if (totalDebit != totalCredit) {
                _operationMessage.value = "سند تراز نیست! اختلاف تراز: ${Math.abs(totalDebit - totalCredit)} تومان"
                return@launch
            }

            val prefix = when (_draftVoucherType.value) {
                "OPENING" -> "[سند افتتاحیه] "
                "CLOSING" -> "[سند اختتامیه] "
                "SETTLEMENT" -> "[سند تسویه] "
                else -> ""
            }

            val entry = JournalEntryEntity(
                voucherNumber = 0L,
                jalaliDate = _draftVoucherDate.value,
                description = "$prefix${_draftVoucherDescription.value.ifBlank { "سند دوبل حسابداری" }}",
                isBalanced = true,
                status = "FINAL"
            )

            val journalItems = items.map {
                JournalItemEntity(
                    voucherId = 0L,
                    generalCode = it.generalCode,
                    subsidiaryCode = it.subsidiaryCode,
                    partyId = it.partyId,
                    description = it.description,
                    debit = it.debit,
                    credit = it.credit
                )
            }

            val result = repository.createJournalVoucher(entry, journalItems)
            if (result.isSuccess) {
                _operationMessage.value = "سند حسابداری با موفقیت ثبت قطعی شد."
                clearDraft()
                onSuccess()
            } else {
                _operationMessage.value = result.exceptionOrNull()?.message ?: "خطا در ثبت سند"
            }
        }
    }

    // --- مدیریت سرفصل‌ها (ایجاد، ویرایش و حذف حساب‌های کل و معین) ---

    fun addGeneralAccount(groupCode: Int, code: Int, title: String, nature: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (code <= 0 || title.isBlank()) {
                _operationMessage.value = "کد و عنوان حساب کل را وارد فرمایید."
                return@launch
            }
            val res = repository.insertGeneralAccount(GeneralAccountEntity(code = code, groupCode = groupCode, title = title.trim(), nature = nature))
            if (res.isSuccess) {
                _operationMessage.value = "حساب کل «$title» با موفقیت ایجاد شد."
                onSuccess()
            } else {
                _operationMessage.value = "خطا در ثبت حساب کل: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateGeneralAccount(account: GeneralAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.updateGeneralAccount(account)
            if (res.isSuccess) {
                _operationMessage.value = "حساب کل ویرایش شد."
                onSuccess()
            }
        }
    }

    fun deleteGeneralAccount(account: GeneralAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteGeneralAccount(account)
            if (res.isSuccess) {
                _operationMessage.value = "حساب کل حذف گردید."
                onSuccess()
            } else {
                _operationMessage.value = "امکان حذف حساب کل به دلیل داشتن زیرمجموعه یا گردش وجود ندارد."
            }
        }
    }

    fun addSubsidiaryAccount(generalCode: Int, code: Int, title: String, nature: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (code <= 0 || title.isBlank()) {
                _operationMessage.value = "کد و عنوان حساب معین را وارد فرمایید."
                return@launch
            }
            val res = repository.insertSubsidiaryAccount(SubsidiaryAccountEntity(code = code, generalCode = generalCode, title = title.trim(), nature = nature))
            if (res.isSuccess) {
                _operationMessage.value = "حساب معین «$title» با موفقیت افزوده شد."
                onSuccess()
            } else {
                _operationMessage.value = "خطا در ثبت حساب معین: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun updateSubsidiaryAccount(account: SubsidiaryAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.updateSubsidiaryAccount(account)
            if (res.isSuccess) {
                _operationMessage.value = "حساب معین ویرایش شد."
                onSuccess()
            }
        }
    }

    fun deleteSubsidiaryAccount(account: SubsidiaryAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteSubsidiaryAccount(account)
            if (res.isSuccess) {
                _operationMessage.value = "حساب معین با موفقیت حذف شد."
                onSuccess()
            } else {
                _operationMessage.value = "امکان حذف حساب معین به دلیل داشتن آرتیکل در اسناد وجود ندارد."
            }
        }
    }

    suspend fun getVoucherItems(voucherId: Long): List<JournalItemEntity> {
        return repository.getJournalItemsByVoucherSync(voucherId)
    }

    // --- Exports for Trial Balance, Journal, and Profit & Loss ---

    fun exportSubsidiaryLedgerPdf(context: Context, sub: SubsidiaryAccountEntity, items: List<JournalItemEntity>) {
        val headers = listOf("ردیف", "شماره سند", "شرح آرتیکل سند", "بدهکار (تومان)", "بستانکار (تومان)", "مانده تجمعی", "تشخیص")
        var running = 0L
        val rows = items.mapIndexed { idx, itm ->
            running += (itm.debit - itm.credit)
            val diag = when {
                running > 0 -> "بد"
                running < 0 -> "بس"
                else -> "تسویه"
            }
            listOf(
                (idx + 1).toString(),
                "سند ${itm.voucherId}",
                itm.description,
                NumberFormatUtils.formatPrice(itm.debit),
                NumberFormatUtils.formatPrice(itm.credit),
                NumberFormatUtils.formatPrice(Math.abs(running)),
                diag
            )
        }
        val file = ExportHelper.exportReportPdf(context, "دفتر معین حساب ${sub.title} (${sub.code})", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "دفتر معین ${sub.title} PDF")
        }
    }

    fun exportSubsidiaryLedgerCsv(context: Context, sub: SubsidiaryAccountEntity, items: List<JournalItemEntity>) {
        val headers = listOf("ردیف", "شماره سند", "شرح", "بدهکار", "بستانکار", "مانده", "تشخیص")
        var running = 0L
        val rows = items.mapIndexed { idx, itm ->
            running += (itm.debit - itm.credit)
            val diag = when {
                running > 0 -> "بد"
                running < 0 -> "بس"
                else -> "تسویه"
            }
            listOf(
                (idx + 1).toString(),
                itm.voucherId.toString(),
                itm.description,
                itm.debit.toString(),
                itm.credit.toString(),
                Math.abs(running).toString(),
                diag
            )
        }
        val file = ExportHelper.exportReportCsv(context, "معین_${sub.code}_${sub.title}", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل دفتر معین")
        }
    }

    fun exportGeneralTrialBalancePdf(context: Context) {
        val headers = listOf("کد کل", "عنوان حساب کل", "گروه حساب", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = generalTrialBalance.value.map { r ->
            listOf(
                r.generalCode.toString(),
                r.title,
                r.groupTitle,
                NumberFormatUtils.formatPrice(r.totalDebit),
                NumberFormatUtils.formatPrice(r.totalCredit),
                NumberFormatUtils.formatPrice(r.balanceDebit),
                NumberFormatUtils.formatPrice(r.balanceCredit)
            )
        }
        val file = ExportHelper.exportReportPdf(context, "تراز آزمایشی چهارستونی سطح کل", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "تراز کل PDF")
        }
    }

    fun exportGeneralTrialBalanceCsv(context: Context) {
        val headers = listOf("کد کل", "عنوان حساب کل", "گروه", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = generalTrialBalance.value.map { r ->
            listOf(
                r.generalCode.toString(),
                r.title,
                r.groupTitle,
                r.totalDebit.toString(),
                r.totalCredit.toString(),
                r.balanceDebit.toString(),
                r.balanceCredit.toString()
            )
        }
        val file = ExportHelper.exportReportCsv(context, "تراز_آزمایشی_کل", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل تراز کل")
        }
    }

    fun exportSingleVoucherPdf(context: Context, voucher: JournalEntryEntity, items: List<JournalItemEntity>) {
        val headers = listOf("ردیف", "کد معین", "شرح آرتیکل سند", "بدهکار (تومان)", "بستانکار (تومان)")
        val rows = items.mapIndexed { idx, itm ->
            listOf(
                (idx + 1).toString(),
                itm.subsidiaryCode.toString(),
                itm.description,
                NumberFormatUtils.formatPrice(itm.debit),
                NumberFormatUtils.formatPrice(itm.credit)
            )
        }
        val file = ExportHelper.exportReportPdf(
            context,
            "سند حسابداری شماره ${voucher.voucherNumber} مورخ ${voucher.jalaliDate} - ${voucher.description}",
            headers,
            rows
        )
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "سند ${voucher.voucherNumber} PDF")
        }
    }

    fun exportTrialBalancePdf(context: Context) {
        val headers = listOf("کد معین", "عنوان حساب معین", "حساب کل", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = trialBalance.value.map { r ->
            listOf(
                r.subsidiaryCode.toString(),
                r.title,
                r.generalTitle,
                NumberFormatUtils.formatPrice(r.totalDebit),
                NumberFormatUtils.formatPrice(r.totalCredit),
                NumberFormatUtils.formatPrice(r.balanceDebit),
                NumberFormatUtils.formatPrice(r.balanceCredit)
            )
        }
        val file = ExportHelper.exportReportPdf(context, "تراز آزمایشی چهارستونی معین", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "تراز آزمایشی PDF")
        }
    }

    fun exportTrialBalanceCsv(context: Context) {
        val headers = listOf("کد معین", "عنوان معین", "حساب کل", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = trialBalance.value.map { r ->
            listOf(
                r.subsidiaryCode.toString(),
                r.title,
                r.generalTitle,
                r.totalDebit.toString(),
                r.totalCredit.toString(),
                r.balanceDebit.toString(),
                r.balanceCredit.toString()
            )
        }
        val file = ExportHelper.exportReportCsv(context, "تراز آزمایشی معین", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل تراز آزمایشی")
        }
    }

    fun exportProfitLossPdf(context: Context) {
        val pl = profitLoss.value
        val headers = listOf("ردیف", "عنوان قلم صورت مالی", "مبلغ (تومان)")
        val rows = listOf(
            listOf("۱", "درآمدهای عملیاتی و حاصل از فروش", NumberFormatUtils.formatPrice(pl.totalRevenue)),
            listOf("۲", "بهای تمام شده کالای فروش رفته", if (pl.costOfGoodsSold > 0) "- ${NumberFormatUtils.formatPrice(pl.costOfGoodsSold)}" else NumberFormatUtils.formatPrice(0L)),
            listOf("۳", "سود (زیان) ناخالص", NumberFormatUtils.formatPrice(pl.grossProfit)),
            listOf("۴", "هزینه‌های عمومی، اداری و توزیع", if (pl.operatingExpenses > 0) "- ${NumberFormatUtils.formatPrice(pl.operatingExpenses)}" else NumberFormatUtils.formatPrice(0L)),
            listOf("۵", "سود (زیان) خالص نهایی دوره", NumberFormatUtils.formatPrice(pl.netProfit))
        )
        val file = ExportHelper.exportReportPdf(context, "صورت سود و زیان رسمی دوره مالی", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "صورت سود و زیان PDF")
        }
    }

    fun exportProfitLossCsv(context: Context) {
        val pl = profitLoss.value
        val headers = listOf("ردیف", "عنوان", "مبلغ به تومان")
        val rows = listOf(
            listOf("۱", "درآمد فروش", pl.totalRevenue.toString()),
            listOf("۲", "بهای تمام شده کالای فروش رفته", pl.costOfGoodsSold.toString()),
            listOf("۳", "سود ناخالص", pl.grossProfit.toString()),
            listOf("۴", "هزینه‌های عمومی و اداری", pl.operatingExpenses.toString()),
            listOf("۵", "سود خالص دوره", pl.netProfit.toString())
        )
        val file = ExportHelper.exportReportCsv(context, "صورت سود و زیان", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل سود و زیان")
        }
    }

    fun exportOfficialJournalPdf(context: Context) {
        val headers = listOf("ردیف", "شماره سند", "تاریخ", "کد معین", "عنوان حساب", "شرح آرتیکل", "بدهکار (تومان)", "بستانکار (تومان)")
        val rows = officialJournalRows.value.map { r ->
            listOf(
                r.rowNumber.toString(),
                r.voucherNumber.toString(),
                r.jalaliDate,
                r.accountCode.toString(),
                r.accountTitle,
                r.lineDescription,
                NumberFormatUtils.formatPrice(r.debit),
                NumberFormatUtils.formatPrice(r.credit)
            )
        }
        val file = ExportHelper.exportReportPdf(context, "دفتر روزنامه عمومی رسمی حسابداری", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "دفتر روزنامه رسمی PDF")
        }
    }

    fun exportOfficialJournalCsv(context: Context) {
        val headers = listOf("ردیف", "شماره سند", "تاریخ", "کد معین", "عنوان حساب", "شرح آرتیکل", "بدهکار", "بستانکار")
        val rows = officialJournalRows.value.map { r ->
            listOf(
                r.rowNumber.toString(),
                r.voucherNumber.toString(),
                r.jalaliDate,
                r.accountCode.toString(),
                r.accountTitle,
                r.lineDescription,
                r.debit.toString(),
                r.credit.toString()
            )
        }
        val file = ExportHelper.exportReportCsv(context, "دفتر_روزنامه_رسمی", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل دفتر روزنامه")
        }
    }

    fun exportSixColumnTrialBalancePdf(context: Context) {
        val headers = listOf("کد معین", "عنوان حساب", "گروه", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = sixColumnTrialBalance.value.map { r ->
            listOf(
                r.code.toString(),
                r.title,
                r.groupTitle,
                NumberFormatUtils.formatPrice(r.periodDebit),
                NumberFormatUtils.formatPrice(r.periodCredit),
                NumberFormatUtils.formatPrice(r.finalDebit),
                NumberFormatUtils.formatPrice(r.finalCredit)
            )
        }
        val file = ExportHelper.exportReportPdf(context, "تراز آزمایشی شش ستونه جامع", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "تراز شش ستونه PDF")
        }
    }

    fun exportSixColumnTrialBalanceCsv(context: Context) {
        val headers = listOf("کد معین", "عنوان حساب", "گروه", "گردش بدهکار", "گردش بستانکار", "مانده بدهکار", "مانده بستانکار")
        val rows = sixColumnTrialBalance.value.map { r ->
            listOf(
                r.code.toString(),
                r.title,
                r.groupTitle,
                r.periodDebit.toString(),
                r.periodCredit.toString(),
                r.finalDebit.toString(),
                r.finalCredit.toString()
            )
        }
        val file = ExportHelper.exportReportCsv(context, "تراز_شش_ستونه", headers, rows)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل تراز شش ستونه")
        }
    }
}
