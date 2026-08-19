package com.cowalskiiw2026.autoklix.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickPointDao {
    @Query("SELECT * FROM click_points WHERE presetId = :presetId ORDER BY `order` ASC")
    fun observePoints(presetId: Long): Flow<List<ClickPointEntity>>

    @Query("SELECT * FROM click_points WHERE presetId = :presetId ORDER BY `order` ASC")
    suspend fun getPointsOnce(presetId: Long): List<ClickPointEntity>

    @Insert
    suspend fun insert(point: ClickPointEntity): Long

    @Update
    suspend fun update(point: ClickPointEntity)

    @Update
    suspend fun updateAll(points: List<ClickPointEntity>)

    @Delete
    suspend fun delete(point: ClickPointEntity)

    @Query("DELETE FROM click_points WHERE presetId = :presetId")
    suspend fun deleteAllForPreset(presetId: Long)

    @Query("SELECT COALESCE(MAX(`order`), -1) FROM click_points WHERE presetId = :presetId")
    suspend fun getMaxOrder(presetId: Long): Int

    @Query("SELECT presetId, COUNT(*) as cnt FROM click_points GROUP BY presetId")
    suspend fun getPointCounts(): List<PresetPointCount>
}

data class PresetPointCount(val presetId: Long, val cnt: Int)
