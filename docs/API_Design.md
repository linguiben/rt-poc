# API接口设计文档

## 1. 目标

本文档重新定义 `src/web/v13/index-latency-monitor-v13.html` 的数据接口。

这次设计基于一个更明确的前提：

- 拓扑图中的卡片种类、卡片文案、卡片位置固定
- 绝大多数线路的几何位置固定
- 真正实时变化的只有“状态”，不是“拓扑结构”

因此，接口不再返回整张拓扑图的完整视图快照，而是返回“固定拓扑模板上的动态状态”。

## 2. 动态范围

当前页面里真正会变化的数据只有下面几类：

1. 线路样式是否为 Primary
2. 哪个 `L3` 被选为 Primary
3. `L3 -> BG` 候选线路中，哪些线路当前可见
4. 线路上的 `latency`
5. 卡片上的 `AVG / P95 / MSG / CPU / MEM`
6. 卡片状态 `OK / WARN / CRITICAL`
7. 线路状态 `OK / WARN / CRITICAL`
8. Source 卡片内部 feed 的 `latency / status / isPrimary`

不再视为动态数据的内容：

- 节点坐标
- 线路几何路径
- 节点标题与展示名称
- 节点和线路的分组关系
- 单站视图中 `L2-B1 ~ L2-B4` 的排列方式
- 单站视图中 3 张 `BG` 卡的排列方式

## 3. 设计原则

### 3.1 前端持有静态拓扑模板

前端应 hardcode 一份稳定的拓扑模板：

- `ALL-SITES TOPOLOGY` 的固定节点和固定候选线路
- `SINGLE-SITE TOPOLOGY` 的固定节点和固定候选线路
- 每个节点/线路对应的坐标、曲线规则、标签摆放规则

后端只返回模板上各元素的“当前状态”。

### 3.2 API 返回 state，不返回 geometry

后端接口默认不再返回：

- `x / y`
- `from / to` 的重复拓扑关系说明
- 曲线路径控制点
- 视图级布局信息

这些内容属于前端静态模板的一部分。

### 3.3 `kind` 与 `isPrimary` 分离

- `kind` 是模板属性，属于静态拓扑定义
- `isPrimary` 是运行时状态，属于 API 返回值

示例：

- `NX1-L3__NX9-BG` 的 `kind = "primary"` 可以 hardcode 在模板中
- 但它当前是否需要 pulse，必须看 `isPrimary`

### 3.4 `primaryTower` 与 `primaryL3NodeId` 分离

- `primaryTower` 表示当前主站点
- `primaryL3NodeId` 表示当前唯一的主 `L3`

两者可以相同，也可以不同。

### 3.5 状态由后端直接裁决

前端只负责渲染，不再根据 threshold 二次推导业务状态。

后端直接返回：

- `node.status`
- `link.status`
- `sourceFeed.status`

前端只根据状态做颜色和闪烁。

## 4. 接口列表

### 4.1 获取当前状态快照

`GET /api/latency/snapshot?scope=ALL|NX1|NX9|NX2`

参数说明：

- `scope = ALL`：返回 `ALL-SITES TOPOLOGY` 所需状态
- `scope = NX1 | NX9 | NX2`：返回对应 `SINGLE-SITE TOPOLOGY` 所需状态

说明：

- 保留 `snapshot` 这个路径，兼容现有命名习惯
- 但语义上，它返回的是“静态模板对应的状态快照”

### 4.2 订阅状态流

`GET /api/latency/stream?scope=ALL|NX1|NX9|NX2`

说明：

- 使用 SSE
- 默认事件名推荐使用 `snapshot`
- 推送结构与 `/api/latency/snapshot` 一致

## 5. 顶层响应结构

```json
{
  "scope": "ALL",
  "topologyTemplateVersion": "v13-static-1",
  "generatedAt": "2026-03-18T10:00:00+08:00",
  "refreshIntervalMs": 3000,
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "nodeStatesById": {},
  "linkStatesById": {},
  "sourceFeedStatesById": {}
}
```

字段说明：

- `scope`: 当前视图范围
- `topologyTemplateVersion`: 当前状态对应哪一版前端模板
- `generatedAt`: 快照生成时间
- `refreshIntervalMs`: 建议轮询间隔
- `primaryTower`: 当前主站点
- `primaryL3NodeId`: 当前唯一主 `L3`
- `nodeStatesById`: 节点状态字典
- `linkStatesById`: 线路状态字典
- `sourceFeedStatesById`: source feed 状态字典

说明：

- 这里用字典而不是数组，是因为前端模板已经持有完整节点/线路定义
- 前端只需要按 ID 合并状态，字典更高效，也更适合后续增量更新

## 6. 节点状态结构

### 6.1 NodeState

```json
{
  "status": "ok",
  "isPrimary": false,
  "metrics": {
    "avgMs": 74,
    "p95Ms": 80,
    "msgCount": null,
    "cpuPct": 82,
    "memPct": 87,
    "ratePerSec": null
  }
}
```

字段说明：

- `status`
  - `ok`
  - `warn`
  - `critical`
  - `unknown`
- `isPrimary`
  - 是否当前被选为 Primary 卡片
- `metrics.avgMs`
  - stage 卡片显示的 `AVG`
- `metrics.p95Ms`
  - stage 卡片显示的 `P95`
- `metrics.msgCount`
  - BG 卡片显示的 `MSG`
- `metrics.cpuPct`
  - 卡片资源条中的 `CPU`
- `metrics.memPct`
  - 卡片资源条中的 `MEM`
- `metrics.ratePerSec`
  - source 卡片的 `rate`

### 6.2 节点 ID 约定

节点 ID 必须稳定，前端模板按固定 ID 找状态。

示例：

- `NX1-RT`
- `NX9-L1`
- `NX2-L3`
- `NX1-BG`
- `NX9-HCS`
- `NX9-L2-B1`
- `NX9-L2-B2`
- `NX9-L2-B3`
- `NX9-L2-B4`

### 6.3 L3 主节点约束

后端必须保证：

1. 所有 `L3` 节点中，任意时刻只有一个 `isPrimary = true`
2. 该节点 ID 必须等于 `primaryL3NodeId`

## 7. 线路状态结构

### 7.1 LinkState

```json
{
  "visible": true,
  "status": "ok",
  "isPrimary": true,
  "latencyMs": 92
}
```

字段说明：

- `visible`
  - 当前线路是否显示
  - 主要用于 `L3 -> BG` 这种候选线路
- `status`
  - `ok | warn | critical | unknown`
- `isPrimary`
  - 当前是否按主线路渲染
  - 前端 pulse 是否出现，应以这个字段为依据
- `latencyMs`
  - 标签显示值，例如 `92ms`

### 7.2 线路 ID 约定

线路 ID 必须稳定，建议继续沿用：

`from__to`

示例：

- `NX1-RT__NX1-L1`
- `NX9-L1__NX9-L2-B1`
- `NX9-L2-B1__NX9-L3`
- `NX1-L3__NX9-BG`
- `NX2-BG__NX9-HCS`

### 7.3 `L3 -> BG` 可见性规则

`L3 -> BG` 不再由前端临时拼装，而是作为“候选线路模板 + API visible 状态”来控制。

后端必须保证：

1. 当前 `primaryL3NodeId` 所属 `L3` 的 `L3 -> BG` 候选线路 `visible = true`
2. 非主 `L3` 的所有 `L3 -> BG` 候选线路 `visible = false`
3. `visible = false` 的候选线路，前端不渲染 path，不渲染 latency label

这条规则同时适用于：

- `ALL-SITES TOPOLOGY`
- `SINGLE-SITE TOPOLOGY`

### 7.4 `isPrimary` 与 `visible` 的职责

- `visible` 决定这条线是否存在于当前画面
- `isPrimary` 决定这条线是否按主线路展示，例如是否显示 pulse

两者必须分离。

## 8. Source Feed 状态结构

### 8.1 SourceFeedState

```json
{
  "status": "ok",
  "isPrimary": true,
  "latencyMs": 31
}
```

字段说明：

- `status`: 当前状态
- `isPrimary`: 当前是否按主 feed 样式显示
- `latencyMs`: 当前 feed latency

### 8.2 Source Feed ID 约定

建议格式：

`sourceNodeId__partition`

示例：

- `NX9-RT__SS`
- `NX9-RT__SZ`
- `NX1-OMDC__HK`
- `NX2-CIIS__BJ`

## 9. 响应示例

### 9.1 ALL 视图示例

```json
{
  "scope": "ALL",
  "topologyTemplateVersion": "v13-static-1",
  "generatedAt": "2026-03-18T10:00:00+08:00",
  "refreshIntervalMs": 3000,
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "nodeStatesById": {
    "NX1-L3": {
      "status": "warn",
      "isPrimary": true,
      "metrics": {
        "avgMs": 61,
        "p95Ms": 148,
        "msgCount": null,
        "cpuPct": 70,
        "memPct": 76,
        "ratePerSec": null
      }
    },
    "NX9-L3": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": 74,
        "p95Ms": 80,
        "msgCount": null,
        "cpuPct": 82,
        "memPct": 87,
        "ratePerSec": null
      }
    },
    "NX9-BG": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": 22,
        "p95Ms": 38,
        "msgCount": 241680,
        "cpuPct": 31,
        "memPct": 39,
        "ratePerSec": null
      }
    },
    "NX9-RT": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": null,
        "p95Ms": null,
        "msgCount": null,
        "cpuPct": null,
        "memPct": null,
        "ratePerSec": 940
      }
    }
  },
  "linkStatesById": {
    "NX1-L2__NX9-L3": {
      "visible": true,
      "status": "ok",
      "isPrimary": false,
      "latencyMs": 68
    },
    "NX1-L3__NX1-BG": {
      "visible": true,
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 92
    },
    "NX1-L3__NX9-BG": {
      "visible": true,
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 92
    },
    "NX1-L3__NX2-BG": {
      "visible": true,
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 92
    },
    "NX9-L3__NX1-BG": {
      "visible": false,
      "status": "unknown",
      "isPrimary": false,
      "latencyMs": null
    }
  },
  "sourceFeedStatesById": {
    "NX9-RT__SS": {
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 31
    },
    "NX9-RT__SZ": {
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 34
    }
  }
}
```

### 9.2 SINGLE-SITE 视图示例

`scope = NX9`

```json
{
  "scope": "NX9",
  "topologyTemplateVersion": "v13-static-1",
  "generatedAt": "2026-03-18T10:00:00+08:00",
  "refreshIntervalMs": 3000,
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "nodeStatesById": {
    "NX9-L1": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": 31,
        "p95Ms": 45,
        "msgCount": null,
        "cpuPct": 40,
        "memPct": 46,
        "ratePerSec": null
      }
    },
    "NX9-L2-B1": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": 33,
        "p95Ms": 52,
        "msgCount": null,
        "cpuPct": 41,
        "memPct": 50,
        "ratePerSec": null
      }
    },
    "NX9-L2-B2": {
      "status": "ok",
      "isPrimary": false,
      "metrics": {
        "avgMs": 35,
        "p95Ms": 54,
        "msgCount": null,
        "cpuPct": 44,
        "memPct": 52,
        "ratePerSec": null
      }
    },
    "NX1-BG": {
      "status": "critical",
      "isPrimary": false,
      "metrics": {
        "avgMs": 18,
        "p95Ms": 335,
        "msgCount": 182340,
        "cpuPct": 24,
        "memPct": 31,
        "ratePerSec": null
      }
    }
  },
  "linkStatesById": {
    "NX9-L1__NX9-L2-B1": {
      "visible": true,
      "status": "ok",
      "isPrimary": false,
      "latencyMs": 34
    },
    "NX9-L2-B1__NX9-L3": {
      "visible": true,
      "status": "ok",
      "isPrimary": false,
      "latencyMs": 54
    },
    "NX9-L3__NX1-BG": {
      "visible": false,
      "status": "unknown",
      "isPrimary": false,
      "latencyMs": null
    },
    "NX1-BG__NX9-L3": {
      "visible": true,
      "status": "ok",
      "isPrimary": false,
      "latencyMs": 56
    },
    "NX1-BG__NX9-HCS": {
      "visible": true,
      "status": "ok",
      "isPrimary": false,
      "latencyMs": 24
    }
  },
  "sourceFeedStatesById": {
    "NX9-RT__SS": {
      "status": "ok",
      "isPrimary": true,
      "latencyMs": 31
    }
  }
}
```

## 10. 前端静态模板建议

### 10.1 应 hardcode 的内容

`src/web/v13/index-latency-monitor-v13.html` 里，下列内容应继续保留在前端模板中：

- 站点集合和顺序
  - `NX1 / NX9 / NX2`
- 卡片标题和展示名称
  - `RT / BB / OMDC / CIIS / L1 / L2 / L3 / BG / HCS`
  - `L2-B1 ~ L2-B4`
  - `BG (NX1) / BG (NX9) / BG (NX2)`
- 所有节点的坐标
- 所有候选线路的起点终点定义
- 所有线路的几何路径规则
- 所有 latency label 的摆放规则
- Source feed 的分区定义
  - `SS / SZ / HK / BJ`
- 单站与全站的固定布局切换逻辑

### 10.2 应改成 API 驱动的内容

下面这些内容不应继续 hardcode 成“当前值”：

- `AVG / P95 / MSG / CPU / MEM / rate`
- 节点 `status`
- 线路 `latency`
- 线路 `status`
- 节点和线路的 `isPrimary`
- `L3 -> BG` 的显示与隐藏
- Source feed 的 `latency / status / isPrimary`

## 11. 对 `src/web/v13/index-latency-monitor-v13.html` 的代码分析

### 11.1 可以继续 hardcode 的代码

这些代码本质上是静态模板，应该保留：

- `const towers`
- `const siteOrder`
- `const levelY`
- `const sourceOffsets`
- `const sourceNames`
- `const sourcePartitions`
- `const singleSiteDefaultGeometry`
- `const singleSiteWideGeometry`
- `function controlPointsForLink(...)`
- `function singleSiteControlPointsForLink(...)`
- `function labelT(...)`
- `function singleSiteLabelPoint(...)`
- `function topologySvgMarkup(...)`
- `function singleSiteShellMarkup(...)`

说明：

- 这些代码定义的是“拓扑长什么样”
- 不是“拓扑当前是什么状态”

### 11.2 应该重构掉的动态 mock

下面这些变量/函数现在扮演的是“伪后端”，应尽量下沉到 API：

- `const sourceFeedModes`
- `const routeVisualModes`
- `const sourceFeedBaseLatency`
- `const sourceFeedSiteBias`
- `const sourceFeedCurrentLift`
- `const sourceFeedLatencies`
- `const stageSeed`
- `const singleSiteBgLinkDefaults`
- `function nodeStatus(...)`
- `function statusClassForLink(...)`
- `function evolveNodes()`
- `function evolveLinks()`
- `function evolveSourceFeeds()`
- `function jitter(...)`

原因：

- 它们都在本地模拟“实时状态”
- 这些状态未来应来自 API，而不是前端种子值和随机抖动

### 11.3 需要拆开的混合逻辑

当前有几段代码同时做了“模板定义”和“状态派生”，这会让后续接 API 比较痛。

建议重点拆开：

#### A. `buildSingleSiteTopologyState(site)`

当前问题：

- 既负责布局节点
- 又负责从现有全局 link 拼出单站 link
- 还负责给 `L2-B1 ~ L2-B4` 和 BG 相关线路塞默认 latency

建议改为两层：

1. `buildSingleSiteTemplate(site)`
   - 只返回静态节点/候选线路
2. `mergeSingleSiteState(template, apiState)`
   - 只合并 `status / isPrimary / latency / visible / metrics`

#### B. `l2ClusterServers(node)`

当前问题：

- 用 `avgDelta / p95Delta / cpuDelta / memDelta` 从父 `L2` 推导出 `L2-B1 ~ L2-B4`
- 这不符合真实监控口径

建议：

- 保留 `L2-B1 ~ L2-B4` 的名字和几何位置
- 删除基于父 `L2` 的数值推导
- 改为直接读取 API 中 `NX?-L2-B1 ~ B4` 的独立状态

#### C. `singleSiteRouteVisualMode(link, site)` 与 `routeVisualMode(link)`

当前问题：

- 现在还在本地用 `routeVisualModes` 和特判决定 primary

建议：

- 改成从 `linkStatesById[link.id].isPrimary` 读取
- 不再在前端硬编码哪条线是 primary

#### D. `buildSourceFeedSegments(node, layout)`

当前问题：

- feed 的几何是静态的，这部分应该保留
- 但 latency / severity / mode 是本地计算的，这部分应外移

建议：

- 保留 feed path 计算
- 改为从 `sourceFeedStatesById` 读取 `latencyMs / status / isPrimary`

### 11.4 可以继续保留为前端派生的小逻辑

以下内容仍然适合保留在前端：

- `pulse` 按钮只控制“是否显示 pulse”
- `flow` 按钮只控制“是否播放流动动画”
- 黄色/红色告警闪烁样式规则
- `MSG:` 的格式化显示
- latency 文案 `${latencyMs}ms`
- `toLocaleString()` 之类的展示格式处理

这些属于纯 UI 行为，不属于后端业务状态。

### 11.5 `heroPrimary` 和 summary 的建议

当前 `renderSummary()` 里仍有一部分写死值：

- `heroPrimary` 直接写死为 `NX9`
- `summaryCardDefaults` 也是静态默认值

建议：

- `heroPrimary` 改为读取 API `primaryTower`
- summary 卡片总数如果是固定拓扑容量，可以继续 hardcode
- summary 卡片中的健康数如果未来要实时变化，再单独补 summary API，不建议把这部分混进 topology state

## 12. 推荐重构后的前端分层

建议把 `v13` 里的数据组织改成 4 层：

### 12.1 静态模板层

例如：

- `ALL_SITES_TEMPLATE`
- `SINGLE_SITE_TEMPLATE_BY_SCOPE`

负责：

- 节点定义
- 候选线路定义
- 坐标
- 路径规则
- 标签规则

### 12.2 API 状态层

负责接收：

- `nodeStatesById`
- `linkStatesById`
- `sourceFeedStatesById`
- `primaryTower`
- `primaryL3NodeId`

### 12.3 合并层

例如：

- `mergeTopologyTemplateWithState(scope, template, apiState)`

负责：

- 把模板节点与动态状态合并成最终 view model
- 过滤 `visible = false` 的候选线路

### 12.4 渲染层

保留现有：

- `renderAllSitesTopology()`
- `renderSingleSiteTopology()`

但它们应只消费合并后的 view model，不再自己生成 mock 状态。

## 13. 最小改造顺序

建议按下面顺序推进，风险最低：

1. 先冻结拓扑模板，把所有坐标和候选线路从动态数据里剥离出来
2. 接入新的 `/api/latency/snapshot`
3. 用 `nodeStatesById / linkStatesById / sourceFeedStatesById` 替换页面里的当前 mock 状态源
4. 去掉 `routeVisualModes / sourceFeedModes / stageSeed / singleSiteBgLinkDefaults` 中的运行时状态职责
5. 去掉 `evolveNodes / evolveLinks / evolveSourceFeeds`
6. 最后再考虑是否需要 SSE 增量更新

## 14. 结论

这次接口设计的核心不是“把更多字段塞给前端”，而是反过来收缩责任边界：

- 前端只负责固定拓扑模板和渲染
- 后端只负责动态状态

这样可以同时解决三个问题：

- 接口更轻
- 前端代码更稳定
- `v13` 当前大量 mock/派生逻辑可以被清理掉
