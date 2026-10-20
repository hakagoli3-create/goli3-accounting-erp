package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSecurityAndSettingsManager
import com.example.data.model.StoreProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * وضعیت داده‌های فرم مشخصات بازرگانی و تنظیمات فروشگاه
 */
data class StoreProfileUiState(
    val name: String = "",
    val managerName: String = "",
    val phone: String = "",
    val mobile: String = "",
    val address: String = "",
    val nationalCode: String = "",
    val economicCode: String = "",
    val defaultBankCard: String = "",
    val defaultSheba: String = "",
    val taxRate: String = "10",
    val wholesaleDiscountLimit: String = "25",
    val isSavedSuccess: Boolean = false
)

/**
 * مدیریت یکپارچه State تنظیمات و مشخصات بازرگانی
 * ویژگی‌ها:
 * ۱. نگهداری پایدار مقادیر ورودی در ViewModel جهت جلوگیری از ریست شدن در سوییچ بین تب‌ها
 * ۲. ذخیره‌سازی بلادرنگ و قطعی (Synchronous Commit) در حافظه دائمی
 * ۳. شروع با مقادیر خالی در اولین اجرا بدون تزریق داده‌های ساختگی
 */
class SettingsViewModel(
    private val securityManager: AppSecurityAndSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreProfileUiState())
    val uiState: StateFlow<StoreProfileUiState> = _uiState.asStateFlow()

    init {
        loadCurrentProfile()
        viewModelScope.launch {
            securityManager.storeProfile.collect { profile ->
                _uiState.update { current ->
                    current.copy(
                        name = profile.name,
                        managerName = profile.managerName,
                        phone = profile.phone,
                        mobile = profile.mobile,
                        address = profile.address,
                        nationalCode = profile.nationalCode,
                        economicCode = profile.economicCode,
                        defaultBankCard = profile.defaultBankCard,
                        defaultSheba = profile.defaultSheba,
                        taxRate = profile.taxRatePercent.toString(),
                        wholesaleDiscountLimit = profile.wholesaleDiscountLimitPercent.toString()
                    )
                }
            }
        }
    }

    /**
     * بارگذاری اطلاعات جاری ذخیره‌شده
     */
    fun loadCurrentProfile() {
        val current = securityManager.storeProfile.value
        _uiState.value = StoreProfileUiState(
            name = current.name,
            managerName = current.managerName,
            phone = current.phone,
            mobile = current.mobile,
            address = current.address,
            nationalCode = current.nationalCode,
            economicCode = current.economicCode,
            defaultBankCard = current.defaultBankCard,
            defaultSheba = current.defaultSheba,
            taxRate = current.taxRatePercent.toString(),
            wholesaleDiscountLimit = current.wholesaleDiscountLimitPercent.toString()
        )
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, isSavedSuccess = false) }
        persistState()
    }

    fun onManagerNameChange(value: String) {
        _uiState.update { it.copy(managerName = value, isSavedSuccess = false) }
        persistState()
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, isSavedSuccess = false) }
        persistState()
    }

    fun onMobileChange(value: String) {
        _uiState.update { it.copy(mobile = value, isSavedSuccess = false) }
        persistState()
    }

    fun onAddressChange(value: String) {
        _uiState.update { it.copy(address = value, isSavedSuccess = false) }
        persistState()
    }

    fun onNationalCodeChange(value: String) {
        _uiState.update { it.copy(nationalCode = value, isSavedSuccess = false) }
        persistState()
    }

    fun onEconomicCodeChange(value: String) {
        _uiState.update { it.copy(economicCode = value, isSavedSuccess = false) }
        persistState()
    }

    fun onDefaultBankCardChange(value: String) {
        _uiState.update { it.copy(defaultBankCard = value, isSavedSuccess = false) }
        persistState()
    }

    fun onDefaultShebaChange(value: String) {
        _uiState.update { it.copy(defaultSheba = value, isSavedSuccess = false) }
        persistState()
    }

    fun onTaxRateChange(value: String) {
        _uiState.update { it.copy(taxRate = value, isSavedSuccess = false) }
        persistState()
    }

    fun onWholesaleDiscountLimitChange(value: String) {
        _uiState.update { it.copy(wholesaleDiscountLimit = value, isSavedSuccess = false) }
        persistState()
    }

    /**
     * ذخیره دائمی مشخصات بازرگانی در دیتابیس / SharedPreferences با Commit قطعی
     */
    private fun persistState() {
        val s = _uiState.value
        val profile = StoreProfile(
            name = s.name,
            managerName = s.managerName,
            phone = s.phone,
            mobile = s.mobile,
            address = s.address,
            nationalCode = s.nationalCode,
            economicCode = s.economicCode,
            defaultBankCard = s.defaultBankCard,
            defaultSheba = s.defaultSheba,
            taxRatePercent = s.taxRate.toIntOrNull() ?: 10,
            wholesaleDiscountLimitPercent = s.wholesaleDiscountLimit.toIntOrNull() ?: 25
        )
        securityManager.updateProfile(profile)
    }

    /**
     * ذخیره‌سازی دستی با تاییدیه کاربر
     */
    fun saveProfile(): Boolean {
        persistState()
        _uiState.update { it.copy(isSavedSuccess = true) }
        return true
    }
}
