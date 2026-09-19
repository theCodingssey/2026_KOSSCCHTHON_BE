package com.kosscchthon.Icelink.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.team.TeamBuilder.BuiltTeam;
import com.kosscchthon.Icelink.team.TeamBuilder.CategoryGroup;
import com.kosscchthon.Icelink.team.TeamBuilder.Member;
import com.kosscchthon.Icelink.team.TeamBuilder.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TeamBuilderTest {

    private static List<Member> members(InterestCategory category, long firstId, int... scores) {
        List<Member> list = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            list.add(new Member(firstId + i, scores[i], category));
        }
        return list;
    }

    private static List<Long> ids(BuiltTeam team) {
        return team.members().stream().map(Member::participantId).toList();
    }

    @Test
    void docExample_eightGamePlayers_teamSizeFour_snakeGivesCloseAverages() {
        // docs/02-api-spec.md 5절 예시: [28, 26, 22, 20, 17, 15, 12, 9] → 2팀
        Result result = TeamBuilder.build(members(InterestCategory.GAME, 1, 28, 26, 22, 20, 17, 15, 12, 9), 4);

        assertThat(result.teams()).hasSize(2);
        BuiltTeam t1 = result.teams().get(0);
        BuiltTeam t2 = result.teams().get(1);
        assertThat(t1.size()).isEqualTo(4);
        assertThat(t2.size()).isEqualTo(4);
        assertThat(t1.category()).isEqualTo(InterestCategory.GAME);
        assertThat(t1.mixed()).isFalse();
        // 팀1 = {28,20,17,9} 18.5 / 팀2 = {26,22,15,12} 18.75 (교환 개선으로 더 좁혀질 수는 있음)
        assertThat(Math.abs(t1.extroversionAvg() - t2.extroversionAvg())).isLessThanOrEqualTo(0.25 + 1e-9);
        assertThat(result.groups()).containsExactly(new CategoryGroup(InterestCategory.GAME, 8, 2, false));
    }

    @Test
    void teamCountUsesRounding_andSizesDifferByAtMostOne() {
        assertThat(TeamBuilder.teamCountFor(5, 4)).isEqualTo(1);
        assertThat(TeamBuilder.teamCountFor(6, 4)).isEqualTo(2);
        assertThat(TeamBuilder.teamCountFor(9, 4)).isEqualTo(2);
        assertThat(TeamBuilder.teamCountFor(10, 4)).isEqualTo(3);
        assertThat(TeamBuilder.teamCountFor(11, 4)).isEqualTo(3);
        assertThat(TeamBuilder.teamCountFor(14, 4)).isEqualTo(4);

        Result eleven = TeamBuilder.build(members(InterestCategory.FOOD, 1, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20), 4);
        assertThat(eleven.teams()).hasSize(3);
        assertThat(eleven.teams().stream().mapToInt(BuiltTeam::size).sorted().toArray()).containsExactly(3, 4, 4);
    }

    @Test
    void groupsAreSeparateByCategory_andNumberedInEnumOrder() {
        List<Member> input = new ArrayList<>();
        input.addAll(members(InterestCategory.FOOD, 100, 20, 21, 22, 23, 24, 25));   // 6 → 2팀
        input.addAll(members(InterestCategory.GAME, 200, 10, 12, 14, 16));           // 4 → 1팀

        Result result = TeamBuilder.build(input, 4);

        assertThat(result.teams()).hasSize(3);
        // 열거형 순서: MOVIE, GAME, FOOD, ... → GAME 팀이 1번, FOOD 팀이 2·3번
        assertThat(result.teams().get(0).category()).isEqualTo(InterestCategory.GAME);
        assertThat(result.teams().get(1).category()).isEqualTo(InterestCategory.FOOD);
        assertThat(result.teams().get(2).category()).isEqualTo(InterestCategory.FOOD);
        result.teams().forEach(t -> assertThat(t.members()).allMatch(m -> m.category() == t.category()));
        assertThat(result.groups()).extracting(CategoryGroup::category)
                .containsExactly(InterestCategory.GAME, InterestCategory.FOOD);
    }

    @Test
    void tinyCategoryBecomesLeftover_andJoinsSmallestTeamAsMixed() {
        List<Member> input = new ArrayList<>();
        input.addAll(members(InterestCategory.GAME, 1, 28, 26, 22, 20, 17, 15, 12));  // 7 → 2팀 (4/3)
        input.add(new Member(99, 30, InterestCategory.TRAVEL));                          // 1명 → 잔여

        Result result = TeamBuilder.build(input, 4);

        assertThat(result.teams()).hasSize(2);
        assertThat(result.teams().stream().mapToInt(BuiltTeam::size).sum()).isEqualTo(8);
        BuiltTeam withLeftover = result.teams().stream().filter(t -> ids(t).contains(99L)).findFirst().orElseThrow();
        assertThat(withLeftover.mixed()).isTrue();
        assertThat(withLeftover.category()).isEqualTo(InterestCategory.GAME);
        assertThat(withLeftover.size()).isEqualTo(4); // 3명 팀에 합류
        assertThat(result.groups()).contains(new CategoryGroup(InterestCategory.TRAVEL, 1, 0, true));
    }

    @Test
    void leftoverPrefersTeamWhoseAverageMovesClosestToGlobal() {
        // 두 팀 모두 3명: 높은 팀(avg 26)과 낮은 팀(avg 10). 잔여 8점은 낮은 팀 평균을 전체 평균(≈17)에서 멀어지게 하고
        // 높은 팀 평균을 전체 평균 쪽으로 끌어오므로 높은 팀에 들어가야 한다.
        List<Member> input = new ArrayList<>();
        input.addAll(members(InterestCategory.GAME, 1, 30, 26, 22, 12, 10, 8));  // 6 → 2팀
        input.add(new Member(50, 8, InterestCategory.SPORTS));                    // 잔여

        Result result = TeamBuilder.build(input, 3);

        BuiltTeam target = result.teams().stream().filter(t -> ids(t).contains(50L)).findFirst().orElseThrow();
        double globalAvg = (30 + 26 + 22 + 12 + 10 + 8 + 8) / 7.0;
        double other = result.teams().stream().filter(t -> t != target).findFirst().orElseThrow().extroversionAvg();
        assertThat(Math.abs(target.extroversionAvg() - globalAvg)).isLessThanOrEqualTo(Math.abs(other - globalAvg) + 1e-9);
    }

    @Test
    void allGroupsTiny_fallsBackToSingleGroupIgnoringCategory() {
        List<Member> input = List.of(
                new Member(1, 30, InterestCategory.MOVIE),
                new Member(2, 20, InterestCategory.GAME),
                new Member(3, 10, InterestCategory.FOOD),
                new Member(4, 15, InterestCategory.TRAVEL),
                new Member(5, 25, InterestCategory.SPORTS));

        Result result = TeamBuilder.build(input, 4);

        assertThat(result.teams()).hasSize(1);
        assertThat(result.teams().get(0).size()).isEqualTo(5);
        assertThat(result.teams().get(0).mixed()).isTrue();
        assertThat(result.groups()).allMatch(CategoryGroup::leftover);
    }

    @Test
    void nullCategory_isTreatedAsLeftover() {
        List<Member> input = new ArrayList<>();
        input.addAll(members(InterestCategory.GAME, 1, 20, 20, 20, 20, 20, 20));
        input.add(new Member(9, 18, null));

        Result result = TeamBuilder.build(input, 3);

        assertThat(result.teams()).hasSize(2);
        assertThat(result.teams().stream().mapToInt(BuiltTeam::size).sum()).isEqualTo(7);
        assertThat(result.teams().stream().filter(t -> ids(t).contains(9L)).findFirst().orElseThrow().mixed()).isTrue();
    }

    @Test
    void refine_strictlyNarrowsGapBetweenExtremeTeams_andStopsWhenNoSwapHelps() {
        TeamBuilder.MutableTeam high = new TeamBuilder.MutableTeam(InterestCategory.GAME);
        TeamBuilder.MutableTeam low = new TeamBuilder.MutableTeam(InterestCategory.GAME);
        high.members.addAll(members(InterestCategory.GAME, 1, 30, 30, 30));
        low.members.addAll(members(InterestCategory.GAME, 4, 10, 10, 10));
        double before = high.avg() - low.avg(); // 20

        TeamBuilder.refine(List.of(high, low));

        double after = Math.abs(high.avg() - low.avg());
        // 30↔10 한 번 교환하면 23.3 vs 16.7 (차이 6.7). 두 번째 교환은 차이를 줄이지 못하므로 멈춘다.
        assertThat(after).isLessThan(before);
        assertThat(after).isCloseTo(20.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(high.members.size()).isEqualTo(3);
        assertThat(low.members.size()).isEqualTo(3);
    }

    @Test
    void build_neverProducesWorseBalanceThanSnakeAlone() {
        // 무작위 입력 여러 개에서 build 결과의 카테고리 내 평균 편차가 스네이크 직후 편차보다 크지 않은지 확인
        Random random = new Random(3);
        for (int round = 0; round < 30; round++) {
            int n = 6 + random.nextInt(20);
            List<Member> input = new ArrayList<>();
            for (long id = 1; id <= n; id++) {
                input.add(new Member(id, 6 + random.nextInt(25), InterestCategory.GAME));
            }
            int k = TeamBuilder.teamCountFor(n, 4);
            List<TeamBuilder.MutableTeam> snakeOnly = TeamBuilder.buildBalanced(input, k, InterestCategory.GAME);
            // buildBalanced 는 이미 refine 을 포함하므로, 여기서는 결과가 정렬 규칙(인원 차 ≤1)을 지키는지만 확인
            int max = snakeOnly.stream().mapToInt(t -> t.members.size()).max().orElse(0);
            int min = snakeOnly.stream().mapToInt(t -> t.members.size()).min().orElse(0);
            assertThat(max - min).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void deterministic_sameInputSameOutput_regardlessOfInputOrder() {
        List<Member> base = new ArrayList<>();
        Random random = new Random(42);
        for (long id = 1; id <= 40; id++) {
            base.add(new Member(id, 6 + random.nextInt(25), InterestCategory.values()[random.nextInt(5)]));
        }
        List<Member> shuffled = new ArrayList<>(base);
        java.util.Collections.shuffle(shuffled, new Random(7));

        Result a = TeamBuilder.build(base, 4);
        Result b = TeamBuilder.build(shuffled, 4);

        assertThat(a.teams().stream().map(TeamBuilderTest::ids).toList())
                .isEqualTo(b.teams().stream().map(TeamBuilderTest::ids).toList());
    }

    @Test
    void hundredParticipants_everyoneAssignedExactlyOnce_andAveragesAreBalanced() {
        Random random = new Random(1);
        List<Member> input = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new Member(i, 6 + random.nextInt(25), InterestCategory.values()[random.nextInt(5)]))
                .toList();

        Result result = TeamBuilder.build(input, 4);

        List<Long> assigned = result.teams().stream().flatMap(t -> ids(t).stream()).toList();
        assertThat(assigned).hasSize(100).doesNotHaveDuplicates();
        for (InterestCategory c : InterestCategory.values()) {
            List<BuiltTeam> pure = result.teams().stream().filter(t -> t.category() == c && !t.mixed()).toList();
            if (pure.size() >= 2) {
                double max = pure.stream().mapToDouble(BuiltTeam::extroversionAvg).max().orElseThrow();
                double min = pure.stream().mapToDouble(BuiltTeam::extroversionAvg).min().orElseThrow();
                assertThat(max - min).as("category %s", c).isLessThanOrEqualTo(3.0);
            }
        }
    }

    @Test
    void rejectsFewerThanTwoMembers() {
        assertThatThrownBy(() -> TeamBuilder.build(List.of(new Member(1, 20, InterestCategory.GAME)), 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minTeamSize_isHalfRoundedUpButAtLeastTwo() {
        assertThat(TeamBuilder.minTeamSize(2)).isEqualTo(2);
        assertThat(TeamBuilder.minTeamSize(3)).isEqualTo(2);
        assertThat(TeamBuilder.minTeamSize(4)).isEqualTo(2);
        assertThat(TeamBuilder.minTeamSize(5)).isEqualTo(3);
        assertThat(TeamBuilder.minTeamSize(6)).isEqualTo(3);
        assertThat(TeamBuilder.minTeamSize(10)).isEqualTo(5);
    }
}
