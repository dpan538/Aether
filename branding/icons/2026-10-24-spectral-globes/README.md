# Aether · 频谱球体研究

2026-10-24。用户选择两个球体参考，明确授权 imagegen 实现具体构图。本轮通过内置 imagegen 分别编辑生成两张图；每次输入对应构图参考和 Aether 既有 CD 配色参考。

| 方向 | 用户指定构成 | 原图 |
| --- | --- | --- |
| E1 彩球／白色地平 | 中央彩色网格球、底部白色网格球；圆窗内灰色噪声、圆窗外黑色 | [PNG](E1-chromatic-sphere-white-horizon.png) |
| E2 上下反相频谱球 | 黑色背景；上半球彩面白线，下半球黑面彩线；完全平面的几何表现 | [PNG](E2-inverted-spectral-globe.png) |

两张原图均为 1254×1254，完整保留生成输出字节。它们是栅格设计研究，不是可编辑矢量母版，也未选作最终应用图标。

后续用户指出 E2 上下半球的网格不共享同一结构。原 E2 作为风格／错误历史保留；当前结构修正版见 [E2-v2](../2026-10-24-globe-structure/README.md)，上下引用同一套完整球面曲线。

浏览 [对照页](index.html) 查看两张大图和 48／96px／圆形样本。[prompts.md](prompts.md) 保存完整提示词与输入角色。`references/` 保存用户原图及已有 Aether CD 配色参考，其哈希和输出哈希见 [manifest.json](manifest.json)。

本轮按用户的新构图要求推进，延续 Aether 的 CD 频谱与印刷颗粒；此前从月相界面提取的 [D1–D3](../2026-10-24-interface-dna/README.md)保留为上一轮提案，不把月相轮廓强加到本次球体上。两页冻结设计及 Android 任务保持独立。

模式：内置 imagegen 编辑，两次调用；无 CLI／API fallback。生成后仅复制保存原文件，没有像素后处理。
