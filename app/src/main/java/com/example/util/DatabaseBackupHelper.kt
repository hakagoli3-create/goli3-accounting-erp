package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * دستیار جامع و امن پشتیبان‌گیری و بازیابی پایگاه داده حسابداری گلی (Room / SQLite)
 * 
 * برطرف‌کننده مشکلات پایداری و امنیت:
 * ۱. سازگاری کامل با حافظه مجاز نرم‌افزار و Scoped Storage (عدم نیاز به دسترسی‌های منسوخ شده و خطرناک)
 * ۲. بستن کامل اتصال دیتابیس Room قبل از فرآیند بازیابی جهت آزادسازی قفل‌های سیستمی و فایل‌های WAL/SHM
 * ۳. مدیریت دقیق خطاها با مکانیزم بازگشت اضطراری (Rollback)، اعتبارسنجی یکپارچگی فایل و عدم اعلام موفقیت کاذب
 */
object DatabaseBackupHelper {

    private const val TAG = "DatabaseBackupHelper"
    private const val DB_NAME = "goli_accounting_erp.db"

    /**
     * پوشه مجاز و اختصاصی برنامه جهت ذخیره فایل‌های پشتیبان (Scoped Storage)
     * اولویت با حافظه خارجی اختصاصی برنامه است تا کاربر بتواند در صورت نیاز فایل را مشاهده کند،
     * در غیر این صورت از حافظه داخلی filesDir استفاده می‌شود.
     */
    fun getBackupDirectory(context: Context): File {
        val externalStorageAvailable = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        val backupDir = if (externalStorageAvailable) {
            context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")
        } else {
            File(context.filesDir, "backups")
        }

        if (!backupDir.exists()) {
            val created = backupDir.mkdirs()
            if (!created && !backupDir.exists()) {
                Log.w(TAG, "Failed to create backup directory on preferred path, falling back to internal filesDir")
                val fallbackDir = File(context.filesDir, "backups")
                if (!fallbackDir.exists()) fallbackDir.mkdirs()
                return fallbackDir
            }
        }
        return backupDir
    }

    /**
     * ایجاد نسخه پشتیبان کامل از دیتابیس بر روی حافظه دستگاه
     */
    suspend fun createDatabaseBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting database backup process...")

            // ۱. فلش کامل تمام تراکنش‌های موجود در فایل WAL به فایل اصلی دیتابیس با TRUNCATE
            try {
                val appDb = AppDatabase.getInstance(context)
                if (appDb.isOpen) {
                    val db = appDb.openHelper.writableDatabase
                    val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                    if (cursor.moveToFirst()) {
                        val busy = cursor.getInt(0)
                        val logPages = cursor.getInt(1)
                        val checkpointedPages = cursor.getInt(2)
                        Log.d(TAG, "WAL Checkpoint result - busy: $busy, log: $logPages, checkpointed: $checkpointedPages")
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Warning while executing wal_checkpoint: ${e.localizedMessage}")
            }

            // ۲. بررسی وجود و معتبر بودن فایل اصلی دیتابیس
            val currentDbPath = context.getDatabasePath(DB_NAME)
            if (!currentDbPath.exists() || currentDbPath.length() == 0L) {
                val msg = "فایل پایگاه داده اصلی برنامه یافت نشد یا خالی است: ${currentDbPath.absolutePath}"
                Log.e(TAG, msg)
                return@withContext Result.failure(IOException(msg))
            }

            // ۳. تعیین مسیر ذخیره‌سازی مجاز با رعایت اصول Scoped Storage
            val backupDir = getBackupDirectory(context)
            if (!backupDir.canWrite()) {
                val msg = "خطای عدم دسترسی نوشتن در پوشه پشتیبان‌گیری: ${backupDir.absolutePath}"
                Log.e(TAG, msg)
                return@withContext Result.failure(IOException(msg))
            }

            val timestamp = JalaliDate.now().toStandardString().replace("/", "-") +
                    "_" + (System.currentTimeMillis() % 100000)
            val backupFile = File(backupDir, "Goli_Accounting_Backup_$timestamp.db")

            // ۴. کپی امن داده‌ها با بافر و همگام‌سازی فیزیکی بر روی دیسک (Sync)
            var bytesCopied: Long = 0
            FileInputStream(currentDbPath).use { input ->
                FileOutputStream(backupFile).use { output ->
                    bytesCopied = input.copyTo(output, bufferSize = 8192)
                    output.flush()
                    output.fd.sync()
                }
            }

            Log.i(TAG, "Backup file copied: ${backupFile.absolutePath}, bytes: $bytesCopied")

            if (bytesCopied == 0L || backupFile.length() == 0L) {
                backupFile.delete()
                val msg = "فایل پشتیبان به درستی ایجاد نشد و حجم آن صفر بایت است."
                Log.e(TAG, msg)
                return@withContext Result.failure(IOException(msg))
            }

            // ۵. اعتبارسنجی دقیق هدر و یکپارچگی فایل پشتیبان تولید شده
            val integrity = DatabaseSecurityManager.verifyDatabaseIntegrity(backupFile)
            if (!integrity.first) {
                backupFile.delete()
                val msg = "اعتبارسنجی ساختار دیتابیس پشتیبان ناموفق بود: ${integrity.second}"
                Log.e(TAG, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            Log.i(TAG, "Database backup created and verified successfully: ${backupFile.name}")
            Result.success(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error creating database backup: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /**
     * اشتراک‌گذاری ایمن فایل پشتیبان با استفاده از FileProvider
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                backupFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "پشتیبان پایگاه داده حسابداری گلی")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "اشتراک‌گذاری فایل پشتیبان (Backup)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share backup file: ${e.localizedMessage}", e)
        }
    }

    /**
     * بازیابی قطعی و امن پایگاه داده Room از فایل یا URI انتخابی کاربر
     * 
     * مراحل به این ترتیب اجرا می‌شوند:
     * ۱. کپی فایل ورودی به فایل موقت (Staging) در حافظه کش
     * ۲. تست سلامت عمیق SQLite و جداول حسابداری روی فایل موقت
     * ۳. ایجاد نسخه پشتیبان اضطراری از دیتابیس فعلی جهت بازگشت در صورت بروز خطا
     * ۴. بستن قطعی اتصال دیتابیس Room جهت جلوگیری از قفل شدن فایل‌ها
     * ۵. پاکسازی فایل‌های قدیمی WAL و SHM
     * ۶. بازنویسی فایل اصلی پایگاه داده با همگام‌سازی دیسک (Sync)
     * ۷. راه‌اندازی مجدد نمونه دیتابیس و اعتبارسنجی نهایی
     */
    suspend fun restoreDatabaseFromFile(context: Context, backupUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting database restore process from URI: $backupUri")

        val currentDbPath = context.getDatabasePath(DB_NAME)
        val dbDir = currentDbPath.parentFile ?: File(context.applicationInfo.dataDir, "databases")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        val walFile = File(dbDir, "$DB_NAME-wal")
        val shmFile = File(dbDir, "$DB_NAME-shm")

        val stagingFile = File(context.cacheDir, "staging_restore_${System.currentTimeMillis()}.tmp")
        val safetyBackupFile = File(context.cacheDir, "pre_restore_safety_${System.currentTimeMillis()}.db")

        try {
            // ۱. خواندن جریان ورودی و کپی به فایل موقت (Staging)
            var bytesRead: Long = 0
            val inputStream = context.contentResolver.openInputStream(backupUri)
                ?: return@withContext Result.failure(IOException("امکان دسترسی و بازگشایی فایل پشتیبان وجود ندارد."))

            inputStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    bytesRead = input.copyTo(output, bufferSize = 8192)
                    output.flush()
                    output.fd.sync()
                }
            }

            Log.i(TAG, "Staging file written: ${stagingFile.absolutePath}, bytes: $bytesRead")

            if (bytesRead == 0L || !stagingFile.exists() || stagingFile.length() == 0L) {
                stagingFile.delete()
                val msg = "فایل انتخاب شده برای بازیابی خالی است یا انتقال داده با خطا مواجه شد."
                Log.e(TAG, msg)
                return@withContext Result.failure(IOException(msg))
            }

            // ۲. بررسی دقیق یکپارچگی فایل موقت قبل از هرگونه دستکاری دیتابیس اصلی
            val integrity = DatabaseSecurityManager.verifyDatabaseIntegrity(stagingFile)
            if (!integrity.first) {
                stagingFile.delete()
                val msg = "فایل انتخاب شده معتبر نیست یا ساختار دیتابیس گلی را ندارد: ${integrity.second}"
                Log.e(TAG, msg)
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            // ۳. فلاش کردن داده‌های در حال استفاده دیتابیس فعلی قبل از بستن
            try {
                val appDb = AppDatabase.getInstance(context)
                if (appDb.isOpen) {
                    val db = appDb.openHelper.writableDatabase
                    val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                    cursor.moveToFirst()
                    cursor.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Checkpoint before restore failed (non-fatal): ${e.localizedMessage}")
            }

            // ۴. تهیه نسخه پشتیبان اضطراری از فایل‌های جاری دیتابیس
            if (currentDbPath.exists() && currentDbPath.length() > 0) {
                try {
                    FileInputStream(currentDbPath).use { input ->
                        FileOutputStream(safetyBackupFile).use { output ->
                            input.copyTo(output, bufferSize = 8192)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                    Log.i(TAG, "Safety backup saved to: ${safetyBackupFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create safety backup: ${e.localizedMessage}")
                }
            }

            // ۵. بستن قطعی و کامل اتصال دیتابیس Room جهت آزادسازی تمامی قفل‌های سیستم فایل
            Log.i(TAG, "Closing active Room database connection...")
            AppDatabase.closeInstance()

            // ۶. پاکسازی کامل فایل‌های WAL و SHM قدیمی برای جلوگیری از ناسازگاری پس از بازنویسی
            if (walFile.exists()) {
                val deleted = walFile.delete()
                Log.d(TAG, "Old WAL file deleted: $deleted")
            }
            if (shmFile.exists()) {
                val deleted = shmFile.delete()
                Log.d(TAG, "Old SHM file deleted: $deleted")
            }
            if (currentDbPath.exists()) {
                val deleted = currentDbPath.delete()
                Log.d(TAG, "Old DB file deleted: $deleted")
            }

            // ۷. کپی فایل موقت به مسیر اصلی دیتابیس برنامه با همگام‌سازی فیزیکی
            FileInputStream(stagingFile).use { input ->
                FileOutputStream(currentDbPath).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                    output.flush()
                    output.fd.sync()
                }
            }
            stagingFile.delete()

            // پاکسازی مجدد احتمالی فایل‌های کمکی پس از کپی
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            Log.i(TAG, "New database file written to: ${currentDbPath.absolutePath}, size: ${currentDbPath.length()}")

            // ۸. راه‌اندازی مجدد نمونه دیتابیس Room و اجرای کوئری آزمایشی جهت تایید سلامت عملکردی
            val reloadedDb = AppDatabase.getInstance(context)
            val dbVersion = reloadedDb.openHelper.readableDatabase.version
            Log.i(TAG, "Room database reopened successfully. Current schema version: $dbVersion")

            // حذف فایل پشتیبان اضطراری پس از تایید موفقیت عملیات
            if (safetyBackupFile.exists()) {
                safetyBackupFile.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during database restore: ${e.localizedMessage}", e)

            // در صورت بروز هرگونه خطا، تلاش برای بازگردانی وضعیت قبلی با استفاده از safetyBackupFile
            try {
                if (safetyBackupFile.exists() && safetyBackupFile.length() > 0) {
                    Log.w(TAG, "Attempting rollback from safety backup...")
                    AppDatabase.closeInstance()
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                    if (currentDbPath.exists()) currentDbPath.delete()

                    FileInputStream(safetyBackupFile).use { input ->
                        FileOutputStream(currentDbPath).use { output ->
                            input.copyTo(output, bufferSize = 8192)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                    AppDatabase.getInstance(context)
                    Log.i(TAG, "Rollback to safety backup completed.")
                }
            } catch (rollbackEx: Exception) {
                Log.e(TAG, "Rollback failed: ${rollbackEx.localizedMessage}", rollbackEx)
            } finally {
                if (safetyBackupFile.exists()) safetyBackupFile.delete()
            }

            if (stagingFile.exists()) stagingFile.delete()

            Result.failure(Exception("خطا در بازنشانی پایگاه داده: ${e.localizedMessage ?: "فایل مخدوش یا دسترسی نامعتبر است."}"))
        }
    }

    /**
     * فهرست فایل‌های پشتیبان موجود در پوشه اختصاصی برنامه
     */
    fun getExistingBackupFiles(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
