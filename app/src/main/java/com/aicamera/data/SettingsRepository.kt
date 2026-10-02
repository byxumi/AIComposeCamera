package com.aicamera.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicamera.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 应用设置 DataStore（全局单例，避免重复创建实例） */
private val Context.dataStore by preferencesDataStore(name = "app_settings")

/** 负责 AppSettings 的持久化读写（DataStore Preferences） */
class SettingsRepository(private val context: Context) {

    /** 持续监听设置，任何变更都会重新发射最新值 */
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            gridMode = prefs[Keys.GRID_MODE] ?: 1,
            showSubjects = prefs[Keys.SHOW_SUBJECTS] ?: true,
            showScore = prefs[Keys.SHOW_SCORE] ?: true,
            autoShutter = prefs[Keys.AUTO_SHUTTER] ?: false,
            shutterSensitivity = prefs[Keys.SHUTTER_SENSITIVITY] ?: 70,
            poseGuidance = prefs[Keys.POSE_GUIDANCE] ?: false,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            shutterSound = prefs[Keys.SHUTTER_SOUND] ?: true,
            saveLocation = prefs[Keys.SAVE_LOCATION] ?: false,
            watermarkMode = prefs[Keys.WATERMARK_MODE] ?: "none"
        )
    }

    suspend fun updateGridMode(value: Int) {
        context.dataStore.edit { it[Keys.GRID_MODE] = value }
    }

    suspend fun updateShowSubjects(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SUBJECTS] = value }
    }

    suspend fun updateShowScore(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SCORE] = value }
    }

    suspend fun updateAutoShutter(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SHUTTER] = value }
    }

    suspend fun updateShutterSensitivity(value: Int) {
        context.dataStore.edit { it[Keys.SHUTTER_SENSITIVITY] = value }
    }

    suspend fun updatePoseGuidance(value: Boolean) {
        context.dataStore.edit { it[Keys.POSE_GUIDANCE] = value }
    }

    suspend fun updateHaptic(value: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = value }
    }

    suspend fun updateShutterSound(value: Boolean) {
        context.dataStore.edit { it[Keys.SHUTTER_SOUND] = value }
    }

    suspend fun updateSaveLocation(value: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_LOCATION] = value }
    }

    suspend fun updateWatermarkMode(value: String) {
        context.dataStore.edit { it[Keys.WATERMARK_MODE] = value }
    }

    private object Keys {
        val GRID_MODE = intPreferencesKey("grid_mode")
        val SHOW_SUBJECTS = booleanPreferencesKey("show_subjects")
        val SHOW_SCORE = booleanPreferencesKey("show_score")
        val AUTO_SHUTTER = booleanPreferencesKey("auto_shutter")
        val SHUTTER_SENSITIVITY = intPreferencesKey("shutter_sensitivity")
        val POSE_GUIDANCE = booleanPreferencesKey("pose_guidance")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound")
        val SAVE_LOCATION = booleanPreferencesKey("save_location")
        val WATERMARK_MODE = stringPreferencesKey("watermark_mode")
    }
}