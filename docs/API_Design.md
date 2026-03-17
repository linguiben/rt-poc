# API接口设计文档

## 1. 目标

本文档定义 `src/web/v13/index-latency-monitor-v13.html` 所需的拓扑图数据接口。

接口需要覆盖以下页面展示数据：

- 线路 latency
- 卡片中的 `AVG / P95 / MSG / CPU / MEM`
- 卡片状态 `OK / WARN / CRITICAL`
- 线路状态 `OK / WARN / CRITICAL`
- 卡片和线路的 `isPrimary`
- Source 卡片内部子线路的 latency / status / isPrimary

本文档只定义接口契约，不涉及前端改造实现。

## 2. 设计原则

### 2.1 以“视图快照”作为接口输出

后端直接返回当前视图所需的节点、线路和 source feed，不要求前端再根据基础拓扑二次派生。

原因：

- `ALL-SITES TOPOLOGY` 与 `SINGLE-SITE TOPOLOGY` 的展示模型不同
- 单站视图中存在 `L2-B1 ~ L2-B4` 和 3 张 `BG` 卡，这些属于视图级节点
- `isPrimary` 属于当前时刻的业务/展示状态，更适合由后端统一计算

### 2.2 `kind` 与 `isPrimary` 必须分离

- `kind` 表示线路业务类型，例如 `intra / primary / backup / feedback`
- `isPrimary` 表示当前是否被选为“主展示路径/主展示卡片”

两者不能混用。

示例：

- 某条线路 `kind = "primary"`，但当前时刻 `isPrimary = false`
- 某条线路 `kind = "feedback"`，理论上也可以 `isPrimary = true`，如果后续产品需要对它做脉冲强调

### 2.3 `primaryTower` 与 `primaryL3NodeId` 必须分离

当前页面已经存在 `primaryTower` 概念，但页面业务还引入了“某一个 L3 被选为 Primary”的概念。

因此：

- `primaryTower` 表示主站点/主塔概念
- `primaryL3NodeId` 表示当前被选中的主 L3 节点

这两个字段可能相同，也可能不同。

## 3. 接口列表

### 3.1 获取当前拓扑快照

`GET /api/latency/snapshot?scope=ALL|NX1|NX9|NX2`

参数说明：

- `scope`
  - `ALL`：返回 `ALL-SITES TOPOLOGY`
  - `NX1` / `NX9` / `NX2`：返回对应 `SINGLE-SITE TOPOLOGY`

### 3.2 订阅拓扑流

`GET /api/latency/stream?scope=ALL|NX1|NX9|NX2`

说明：

- 使用 SSE
- 推送的数据结构与 `/api/latency/snapshot` 完全一致
- 推荐事件名为 `snapshot`

## 4. 顶层响应结构

```json
{
  "scope": "ALL",
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "refreshIntervalMs": 3000,
  "generatedAt": "2026-03-17T14:25:30+08:00",
  "thresholds": {
    "default": { "warn": 100, "critical": 180 },
    "groups": {
      "sources": { "warn": 100, "critical": 180 },
      "l1-l2": { "warn": 100, "critical": 180 },
      "l2-l3": { "warn": 100, "critical": 180 },
      "l3-bg": { "warn": 100, "critical": 180 },
      "bg-l3-feedback": { "warn": 100, "critical": 180 },
      "l3-l1-feedback": { "warn": 100, "critical": 180 },
      "bg-hcs": { "warn": 100, "critical": 180 }
    },
    "links": {}
  },
  "nodes": [],
  "links": [],
  "sourceFeeds": []
}
```

字段说明：

- `scope`: 当前返回的是哪个视图
- `primaryTower`: 当前主塔
- `primaryL3NodeId`: 当前被选中的主 L3 节点 ID，任何时刻全局只能有一个
- `refreshIntervalMs`: 建议前端轮询/流刷新节奏
- `generatedAt`: 快照生成时间
- `thresholds`: 阈值信息，便于调试和对账
- `nodes`: 当前视图应显示的卡片数据
- `links`: 当前视图应显示的线路数据
- `sourceFeeds`: source 卡片内部子线路数据

## 5. Node 结构

```json
{
  "id": "NX9-L3",
  "site": "NX9",
  "name": "L3",
  "displayName": "L3",
  "type": "stage",
  "parentId": null,
  "kind": "process",
  "status": "ok",
  "isPrimary": false,
  "x": 820,
  "y": 628,
  "rate": null,
  "avg": 74,
  "p95": 80,
  "load": 82,
  "cpu": 82,
  "mem": 87,
  "messages": null
}
```

字段定义：

- `id`: 节点唯一标识，例如 `NX9-L3`
- `site`: `NX1 | NX9 | NX2`
- `name`: 逻辑名称，例如 `RT / BB / OMDC / CIIS / L1 / L2 / L3 / BG / HCS`
- `displayName`: 页面展示名称
  - 例如 `BG (NX1)`
  - 例如 `L2-B1`
- `type`
  - `source`
  - `stage`
  - `stage-fragment`
- `parentId`
  - 普通节点为 `null`
  - 单站 `L2-B1 ~ L2-B4` 需要指向父节点 `NX9-L2`
- `kind`
  - `flow`
  - `process`
- `status`
  - `ok`
  - `warn`
  - `critical`
  - `unknown`
- `isPrimary`
  - 是否当前被选为 Primary 卡片
  - 对 `L3` 来说，该字段具有全局唯一性约束，任意时刻只有一个 `L3` 为 `true`
- `x / y`
  - 可选
  - 如果后端希望继续下发布局坐标，可保留
  - 如果未来改为前端自行布局，可以去掉
- `rate`
  - 仅 `source` 节点需要
- `avg / p95 / cpu / mem / messages`
  - 页面卡片直接显示的数据

### 5.1 Node `isPrimary` 规则

- 每张卡片都返回 `isPrimary`
- `L3` 节点中，任何时刻只能有一个节点 `isPrimary = true`
- 单站 `L2-B1 ~ L2-B4` 可以按后端策略返回 `isPrimary`
  - 若当前没有主备高亮需求，可统一返回 `false`
- BG / HCS / L1 / L2 / Source 节点也可以返回 `isPrimary`
  - 若页面只需要 L3 具备主节点语义，其它节点可先返回 `false`

建议：

- 第一阶段至少保证所有 `L3` 节点正确返回 `isPrimary`
- 后续如需要高亮整条主路径，再逐步给其它节点补 `isPrimary`

## 6. Link 结构

```json
{
  "id": "NX1-L3__NX9-BG",
  "from": "NX1-L3",
  "to": "NX9-BG",
  "parentId": null,
  "group": "l3-bg",
  "kind": "primary",
  "status": "ok",
  "isPrimary": true,
  "base": 30,
  "current": 92
}
```

字段定义：

- `id`: 唯一标识，建议继续沿用 `from__to`
- `from / to`: 起点和终点节点 ID
- `parentId`
  - 派生线使用
  - 例如单站 `L1 -> L2-B1` 可挂到父链路 `NX9-L1__NX9-L2`
- `group`
  - `sources`
  - `l1-l2`
  - `l2-l3`
  - `l3-bg`
  - `bg-l3-feedback`
  - `l3-l1-feedback`
  - `bg-hcs`
- `kind`
  - `intra`
  - `primary`
  - `backup`
  - `feedback`
- `status`
  - `ok`
  - `warn`
  - `critical`
  - `unknown`
- `isPrimary`
  - 当前是否被选为 Primary 线路
  - 前端是否显示 pulse，应以该字段为依据
- `base`
  - 基准延迟
- `current`
  - 当前延迟，页面标签直接显示 `${current}ms`

### 6.1 Link `isPrimary` 规则

- 每条线路都返回 `isPrimary`
- 若 `isPrimary = true`，前端允许对该线路显示 pulse
- 若 `isPrimary = false`，前端不显示 pulse
- `flow` 按钮只控制线路是否流动，不改变接口里的 `isPrimary`
- `pulse` 按钮只控制是否显示 pulse，不改变接口里的 `isPrimary`

### 6.2 `L3 -> BG` 可见性规则

这是本次新增的关键业务约束：

- 若某个 `L3` 被选为 Primary，即该节点 `isPrimary = true`
- 则该 `L3` 可以存在 `group = "l3-bg"` 的输出线路
- 若某个 `L3` 没有被选为 Primary，即 `isPrimary = false`
- 则该 `L3` 不应返回任何 `group = "l3-bg"` 的输出线路

换句话说：

- `l3-bg` 线路的存在与否，受 `primaryL3NodeId` 控制
- 页面不应再看到“非 Primary 的 L3 也有到 BG 的线路”

### 6.3 `primaryL3NodeId` 与 `l3-bg` 的一致性约束

后端必须保证以下约束始终成立：

1. `nodes` 中有且仅有一个 `name = "L3"` 的节点满足 `isPrimary = true`
2. `primaryL3NodeId` 必须等于该节点 `id`
3. `links` 中所有 `group = "l3-bg"` 的线路，其 `from` 必须等于 `primaryL3NodeId`
4. 任何其它 `L3` 节点都不应拥有 `group = "l3-bg"` 的输出线路

## 7. SourceFeed 结构

```json
{
  "id": "NX9-RT__SS",
  "sourceNodeId": "NX9-RT",
  "site": "NX9",
  "sourceName": "RT",
  "partition": "SS",
  "status": "ok",
  "isPrimary": true,
  "latency": 31
}
```

字段定义：

- `id`: 唯一标识
- `sourceNodeId`: 对应 source 卡片
- `site`: 所属站点
- `sourceName`: `RT / BB / OMDC / CIIS`
- `partition`: `SS / SZ / HK / BJ`
- `status`: 当前状态
- `isPrimary`: 当前是否主输入 feed
- `latency`: 当前子线路延迟

说明：

- `sourceFeeds` 用于 source 卡片内部的小线路和小标签
- 它不是主拓扑 `links` 的替代，而是补充

## 8. 阈值和状态计算建议

### 8.1 建议由后端直接返回 `status`

前端只消费：

- `node.status`
- `link.status`
- `sourceFeed.status`

不要让前端再自行根据 threshold 推导状态。

原因：

- 业务阈值最终应以服务端口径为准
- 避免前后端状态判定不一致
- 便于后续为个别节点/线路做单独阈值覆盖

### 8.2 阈值仍建议一起返回

保留 `thresholds` 的目的是：

- 调试
- 排查
- 回放
- 与页面显示对账

## 9. ALL 视图响应示例

```json
{
  "scope": "ALL",
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "refreshIntervalMs": 3000,
  "generatedAt": "2026-03-17T14:25:30+08:00",
  "thresholds": {
    "default": { "warn": 100, "critical": 180 },
    "groups": {
      "sources": { "warn": 100, "critical": 180 },
      "l1-l2": { "warn": 100, "critical": 180 },
      "l2-l3": { "warn": 100, "critical": 180 },
      "l3-bg": { "warn": 100, "critical": 180 },
      "bg-l3-feedback": { "warn": 100, "critical": 180 },
      "l3-l1-feedback": { "warn": 100, "critical": 180 },
      "bg-hcs": { "warn": 100, "critical": 180 }
    },
    "links": {}
  },
  "nodes": [
    {
      "id": "NX1-L3",
      "site": "NX1",
      "name": "L3",
      "displayName": "L3",
      "type": "stage",
      "parentId": null,
      "kind": "process",
      "status": "warn",
      "isPrimary": true,
      "x": 292,
      "y": 628,
      "rate": null,
      "avg": 61,
      "p95": 148,
      "load": 70,
      "cpu": 70,
      "mem": 76,
      "messages": null
    },
    {
      "id": "NX9-L3",
      "site": "NX9",
      "name": "L3",
      "displayName": "L3",
      "type": "stage",
      "parentId": null,
      "kind": "process",
      "status": "ok",
      "isPrimary": false,
      "x": 820,
      "y": 628,
      "rate": null,
      "avg": 74,
      "p95": 80,
      "load": 82,
      "cpu": 82,
      "mem": 87,
      "messages": null
    }
  ],
  "links": [
    {
      "id": "NX1-L3__NX1-BG",
      "from": "NX1-L3",
      "to": "NX1-BG",
      "parentId": null,
      "group": "l3-bg",
      "kind": "intra",
      "status": "ok",
      "isPrimary": true,
      "base": 30,
      "current": 92
    },
    {
      "id": "NX1-L3__NX9-BG",
      "from": "NX1-L3",
      "to": "NX9-BG",
      "parentId": null,
      "group": "l3-bg",
      "kind": "primary",
      "status": "ok",
      "isPrimary": true,
      "base": 30,
      "current": 92
    }
  ],
  "sourceFeeds": [
    {
      "id": "NX9-RT__SS",
      "sourceNodeId": "NX9-RT",
      "site": "NX9",
      "sourceName": "RT",
      "partition": "SS",
      "status": "ok",
      "isPrimary": true,
      "latency": 31
    }
  ]
}
```

## 10. SINGLE-SITE 视图响应示例

`scope = NX9` 时，返回数据应直接对应 `SINGLE-SITE TOPOLOGY`。

```json
{
  "scope": "NX9",
  "primaryTower": "NX9",
  "primaryL3NodeId": "NX1-L3",
  "refreshIntervalMs": 3000,
  "generatedAt": "2026-03-17T14:25:30+08:00",
  "thresholds": {
    "default": { "warn": 100, "critical": 180 },
    "groups": {
      "sources": { "warn": 100, "critical": 180 },
      "l1-l2": { "warn": 100, "critical": 180 },
      "l2-l3": { "warn": 100, "critical": 180 },
      "l3-bg": { "warn": 100, "critical": 180 },
      "bg-l3-feedback": { "warn": 100, "critical": 180 },
      "l3-l1-feedback": { "warn": 100, "critical": 180 },
      "bg-hcs": { "warn": 100, "critical": 180 }
    },
    "links": {}
  },
  "nodes": [
    {
      "id": "NX9-L2-B1",
      "site": "NX9",
      "name": "L2",
      "displayName": "L2-B1",
      "type": "stage-fragment",
      "parentId": "NX9-L2",
      "kind": "process",
      "status": "ok",
      "isPrimary": false,
      "x": 700,
      "y": 245,
      "rate": null,
      "avg": 33,
      "p95": 52,
      "load": null,
      "cpu": 41,
      "mem": 50,
      "messages": null
    },
    {
      "id": "NX1-BG",
      "site": "NX1",
      "name": "BG",
      "displayName": "BG (NX1)",
      "type": "stage",
      "parentId": null,
      "kind": "process",
      "status": "critical",
      "isPrimary": false,
      "x": 1160,
      "y": 220,
      "rate": null,
      "avg": 18,
      "p95": 335,
      "load": 24,
      "cpu": 24,
      "mem": 31,
      "messages": 182340
    },
    {
      "id": "NX9-L3",
      "site": "NX9",
      "name": "L3",
      "displayName": "L3",
      "type": "stage",
      "parentId": null,
      "kind": "process",
      "status": "ok",
      "isPrimary": false,
      "x": 930,
      "y": 320,
      "rate": null,
      "avg": 74,
      "p95": 80,
      "load": 82,
      "cpu": 82,
      "mem": 87,
      "messages": null
    }
  ],
  "links": [
    {
      "id": "NX9-L1__NX9-L2-B1",
      "from": "NX9-L1",
      "to": "NX9-L2-B1",
      "parentId": "NX9-L1__NX9-L2",
      "group": "l1-l2",
      "kind": "intra",
      "status": "ok",
      "isPrimary": false,
      "base": 30,
      "current": 34
    },
    {
      "id": "NX9-L2-B1__NX9-L3",
      "from": "NX9-L2-B1",
      "to": "NX9-L3",
      "parentId": "NX9-L2__NX9-L3",
      "group": "l2-l3",
      "kind": "intra",
      "status": "ok",
      "isPrimary": false,
      "base": 50,
      "current": 54
    }
  ],
  "sourceFeeds": [
    {
      "id": "NX9-RT__SS",
      "sourceNodeId": "NX9-RT",
      "site": "NX9",
      "sourceName": "RT",
      "partition": "SS",
      "status": "ok",
      "isPrimary": true,
      "latency": 31
    }
  ]
}
```

说明：

- 上述示例中 `primaryL3NodeId = NX1-L3`
- 因此 `scope = NX9` 的单站视图里，`NX9-L3.isPrimary = false`
- 该快照下不应返回任何 `from = NX9-L3` 且 `group = "l3-bg"` 的线路

## 11. 与前端显示规则的对应关系

### 11.1 卡片显示

- 卡片标题：`displayName`
- 卡片状态徽标：`status`
- `AVG`：`avg`
- `P95`：`p95`
- `MSG`：`messages`
- `CPU`：`cpu`
- `MEM`：`mem`
- 卡片主状态：`status`
- 卡片是否主节点：`isPrimary`

### 11.2 线路显示

- 线路标签：`current`
- 线路颜色：`status`
- 线路类型：`kind`
- 是否显示 pulse：`isPrimary`
- 是否显示这条线：由该条 `link` 是否存在决定

### 11.3 Source 卡片内部显示

- 子线路标签：`latency`
- 子线路状态：`status`
- 子线路是否主 feed：`isPrimary`

## 12. DTO 演进建议

仓库当前已有以下 DTO：

- `TopologySnapshot`
- `TopologyNode`
- `TopologyLink`

如果按本文档实现，建议扩展为：

### 12.1 `TopologySnapshot`

- 新增 `scope`
- 保留 `primaryTower`
- 新增 `primaryL3NodeId`
- 保留 `refreshIntervalMs`
- 保留 `generatedAt`
- 新增 `thresholds`
- 保留 `nodes`
- 保留 `links`
- 新增 `sourceFeeds`

### 12.2 `TopologyNode`

- 新增 `displayName`
- 新增 `parentId`
- 新增 `status`
- 新增 `isPrimary`
- 保留 `id/site/name/type/x/y/rate/avg/p95/load/cpu/mem/kind/messages`

### 12.3 `TopologyLink`

- 新增 `parentId`
- 新增 `status`
- 新增 `isPrimary`
- 保留 `id/from/to/base/group/kind/current`

### 12.4 新增 `TopologySourceFeed`

- `id`
- `sourceNodeId`
- `site`
- `sourceName`
- `partition`
- `status`
- `isPrimary`
- `latency`

## 13. 最终建议

第一阶段建议优先落地以下能力：

1. `snapshot/stream` 增加 `scope`
2. 节点、线路、source feed 全量返回 `status`
3. 节点、线路、source feed 全量返回 `isPrimary`
4. 顶层增加 `primaryL3NodeId`
5. 严格执行“只有 Primary L3 才允许有 `L3 -> BG` 线路”的约束

这样前端就可以稳定驱动：

- 卡片状态
- 线路状态
- pulse 显示
- 主 L3 切换
- `ALL-SITES` / `SINGLE-SITE` 两套视图
