package com.kosscchthon.Icelink.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SseRoomEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Mock RoomEventRepository eventRepository;
    @Mock SseEmitterRegistry registry;
    @Mock SseParticipantView participantView;

    @Test
    void publish_persistsEvent_andBroadcastsImmediatelyWithoutTransaction() {
        SseRoomEventPublisher publisher = new SseRoomEventPublisher(eventRepository, registry, participantView);
        RoomEvent event = RoomEvent.room(12L, RoomEventType.PARTICIPANT_JOINED, Map.of("participantId", 1L), NOW);
        RoomEventEntity saved = SseSubscriptionTest.event(77, 12L, null, RoomEventType.PARTICIPANT_JOINED);
        when(eventRepository.save(any(RoomEventEntity.class))).thenReturn(saved);
        when(registry.connectionCount(12L)).thenReturn(2);

        publisher.publish(event);

        ArgumentCaptor<RoomEventEntity> captor = ArgumentCaptor.forClass(RoomEventEntity.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo(12L);
        assertThat(captor.getValue().getType()).isEqualTo(RoomEventType.PARTICIPANT_JOINED);
        assertThat(captor.getValue().getPayload()).containsEntry("participantId", 1L);
        verify(registry).broadcast(eq(saved), eq(Map.of()), any());
        verify(participantView, never()).participantTeams(any());
    }

    @Test
    void publish_skipsBroadcastWhenNobodyIsConnected() {
        SseRoomEventPublisher publisher = new SseRoomEventPublisher(eventRepository, registry, participantView);
        RoomEventEntity saved = SseSubscriptionTest.event(1, 12L, null, RoomEventType.ROOM_UPDATED);
        when(eventRepository.save(any(RoomEventEntity.class))).thenReturn(saved);
        when(registry.connectionCount(12L)).thenReturn(0);

        publisher.publish(RoomEvent.room(12L, RoomEventType.ROOM_UPDATED, Map.of(), NOW));

        verify(registry, never()).broadcast(any(), any(), any());
    }

    @Test
    void teamScopedEvent_loadsParticipantTeamMapping() {
        SseRoomEventPublisher publisher = new SseRoomEventPublisher(eventRepository, registry, participantView);
        RoomEventEntity saved = SseSubscriptionTest.event(5, 12L, 501L, RoomEventType.QUESTION_CREATED);
        when(eventRepository.save(any(RoomEventEntity.class))).thenReturn(saved);
        when(registry.connectionCount(12L)).thenReturn(1);
        when(participantView.participantTeams(12L)).thenReturn(Map.of(101L, 501L));

        publisher.publish(RoomEvent.team(12L, 501L, RoomEventType.QUESTION_CREATED, Map.of(), NOW));

        verify(registry).broadcast(eq(saved), eq(Map.of(101L, 501L)), any());
    }

    @Test
    void roomFinished_customizerFillsMyTeamOnlyForParticipantScope() {
        SseRoomEventPublisher publisher = new SseRoomEventPublisher(eventRepository, registry, participantView);
        RoomEventEntity saved = SseSubscriptionTest.event(9, 12L, null, RoomEventType.ROOM_FINISHED);
        when(eventRepository.save(any(RoomEventEntity.class))).thenReturn(saved);
        when(registry.connectionCount(12L)).thenReturn(1);
        when(participantView.participantTeams(12L)).thenReturn(Map.of());
        Map<String, Object> enriched = Map.of("finishedAt", "x", "myTeam", Map.of("teamId", 501L, "name", "1팀", "keywords", List.of()));
        when(participantView.customizeForParticipant(eq(101L), eq(saved))).thenReturn(enriched);

        publisher.publish(RoomEvent.room(12L, RoomEventType.ROOM_FINISHED, Map.of("finishedAt", "x"), NOW));

        ArgumentCaptor<SseEmitterRegistry.SsePayloadCustomizer> captor = ArgumentCaptor.forClass(SseEmitterRegistry.SsePayloadCustomizer.class);
        verify(registry).broadcast(eq(saved), any(), captor.capture());
        SseSubscription participant = new SseSubscription(1, SseScope.PARTICIPANT, 12L, null, 101L, "k", null);
        SseSubscription host = new SseSubscription(2, SseScope.HOST, 12L, null, null, "h", null);
        assertThat(captor.getValue().customize(participant, saved)).isEqualTo(enriched);
        assertThat(captor.getValue().customize(host, saved)).isEqualTo(saved.getPayload());
    }
}
