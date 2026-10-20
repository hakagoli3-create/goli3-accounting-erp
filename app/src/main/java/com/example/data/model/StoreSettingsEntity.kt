package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * موجودیت دائمی تنظیمات و مشخصات بازرگانی فروشگاه در پایگاه داده SQLite / Room
 * این جدول تضمین می‌کند که تمامی مشخصات فروشگاه به صورت محلی و اتمیک همراه با فایل اصلی پایگاه داده
 * ذخیره شده و در نسخه‌های پشتیبان (Backup) به طور کامل قرار گیرند.
 */
@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "",
    val managerName: String = "",
    val phone: String = "",
    val mobile: String = "",
    val address: String = "",
    val nationalCode: String = "",
    val economicCode: String = "",
    val defaultBankCard: String = "",
    val defaultSheba: String = "",
    val taxRatePercent: Int = 10,
    val wholesaleDiscountLimitPercent: Int = 25,
    val currenciesJson: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
