package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.room.port.RoomParticipantQuery;
import com.kosscchthon.Icelink.user.ActiveRoomProvider;
import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /users/me 의 activeRoom.
 * 주최 중인 방(host)과 참가 중인 방(participant) 중 더 최근에 만들어진 방을 고른다.
 */
@Component
@RequiredArgsConstructor
public class RoomActiveRoomProvider implements ActiveRoomProvider {

    private final RoomRepository roomRepository;
    private final RoomParticipantQuery participantQuery;

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveRoomResponse> findActiveRoom(String userKey) {
        Optional<Room> hosted = roomRepository.findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(userKey, RoomStatus.ACTIVE);
        Optional<ParticipantView> joined = participantQuery.findActiveParticipation(userKey)
                .flatMap(p -> roomRepository.findById(p.roomId())
                        .filter(Room::isActive)
                        .map(room -> new ParticipantView(room, p)));

        if (hosted.isPresent() && joined.isPresent()) {
            boolean hostIsNewer = !hosted.get().getCreatedAt().isBefore(joined.get().room().getCreatedAt());
            return Optional.of(hostIsNewer ? asHost(hosted.get()) : asParticipant(joined.get()));
        }
        if (hosted.isPresent()) {
            return hosted.map(RoomActiveRoomProvider::asHost);
        }
        return joined.map(RoomActiveRoomProvider::asParticipant);
    }

    private static ActiveRoomResponse asHost(Room room) {
        return new ActiveRoomResponse("HOST", room.getId(), room.getCode(), room.getTitle(),
                room.getStatus().name(), null, null, null);
    }

    private static ActiveRoomResponse asParticipant(ParticipantView view) {
        Room room = view.room();
        RoomParticipantQuery.ActiveParticipation p = view.participation();
        return new ActiveRoomResponse("PARTICIPANT", room.getId(), room.getCode(), room.getTitle(),
                room.getStatus().name(), p.participantId(), p.participantStatus(), p.teamId());
    }

    private record ParticipantView(Room room, RoomParticipantQuery.ActiveParticipation participation) {
    }
}
