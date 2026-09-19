package com.kosscchthon.Icelink.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 유저 키 규칙 (docs/01-requirements.md 1.2절):
 * <pre>
 *   userKey = lowercase_hex( SHA-256( UTF-8(trim(name)) || String(nonce) ) ), nonce in [1, 100]
 * </pre>
 * 키는 서버가 생성한다. 프론트는 이름만 보내고 응답으로 받은 키를 기기에 저장한다.
 */
public final class UserKeys {

    public static final int NONCE_MIN = 1;
    public static final int NONCE_MAX = 100;

    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final HexFormat HEX = HexFormat.of().withLowerCase();

    private UserKeys() {
    }

    public static boolean isValidFormat(String key) {
        return key != null && HEX64.matcher(key).matches();
    }

    public static boolean isValidNonce(int nonce) {
        return nonce >= NONCE_MIN && nonce <= NONCE_MAX;
    }

    /** name 은 호출 측에서 trim 된 값을 넘긴다. */
    public static String derive(String name, int nonce) {
        if (!isValidNonce(nonce)) {
            throw new IllegalArgumentException("nonce must be in [" + NONCE_MIN + ", " + NONCE_MAX + "]: " + nonce);
        }
        byte[] input = (name + nonce).getBytes(StandardCharsets.UTF_8);
        return HEX.formatHex(sha256().digest(input));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
