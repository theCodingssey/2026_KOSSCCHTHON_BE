package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.room.RoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByRoom_IdAndUser_UserKey(Long roomId, String userKey);

    Optional<Participant> findByIdAndRoom_Id(Long id, Long roomId);

    /** LEFT 제외 인원 */
    int countByRoom_IdAndStatusNot(Long roomId, ParticipantStatus excluded);

    int countByRoom_IdAndStatus(Long roomId, ParticipantStatus status);

    /** 팀원 목록 (ID 순) */
    List<Participant> findAllByTeam_IdOrderByIdAsc(Long teamId);

    /** 방의 배정된 참가자 전체 (팀별 그룹핑용) */
    List<Participant> findAllByRoom_IdAndTeamIsNotNullOrderByIdAsc(Long roomId);

    @Query("select p.team.id, count(p) from Participant p where p.room.id = :roomId and p.team is not null group by p.team.id")
    List<Object[]> countMembersByTeamRaw(@Param("roomId") Long roomId);

    /** teamId → 팀원 수 */
    default Map<Long, Long> countMembersByTeam(Long roomId) {
        return countMembersByTeamRaw(roomId).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    /** LEFT 제외, 입장 순 */
    List<Participant> findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(Long roomId, ParticipantStatus excluded);

    /** 방 안에서 쓰이는 닉네임(LEFT 제외), 소문자. 중복 검사·추천용 */
    @Query("select lower(p.nickname) from Participant p where p.room.id = :roomId and p.status <> com.kosscchthon.Icelink.participant.ParticipantStatus.LEFT")
    List<String> findActiveNicknamesLower(@Param("roomId") Long roomId);

    /** 유저가 참가자로 속한 진행 중인 방 (R-09, activeRoom) */
    @Query("""
            select p from Participant p
            where p.user.userKey = :userKey
              and p.status <> com.kosscchthon.Icelink.participant.ParticipantStatus.LEFT
              and p.room.status in :roomStatuses
            order by p.joinedAt desc
            limit 1
            """)
    Optional<Participant> findActiveParticipation(@Param("userKey") String userKey,
                                                  @Param("roomStatuses") Collection<RoomStatus> roomStatuses);
}
