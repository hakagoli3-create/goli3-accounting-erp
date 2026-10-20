package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * دستیار پشتیبان‌گیری و بازیابی امن و قابل اتکای پایگاه داده حسابداری گلی
 * ویژگی‌ها:
 * 1. اجرای فلش کامل و Truncate روی فایل لاگ WAL جهت انتقال 100% تراکنش‌های رم به فایل اصلی دیتابیس
 * 2. استفاده از فایل موقت (Staging) در عملیات بازیابی (Restore) و بررسی سلامت هدر و یکپارچگی پیش از جایگزینی
 * 3. تهیه پشتیبان خودکار اضطراری (Safety Backup) قبل از هرگونه بازنویسی دیتابیس
 * 4. بستن ایمن نمونه فعال Room و بازسازی آن پس از بازیابی جهت جلوگیری از خطای قفل دیتابیس
 */
object DatabaseBackupHelper {

    private const val DB_NAME = "goli_accounting_erp.db"

    /**
     * تهیه نسخه پشتیبان کامل از دیتابیس بر روی حافظه داخلی دستگاه
     */
    suspend fun createDatabaseBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 1. فلش کامل فایل‌های WAL به فایل اصلی دیتابیس با TRUNCATE
            val appDb = AppDatabase.getInstance(context)
            try {
                val db = appDb.openHelper.writableDatabase
                val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                cursor.moveToFirst()
                cursor.close()
            } catch (e: Exception) {
                android.util.Log.w("DatabaseBackupHelper", "Warning checkpointing WAL: ${e.localizedMessage}")
            }

            val currentDbPath = context.getDatabasePath(DB_NAME)
            if (!currentDbPath.exists() || currentDbPath.length() == 0L) {
                return@withContext Result.failure(Exception("فایل اصلی پایگاه داده یافت نشد یا خالی است."))
            }

            // 2. ساخت پوشه پشتیبان‌ها
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = JalaliDate.now().toStandardString().replace("/", "-") +
                    "_" + (System.currentTimeMillis() % 10000)
            val backupFile = File(backupDir, "Goli_Accounting_Backup_$timestamp.db")

            // 3. کپی امن اطلاعات با Buffer
            FileInputStream(currentDbPath).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                    output.flush()
                    output.fd.sync()
                }
            }

            // 4. اعتبارسنجی فایل پشتیبان ایجاد شده
            val integrity = DatabaseSecurityManager.verifyDatabaseIntegrity(backupFile)
            if (!integrity.first) {
                backupFile.delete()
                return@withContext Result.failure(Exception("اعتبارسنجی فایل پشتیبان ناموفق بود: ${integrity.second}"))
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * اشتراک‌گذاری فایل پشتیبان با سایر برنامه‌ها (تلگرام، واتساپ، درایو، ایمیل)
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            backupFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "پشتیبان دیتابیس حسابداری گلی")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "اشتراک‌گذاری فایل پشتیبان (Backup)")
        context.startActivity(chooser)
    }

    /**
     * بازیابی امن پایگاه داده با تست پیشگیرانه یکپارچگی و بازگشت اضطراری در صورت بروز خطا
     */
    suspend fun restoreDatabaseFromFile(context: Context, backupUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val currentDbPath = context.getDatabasePath(DB_NAME)
        val dbDir = currentDbPath.parentFile ?: context.filesDir
        val walFile = File(dbDir, "$DB_NAME-wal")
        val shmFile = File(dbDir, "$DB_NAME-shm")

        val stagingFile = File(dbDir, "staging_restore_${System.currentTimeMillis()}.tmp")
        val safetyBackupFile = File(dbDir, "pre_restore_safety_backup.db")

        try {
            // ۱. کپی جریان ورودی فایل انتخابی کاربر به فایل موقت (Staging)
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                    output.flush()
                    output.fd.sync()
                }
            } ?: return@withContext Result.failure(Exception("امکان بازگشایی و خواندن فایل پشتیبان وجود ندارد."))

            // ۲. بررسی دقیق یکپارچگی فایل موقت قبل از هرگونه دستکاری دیتابیس اصلی
            val integrity = DatabaseSecurityManager.verifyDatabaseIntegrity(stagingFile)
            if (!integrity.first) {
                stagingFile.delete()
                return@withContext Result.failure(Exception("فایل انتخاب شده خراب، نامعتبر یا ناسازگار است: ${integrity.second}"))
            }

            // ۳. تهیه نسخه پشتیبان اضطراری از دیتابیس فعلی جهت بازگشت در صورت بروز هرگونه مشکل
            if (currentDbPath.exists() && currentDbPath.length() > 0) {
                try {
                    FileInputStream(currentDbPath).use { input ->
                        FileOutputStream(safetyBackupFile).use { output ->
                            input.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                } catch (_: Exception) {}
            }

            // ۴. بستن کامل تمامی کانکشن‌ها و استخرهای اتصالات Room
            AppDatabase.closeInstance()

            // ۵. پاکسازی فایل‌های ژورنال قدیمی WAL و SHM
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (currentDbPath.exists()) currentDbPath.delete()

            // ۶. انتقال اتمیک یا کپی فایل اعتبارسنجی شده به مسیر نهایی پایگاه داده
            val renamed = stagingFile.renameTo(currentDbPath)
            if (!renamed) {
                FileInputStream(stagingFile).use { input ->
                    FileOutputStream(currentDbPath).use { output ->
                        input.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
                }
                stagingFile.delete()
            }

            // ۷. راه‌اندازی مجدد و بررسی دسترسی Room به دیتابیس بازیابی شده
            val newDb = AppDatabase.getInstance(context)
            // اجرای کوئری تست برای اطمینان از سلامت دسترسی
            newDb.openHelper.readableDatabase.version

            // حذف نسخه پشتیبان اضطراری پس از موفقیت قطعی
            if (safetyBackupFile.exists()) {
                safetyBackupFile.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            // مکانیزم خودکار بازگشت اضطراری (Rollback) به نسخه قبل از تلاش برای بازیابی
            try {
                if (safetyBackupFile.exists()) {
                    AppDatabase.closeInstance()
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                    if (currentDbPath.exists()) currentDbPath.delete()
                    safetyBackupFile.renameTo(currentDbPath)
                    AppDatabase.getInstance(context)
                }
            } catch (_: Exception) {}

            stagingFile.delete()
            Result.failure(Exception("خطا در بازنشانی پایگاه داده: ${e.localizedMessage ?: "فایل مخدوش است"}"))
        }
    }
}
