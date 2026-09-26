# AI 构图相机（AI Compose Camera）

基于 **Kotlin 原生架构** 的 Android AI 构图相机应用。打开相机即实时分析画面，用构图引导线、推荐取景框、方向箭头与实时评分告诉你"怎么拍更好看"，构图到位时可自动按下快门。

## ✨ 功能

- 📷 **实时预览与拍照**：CameraX 三 UseCase（Preview / ImageCapture / ImageAnalysis）
- 🤖 **端侧 AI 检测（离线）**：ML Kit 对象检测 + 人脸检测，识别人物/宠物/食物/商品/建筑等主体
- 📐 **构图引导**：
  - 三分法 / 黄金分割网格
  - 推荐取景框（主体对准交叉点）
  - 方向箭头（向左/右/上/下移动）
  - 实时评分 0-100
- ⚠️ **错误预防**：头脚裁切、主体过小/过大、头顶留白不足检测
- ⚡ **自动快门**：构图达标且稳定时自动拍摄（三档灵敏度）
- 🔄 前后摄切换、闪光灯、点击对焦、变焦滑杆
- 🎨 徕卡风极简 UI：近黑底 + 冷青点缀

## 🛠 技术栈

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin 1.9 + XML View |
| 相机 | CameraX 1.3.2 |
| 检测 | ML Kit Object Detection 17 / Face Detection 16 |
| 架构 | MVVM（MainActivity → ViewModel → CameraManager → 规则引擎 → OverlayView） |
| 构建 | Gradle 8.7 + AGP 8.2 + JDK 17，minSdk 24 / target 34 |

## 🚀 构建

本地（Android Studio）：
```bash
./gradlew :app:assembleDebug
```

或使用 **GitHub Actions 云端构建**：推送后自动构建，在 Actions 页面下载 APK Artifact。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 📁 结构

```
app/src/main/java/com/aicamera/
├── MainActivity.kt                # UI 壳：权限、相机初始化、状态渲染
├── camera/CameraManager.kt        # CameraX + ML Kit 检测 + 拍照
├── composition/
│   ├── CompositionModels.kt       # 数据模型（Subject/Guidance/Result）
│   ├── CompositionRuleEngine.kt   # 本地构图规则引擎（评分/引导）
│   └── OverlayView.kt             # Canvas 取景叠加层
├── viewmodel/CompositionViewModel.kt  # 状态编排 + 自动快门
└── settings/SettingsActivity.kt   # 设置页
```

## 📄 说明

- 全部检测与构图分析**本地离线**，无需联网、无需 API key
- 参考项目：ai-composition-assistant（构图规则体系）、compose-ai（构图文案）、CameraQ（本地评分思路）