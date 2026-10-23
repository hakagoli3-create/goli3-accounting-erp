package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * نرخ تبدیل ثبت‌شده دستی بین ارزها با تاریخچه و تاریخ اعتبار
 */
@Entity(
    tableName = "exchange_rates",
    indices = [
        Index("sourceCurrency"),
        Index("targetCurrency"),
        Index("effectiveDateJalali")
    ]
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceCurrency: String, // مثلا "USD"
    val targetCurrency: String, // مثلا "TOMAN"
    val rate: Double,           // نرخ تبدیل: ۱ واحد ارز مبدا = چند واحد ارز مقصد
    val inverseRate: Double = if (rate > 0) 1.0 / rate else 0.0, // نرخ معکوس
    val effectiveDateJalali: String, // تاریخ اعتبار شمسی مثلا "1405/01/30"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val recordedBy: String = "مدیر سیستم",
    val isActive: Boolean = true
)

/**
 * موجودی تفکیکی صندوق‌ها و حساب‌های بانکی برای هر ارز
 */
@Entity(
    tableName = "treasury_currency_balances",
    indices = [
        Index("accountType"),
        Index("accountId"),
        Index("currencyCode")
    ]
)
data class TreasuryCurrencyBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountType: String, // "CASH" (صندوق) or "BANK" (حساب بانکی)
    val accountId: Long,     // شناسه صندوق یا حساب بانکی
    val currencyCode: String, // "TOMAN", "USD", "EUR", "TRY", "IRR", "IQD", ...
    val balance: Double = 0.0 // موجودی به این ارز
)

/**
 * مدل انتقال داده (DTO) برای گزارش کل دارایی‌ها به تفکیک نوع ارز
 */
data class CurrencyAssetDto(
    val currencyType: String,
    val totalAmount: Double
) {
    /**
     * نام فارسی و عنوان نمایشی استاندارد ارز
     */
    val displayTitle: String
        get() = when (currencyType.trim().uppercase()) {
            "TOMAN", "تومان" -> "تومان"
            "USD", "دلار" -> "دلار آمریکا"
            "EUR", "یورو" -> "یورو"
            "TRY", "لیر" -> "لیر ترکیه"
            "AED", "درهم" -> "درهم امارات"
            "IQD", "دینار" -> "دینار عراق"
            "IRR", "ریال" -> "ریال"
            "GBP", "پوند" -> "پوند"
            "CNY", "یوان" -> "یوان"
            else -> currencyType
        }

    val symbol: String
        get() = when (currencyType.trim().uppercase()) {
            "TOMAN", "تومان" -> "تومان"
            "USD", "دلار" -> "$"
            "EUR", "یورو" -> "€"
            "TRY", "لیر" -> "₺"
            "AED", "درهم" -> "د.إ"
            "IQD", "دینار" -> "ع.د"
            "IRR", "ریال" -> "ریال"
            "GBP", "پوند" -> "£"
            "CNY", "یوان" -> "¥"
            else -> ""
        }
}
