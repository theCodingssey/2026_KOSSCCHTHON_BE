package com.kosscchthon.Icelink.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class UserKeysTest {

    @Test
    void derive_returns64LowercaseHex() {
        String key = UserKeys.derive("민수", 42);

        assertThat(key).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(UserKeys.isValidFormat(key)).isTrue();
    }

    @Test
    void derive_isDeterministicAndDependsOnNameAndNonce() {
        assertThat(UserKeys.derive("민수", 42)).isEqualTo(UserKeys.derive("민수", 42));
        assertThat(UserKeys.derive("민수", 42)).isNotEqualTo(UserKeys.derive("민수", 43));
        assertThat(UserKeys.derive("민수", 42)).isNotEqualTo(UserKeys.derive("민수2", 42));
    }

    @Test
    void derive_equalsPlainSha256OfNameConcatNonce() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("민수42".getBytes(StandardCharsets.UTF_8));
        String expected = HexFormat.of().withLowerCase().formatHex(digest);

        assertThat(UserKeys.derive("민수", 42)).isEqualTo(expected);
    }

    @Test
    void derive_rejectsNonceOutOfRange() {
        assertThatThrownBy(() -> UserKeys.derive("민수", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserKeys.derive("민수", 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidFormat_rejectsUppercaseWrongLengthAndNull() {
        String valid = UserKeys.derive("a", 1);

        assertThat(UserKeys.isValidFormat(valid.toUpperCase())).isFalse();
        assertThat(UserKeys.isValidFormat(valid.substring(1))).isFalse();
        assertThat(UserKeys.isValidFormat(valid + "0")).isFalse();
        assertThat(UserKeys.isValidFormat(null)).isFalse();
        assertThat(UserKeys.isValidFormat("")).isFalse();
    }

    @Test
    void isValidNonce_boundaries() {
        assertThat(UserKeys.isValidNonce(0)).isFalse();
        assertThat(UserKeys.isValidNonce(1)).isTrue();
        assertThat(UserKeys.isValidNonce(100)).isTrue();
        assertThat(UserKeys.isValidNonce(101)).isFalse();
    }
}
