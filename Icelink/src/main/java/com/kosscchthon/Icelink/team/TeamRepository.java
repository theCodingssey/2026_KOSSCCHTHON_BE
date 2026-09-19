package com.kosscchthon.Icelink.team;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByRoom_IdOrderByTeamNoAsc(Long roomId);

    Optional<Team> findByIdAndRoom_Id(Long id, Long roomId);

    boolean existsByRoom_Id(Long roomId);

    /** 방 안에서 팀명 중복 검사 (대소문자 무시). Q-03a */
    boolean existsByRoom_IdAndNameIgnoreCaseAndIdNot(Long roomId, String name, Long excludeTeamId);
}
