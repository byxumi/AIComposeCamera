# AI 构图相机（AI Compose Camera）

基于 **Kotlin 原生架构 + Jetpack Compose** 的 Android AI 构图相机，对标 mola 相机的操作逻辑与 AI 构图功能（逆向分析 com.shijiexiangxian.mola 后实现），v6.0 = **按 mola 相机 APK 逐元素一比一重建主界面**（rj0.java / wx1.java 精确复刻）：底部导航 52dp 胶囊 + 金分隔线 / 80dp 黑金快门 / 顶部镜头标识 / 调色盘弹出层 / 模式栏金色渐变胶囊，消除「ui 相似但不完全是、界面杂乱差」问题；v5.x 全部 mola 功能（黑金主题 / 151 款 LUT 滤镜 + 收藏与 AI 推荐 / AI 目标圈 / 流光快门 / 实况照片 / 满血像素 / 会员三档 / AI 摄影师三方案）与 Doka 编辑器完整保留。

## ✨ 功能

- 📷 **实时预览与拍照**：CameraX 6 UseCase（Preview / ImageCapture / ImageAnalysis / VideoCapture），保存到相册 + EXIF
- 🤖 **AI 摄影师（mola 核心）**：
  - 相机页笑脸入口 → 欢迎语 → 输入拍摄意图 → AI 思考中 → 生成定制拍摄方案 → **分步引导**
  - 引导步骤：找主体 → 对准构图 → 按下快门
  - **目标圆圈**：黄色未对准（带移动方向箭头）/ 绿色已对准（自动拍摄）
  - 底部「AI 辅助」一键开启自动构图
- 📐 **拍摄模式**：自动 / 人像 / 夜景 / 美食 / 风景 / 视频，各模式构图规则不同
- 🎨 **滤镜轮**：原图 / 胶片 / 清新 / 复古 / 黑白 / 暖阳 / 冷调 / 美食暖 / 人像柔 / 夜城（编辑器实时预览 + 保存）
- 🖼️ **Doka 全功能编辑器**（相册点按进入）：
  - ✂️ 裁剪（四角手柄 + 遮罩）、🔄 旋转 90°、↔️ 水平/垂直翻转
  - 🎨 滤镜实时预览、亮度 / 对比度 / 饱和度 / 锐化 / 色温 / 暗角 6 项调整滑块
  - 🔤 文字叠加（拖动/删除）、😀 贴纸 6 种（心/星/笑脸/相机/闪光/勾）、💧 水印（日期/品牌）
  - ↩️ 撤销 8 步、保存到相册、分享
- ⚡ **端侧 AI 检测（离线）**：ML Kit 对象检测 + 人脸检测，识别人物/宠物/食物/商品
- 🧍 姿态引导（可选）：MediaPipe Pose Landmarker
- 📐 构图引导：三分法 / 中心网格、推荐取景框 + 方向箭头、水平仪、实时评分 0-100
- ⚠️ 错误预防：头脚裁切、主体过小/过大、头顶留白不足、闭眼/微笑/肩膀倾斜
- ⚡ 自动快门：三档灵敏度，构图达标且稳定时自动拍摄
- 🎥 视频录制：模式栏切到视频 → 快门变录制
- 🔄 前后摄切换、闪光灯、点击对焦、数码变焦
- 🏠 顶部工具栏：画幅 4:3 / 闪光灯 / 定时 / 相框 / 设置（mola 布局）
- 🖼️ 相册：2 列网格 + 全屏大图 + 点按进编辑器
- ⚙️ 设置：网格模式 / 检测开关 / 评分 / 自动快门灵敏度 / 姿势 / 震动 / 快门音 / 独立相册 / 水印

## 🛠 技术栈

| 组件 | 技术 |
|---|---|
| 语言/UI | Kotlin 2.0.21 + Jetpack Compose（BOM 2024.12.01） |
| 相机 | CameraX 1.4.2（含 camera-video） |
| 检测 | ML Kit Object Detection 17.0.2 / Face Detection 16.1.5 + MediaPipe Pose Landmarker |
| 架构 | MVVM（MainActivity → NavHost → ViewModel → CameraManager → AiGuideEngine → Compose Overlay） |
| 数据 | DataStore Preferences + Coil |
| 构建 | Gradle 8.11 + AGP 8.10 + JDK 17，minSdk 26 / target 35 |

## 🎨 设计系统（v4.0 taste 重构）

- **Design Read**：专业暗色相机 — 纯黑取景器 + 白色控件 + 单一强调色（iOS 黄 `#FFD60A`）
- **单强调色锁定**：全应用唯一强调色；语义色（成功绿/错误红/警告橙）仅表含义
- **受控玻璃**：仅底部控制面板 1 处毛玻璃浮层
- **设计 token**：`core/design/` Color（14 色）/ Type（7 档）/ Shape（面板 24dp / 控件 14dp / 小 8dp）/ Motion（4 类动机化动效，尊重系统减弱动效）
- 对比度 WCAG AA（白 on 黑 21:1 / 黄 on 黑 11:1 / 次级灰 6.7:1）

## 🚀 构建

本地（Android Studio / 命令行）：
```bash
./gradlew :app:assembleDebug
```

或使用 **GitHub Actions 云端构建**：推送后自动构建；打 `v*` 标签自动发布 Release（双 APK）。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

最新 Release（镜像加速下载）：
```text
https://ghfast.top/https://github.com/byxumi/AIComposeCamera/releases/download/v6.0.1/app-debug.apk
```

## 📁 结构

```
app/src/main/java/com/aicamera/
├── MainActivity.kt                # Compose 壳 + 暗色主题
├── core/
│   ├── design/                    # 设计 token 系统：Color/Type/Shape/Motion/Components
│   └── util/BitmapFilters.kt      # 滤镜 ColorMatrix 管线
├── camera/
│   ├── CameraManager.kt           # CameraX 6 UseCase + 对焦/变焦/拍照/录像
│   └── AnalyzerManager.kt         # ML Kit Object/Face + MediaPipe Pose 检测
├── ai/
│   ├── AiGuideEngine.kt           # 目标圈 / 分步引导 / 手动选主体
│   ├── AiProvider.kt              # 云端大模型预留（qwen-vl-plus）
│   └── LocalAiRules.kt            # 本地场景识别与建议
├── data/
│   ├── SettingsRepository.kt      # DataStore 设置
│   ├── GalleryRepository.kt       # MediaStore 相册
│   └── PhotoStore.kt              # 保存/删除/分享
├── domain/model/Models.kt         # 数据契约（Subject/Guidance/AiTarget/OverlayState…）
└── ui/
    ├── camera/                    # CameraScreen / CameraOverlay / CameraViewModel
    ├── gallery/GalleryScreen.kt   # 2 列网格 + 全屏大图
    ├── editor/EditorScreen.kt     # Doka 全功能编辑器（裁剪/滤镜/文字/贴纸/水印）
    ├── editor/EditorViewModel.kt  # 编辑器状态（撤销 8 步 / 合成导出）
    ├── settings/SettingsScreen.kt # 分组设置
    └── navigation/AppNavHost.kt   # 导航路由
```

## 📄 说明

- 全部检测与构图分析**本地离线**，无需联网、无需 API key（云端 AiProvider 接口已预留）
- 参考项目：ai-composition-assistant、clifftseng/AI-Camera、compose-ai、CameraQ、mola（产品形态）
- 真机兼容性：CameraX 已封装，适配主流机型（已在 vivo V2329A 目标机型验证）
- 版本历史：v2.0 功能闭环 → v2.1 iOS 二改 → v2.2 mola 复刻 → v2.3 重构闭环 → v3.0 全面重写 → v4.0 taste 重构 → v5.0 mola UI + Doka 编辑器 + 可用性修复 → v5.1 直接逆向 mola 一比一复刻（151 LUT/黑金/流光/实况/满血）→ v5.2 + 收藏/AI 推荐滤镜/会员对话框/AI 摄影师三方案 → **v6.0 主界面逐元素一比一重建（底栏/快门/镜头标识/调色盘/模式渐变）** → **v6.0.1 顶部会员按钮 + 调色盘模式感知提示**