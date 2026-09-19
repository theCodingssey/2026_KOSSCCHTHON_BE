package com.kosscchthon.Icelink.realtime;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link RoomEventPublisher} 구현. 이벤트를 room_events 에 저장하고(seq 부여),
 * 호출 측 트랜잭션이 커밋된 뒤 SSE 로 브로드캐스트한다.
 * 커밋 전에 보내면 클라이언트가 아직 반영되지 않은 상태를 조회하게 되므로 반드시 afterCommit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseRoomEventPublisher implements RoomEventPublisher {

    private final RoomEventRepository eventRepository;
    private final SseEmitterRegistry registry;
    private final SseParticipantView participantView;

    @Override
    public void publish(RoomEvent event) {
        RoomEventEntity saved = eventRepository.save(RoomEventEntity.from(event));
        log.info("[event] #{} room={} team={} type={}", saved.getId(), event.roomId(), event.teamId(), event.type());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(saved);
                }
            });
        } else {
            broadcast(saved);
        }
    }

    void broadcast(RoomEventEntity event) {
        try {
            if (registry.connectionCount(event.getRoomId()) == 0) {
                return;
            }
            Map<Long, Long> participantTeams = event.isTeamScoped() || needsParticipantView(event)
                    ? participantView.participantTeams(event.getRoomId())
                    : Map.of();
            registry.broadcast(event, participantTeams, (sub, e) ->
                    sub.scope() == SseScope.PARTICIPANT && needsParticipantView(e)
                            ? participantView.customizeForParticipant(sub.participantId(), e)
                            : e.getPayload());
        } catch (RuntimeException e) {
            // 브로드캐스트 실패가 비즈니스 트랜잭션을 깨면 안 된다
            log.warn("sse broadcast failed for event #{}: {}", event.getId(), e.getMessage());
        }
    }

    private static boolean needsParticipantView(RoomEventEntity event) {
        return event.getType() == RoomEventType.TEAM_BUILDING_COMPLETED
                || event.getType() == RoomEventType.ROOM_FINISHED;
    }
}
