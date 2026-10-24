package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.BankAccountEntity
import com.example.data.model.CashDeskEntity
import com.example.data.model.ChequeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreasuryDao {

    // Cash Desks
    @Query("SELECT * FROM cash_desks ORDER BY id ASC")
    fun getAllCashDesks(): Flow<List<CashDeskEntity>>

    @Query("SELECT * FROM cash_desks ORDER BY id ASC LIMIT 1")
    suspend fun getFirstCashDesk(): CashDeskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashDesk(cashDesk: CashDeskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashDesks(cashDesks: List<CashDeskEntity>)

    @Query("UPDATE cash_desks SET currentBalance = currentBalance + :delta WHERE id = :id")
    suspend fun updateCashBalance(id: Long, delta: Long)

    @Query("SELECT SUM(currentBalance) FROM cash_desks")
    fun getTotalCashBalance(): Flow<Long?>

    @Update
    suspend fun updateCashDesk(cashDesk: CashDeskEntity)

    @Delete
    suspend fun deleteCashDesk(cashDesk: CashDeskEntity)

    // Bank Accounts
    @Query("SELECT * FROM bank_accounts ORDER BY id ASC")
    fun getAllBankAccounts(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts ORDER BY id ASC LIMIT 1")
    suspend fun getFirstBankAccount(): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(bankAccount: BankAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccounts(bankAccounts: List<BankAccountEntity>)

    @Query("UPDATE bank_accounts SET currentBalance = currentBalance + :delta WHERE id = :id")
    suspend fun updateBankBalance(id: Long, delta: Long)

    @Update
    suspend fun updateBankAccount(bankAccount: BankAccountEntity)

    @Delete
    suspend fun deleteBankAccount(bankAccount: BankAccountEntity)

    @Query("SELECT SUM(currentBalance) FROM bank_accounts")
    fun getTotalBankBalance(): Flow<Long?>

    // Cheques
    @Query("SELECT * FROM cheques ORDER BY dueDateEpochMillis ASC")
    fun getAllCheques(): Flow<List<ChequeEntity>>

    @Query("SELECT * FROM cheques WHERE type = :type ORDER BY dueDateEpochMillis ASC")
    fun getChequesByType(type: String): Flow<List<ChequeEntity>>

    @Query("SELECT * FROM cheques WHERE status = 'NORMAL' ORDER BY dueDateEpochMillis ASC")
    fun getActivePendingCheques(): Flow<List<ChequeEntity>>

    @Query("SELECT * FROM cheques WHERE status = 'NORMAL' AND dueDateEpochMillis <= :cutoffEpoch ORDER BY dueDateEpochMillis ASC")
    fun getDueOrOverdueCheques(cutoffEpoch: Long): Flow<List<ChequeEntity>>

    @Query("SELECT COUNT(*) FROM cheques WHERE status = 'NORMAL' AND dueDateEpochMillis <= :cutoffEpoch")
    fun getDueOrOverdueChequesCount(cutoffEpoch: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheque(cheque: ChequeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheques(cheques: List<ChequeEntity>)

    @Update
    suspend fun updateCheque(cheque: ChequeEntity)

    @Delete
    suspend fun deleteCheque(cheque: ChequeEntity)

    @Query("SELECT * FROM cheques WHERE id = :id LIMIT 1")
    suspend fun getChequeById(id: Long): ChequeEntity?

    @Query("DELETE FROM cheques WHERE id = :id")
    suspend fun deleteChequeById(id: Long): Int

    @Query("DELETE FROM cheques WHERE notes LIKE '%' || :invoiceNumber || '%'")
    suspend fun deleteChequesByInvoiceNumber(invoiceNumber: String): Int

    @Query("SELECT * FROM cheques WHERE notes LIKE '%' || :invoiceNumber || '%' LIMIT 1")
    suspend fun getChequeByInvoiceNumber(invoiceNumber: String): ChequeEntity?

    @Query("DELETE FROM cheques WHERE notes LIKE '%' || :query || '%' OR sayadNumber = :query OR chequeNumber = :query")
    suspend fun deleteChequesByQuery(query: String): Int

    @Query("SELECT * FROM cheques WHERE notes LIKE '%' || :query || '%' OR sayadNumber = :query OR chequeNumber = :query")
    suspend fun getChequesByQuery(query: String): List<ChequeEntity>

    // Multi-Currency Balances for Cash Desks and Bank Accounts
    @Query("SELECT * FROM treasury_currency_balances ORDER BY currencyCode ASC")
    fun getAllCurrencyBalances(): Flow<List<com.example.data.model.TreasuryCurrencyBalanceEntity>>

    @Query("""
        WITH CombinedAssets AS (
            -- ۱. موجودی تومانی کلیه صندوق‌ها شامل موجودی‌های اول‌دوره و تمامی گردش‌های دریافتی/پرداختی
            SELECT 'TOMAN' AS currencyType, 
                   CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount 
            FROM cash_desks
            
            UNION ALL
            
            -- ۲. موجودی تومانی کلیه حساب‌های بانکی و پوزها شامل موجودی‌های اول‌دوره و تراکنش‌ها
            SELECT 'TOMAN' AS currencyType, 
                   CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount 
            FROM bank_accounts
            
            UNION ALL
            
            -- ۳. موجودی کلیه ارزهای خارجی (دلار، یورو، لیر، درهم و...) ثبت‌شده در خزانه‌داری
            SELECT UPPER(TRIM(currencyCode)) AS currencyType, 
                   COALESCE(SUM(balance), 0.0) AS amount 
            FROM treasury_currency_balances 
            WHERE UPPER(TRIM(currencyCode)) NOT IN ('TOMAN', 'تومان')
            GROUP BY UPPER(TRIM(currencyCode))
            
            UNION ALL
            
            -- ۴. ردیف‌های تومانی ثبت‌شده در جدول تفکیک ارز که به صندوق یا بانک جاری منتسب نیستند
            SELECT 'TOMAN' AS currencyType, 
                   COALESCE(SUM(balance), 0.0) AS amount 
            FROM treasury_currency_balances 
            WHERE UPPER(TRIM(currencyCode)) IN ('TOMAN', 'تومان')
              AND (
                  (accountType = 'CASH' AND accountId NOT IN (SELECT id FROM cash_desks))
                  OR (accountType = 'BANK' AND accountId NOT IN (SELECT id FROM bank_accounts))
                  OR (accountType NOT IN ('CASH', 'BANK'))
              )
            
            UNION ALL
            
            -- ۵. مانده خالص اسناد ثبت‌شده در کدینگ برای موجودی نقد و بانک (کد ۱۰۱) در صورتی که بیشتر از موجودی فیزیکی ثبت شده باشد
            SELECT 'TOMAN' AS currencyType,
                   CAST(
                       MAX(
                           0,
                           COALESCE(
                               (SELECT SUM(ji.debit - ji.credit) 
                                FROM journal_items ji 
                                INNER JOIN journal_entries je ON ji.voucherId = je.id 
                                WHERE je.status != 'VOID' AND ji.generalCode = 101),
                               0
                           ) - (
                               COALESCE((SELECT SUM(currentBalance) FROM cash_desks), 0) + 
                               COALESCE((SELECT SUM(currentBalance) FROM bank_accounts), 0)
                           )
                       ) AS REAL
                   ) AS amount

            UNION ALL

            -- ۶. حساب‌های بدهکاران (مشتریان بدهکار / مطالبات تجاری و جاری) به عنوان دارایی جاری
            SELECT 'TOMAN' AS currencyType,
                   CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount
            FROM parties
            WHERE currentBalance > 0

            UNION ALL

            -- ۷. حساب‌های بستانکاران (تامین‌کنندگان بستانکار / بدهی‌ها) به عنوان بدهی از مجموع دارایی‌ها کسر گردند
            SELECT 'TOMAN' AS currencyType,
                   CAST(-COALESCE(SUM(ABS(currentBalance)), 0) AS REAL) AS amount
            FROM parties
            WHERE currentBalance < 0
        )
        SELECT currencyType, 
               COALESCE(SUM(amount), 0.0) AS totalAmount 
        FROM CombinedAssets 
        GROUP BY currencyType
    """)
    fun observeTotalAssetsByCurrency(): Flow<List<com.example.data.model.CurrencyAssetDto>>

    @Query("SELECT * FROM treasury_currency_balances WHERE accountType = :accountType AND accountId = :accountId")
    fun getBalancesForAccount(accountType: String, accountId: Long): Flow<List<com.example.data.model.TreasuryCurrencyBalanceEntity>>

    @Query("SELECT * FROM treasury_currency_balances WHERE accountType = :accountType AND accountId = :accountId AND currencyCode = :currencyCode LIMIT 1")
    suspend fun getCurrencyBalance(accountType: String, accountId: Long, currencyCode: String): com.example.data.model.TreasuryCurrencyBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCurrencyBalance(balanceEntity: com.example.data.model.TreasuryCurrencyBalanceEntity): Long

    @Query("DELETE FROM treasury_currency_balances WHERE accountType = :accountType AND accountId = :accountId")
    suspend fun deleteBalancesForAccount(accountType: String, accountId: Long)

    @Transaction
    suspend fun adjustCurrencyAndBaseBalance(
        balanceEntity: com.example.data.model.TreasuryCurrencyBalanceEntity,
        accountType: String,
        accountId: Long,
        deltaInBase: Long
    ) {
        insertOrUpdateCurrencyBalance(balanceEntity)
        if (deltaInBase != 0L) {
            if (accountType == "CASH") {
                updateCashBalance(accountId, deltaInBase)
            } else {
                updateBankBalance(accountId, deltaInBase)
            }
        }
    }

    /**
     * به روزرسانی ایزوله و خالص موجودی ارزی خزانه‌داری بدون کوچکترین دستکاری در نقدینگی پایه تومانی
     */
    @Transaction
    suspend fun adjustCurrencyBalanceOnly(
        accountType: String,
        accountId: Long,
        currencyCode: String,
        delta: Double
    ) {
        val existing = getCurrencyBalance(accountType, accountId, currencyCode)
        val newBalance = (existing?.balance ?: 0.0) + delta
        insertOrUpdateCurrencyBalance(
            com.example.data.model.TreasuryCurrencyBalanceEntity(
                id = existing?.id ?: 0L,
                accountType = accountType,
                accountId = accountId,
                currencyCode = currencyCode,
                balance = newBalance
            )
        )
    }


    @Transaction
    suspend fun transferBetweenAccounts(
        fromType: String,
        fromId: Long,
        toType: String,
        toId: Long,
        amount: Long
    ) {
        if (fromType == "CASH") updateCashBalance(fromId, -amount)
        else updateBankBalance(fromId, -amount)

        if (toType == "CASH") updateCashBalance(toId, amount)
        else updateBankBalance(toId, amount)
    }
}
