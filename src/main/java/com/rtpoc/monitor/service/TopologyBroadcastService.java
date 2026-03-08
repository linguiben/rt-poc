package com.rtpoc.monitor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TopologyBroadcastService {

  private final LatencyTopologyService topologyService;
  private final SsePublisherService publisherService;

  public TopologyBroadcastService(LatencyTopologyService topologyService, SsePublisherService publisherService) {
    this.topologyService = topologyService;
    this.publisherService = publisherService;
  }

  @Scheduled(
      fixedDelay = LatencyTopologyService.REFRESH_INTERVAL_MS,
      initialDelay = LatencyTopologyService.REFRESH_INTERVAL_MS
  )
  public void publishSnapshot() {
    publisherService.broadcast(topologyService.refreshSnapshot());
  }
}
