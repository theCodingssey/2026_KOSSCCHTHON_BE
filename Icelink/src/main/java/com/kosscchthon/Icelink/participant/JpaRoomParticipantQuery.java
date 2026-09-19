package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.room.dto.ParticipantCounts;
import com.kosscchthon.Icelink.room.dto.RoomParticipantSummary;
import com.kosscchthon.Icelink.room.port.RoomParticipantQuery;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** room 모듈의 {@link RoomParticipantQuery} 포트 구현. */
@Component
@RequiredArgsConstructor
public class JpaRoomParticipantQuery implements RoomParticipantQuery {

    private final ParticipantRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public int countActive(Long roomId) {
        return participantRepository.countByRoom_IdAndStatusNot(roomId, ParticipantStatus.LEFT);
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantCounts counts(Long roomId) {
        Map<ParticipantStatus, Integer> byStatus = new EnumMap<>(ParticipantStatus.class);
        List<Participant> active = participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(roomId, ParticipantStatus.LEFT);
        for (Participant p : active) {
            byStatus.merge(p.getStatus(), 1, Integer::sum);
        }
        return new ParticipantCounts(
                byStatus.getOrDefault(ParticipantStatus.JOINED, 0),
                byStatus.getOrDefault(ParticipantStatus.SURVEY_DONE, 0),
                byStatus.getOrDefault(ParticipantStatus.ASSIGNED, 0),
                byStatus.getOrDefault(ParticipantStatus.LATE, 0),
                active.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomParticipantSummary> listActive(Long roomId) {
        return participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(roomId, ParticipantStatus.LEFT)
                .stream()
                .map(p -> new RoomParticipantSummary(p.getId(), p.getNickname(), p.getStatus().name(), null, p.getJoinedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveParticipation> findActiveParticipation(String userKey) {
        return participantRepository.findActiveParticipation(userKey, RoomStatus.ACTIVE)
                .map(p -> new ActiveParticipation(p.getRoomId(), p.getId(), p.getStatus().name(), null));
    }
}
