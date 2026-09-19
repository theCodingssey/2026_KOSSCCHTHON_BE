package com.kosscchthon.Icelink.ai;

import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 실패 시 쓰는 카테고리별 기본 질문 풀 (AI-01). 이 팀에서 이미 쓴 질문은 제외하고 무작위로 고른다.
 * 모두 썼으면 아무거나 하나.
 */
@Component
@RequiredArgsConstructor
public class FallbackQuestionPool {

    private static final Map<InterestCategory, List<String>> POOL = Map.of(
            InterestCategory.MOVIE, List.of(
                    "최근에 본 영화나 드라마 중 다른 사람에게 꼭 추천하고 싶은 작품은 무엇인가요?",
                    "인생 영화를 하나만 꼽는다면 무엇이고, 그 이유는 무엇인가요?",
                    "영화를 볼 때 극장과 집 중 어디를 더 선호하시나요? 이유도 함께 들려주세요.",
                    "가장 여러 번 다시 본 영화나 드라마는 무엇인가요?",
                    "울거나 크게 웃었던 장면이 기억나는 작품이 있나요?",
                    "좋아하는 배우나 감독이 있다면 누구이고, 어떤 점이 좋으신가요?",
                    "영화 볼 때 꼭 챙기는 간식이나 습관이 있나요?",
                    "결말이 가장 충격적이었던 작품은 무엇이었나요?",
                    "요즘 가장 기대하는 개봉 예정작이나 신작 드라마가 있나요?",
                    "친구에게 영화를 추천할 때 가장 먼저 물어보는 것은 무엇인가요?"),
            InterestCategory.GAME, List.of(
                    "인생 게임을 하나만 꼽는다면 무엇인가요? 그 게임의 어떤 점이 좋으셨나요?",
                    "최근 가장 오래 플레이한 게임은 무엇이고, 얼마나 하셨나요?",
                    "혼자 하는 게임과 함께 하는 게임 중 어느 쪽을 더 좋아하시나요?",
                    "게임하다 밤을 새운 경험이 있다면 어떤 게임이었나요?",
                    "처음으로 빠졌던 게임은 무엇이었나요?",
                    "게임에서 가장 뿌듯했던 순간이나 업적이 있나요?",
                    "모바일, PC, 콘솔 중 주로 어디서 게임을 하시나요?",
                    "같이 게임하면 잘 맞을 것 같은 사람의 특징은 무엇인가요?",
                    "게임 음악이나 캐릭터 중 특별히 기억에 남는 것이 있나요?",
                    "요즘 해보고 싶은데 아직 못 해본 게임이 있나요?"),
            InterestCategory.FOOD, List.of(
                    "최근에 먹은 음식 중 가장 맛있었던 것은 무엇이었나요?",
                    "스트레스 받을 때 꼭 찾게 되는 음식이 있나요?",
                    "여러 사람에게 꼭 추천하고 싶은 맛집이 있다면 어디인가요?",
                    "직접 요리하는 걸 좋아하시나요? 자신 있는 메뉴가 있다면요?",
                    "어릴 때는 싫었는데 지금은 좋아진 음식이 있나요?",
                    "야식으로 가장 자주 먹는 메뉴는 무엇인가요?",
                    "여행지에서 먹어본 음식 중 가장 기억에 남는 것은 무엇인가요?",
                    "매운 음식은 어느 정도까지 드시나요?",
                    "카페에 가면 주로 무엇을 주문하시나요?",
                    "누군가에게 밥을 사준다면 어떤 메뉴를 고르시겠어요?"),
            InterestCategory.TRAVEL, List.of(
                    "지금까지 다녀온 곳 중 가장 기억에 남는 여행지는 어디인가요?",
                    "계획형 여행과 즉흥 여행 중 어느 쪽을 선호하시나요?",
                    "다음에 꼭 가보고 싶은 여행지가 있다면 어디인가요?",
                    "여행 중 예상 밖의 일이 벌어졌던 경험이 있나요?",
                    "여행 갈 때 꼭 챙기는 물건이 있나요?",
                    "혼자 여행과 함께하는 여행 중 어느 쪽이 더 좋으신가요?",
                    "여행지에서 가장 좋아하는 활동은 무엇인가요? 먹기, 걷기, 쇼핑, 휴식 등",
                    "국내 여행지 중 숨은 명소라고 생각하는 곳이 있나요?",
                    "여행 사진을 많이 찍는 편인가요, 아니면 눈으로 담는 편인가요?",
                    "당일치기로 떠난다면 어디로 가고 싶으신가요?"),
            InterestCategory.SPORTS, List.of(
                    "직접 하는 운동과 보는 스포츠 중 어느 쪽을 더 즐기시나요?",
                    "응원하는 팀이나 선수가 있다면 누구인가요?",
                    "최근에 해본 운동 중 가장 재미있었던 것은 무엇인가요?",
                    "스포츠 경기를 직접 관람한 경험이 있다면 어땠나요?",
                    "새로 배워보고 싶은 운동이 있나요?",
                    "운동할 때 즐겨 듣는 음악이나 루틴이 있나요?",
                    "학교 다닐 때 체육 시간에 가장 좋아했던 종목은 무엇이었나요?",
                    "가장 기억에 남는 스포츠 경기나 명장면이 있나요?",
                    "운동을 꾸준히 하기 위해 쓰는 나만의 방법이 있나요?",
                    "팀 스포츠와 개인 스포츠 중 어느 쪽이 더 잘 맞으시나요?"));

    private final RandomGenerator random;

    public String pick(InterestCategory category, Collection<String> alreadyUsed) {
        List<String> pool = POOL.getOrDefault(category, POOL.get(InterestCategory.GAME));
        Set<String> used = new HashSet<>();
        if (alreadyUsed != null) {
            alreadyUsed.forEach(q -> used.add(normalize(q)));
        }
        List<String> candidates = new ArrayList<>();
        for (String q : pool) {
            if (!used.contains(normalize(q))) {
                candidates.add(q);
            }
        }
        List<String> from = candidates.isEmpty() ? pool : candidates;
        return from.get(random.nextInt(from.size()));
    }

    public static String normalize(String q) {
        return q == null ? "" : q.replaceAll("\\s+", "").toLowerCase();
    }
}
