package com.kosscchthon.Icelink.team.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.survey.SurveyTestFixtures;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamAccessChecker;
import com.kosscchthon.Icelink.team.TeamRepository;
import com.kosscchthon.Icelink.team.TeamStatus;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.NextQuestionResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.RenameTeamRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.StartTeamResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerRequest;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.SubmitAnswerResponse;
import com.kosscchthon.Icelink.team.session.dto.SessionDtos.TeamSummaryResponse;
import com.kosscchthon.Icelink.user.User;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);
    private static final User MEMBER = User.register("b".repeat(64), "민수", NOW);
    private static final User OTHER = User.register("c".repeat(64), "지현", NOW);

    @Mock TeamRepository teamRepository;
    @Mock TeamQuestionRepository questionRepository;
    @Mock TeamAnswerRepository answerRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock QuestionGenerationService generationService;
    @Mock RoomEventPublisher eventPublisher;

    private TeamSessionService service;
    private Room room;
    private Team team;
    private Participant member;
    private final AtomicLong ids = new AtomicLong(9000);

    @BeforeEach
    void setUp() throws Exception {
        service = new TeamSessionService(new TeamAccessChecker(teamRepository, participantRepository), teamRepository,
                questionRepository, answerRepository, participantRepository, generationService, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        room = Room.create("K7M3PQ", HOST, "해커톤", "s", 4, List.of("마무리?"), NOW.minusSeconds(600), Duration.ofHours(24));
        setId(room, Room.class, 12L);
        room.startTeamBuilding(NOW);
        room.completeTeamBuilding(NOW);

        team = Team.create(room, 1, InterestCategory.GAME, false, 18.5);
        setId(team, Team.class, 501L);

        member = Participant.join(room, MEMBER, "민수", NOW);
        setId(member, Participant.class, 101L);
        member.submitPersonality(SurveyTestFixtures.uniform(3));
        member.selectCategory(InterestCategory.GAME);
        member.assignTo(team);

        lenient().when(teamRepository.findById(501L)).thenReturn(Optional.of(team));
        lenient().when(teamRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(team));
        lenient().when(participantRepository.findByRoom_IdAndUser_UserKey(12L, MEMBER.getUserKey())).thenReturn(Optional.of(member));
        lenient().when(participantRepository.findByRoom_IdAndUser_UserKey(12L, OTHER.getUserKey())).thenReturn(Optional.empty());
        lenient().when(participantRepository.findByRoom_IdAndUser_UserKey(12L, HOST.getUserKey())).thenReturn(Optional.empty());
        lenient().when(questionRepository.save(any(TeamQuestion.class))).thenAnswer(inv -> {
            TeamQuestion q = inv.getArgument(0);
            setId(q, TeamQuestion.class, ids.incrementAndGet());
            return q;
        });
        lenient().when(answerRepository.saveAndFlush(any(TeamAnswer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(answerRepository.findByQuestionId(any())).thenReturn(Optional.empty());
    }

    private static void setId(Object entity, Class<?> type, long id) throws Exception {
        Field f = type.getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private TeamQuestion question(QuestionType type, int orderNo) throws Exception {
        TeamQuestion q = TeamQuestion.create(team, orderNo, type, "q" + orderNo, null, NOW);
        setId(q, TeamQuestion.class, ids.incrementAndGet());
        return q;
    }

    private List<RoomEventType> publishedTypes() {
        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream().map(RoomEvent::type).toList();
    }

    @Nested
    class Start {

        @Test
        void start_createsIntroQuestionWithTeamName_andPublishes() {
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L))
                    .thenAnswer(inv -> Optional.of(TeamQuestion.create(team, 1, QuestionType.INTRO, "intro", null, NOW)));

            StartTeamResponse res = service.start(501L, MEMBER);

            assertThat(res.started()).isTrue();
            assertThat(res.status()).isEqualTo(TeamStatus.NAMING);
            assertThat(team.getQuestionCount()).isEqualTo(1);
            ArgumentCaptor<TeamQuestion> saved = ArgumentCaptor.forClass(TeamQuestion.class);
            verify(questionRepository).save(saved.capture());
            assertThat(saved.getValue().getType()).isEqualTo(QuestionType.INTRO);
            assertThat(saved.getValue().getContent()).contains("'1팀' 대신");
            assertThat(publishedTypes()).contains(RoomEventType.TEAM_STARTED, RoomEventType.TEAM_STATUS_CHANGED);
        }

        @Test
        void start_isIdempotent() throws Exception {
            team.start(NOW.minusSeconds(60));
            team.incrementQuestionCount();
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.of(question(QuestionType.INTRO, 1)));

            StartTeamResponse res = service.start(501L, MEMBER);

            assertThat(res.started()).isFalse();
            assertThat(res.currentQuestion().type()).isEqualTo(QuestionType.INTRO);
            verify(questionRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        void start_byHost_isForbidden_andByStrangerForbidden() {
            assertThatThrownBy(() -> service.start(501L, HOST))
                    .isInstanceOfSatisfying(IcelinkException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> service.start(501L, OTHER))
                    .isInstanceOfSatisfying(IcelinkException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        void start_rejectedWhenRoomFinished() {
            room.finish(NOW);

            assertThatThrownBy(() -> service.start(501L, MEMBER))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Rename {

        @Test
        void rename_setsNameAndPublishes() {
            when(teamRepository.existsByRoom_IdAndNameIgnoreCaseAndIdNot(12L, "감자전사", 501L)).thenReturn(false);

            var res = service.rename(501L, MEMBER, new RenameTeamRequest(" 감자전사 "));

            assertThat(res.name()).isEqualTo("감자전사");
            assertThat(res.isDefaultName()).isFalse();
            assertThat(res.updatedBy().nickname()).isEqualTo("민수");
            assertThat(publishedTypes()).containsExactly(RoomEventType.TEAM_NAME_CHANGED);
        }

        @Test
        void rename_blankRestoresDefault() {
            team.rename("감자전사");

            var res = service.rename(501L, MEMBER, new RenameTeamRequest(""));

            assertThat(res.name()).isEqualTo("1팀");
            assertThat(res.isDefaultName()).isTrue();
        }

        @Test
        void rename_duplicateInRoom_is409() {
            when(teamRepository.existsByRoom_IdAndNameIgnoreCaseAndIdNot(12L, "감자전사", 501L)).thenReturn(true);

            assertThatThrownBy(() -> service.rename(501L, MEMBER, new RenameTeamRequest("감자전사")))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.TEAM_NAME_DUPLICATED));
        }

        @Test
        void rename_afterFinish_is409() {
            team.finish(NOW);

            assertThatThrownBy(() -> service.rename(501L, MEMBER, new RenameTeamRequest("x")))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Next {

        @Test
        void next_fromNaming_completesIntro_beginsQuestioning_andTriggersGeneration() throws Exception {
            team.start(NOW);
            team.incrementQuestionCount();
            TeamQuestion intro = question(QuestionType.INTRO, 1);
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.of(intro));

            NextQuestionResponse res = service.next(501L, MEMBER, null);

            assertThat(res.status()).isEqualTo(TeamStatus.QUESTIONING);
            assertThat(res.generating()).isTrue();
            assertThat(res.skippedQuestionId()).isNull();
            assertThat(intro.getStatus()).isEqualTo(QuestionStatus.DONE);
            verify(generationService).generateFirstQuestionAsync(501L); // 테스트에선 트랜잭션이 없어 즉시 실행
            assertThat(publishedTypes()).contains(RoomEventType.QUESTION_GENERATING);
        }

        @Test
        void next_inQuestioning_skipsCurrentAnsweringQuestion() throws Exception {
            team.start(NOW);
            team.beginQuestioning();
            team.incrementQuestionCount();
            team.incrementQuestionCount();
            TeamQuestion current = question(QuestionType.AI_GENERATED, 2);
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.of(current));

            NextQuestionResponse res = service.next(501L, MEMBER, null);

            assertThat(res.skippedQuestionId()).isEqualTo(current.getId());
            assertThat(current.getStatus()).isEqualTo(QuestionStatus.SKIPPED);
            verify(generationService).generateFirstQuestionAsync(501L);
        }

        @Test
        void next_rejectedWhileProcessing_orWhileGenerating() throws Exception {
            team.start(NOW);
            team.beginQuestioning();
            TeamQuestion current = question(QuestionType.AI_GENERATED, 2);
            current.startProcessing();
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.of(current));

            assertThatThrownBy(() -> service.next(501L, MEMBER, null))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

            current.markDone(); // 생성 중 (열린 질문 없음)
            assertThatThrownBy(() -> service.next(501L, MEMBER, null))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            verify(generationService, never()).generateFirstQuestionAsync(any());
        }

        @Test
        void next_atQuestionLimit_is409() {
            team.start(NOW);
            team.beginQuestioning();
            for (int i = 0; i < Team.QUESTION_LIMIT; i++) {
                team.incrementQuestionCount();
            }

            assertThatThrownBy(() -> service.next(501L, MEMBER, null))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.QUESTION_LIMIT_EXCEEDED));
        }

        @Test
        void next_beforeStart_is409() {
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.next(501L, MEMBER, null))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Answer {

        private TeamQuestion current;

        @BeforeEach
        void questioning() throws Exception {
            team.start(NOW);
            team.beginQuestioning();
            team.incrementQuestionCount();
            team.incrementQuestionCount();
            current = question(QuestionType.AI_GENERATED, 2);
            lenient().when(questionRepository.findByIdAndTeam_Id(current.getId(), 501L)).thenReturn(Optional.of(current));
            lenient().when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.of(current));
        }

        @Test
        void submit_movesToProcessing_savesAnswer_andTriggersAsync() {
            SubmitAnswerResponse res = service.submitAnswer(501L, current.getId(), MEMBER,
                    new SubmitAnswerRequest("  민수: 롤 다시 시작했어요 ", 40));

            assertThat(res.status()).isEqualTo("PROCESSING");
            assertThat(res.nextQuestionGenerated()).isTrue();
            assertThat(current.getStatus()).isEqualTo(QuestionStatus.PROCESSING);
            ArgumentCaptor<TeamAnswer> saved = ArgumentCaptor.forClass(TeamAnswer.class);
            verify(answerRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getAnswerText()).isEqualTo("민수: 롤 다시 시작했어요");
            assertThat(saved.getValue().getSubmittedBy()).isSameAs(member);
            verify(generationService).processAnswerAsync(current.getId(), false);
            assertThat(publishedTypes()).containsExactly(RoomEventType.ANSWER_PROCESSING);
        }

        @Test
        void submit_secondTime_isAlreadySubmitted() {
            service.submitAnswer(501L, current.getId(), MEMBER, new SubmitAnswerRequest("첫 답변입니다", null));

            assertThatThrownBy(() -> service.submitAnswer(501L, current.getId(), MEMBER, new SubmitAnswerRequest("두 번째", null)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ANSWER_ALREADY_SUBMITTED));
        }

        @Test
        void submit_toNonCurrentQuestion_is409() throws Exception {
            TeamQuestion old = question(QuestionType.AI_GENERATED, 1);
            when(questionRepository.findByIdAndTeam_Id(old.getId(), 501L)).thenReturn(Optional.of(old));

            assertThatThrownBy(() -> service.submitAnswer(501L, old.getId(), MEMBER, new SubmitAnswerRequest("답", null)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void submit_whenLimitReached_stillAcceptedButNoNextQuestion() {
            for (int i = team.getQuestionCount(); i < Team.QUESTION_LIMIT; i++) {
                team.incrementQuestionCount();
            }

            SubmitAnswerResponse res = service.submitAnswer(501L, current.getId(), MEMBER, new SubmitAnswerRequest("마지막 답변", null));

            assertThat(res.nextQuestionGenerated()).isFalse();
            verify(generationService).processAnswerAsync(current.getId(), false);
        }

        @Test
        void submit_byHost_isForbidden() {
            assertThatThrownBy(() -> service.submitAnswer(501L, current.getId(), HOST, new SubmitAnswerRequest("답", null)))
                    .isInstanceOfSatisfying(IcelinkException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        void retry_fromFailed_withAnswer_triggersRetryProcessing() throws Exception {
            current.startProcessing();
            current.markFailed(com.kosscchthon.Icelink.ai.AiFailureReason.LLM_TIMEOUT);
            when(answerRepository.findByQuestionId(current.getId()))
                    .thenReturn(Optional.of(TeamAnswer.submit(current, "답변 텍스트입니다", member, null, NOW)));

            SubmitAnswerResponse res = service.retry(501L, current.getId(), MEMBER);

            assertThat(res.status()).isEqualTo("PROCESSING");
            assertThat(current.getStatus()).isEqualTo(QuestionStatus.PROCESSING);
            verify(generationService).processAnswerAsync(current.getId(), true);
        }

        @Test
        void retry_withoutAnswer_is409() {
            current.startProcessing();
            current.markFailed(com.kosscchthon.Icelink.ai.AiFailureReason.LLM_ERROR);

            assertThatThrownBy(() -> service.retry(501L, current.getId(), MEMBER))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class ReadAndSummary {

        @Test
        void listQuestions_attachesAnswers_andHostMayRead() throws Exception {
            TeamQuestion q1 = question(QuestionType.INTRO, 1);
            TeamQuestion q2 = question(QuestionType.AI_GENERATED, 2);
            TeamAnswer a2 = TeamAnswer.submit(q2, "롤 다시 시작", member, 30, NOW);
            a2.applyKeywords(List.of("롤"), NOW);
            when(questionRepository.findAllByTeam_IdOrderByOrderNoAsc(501L)).thenReturn(List.of(q1, q2));
            when(answerRepository.findAllByQuestionIdIn(List.of(q1.getId(), q2.getId()))).thenReturn(List.of(a2));

            var res = service.listQuestions(501L, HOST);

            assertThat(res).hasSize(2);
            assertThat(res.get(0).answer()).isNull();
            assertThat(res.get(1).answer().keywords()).containsExactly("롤");
            assertThat(res.get(1).answer().submittedBy().nickname()).isEqualTo("민수");
        }

        @Test
        void current_beforeStart_is404() {
            when(questionRepository.findFirstByTeam_IdOrderByOrderNoDesc(501L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrent(501L, MEMBER))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.QUESTION_NOT_FOUND));
        }

        @Test
        void summary_requiresFinishedRoom_thenAggregatesKeywordsAndHighlights() throws Exception {
            assertThatThrownBy(() -> service.summary(501L, MEMBER))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

            team.start(NOW.minusSeconds(300));
            room.finish(NOW);
            team.finish(NOW);
            team.incrementQuestionCount();
            team.incrementQuestionCount();
            TeamQuestion q1 = question(QuestionType.INTRO, 1);
            TeamQuestion q2 = question(QuestionType.AI_GENERATED, 2);
            TeamAnswer a2 = TeamAnswer.submit(q2, "롤, 젤다 얘기", member, 30, NOW);
            a2.applyKeywords(List.of("롤", "젤다"), NOW);
            when(questionRepository.findAllByTeam_IdOrderByOrderNoAsc(501L)).thenReturn(List.of(q1, q2));
            when(answerRepository.findAllByQuestion_Team_IdOrderByQuestion_OrderNoAsc(501L)).thenReturn(List.of(a2));
            when(participantRepository.findAllByTeam_IdOrderByIdAsc(501L)).thenReturn(List.of(member));

            TeamSummaryResponse res = service.summary(501L, MEMBER);

            assertThat(res.finalQuestions()).containsExactly("마무리?");
            assertThat(res.questionCount()).isEqualTo(2);
            assertThat(res.answeredCount()).isEqualTo(1);
            assertThat(res.keywords()).containsExactly("롤", "젤다");
            assertThat(res.highlights()).hasSize(1);
            assertThat(res.durationSec()).isEqualTo(300L);
            assertThat(res.members()).extracting("nickname").containsExactly("민수");
        }
    }
}
