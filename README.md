# AI 构图相机（AI Compose Camera）

基于 **Kotlin 原生架构 + Jetpack Compose** 的 Android AI 构图相机，**一比一复刻 mola 相机**的操作逻辑与 AI 构图功能（逆向分析 com.shijiexiangxian.mola 后实现）。

## ✨ 功能

- 📷 **实时预览与拍照**：CameraX 三 UseCase（Preview / ImageCapture / ImageAnalysis），保存到相册 + EXIF
- 🤖 **AI 摄影师（mola 核心）**：
  - 右下角笑脸入口 → 欢迎语 → 输入拍摄意图 → AI 思考中 → 生成定制拍摄方案 → **分步引导**
  - AI 推荐的引导步骤：找主体 → 对准构图 → 按下快门
  - **目标圆圈**：黄色未对准（带移动方向箭头）/ 绿色已对准
  - 底部「AI 辅助」按钮一键开启自动构图
- 📐 **拍摄模式**（mola 底部模式栏）：自动 / 人像 / 夜景 / 美食 / 风景 / 视频，各模式智能构图规则不同
- 🎨 **滤镜轮**：原图 / 胶片 / 清新 / 复古 / 黑白 / 暖阳 / 冷调 / 美食暖 / 人像柔 / 夜城
- ⚡ **端侧 AI 检测（离线）**：ML Kit 对象检测 + 人脸检测，识别人物/宠物/食物/商品
- 🧍 姿态引导（可选）：MediaPipe Pose Landmarker
- 📐 构图引导：三分法 / 黄金分割网格、推荐取景框 + 方向箭头、水平仪、实时评分 0-100
- ⚠️ 错误预防：头脚裁切、主体过小/过大、头顶留白不足、闭眼/微笑/肩膀倾斜
- ⚡ 自动快门：构图达标且稳定时自动拍摄
- 🔄 前后摄切换、闪光灯、点击对焦、数码变焦
- 🏠 顶部工具栏：画幅 4:3 / 闪光灯 / 定时 / 设置（mola 布局）
- 🎨 iOS 风格深色 UI（Material 3 Compose）

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