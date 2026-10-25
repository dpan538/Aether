> Newer delivery: the local-album extension is preview.3 / code4; see [album acceptance](ALBUM-REPORT.md). The repair-cycle evidence below remains the preview.2 baseline.

# preview.2 严重缺陷修复验证

preview.1 的验收已撤回。用户实机截图与原有真实播放截图都能看到文字损坏；此前300轮主要检查状态，漏掉了实际画面的严重问题。[历史报告](HISTORICAL-PREVIEW1.md)与原始数据保留，不能作为当前交付通过依据。

本轮只以小米 Pad 9 Pro Max 的 **3408×2272、横屏3:2** 为适配目标。当前可执行环境是独立 API36 ARM64 模拟器；不冒充小米实机、HyperOS、144Hz、扬声器或振动马达验证。

## 根因与改动

- **文字损坏**：原绘制代码让频谱和文字共用 Paint，依赖 reset 清除文字对齐。原生探针实测 reset 后仍保持 CENTER/RIGHT；按字符逐个绘制时因此产生丢字、错位。现在每个完整字符串使用独立 TextPaint，显式设置对齐、缩放、斜切与字距，并按整段文字塑形；省略及双行使用相同测量方式，兼容中文、组合字符和代理对。
- **四个 Station 键无效**：原先只绑定收藏，首次启动收藏为空时四键全部禁用。现在优先真实收藏，其后接真实目录并按 UUID 去重；四键立即可选，横滑可到后续电台，不替用户创建收藏。锁定/关机时仍禁用。
- **主频谱与沉浸频谱**：真实模式默认启用；Equalizer 控制真实 PCM 监测，波形键独立控制艺术动画。左右声道分别 FFT、按功率合成，避免反相抵消。沉浸页的模拟日期曲线替换为同一 PCM 帧的左右声道频率图。暂停、缓冲、错误、关闭监测分别显示真实状态，缺失资料不编造。
- **页面与点击**：小目标扩展到48dp；普通手指抖动仍算点击，明确滚动/旋转后不误点。设置返回先前目录/收藏页，两页共用当前电台和同一个播放器，三角按实际筛选池切台。
- **资料与生命周期**：节目资料缺失时显示真实台名；音频格式使用真实解码器的 codec、采样率、声道和已知码率；时间明确为 DEVICE TIME，设备时区在独立小字行完整显示；长音频资料使用可见省略号，避免末尾无提示裁切。筛选重置也触发真实目录请求，中断请求在恢复前台后重试。频谱帧合并投递，避免主线程恢复后补播过期帧。

## 已执行的针对性验证

15项单元测试通过，Release lint零错误、13警告、1提示。原生测试验证16位/浮点PCM、左右声道分频、反相信号、静音、空缓冲/EOS、20Hz投递上限及音频字节完全不变。对3408×2272整屏分别注入 CENTER/RIGHT、非默认缩放、斜切与字距，重绘变化像素均为0。

四键真实触摸含4×3物理像素移动：逐一比对键面台名、选中UUID、Media3 mediaId；收藏保持为空。另验证50/150/100%速度与pitch=1、EQ开关、设置中英文、设置返回、两个沉浸三角、单播放器切页、暂停、锁定、关机留墨。主屏和沉浸频谱分别保存不同时间的实际PNG，并检测各自区域的变化像素。

## 循环与交付

最终非debug APK（versionCode3，SHA-256 `12eb8b35a0787b6cd8aef70d1b6de7bb63f4024549c907f2c6f8adf2d3648d11`）完成 **300次使用，300次操作检查通过**。共301次原始尝试：第261次时整个宿主模拟器进程退出，ADB设备消失，宿主日志记录OpenGL上下文错误；此条失败保留，恢复后额外执行301号尝试补测原场景。不能称为连续300轮无中断，也不能据此把宿主退出判为App单独崩溃。原因为未确定，原始记录与恢复过程分别保存。

实际完成31次进程冷启动、269次恢复前台；12类操作各25次，包括100次四键触摸和25轮双页实际频谱像素检查。已导出的Android事件日志中未记录本App的crash/ANR；宿主中断另列，不能把日志缺失处当作无故障证明。冷启动就绪中位数8.155秒、P95 8.743秒；恢复前台中位数263毫秒、P95 1.680秒。冷启动含真实目录准备、资源、刷新和测试等待，不能解释为单次触摸延迟或平板启动性能。PSS中位382.0MiB、最大393.2MiB；这不是无内存泄漏证明。

[逐次JSON](evidence/repair/experience-rounds.json) · [CSV](evidence/repair/experience-rounds.csv) · [统计](evidence/repair/experience-summary.json) · [环境](evidence/repair/experience-environment.json) · [中断](evidence/repair/infrastructure/incident.json)。发行附件提供离线交互报告、六类图表、逐次截图和原生3.4K图片。此前两段12场景预检与候选10轮单列，不计入本次300完成轮次。

最终APK上的[原生PCM复验](evidence/repair/final-processor.json)与[整屏文字复验](evidence/repair/final-render-regression.json)均通过。八个原始冻结全页图与一个第四预设派生参考共九态差分，MAE为4.052–5.737/255；第四预设明确标为从未改动冻结源码复现，不冒充原始冻结图。33文件校验通过，原月面着色器逐字节相同。

## 依赖与交付边界

[最终APK检查](evidence/repair/apk-surface-inspection.json)确认非debug、无私有签名文件、无麦克风权限；请求网络、网络状态、APK来源安装及AndroidX同签名动态接收器权限。[OSV](evidence/repair/osv-dependencies.json)本次查询56个锁定Maven版本、0条匹配公告，不等于没有未知漏洞。242个生产文件与[发行源码提交97e277c](https://github.com/dpan538/Aether/commit/97e277cb7646b4636516db08b688008bed24ef26)逐字节一致；补测标记只修改独立测试包。

[公开2→3更新](evidence/repair/public-update/integrity-and-preservation.json)已在隔离模拟器中完成：旧版从本仓库公开Release下载最终APK一次；同进程复查、进程重启、未授权返回及系统安装取消后复用同一个文件，字节数35,459,020、修改时间和SHA均未改变。重启后的APK下载请求为0。通过Android系统安装页Update按钮覆盖安装后，实际已安装APK SHA为`12eb8b35a0787b6cd8aef70d1b6de7bb63f4024549c907f2c6f8adf2d3648d11`；实际SomaFM电台UUID、收藏、音量17、Tempo90和中文均保留。旧待安装包在成功升级后清理；3版公开检查返回CURRENT、0次下载。升级后再次通过真实播放键播放保留的电台，PCM采样计数143,360，Tempo90、音量17和中文仍保留。

[最终公开包安全验证](evidence/repair/public-update/security.json)接受同签名包，拒绝错签名、错版本、降级、截断大小、错哈希、四种不可信URL及广告哈希匹配但已篡改的包，共11项。旧版测试夹具最初错误等待debug专用自动播放，失败后改成实际点击播放键；夹具失败保留，未计为App操作通过。

## 边界

冻结设计文件保持原始字节，另保留原生参考态截图、叠图与差分；实时资料、首次可用预设及真实频谱的改动有明确功能原因，不能宣称与静态MOCK稿逐像素相同。测试不证明零未知漏洞、全球电台可用性或实机触感。APK沿用永久包名和Aether自己的签名，更新仍由Android系统确认安装。
