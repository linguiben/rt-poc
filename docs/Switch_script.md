
## 1. fast switching: NX9 -> NX1
```javascript
const s = buildMockSnapshotState();

  // 1. NX9 当前 primary 的 source feed 降级为 back/internal
  [
    "NX9-RT__SS",
    "NX9-RT__SZ",
    "NX9-OMDC__HK",
    "NX9-CIIS__BJ"
  ].forEach((id) => {
    s.sourceFeedStatesById[id] = {
      ...s.sourceFeedStatesById[id],
      isPrimary: false
    };
  });

  // 2. NX1 对应 source feed 提升为 primary
  [
    "NX1-RT__SS",
    "NX1-RT__SZ",
    "NX1-OMDC__HK",
    "NX1-CIIS__BJ"
  ].forEach((id) => {
    s.sourceFeedStatesById[id] = {
      ...s.sourceFeedStatesById[id],
      isPrimary: true
    };
  });

  // 3. NX9 的 source -> L1 / L1 -> L2 / L2 -> L3 降级
  [
    "NX9-RT__NX9-L1",
    "NX9-OMDC__NX9-L1",
    "NX9-CIIS__NX9-L1",
    "NX9-L1__NX9-L2",
    "NX9-L2__NX9-L3"
  ].forEach((id) => {
    s.linkStatesById[id] = {
      ...s.linkStatesById[id],
      isPrimary: false
    };
  });

  // 4. NX1 的 source -> L1 / L1 -> L2 / L2 -> NX9-L3 提升为 primary
  [
    "NX1-RT__NX1-L1",
    "NX1-OMDC__NX1-L1",
    "NX1-CIIS__NX1-L1",
    "NX1-L1__NX1-L2",
    "NX1-L2__NX9-L3"
  ].forEach((id) => {
    s.linkStatesById[id] = {
      ...s.linkStatesById[id],
      isPrimary: true
    };
  });

  // 如果你还想让顶部 Primary Site 和 tower 高亮也切到 NX1，可以打开这一行
  // s.primaryTower = "NX1";

  applyTopologySnapshot(s);
```

## 2. Role swap: NX9 -> NX2
```javascript
/** role switch
    1.NX9-L3 -> NX2-L3 : primary
    2.NX9-L2__NX9-L3 -> back/internal
    3.NX9-L2__NX2-L3 -> primary */
  const s = buildMockSnapshotState();
  s.primaryL3NodeId = "NX2-L3";
  s.linkStatesById["NX9-L2__NX9-L3"] = {
    ...s.linkStatesById["NX9-L2__NX9-L3"],
    isPrimary: false
  };
  s.linkStatesById["NX9-L2__NX2-L3"] = {
    ...s.linkStatesById["NX9-L2__NX2-L3"],
    isPrimary: true
  };
  applyTopologySnapshot(s);
```