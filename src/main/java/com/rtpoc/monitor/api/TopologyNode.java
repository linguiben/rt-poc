package com.rtpoc.monitor.api;

public record TopologyNode(
    String id,
    String site,
    String name,
    String type,
    int x,
    int y,
    Integer rate,
    Integer avg,
    Integer p95,
    Integer load,
    Integer cpu,
    Integer mem,
    String kind,
    Integer messages
) {
}
