package com.aicamera.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.composition.CompositionModels.Subject
import com.aicamera.composition.CompositionRuleEngine
import com.aicamera.composition.CompositionModels.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * CompositionViewModel — 业务状态编排
 *
 * 接收 CameraManager 的检测结果，跑本地构图规则引擎，
 * 输出 [CompositionUiState] 给 UI（OverlayView 绘制 + 顶部提示）。
 */
class CompositionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompositionUiState())
    val uiState: StateFlow<CompositionUiState> = _uiState.asStateFlow()

    // 是否允许自动快门
    var autoShutterEnabled: Boolean = false
    // 自动快门: 构图评分阈值
    var autoShutterThreshold: Int = 80

    private var lastScore = 0
    private var stableFrames = 0

    /**
     * 每帧调用（后台线程 -> 这里做轻量计算）
     */
    fun onFrameDetected(subjects: List<Subject>, imageWidth: Int, imageHeight: Int) {
        val result = CompositionRuleEngine.analyze(subjects, imageWidth, imageHeight)

        // 自动快门状态机：构图稳定达标时提示
        val shutterHint = if (autoShutterEnabled && result.isPerfect) {
            if (result.score >= autoShutterThreshold) {
                if (result.score == lastScore) stableFrames++ else stableFrames = 0
                if (stableFrames >= 3) "ready" else "locking"
            } else "waiting"
        } else "waiting"
        lastScore = result.score

        _uiState.update {
            it.copy(
                subjects = subjects,
                composition = result,
                score = result.score,
                isPerfect = result.isPerfect,
                shutterHint = shutterHint
            )
        }
    }

    fun setAutoShutter(enabled: Boolean, threshold: Int = 80) {
        autoShutterEnabled = enabled
        autoShutterThreshold = threshold
        stableFrames = 0
    }

    fun resetShutter() {
        stableFrames = 0
        _uiState.update { it.copy(shutterHint = "waiting") }
    }
}

/**
 * UI 状态
 */
data class CompositionUiState(
    val subjects: List<Subject> = emptyList(),
    val composition: Result = Result(),
    val score: Int = 100,
    val isPerfect: Boolean = false,
    val shutterHint: String = "waiting",  // waiting / locking / ready
    val isCapturing: Boolean = false,
    val lastPhotoUri: Uri? = null
)