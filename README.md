# 漫频 Aether

**不设边界，漫游频率。**

*No boundaries. Just frequencies.*

In the unseen Aether, hear the distant.

（在无形的风波里，听见远方。）

**漫频 Aether** 是一款专注于声音纯粹性与视觉美学的电台套件。我们拒绝信息流干预、拒绝算法推荐，将调频的控制权完全交还给双手。

在这里，无线电波如同充斥宇宙的无形介质，无形，无界，却无处不在。从东京深夜的爵士窄频，到柏林地下的电子脉冲；从巴黎街头的午后谈资，再到悉尼海岸的浪潮与广播。只需轻拨旋钮，即可在星罗棋布的全球频率中自由漫游。

## 项目阶段

**2026-10-24：正式进入产品规划与设计阶段。**

当前仓库保存品牌文案、功能需求、体验规划与决策记录。功能清单表达产品目标；目前尚无可运行应用、视觉稿、播放实测或发布版本。

先确定功能与信息含义，再讨论交互和 UI。首个产品聚焦网络电台；本地音乐播放和外部音乐 App 的美化套件属于后续独立产品。

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
└── docs/           # 产品与体验规划、决策记录
```

技术路线候选为 Android、Kotlin、Jetpack Compose 与 Media3，电台目录候选为 Radio Browser。技术选型不代表工程已经创建，亦不预先确定页面数量、布局或视觉风格。

代码与品牌资产的许可方式待确定。

## English

**Aether · No boundaries. Just frequencies.**

A radio app shaped around pure listening, visual craft and personal control. Discover stations through direct search, countries and regions, languages, genres, favorites and deliberate random tuning, without an algorithmic recommendation feed.

The project entered product planning and design on **24 October 2026**. This repository currently contains brand copy, requirements and experience proposals. It does not yet contain a playable app or a release.
