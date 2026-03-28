package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.ReferenceAssetRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ReferenceAssetRecord)

    @Query("SELECT * FROM reference_asset_records WHERE drillId = :drillId ORDER BY createdAtMs DESC")
    fun observeByDrill(drillId: String): Flow<List<ReferenceAssetRecord>>

    @Query("SELECT * FROM reference_asset_records WHERE id = :assetId LIMIT 1")
    suspend fun getById(assetId: String): ReferenceAssetRecord?
}
