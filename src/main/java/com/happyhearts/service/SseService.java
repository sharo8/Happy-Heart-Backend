package com.happyhearts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class SseService {

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(String eventType, Object data) {
        String json;
        try {
            json = mapper.writeValueAsString(Map.of("type", eventType, "data", data));
        } catch (Exception e) {
            return;
        }

        Set<SseEmitter> dead = new CopyOnWriteArraySet<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(json));
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
