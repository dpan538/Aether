# Aether · 用户 Canvas Logo 试稿

2026-10-25，按用户给出的 `AetherLogoCanvas` 绘制效果稿。`AetherLogoCanvas.kt` 保存原函数，仅补充 imports；未接入 Android 工程，也没有 imagegen。

[整体预览](index.html) · [原代码比例 SVG](canvas-original.svg) · [圆角 SVG](canvas-logo.svg) · [3072px PNG](canvas-logo-3072.png)

主图以 512 逻辑单位、密度 1 为基准重建代码：背景 `#0B0D12`，中央渐变半径为宽度的 20%，淡粉 `#FDE8E0` → 金黄 `#F3C785` → 淡绿 `#91B879` → 透明。随后按代码顺序绘制 120 条白色圆环，半径从宽度的 12% 到约 39.77%，中心最多上移约 9.92 个单位，笔宽 1，透明度从 0.4 递减。没有新增双曲线变形；代码的 `addOval(Rect(center, radius))` 实际指定的是圆。

`canvas-original.svg` 保留方形画布。`canvas-logo.svg` 仅额外添加此前约定的圆角图标外轮廓，透明四角；圆角不在用户本次函数中，明确作为展示包装。两者内部图形相同。

预览页另放两个小尺寸样本：一个将 512 基准稿等比缩到 48；另一个在 48 逻辑单位内重新执行原参数。原函数的 `1.dp` 线宽与 `10 * progress` 像素位移并非随画布等比变化，小尺寸直接绘制会更密集且偏上。这里采用密度 1 进行可解释的对照；不同 Android 密度下，固定像素位移与 dp 线宽的比例还会改变。

这是基于参数的 SVG／浏览器视觉预览，不能作为 Compose／Skia 的逐像素实机证据。当前任务没有替换 E1 临时图标。若确认这个方向，再由 Android 实现任务接入并核对实际渲染。

使用 Node.js 与 sharp 导出：

```sh
node branding/icons/2026-10-25-canvas-logo/build.mjs
```
