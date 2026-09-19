package com.kosscchthon.Icelink.ai;

import com.kosscchthon.Icelink.ai.QuestionAiClient.FirstQuestionContext;
import com.kosscchthon.Icelink.ai.QuestionAiClient.FollowUpContext;
import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.List;
import java.util.Map;

/** 두 프롬프트의 본문. docs/02-api-spec.md 4.1 / 4.2 절과 동일하게 유지한다. */
public final class PromptTemplates {

    public static final int QUESTION_MAX_LENGTH = 200;

    private static final Map<InterestCategory, String> CATEGORY_LABEL = Map.of(
            InterestCategory.MOVIE, "영화",
            InterestCategory.GAME, "게임",
            InterestCategory.FOOD, "음식",
            InterestCategory.TRAVEL, "여행",
            InterestCategory.SPORTS, "스포츠");

    private PromptTemplates() {
    }

    public static String label(InterestCategory category) {
        return CATEGORY_LABEL.getOrDefault(category, category.name());
    }

    /** 프롬프트 ① — 카테고리 기반 첫 질문 (출력: 질문 평문) */
    public static String firstQuestion(FirstQuestionContext ctx) {
        String category = label(ctx.category());
        return """
                당신은 팀 아이스브레이킹을 돕는 AI 진행자입니다.

                [상황]
                방의 주제: %s
                팀의 관심사: %s
                팀원 수: %d명

                [지시사항]
                1. 위 정보를 바탕으로 팀원 모두가 함께 이야기할 수 있는 대화 주제 질문 1개를 생성하세요.
                2. 질문은 %s 카테고리와 직접 관련이 있어야 합니다.
                3. 질문은 자연스럽고 답하기 쉬워야 하며, 팀원 전원이 돌아가며 답할 수 있는 개방형이어야 합니다.
                4. 한국어 존댓말로 1~2문장, 200자 이내로 작성하세요.
                5. 정치·종교·외모·연봉·연애 여부·개인 신상을 묻는 질문은 금지합니다.
                6. 질문만 반환하고, 설명·번호·따옴표·추가 텍스트는 없어야 합니다.

                질문을 생성하세요:""".formatted(ctx.situation(), category, ctx.memberCount(), category);
    }

    /**
     * 프롬프트 ② — 대화 기반 꼬리 주제 + 키워드 (출력: 한 줄 JSON).
     * 대화 텍스트는 팀원 한 명의 기기로 녹음된 것이라 화자 구분이 없다. 개인을 지목하지 않고
     * 키워드에서 팀 전체가 함께 이야기할 "하나의 주제"를 첫 질문과 같은 형식으로 낸다.
     */
    public static String followUp(FollowUpContext ctx) {
        String category = label(ctx.category());
        return """
                당신은 팀 아이스브레이킹을 돕는 AI 진행자입니다.

                [상황]
                방의 주제: %s
                팀의 관심사: %s
                팀원 수: %d명

                [직전 대화]
                이전 질문: %s
                팀의 대화 내용 (팀원 한 명의 기기로 녹음한 음성 인식 텍스트. 여러 사람의 말이 섞여 있고 누가 말했는지는 알 수 없음): %s

                [누적 정보]
                지금까지 나온 키워드: %s
                이전에 이미 던진 질문들: %s

                [지시사항]
                1. 팀의 대화 내용에서 핵심 키워드 3~7개를 짧은 명사구로 뽑으세요.
                2. 그 키워드 중 1개 이상을 소재로, 팀원 모두가 함께 이야기할 수 있는 하나의 대화 주제 질문을 1개 생성하세요. 이전 질문과 같은 형식의 개방형 질문이어야 합니다.
                3. 누가 무슨 말을 했는지는 알 수 없습니다. 특정 사람을 지목하거나 이름을 부르지 말고, "OO님은 어떠세요?" 같은 개인별 질문을 만들지 마세요. 항상 팀 전체에게 묻는 형태로 작성하세요.
                4. 질문은 반드시 %s 카테고리와 연관되어야 합니다. 대화가 다른 주제로 흘렀으면 그 주제와 카테고리를 연결하세요.
                5. 이전에 던진 질문들과 소재·형식이 중복되지 않도록 하세요.
                6. 한국어 존댓말로 1~2문장, 200자 이내.
                7. 정치·종교·외모·연봉·연애 여부·개인 신상을 묻는 질문은 금지합니다.
                8. 출력은 공백·줄바꿈 없는 한 줄 JSON 객체 {"keywords":[...],"nextQuestion":"..."} 만 허용합니다. 다른 텍스트는 금지.""".formatted(
                ctx.situation(), category, ctx.memberCount(), ctx.currentQuestion(), ctx.currentAnswerText(),
                joinOrNone(ctx.accumulatedKeywords()), joinNumberedOrNone(ctx.previousQuestions()), category);
    }

    private static String joinOrNone(List<String> items) {
        return items == null || items.isEmpty() ? "(없음)" : String.join(", ", items);
    }

    private static String joinNumberedOrNone(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append('\n').append(i + 1).append(") ").append(items.get(i));
        }
        return sb.toString();
    }
}
