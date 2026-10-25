# 漫频 Aether · 当前正式 Logo／图标

2026-10-25，用户明确选择 **99 环 Canvas Logo** 为新的 Logo 与应用图标。采用 46% 外半径、99 条同心白环、512 基准线宽 1.30、最高透明度 0.48、20% 暖色光核；中心暖白微提亮至 `#FFF5EE`，金黄 `#F3C785`、淡绿 `#91B879`、深底 `#0B0D12` 保持。四向留白相等。

![当前正式图标](preview-512.png)

## 母版与来源

- [app-icon.svg](app-icon.svg)、[app-icon.png](app-icon.png)：圆角母版；PNG 为 3072×3072 RGBA，四角透明。
- [app-icon-square.svg](app-icon-square.svg)、[app-icon-square.png](app-icon-square.png)：同一图形的无遮罩方形版本，供 Android 系统遮罩接入。
- [selection.json](selection.json) 保存用户选择、精确参数与每份母版 SHA-256；母版须与[已选定快照](../2026-10-25-canvas-logo/approved-99/README.md)字节一致。
- 来源为用户提供 Canvas 构图及后续精修，原生数学圆环与渐变；本定稿没有 imagegen。Kotlin 文件是 512 逻辑基准的参数参考，不能把固定 dp 线宽直接移到小尺寸后宣称视觉一致。

旧 E1 临时图标及原字节已[归档](../archive/2026-10-25-provisional-E1/README.md)，不再作为当前图标。后续提出的 33 环和 60 环为独立小屏候选，目前没有覆盖已选定的 99 环母版。见[小屏对照](../2026-10-25-canvas-logo/ring-comparison.html)。

## Android 资产

`android-legacy/mipmap-mdpi` 至 `mipmap-xxxhdpi` 包含 48／72／96／144／192px `ic_launcher.png`。`preview-512.png` 是圆角预览，`preview-square-512.png` 是方形预览。派生图从冻结母版等比缩小，没有改变颜色、环数或构图。

Android 任务负责替换旧 E1 资源及 adaptive foreground，选用合适的方形输入并检查系统遮罩，避免二次圆角、偏心或裁掉外环。当前品牌资产已更新；不据此宣称 APK／Launcher 已接入完成，也不涉及商店发布。

运行 `python3 branding/icons/current/prepare.py` 可在校验批准母版哈希后重建派生尺寸与清单。冻结的两页视觉稿保持原样。
