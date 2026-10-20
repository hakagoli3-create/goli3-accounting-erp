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
 * دستیار جامع پشتیبان‌گیری و بازیابی پایگاه داده حسابداری گلی (Room / SQLite)
 * 
 * برطرف‌کننده باگ عدم بازیابی واقعی اطلاعات:
 * ۱. بستن کامل و قطعی اتصال دیتابیس Room (Close Database Connections) قبل از دستکاری فایل‌ها
 * ۲. بازنویسی واقعی و باینری فایل دیتابیس (Direct File Overwrite) با پاکسازی کامل فایلهای WAL/SHM
 * ۳. مدیریت خطاهای واقعی (No Fake Success): در صورت هرگونه خطا، فرآیند متوقف شده و خطای صریح گزارش می‌شود
 * ۴. پشتیبانی کامل از فایل‌های خارجی (External Uri Handling) دریافتی از تلگرام، درایو، دانلودها و سایر برنامه‌ها
 */
object DatabaseBackupHelper {

    private const val TAG = "DatabaseBackupHelper"
    private const val DB_NAME = "goli_accounting_erp.db"

    // هدر استاندارد ۱۶ بایتی فایل‌های SQLite جهت اعتبارسنجی باینری
    private val SQLITE_HEADER_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * مسیر پوشه ذخیره‌سازی پشتیبان‌ها در حافظه اختصاصی نرم‌افزار (Scoped Storage)
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
                Log.w(TAG, "Failed to create backup dir in primary storage, using internal filesDir")
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
            Log.i(TAG, "Starting database backup creation...")

            // ۱. فلاش کردن تمام صفحات معلق لاگ WAL به فایل اصلی با دستور PRAGMA wal_checkpoint(TRUNCATE)
            try {
                val appDb = AppDatabase.getInstance(context)
                if (appDb.isOpen) {
                    val db = appDb.openHelper.writableDatabase
                    val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                    cursor.moveToFirst()
                    cursor.close()
                    Log.d(TAG, "WAL checkpoint TRUNCATE executed successfully before backup.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Warning during WAL checkpoint: ${e.localizedMessage}")
            }

            // ۲. بررسی وجود و اندازه فایل دیتابیس فعلی
            val currentDbPath = context.getDatabasePath(DB_NAME)
            if (!currentDbPath.exists() || currentDbPath.length() == 0L) {
                val errorMsg = "فایل پایگاه داده اصلی برنامه یافت نشد یا هنوز ایجاد نشده است: ${currentDbPath.absolutePath}"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            // ۳. ایجاد فایل مقصد در پوشه مجاز
            val backupDir = getBackupDirectory(context)
            val timestamp = JalaliDate.now().toStandardString().replace("/", "-") +
                    "_" + (System.currentTimeMillis() % 100000)
            val backupFile = File(backupDir, "Goli_Accounting_Backup_$timestamp.db")

            // ۴. کپی مستقیم باینری با بافر و سینک فیزیکی دیسک
            var totalBytesCopied: Long = 0
            FileInputStream(currentDbPath).use { input ->
                FileOutputStream(backupFile).use { output ->
                    totalBytesCopied = input.copyTo(output, bufferSize = 32768)
                    output.flush()
                    output.fd.sync()
                }
            }

            if (totalBytesCopied == 0L || !backupFile.exists() || backupFile.length() == 0L) {
                backupFile.delete()
                val errorMsg = "عملیات کپی ناموفق بود و فایل پشتیبان خالی است."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            // ۵. اعتبارسنجی هدر باینری فایل پشتیبان تولید شده
            if (!verifySqliteHeader(backupFile)) {
                backupFile.delete()
                val errorMsg = "هدر فایل خروجی با ساختار استاندارد SQLite همخوانی ندارد."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            Log.i(TAG, "Backup created successfully: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            Result.success(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error creating database backup: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /**
     * بازیابی واقعی و بازنویسی باینری پایگاه داده از Uri انتخابی (تلگرام، فایل منیجر، دانلودها)
     *
     * گام‌های دقیق برای تضمین بازیابی واقعی:
     * ۱. کپی فایل ورودی از طریق ContentResolver به فایل موقت (Temp) در پوشه دیتابیس
     * ۲. اعتبارسنجی هدر باینری فایل جهت جلوگیری از جایگزینی فایلهای خراب یا غیردیتابیسی
     * ۳. بستن قطعی تمام اتصالات فعال Room (Close Database Connections) جهت رهاسازی قفلها
     * ۴. حذف فایلهای لاگ WAL و SHM قبلی برای جلوگیری از اجرای لاگهای قدیمی روی دیتابیس جدید
     * ۵. بازنویسی باینری مستقیم (Direct Binary Overwrite) روی مسیر getDatabasePath
     * ۶. بازگشایی مجدد نمونه Room و تست خواندن دادهها
     */
    suspend fun restoreDatabaseFromFile(context: Context, backupUri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting database restore from URI: $backupUri")

        val currentDbPath = context.getDatabasePath(DB_NAME)
        val dbDir = currentDbPath.parentFile ?: File(context.applicationInfo.dataDir, "databases")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        val walFile = File(dbDir, "$DB_NAME-wal")
        val shmFile = File(dbDir, "$DB_NAME-shm")
        val journalFile = File(dbDir, "$DB_NAME-journal")
        val tempRestoreFile = File(dbDir, "temp_incoming_restore_${System.currentTimeMillis()}.bin")

        try {
            // گام ۱: خواندن جریان باینری از طریق ContentResolver و نوشتن در فایل موقت
            val inputStream = try {
                context.contentResolver.openInputStream(backupUri)
            } catch (secEx: SecurityException) {
                Log.e(TAG, "SecurityException opening URI: ${secEx.localizedMessage}", secEx)
                return@withContext Result.failure(
                    SecurityException("عدم دسترسی به فایل انتخاب‌شده. لطفاً فایل را در پوشه Downloads ذخیره کرده و مجدداً انتخاب کنید.")
                )
            } catch (ioEx: IOException) {
                Log.e(TAG, "IOException opening URI: ${ioEx.localizedMessage}", ioEx)
                return@withContext Result.failure(
                    IOException("خطا در باز کردن فایل از مسیر خارجی: ${ioEx.localizedMessage}")
                )
            }

            if (inputStream == null) {
                val errorMsg = "امکان باز کردن جریان داده از فایل انتخابی وجود ندارد (InputStream خالی است)."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            var bytesCopied: Long = 0
            inputStream.use { input ->
                FileOutputStream(tempRestoreFile).use { output ->
                    bytesCopied = input.copyTo(output, bufferSize = 32768)
                    output.flush()
                    output.fd.sync()
                }
            }

            Log.i(TAG, "Stream read completed. Bytes received: $bytesCopied")

            // اعتبارسنجی حجم فایل موقت
            if (bytesCopied == 0L || !tempRestoreFile.exists() || tempRestoreFile.length() == 0L) {
                tempRestoreFile.delete()
                val errorMsg = "فایل انتخاب‌شده برای بازیابی خالی است (۰ بایت)."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            // گام ۲: اعتبارسنجی هدر فایل برای اطمینان از باینری بودن دیتابیس SQLite
            if (!verifySqliteHeader(tempRestoreFile)) {
                tempRestoreFile.delete()
                val errorMsg = "فایل انتخاب‌شده یک فایل معتبر دیتابیس SQLite نمی‌باشد."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IllegalArgumentException(errorMsg))
            }

            // گام ۳: بستن کامل و قطعی تمام ارتباطات با AppDatabase جهت رهاسازی قفل‌های سیستمی
            Log.i(TAG, "Closing active AppDatabase connection before overwriting file...")
            AppDatabase.closeInstance()

            // گام ۴: پاکسازی کامل فایل‌های ژورنال قدیمی (WAL / SHM / Journal)
            if (walFile.exists()) {
                val walDeleted = walFile.delete()
                Log.d(TAG, "Old WAL file deleted: $walDeleted")
            }
            if (shmFile.exists()) {
                val shmDeleted = shmFile.delete()
                Log.d(TAG, "Old SHM file deleted: $shmDeleted")
            }
            if (journalFile.exists()) {
                val journalDeleted = journalFile.delete()
                Log.d(TAG, "Old Journal file deleted: $journalDeleted")
            }

            // گام ۵: بازنویسی واقعی و باینری مستقیم روی فایل اصلی دیتابیس (Direct File Overwrite)
            if (currentDbPath.exists()) {
                val oldDeleted = currentDbPath.delete()
                Log.d(TAG, "Previous DB file deleted before rewrite: $oldDeleted")
            }

            var writtenBytes: Long = 0
            FileInputStream(tempRestoreFile).use { input ->
                FileOutputStream(currentDbPath).use { output ->
                    writtenBytes = input.copyTo(output, bufferSize = 32768)
                    output.flush()
                    output.fd.sync()
                }
            }
            tempRestoreFile.delete()

            Log.i(TAG, "Direct binary overwrite completed. Written bytes: $writtenBytes, Current DB size: ${currentDbPath.length()}")

            if (writtenBytes != bytesCopied || !currentDbPath.exists() || currentDbPath.length() == 0L) {
                val errorMsg = "خطا در فرآیند بازنویسی دیتابیس؛ حجم فایل نهایی با فایل پشتیبان تطابق ندارد."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            // حذف مجدد احتمالی فایلهای کمکی تا SQLite از ابتدا با دیتابیس جدید شروع کند
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (journalFile.exists()) journalFile.delete()

            // گام ۶: بازگشایی مجدد نمونه دیتابیس Room و اجرای کوئری سلامت
            Log.i(TAG, "Reopening Room AppDatabase...")
            val newAppDb = AppDatabase.getInstance(context)
            val readableDb = newAppDb.openHelper.readableDatabase
            val dbVersion = readableDb.version
            Log.i(TAG, "Room database restored and reopened successfully. Schema version: $dbVersion")

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Restore operation failed with error: ${e.localizedMessage}", e)
            if (tempRestoreFile.exists()) tempRestoreFile.delete()
            Result.failure(Exception("خطا در بازیابی پایگاه داده: ${e.localizedMessage ?: "فایل مخدوش یا دسترسی نامعتبر است."}"))
        }
    }

    /**
     * اشتراک‌گذاری فایل پشتیبان با نرم‌افزارهای خارجی (تلگرام، واتساپ، ایمیل)
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
     * فهرست فایل‌های پشتیبان موجود در پوشه اختصاصی برنامه
     */
    fun getExistingBackupFiles(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * بررسی هدر ۱۶ بایتی استاندارد SQLite برای اطمینان از سلامت باینری فایل
     */
    fun verifySqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                read == 16 && header.contentEquals(SQLITE_HEADER_MAGIC)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check SQLite header: ${e.localizedMessage}")
            false
        }
    }
}
