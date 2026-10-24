# Aether 应用图标 · 第一轮探索

当前状态：草案，尚未批准，暂不替换 Android launcher 图标。页面设计基线继续保持 [d7d606e](https://github.com/dpan538/Aether/commit/d7d606ec5d4600b49185d49ba395555065537931)。

从既有沉浸页的月相 CD 和现代页的仪器丝印提炼图形：倾斜的新月、中心孔和短频率回声弧，收敛为一个小尺寸可识别的圆盘标志。使用炭黑、暖银、冰蓝，不添加文字、绿色塑料或亮面金属。

采用内置 imagegen 生成概念；[完整提示词](prompts.md)随稿保存。当前图为视觉提案，尚非分层 Android adaptive icon 或正式商店资产；批准方向后再制作可控的矢量轮廓、前景／背景分层与单色版本，并检查不同 mask 和实际小尺寸。

Android 实现任务已在独立窗口开始，配置为 GPT‑6.1 Sol / Extra High；该任务使用冻结页面进行实际截图对照，图标由本窗口独立探索。

## 文件与检查

- [02-lunar-frequency-matte.png](02-lunar-frequency-matte.png)：当前建议稿，1254×1254；月面纹理已减去，暖银至冰蓝月牙与回声弧保留。
- [01-lunar-frequency-texture-study.png](01-lunar-frequency-texture-study.png)：第一轮材质研究，1254×1254；不作为当前建议方向。
- [preview.html](preview.html)：48／64／96像素、圆形／圆角方形及深浅底浏览器检查；只做显示缩放／CSS裁切，不改生成图的原像素。

以 `python3 -m http.server 4178 --bind 127.0.0.1` 在本目录运行，打开 `/preview.html` 查看。两张生成原稿均原样保存，未自动重采样或当作已经适配所有Android密度的资产。完整来源、尺寸及SHA-256在 `manifest.json`。

Android派发任务：`01a16d9c-fbea-7920-bd6b-f78caf804e87`，模型 `gpt-6.1-sol`、推理 `xhigh`；已确认任务处于执行中，开始读取冻结稿与调查工具链。此记录不宣称Android实现完成。
