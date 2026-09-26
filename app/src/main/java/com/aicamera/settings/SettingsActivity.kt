package com.aicamera.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aicamera.composition.OverlayView
import com.aicamera.databinding.ActivitySettingsBinding
import com.google.android.material.slider.Slider

/**
 * 设置页 — 自动快门、构图网格、人脸检测偏好
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "aicamera_prefs"
        const val KEY_AUTO_SHUTTER = "auto_shutter"
        const val KEY_SENSITIVITY = "shutter_sensitivity"
        const val KEY_GRID = "grid_enabled"
        const val KEY_FACE_DETECTION = "face_detection"

        fun isAutoShutter(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_SHUTTER, false)

        fun sensitivity(context: Context): Int =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SENSITIVITY, 1)

        fun isGridEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GRID, true)

        fun isFaceDetection(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FACE_DETECTION, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 初始值
        binding.autoShutterSwitch.isChecked = isAutoShutter(this)
        binding.gridSwitch.isChecked = isGridEnabled(this)
        binding.faceSwitch.isChecked = isFaceDetection(this)
        binding.sensitivitySlider.value = sensitivity(this).toFloat()
        updateSensitivityLabel()

        // 监听
        binding.autoShutterSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_AUTO_SHUTTER, checked).apply()
        }
        binding.gridSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_GRID, checked).apply()
        }
        binding.faceSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_FACE_DETECTION, checked).apply()
        }
        binding.sensitivitySlider.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            prefs.edit().putInt(KEY_SENSITIVITY, value.toInt()).apply()
            updateSensitivityLabel()
        }
    }

    private fun updateSensitivityLabel() {
        binding.sensitivityLabel.text = when (binding.sensitivitySlider.value.toInt()) {
            0 -> "低（更严格，构图更稳才拍）"
            1 -> "中"
            2 -> "高（更灵敏，快速抓拍）"
            else -> "中"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}