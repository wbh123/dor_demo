package com.wust.dormitory.realtime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomEventHub {
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long batchId, long roomId) {
        String channel = channel(batchId, roomId);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> remove(channel, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        send(emitter, new RoomEvent(UUID.randomUUID().toString(), "CONNECTED", batchId, roomId,
                Map.of("message", "房间实时连接已建立"), Instant.now()));
        return emitter;
    }

    public void publish(long batchId, long roomId, String type, Object data) {
        RoomEvent event = new RoomEvent(UUID.randomUUID().toString(), type, batchId, roomId, data, Instant.now());
        Set<SseEmitter> channelEmitters = emitters.get(channel(batchId, roomId));
        if (channelEmitters == null) {
            return;
        }
        channelEmitters.forEach(emitter -> send(emitter, event));
    }

    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        emitters.forEach((channel, channelEmitters) -> channelEmitters.forEach(emitter ->
                send(emitter, new RoomEvent(UUID.randomUUID().toString(), "HEARTBEAT", 0, 0,
                        Map.of("channel", channel), Instant.now()))));
    }

    private void send(SseEmitter emitter, RoomEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.eventId()).name(event.type()).data(event));
        } catch (IOException | IllegalStateException exception) {
            emitters.values().forEach(set -> set.remove(emitter));
        }
    }

    private void remove(String channel, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(channel);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(channel);
            }
        }
    }

    private String channel(long batchId, long roomId) {
        return batchId + ":" + roomId;
    }

    public record RoomEvent(String eventId, String type, long batchId, long roomId,
                            Object data, Instant occurredAt) {
    }
}
