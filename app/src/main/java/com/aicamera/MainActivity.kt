package com.aicamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aicamera.camera.CameraManager
import com.aicamera.composition.OverlayView
import com.aicamera.databinding.ActivityMainBinding
import com.aicamera.settings.SettingsActivity
import com.aicamera.viewmodel.CompositionViewModel
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity — UI 壳
 *
 * 只做：权限、相机初始化、UI 交互转发、状态渲染。
 * 业务逻辑在 CompositionViewModel / CameraManager / 规则引擎。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: CompositionViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager

    private var outputDir: File? = null
    private var flashOn = false
    private var zoomExpanded = false
    private var gridStyle = 0

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true) {
            initCamera()
        } else {
            binding.guideText.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareOutputDir()
        setupListeners()

        if (hasCameraPermission()) {
            initCamera()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        observeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }

    // ─── 初始化 ───

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun initCamera() {
        cameraManager = CameraManager(this, this, binding.previewView)
        cameraManager.frameCallback = object : CameraManager.FrameCallback {
            override fun onFrameAnalyzed(subjects: List<com.aicamera.composition.CompositionModels.Subject>, imageWidth: Int, imageHeight: Int) {
                runOnUiThread {
                    viewModel.onFrameDetected(subjects, imageWidth, imageHeight)
                }
            }

            override fun onCameraError(message: String) {
                runOnUiThread {
                    binding.guideText.text = message
                }
            }
        }
        cameraManager.setDetectors(objectsEnabled = true, faceEnabled = true)
        cameraManager.startCamera()
    }

    private fun prepareOutputDir() {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AICompose")
        if (!dir.exists()) dir.mkdirs()
        outputDir = dir
    }

    // ─── 监听 ───

    private fun setupListeners() {
        binding.captureButton.setOnClickListener { takePicture() }

        binding.cameraSwitch.setOnClickListener {
            cameraManager.switchCamera()
        }

        binding.flashToggle.setOnClickListener {
            flashOn = !flashOn
            cameraManager.setFlashEnabled(flashOn)
            binding.flashToggle.setImageResource(if (flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
            binding.flashToggle.imageTintList = ContextCompat.getColorStateList(
                this,
                if (flashOn) R.color.neo_accent else R.color.neo_text_secondary
            )
        }

        binding.gridToggle.setOnClickListener {
            gridStyle = (gridStyle + 1) % 2 // 0 三分法 -> 1 黄金分割 -> 0
            binding.overlayView.gridStyle = gridStyle
            binding.overlayView.showGrid = true
            Toast.makeText(
                this,
                if (gridStyle == 0) "三分法网格" else "黄金分割网格",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.autoShutterToggle.setOnClickListener {
            val enabled = !viewModel.autoShutterEnabled
            viewModel.setAutoShutter(enabled)
            binding.autoShutterToggle.imageTintList = ContextCompat.getColorStateList(
                this,
                if (enabled) R.color.neo_accent else R.color.neo_text_secondary
            )
            Toast.makeText(
                this,
                if (enabled) "自动快门已开启" else "自动快门已关闭",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val max = cameraManager.getMaxZoom()
                val ratio = 1f + (max - 1f) * progress / 100f
                cameraManager.setZoom(ratio)
                binding.zoomText.text = String.format("%.1fx", ratio)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 长按弹出变焦滑杆
        binding.previewView.setOnLongClickListener {
            zoomExpanded = !zoomExpanded
            binding.zoomRow.visibility = if (zoomExpanded) View.VISIBLE else View.GONE
            true
        }

        // 单点对焦（轻触预览区对焦）
        binding.previewView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 长按 vs 单击：用 down 时间区分，这里简单实现：down 记录，up 时若时间短则对焦
                    return@setOnTouchListener false // 让系统继续处理长按
                }
                MotionEvent.ACTION_UP -> {
                    val duration = event.eventTime - event.downTime
                    if (duration < 300) {
                        cameraManager.focusOn(event.x, event.y)
                    }
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 构图叠加层
                    binding.overlayView.updateData(
                        state.composition,
                        state.subjects
                    )
                    // 评分
                    binding.scoreText.text = getString(R.string.score_format, state.score)
                    binding.scoreText.setTextColor(
                        if (state.score >= 80) ContextCompat.getColor(this@MainActivity, R.color.guide_good)
                        else if (state.score >= 60) ContextCompat.getColor(this@MainActivity, R.color.neo_accent)
                        else ContextCompat.getColor(this@MainActivity, R.color.guide_warn)
                    )
                    // 引导文字（取第 2 条起（跳过 NONE_001 未检测提示）
                    val guide = state.composition.guidances.firstOrNull()?.message
                    binding.guideText.text = guide ?: getString(R.string.guide_status)
                    binding.guideText.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            if (state.composition.isPerfect) R.color.guide_good
                            else if (state.composition.guidances.any { it.style == com.aicamera.composition.CompositionModels.Style.ERROR }) R.color.guide_error
                            else R.color.neo_text_primary
                        )
                    )
                    // 自动快门就绪提示
                    when (state.shutterHint) {
                        "ready" -> {
                            binding.readyOverlay.text = getString(R.string.position_good)
                            binding.readyOverlay.visibility = View.VISIBLE
                        }
                        "locking" -> {
                            binding.readyOverlay.text = "锁定中…"
                            binding.readyOverlay.visibility = View.VISIBLE
                        }
                        else -> binding.readyOverlay.visibility = View.GONE
                    }
                }
            }
        }
    }

    // ─── 拍照 ───

    private fun takePicture() {
        val dir = outputDir ?: return
        cameraManager.takePicture(dir, object : CameraManager.CaptureCallback {
            override fun onCaptured(file: File) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
                    // 通知相册
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.DATA, file.absolutePath)
                    }
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }
            }

            override fun onCaptureError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}