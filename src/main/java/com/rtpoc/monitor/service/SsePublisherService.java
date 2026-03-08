package com.rtpoc.monitor.service;

import com.rtpoc.monitor.api.TopologySnapshot;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SsePublisherService {

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribe(TopologySnapshot snapshot) {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError((error) -> emitters.remove(emitter));
    send(emitter, snapshot);
    return emitter;
  }

  public void broadcast(TopologySnapshot snapshot) {
    for (SseEmitter emitter : emitters) {
      if (!send(emitter, snapshot)) {
        emitters.remove(emitter);
      }
    }
  }

  private boolean send(SseEmitter emitter, TopologySnapshot snapshot) {
    try {
      emitter.send(SseEmitter.event()
          .name("snapshot")
          .id(snapshot.generatedAt().toString())
          .data(snapshot, MediaType.APPLICATION_JSON));
      return true;
    } catch (IOException ex) {
      emitter.completeWithError(ex);
      return false;
    }
  }
}
