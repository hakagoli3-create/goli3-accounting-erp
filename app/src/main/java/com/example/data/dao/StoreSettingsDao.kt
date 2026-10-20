package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StoreSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreSettingsDao {

    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    suspend fun getStoreSettings(): StoreSettingsEntity?

    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    fun getStoreSettingsFlow(): Flow<StoreSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: StoreSettingsEntity)
}
