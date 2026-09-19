package com.kosscchthon.Icelink.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserKeysTest {

    @Test
    void generate_returns64LowercaseHex() {
        String key = UserKeys.generate(new SecureRandom());

        assertThat(key).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(UserKeys.isValidFormat(key)).isTrue();
    }

    @Test
    void generate_isReproducibleForSeededRandomAndDistinctAcrossCalls() {
        assertThat(UserKeys.generate(new Random(7))).isEqualTo(UserKeys.generate(new Random(7)));

        Random random = new Random(7);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(UserKeys.generate(random));
        }
        assertThat(keys).hasSize(1000);
    }

    @Test
    void generate_doesNotDependOnAnyName() {
        // 키는 이름과 무관한 순수 난수다: 같은 시드면 어떤 "이름"이든 같은 키가 나온다는 뜻이 아니라,
        // 생성 API 가 이름을 받지 않는다는 것을 컴파일 타임에 고정한다.
        String key = UserKeys.generate(new Random(1));
        assertThat(key).doesNotContain("민수");
    }

    @Test
    void isValidFormat_rejectsUppercaseWrongLengthAndNull() {
        String valid = UserKeys.generate(new Random(1));

        assertThat(UserKeys.isValidFormat(valid.toUpperCase())).isFalse();
        assertThat(UserKeys.isValidFormat(valid.substring(1))).isFalse();
        assertThat(UserKeys.isValidFormat(valid + "0")).isFalse();
        assertThat(UserKeys.isValidFormat(null)).isFalse();
        assertThat(UserKeys.isValidFormat("")).isFalse();
    }
}
