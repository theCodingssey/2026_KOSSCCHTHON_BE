package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamAccessChecker;
import com.kosscchthon.Icelink.team.TeamRepository;
import com.kosscchthon.Icelink.team.TeamStatus;
import com.kosscchthon.Icelink.team.dto.TeamMemberView;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.StartTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.TeamSummaryResponse;
import com.kosscchthon.Icelink.team.session.dto.TeamQuestionResponse;
import com.kosscchthon.Icelink.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 팀 세션 진행: 시작, 팀명, 질문 조회, 다음 질문, 답변 제출, 재시도, 요약 (docs 2.4절). */
@Service
@RequiredArgsConstructor
public class TeamSessionService {

    static final int ESTIMATED_SECONDS = 8;
    static final String INTRO_TEMPLATE = "돌아가며 간단히 자기소개를 하고, '%s' 대신 우리 팀만의 이름을 정해보세요!";

    private final TeamAccessChecker teamAccessChecker;
    private final TeamRepository teamRepository;
    private final TeamQuestionRepository questionRepository;
    private final TeamAnswerRepository answerRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionGenerationService generationService;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    /** POST /teams/{id}/start — 모두 모였어요 (Q-01, Q-02). 멱등. */
    @Transactional
    public StartTeamResponse start(Long teamId, User user) {
        Team team = lockTeam(teamId);
        teamAccessChecker.requireMember(team, user.getUserKey());
        requireRoomInProgress(team);

        Instant now = Instant.now(clock);
        boolean started = team.start(now);
        if (started) {
            team.incrementQuestionCount();
            TeamQuestion intro = questionRepository.save(TeamQuestion.create(team, team.getQuestionCount(), QuestionType.INTRO,
                    INTRO_TEMPLATE.formatted(team.getName()), null, now));
            eventPublisher.publish(RoomEvent.team(team.getRoomId(), teamId, RoomEventType.TEAM_STARTED, Map.of(
                    "teamId", teamId,
                    "currentQuestion", questionPayload(intro)
            ), now));
            publishTeamStatus(team, now);
        }
        TeamQuestionResponse current = currentQuestion(teamId).orElse(null);
        return new StartTeamResponse(team.getStatus(), started, current);
    }

    /** PUT /teams/{id}/name — 팀명 수정 (Q-03, Q-03a). */
    @Transactional
    public RenameTeamResponse rename(Long teamId, User user, RenameTeamRequest request) {
        Team team = teamAccessChecker.getTeam(teamId);
        Participant me = teamAccessChecker.requireMember(team, user.getUserKey());

        String requested = request == null || request.name() == null ? "" : request.name().trim();
        if (!requested.isEmpty() && teamRepository.existsByRoom_IdAndNameIgnoreCaseAndIdNot(team.getRoomId(), requested, teamId)) {
            throw new IcelinkException(ErrorCode.TEAM_NAME_DUPLICATED, "같은 방의 다른 팀이 이미 쓰는 팀명입니다.");
        }
        team.rename(requested);

        Instant now = Instant.now(clock);
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), teamId, RoomEventType.TEAM_NAME_CHANGED, Map.of(
                "teamId", teamId,
                "teamNo", team.getTeamNo(),
                "name", team.getName(),
                "isDefaultName", team.isDefaultName(),
                "updatedBy", Map.of("participantId", me.getId(), "nickname", me.getNickname())
        ), now));
        return new RenameTeamResponse(teamId, team.getTeamNo(), team.getName(), team.isDefaultName(),
                new RenameTeamResponse.Updater(me.getId(), me.getNickname()));
    }

    /** GET /teams/{id}/questions */
    @Transactional(readOnly = true)
    public List<TeamQuestionResponse> listQuestions(Long teamId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        List<TeamQuestion> questions = questionRepository.findAllByTeam_IdOrderByOrderNoAsc(teamId);
        Map<Long, TeamAnswer> answers = answerRepository.findAllByQuestionIdIn(questions.stream().map(TeamQuestion::getId).toList())
                .stream().collect(Collectors.toMap(TeamAnswer::getQuestionId, Function.identity()));
        return questions.stream().map(q -> TeamQuestionResponse.from(q, answers.get(q.getId()))).toList();
    }

    /** GET /teams/{id}/questions/current — 세션 미시작이면 404 */
    @Transactional(readOnly = true)
    public TeamQuestionResponse getCurrent(Long teamId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        return currentQuestion(teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.QUESTION_NOT_FOUND, "아직 세션이 시작되지 않았습니다."));
    }

    /** GET /teams/{id}/questions/{qid} */
    @Transactional(readOnly = true)
    public TeamQuestionResponse getQuestion(Long teamId, Long questionId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        TeamQuestion q = questionRepository.findByIdAndTeam_Id(questionId, teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.QUESTION_NOT_FOUND, "이 팀에 없는 질문입니다."));
        return TeamQuestionResponse.from(q, answerRepository.findByQuestionId(questionId).orElse(null));
    }

    /**
     * POST /teams/{id}/questions/next — NAMING → QUESTIONING 진입, 또는 현재 질문 건너뛰기 (Q-09).
     * AI 생성은 커밋 후 비동기로 시작한다.
     */
    @Transactional
    public NextQuestionResponse next(Long teamId, User user, NextQuestionRequest request) {
        Team team = lockTeam(teamId);
        teamAccessChecker.requireMember(team, user.getUserKey());
        requireRoomInProgress(team);
        if (team.hasReachedQuestionLimit()) {
            throw new IcelinkException(ErrorCode.QUESTION_LIMIT_EXCEEDED,
                    "이 팀의 질문이 상한(" + Team.QUESTION_LIMIT + "개)에 도달했습니다. 주최자의 종료를 기다려 주세요.");
        }
        TeamQuestion current = questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "먼저 세션을 시작해 주세요."));

        Long skippedId = null;
        if (team.getStatus() == TeamStatus.NAMING) {
            current.completeIntro();
            team.beginQuestioning();
        } else if (team.getStatus() == TeamStatus.QUESTIONING) {
            if (current.getStatus() == QuestionStatus.PROCESSING) {
                throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "답변이 처리 중입니다. 잠시 후 다음 질문이 도착합니다.");
            }
            if (current.getStatus() != QuestionStatus.ANSWERING) {
                throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "이미 다음 질문을 생성하고 있습니다.");
            }
            current.skip();
            skippedId = current.getId();
        } else {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "다음 질문을 요청할 수 없는 상태입니다. (현재 상태: " + team.getStatus() + ")");
        }

        Instant now = Instant.now(clock);
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), teamId, RoomEventType.QUESTION_GENERATING, Map.of(
                "teamId", teamId,
                "orderNo", team.getQuestionCount() + 1
        ), now));
        publishTeamStatus(team, now);
        afterCommit(() -> generationService.generateFirstQuestionAsync(teamId));
        return new NextQuestionResponse(team.getStatus(), true, skippedId, team.getQuestionCount());
    }

    /** POST /teams/{id}/questions/{qid}/answer — 대화 텍스트 제출 (Q-05, Q-15). */
    @Transactional
    public SubmitAnswerResponse submitAnswer(Long teamId, Long questionId, User user, SubmitAnswerRequest request) {
        Team team = lockTeam(teamId);
        Participant me = teamAccessChecker.requireMember(team, user.getUserKey());
        requireRoomInProgress(team);
        if (team.getStatus() != TeamStatus.QUESTIONING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "답변은 질문 단계(QUESTIONING)에서만 제출할 수 있습니다. (현재 상태: " + team.getStatus() + ")");
        }
        TeamQuestion question = questionRepository.findByIdAndTeam_Id(questionId, teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.QUESTION_NOT_FOUND, "이 팀에 없는 질문입니다."));
        TeamQuestion current = questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(teamId).orElse(null);
        if (current == null || !current.getId().equals(questionId)) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "현재 질문이 아닙니다.");
        }

        String text = request.answerText().trim();
        if (text.isEmpty()) {
            throw new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                    Map.of("errors", List.of(Map.of("field", "answerText", "message", "답변은 공백 제외 1자 이상이어야 합니다"))));
        }

        Instant now = Instant.now(clock);
        question.startProcessing();
        try {
            answerRepository.saveAndFlush(TeamAnswer.submit(question, text, me, request.speechDurationSec(), now));
        } catch (OptimisticLockingFailureException e) {
            throw new IcelinkException(ErrorCode.ANSWER_ALREADY_SUBMITTED, "다른 팀원이 먼저 답변을 제출했습니다. 처리 중입니다.");
        }
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), teamId, RoomEventType.ANSWER_PROCESSING, Map.of(
                "teamId", teamId,
                "questionId", questionId,
                "submittedBy", Map.of("participantId", me.getId(), "nickname", me.getNickname())
        ), now));
        afterCommit(() -> generationService.processAnswerAsync(questionId, false));
        return new SubmitAnswerResponse(questionId, QuestionStatus.PROCESSING.name(), ESTIMATED_SECONDS,
                !team.hasReachedQuestionLimit());
    }

    /** POST /teams/{id}/questions/{qid}/retry — FAILED 재처리. 실패하면 폴백 질문으로 이어간다. */
    @Transactional
    public SubmitAnswerResponse retry(Long teamId, Long questionId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMember(team, user.getUserKey());
        TeamQuestion question = questionRepository.findByIdAndTeam_Id(questionId, teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.QUESTION_NOT_FOUND, "이 팀에 없는 질문입니다."));
        if (answerRepository.findByQuestionId(questionId).isEmpty()) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "재시도할 답변이 없습니다.");
        }
        question.retryProcessing();
        Instant now = Instant.now(clock);
        eventPublisher.publish(RoomEvent.team(team.getRoomId(), teamId, RoomEventType.ANSWER_PROCESSING, Map.of(
                "teamId", teamId,
                "questionId", questionId,
                "retry", true
        ), now));
        afterCommit(() -> generationService.processAnswerAsync(questionId, true));
        return new SubmitAnswerResponse(questionId, QuestionStatus.PROCESSING.name(), ESTIMATED_SECONDS,
                !team.hasReachedQuestionLimit());
    }

    /** GET /teams/{id}/summary — 방 종료 후 요약 */
    @Transactional(readOnly = true)
    public TeamSummaryResponse summary(Long teamId, User user) {
        Team team = teamAccessChecker.getTeam(teamId);
        teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        Room room = team.getRoom();
        if (room.getStatus() != RoomStatus.FINISHED) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "요약은 방이 종료된 뒤에 볼 수 있습니다.");
        }
        List<TeamQuestion> questions = questionRepository.findAllByTeam_IdOrderByOrderNoAsc(teamId);
        Map<Long, TeamAnswer> answers = answerRepository.findAllByQuestion_Team_IdOrderByQuestion_OrderNoAsc(teamId)
                .stream().collect(Collectors.toMap(TeamAnswer::getQuestionId, Function.identity()));

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        List<TeamSummaryResponse.Highlight> highlights = new ArrayList<>();
        int answered = 0;
        for (TeamQuestion q : questions) {
            TeamAnswer a = answers.get(q.getId());
            if (a != null && a.isProcessed()) {
                answered++;
                keywords.addAll(a.getKeywords());
                highlights.add(new TeamSummaryResponse.Highlight(q.getContent(), a.getKeywords()));
            }
        }
        Long durationSec = team.getStartedAt() == null || team.getFinishedAt() == null ? null
                : Duration.between(team.getStartedAt(), team.getFinishedAt()).toSeconds();
        List<TeamMemberView> members = participantRepository.findAllByTeam_IdOrderByIdAsc(teamId).stream()
                .map(TeamMemberView::minimal).toList();
        return new TeamSummaryResponse(teamId, team.getTeamNo(), team.getName(), members, team.getCategory(),
                room.getFinalQuestions(), team.getQuestionCount(), answered, durationSec, List.copyOf(keywords),
                highlights, team.getStartedAt(), team.getFinishedAt());
    }

    // ---- 내부 ----

    Optional<TeamQuestionResponse> currentQuestion(Long teamId) {
        return questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(teamId)
                .map(q -> TeamQuestionResponse.from(q, answerRepository.findByQuestionId(q.getId()).orElse(null)));
    }

    private static void requireRoomInProgress(Team team) {
        if (team.getRoom().getStatus() != RoomStatus.IN_PROGRESS || team.isFinished()) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "진행 중인 방의 팀에서만 가능합니다. (방: " + team.getRoom().getStatus() + ", 팀: " + team.getStatus() + ")");
        }
    }

    /**
     * 상태를 바꾸는 진입점(start/next/answer)은 팀 행을 잠그고 시작한다.
     * 같은 팀의 다른 팀원 요청은 앞 트랜잭션이 끝날 때까지 기다린 뒤 최신 상태를 읽으므로,
     * 두 번째 start 는 멱등(200), 두 번째 next/answer 는 409 로 정리된다 (500 아님).
     */
    private Team lockTeam(Long teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.TEAM_NOT_FOUND, "존재하지 않는 팀입니다."));
    }

    private void publishTeamStatus(Team team, Instant now) {
        eventPublisher.publish(RoomEvent.room(team.getRoomId(), RoomEventType.TEAM_STATUS_CHANGED, Map.of(
                "teamId", team.getId(),
                "teamNo", team.getTeamNo(),
                "status", team.getStatus().name(),
                "questionCount", team.getQuestionCount()
        ), now));
    }

    private static Map<String, Object> questionPayload(TeamQuestion q) {
        return Map.of("questionId", q.getId(), "orderNo", q.getOrderNo(), "type", q.getType().name(),
                "content", q.getContent(), "status", q.getStatus().name());
    }

    /** 트랜잭션 커밋 후에 비동기 작업을 시작한다 (커밋 전 시작하면 스냅샷이 옛 상태를 읽음). */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
