# Aether 两页冻结快照 · 2026-10-24

状态：用户决定现代／沉浸两页已足够，要求记录并同步仓库，随后开始 Kotlin Android 构建。此包是设计基线，不是 Android 运行证据或应用发行。

## 内容

- `source/`：两页当前完整 HTML/CSS/SVG/JS、共享示例会话、预览宿主、Node 预览服务及 package.json，保持相对目录可独立运行。
- `renders/`：当前现代 v10 的默认墨水、点亮、设置、语言菜单、流派菜单、静音、关机七张全页图；已接受的沉浸页全图；现代控件与引导线两张局部。
- `evidence/`：当前浏览器几何与材质检查、四条长引导线的路径及无交叉采样记录；仅浏览器证据。
- `manifest.json`：每个包内文件的字节数、SHA-256；PNG 记录像素尺寸。源与当前活源码在冻结点逐字节一致。

原始艺术参考另存于 `../../references/`，早期沉浸单页接受记录保留于 `../2026-10-24-immersive-approved/`；两者不改原始字节。包内不混入用户私人桌面截图。

## 运行冻结源码

在本目录的 `source/` 下运行：

```sh
AETHER_PREVIEW_PORT=4177 npm run dev
```

打开 `http://127.0.0.1:4177/preview.html?page=modern` 或 `?page=immersive`。无依赖安装需求，Node.js 22+。端口 4177 用于避开活动设计预览的 4176。

在仓库根执行 `node scripts/verify-snapshot.mjs` 校验包内所有文件及当前活源码的一致性。后续若活源码主动改动，只校验历史包可用 `--archive-only`；不要重写清单来掩盖意外变更。

## 高清图及复现状态

| 文件 | 状态与操作 |
| --- | --- |
| [modern-ink.png](renders/modern-ink.png) | 新标签初始态，电源开／前光关，FM 802，音量内部66→20/30，Tempo100%，四收藏，动态关 |
| [modern-lit.png](renders/modern-lit.png) | 初始态点击左上灯光键，等渐变稳定；其余相同 |
| [modern-settings.png](renders/modern-settings.png) | 点亮后点击左上设置；Language／Check updates／About 三项 |
| [modern-language-menu.png](renders/modern-language-menu.png) | 点亮设置态展开 Language，自绘 English／中文选项 |
| [modern-genre-menu.png](renders/modern-genre-menu.png) | 点亮目录态展开 GENRE，五项自绘菜单，ALL GENRES 选中 |
| [modern-mute.png](renders/modern-mute.png) | 点亮目录态点击扬声器，音量0／30、扬声器叉号、琥珀指示 |
| [modern-off.png](renders/modern-off.png) | 静音恢复20／30后关机；待刷新结束，内容保留、前光0、LED灭 |
| [immersive.png](renders/immersive.png) | 已接受的沉浸稿，FM802、MOCK、主盘静态，未改艺术构图 |
| [modern-controls.png](renders/modern-controls.png) | v10 最新普通哑光灰拉丝操作区，无反光条带 |
| [modern-guides.png](renders/modern-guides.png) | v10 四条同模板长丝印线，三个等半径小圆角、等距分道 |

八张全页均为 **3408×2272**。现代七态在同一 v10 代码上重新操作截取，不沿用 v9 周边材质。浏览器视口请求2726×1818，经该会话实际缩放得到3408×2273采样，去除边界一行并转换PNG；未放大图像。参考预览逻辑框为1704×1136。浏览器抗锯齿／字体与Android可能不同，应实测、迭代并记录残余差异。

## 验证范围

源码语法、原图／历史快照哈希、包内哈希、文件链接、当前几何与材质、实际浏览器菜单／静音／关机状态。关机观察 powered=false、refreshing=false、frontlight=0；未接音频。所有示例节目、曲目、码率、时钟及仪表为MOCK，不是实际播放或测量。

Android 必须按 [交接书](../../../docs/ANDROID-HANDOFF.md)提供实际截图、叠加／差异图、手势与动效验证。不能把浏览器快照当原生应用测试；不能修改此基线来回避还原差异。
