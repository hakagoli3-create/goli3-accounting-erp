package com.example.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * مدیریت امنیت و رمزنگاری داده‌های مالی و پایگاه داده گلی
 * این کلاس شامل پیاده‌سازی مکانیزم‌های استاندارد رمزنگاری به شرح زیر است:
 * 1. ایجاد و نگهداری کلیدهای سخت‌افزاری امن با استفاده از Android Keystore (TEE / StrongBox)
 * 2. هشینگ امن رمزهای عبور کاربران با الگوریتم نمک‌دار (Salted SHA-256)
 * 3. اعتبارسنجی یکپارچگی (Integrity Check) و ساختار فیزیکی پایگاه داده SQLite
 * 4. فراهم‌سازی الگوی رمزنگاری پایگاه داده محلی (SQLCipher) برای پایگاه داده مالی Room
 */
object DatabaseSecurityManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "GoliAccountingMasterKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val DEFAULT_SALT = "GoliERP@Financial#Secured2026"

    /**
     * بایت‌های شناسه استاندارد فایل‌های SQLite جهت اعتبارسنجی هدر فایل
     */
    val SQLITE_HEADER_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * هش کردن امن کلمه عبور با استفاده از SHA-256 به همراه نمک (Salt)
     */
    fun hashPassword(password: String, salt: String = DEFAULT_SALT): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$password:$salt".toByteArray(Charsets.UTF_8)
        val digest = md.digest(combined)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * بررسی صحت کلمه عبور کاربر با پشتیبانی از کلمات عبور پیشین (Legacy) جهت جلوگیری از قفل شدن کاربران
     */
    fun verifyPassword(inputPassword: String, storedHashOrPlain: String, salt: String = DEFAULT_SALT): Boolean {
        if (inputPassword == storedHashOrPlain) {
            return true
        }
        val hashedInput = hashPassword(inputPassword, salt)
        return hashedInput.equals(storedHashOrPlain, ignoreCase = true)
    }

    /**
     * دریافت یا تولید کلید متقارن سخت‌افزاری AES-256 در محیط امن Android Keystore
     */
    fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * تولید یا بازیابی عبارت عبور (Passphrase) ایمن جهت استفاده در رمزنگاری پایگاه داده محلی
     */
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("goli_db_security", Context.MODE_PRIVATE)
        val encPassphraseHex = prefs.getString("encrypted_db_passphrase", null)
        val ivHex = prefs.getString("db_passphrase_iv", null)

        if (encPassphraseHex != null && ivHex != null) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = hexToBytes(ivHex)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
                return cipher.doFinal(hexToBytes(encPassphraseHex))
            } catch (_: Exception) {
                // اگر مشکلی در واکشی رخ دهد، کلید جدید بازتولید می‌شود
            }
        }

        // تولید ۳۲ بایت تصادفی به عنوان کلید ۲۵۶ بیتی SQLCipher
        val randomPassphrase = ByteArray(32)
        SecureRandom().nextBytes(randomPassphrase)

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
            val encryptedBytes = cipher.doFinal(randomPassphrase)
            val iv = cipher.iv

            prefs.edit()
                .putString("encrypted_db_passphrase", bytesToHex(encryptedBytes))
                .putString("db_passphrase_iv", bytesToHex(iv))
                .apply()
        } catch (_: Exception) {}

        return randomPassphrase
    }

    /**
     * بررسی یکپارچگی ساختار فیزیکی دیتابیس با هدر استاندارد SQLite و اجرای PRAGMA integrity_check
     */
    fun verifyDatabaseIntegrity(dbFile: File): Pair<Boolean, String> {
        if (!dbFile.exists() || dbFile.length() < 100) {
            return Pair(false, "فایل پایگاه داده خالی یا مخدوش است.")
        }

        // 1. بررسی هدر جادویی ۱۶ بایتی SQLite
        try {
            FileInputStream(dbFile).use { input ->
                val header = ByteArray(16)
                val readCount = input.read(header)
                if (readCount != 16 || !header.contentEquals(SQLITE_HEADER_MAGIC)) {
                    return Pair(false, "فرمت فایل انتخاب‌شده پایگاه داده معتبر SQLite نیست.")
                }
            }
        } catch (e: Exception) {
            return Pair(false, "خطا در خواندن هدر دیتابیس: ${e.localizedMessage}")
        }

        // 2. اجرای تست سلامت عمیق SQLite PRAGMA integrity_check در حالت Read-Only
        var testDb: SQLiteDatabase? = null
        try {
            testDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            val cursor = testDb.rawQuery("PRAGMA integrity_check", null)
            var isOk = false
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                isOk = result.equals("ok", ignoreCase = true)
            }
            cursor.close()

            if (!isOk) {
                return Pair(false, "تست سلامت PRAGMA integrity_check روی پایگاه داده ناموفق بود.")
            }

            // 3. بررسی وجود جداول کلیدی حسابداری و مالی
            val tablesCursor = testDb.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('invoices', 'products', 'warehouses', 'users')",
                null
            )
            var tableCount = 0
            if (tablesCursor.moveToFirst()) {
                tableCount = tablesCursor.getInt(0)
            }
            tablesCursor.close()

            if (tableCount < 3) {
                return Pair(false, "فایل انتخابی مربوط به نرم‌افزار حسابداری گلی نبوده یا ساختار جداول آن ناقص است.")
            }

            return Pair(true, "پایگاه داده معتبر و سالم است.")
        } catch (e: Exception) {
            return Pair(false, "خطا در تست یکپارچگی دیتابیس: ${e.localizedMessage}")
        } finally {
            try {
                testDb?.close()
            } catch (_: Exception) {}
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
