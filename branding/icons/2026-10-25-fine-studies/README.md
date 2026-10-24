# Aether · 四组图形，十二张对照

2026-10-25。用户从六个研究方向中选择 **J1、J3、J5、J6**，要求每组一张黑白与两张限色版，共 **4 × 3 = 12 张**。以参考图形的视觉复刻、细线和明暗精度为本轮重点。此前完整虹彩配色撤下；本轮颜色只取自新提供的 CD 局部。

打开 [index.html](index.html) 浏览四组并放大 SVG，或查看[总览](overview.png)、[上半页](overview-01.png)、[下半页](overview-02.png)。每张均提供 3072×3072 PNG、矢量 SVG 与独立局部放大图。

| 组别 | 黑白 | 绿／金 | 珊瑚／金 | 参考核对 |
| --- | --- | --- | --- | --- |
| J1 负空间回声 | [PNG](exports/J1-negative-echo-mono.png) | [PNG](exports/J1-negative-echo-green-gold.png) | [PNG](exports/J1-negative-echo-coral-gold.png) | [对照](reference-checks/J1.png) |
| J3 轴向折光 | [PNG](exports/J3-axial-caustic-mono.png) | [PNG](exports/J3-axial-caustic-green-gold.png) | [PNG](exports/J3-axial-caustic-coral-gold.png) | [对照](reference-checks/J3.png) |
| J5 相位折叠 | [PNG](exports/J5-phase-fold-mono.png) | [PNG](exports/J5-phase-fold-green-gold.png) | [PNG](exports/J5-phase-fold-coral-gold.png) | [对照](reference-checks/J5.png) |
| J6 放射相位 | [PNG](exports/J6-radial-phase-mono.png) | [PNG](exports/J6-radial-phase-green-gold.png) | [PNG](exports/J6-radial-phase-coral-gold.png) | [对照](reference-checks/J6.png) |

## 配色控制

输入：[用户指定 CD 色段原文件](references/cd-palette-crop.png)。从指定坐标的 7×7 像素区域取 RGB 中位数，坐标以原图左上为原点：

| 色锚 | RGB / HEX | 取样中心 |
| --- | --- | --- |
| 绿 | 78, 119, 89 / `#4E7759` | 28, 176 |
| 金 | 213, 187, 103 / `#D5BB67` | 28, 64 |
| 珊瑚 | 194, 99, 100 / `#C26364` | 115, 20 |

绿／金版只使用绿、金及黑白中性色；珊瑚／金版只使用珊瑚、金及黑白中性色。过渡是这些色锚之间的插值。同组的形状与坐标保持一致。J1 只给圆心着色；J6 保留黑色射线，只对球体的原有明暗着色；J3／J5 沿原灰阶映射颜色。

## 结构与细节

- **J1**：按参考轮廓恢复实面、负形、细弧与圆心；彩色圆心引用原黑白路径中的同一闭合轮廓。
- **J3**：沿参考重建光环、弧面、细轴与柔光范围，避免用通用十字星代替原结构；192 级灰阶曲线配合独立的细纹理，清理暗部结块。
- **J5**：沿参考恢复折面与实际遮挡关系，避免简化成围绕一点的扇叶。折面明暗使用 192 级闭合三次曲线，粗噪声与结构分开处理，再以确定的高频灰阶纹理补充细颗粒。
- **J6**：参数化生成 224 条等角、收尖射线；球体明暗与固定种子的细颗粒独立控制。射线数量、规整性与表面灰阶是本轮重建选择，参考原图仍保留供核对。

这些是**参考图形复刻与配色研究**。J1／J3／J5 的复杂轮廓来自对输入图像明暗边界的分析与三次曲线拟合，SVG 母版不嵌入输入位图；这并不把参考作品变成 Aether 原创构图，也不等于逐像素复制整张海报。参考里的作者文字、纸张版式与边框不进入图标图形。

本轮没有调用 imagegen。细纹理以 SVG 原生滤镜逐通道等比调制，不引入新色相。当前暂用的 [E1 图标](../current/README.md)与两页冻结基线保持原样；十二张研究稿未获最终图标选择。

## 文件与重建

- `references/`：四张原始图形参考及一张指定配色截屏，保留输入字节。
- `svg/`、`exports/`、`previews/`、`details/`：分别为 12 张 SVG、12 张 3072px PNG、12 张预览与 12 张局部。
- `reference-checks/`：四张原图形部分归一化对照；不把纸张底色差异当作几何差异。
- `contours.json`、`reconstruct.py`：参考曲线与拟合来源；需要 Pillow、NumPy、SciPy 才能重新提取。常规导出直接使用已保存曲线。
- `geometry.mjs`、`build.mjs`：参数化射线、统一变体与画册导出。运行 `node branding/icons/2026-10-25-fine-studies/build.mjs`，需要 Node.js 22+ 与 `sharp`。
- [manifest.json](manifest.json)：来源、取样、尺寸、文件字节和 SHA-256。[verification.json](verification.json)记录本次实际检查；[scale-check.png](scale-check.png)保留 48／96／192px 的细节损失，不将复杂视觉图直接宣称为已适配的小尺寸 Launcher。
