package com.kosscchthon.Icelink.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.room.port.RoomParticipantQuery;
import com.kosscchthon.Icelink.room.port.RoomParticipantQuery.ActiveParticipation;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomActiveRoomProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String KEY = "a".repeat(64);
    private static final User USER = User.register(KEY, "민수", NOW);

    @Mock RoomRepository roomRepository;
    @Mock RoomParticipantQuery participantQuery;
    @InjectMocks RoomActiveRoomProvider provider;

    private static Room room(long id, String code, Instant createdAt) throws Exception {
        Room room = Room.create(code, USER, "t", "s", 4, List.of(), createdAt, Duration.ofHours(24));
        Field idField = Room.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(room, id);
        return room;
    }

    @Test
    void emptyWhenNeitherHostingNorParticipating() {
        when(roomRepository.findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(KEY, RoomStatus.ACTIVE)).thenReturn(Optional.empty());
        when(participantQuery.findActiveParticipation(KEY)).thenReturn(Optional.empty());

        assertThat(provider.findActiveRoom(KEY)).isEmpty();
    }

    @Test
    void hostRoleWhenHostingOnly() throws Exception {
        Room hosted = room(1L, "AAAAAA", NOW);
        when(roomRepository.findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(KEY, RoomStatus.ACTIVE)).thenReturn(Optional.of(hosted));
        when(participantQuery.findActiveParticipation(KEY)).thenReturn(Optional.empty());

        ActiveRoomResponse res = provider.findActiveRoom(KEY).orElseThrow();

        assertThat(res.role()).isEqualTo("HOST");
        assertThat(res.code()).isEqualTo("AAAAAA");
        assertThat(res.participantId()).isNull();
    }

    @Test
    void participantRoleWhenParticipatingOnly() throws Exception {
        Room joined = room(2L, "BBBBBB", NOW);
        when(roomRepository.findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(KEY, RoomStatus.ACTIVE)).thenReturn(Optional.empty());
        when(participantQuery.findActiveParticipation(KEY)).thenReturn(Optional.of(new ActiveParticipation(2L, 101L, "ASSIGNED", 501L)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(joined));

        ActiveRoomResponse res = provider.findActiveRoom(KEY).orElseThrow();

        assertThat(res.role()).isEqualTo("PARTICIPANT");
        assertThat(res.participantId()).isEqualTo(101L);
        assertThat(res.participantStatus()).isEqualTo("ASSIGNED");
        assertThat(res.teamId()).isEqualTo(501L);
    }

    @Test
    void picksMoreRecentRoomWhenBothHostingAndParticipating() throws Exception {
        Room hosted = room(1L, "AAAAAA", NOW.minusSeconds(600));
        Room joined = room(2L, "BBBBBB", NOW);
        when(roomRepository.findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(KEY, RoomStatus.ACTIVE)).thenReturn(Optional.of(hosted));
        when(participantQuery.findActiveParticipation(KEY)).thenReturn(Optional.of(new ActiveParticipation(2L, 101L, "JOINED", null)));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(joined));

        assertThat(provider.findActiveRoom(KEY).orElseThrow().role()).isEqualTo("PARTICIPANT");
    }
}
