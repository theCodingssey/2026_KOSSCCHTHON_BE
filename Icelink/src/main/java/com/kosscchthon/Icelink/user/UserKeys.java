package com.kosscchthon.Icelink.user;

import java.util.HexFormat;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * 유저 키 규칙 (docs/01-requirements.md 1.2절):
 * <pre>
 *   userKey = lowercase_hex( 256-bit CSPRNG )   // 64자 hex
 * </pre>
 * 키는 서버가 난수로 생성한다. 이름과 무관하므로 이름을 알아도 추측할 수 없고, 같은 이름이 몇 명이든 등록된다.
 * 프론트는 이름만 보내고 응답으로 받은 키를 기기에 저장한다. 일회성 서비스이므로 복구 수단은 두지 않는다.
 */
public final class UserKeys {

    /** 256-bit. 64자 hex 로 표현된다. */
    public static final int KEY_BYTES = 32;

    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final HexFormat HEX = HexFormat.of().withLowerCase();

    private UserKeys() {
    }

    public static boolean isValidFormat(String key) {
        return key != null && HEX64.matcher(key).matches();
    }

    /** 암호학적 난수 32바이트를 소문자 hex 64자로. random 은 SecureRandom 이어야 한다 (ClockConfig). */
    public static String generate(RandomGenerator random) {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
