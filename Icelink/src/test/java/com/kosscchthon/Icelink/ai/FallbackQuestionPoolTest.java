package com.kosscchthon.Icelink.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.kosscchthon.Icelink.participant.InterestCategory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FallbackQuestionPoolTest {

    private final FallbackQuestionPool pool = new FallbackQuestionPool(new Random(1));

    @Test
    void pick_returnsCategoryQuestionNotAlreadyUsed() {
        Set<String> seen = new HashSet<>();
        List<String> used = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String q = pool.pick(InterestCategory.FOOD, used);
            assertThat(q).isNotBlank();
            assertThat(seen.add(FallbackQuestionPool.normalize(q))).as("no repeats while pool has unused: %s", q).isTrue();
            used.add(q);
        }
    }

    @Test
    void pick_fallsBackToAnyWhenAllUsed() {
        List<String> used = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            used.add(pool.pick(InterestCategory.SPORTS, used));
        }
        assertThat(pool.pick(InterestCategory.SPORTS, used)).isNotBlank();
    }

    @Test
    void normalize_ignoresWhitespaceAndCase() {
        assertThat(FallbackQuestionPool.normalize(" A b  C ")).isEqualTo("abc");
        assertThat(FallbackQuestionPool.normalize(null)).isEmpty();
    }

    @Test
    void everyCategoryHasQuestions() {
        for (InterestCategory c : InterestCategory.values()) {
            assertThat(pool.pick(c, List.of())).isNotBlank();
        }
    }
}
