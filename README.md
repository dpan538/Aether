# 漫频 Aether

**不设边界，漫游频率。**

*No boundaries. Just frequencies.*

In the unseen Aether, hear the distant.

（在无形的风波里，听见远方。）

**漫频 Aether** 是一款专注于声音纯粹性与视觉美学的电台套件。我们拒绝信息流干预、拒绝算法推荐，将调频的控制权完全交还给双手。

在这里，无线电波如同充斥宇宙的无形介质，无形，无界，却无处不在。从东京深夜的爵士窄频，到柏林地下的电子脉冲；从巴黎街头的午后谈资，再到悉尼海岸的浪潮与广播。只需轻拨旋钮，即可在星罗棋布的全球频率中自由漫游。

## 项目阶段

**2026-10-24：正式进入产品规划与设计阶段。**

当前仓库保存品牌文案、功能需求、三页视觉方向、参考原图、页面元素草案与决策记录。功能清单表达产品目标；目前尚无可运行应用、原创 UI 稿、播放实测或发布版本。

**Aether 是以视觉驱动、视觉体验特化的项目。** 功能围绕选台与收听，以及三页各自的视觉体验取舍。首个产品聚焦网络电台；本地音乐播放和外部音乐 App 的美化套件属于后续独立产品。

## 三个页面与三套 UI

| 页面 | 已确定的方向 | 角色 |
| --- | --- | --- |
| 第一页：纯沉浸播放 | 图一／图二的虹彩同心圆、深色背景与印刷质感 | 以观赏和聆听为主 |
| 第二页：复古调节 | 图三的成组音响设备、多按键与旋钮层次 | 提供复古操作体验 |
| 第三页：现代主页面 | 下部参考图四，上方增加与其蓝色显示屏同款的可筛选电台屏幕 | 主要入口，清晰选台，并承载设置 |

三页方向与现代页的主页面身份已确定，具体控件映射和页面切换方式仍在讨论。四张用户参考原图已[归档](design/references/README.md)；当前继续[页面元素规划](docs/PAGE-ELEMENTS.md)。

## 产品方向

- **中立索引**：应用不投放广告，不做人气榜、付费置顶或算法推荐。电台原始节目内容由电台提供。
- **直达搜索**：以电台名称、城市、呼号找到想听的频道。
- **自主探索**：按国家／地区、类型、语言浏览，收藏常听的台，主动触发随机调频。
- **看见声音的来处**：呈现电台身份、真实播放状态，以及数据允许时的节目名、音频规格和当地时间。
- **尊重真实信息**：缺失数据自然降级，直播属性、连接质量和音频电平分别表达。

上述能力仍在规划；具体数据依赖、候选功能和待定事项见下列文档。

## 文档

| 文档 | 内容 |
| --- | --- |
| [品牌文案](branding/README.md) | 已确定的中英文名称、标语与完整介绍 |
| [视觉方向](design/README.md) | 视觉体验特化原则、三个页面与四张参考图的对应关系 |
| [页面元素](docs/PAGE-ELEMENTS.md) | 各页必须表达的内容、控件候选和待定项 |
| [产品范围](docs/PRODUCT-SCOPE.md) | 用户已提出的功能、条件信息、候选能力与范围边界 |
| [体验规划](docs/EXPERIENCE-PLAN.md) | 搜索、浏览、收听、收藏与随机探索的行为草案 |
| [决策记录](docs/DECISIONS.md) | 已确定事项、待讨论问题与阶段推进顺序 |
| [项目协作规则](AGENTS.md) | 后续设计、实现与记录的工作约束 |

## 仓库结构

```text
Aether/
├── README.md
├── AGENTS.md
├── branding/       # 已确定的品牌内容
├── design/         # 三页视觉方向与参考原图
└── docs/           # 产品与体验规划、决策记录
```

技术路线候选为 Android、Kotlin、Jetpack Compose 与 Media3，电台目录候选为 Radio Browser。技术选型不代表工程已经创建；它服务于已确定的三页视觉方向，具体实现方式尚未决定。

代码与品牌资产的许可方式待确定。

## English

**Aether · No boundaries. Just frequencies.**

A radio app shaped around pure listening, visual craft and personal control. Discover stations through direct search, countries and regions, languages, genres, favorites and deliberate random tuning, without an algorithmic recommendation feed.

The project entered product planning and design on **24 October 2026**. Aether specializes in visual listening experiences through three distinct interfaces: immersive playback, retro controls, and a modern main page for station selection and settings. Four user-provided references are archived alongside the page-element proposals. There is no playable app or release yet.
