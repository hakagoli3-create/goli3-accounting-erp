package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.AccountGroupEntity
import com.example.data.model.GeneralAccountEntity
import com.example.data.model.JournalEntryEntity
import com.example.data.model.JournalItemEntity
import com.example.data.model.JournalItemWithVoucher
import com.example.data.model.SubsidiaryAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {

    // Account Groups
    @Query("SELECT * FROM account_groups ORDER BY code ASC")
    fun getAllAccountGroups(): Flow<List<AccountGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountGroups(groups: List<AccountGroupEntity>)

    // General Accounts
    @Query("SELECT * FROM general_accounts ORDER BY code ASC")
    fun getAllGeneralAccounts(): Flow<List<GeneralAccountEntity>>

    @Query("SELECT * FROM general_accounts WHERE groupCode = :groupCode ORDER BY code ASC")
    fun getGeneralAccountsByGroup(groupCode: Int): Flow<List<GeneralAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneralAccounts(accounts: List<GeneralAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneralAccount(account: GeneralAccountEntity): Long

    @androidx.room.Update
    suspend fun updateGeneralAccount(account: GeneralAccountEntity)

    @androidx.room.Delete
    suspend fun deleteGeneralAccount(account: GeneralAccountEntity)

    // Subsidiary Accounts
    @Query("SELECT * FROM subsidiary_accounts ORDER BY code ASC")
    fun getAllSubsidiaryAccounts(): Flow<List<SubsidiaryAccountEntity>>

    @Query("SELECT * FROM subsidiary_accounts WHERE generalCode = :generalCode ORDER BY code ASC")
    fun getSubsidiaryAccountsByGeneral(generalCode: Int): Flow<List<SubsidiaryAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubsidiaryAccounts(accounts: List<SubsidiaryAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubsidiaryAccount(account: SubsidiaryAccountEntity): Long

    @androidx.room.Update
    suspend fun updateSubsidiaryAccount(account: SubsidiaryAccountEntity)

    @androidx.room.Delete
    suspend fun deleteSubsidiaryAccount(account: SubsidiaryAccountEntity)

    @Query("SELECT * FROM journal_items WHERE subsidiaryCode = :subsidiaryCode ORDER BY id ASC")
    fun getJournalItemsBySubsidiary(subsidiaryCode: Int): Flow<List<JournalItemEntity>>

    // Journal Entries (اسناد حسابداری)
    @Query("SELECT * FROM journal_entries ORDER BY voucherNumber DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getJournalEntryById(id: Long): JournalEntryEntity?

    @Query("SELECT COALESCE(MAX(voucherNumber), 0) FROM journal_entries")
    suspend fun getMaxVoucherNumber(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity): Long

    // Journal Items (آرتیکل‌های سند)
    @Query("SELECT * FROM journal_items WHERE voucherId = :voucherId")
    fun getJournalItemsByVoucher(voucherId: Long): Flow<List<JournalItemEntity>>

    @Query("SELECT * FROM journal_items WHERE voucherId = :voucherId")
    suspend fun getJournalItemsByVoucherSync(voucherId: Long): List<JournalItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalItems(items: List<JournalItemEntity>)

    @Query("SELECT SUM(debit) FROM journal_items")
    fun getTotalDebitSum(): Flow<Long?>

    @Query("SELECT SUM(credit) FROM journal_items")
    fun getTotalCreditSum(): Flow<Long?>

    @Query("""
        WITH AllBaseAssets AS (
            SELECT CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount FROM cash_desks
            UNION ALL
            SELECT CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount FROM bank_accounts
            UNION ALL
            SELECT CAST(
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
            -- حساب‌های بدهکاران (مشتریان بدهکار / مطالبات جاری)
            SELECT CAST(COALESCE(SUM(currentBalance), 0) AS REAL) AS amount FROM parties WHERE currentBalance > 0
            UNION ALL
            -- حساب‌های بستانکاران (تامین‌کنندگان بستانکار / بدهی‌ها)
            SELECT CAST(-COALESCE(SUM(ABS(currentBalance)), 0) AS REAL) AS amount FROM parties WHERE currentBalance < 0
        )
        SELECT 'تومان' AS currencyType,
               COALESCE(SUM(amount), 0.0) AS totalAmount
        FROM AllBaseAssets
    """)
    fun observeBaseAssetsTotal(): Flow<com.example.data.model.CurrencyAssetDto?>

    @Query("SELECT * FROM journal_items ORDER BY id DESC")
    fun getAllJournalItems(): Flow<List<JournalItemEntity>>

    @Query("""
        SELECT ji.* FROM journal_items ji
        INNER JOIN journal_entries je ON ji.voucherId = je.id
        WHERE je.status != 'VOID'
        ORDER BY ji.id DESC
    """)
    fun getActiveJournalItems(): Flow<List<JournalItemEntity>>

    @Query("""
        SELECT ji.* FROM journal_items ji
        INNER JOIN journal_entries je ON ji.voucherId = je.id
        WHERE ji.partyId = :partyId 
          AND je.status != 'VOID'
          AND ji.generalCode IN (102, 301)
        ORDER BY je.id ASC, ji.id ASC
    """)
    fun getJournalItemsByParty(partyId: Long): Flow<List<JournalItemEntity>>

    @androidx.room.Update
    suspend fun updateJournalEntry(entry: JournalEntryEntity)

    @androidx.room.Delete
    suspend fun deleteJournalEntry(entry: JournalEntryEntity)

    @androidx.room.Update
    suspend fun updateJournalItem(item: JournalItemEntity)

    @androidx.room.Delete
    suspend fun deleteJournalItem(item: JournalItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalItem(item: JournalItemEntity): Long

    @Query("SELECT * FROM journal_items WHERE id = :id LIMIT 1")
    suspend fun getJournalItemById(id: Long): JournalItemEntity?

    @Query("UPDATE journal_entries SET status = 'VOID' WHERE id = :voucherId")
    suspend fun voidJournalEntry(voucherId: Long)

    @Query("DELETE FROM journal_items WHERE voucherId = :voucherId")
    suspend fun deleteJournalItemsByVoucher(voucherId: Long)

    @Query("DELETE FROM journal_entries WHERE id = :voucherId")
    suspend fun deleteJournalEntryById(voucherId: Long)

    @Query("SELECT * FROM journal_entries WHERE description LIKE '%' || :pattern || '%'")
    suspend fun getJournalEntriesByDescriptionPattern(pattern: String): List<JournalEntryEntity>

    @Query("DELETE FROM journal_items WHERE voucherId IN (SELECT id FROM journal_entries WHERE description LIKE '%' || :pattern || '%')")
    suspend fun deleteJournalItemsByDescriptionPattern(pattern: String): Int

    @Query("DELETE FROM journal_entries WHERE description LIKE '%' || :pattern || '%'")
    suspend fun deleteJournalEntriesByDescriptionPattern(pattern: String): Int

    @Query("""
        SELECT ji.id, ji.voucherId, je.voucherNumber, je.jalaliDate, je.status AS voucherStatus,
               je.description AS voucherDescription, ji.generalCode, ji.subsidiaryCode,
               ji.partyId, ji.description, ji.debit, ji.credit
        FROM journal_items ji
        INNER JOIN journal_entries je ON ji.voucherId = je.id
        ORDER BY je.voucherNumber DESC, ji.id ASC
    """)
    fun getJournalItemsWithVoucher(): Flow<List<JournalItemWithVoucher>>

    @Query("""
        SELECT ji.id, ji.voucherId, je.voucherNumber, je.jalaliDate, je.status AS voucherStatus,
               je.description AS voucherDescription, ji.generalCode, ji.subsidiaryCode,
               ji.partyId, ji.description, ji.debit, ji.credit
        FROM journal_items ji
        INNER JOIN journal_entries je ON ji.voucherId = je.id
        WHERE ji.subsidiaryCode = :subsidiaryCode
        ORDER BY je.voucherNumber ASC, ji.id ASC
    """)
    fun getJournalItemsWithVoucherBySubsidiary(subsidiaryCode: Int): Flow<List<JournalItemWithVoucher>>

    @Query("""
        SELECT ji.id, ji.voucherId, je.voucherNumber, je.jalaliDate, je.status AS voucherStatus,
               je.description AS voucherDescription, ji.generalCode, ji.subsidiaryCode,
               ji.partyId, ji.description, ji.debit, ji.credit
        FROM journal_items ji
        INNER JOIN journal_entries je ON ji.voucherId = je.id
        WHERE ji.partyId = :partyId
        ORDER BY je.voucherNumber ASC, ji.id ASC
    """)
    fun getJournalItemsWithVoucherByParty(partyId: Long): Flow<List<JournalItemWithVoucher>>

    @Query("UPDATE journal_entries SET status = 'FINAL' WHERE id = :voucherId")
    suspend fun approveJournalEntry(voucherId: Long)

    @Query("UPDATE journal_entries SET status = :status WHERE id = :voucherId")
    suspend fun updateJournalEntryStatus(voucherId: Long, status: String)

    @Transaction
    suspend fun createVoucherWithItems(
        entry: JournalEntryEntity,
        items: List<JournalItemEntity>
    ): Long {
        val nextNumber = getMaxVoucherNumber() + 1
        val voucherId = insertJournalEntry(entry.copy(id = 0L, voucherNumber = nextNumber))
        val attachedItems = items.map { it.copy(id = 0L, voucherId = voucherId) }
        insertJournalItems(attachedItems)
        return voucherId
    }
}

