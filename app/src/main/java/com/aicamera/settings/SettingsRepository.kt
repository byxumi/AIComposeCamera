package com.aicamera.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "camera_settings")

/** 构图网格模式 */
enum class GridMode(val value: Int) {
    NONE(0), THIRDS(1), GOLDEN(2);

    companion object {
        fun from(v: Int) = entries.firstOrNull { it.value == v } ?: THIRDS
    }
}

/** 自动快门灵敏度阈值（构图达标分） */
enum class ShutterSensitivity(val value: Int, val threshold: Int) {
    HIGH(0, 80), MEDIUM(1, 70), LOW(2, 60);

    companion object {
        fun from(v: Int) = entries.firstOrNull { it.value == v } ?: MEDIUM
    }
}

/** 应用设置数据类 */
data class CameraSettings(
    val gridMode: GridMode = GridMode.THIRDS,
    val showSubjects: Boolean = true,
    val showScore: Boolean = true,
    val autoShutter: Boolean = false,
    val shutterSensitivity: ShutterSensitivity = ShutterSensitivity.MEDIUM,
    val poseGuidance: Boolean = false,
    val hapticFeedback: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GRID = intPreferencesKey("grid_mode")
        val SUBJECTS = booleanPreferencesKey("show_subjects")
        val SCORE = booleanPreferencesKey("show_score")
        val AUTO = booleanPreferencesKey("auto_shutter")
        val SENSITIVITY = intPreferencesKey("shutter_sensitivity")
        val POSE = booleanPreferencesKey("pose_guidance")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
    }

    val settings: Flow<CameraSettings> = context.dataStore.data.map { p ->
        CameraSettings(
            gridMode = GridMode.from(p[Keys.GRID] ?: GridMode.THIRDS.value),
            showSubjects = p[Keys.SUBJECTS] ?: true,
            showScore = p[Keys.SCORE] ?: true,
            autoShutter = p[Keys.AUTO] ?: false,
            shutterSensitivity = ShutterSensitivity.from(p[Keys.SENSITIVITY] ?: ShutterSensitivity.MEDIUM.value),
            poseGuidance = p[Keys.POSE] ?: false,
            hapticFeedback = p[Keys.HAPTIC] ?: true
        )
    }

    suspend fun setGridMode(mode: GridMode) =
        context.dataStore.edit { it[Keys.GRID] = mode.value }

    suspend fun setShowSubjects(v: Boolean) =
        context.dataStore.edit { it[Keys.SUBJECTS] = v }

    suspend fun setShowScore(v: Boolean) =
        context.dataStore.edit { it[Keys.SCORE] = v }

    suspend fun setAutoShutter(v: Boolean) =
        context.dataStore.edit { it[Keys.AUTO] = v }

    suspend fun setSensitivity(s: ShutterSensitivity) =
        context.dataStore.edit { it[Keys.SENSITIVITY] = s.value }

    suspend fun setPoseGuidance(v: Boolean) =
        context.dataStore.edit { it[Keys.POSE] = v }

    suspend fun setHaptic(v: Boolean) =
        context.dataStore.edit { it[Keys.HAPTIC] = v }
}