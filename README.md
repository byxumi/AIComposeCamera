# AI 构图相机（AI Compose Camera）

基于 **Kotlin 原生架构 + Jetpack Compose** 的 Android AI 构图相机。打开相机即实时分析画面，用构图引导线、推荐取景框、方向箭头与实时评分告诉你"怎么拍更好看"，构图到位时可自动按下快门。

## ✨ 功能

- 📷 **实时预览与拍照**：CameraX 三 UseCase（Preview / ImageCapture / ImageAnalysis），保存到相册 + EXIF
- 🤖 **端侧 AI 检测（离线）**：ML Kit 对象检测 + 人脸检测，识别人物/宠物/食物/商品等主体
- 🧍 **姿态引导（可选）**：MediaPipe Pose Landmarker 实时骨架，辅助构图（设置页开关）
- 📐 **构图引导**：
  - 三分法 / 黄金分割网格
  - 推荐取景框（主体对准交叉点）+ 方向箭头（左/右/上/下/进退）
  - 水平仪（传感器，±1.5° 变绿）
  - 实时评分 0-100
- ⚠️ **错误预防**：头脚裁切、主体过小/过大、头顶留白不足、闭眼/微笑/肩膀倾斜检测
- ⚡ **自动快门**：构图达标且稳定时自动拍摄（三档灵敏度）
- 🔄 前后摄切换、闪光灯、点击对焦、数码变焦
- 🎨 徕卡风极简 UI：近黑底 + 冷青点缀（Material 3 Compose）

## 🛠 技术栈

| 组件 | 技术 |
|---|---|
| 语言/UI | Kotlin 2.0 + Jetpack Compose（Material 3） |
| 相机 | CameraX 1.4.2 |
| 检测 | ML Kit Object Detection 17 / Face Detection 16 + MediaPipe Pose Landmarker |
| 架构 | MVVM（MainActivity → ViewModel → CameraManager → 规则引擎 → Compose Overlay） |
| 设置 | DataStore Preferences |
| 构建 | Gradle 8.7 + AGP 8.10 + JDK 17，minSdk 26 / target 34 |

## 🚀 构建

本地（Android Studio / 命令行）：
```bash
./gradlew :app:assembleDebug
```

或使用 **GitHub Actions 云端构建**：推送后自动构建，在 Actions 页面下载 APK Artifact；打 `v*` 标签自动发布 Release。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 📁 结构

```
app/src/main/java/com/aicamera/
├── MainActivity.kt                # Compose 壳 + 主题
├── camera/
│   ├── CameraManager.kt           # CameraX 三 UseCase + 对焦/变焦/拍照
│   ├── AnalyzerManager.kt         # ML Kit Object/Face + MediaPipe Pose 检测
│   └── PhotoSaver.kt              # 保存到相册 + EXIF
├── composition/
│   ├── CompositionModels.kt       # 数据模型（Subject/Guidance/OverlayState）
│   ├── CompositionRuleEngine.kt   # 优先级规则引擎（一次一个提示）
│   ├── CompositionScorer.kt       # 0-100 评分
│   ├── FramingBoxEngine.kt        # 推荐取景框 + 方向箭头
│   ├── DebounceTracker.kt         # 连续 N 帧防抖
│   └── AutoShutterEngine.kt       # 自动快门状态机
├── settings/SettingsRepository.kt # DataStore 设置
├── viewmodel/CompositionViewModel.kt  # 状态编排 + 传感器水平仪
└── ui/
    ├── CameraScreen.kt            # 相机界面（预览 + 控制栏）
    ├── CompositionOverlay.kt      # Canvas 覆盖层（网格/框/箭头/水平仪）
    └── SettingsScreen.kt          # 设置页
```

## 📄 说明

- 全部检测与构图分析**本地离线**，无需联网、无需 API key
- 参考项目：ai-composition-assistant、clifftseng/AI-Camera、compose-ai、CameraQ、mola（产品形态）
- 真机兼容性：CameraX 已封装，适配主流机型（已在 vivo V2329A 目标机型验证）