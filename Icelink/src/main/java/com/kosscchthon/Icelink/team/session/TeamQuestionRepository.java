package com.kosscchthon.Icelink.team.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamQuestionRepository extends JpaRepository<TeamQuestion, Long> {

    List<TeamQuestion> findAllByTeam_IdOrderByOrderNoAsc(Long teamId);

    /** 현재(가장 최근) 질문 */
    Optional<TeamQuestion> findFirstByTeam_IdOrderByOrderNoDesc(Long teamId);

    Optional<TeamQuestion> findByIdAndTeam_Id(Long id, Long teamId);

    /** 팀 내 중복 방지용 질문 본문 목록 (순서대로) */
    @Query("select q.content from TeamQuestion q where q.team.id = :teamId order by q.orderNo asc")
    List<String> findContentsByTeamId(@Param("teamId") Long teamId);

    boolean existsByTeam_IdAndStatus(Long teamId, QuestionStatus status);
}
