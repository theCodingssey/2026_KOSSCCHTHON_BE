package com.kosscchthon.Icelink.team;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByRoom_IdOrderByTeamNoAsc(Long roomId);

    /**
     * 팀 세션 상태 전이용 행 잠금(SELECT ... FOR UPDATE).
     * 팀원 여러 명이 동시에 start/next/answer 를 눌러도 한 트랜잭션씩 순서대로 처리되어
     * (team_id, order_no) 유니크 위반이나 TeamAnswer PK 중복으로 500 이 나지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);

    Optional<Team> findByIdAndRoom_Id(Long id, Long roomId);

    boolean existsByRoom_Id(Long roomId);

    /** 방 안에서 팀명 중복 검사 (대소문자 무시). Q-03a */
    boolean existsByRoom_IdAndNameIgnoreCaseAndIdNot(Long roomId, String name, Long excludeTeamId);
}
