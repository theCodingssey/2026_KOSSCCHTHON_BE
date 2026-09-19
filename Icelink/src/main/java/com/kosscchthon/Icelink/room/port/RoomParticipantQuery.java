package com.kosscchthon.Icelink.room.port;

import com.kosscchthon.Icelink.room.dto.ParticipantCounts;
import com.kosscchthon.Icelink.room.dto.RoomParticipantSummary;
import java.util.List;
import java.util.Optional;

/**
 * room 모듈이 참가자 데이터를 읽는 포트. participant 모듈(3.2)이 구현한다.
 * room → participant 방향 의존을 끊어 두 모듈을 독립적으로 개발하기 위한 경계.
 */
public interface RoomParticipantQuery {

    /** LEFT 가 아닌 참가자 수 */
    int countActive(Long roomId);

    ParticipantCounts counts(Long roomId);

    /** 주최자 상세용 참가자 요약 목록 (LEFT 제외, 입장 순) */
    List<RoomParticipantSummary> listActive(Long roomId);

    /** 유저가 참가자로 속한 진행 중인 방 (activeRoom 판정용) */
    Optional<ActiveParticipation> findActiveParticipation(String userKey);

    record ActiveParticipation(Long roomId, Long participantId, String participantStatus, Long teamId) {
    }
}
