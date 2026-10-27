package com.example.ui.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItemEntity
import com.example.data.model.InvoiceWithItems
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StoreProfile
import com.example.data.repository.InventoryRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PartyRepository
import com.example.data.repository.TreasuryRepository
import com.example.util.BluetoothThermalPrinter
import com.example.util.EscPosPrinterHelper
import com.example.util.ExportHelper
import com.example.util.JalaliDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartItem(
    val product: ProductEntity,
    val isPrimaryUnit: Boolean, // آیا واحد اصلی (کارتن) است یا فرعی (عدد)
    val quantity: Double,
    val unitPrice: Long
) {
    val unitName: String get() = if (isPrimaryUnit) product.primaryUnit else product.secondaryUnit
    val quantityInSecondary: Double get() = if (isPrimaryUnit) quantity * (if (product.conversionRate > 0) product.conversionRate else 1.0) else quantity
    val totalPrice: Long get() = maxOf(0L, Math.round(quantity * maxOf(0L, unitPrice)))
}

class InvoiceViewModel(
    private val invoiceRepo: InvoiceRepository,
    private val inventoryRepo: InventoryRepository,
    private val partyRepo: PartyRepository,
    private val treasuryRepo: TreasuryRepository
) : ViewModel() {

    val allInvoices: StateFlow<List<InvoiceWithItems>> = invoiceRepo.allInvoicesWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<ProductEntity>> = inventoryRepo.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomers: StateFlow<List<PartyEntity>> = partyRepo.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<PartyEntity>> = partyRepo.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allParties: StateFlow<List<PartyEntity>> = partyRepo.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCashDesks: StateFlow<List<CashDeskEntity>> = treasuryRepo.allCashDesks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBankAccounts: StateFlow<List<BankAccountEntity>> = treasuryRepo.allBankAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Invoice Type: SALE (فروش), PURCHASE (خرید), RETURN_SALE (برگشت از فروش), RETURN_PURCHASE (برگشت از خرید) ---
    private val _currentInvoiceType = MutableStateFlow("SALE")
    val currentInvoiceType: StateFlow<String> = _currentInvoiceType.asStateFlow()

    // --- State for editing existing invoice ---
    private val _editingInvoiceId = MutableStateFlow<Long?>(null)
    val editingInvoiceId: StateFlow<Long?> = _editingInvoiceId.asStateFlow()

    private val _editingInvoiceNumber = MutableStateFlow<String?>(null)
    val editingInvoiceNumber: StateFlow<String?> = _editingInvoiceNumber.asStateFlow()

    private val _editingPartyId = MutableStateFlow<Long?>(null)
    val editingPartyId: StateFlow<Long?> = _editingPartyId.asStateFlow()

    private val _editingPartyName = MutableStateFlow<String?>(null)
    val editingPartyName: StateFlow<String?> = _editingPartyName.asStateFlow()

    private val _editingPartyPhone = MutableStateFlow<String?>(null)
    val editingPartyPhone: StateFlow<String?> = _editingPartyPhone.asStateFlow()

    private val _editingOriginalDate = MutableStateFlow<String?>(null)
    val editingOriginalDate: StateFlow<String?> = _editingOriginalDate.asStateFlow()

    private val _editingPaymentType = MutableStateFlow("CASH")
    val editingPaymentType: StateFlow<String> = _editingPaymentType.asStateFlow()

    private val _editingCashAmount = MutableStateFlow(0L)
    val editingCashAmount: StateFlow<Long> = _editingCashAmount.asStateFlow()

    private val _editingPosAmount = MutableStateFlow(0L)
    val editingPosAmount: StateFlow<Long> = _editingPosAmount.asStateFlow()

    private val _editingChequeAmount = MutableStateFlow(0L)
    val editingChequeAmount: StateFlow<Long> = _editingChequeAmount.asStateFlow()

    private val _editingCreditAmount = MutableStateFlow(0L)
    val editingCreditAmount: StateFlow<Long> = _editingCreditAmount.asStateFlow()

    private val _editingNotes = MutableStateFlow("")
    val editingNotes: StateFlow<String> = _editingNotes.asStateFlow()

    // --- Pricing Mode: RETAIL (تکی) vs WHOLESALE (عمده) ---
    private val _pricingMode = MutableStateFlow("RETAIL")
    val pricingMode: StateFlow<String> = _pricingMode.asStateFlow()

    // --- POS & Fast Cart State ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedParty = MutableStateFlow<PartyEntity?>(null)
    val selectedParty: StateFlow<PartyEntity?> = _selectedParty.asStateFlow()

    private val _discountAmount = MutableStateFlow(0L)
    val discountAmount: StateFlow<Long> = _discountAmount.asStateFlow()

    private val _applyTax = MutableStateFlow(false)
    val applyTax: StateFlow<Boolean> = _applyTax.asStateFlow()

    // Selected destination bank & cash desk
    private val _selectedCashDeskId = MutableStateFlow(1L)
    val selectedCashDeskId: StateFlow<Long> = _selectedCashDeskId.asStateFlow()

    private val _selectedBankAccountId = MutableStateFlow(1L)
    val selectedBankAccountId: StateFlow<Long> = _selectedBankAccountId.asStateFlow()

    // Direct Expense on invoice (هزینه مستقیم مانند حمل، بسته‌بندی و...)
    private val _directExpenseAmount = MutableStateFlow(0L)
    val directExpenseAmount: StateFlow<Long> = _directExpenseAmount.asStateFlow()

    private val _directExpenseType = MutableStateFlow("کرایه حمل و باربری")
    val directExpenseType: StateFlow<String> = _directExpenseType.asStateFlow()

    // Issue as Proforma (پیش‌فاکتور)
    private val _issueAsProforma = MutableStateFlow(false)
    val issueAsProforma: StateFlow<Boolean> = _issueAsProforma.asStateFlow()

    // Printable Receipt State
    private val _previewReceiptText = MutableStateFlow<String?>(null)
    val previewReceiptText: StateFlow<String?> = _previewReceiptText.asStateFlow()

    // Selected Invoice for Detailed Official View / Export Dialog
    private val _selectedInvoiceForDetails = MutableStateFlow<InvoiceWithItems?>(null)
    val selectedInvoiceForDetails: StateFlow<InvoiceWithItems?> = _selectedInvoiceForDetails.asStateFlow()

    // Print Format Choice: OFFICIAL_A4, OFFICIAL_A5, THERMAL
    private val _selectedPrintFormat = MutableStateFlow("OFFICIAL_A4")
    val selectedPrintFormat: StateFlow<String> = _selectedPrintFormat.asStateFlow()

    fun setPrintFormat(format: String) {
        _selectedPrintFormat.value = format
    }

    // Multi-Currency selection for Invoices
    private val _selectedCurrencyCode = MutableStateFlow("TOMAN")
    val selectedCurrencyCode: StateFlow<String> = _selectedCurrencyCode.asStateFlow()

    private val _invoiceCurrencyRate = MutableStateFlow(1.0)
    val invoiceCurrencyRate: StateFlow<Double> = _invoiceCurrencyRate.asStateFlow()

    fun setInvoiceCurrency(code: String, rate: Double) {
        _selectedCurrencyCode.value = code
        _invoiceCurrencyRate.value = if (rate > 0) rate else 1.0
    }

    private val _alertMessage = MutableStateFlow<String?>(null)
    val alertMessage: StateFlow<String?> = _alertMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    fun setCurrentInvoiceType(type: String) {
        _currentInvoiceType.value = type
        val isPurchase = type in listOf("PURCHASE", "RETURN_PURCHASE")
        if (isPurchase) {
            if (_selectedParty.value != null && _selectedParty.value?.partyType != "SUPPLIER" && _selectedParty.value?.partyType != "BOTH") {
                _selectedParty.value = allSuppliers.value.firstOrNull()
            }
        } else {
            if (_selectedParty.value != null && _selectedParty.value?.partyType != "CUSTOMER" && _selectedParty.value?.partyType != "BOTH") {
                _selectedParty.value = allCustomers.value.firstOrNull()
            }
        }
        val isWholesale = _pricingMode.value == "WHOLESALE"
        _cartItems.value = _cartItems.value.map { item ->
            val p = item.product
            val newPrice = if (isPurchase) {
                if (item.isPrimaryUnit) (p.buyPrice * (if (p.conversionRate > 0) p.conversionRate else 1.0)).toLong() else p.buyPrice
            } else if (isWholesale) {
                if (item.isPrimaryUnit) (p.wholesalePrice * (if (p.conversionRate > 0) p.conversionRate else 1.0)).toLong() else p.wholesalePrice
            } else {
                if (item.isPrimaryUnit) (p.retailPrice * (if (p.conversionRate > 0) p.conversionRate else 1.0)).toLong() else p.retailPrice
            }
            item.copy(unitPrice = maxOf(0L, newPrice))
        }
    }

    fun loadInvoiceForEditing(invoiceWithItems: InvoiceWithItems) {
        val inv = invoiceWithItems.invoice
        _editingInvoiceId.value = inv.id
        _editingInvoiceNumber.value = inv.invoiceNumber
        _editingOriginalDate.value = inv.jalaliDate
        _editingPartyId.value = inv.partyId
        _editingPartyName.value = inv.partyName
        _editingPartyPhone.value = inv.partyPhone
        _currentInvoiceType.value = inv.invoiceType
        _pricingMode.value = inv.pricingMode
        _discountAmount.value = inv.discountAmount
        _applyTax.value = inv.taxAmount > 0
        _directExpenseAmount.value = inv.directExpenseAmount
        _directExpenseType.value = inv.directExpenseType.ifBlank { "کرایه حمل و باربری" }
        _selectedCashDeskId.value = inv.cashDeskId
        _selectedBankAccountId.value = inv.bankAccountId
        _selectedCurrencyCode.value = inv.currencyCode
        _invoiceCurrencyRate.value = if (inv.currencyRateToBase > 0) inv.currencyRateToBase else 1.0
        _editingPaymentType.value = inv.paymentType
        _editingCashAmount.value = inv.cashAmount
        _editingPosAmount.value = inv.posAmount
        _editingChequeAmount.value = inv.chequeAmount
        _editingCreditAmount.value = inv.creditAmount
        _editingNotes.value = inv.notes

        // Retrieve existing party or create reliable fallback so party info is never lost or blank
        val foundParty = allParties.value.find { it.id == inv.partyId }
        if (foundParty != null) {
            _selectedParty.value = foundParty
        } else {
            val fallbackParty = PartyEntity(
                id = inv.partyId ?: 0L,
                code = if ((inv.partyId ?: 0L) > 0) "P-${inv.partyId}" else "CUS-CASH",
                name = inv.partyName.ifBlank { "مشتری نقدی" },
                partyType = if (inv.invoiceType in listOf("PURCHASE", "RETURN_PURCHASE")) "SUPPLIER" else "CUSTOMER",
                mobile = inv.partyPhone,
                phone = inv.partyPhone
            )
            _selectedParty.value = fallbackParty

            if (inv.partyId != null && inv.partyId > 0) {
                viewModelScope.launch {
                    val dbParty = partyRepo.getPartyById(inv.partyId)
                    if (dbParty != null) {
                        _selectedParty.value = dbParty
                    }
                }
            }
        }

        val productsMap = allProducts.value.associateBy { it.id }
        val loadedCart = invoiceWithItems.items.map { item ->
            val product = productsMap[item.productId] ?: ProductEntity(
                id = item.productId,
                code = "PRD-${item.productId}",
                barcode = item.barcode,
                name = item.productName,
                category = "عمومی",
                primaryUnit = if (item.isPrimaryUnit) item.unitName else "کارتن",
                secondaryUnit = if (!item.isPrimaryUnit) item.unitName else "عدد",
                conversionRate = if (item.conversionRate > 0) item.conversionRate else 1.0,
                buyPrice = item.unitPrice,
                retailPrice = item.unitPrice,
                wholesalePrice = item.unitPrice
            )
            CartItem(
                product = product,
                isPrimaryUnit = item.isPrimaryUnit,
                quantity = item.quantity,
                unitPrice = item.unitPrice
            )
        }
        _cartItems.value = loadedCart
    }

    fun cancelEditingInvoice() {
        _editingInvoiceId.value = null
        _editingInvoiceNumber.value = null
        _editingPartyId.value = null
        _editingPartyName.value = null
        _editingPartyPhone.value = null
        _editingOriginalDate.value = null
        _editingPaymentType.value = "CASH"
        _editingCashAmount.value = 0L
        _editingPosAmount.value = 0L
        _editingChequeAmount.value = 0L
        _editingCreditAmount.value = 0L
        _editingNotes.value = ""
        clearCart()
    }

    fun voidInvoice(invoiceId: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val res = invoiceRepo.voidInvoice(invoiceId)
                if (res) {
                    _alertMessage.value = "فاکتور با موفقیت باطل شد و اثرات انبار و مالی آن معکوس گردید."
                } else {
                    _alertMessage.value = "امکان ابطال این فاکتور وجود ندارد (ممکن است قبلاً باطل شده باشد)."
                }
                onDone(res)
            } catch (e: Exception) {
                _alertMessage.value = "امکان ابطال این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
                onDone(false)
            }
        }
    }

    fun deleteInvoice(invoiceId: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val res = invoiceRepo.deleteInvoice(invoiceId)
                if (res) {
                    _alertMessage.value = "فاکتور با موفقیت حذف گردید."
                } else {
                    _alertMessage.value = "امکان حذف این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
                }
                onDone(res)
            } catch (e: Exception) {
                _alertMessage.value = "امکان حذف این سند به دلیل داشتن تراکنش‌های وابسته وجود ندارد. ابتدا تراکنش‌های مرتبط را مدیریت کنید."
                onDone(false)
            }
        }
    }

    fun setPricingMode(mode: String) {
        _pricingMode.value = mode
        val isWholesale = mode == "WHOLESALE"
        val isPurchase = _currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")
        val updated = _cartItems.value.map { item ->
            val p = item.product
            val price = if (isPurchase) {
                if (item.isPrimaryUnit) (p.buyPrice * p.conversionRate).toLong() else p.buyPrice
            } else if (isWholesale) {
                if (item.isPrimaryUnit) (p.wholesalePrice * p.conversionRate).toLong() else p.wholesalePrice
            } else {
                if (item.isPrimaryUnit) (p.retailPrice * p.conversionRate).toLong() else p.retailPrice
            }
            item.copy(unitPrice = price)
        }
        _cartItems.value = updated
    }

    fun setSelectedCashDesk(id: Long) {
        _selectedCashDeskId.value = id
    }

    fun setSelectedBankAccount(id: Long) {
        _selectedBankAccountId.value = id
    }

    fun addToCart(product: ProductEntity, isPrimary: Boolean = false, forceWholesale: Boolean? = null) {
        val isWholesale = forceWholesale ?: (_pricingMode.value == "WHOLESALE")
        val isPurchase = _currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id && it.isPrimaryUnit == isPrimary }

        val price = if (isPurchase) {
            if (isPrimary) (product.buyPrice * product.conversionRate).toLong() else product.buyPrice
        } else if (isWholesale) {
            if (isPrimary) (product.wholesalePrice * product.conversionRate).toLong() else product.wholesalePrice
        } else {
            if (isPrimary) (product.retailPrice * product.conversionRate).toLong() else product.retailPrice
        }

        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1.0)
        } else {
            current.add(
                CartItem(
                    product = product,
                    isPrimaryUnit = isPrimary,
                    quantity = 1.0,
                    unitPrice = price
                )
            )
        }
        _cartItems.value = current
    }

    fun toggleCartItemUnit(index: Int) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            val newIsPrimary = !item.isPrimaryUnit
            val isWholesale = _pricingMode.value == "WHOLESALE"
            val isPurchase = _currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")
            val price = if (isPurchase) {
                if (newIsPrimary) (item.product.buyPrice * item.product.conversionRate).toLong() else item.product.buyPrice
            } else if (isWholesale) {
                if (newIsPrimary) (item.product.wholesalePrice * item.product.conversionRate).toLong() else item.product.wholesalePrice
            } else {
                if (newIsPrimary) (item.product.retailPrice * item.product.conversionRate).toLong() else item.product.retailPrice
            }
            current[index] = item.copy(isPrimaryUnit = newIsPrimary, unitPrice = price)
            _cartItems.value = current
        }
    }

    fun updateCartItemFull(index: Int, newQuantity: Double, newUnitPrice: Long, isPrimary: Boolean) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            if (newQuantity <= 0.0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(
                    quantity = newQuantity,
                    unitPrice = maxOf(0L, newUnitPrice),
                    isPrimaryUnit = isPrimary
                )
            }
            _cartItems.value = current
        }
    }

    fun removeCartItem(index: Int) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _cartItems.value = current
        }
    }

    fun updateCartItemQuantity(index: Int, newQuantity: Double) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            if (newQuantity <= 0.0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(quantity = newQuantity)
            }
            _cartItems.value = current
        }
    }

    fun updateCartItemUnitPrice(index: Int, newUnitPrice: Long) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices && newUnitPrice >= 0) {
            current[index] = current[index].copy(unitPrice = newUnitPrice)
            _cartItems.value = current
        }
    }

    fun setDirectExpense(amount: Long, type: String = "کرایه حمل و باربری") {
        _directExpenseAmount.value = amount
        _directExpenseType.value = type
    }

    fun setIssueAsProforma(isProforma: Boolean) {
        _issueAsProforma.value = isProforma
    }

    /**
     * تنظیم تعداد کارتن و تعداد تکی به طور همزمان برای هر قلم کالا
     */
    fun setCartItemQuantities(index: Int, cartons: Double, units: Double) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            val conversion = if (item.product.conversionRate > 0) item.product.conversionRate else 1.0
            val totalInSecondary = (cartons * conversion) + units
            if (totalInSecondary <= 0) {
                current.removeAt(index)
            } else {
                val qty = if (item.isPrimaryUnit) totalInSecondary / conversion else totalInSecondary
                current[index] = item.copy(quantity = qty)
            }
            _cartItems.value = current
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountAmount.value = 0L
        _applyTax.value = false
        _directExpenseAmount.value = 0L
        _issueAsProforma.value = false
        _selectedParty.value = null
        _selectedCurrencyCode.value = "TOMAN"
        _invoiceCurrencyRate.value = 1.0
    }

    fun selectParty(party: PartyEntity?) {
        _selectedParty.value = party
        if (party != null) {
            _editingPartyId.value = if (party.id > 0) party.id else null
            _editingPartyName.value = party.name
            _editingPartyPhone.value = party.mobile.ifBlank { party.phone }
        } else {
            _editingPartyId.value = null
            _editingPartyName.value = null
            _editingPartyPhone.value = null
        }
    }

    fun setDiscount(amount: Long) {
        _discountAmount.value = amount
    }

    fun toggleTax(apply: Boolean) {
        _applyTax.value = apply
    }

    fun clearAlertMessage() {
        _alertMessage.value = null
    }

    fun closeReceiptPreview() {
        _previewReceiptText.value = null
    }

    fun openInvoiceDetails(invoice: InvoiceWithItems) {
        _selectedInvoiceForDetails.value = invoice
    }

    fun closeInvoiceDetails() {
        _selectedInvoiceForDetails.value = null
    }

    fun scanBarcode(barcode: String): kotlinx.coroutines.Job = viewModelScope.launch {
        val clean = barcode.trim()
        if (clean.isBlank()) return@launch
        val product = com.example.util.InvoiceBarcodeHelper.findProductByBarcode(
            clean,
            inventoryRepo,
            allProducts.value
        )
        if (product != null) {
            val alreadyInCart = _cartItems.value.any { it.product.id == product.id }
            addToCart(product, isPrimary = false)
            if (alreadyInCart) {
                _alertMessage.value = "تعداد کالای «${product.name}» در فاکتور یک واحد افزایش یافت."
            } else {
                _alertMessage.value = "کالای «${product.name}» با موفقیت به فاکتور افزوده شد."
            }
        } else {
            _alertMessage.value = "کالایی با بارکد یا کد «$clean» در انبار یا فروشگاه یافت نشد."
        }
    }

    /**
     * محاسبه سود ناخالص یک فاکتور بر اساس بهای تمام شده خرید (COGS)
     */
    fun calculateGrossProfit(inv: InvoiceWithItems): Long {
        val totalRevenue = inv.invoice.subtotalAmount - inv.invoice.discountAmount
        // بهای تمام شده بر اساس buyPrice اقلام کالا
        val productMap = allProducts.value.associateBy { it.id }
        val cogs = inv.items.sumOf { item ->
            val p = productMap[item.productId]
            val buyPrice = p?.buyPrice ?: (item.unitPrice * 0.75).toLong()
            (item.quantityInSecondary * buyPrice).toLong()
        }
        return totalRevenue - cogs
    }

    /**
     * تبدیل سریع پیش‌فاکتور به فاکتور فروش قطعی
     */
    fun convertProformaToFinal(invoiceWithItems: InvoiceWithItems, onCompleted: () -> Unit) {
        viewModelScope.launch {
            val success = invoiceRepo.convertProformaToFinal(invoiceWithItems.invoice.id)
            if (success) {
                _alertMessage.value = "پیش‌فاکتور شماره ${invoiceWithItems.invoice.invoiceNumber} به فاکتور قطعی تبدیل شد و موجودی انبار کسر گردید."
                onCompleted()
            } else {
                _alertMessage.value = "خطا در تبدیل پیش‌فاکتور."
            }
        }
    }

    /**
     * ابطال تمامی اسناد و فاکتور
     */
    fun voidInvoice(invoiceWithItems: InvoiceWithItems, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val success = invoiceRepo.voidInvoice(invoiceWithItems.invoice.id)
            if (success) {
                _alertMessage.value = "فاکتور شماره ${invoiceWithItems.invoice.invoiceNumber} با موفقیت باطل شد و موجودی انبار به حالت قبل بازگشت."
                onCompleted()
            } else {
                _alertMessage.value = "فاکتور قبلاً باطل شده است یا یافت نشد."
            }
        }
    }

    /**
     * پاکسازی داده‌های معیوب، حذف کاردکس‌های تکراری و بازیابی دقیق موجودی انبار
     */
    fun cleanCorruptedMovementsAndReconcileStock(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val cleanedCount = invoiceRepo.cleanupCorruptedMovementsAndReconcileStock()
                if (cleanedCount > 0) {
                    _alertMessage.value = "اصلاح و پاکسازی کاردکس انجام شد: $cleanedCount رکورد تکراری یا نامعتبر پاکسازی و موجودی انبار بازنگری شد."
                } else {
                    _alertMessage.value = "کاردکس انبار و موجودی کالاها در وضعیت صحیح و بدون خطا قرار دارند."
                }
                onDone(cleanedCount)
            } catch (e: Exception) {
                _alertMessage.value = "خطا در پاکسازی کاردکس: ${e.message}"
                onDone(0)
            }
        }
    }

    /**
     * ارسال مستقیم دستورات چاپ به پرینتر حرارتی بلوتوثی
     */
    fun printThermalReceiptBluetooth(
        device: BluetoothDevice,
        invoiceWithItems: InvoiceWithItems,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val inv = invoiceWithItems.invoice
            val printable = EscPosPrinterHelper.PrintableInvoice(
                invoiceNumber = inv.invoiceNumber,
                invoiceDate = inv.jalaliDate,
                customerName = inv.partyName,
                customerPhone = inv.partyPhone,
                warehouseName = inv.warehouseName,
                items = invoiceWithItems.items.map {
                    EscPosPrinterHelper.PrintableItem(
                        name = it.productName,
                        quantity = it.quantity,
                        unit = it.unitName,
                        unitPrice = it.unitPrice,
                        totalPrice = it.totalPrice
                    )
                },
                subtotalAmount = inv.subtotalAmount,
                discountAmount = inv.discountAmount,
                taxAmount = inv.taxAmount,
                totalAmount = inv.totalAmount,
                paymentMethod = inv.paymentType,
                directExpenseAmount = inv.directExpenseAmount,
                directExpenseType = inv.directExpenseType,
                note = inv.notes,
                invoiceType = inv.invoiceType
            )
            val bytes = EscPosPrinterHelper.buildEscPosBytes(printable)
            val result = BluetoothThermalPrinter.printBytes(device, bytes)
            if (result.isSuccess) {
                onResult(true, "فاکتور با موفقیت به پرینتر حرارتی ارسال و چاپ شد.")
            } else {
                val err = result.exceptionOrNull()?.message ?: "خطای اتصال به پرینتر بلوتوثی"
                onResult(false, "عدم موفقیت در چاپ بلوتوث: $err")
            }
        }
    }

    /**
     * ثبت نهایی فاکتور همراه با تسویه پیشرفته (نقد، پوز، چک، نسیه یا ترکیبی) و هزینه‌های مستقیم
     */
    fun checkoutAdvanced(
        paymentType: String, // "CASH", "POS", "CREDIT", "CHEQUE", "MIXED"
        cashAmount: Long = 0L,
        posAmount: Long = 0L,
        chequeAmount: Long = 0L,
        creditAmount: Long = 0L,
        cashDeskId: Long? = null,
        bankAccountId: Long? = null,
        chequeNumber: String = "",
        sayadNumber: String = "",
        chequeDueDate: String = "",
        chequeBankName: String = "",
        notes: String = "",
        onCompleted: (Long) -> Unit
    ) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true

        viewModelScope.launch {
            try {
                val items = _cartItems.value
                if (items.isEmpty()) {
                    _alertMessage.value = "سبد خرید خالی است."
                    return@launch
                }

                val subtotal = items.sumOf { it.totalPrice }
                val effectiveDiscount = minOf(maxOf(0L, _discountAmount.value), subtotal)
                val taxable = subtotal - effectiveDiscount
                val tax = if (_applyTax.value) Math.round(taxable * 0.10) else 0L
                val directExpense = maxOf(0L, _directExpenseAmount.value)
                val total = taxable + tax + directExpense

                val party = _selectedParty.value

                val effectiveCash = when (paymentType) {
                    "CASH" -> total
                    "MIXED" -> maxOf(0L, cashAmount)
                    else -> 0L
                }
                val effectivePos = when (paymentType) {
                    "POS" -> total
                    "MIXED" -> maxOf(0L, posAmount)
                    else -> 0L
                }
                val effectiveCheque = when (paymentType) {
                    "CHEQUE" -> total
                    "MIXED" -> maxOf(0L, chequeAmount)
                    else -> 0L
                }
                val immediatePaid = effectiveCash + effectivePos + effectiveCheque
                val effectiveCredit = when (paymentType) {
                    "CREDIT" -> total
                    "MIXED" -> {
                        if (creditAmount > 0) creditAmount
                        else maxOf(0L, total - immediatePaid)
                    }
                    else -> 0L
                }

                // بررسی سقف اعتبار در صورت وجود بخش نسیه
                if (effectiveCredit > 0 && !_issueAsProforma.value) {
                    if (party == null) {
                        val role = if (_currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")) "تأمین‌کننده" else "طرف حساب"
                        _alertMessage.value = "برای خرید یا فروش نسیه، مشخص کردن $role الزامی است."
                        return@launch
                    }
                    val isPurchaseDoc = _currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")
                    val isCustomer = party.partyType == "CUSTOMER" || party.partyType == "BOTH"
                    if (!isPurchaseDoc && isCustomer && !party.canAcceptCredit(effectiveCredit)) {
                        val remaining = party.remainingCredit()
                        _alertMessage.value = "هشدار سقف اعتبار! مانده بدهی مشتری از سقف مجاز فراتر می‌رود. مانده اعتبار مجاز: $remaining تومان"
                        return@launch
                    }
                }

                val effectiveCashDesk = cashDeskId ?: _selectedCashDeskId.value
                val effectiveBank = bankAccountId ?: _selectedBankAccountId.value

                val isEditing = _editingInvoiceId.value != null
                val effectiveInvoiceType = _currentInvoiceType.value
                val nextNumber = _editingInvoiceNumber.value ?: invoiceRepo.generateNextInvoiceNumber(effectiveInvoiceType)
                val today = JalaliDate.now()

                val effectivePartyId = party?.id ?: _editingPartyId.value
                val effectivePartyName = party?.name ?: _editingPartyName.value ?: if (_currentInvoiceType.value in listOf("PURCHASE", "RETURN_PURCHASE")) "تأمین‌کننده نقدی" else "مشتری نقدی"
                val effectivePartyPhone = party?.mobile?.ifBlank { party.phone } ?: _editingPartyPhone.value ?: ""
                val effectiveDate = if (isEditing) (_editingOriginalDate.value ?: today.toStandardString()) else today.toStandardString()

                val curCode = _selectedCurrencyCode.value
                val curRate = if (_invoiceCurrencyRate.value > 0) _invoiceCurrencyRate.value else 1.0

                fun toBase(amount: Long): Long {
                    if (curCode == "TOMAN" || curRate == 1.0 || amount == 0L) return amount
                    return java.math.BigDecimal(amount)
                        .multiply(java.math.BigDecimal.valueOf(curRate))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .toLong()
                }

                val totalInBase = toBase(total)
                var cashInBase = toBase(effectiveCash)
                var posInBase = toBase(effectivePos)
                var chequeInBase = toBase(effectiveCheque)
                var creditInBase = toBase(effectiveCredit)

                val sumInBase = cashInBase + posInBase + chequeInBase + creditInBase
                val roundingDiff = totalInBase - sumInBase
                if (roundingDiff != 0L) {
                    when {
                        creditInBase > 0 -> creditInBase += roundingDiff
                        posInBase > 0 -> posInBase += roundingDiff
                        cashInBase > 0 -> cashInBase += roundingDiff
                        chequeInBase > 0 -> chequeInBase += roundingDiff
                        else -> creditInBase += roundingDiff
                    }
                }

                val invoice = InvoiceEntity(
                    invoiceNumber = nextNumber,
                    invoiceType = effectiveInvoiceType,
                    jalaliDate = effectiveDate,
                    timestamp = System.currentTimeMillis(),
                    partyId = effectivePartyId,
                    partyName = effectivePartyName,
                    partyPhone = effectivePartyPhone,
                    warehouseId = 1L,
                    warehouseName = "انبار مرکزی تهران",
                    subtotalAmount = subtotal,
                    discountAmount = effectiveDiscount,
                    taxAmount = tax,
                    totalAmount = total,
                    paymentType = paymentType,
                    cashAmount = effectiveCash,
                    posAmount = effectivePos,
                    creditAmount = effectiveCredit,
                    chequeAmount = effectiveCheque,
                    cashDeskId = effectiveCashDesk,
                    bankAccountId = effectiveBank,
                    pricingMode = _pricingMode.value,
                    directExpenseAmount = directExpense,
                    directExpenseType = _directExpenseType.value,
                    isPaid = effectiveCredit == 0L,
                    status = if (_issueAsProforma.value) "PROFORMA" else "FINAL",
                    notes = notes,
                    currencyCode = curCode,
                    currencyRateToBase = curRate,
                    totalAmountInBase = totalInBase,
                    cashAmountInBase = cashInBase,
                    posAmountInBase = posInBase,
                    creditAmountInBase = creditInBase,
                    chequeAmountInBase = chequeInBase
                )

                val invoiceItems = items.map { cartItem ->
                    InvoiceItemEntity(
                        invoiceId = 0L,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        barcode = cartItem.product.barcode,
                        unitName = cartItem.unitName,
                        isPrimaryUnit = cartItem.isPrimaryUnit,
                        quantity = cartItem.quantity,
                        conversionRate = cartItem.product.conversionRate,
                        quantityInSecondary = cartItem.quantityInSecondary,
                        unitPrice = cartItem.unitPrice,
                        totalPrice = cartItem.totalPrice
                    )
                }

                val chequeEntity = if (!_issueAsProforma.value && effectiveCheque > 0 && chequeNumber.isNotBlank()) {
                    val dueEpoch = try {
                        JalaliDate.parse(chequeDueDate).toEpochMillis()
                    } catch (e: Exception) {
                        today.addDays(30).toEpochMillis()
                    }
                    val isPurchaseOrReturnSale = effectiveInvoiceType in listOf("PURCHASE", "RETURN_SALE")
                    val chequeType = if (isPurchaseOrReturnSale) "PAYABLE" else "RECEIVABLE"
                    val chequeNotes = if (isPurchaseOrReturnSale) {
                        "صدور چک صیادی بابت فاکتور $nextNumber ($effectiveInvoiceType)"
                    } else {
                        "دریافت چک صیادی بابت فاکتور $nextNumber ($effectiveInvoiceType)"
                    }
                    val defaultBankName = if (isPurchaseOrReturnSale) "حساب شرکت" else "بانک مشتری"

                    ChequeEntity(
                        chequeNumber = chequeNumber,
                        sayadNumber = sayadNumber,
                        type = chequeType,
                        partyId = effectivePartyId,
                        partyName = party?.name ?: if (isPurchaseOrReturnSale) "تأمین‌کننده فاکتور $nextNumber" else "مشتری فاکتور $nextNumber",
                        bankName = chequeBankName.ifBlank { defaultBankName },
                        branch = "مرکزی",
                        amount = effectiveCheque,
                        issueDateJalali = today.toStandardString(),
                        dueDateJalali = chequeDueDate.ifBlank { today.addDays(30).toStandardString() },
                        dueDateEpochMillis = dueEpoch,
                        status = "NORMAL",
                        notes = chequeNotes
                    )
                } else null

                val invoiceId = if (isEditing) {
                    val eId = _editingInvoiceId.value
                    if (eId == null) {
                        _alertMessage.value = "شناسه فاکتور ویرایشی معتبر نمی‌باشد."
                        return@launch
                    }
                    invoiceRepo.updateExistingInvoice(eId, invoice.copy(id = eId), invoiceItems, chequeEntity)
                    _editingInvoiceId.value = null
                    _editingInvoiceNumber.value = null
                    _editingPartyId.value = null
                    _editingPartyName.value = null
                    _editingPartyPhone.value = null
                    _editingOriginalDate.value = null
                    _editingPaymentType.value = "CASH"
                    _editingCashAmount.value = 0L
                    _editingPosAmount.value = 0L
                    _editingChequeAmount.value = 0L
                    _editingCreditAmount.value = 0L
                    _editingNotes.value = ""
                    eId
                } else {
                    invoiceRepo.createInvoice(invoice, invoiceItems, chequeEntity)
                }

                // آماده‌سازی پیش‌نمایش چاپ حرارتی
                val printable = EscPosPrinterHelper.PrintableInvoice(
                    invoiceNumber = invoice.invoiceNumber,
                    invoiceDate = today.format(includeTime = true),
                    customerName = invoice.partyName,
                    customerPhone = invoice.partyPhone,
                    warehouseName = invoice.warehouseName,
                    items = invoiceItems.map {
                        EscPosPrinterHelper.PrintableItem(
                            name = it.productName,
                            quantity = it.quantity,
                            unit = it.unitName,
                            unitPrice = it.unitPrice,
                            totalPrice = it.totalPrice
                        )
                    },
                    subtotalAmount = invoice.subtotalAmount,
                    discountAmount = invoice.discountAmount,
                    taxAmount = invoice.taxAmount,
                    totalAmount = invoice.totalAmount,
                    paymentMethod = invoice.paymentType,
                    directExpenseAmount = invoice.directExpenseAmount,
                    directExpenseType = invoice.directExpenseType,
                    note = notes,
                    invoiceType = invoice.invoiceType
                )

                _previewReceiptText.value = EscPosPrinterHelper.buildPlainTextReceipt(printable)
                clearCart()
                onCompleted(invoiceId)
            } catch (e: Exception) {
                android.util.Log.e("InvoiceViewModel", "Error in checkoutAdvanced: ${e.message}", e)
                val isForeignKeyError = e is android.database.sqlite.SQLiteConstraintException ||
                        (e.message?.contains("FOREIGN KEY", ignoreCase = true) == true) ||
                        (e.cause?.message?.contains("FOREIGN KEY", ignoreCase = true) == true)
                if (isForeignKeyError) {
                    _alertMessage.value = "خطای قید کلید خارجی در دیتابیس (SQLITE_CONSTRAINT_FOREIGNKEY): اطلاعات ارجاعی کالا یا انبار یافت نشد. به دلیل حفاظت از یکپارچگی مالی، کلیه عملیات بازگشت (Rollback) داده شد."
                } else {
                    _alertMessage.value = "خطا در ثبت نهایی سند: ${e.localizedMessage ?: "عملیات با خطا مواجه شد."}"
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun showReceiptForInvoice(inv: InvoiceWithItems) {
        val invoice = inv.invoice
        val printable = EscPosPrinterHelper.PrintableInvoice(
            invoiceNumber = invoice.invoiceNumber,
            invoiceDate = invoice.jalaliDate,
            customerName = invoice.partyName,
            customerPhone = invoice.partyPhone,
            warehouseName = invoice.warehouseName,
            items = inv.items.map {
                EscPosPrinterHelper.PrintableItem(
                    name = it.productName,
                    quantity = it.quantity,
                    unit = it.unitName,
                    unitPrice = it.unitPrice,
                    totalPrice = it.totalPrice
                )
            },
            subtotalAmount = invoice.subtotalAmount,
            discountAmount = invoice.discountAmount,
            taxAmount = invoice.taxAmount,
            totalAmount = invoice.totalAmount,
            paymentMethod = invoice.paymentType,
            directExpenseAmount = invoice.directExpenseAmount,
            directExpenseType = invoice.directExpenseType,
            note = invoice.notes,
            invoiceType = invoice.invoiceType
        )
        _previewReceiptText.value = EscPosPrinterHelper.buildPlainTextReceipt(printable)
    }

    fun quickAddParty(party: PartyEntity, onComplete: (PartyEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val newId = partyRepo.insertParty(party)
                val created = party.copy(id = newId)
                _selectedParty.value = created
                onComplete(created)
            } catch (e: Exception) {
                _alertMessage.value = "خطا در ایجاد طرف‌حساب جدید: ${e.localizedMessage ?: "ثبت ناموفق بود"}"
            }
        }
    }

    fun quickAddOrUpdateProduct(product: ProductEntity, onComplete: (ProductEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val savedProduct = if (product.id == 0L) {
                    val newId = inventoryRepo.insertProduct(product)
                    product.copy(id = newId)
                } else {
                    inventoryRepo.updateProduct(product)
                    product
                }
                onComplete(savedProduct)
            } catch (e: Exception) {
                _alertMessage.value = "خطا در ذخیره کالا: ${e.localizedMessage ?: "ثبت ناموفق بود"}"
            }
        }
    }

    fun exportPdf(context: Context, invoiceWithItems: InvoiceWithItems, storeProfile: StoreProfile) {
        val file = ExportHelper.exportInvoicePdf(context, invoiceWithItems, storeProfile, allProducts.value)
        if (file != null) {
            ExportHelper.shareFile(context, file, "application/pdf", "اشتراک‌گذاری فاکتور PDF")
        }
    }

    fun exportCsv(context: Context, invoiceWithItems: InvoiceWithItems, storeProfile: StoreProfile) {
        val file = ExportHelper.exportInvoiceCsv(context, invoiceWithItems, storeProfile)
        if (file != null) {
            ExportHelper.shareFile(context, file, "text/csv", "خروجی اکسل فاکتور")
        }
    }

    fun shareText(context: Context, invoiceWithItems: InvoiceWithItems, storeProfile: StoreProfile) {
        val inv = invoiceWithItems.invoice
        val sb = StringBuilder()
        val docTitle = when (inv.invoiceType) {
            "PURCHASE" -> "فاکتور رسمی خرید"
            "RETURN_SALE" -> "فاکتور برگشت از فروش"
            "RETURN_PURCHASE" -> "فاکتور برگشت از خرید"
            else -> "فاکتور رسمی فروش"
        }
        val isPurchaseOrReturnPurchase = inv.invoiceType in listOf("PURCHASE", "RETURN_PURCHASE")
        val partyLabel = when (inv.invoiceType) {
            "PURCHASE", "RETURN_PURCHASE" -> "تأمین‌کننده / فروشنده"
            "RETURN_SALE" -> "خریدار / مرجوع‌کننده"
            else -> "مشتری / خریدار"
        }
        sb.append("🧾 $docTitle - ${storeProfile.name}\n")
        sb.append("شماره فاکتور: ${com.example.util.NumberFormatUtils.formatInvoiceNumber(inv.invoiceNumber)}\n")
        sb.append("تاریخ: ${com.example.util.NumberFormatUtils.toPersianDigits(inv.jalaliDate)}\n")
        if (isPurchaseOrReturnPurchase) {
            sb.append("$partyLabel: ${inv.partyName}\n")
            sb.append("خریدار: ${storeProfile.name}\n")
        } else {
            sb.append("فروشنده: ${storeProfile.name}\n")
            sb.append("$partyLabel: ${inv.partyName}\n")
        }
        sb.append("-----------------------------\n")
        invoiceWithItems.items.forEachIndexed { i, itm ->
            sb.append("${i + 1}. ${itm.productName} | ${itm.quantity} ${itm.unitName} × ${com.example.util.NumberFormatUtils.formatPrice(itm.unitPrice)} = ${com.example.util.NumberFormatUtils.formatPrice(itm.totalPrice)}\n")
        }
        sb.append("-----------------------------\n")
        sb.append("جمع اقلام: ${com.example.util.NumberFormatUtils.formatPrice(inv.subtotalAmount)}\n")
        if (inv.discountAmount > 0) sb.append("تخفیف: ${com.example.util.NumberFormatUtils.formatPrice(inv.discountAmount)}\n")
        if (inv.taxAmount > 0) sb.append("مالیات ارزش افزوده: ${com.example.util.NumberFormatUtils.formatPrice(inv.taxAmount)}\n")
        if (inv.directExpenseAmount > 0) {
            val expLabel = if (inv.directExpenseType.isNotBlank()) "هزینه مستقیم (${inv.directExpenseType})" else "هزینه‌های مستقیم و جانبی"
            sb.append("$expLabel: ${com.example.util.NumberFormatUtils.formatPrice(inv.directExpenseAmount)}\n")
        }
        sb.append("مبلغ نهایی: ${com.example.util.NumberFormatUtils.formatPrice(inv.totalAmount)}\n")
        sb.append("نوع تسویه: ${com.example.util.NumberFormatUtils.formatPaymentType(inv.paymentType)}\n")
        if (!isPurchaseOrReturnPurchase && storeProfile.defaultBankCard.isNotBlank()) {
            sb.append("شماره کارت جهت واریز: ${storeProfile.defaultBankCard}\n")
        }
        val closingText = when (inv.invoiceType) {
            "PURCHASE" -> "تأییدیه دریافت و ورود کالا به انبار"
            "RETURN_SALE" -> "رسید مرجوعی کالا و استرداد وجه"
            "RETURN_PURCHASE" -> "رسید عودت کالا به تأمین‌کننده"
            else -> "با تشکر از حسن انتخاب شما."
        }
        sb.append(closingText)
        ExportHelper.shareText(context, sb.toString(), "ارسال متنی $docTitle")
    }
}

