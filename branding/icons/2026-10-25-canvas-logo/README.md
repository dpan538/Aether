# Aether · Canvas Logo 定稿 · 99 条圆环

2026-10-25，用户认可 Canvas 构图适合作为 Logo，并要求外围稍亮、密度降低、线略粗，改为 **99 条细圆环**。用户明确选择本设计为新的 Logo／图标，最后要求绝对居中、四周留白相等、外半径 46%、圆环再略粗、中心白光稍亮。当前 `AetherLogoCanvas.kt` 与导出图同步采用 99 条、1.30dp 线宽、最高透明度 0.48，所有圆环和光核共用画布中心，最外圈半径精确为宽度 46%。此前 120 条及 99 条／40%、42%、44% 版本均留存。未接入 Android 工程，也没有 imagegen。

[整体预览](index.html) · [上一版 44%](references/logo-99-centered-44.svg) · [原稿 120 条](references/logo-120.svg) · [当前方形 SVG](canvas-square.svg) · [圆角 SVG](canvas-logo.svg) · [3072px PNG](canvas-logo-3072.png)

## 33 环小屏候选

用户在选定 99 环后，另要求尝试 33 环以便小屏辨认。`small-screen-33.*` 保留 46% 半径、同心位置、暖白光核及颜色，线宽按 512 逻辑基准从 1.30 增为 3.60，最高透明度仍为 0.48。这是另一个候选，不替换已选定的 99 环母版。

用户随后认为 33 环太粗，要求试 60 环。最新 `small-screen-60.*` 采用 60 条、2.00 线宽，半径／光核／颜色和最高透明度保持相同。[99／60／33 同比例及小尺寸对照](ring-comparison.html)用于选择；60 环当前仍是试稿。对应 Kotlin 参数保存在 `AetherLogoCanvas60.kt`。

[99／33 对照](33-rings.html)同时展示相同比例大图和 48、96、192 三种尺寸；对照图统一由 3072px 母版经 Lanczos 缩小，避免在低分辨率直接重新叠画造成的摩尔纹；图形比例仍以 512 逻辑基准等比缩放。对应参考函数保存在 `AetherLogoCanvas33.kt`。是否采用 33 环作为特定小尺寸资源，待用户看图后决定。

主图仍以 512 逻辑单位、密度 1 为基准：背景 `#0B0D12`，中央渐变半径为宽度的 20%，暖白 `#FFF5EE`（由 `#FDE8E0` 微提亮） → 金黄 `#F3C785` → 淡绿 `#91B879` → 透明。白色圆环从宽度 12% 排列到精确 46%；半径位置使用 `i / 98`，渐淡使用 `i / 99`，使最外圈达到指定半径且仍可见。取消原来逐圈上移。四向几何留白均为画布宽度 4%，计入 1.30 线宽后均为 19.83 个逻辑单位。PNG 从同一象限镜像生成，校验水平和垂直像素对称；预览页主图也独立居中，小尺寸样本移到下方。

`references/AetherLogoCanvas-original.kt` 保留用户原函数与补充 imports，`canvas-original.svg`、`references/logo-120.svg` 和 `references/preview-120.png` 保留上一轮 120 条参考。`references/*99-compact*` 保存初次 99 条微调，`references/*99-expanded-42*` 保存 42% 版本，`references/*99-centered-44*` 保存 44% 版本。当前 `canvas-square.svg` 保留方形画布，`canvas-logo.svg` 另加此前约定的圆角图标外轮廓、透明四角；当前方形与圆角版内部图形相同。

预览页下方另放两个小尺寸样本：一个将 512 基准稿等比缩到 48；另一个在 48 逻辑单位内执行当前 99 条参数。固定 dp 线宽并非随画布等比变化，小尺寸直接绘制会更密集。这里采用密度 1 进行可解释的对照。

这是基于参数的 SVG／浏览器视觉预览，不能作为 Compose／Skia 的逐像素实机证据。用户现已选定本设计，已将定稿母版同步到 `../current/`，替换旧 E1 临时品牌资产；Android 工程内的 Launcher 接入及实际显示仍由 Android 任务独立验证。

使用 Node.js 与 sharp 导出：

```sh
node branding/icons/2026-10-25-canvas-logo/build.mjs
```
