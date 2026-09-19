package com.kosscchthon.Icelink.team.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.ai.AiClientException;
import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.ai.FallbackQuestionPool;
import com.kosscchthon.Icelink.ai.QuestionAiClient;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionContext;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionResult;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpContext;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpResult;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.session.QuestionGenerationStore.FirstQuestionSnapshot;
import com.kosscchthon.Icelink.team.session.QuestionGenerationStore.FollowUpSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionGenerationServiceTest {

    @Mock QuestionAiClient aiClient;
    @Mock QuestionGenerationStore store;

    private QuestionGenerationService service;

    private static final FirstQuestionContext FIRST_CTX = new FirstQuestionContext("해커톤", InterestCategory.GAME, 3);
    private static final FollowUpContext FOLLOW_CTX = new FollowUpContext("해커톤", InterestCategory.GAME, 3,
            "최근 몰입한 게임은?", "민수: 롤 다시 시작했어요. 지현: 젤다 밤샘.", List.of(), List.of("intro", "최근 몰입한 게임은?"));

    @BeforeEach
    void setUp() {
        service = new QuestionGenerationService(aiClient, new FallbackQuestionPool(new Random(1)), store);
    }

    @Test
    void firstQuestion_savesAiResultAsAiGenerated() {
        when(store.loadFirstQuestionSnapshot(501L)).thenReturn(Optional.of(new FirstQuestionSnapshot(501L, FIRST_CTX, List.of("intro"))));
        when(aiClient.generateFirstQuestion(FIRST_CTX)).thenReturn(new FirstQuestionResult("인생 게임은 무엇인가요?", 120));

        service.generateFirstQuestionAsync(501L);

        verify(store).saveGeneratedQuestion(eq(501L), eq("인생 게임은 무엇인가요?"), eq(QuestionType.AI_GENERATED), anyMap());
    }

    @Test
    void firstQuestion_fallsBackWhenAiFails() {
        when(store.loadFirstQuestionSnapshot(501L)).thenReturn(Optional.of(new FirstQuestionSnapshot(501L, FIRST_CTX, List.of())));
        when(aiClient.generateFirstQuestion(FIRST_CTX)).thenThrow(new AiClientException(AiFailureReason.LLM_TIMEOUT, "slow"));

        service.generateFirstQuestionAsync(501L);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(store).saveGeneratedQuestion(eq(501L), content.capture(), eq(QuestionType.FALLBACK), anyMap());
        assertThat(content.getValue()).isNotBlank();
    }

    @Test
    void firstQuestion_fallsBackWhenAiRepeatsExistingQuestion() {
        when(store.loadFirstQuestionSnapshot(501L)).thenReturn(Optional.of(new FirstQuestionSnapshot(501L, FIRST_CTX, List.of("인생 게임은 무엇인가요?"))));
        when(aiClient.generateFirstQuestion(FIRST_CTX)).thenReturn(new FirstQuestionResult("인생 게임은  무엇인가요?", 100));

        service.generateFirstQuestionAsync(501L);

        verify(store).saveGeneratedQuestion(eq(501L), any(), eq(QuestionType.FALLBACK), anyMap());
    }

    @Test
    void firstQuestion_skipsWhenTeamNotEligible() {
        when(store.loadFirstQuestionSnapshot(501L)).thenReturn(Optional.empty());

        service.generateFirstQuestionAsync(501L);

        verify(aiClient, never()).generateFirstQuestion(any());
        verify(store, never()).saveGeneratedQuestion(any(), any(), any(), any());
    }

    @Test
    void answer_success_savesKeywordsAndNextQuestion() {
        when(store.loadFollowUpSnapshot(9001L)).thenReturn(Optional.of(new FollowUpSnapshot(501L, 9001L, FOLLOW_CTX, 3, false, FOLLOW_CTX.previousQuestions())));
        when(aiClient.generateFollowUp(FOLLOW_CTX)).thenReturn(new FollowUpResult(List.of("롤", "젤다", "밤샘"), "젤다 밤샘처럼 빠져든 순간은?", 900));

        service.processAnswerAsync(9001L, false);

        verify(store).completeAnswer(eq(9001L), eq(List.of("롤", "젤다", "밤샘")), eq("젤다 밤샘처럼 빠져든 순간은?"),
                eq(QuestionType.AI_GENERATED), anyMap());
        verify(store, never()).markFailed(any(), any());
    }

    @Test
    void answer_aiFailure_firstAttempt_marksFailed() {
        when(store.loadFollowUpSnapshot(9001L)).thenReturn(Optional.of(new FollowUpSnapshot(501L, 9001L, FOLLOW_CTX, 3, false, List.of())));
        when(aiClient.generateFollowUp(FOLLOW_CTX)).thenThrow(new AiClientException(AiFailureReason.LLM_INVALID_RESPONSE, "bad json"));

        service.processAnswerAsync(9001L, false);

        verify(store).markFailed(9001L, AiFailureReason.LLM_INVALID_RESPONSE);
        verify(store, never()).completeAnswer(any(), anyList(), any(), any(), any());
    }

    @Test
    void answer_aiFailure_onRetry_completesWithFallbackQuestion() {
        when(store.loadFollowUpSnapshot(9001L)).thenReturn(Optional.of(new FollowUpSnapshot(501L, 9001L, FOLLOW_CTX, 3, false, List.of())));
        when(aiClient.generateFollowUp(FOLLOW_CTX)).thenThrow(new AiClientException(AiFailureReason.LLM_ERROR, "500"));

        service.processAnswerAsync(9001L, true);

        verify(store).completeAnswer(eq(9001L), eq(List.of()), any(String.class), eq(QuestionType.FALLBACK), anyMap());
        verify(store, never()).markFailed(any(), any());
    }

    @Test
    void answer_tooShort_usesFirstQuestionPromptInstead() {
        FollowUpContext shortCtx = new FollowUpContext("해커톤", InterestCategory.GAME, 3, "q", "네", List.of(), List.of("q"));
        when(store.loadFollowUpSnapshot(9001L)).thenReturn(Optional.of(new FollowUpSnapshot(501L, 9001L, shortCtx, 3, true, List.of("q"))));
        when(aiClient.generateFirstQuestion(shortCtx.asFirstQuestion())).thenReturn(new FirstQuestionResult("새 일반 질문?", 80));

        service.processAnswerAsync(9001L, false);

        verify(aiClient, never()).generateFollowUp(any());
        verify(store).completeAnswer(eq(9001L), eq(List.of()), eq("새 일반 질문?"), eq(QuestionType.AI_GENERATED), anyMap());
    }

    @Test
    void answer_duplicateNextQuestion_replacedByFallback() {
        when(store.loadFollowUpSnapshot(9001L)).thenReturn(Optional.of(new FollowUpSnapshot(501L, 9001L, FOLLOW_CTX, 3, false, FOLLOW_CTX.previousQuestions())));
        when(aiClient.generateFollowUp(FOLLOW_CTX)).thenReturn(new FollowUpResult(List.of("롤"), "최근 몰입한 게임은?", 500));

        service.processAnswerAsync(9001L, false);

        verify(store).completeAnswer(eq(9001L), eq(List.of("롤")), any(String.class), eq(QuestionType.FALLBACK), anyMap());
    }

    @Test
    void isUsable_rules() {
        assertThat(QuestionGenerationService.isUsable("좋은 질문?", List.of())).isTrue();
        assertThat(QuestionGenerationService.isUsable(null, List.of())).isFalse();
        assertThat(QuestionGenerationService.isUsable("   ", List.of())).isFalse();
        assertThat(QuestionGenerationService.isUsable("가".repeat(201), List.of())).isFalse();
        assertThat(QuestionGenerationService.isUsable("같은 질문", List.of("같은  질문"))).isFalse();
    }
}
