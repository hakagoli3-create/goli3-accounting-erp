package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {

    @Query("SELECT * FROM parties ORDER BY id DESC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE partyType IN ('CUSTOMER', 'BOTH') ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE partyType IN ('SUPPLIER', 'BOTH') ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun getPartyById(id: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE name LIKE '%' || :query || '%' OR mobile LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%'")
    fun searchParties(query: String): Flow<List<PartyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParties(parties: List<PartyEntity>)

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Delete
    suspend fun deleteParty(party: PartyEntity)

    @Query("UPDATE parties SET currentBalance = currentBalance + :delta WHERE id = :partyId")
    suspend fun updatePartyBalance(partyId: Long, delta: Long)

    @Query("SELECT COUNT(*) FROM parties WHERE creditLimit > 0 AND currentBalance > creditLimit")
    fun getOverCreditLimitCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(currentBalance), 0) FROM parties WHERE partyType IN ('CUSTOMER', 'BOTH') AND currentBalance > 0")
    fun getCustomerReceivables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(ABS(currentBalance)), 0) FROM parties WHERE partyType IN ('SUPPLIER', 'BOTH') AND currentBalance < 0")
    fun getSupplierPayables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(ABS(currentBalance)), 0) FROM parties WHERE partyType IN ('CUSTOMER', 'BOTH') AND currentBalance < 0")
    fun getCustomerPayables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(currentBalance), 0) FROM parties WHERE partyType IN ('SUPPLIER', 'BOTH') AND currentBalance > 0")
    fun getSupplierReceivables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(currentBalance), 0) FROM parties WHERE currentBalance > 0")
    fun getTotalReceivables(): Flow<Long>

    @Query("SELECT COALESCE(SUM(ABS(currentBalance)), 0) FROM parties WHERE currentBalance < 0")
    fun getTotalPayables(): Flow<Long>

    @Query("""
        UPDATE journal_items 
        SET partyId = NULL 
        WHERE subsidiaryCode NOT IN (10201, 30101) AND partyId IS NOT NULL
    """)
    suspend fun sanitizePartyJournalItems()

    @Query("""
        UPDATE parties 
        SET currentBalance = (
            SELECT COALESCE(SUM(ji.debit - ji.credit), 0)
            FROM journal_items ji
            INNER JOIN journal_entries je ON ji.voucherId = je.id
            WHERE ji.partyId = :partyId 
              AND je.status != 'VOID'
              AND ji.subsidiaryCode IN (10201, 30101)
        )
        WHERE id = :partyId
    """)
    suspend fun recalculatePartyBalance(partyId: Long)

    @Query("""
        UPDATE parties 
        SET currentBalance = (
            SELECT COALESCE(SUM(ji.debit - ji.credit), 0)
            FROM journal_items ji
            INNER JOIN journal_entries je ON ji.voucherId = je.id
            WHERE ji.partyId = parties.id 
              AND je.status != 'VOID'
              AND ji.subsidiaryCode IN (10201, 30101)
        )
    """)
    suspend fun recalculateAllPartyBalances()
}
