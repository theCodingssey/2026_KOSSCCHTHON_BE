package com.kosscchthon.Icelink.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방 단위 SseEmitter 레지스트리. 단일 인스턴스 기준 (인메모리).
 * - subscribe: 연결 등록, 종료/타임아웃/에러 시 자동 제거
 * - broadcast: 저장된 이벤트를 스코프에 맞는 연결에 전송
 * - 15초마다 keep-alive 코멘트 (docs 3.1)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterRegistry {

    public static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(30);
    public static final long HEARTBEAT_MS = 15_000;

    private final SseEventSerializer serializer;
    private final Map<Long, List<SseSubscription>> byRoom = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    public SseSubscription subscribe(SseScope scope, Long roomId, Long teamId, Long participantId, String userKey) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        SseSubscription sub = new SseSubscription(nextId.incrementAndGet(), scope, roomId, teamId, participantId, userKey, emitter);
        byRoom.computeIfAbsent(roomId, r -> new CopyOnWriteArrayList<>()).add(sub);
        Runnable cleanup = () -> remove(sub);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(e -> cleanup.run());
        log.debug("sse subscribed: {} room={} team={} participant={}", scope, roomId, teamId, participantId);
        return sub;
    }

    /** 저장된 이벤트를 해당 방의 구독자 중 스코프가 맞는 곳에 보낸다. 실패한 연결은 제거. */
    public void broadcast(RoomEventEntity event, Map<Long, Long> participantTeams, SsePayloadCustomizer customizer) {
        List<SseSubscription> subs = byRoom.get(event.getRoomId());
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (SseSubscription sub : subs) {
            if (!sub.accepts(event, participantTeams)) {
                continue;
            }
            Map<String, Object> payload = customizer == null ? event.getPayload() : customizer.customize(sub, event);
            send(sub, event.getId(), event.getType().name(), serializer.data(event, payload));
        }
    }

    /** 단일 구독에 이벤트 하나 전송 (재연결 재전송·접속 확인용) */
    public boolean send(SseSubscription sub, Long id, String eventName, String jsonData) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName).data(jsonData, MediaType.APPLICATION_JSON);
            if (id != null) {
                builder.id(Long.toString(id));
            }
            sub.emitter().send(builder);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("sse send failed, dropping subscription {}: {}", sub.id(), e.getMessage());
            remove(sub);
            try {
                sub.emitter().completeWithError(e);
            } catch (RuntimeException ignored) {
                // already completed
            }
            return false;
        }
    }

    /** 15초마다 모든 연결에 `: keep-alive` 코멘트. 끊긴 연결은 이때 정리된다. */
    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        for (List<SseSubscription> subs : byRoom.values()) {
            for (SseSubscription sub : subs) {
                try {
                    sub.emitter().send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    remove(sub);
                }
            }
        }
    }

    public int connectionCount(Long roomId) {
        List<SseSubscription> subs = byRoom.get(roomId);
        return subs == null ? 0 : subs.size();
    }

    private void remove(SseSubscription sub) {
        List<SseSubscription> subs = byRoom.get(sub.roomId());
        if (subs != null) {
            subs.remove(sub);
            if (subs.isEmpty()) {
                byRoom.remove(sub.roomId(), subs);
            }
        }
    }

    /** 구독자별로 payload 를 바꿔야 할 때 (참가자 스트림의 myTeam 등) */
    @FunctionalInterface
    public interface SsePayloadCustomizer {
        Map<String, Object> customize(SseSubscription subscription, RoomEventEntity event);
    }
}
