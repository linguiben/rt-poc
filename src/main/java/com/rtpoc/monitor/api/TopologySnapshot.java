package com.rtpoc.monitor.api;

import java.time.Instant;
import java.util.List;

public record TopologySnapshot(
    String primaryTower,
    long refreshIntervalMs,
    Instant generatedAt,
    List<TopologyNode> nodes,
    List<TopologyLink> links
) {
}
