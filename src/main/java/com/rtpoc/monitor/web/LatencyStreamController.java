package com.rtpoc.monitor.web;

import com.rtpoc.monitor.api.TopologySnapshot;
import com.rtpoc.monitor.service.LatencyTopologyService;
import com.rtpoc.monitor.service.SsePublisherService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/latency")
public class LatencyStreamController {

  private final LatencyTopologyService topologyService;
  private final SsePublisherService publisherService;

  public LatencyStreamController(LatencyTopologyService topologyService, SsePublisherService publisherService) {
    this.topologyService = topologyService;
    this.publisherService = publisherService;
  }

  @GetMapping("/snapshot")
  public TopologySnapshot snapshot() {
    return topologyService.currentSnapshot();
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return publisherService.subscribe(topologyService.currentSnapshot());
  }
}
