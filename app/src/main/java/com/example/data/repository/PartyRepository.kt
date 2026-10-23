package com.example.data.repository

import com.example.data.dao.PartyDao
import com.example.data.model.PartyEntity
import kotlinx.coroutines.flow.Flow

class PartyRepository(val dao: PartyDao) {

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties()
    val allCustomers: Flow<List<PartyEntity>> = dao.getAllCustomers()
    val allSuppliers: Flow<List<PartyEntity>> = dao.getAllSuppliers()
    val overCreditLimitCount: Flow<Int> = dao.getOverCreditLimitCount()
    val totalReceivables: Flow<Long> = dao.getTotalReceivables()
    val totalPayables: Flow<Long> = dao.getTotalPayables()
    val customerReceivables: Flow<Long> = dao.getCustomerReceivables()
    val supplierPayables: Flow<Long> = dao.getSupplierPayables()
    val customerPayables: Flow<Long> = dao.getCustomerPayables()
    val supplierReceivables: Flow<Long> = dao.getSupplierReceivables()

    fun searchParties(query: String): Flow<List<PartyEntity>> = dao.searchParties(query)

    suspend fun getPartyById(id: Long): PartyEntity? = dao.getPartyById(id)

    suspend fun insertParty(party: PartyEntity): Long = dao.insertParty(party)

    suspend fun updateParty(party: PartyEntity) = dao.updateParty(party)

    suspend fun deleteParty(party: PartyEntity) = dao.deleteParty(party)

    suspend fun adjustBalance(partyId: Long, delta: Long) =
        dao.updatePartyBalance(partyId, delta)

    suspend fun updatePartyBalance(partyId: Long, delta: Long) =
        dao.updatePartyBalance(partyId, delta)

    suspend fun recalculatePartyBalance(partyId: Long) =
        dao.recalculatePartyBalance(partyId)

    suspend fun recalculateAllPartyBalances() =
        dao.recalculateAllPartyBalances()
}
