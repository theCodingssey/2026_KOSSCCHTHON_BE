package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionContext;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpContext;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamRepository;
import com.kosscchthon.Icelink.team.TeamStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비동기 파이프라인의 DB 경계. LLM 호출(수 초)은 트랜잭션 밖에서 하고,
 * 그 전후의 "읽기"와 "쓰기"만 여기서 짧은 트랜잭션으로 처리한다.
 * 모든 메서드는 REQUIRES_NEW 라서 @Async 스레드에서 독립적으로 커밋된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGenerationStore {

    private final TeamRepository teamRepository;
    private final TeamQuestionRepository questionRepository;
    private final TeamAnswerRepository answerRepository;
    private final ParticipantRepository participantRepository;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    /** 첫 질문(프롬프트 ①) 생성에 필요한 값. 지금 생성하면 안 되는 상태면 empty. */
    public record FirstQuestionSnapshot(Long teamId, FirstQuestionContext ctx, List<String> existingQuestions) {
    }

    /** 답변 처리(프롬프트 ②)에 필요한 값. */
    public record FollowUpSnapshot(Long teamId, Long questionId, FollowUpContext ctx, int memberCount,
                                   boolean tooShort, List<String> existingQuestions) {
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<FirstQuestionSnapshot> loadFirstQuestionSnapshot(Long teamId) {
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null || !canGenerateNext(team)) {
            return Optional.empty();
        }
        if (questionRepository.existsByTeam_IdAndStatus(teamId, QuestionStatus.ANSWERING)
                || questionRepository.existsByTeam_IdAndStatus(teamId, QuestionStatus.PROCESSING)) {
            return Optional.empty(); // 이미 열린 질문이 있음
        }
        Room room = team.getRoom();
        int memberCount = participantRepository.findAllByTeam_IdOrderByIdAsc(teamId).size();
        return Optional.of(new FirstQuestionSnapshot(teamId,
                new FirstQuestionContext(room.getSituation(), team.getCategory(), memberCount),
                questionRepository.findContentsByTeamId(teamId)));
    }

    /** 생성된 질문 저장 + QUESTION_CREATED. 상태가 바뀌었으면(종료 등) 저장하지 않고 false. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveGeneratedQuestion(Long teamId, String content, QuestionType type, Map<String, Object> generationContext) {
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null || !canGenerateNext(team)) {
            log.info("skip saving generated question: team {} no longer eligible", teamId);
            return false;
        }
        if (questionRepository.existsByTeam_IdAndStatus(teamId, QuestionStatus.ANSWERING)) {
            log.info("skip saving generated question: team {} already has an open question", teamId);
            return false;
        }
        createQuestion(team, content, type, generationContext, Instant.now(clock));
        return true;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<FollowUpSnapshot> loadFollowUpSnapshot(Long questionId) {
        TeamQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null || question.getStatus() != QuestionStatus.PROCESSING) {
            return Optional.empty();
        }
        TeamAnswer answer = answerRepository.findByQuestionId(questionId).orElse(null);
        if (answer == null) {
            return Optional.empty();
        }
        Team team = question.getTeam();
        Room room = team.getRoom();
        List<String> previousQuestions = questionRepository.findContentsByTeamId(team.getId());
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (TeamAnswer a : answerRepository.findAllByQuestion_Team_IdOrderByQuestion_OrderNoAsc(team.getId())) {
            if (!a.getQuestionId().equals(questionId)) {
                keywords.addAll(a.getKeywords());
            }
        }
        int memberCount = participantRepository.findAllByTeam_IdOrderByIdAsc(team.getId()).size();
        FollowUpContext ctx = new FollowUpContext(room.getSituation(), team.getCategory(), memberCount, question.getContent(),
                answer.getAnswerText(), List.copyOf(keywords), previousQuestions);
        return Optional.of(new FollowUpSnapshot(team.getId(), questionId, ctx, memberCount, answer.isTooShort(), previousQuestions));
    }

    /**
     * 답변 처리 결과 반영: 키워드 저장, 질문 DONE, ANSWER_PROCESSED.
     * 이어서 다음 질문을 만들 수 있으면(방 진행 중·팀 QUESTIONING·상한 미달) 생성하고 QUESTION_CREATED.
     * @return 다음 질문이 생성됐으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeAnswer(Long questionId, List<String> keywords, String nextQuestion, QuestionType nextType,
                                  Map<String, Object> generationContext) {
        TeamQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null || question.getStatus() != QuestionStatus.PROCESSING) {
            return false;
        }
        Instant now = Instant.now(clock);
        Team team = question.getTeam();
        TeamAnswer answer = answerRepository.findByQuestionId(questionId).orElse(null);
        if (answer != null) {
            answer.applyKeywords(keywords, now);
        }
        question.markDone();
        question.attachGenerationContext(generationContext);
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), team.getId(), RoomEventType.ANSWER_PROCESSED, Map.of(
                "teamId", team.getId(),
                "questionId", questionId,
                "keywords", keywords == null ? List.of() : keywords
        ), now));

        if (nextQuestion == null || !canGenerateNext(team)) {
            return false;
        }
        createQuestion(team, nextQuestion, nextType, generationContext, now);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long questionId, AiFailureReason reason) {
        TeamQuestion question = questionRepository.findById(questionId).orElse(null);
        if (question == null || question.getStatus() != QuestionStatus.PROCESSING) {
            return;
        }
        question.markFailed(reason);
        Team team = question.getTeam();
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), team.getId(), RoomEventType.ANSWER_FAILED, Map.of(
                "teamId", team.getId(),
                "questionId", questionId,
                "failureReason", reason.name(),
                "retryable", true
        ), Instant.now(clock)));
    }

    // ---- 내부 ----

    private static boolean canGenerateNext(Team team) {
        return team.getRoom().getStatus() == RoomStatus.IN_PROGRESS
                && team.getStatus() == TeamStatus.QUESTIONING
                && !team.hasReachedQuestionLimit();
    }

    private void createQuestion(Team team, String content, QuestionType type, Map<String, Object> generationContext, Instant now) {
        team.incrementQuestionCount();
        TeamQuestion created = questionRepository.save(
                TeamQuestion.create(team, team.getQuestionCount(), type, content, generationContext, now));
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), team.getId(), RoomEventType.QUESTION_CREATED, Map.of(
                "teamId", team.getId(),
                "question", Map.of(
                        "questionId", created.getId(),
                        "orderNo", created.getOrderNo(),
                        "type", created.getType().name(),
                        "content", created.getContent())
        ), now));
        eventPublisher.publish(RoomEvent.room(team.getRoomId(), RoomEventType.TEAM_STATUS_CHANGED, Map.of(
                "teamId", team.getId(),
                "teamNo", team.getTeamNo(),
                "status", team.getStatus().name(),
                "questionCount", team.getQuestionCount()
        ), now));
    }
}
