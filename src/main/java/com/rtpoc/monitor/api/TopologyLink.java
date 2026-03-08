package com.rtpoc.monitor.api;

public record TopologyLink(
    String id,
    String from,
    String to,
    int base,
    String group,
    String kind,
    int current
) {
}
