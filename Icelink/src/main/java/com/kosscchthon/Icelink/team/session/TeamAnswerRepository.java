package com.kosscchthon.Icelink.team.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamAnswerRepository extends JpaRepository<TeamAnswer, Long> {

    Optional<TeamAnswer> findByQuestionId(Long questionId);

    /** 팀의 모든 답변 (질문 순). 누적 키워드·요약용 */
    List<TeamAnswer> findAllByQuestion_Team_IdOrderByQuestion_OrderNoAsc(Long teamId);

    List<TeamAnswer> findAllByQuestionIdIn(List<Long> questionIds);
}
