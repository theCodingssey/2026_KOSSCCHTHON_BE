package com.kosscchthon.Icelink.participant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NicknameSuggesterTest {

    @Test
    void suggestsFirstFreeNumberedVariant() {
        assertThat(NicknameSuggester.suggest("민수", List.of("민수"))).isEqualTo("민수2");
        assertThat(NicknameSuggester.suggest("민수", List.of("민수", "민수2", "민수3"))).isEqualTo("민수4");
    }

    @Test
    void comparisonIgnoresCase() {
        assertThat(NicknameSuggester.suggest("Alex", List.of("alex", "alex2"))).isEqualTo("Alex3");
    }

    @Test
    void truncatesBaseToKeepWithinTwelveChars() {
        String twelve = "가나다라마바사아자차카타";
        String suggested = NicknameSuggester.suggest(twelve, List.of(twelve.toLowerCase()));

        assertThat(suggested).hasSize(12).isEqualTo("가나다라마바사아자차카2");
    }

    @Test
    void returnsNullWhenAllVariantsTaken() {
        List<String> taken = IntStream.rangeClosed(2, 99).mapToObj(n -> "a" + n).toList();
        assertThat(NicknameSuggester.suggest("a", taken)).isNull();
    }
}
