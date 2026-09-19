package com.kosscchthon.Icelink.team.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamQuestionTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final User HOST = User.register("a".repeat(64), "호스트", NOW);
    private static final Room ROOM = Room.create("K7M3PQ", HOST, "t", "s", 4, List.of(), NOW, Duration.ofHours(24));
    private static final Team TEAM = Team.create(ROOM, 1, InterestCategory.GAME, false, 18);

    @Test
    void answerLifecycle_answering_processing_done() {
        TeamQuestion q = TeamQuestion.create(TEAM, 2, QuestionType.AI_GENERATED, "q?", null, NOW);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.ANSWERING);

        q.startProcessing();
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.PROCESSING);

        assertThatThrownBy(q::startProcessing)
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ANSWER_ALREADY_SUBMITTED));

        q.markDone();
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.DONE);
        assertThat(q.getStatus().isClosed()).isTrue();
        assertThatThrownBy(q::startProcessing)
                .isInstanceOfSatisfying(IcelinkException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void failedThenRetry() {
        TeamQuestion q = TeamQuestion.create(TEAM, 2, QuestionType.AI_GENERATED, "q?", null, NOW);
        q.startProcessing();
        q.markFailed(AiFailureReason.LLM_TIMEOUT);
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.FAILED);
        assertThat(q.getFailureReason()).isEqualTo(AiFailureReason.LLM_TIMEOUT);

        q.retryProcessing();
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.PROCESSING);
        assertThat(q.getFailureReason()).isNull();

        assertThatThrownBy(q::retryProcessing).isInstanceOf(IcelinkException.class);
    }

    @Test
    void skip_onlyFromAnswering_andIntroCompletion() {
        TeamQuestion q = TeamQuestion.create(TEAM, 2, QuestionType.AI_GENERATED, "q?", null, NOW);
        q.skip();
        assertThat(q.getStatus()).isEqualTo(QuestionStatus.SKIPPED);
        assertThatThrownBy(q::skip).isInstanceOf(IcelinkException.class);

        TeamQuestion intro = TeamQuestion.create(TEAM, 1, QuestionType.INTRO, "intro", null, NOW);
        intro.completeIntro();
        assertThat(intro.getStatus()).isEqualTo(QuestionStatus.DONE);
        assertThatThrownBy(q::completeIntro).isInstanceOf(IcelinkException.class); // not INTRO
    }

    @Test
    void answer_shortTextDetection_andKeywords() {
        TeamQuestion q = TeamQuestion.create(TEAM, 2, QuestionType.AI_GENERATED, "q?", null, NOW);
        Participant p = Participant.join(ROOM, User.register("b".repeat(64), "민수", NOW), "민수", NOW);

        TeamAnswer shortAnswer = TeamAnswer.submit(q, "네 좋아요", p, 5, NOW);
        assertThat(shortAnswer.isTooShort()).isTrue();
        assertThat(shortAnswer.isProcessed()).isFalse();
        assertThat(shortAnswer.getKeywords()).isEmpty();

        TeamAnswer full = TeamAnswer.submit(q, "민수: 저는 롤을 다시 시작했어요. 지현: 젤다 하다가 밤새웠어요.", p, 40, NOW);
        assertThat(full.isTooShort()).isFalse();
        full.applyKeywords(List.of("롤", "젤다"), NOW.plusSeconds(5));
        assertThat(full.getKeywords()).containsExactly("롤", "젤다");
        assertThat(full.isProcessed()).isTrue();
    }
}
