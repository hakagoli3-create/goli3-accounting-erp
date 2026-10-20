package com.example.data.database

import com.example.data.model.AccountGroupEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ExchangeRateEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.IncomeCategoryEntity
import com.example.data.model.SubsidiaryAccountEntity
import com.example.data.model.TreasuryCurrencyBalanceEntity
import com.example.data.model.UserEntity
import com.example.data.model.WarehouseEntity
import com.example.util.JalaliDate

/**
 * مدیریت تزریق داده‌های ساختاری پایه سیستم حسابداری و بازرگانی گلی
 * 
 * اصول طراحی پاک (Clean Architecture & Clean Slate):
 * ۱. عدم تزریق هرگونه داده، تراکنش، شماره تماس، کارت بانکی، کالا یا طرف‌حساب ساختگی/تستی
 * ۲. راه‌اندازی ساختار استاندارد کدینگ حسابداری چندسطحی (گروه‌ها، کل‌ها و معین‌های استاندارد)
 * ۳. ایجاد انبار و صندوق اصلی پیش‌فرض با مانده صفر جهت شروع به کار فوری نرم‌افزار
 * ۴. سرفصل‌های موضوعی استاندارد هزینه‌ها و درآمدها
 */
object DatabaseSeeder {

    suspend fun seedDefaultData(db: AppDatabase) {
        val todayStr = JalaliDate.now().toStandardString()

        // ۱. گروه‌های اصلی حسابداری (Account Groups) بر اساس استانداردهای رسمی
        val groups = listOf(
            AccountGroupEntity(1, "دارایی‌های جاری", "DEBIT"),
            AccountGroupEntity(2, "دارایی‌های غیرجاری", "DEBIT"),
            AccountGroupEntity(3, "بدهی‌های جاری", "CREDIT"),
            AccountGroupEntity(4, "بدهی‌های بلندمدت", "CREDIT"),
            AccountGroupEntity(5, "حقوق صاحبان سهام", "CREDIT"),
            AccountGroupEntity(6, "درآمدها و فروش", "CREDIT"),
            AccountGroupEntity(7, "بهای تمام شده کالای فروش رفته", "DEBIT"),
            AccountGroupEntity(8, "هزینه‌های عمومی و اداری", "DEBIT")
        )
        db.accountingDao().insertAccountGroups(groups)

        // ۲. حساب‌های کل (General Accounts)
        val generals = listOf(
            GeneralAccountEntity(101, 1, "موجودی نقد و بانک", "DEBIT"),
            GeneralAccountEntity(102, 1, "حساب‌ها و اسناد دریافتنی تجاری", "DEBIT"),
            GeneralAccountEntity(103, 1, "موجودی کالا و انبار", "DEBIT"),
            GeneralAccountEntity(201, 2, "اموال، ماشین‌آلات و تجهیزات", "DEBIT"),
            GeneralAccountEntity(301, 3, "حساب‌ها و اسناد پرداختنی تجاری", "CREDIT"),
            GeneralAccountEntity(302, 3, "پیش‌دریافت‌ها و مالیات پرداختنی", "CREDIT"),
            GeneralAccountEntity(501, 5, "سرمایه اولیه موسسه", "CREDIT"),
            GeneralAccountEntity(601, 6, "درآمد حاصل از فروش کالا و خدمات", "CREDIT"),
            GeneralAccountEntity(701, 7, "بهای تمام شده کالای فروش رفته", "DEBIT"),
            GeneralAccountEntity(801, 8, "هزینه‌های حقوق، کرایه و ملزومات", "DEBIT")
        )
        db.accountingDao().insertGeneralAccounts(generals)

        // ۳. حساب‌های معین استاندارد (Subsidiary Accounts)
        val subsidiaries = listOf(
            SubsidiaryAccountEntity(10101, 101, "صندوق‌های فروشگاه و دخل", "DEBIT"),
            SubsidiaryAccountEntity(10102, 101, "حساب‌های بانکی و پوز POS", "DEBIT"),
            SubsidiaryAccountEntity(10103, 101, "تنخواه‌گردان مدیریت", "DEBIT"),
            SubsidiaryAccountEntity(10201, 102, "بدهکاران تجاری (مشتریان نسیه)", "DEBIT"),
            SubsidiaryAccountEntity(10202, 102, "اسناد دریافتنی (چک‌های اشخاص)", "DEBIT"),
            SubsidiaryAccountEntity(10301, 103, "موجودی انبار مرکزی", "DEBIT"),
            SubsidiaryAccountEntity(10302, 103, "موجودی انبار فروشگاه", "DEBIT"),
            SubsidiaryAccountEntity(30101, 301, "بستانکاران تجاری (تامین‌کنندگان کالا)", "CREDIT"),
            SubsidiaryAccountEntity(30102, 301, "اسناد پرداختنی (چک‌های صادره)", "CREDIT"),
            SubsidiaryAccountEntity(30201, 302, "مالیات بر ارزش افزوده و عوارض پرداختنی", "CREDIT"),
            SubsidiaryAccountEntity(50101, 501, "سرمایه ثبت شده شرکت", "CREDIT"),
            SubsidiaryAccountEntity(60101, 601, "فروش نقدی و اعتباری کالا", "CREDIT"),
            SubsidiaryAccountEntity(60102, 601, "برگشت از فروش و تخفیفات نقدی", "DEBIT"),
            SubsidiaryAccountEntity(70101, 701, "قیمت تمام شده اجناس فروش رفته", "DEBIT"),
            SubsidiaryAccountEntity(80101, 801, "هزینه‌های پرسنلی و حقوق", "DEBIT"),
            SubsidiaryAccountEntity(80102, 801, "کرایه حمل، ملزومات و بسته‌بندی", "DEBIT")
        )
        db.accountingDao().insertSubsidiaryAccounts(subsidiaries)

        // ۴. انبار اصلی پیش‌فرض (کاملاً تمیز و آماده ورود اطلاعات توسط کاربر)
        val defaultWarehouse = WarehouseEntity(
            id = 1,
            code = "W01",
            name = "انبار مرکزی",
            managerName = "",
            address = "",
            phone = "",
            isDefault = true
        )
        db.inventoryDao().insertWarehouses(listOf(defaultWarehouse))

        // ۵. صندوق اصلی پیش‌فرض با موجودی اولیه صفر
        val defaultCashDesk = CashDeskEntity(
            id = 1,
            code = "C01",
            name = "صندوق اصلی",
            currentBalance = 0L,
            keeperName = "",
            note = ""
        )
        db.treasuryDao().insertCashDesks(listOf(defaultCashDesk))

        // ۶. سرفصل‌های پیش‌فرض هزینه‌های جاری (بدون ثبت هیچ تراکنش جعلی)
        val defaultExpenseCategories = listOf(
            ExpenseCategoryEntity(1, "EXP-01", "اجاره محل و انبار", "اجاره ماهانه دفتر مرکزی، مغازه و انبارهای نگهداری کالا", "Store"),
            ExpenseCategoryEntity(2, "EXP-02", "حقوق و دستمزد پرسنل", "حقوق پایه، پاداش، پورسانت فروش و بیمه کارکنان", "People"),
            ExpenseCategoryEntity(3, "EXP-03", "کرایه حمل و باربری", "هزینه پیک موتوری، وانت‌بار، باربری بین شهری و انبارداری", "LocalShipping"),
            ExpenseCategoryEntity(4, "EXP-04", "ملزومات و بسته‌بندی", "خرید کارتن، چسب، نایلون، کاغذ چاپگر حرارتی و اداری", "Inventory2"),
            ExpenseCategoryEntity(5, "EXP-05", "تبلیغات و بازاریابی", "هزینه تبلیغات پیامکی، بنر، فضای مجازی و کاتالوگ", "Campaign"),
            ExpenseCategoryEntity(6, "EXP-06", "قبوض و انشعابات", "قبوض برق، گاز، آب، تلفن ثابت و اینترنت پرسرعت", "Receipt"),
            ExpenseCategoryEntity(7, "EXP-07", "پذیرایی و تشریفات", "خرید چای، قند، قهوه و پذیرایی از مشتریان و پرسنل", "Coffee"),
            ExpenseCategoryEntity(8, "EXP-08", "تعمیرات و نگهداری", "سرویس کولر، تعمیر کامپیوترها، ترازو و خودرو باربری", "Build"),
            ExpenseCategoryEntity(9, "EXP-09", "هزینه‌های متفرقه اداری", "سایر هزینه‌های پیش‌بینی‌نشده جاری شرکت", "MoreHoriz")
        )
        db.expenseAndIncomeDao().insertExpenseCategories(defaultExpenseCategories)

        // ۷. سرفصل‌های پیش‌فرض درآمدهای متفرقه (بدون ثبت هیچ تراکنش جعلی)
        val defaultIncomeCategories = listOf(
            IncomeCategoryEntity(1, "INC-01", "درآمدهای متفرقه و غیرعملیاتی", "کلیه درآمدهای خارج از چرخه اصلی فروش کالا", "TrendingUp"),
            IncomeCategoryEntity(2, "INC-02", "سود سپرده بانکی", "سود واریزی ماهانه حساب‌های سرمایه‌گذاری", "AccountBalance"),
            IncomeCategoryEntity(3, "INC-03", "خدمات و مشاورات بازرگانی", "کارمزد ارائه خدمات، مشاوره‌های توزیع و حق‌العمل‌کاری", "Handshake"),
            IncomeCategoryEntity(4, "INC-04", "فروش ضایعات و کارتن", "درآمد ناشی از فروش جعبه‌های خالی، پالت و ضایعات انبار", "Recycling"),
            IncomeCategoryEntity(5, "INC-05", "اجاره دریافتی انبار/ملک", "اجاره بخشی از فضای مازاد انبار به سایر همکاران", "HomeWork")
        )
        db.expenseAndIncomeDao().insertIncomeCategories(defaultIncomeCategories)

        // ۸. کاربر مدیر سیستم پیش‌فرض با مشخصات پاک
        ensureDefaultUsers(db)

        // ۹. نسبت تبدیل ثابت و استاندارد ریال به تومان
        try {
            val standardRates = listOf(
                ExchangeRateEntity(
                    id = 1,
                    sourceCurrency = "IRR",
                    targetCurrency = "TOMAN",
                    rate = 0.1,
                    inverseRate = 10.0,
                    effectiveDateJalali = todayStr,
                    notes = "نسبت ثابت ریال به تومان",
                    recordedBy = "سیستم خودکار"
                )
            )
            db.exchangeRateDao().insertRates(standardRates)
        } catch (_: Exception) {}

        // ۱۰. ثبت ردیف مانده اولیه ارز پایه برای صندوق اصلی با مقدار صفر
        try {
            db.treasuryDao().insertOrUpdateCurrencyBalance(
                TreasuryCurrencyBalanceEntity(
                    id = 1,
                    accountType = "CASH",
                    accountId = 1,
                    currencyCode = "TOMAN",
                    balance = 0.0
                )
            )
        } catch (_: Exception) {}
    }

    suspend fun ensureDefaultUsers(db: AppDatabase) {
        try {
            val adminUser = db.userDao().getUserByUsername("admin")
            if (adminUser == null) {
                db.userDao().insertUser(
                    UserEntity(
                        username = "admin",
                        passwordHash = "1234",
                        fullName = "مدیر سیستم",
                        role = "ADMIN",
                        mobile = "",
                        isActive = true
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore if already existing
        }
    }

    suspend fun ensureStoreSettings(db: AppDatabase, context: android.content.Context) {
        try {
            val existing = db.storeSettingsDao().getStoreSettings()
            if (existing == null) {
                val sec = com.example.data.model.AppSecurityAndSettingsManager.getInstance(context)
                val profile = sec.storeProfile.value
                val currenciesJson = sec.getCurrenciesAsJson()
                db.storeSettingsDao().insertOrUpdate(
                    com.example.data.model.StoreSettingsEntity(
                        id = 1,
                        name = profile.name,
                        managerName = profile.managerName,
                        phone = profile.phone,
                        mobile = profile.mobile,
                        address = profile.address,
                        nationalCode = profile.nationalCode,
                        economicCode = profile.economicCode,
                        defaultBankCard = profile.defaultBankCard,
                        defaultSheba = profile.defaultSheba,
                        taxRatePercent = profile.taxRatePercent,
                        wholesaleDiscountLimitPercent = profile.wholesaleDiscountLimitPercent,
                        currenciesJson = currenciesJson,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {}
    }
}
