package com.kosscchthon.Icelink.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionContext;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpContext;
import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 네트워크 없이 검증 가능한 부분: 응답 정리·JSON 추출·프롬프트 조립. */
class KookminGatewayAiClientTest {

    @Test
    void cleanQuestion_stripsQuotesNumberingPrefixAndTrims() {
        assertThat(KookminGatewayAiClient.cleanQuestion("  \"최근 본 영화는?\"  ")).isEqualTo("최근 본 영화는?");
        assertThat(KookminGatewayAiClient.cleanQuestion("1. 최근 본 영화는?")).isEqualTo("최근 본 영화는?");
        assertThat(KookminGatewayAiClient.cleanQuestion("질문: 최근 본 영화는?")).isEqualTo("최근 본 영화는?");
        assertThat(KookminGatewayAiClient.cleanQuestion("“한글 따옴표”")).isEqualTo("한글 따옴표");
        assertThat(KookminGatewayAiClient.cleanQuestion(null)).isEmpty();
    }

    @Test
    void cleanQuestion_capsAt200Chars() {
        String long300 = "가".repeat(300);
        assertThat(KookminGatewayAiClient.cleanQuestion(long300)).hasSize(200);
    }

    @Test
    void extractJsonObject_takesFirstBraceToLastBrace() {
        String noisy = "물론이죠! 결과입니다:\n{\"keywords\":[\"a\"],\"nextQuestion\":\"q?\"}\n도움이 되셨길.";
        assertThat(KookminGatewayAiClient.extractJsonObject(noisy)).isEqualTo("{\"keywords\":[\"a\"],\"nextQuestion\":\"q?\"}");
        assertThat(KookminGatewayAiClient.extractJsonObject("no json here")).isEqualTo("no json here");
        assertThat(KookminGatewayAiClient.extractJsonObject(null)).isEmpty();
    }

    @Test
    void firstQuestionPrompt_containsSituationCategoryLabelAndMemberCount() {
        String p = PromptTemplates.firstQuestion(new FirstQuestionContext("해커톤 참가자", InterestCategory.GAME, 4));

        assertThat(p).contains("방의 주제: 해커톤 참가자")
                .contains("팀의 관심사: 게임")
                .contains("팀원 수: 4명")
                .contains("게임 카테고리와 직접 관련")
                .contains("질문만 반환");
    }

    @Test
    void followUpPrompt_containsDialogueKeywordsPreviousQuestionsAndJsonInstruction() {
        String p = PromptTemplates.followUp(new FollowUpContext("해커톤", InterestCategory.FOOD, 3,
                "최근 먹은 최고의 음식은?", "민수: 떡볶이요", List.of("롤", "밤샘"), List.of("q1", "q2")));

        assertThat(p).contains("이전 질문: 최근 먹은 최고의 음식은?")
                .contains("팀의 대화 내용 (팀원 한 명의 기기로 녹음한 음성 인식 텍스트")
                .contains("떡볶이요")
                .contains("팀원 수: 3명")
                .contains("특정 사람을 지목하거나 이름을 부르지 말고")
                .contains("지금까지 나온 키워드: 롤, 밤샘")
                .contains("1) q1").contains("2) q2")
                .contains("{\"keywords\":[...],\"nextQuestion\":\"...\"}");
    }

    @Test
    void followUpPrompt_showsNoneWhenListsEmpty() {
        String p = PromptTemplates.followUp(new FollowUpContext("s", InterestCategory.MOVIE, 3, "q", "a", List.of(), List.of()));
        assertThat(p).contains("지금까지 나온 키워드: (없음)").contains("이전에 이미 던진 질문들: (없음)");
    }
}
