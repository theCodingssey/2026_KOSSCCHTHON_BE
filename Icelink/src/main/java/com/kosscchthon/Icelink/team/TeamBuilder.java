package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 팀 빌딩 알고리즘 (docs/02-api-spec.md 5절). DB 와 무관한 순수 함수.
 *
 * <pre>
 * 목표: 같은 카테고리끼리 팀을 만들고, 각 팀의 외향 점수 평균이 서로 비슷하게.
 *
 * 1. 카테고리별 그룹화 (카테고리 없는 멤버는 잔여)
 * 2. 그룹 인원 n < minTeam(= max(2, ceil(s/2))) 이면 잔여로
 * 3. 팀 수 k = max(1, round(n / s)), 스네이크 배분 + 교환 개선
 * 4. 팀이 하나도 없으면 카테고리 무시하고 전체를 한 그룹으로
 * 5. 잔여 인원은 인원이 가장 적은 팀 중 평균이 전체 평균에 가장 가까워지는 팀에 배치 (mixed)
 * 6. 팀 번호는 카테고리 열거형 순 → 팀 순
 * </pre>
 * 동점은 participantId 오름차순으로 결정적(deterministic)이다.
 */
public final class TeamBuilder {

    static final int MAX_REFINEMENT_ITERATIONS = 20;

    private TeamBuilder() {
    }

    /** 입력 멤버. category 가 null 이면(설문 미완료 포함 옵션) 잔여 인원으로 취급. */
    public record Member(long participantId, int extroversion, InterestCategory category) {
    }

    public record BuiltTeam(InterestCategory category, boolean mixed, List<Member> members) {

        public double extroversionAvg() {
            return average(members);
        }

        public int size() {
            return members.size();
        }
    }

    public record CategoryGroup(InterestCategory category, int participantCount, int teamCount, boolean leftover) {
    }

    /** teams 는 팀 번호 순(1부터), groups 는 카테고리 열거형 순. */
    public record Result(List<BuiltTeam> teams, List<CategoryGroup> groups) {
    }

    public static int minTeamSize(int teamSize) {
        return Math.max(2, (teamSize + 1) / 2);
    }

    public static int teamCountFor(int n, int teamSize) {
        return Math.max(1, (int) Math.round((double) n / teamSize));
    }

    public static Result build(List<Member> input, int teamSize) {
        if (input == null || input.size() < 2) {
            throw new IllegalArgumentException("at least 2 members required");
        }
        if (teamSize < 2) {
            throw new IllegalArgumentException("teamSize must be >= 2");
        }

        Map<InterestCategory, List<Member>> byCategory = new EnumMap<>(InterestCategory.class);
        List<Member> leftover = new ArrayList<>();
        for (Member m : input) {
            if (m.category() == null) {
                leftover.add(m);
            } else {
                byCategory.computeIfAbsent(m.category(), c -> new ArrayList<>()).add(m);
            }
        }

        int minTeam = minTeamSize(teamSize);
        List<MutableTeam> teams = new ArrayList<>();
        List<CategoryGroup> groups = new ArrayList<>();

        for (InterestCategory category : InterestCategory.values()) {
            List<Member> members = byCategory.get(category);
            if (members == null || members.isEmpty()) {
                continue;
            }
            int n = members.size();
            if (n < minTeam) {
                leftover.addAll(members);
                groups.add(new CategoryGroup(category, n, 0, true));
                continue;
            }
            int k = teamCountFor(n, teamSize);
            teams.addAll(buildBalanced(members, k, category));
            groups.add(new CategoryGroup(category, n, k, false));
        }

        if (teams.isEmpty()) {
            // 모든 그룹이 소수 (T-07): 카테고리 무시
            List<Member> all = new ArrayList<>(input);
            int k = teamCountFor(all.size(), teamSize);
            InterestCategory category = mostCommonCategory(all);
            teams.addAll(buildBalanced(all, k, category));
            for (MutableTeam t : teams) {
                t.mixed = true;
            }
            leftover.clear();
        }

        placeLeftover(leftover, teams, average(input));

        List<BuiltTeam> built = teams.stream()
                .map(t -> new BuiltTeam(t.category, t.mixed, List.copyOf(t.members)))
                .toList();
        return new Result(built, List.copyOf(groups));
    }

    // ---- 내부 ----

    /** 외향 평균 균등 배치: 정렬 → 스네이크 → 교환 개선 */
    static List<MutableTeam> buildBalanced(List<Member> members, int k, InterestCategory category) {
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingInt(Member::extroversion).reversed()
                .thenComparingLong(Member::participantId));

        List<MutableTeam> teams = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            teams.add(new MutableTeam(category));
        }
        // 스네이크: 1→k, k→1, 1→k ...
        for (int i = 0; i < sorted.size(); i++) {
            int row = i / k;
            int col = i % k;
            int teamIndex = (row % 2 == 0) ? col : (k - 1 - col);
            teams.get(teamIndex).members.add(sorted.get(i));
        }
        refine(teams);
        return teams;
    }

    /** 평균 최고 팀 H 와 최저 팀 L 사이에서 |avg(H') - avg(L')| 를 가장 줄이는 1:1 교환을 반복 */
    static void refine(List<MutableTeam> teams) {
        if (teams.size() < 2) {
            return;
        }
        for (int iter = 0; iter < MAX_REFINEMENT_ITERATIONS; iter++) {
            MutableTeam high = teams.get(0);
            MutableTeam low = teams.get(0);
            for (MutableTeam t : teams) {
                if (t.avg() > high.avg()) {
                    high = t;
                }
                if (t.avg() < low.avg()) {
                    low = t;
                }
            }
            double currentDiff = high.avg() - low.avg();
            if (currentDiff <= 0) {
                return;
            }

            int bestI = -1;
            int bestJ = -1;
            double bestDiff = currentDiff;
            double hn = high.members.size();
            double ln = low.members.size();
            double hSum = high.sum();
            double lSum = low.sum();
            for (int i = 0; i < high.members.size(); i++) {
                for (int j = 0; j < low.members.size(); j++) {
                    int a = high.members.get(i).extroversion();
                    int b = low.members.get(j).extroversion();
                    double newHigh = (hSum - a + b) / hn;
                    double newLow = (lSum - b + a) / ln;
                    double diff = Math.abs(newHigh - newLow);
                    if (diff < bestDiff - 1e-9) {
                        bestDiff = diff;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }
            if (bestI < 0) {
                return;
            }
            Member a = high.members.get(bestI);
            Member b = low.members.get(bestJ);
            high.members.set(bestI, b);
            low.members.set(bestJ, a);
        }
    }

    /** 잔여 인원: 인원 최소 팀들 중, 넣었을 때 팀 평균이 전체 평균에 가장 가까워지는 팀 */
    static void placeLeftover(List<Member> leftover, List<MutableTeam> teams, double globalAvg) {
        if (leftover.isEmpty()) {
            return;
        }
        List<Member> sorted = new ArrayList<>(leftover);
        sorted.sort(Comparator.comparingInt(Member::extroversion).reversed()
                .thenComparingLong(Member::participantId));
        for (Member m : sorted) {
            int minSize = teams.stream().mapToInt(t -> t.members.size()).min().orElse(0);
            MutableTeam target = null;
            double bestDistance = Double.MAX_VALUE;
            for (MutableTeam t : teams) {
                if (t.members.size() != minSize) {
                    continue;
                }
                double avgAfter = (t.sum() + m.extroversion()) / (t.members.size() + 1.0);
                double distance = Math.abs(avgAfter - globalAvg);
                if (distance < bestDistance - 1e-9) {
                    bestDistance = distance;
                    target = t;
                }
            }
            target.members.add(m);
            if (m.category() != target.category) {
                target.mixed = true;
            }
        }
    }

    static InterestCategory mostCommonCategory(List<Member> members) {
        Map<InterestCategory, Integer> counts = new EnumMap<>(InterestCategory.class);
        for (Member m : members) {
            if (m.category() != null) {
                counts.merge(m.category(), 1, Integer::sum);
            }
        }
        InterestCategory best = null;
        int bestCount = -1;
        for (InterestCategory c : InterestCategory.values()) {
            int count = counts.getOrDefault(c, 0);
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best == null ? InterestCategory.values()[0] : best;
    }

    static double average(List<Member> members) {
        if (members.isEmpty()) {
            return 0;
        }
        return members.stream().mapToInt(Member::extroversion).sum() / (double) members.size();
    }

    static final class MutableTeam {
        final InterestCategory category;
        final List<Member> members = new ArrayList<>();
        boolean mixed;

        MutableTeam(InterestCategory category) {
            this.category = category;
        }

        double sum() {
            return members.stream().mapToInt(Member::extroversion).sum();
        }

        double avg() {
            return members.isEmpty() ? 0 : sum() / members.size();
        }
    }
}
