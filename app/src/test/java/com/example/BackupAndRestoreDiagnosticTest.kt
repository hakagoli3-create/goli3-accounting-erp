package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.AppSecurityAndSettingsManager
import com.example.data.model.PartyEntity
import com.example.data.model.ProductEntity
import com.example.data.model.StoreProfile
import com.example.ui.viewmodel.SettingsViewModel
import com.example.util.DatabaseBackupHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndRestoreDiagnosticTest {

    @org.junit.Before
    fun setUp() {
        AppDatabase.closeInstance()
    }

    @org.junit.After
    fun tearDown() {
        AppDatabase.closeInstance()
    }

    @Test
    fun `diagnostic test store profile backup and restore reproduction`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val securityManager = AppSecurityAndSettingsManager.getInstance(context)
            val viewModel = SettingsViewModel(securityManager)

            // ۱. مقدار منحصربه‌فرد در مشخصات فروشگاه
            val testName = "آزمون پایدار ۷۳۱۹"
            viewModel.onNameChange(testName)
            viewModel.onManagerNameChange("مدیر تست ۷۳۱۹")
            viewModel.saveProfile()

            // بررسی ذخیره در منبع
            assertEquals(testName, securityManager.storeProfile.value.name)

            // شبیه‌سازی بستن و باز کردن با ایجاد ViewModel جدید
            val reloadedViewModel = SettingsViewModel(securityManager)
            assertEquals(testName, reloadedViewModel.uiState.value.name)

            // ۶. تهیه نسخه پشتیبان
            val backupResult = DatabaseBackupHelper.createDatabaseBackup(context)
            assertTrue("Backup creation should succeed, error was: ${backupResult.exceptionOrNull()?.message}", backupResult.isSuccess)
            val backupFile = backupResult.getOrNull()
            assertNotNull(backupFile)
            assertTrue(backupFile!!.exists())

            // ۷. ثبت مقدار متفاوت تا مشخص شود بازیابی تغییر ایجاد می‌کند
            viewModel.onNameChange("فروشگاه تغییر یافته ۹۹۹۹")
            viewModel.saveProfile()
            assertEquals("فروشگاه تغییر یافته ۹۹۹۹", securityManager.storeProfile.value.name)

            // ۸. بازیابی نسخه پشتیبان
            val restoreResult = DatabaseBackupHelper.restoreDatabaseFromFile(context, Uri.fromFile(backupFile))
            assertTrue("Restore should succeed", restoreResult.isSuccess)

            // ۹. بررسی بازگشت مقدار قبلی «آزمون پایدار ۷۳۱۹»
            val postRestoreManager = AppSecurityAndSettingsManager.getInstance(context)
            assertEquals("نام فروشگاه باید پس از بازیابی دقیقاً به مقدار نسخه پشتیبان بازگردد", testName, postRestoreManager.storeProfile.value.name)
        }
    }

    @Test
    fun `diagnostic test party and product backup and restore`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = AppDatabase.getInstance(context)

            // درج مشتری و کالا با شناسه‌های مشخص
            val testParty = PartyEntity(
                code = "C-7319",
                name = "مشتری پایدار ۷۳۱۹",
                companyName = "شرکت آزمون ۷۳۱۹",
                mobile = "09127319000",
                phone = "0217319000",
                address = "تهران، خیابان تست",
                nationalCode = "0073190000",
                economicCode = "4117319000",
                partyType = "CUSTOMER",
                creditLimit = 10000000,
                currentBalance = 5000000,
                isActive = true,
                notes = "مشتری نسخه پشتیبان"
            )
            val partyId = db.partyDao().insertParty(testParty)

            val testProduct = ProductEntity(
                code = "P-7319",
                barcode = "626073190000",
                name = "روغن زیتون ۷۳۱۹",
                category = "مواد غذایی",
                primaryUnit = "کارتن",
                secondaryUnit = "عدد",
                conversionRate = 12.0,
                currentStockInSecondary = 120.0,
                minStockThreshold = 10.0,
                buyPrice = 150000,
                retailPrice = 200000,
                wholesalePrice = 180000,
                partnerPrice = 175000,
                description = "کالای نسخه پشتیبان"
            )
            val productId = db.inventoryDao().insertProduct(testProduct)

            // تهیه پشتیبان
            val backupResult = DatabaseBackupHelper.createDatabaseBackup(context)
            assertTrue(backupResult.isSuccess)
            val backupFile = backupResult.getOrNull()!!

            // تغییر داده‌ها پس از پشتیبان‌گیری
            db.partyDao().insertParty(testParty.copy(id = partyId, name = "مشتری تغییر یافته"))
            db.inventoryDao().insertProduct(testProduct.copy(id = productId, name = "کالای تغییر یافته"))

            assertEquals("مشتری تغییر یافته", db.partyDao().getPartyById(partyId)?.name)
            assertEquals("کالای تغییر یافته", db.inventoryDao().getProductById(productId)?.name)

            // بازیابی نسخه پشتیبان
            val restoreResult = DatabaseBackupHelper.restoreDatabaseFromFile(context, Uri.fromFile(backupFile))
            assertTrue(restoreResult.isSuccess)

            // بررسی بازیابی در دیتابیس بازگشایی‌شده
            val restoredDb = AppDatabase.getInstance(context)
            val restoredParty = restoredDb.partyDao().getPartyById(partyId)
            val restoredProduct = restoredDb.inventoryDao().getProductById(productId)

            assertNotNull("مشتری باید بازیابی شده باشد", restoredParty)
            assertEquals("مشتری پایدار ۷۳۱۹", restoredParty?.name)
            assertNotNull("کالا باید بازیابی شده باشد", restoredProduct)
            assertEquals("روغن زیتون ۷۳۱۹", restoredProduct?.name)
        }
    }

    @Test
    fun `diagnostic test corrupted backup is rejected and current data preserved safely`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val securityManager = AppSecurityAndSettingsManager.getInstance(context)
            securityManager.updateProfile(StoreProfile(name = "فروشگاه ایمن قبل از تست"))

            val db = AppDatabase.getInstance(context)
            val partyId = db.partyDao().insertParty(
                PartyEntity(
                    code = "SAFE-1",
                    name = "مشتری محفوظ",
                    companyName = "",
                    mobile = "",
                    phone = "",
                    address = "",
                    nationalCode = "",
                    economicCode = "",
                    partyType = "CUSTOMER",
                    creditLimit = 0,
                    currentBalance = 1000,
                    isActive = true,
                    notes = ""
                )
            )

            // فایل مخدوش
            val corruptFile = java.io.File(context.cacheDir, "corrupt_backup.db")
            corruptFile.writeBytes("This is not a sqlite database at all!".toByteArray())

            val restoreResult = DatabaseBackupHelper.restoreDatabaseFromFile(context, Uri.fromFile(corruptFile))
            org.junit.Assert.assertFalse("Restore of corrupted file must fail", restoreResult.isSuccess)
            assertTrue(restoreResult.exceptionOrNull()?.message?.contains("معتبر") == true || restoreResult.exceptionOrNull()?.message?.contains("SQLite") == true)

            // اطمینان از حفظ کامل اطلاعات جاری
            val currentDb = AppDatabase.getInstance(context)
            val party = currentDb.partyDao().getPartyById(partyId)
            assertNotNull("داده‌های جاری نباید با فایل مخدوش حذف شوند", party)
            assertEquals("مشتری محفوظ", party?.name)
            assertEquals("فروشگاه ایمن قبل از تست", securityManager.storeProfile.value.name)
            corruptFile.delete()
        }
    }
}
