package com.cowalskiiw2026.autoklix.data

import android.content.Context
import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Menjembatani Room (Entity) <-> model domain (Preset/ClickPoint) yang dipakai UI & BotEngine.
 */
class PresetRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val presetDao = db.presetDao()
    private val pointDao = db.clickPointDao()

    fun observePresets(): Flow<List<Preset>> =
        presetDao.observePresets().map { list ->
            val counts = pointDao.getPointCounts().associate { it.presetId to it.cnt }
            list.map { entity ->
                val cnt = counts[entity.id] ?: 0
                val dummyPoints = List(cnt) { com.cowalskiiw2026.autoklix.model.ClickPoint(order = it, type = com.cowalskiiw2026.autoklix.model.PointType.TAP, x = 0f, y = 0f) }
                Preset(entity.id, entity.name, entity.repeatCount, entity.repeatDurationMs, dummyPoints)
            }
        }

    fun observePoints(presetId: Long): Flow<List<ClickPoint>> =
        pointDao.observePoints(presetId).map { list -> list.map { it.toModel() } }

    suspend fun getFullPreset(presetId: Long): Preset? {
        val entity = presetDao.getPreset(presetId) ?: return null
        val points = pointDao.getPointsOnce(presetId).map { it.toModel() }
        return Preset(entity.id, entity.name, entity.repeatCount, entity.repeatDurationMs, points)
    }

    suspend fun savePreset(preset: Preset): Long {
        val id = if (preset.id == 0L) {
            presetDao.insert(PresetEntity(name = preset.name, repeatCount = preset.repeatCount, repeatDurationMs = preset.repeatDurationMs))
        } else {
            presetDao.update(PresetEntity(preset.id, preset.name, preset.repeatCount, preset.repeatDurationMs))
            preset.id
        }
        pointDao.deleteAllForPreset(id)
        preset.points.forEachIndexed { index, point ->
            pointDao.insert(point.toEntity(presetId = id, order = index))
        }
        return id
    }

    suspend fun deletePreset(preset: Preset) {
        presetDao.delete(PresetEntity(preset.id, preset.name, preset.repeatCount, preset.repeatDurationMs))
    }

    suspend fun addPoint(presetId: Long, point: ClickPoint): Long {
        val nextOrder = pointDao.getMaxOrder(presetId) + 1
        return pointDao.insert(point.toEntity(presetId = presetId, order = nextOrder))
    }

    suspend fun updatePoint(point: ClickPoint) {
        pointDao.update(point.toEntity(presetId = point.presetId, order = point.order))
    }

    suspend fun deletePoint(point: ClickPoint) {
        pointDao.delete(point.toEntity(presetId = point.presetId, order = point.order))
        // rapikan ulang urutan sisa titik
        val remaining = pointDao.getPointsOnce(point.presetId)
        remaining.forEachIndexed { idx, entity ->
            if (entity.order != idx) pointDao.update(entity.copy(order = idx))
        }
    }

    suspend fun getPointsOnce(presetId: Long): List<ClickPoint> =
        pointDao.getPointsOnce(presetId).map { it.toModel() }
}

private fun ClickPointEntity.toModel() = ClickPoint(
    id = id, presetId = presetId, order = order, type = type, x = x, y = y, x2 = x2, y2 = y2,
    actionDurationMs = actionDurationMs, delayAfterMs = delayAfterMs,
    pointSizeOverride = pointSizeOverride, pointOpacityOverride = pointOpacityOverride
)

private fun ClickPoint.toEntity(presetId: Long, order: Int) = ClickPointEntity(
    id = id, presetId = presetId, order = order, type = type, x = x, y = y, x2 = x2, y2 = y2,
    actionDurationMs = actionDurationMs, delayAfterMs = delayAfterMs,
    pointSizeOverride = pointSizeOverride, pointOpacityOverride = pointOpacityOverride
)
