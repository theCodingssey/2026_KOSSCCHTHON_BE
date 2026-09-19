package com.kosscchthon.Icelink.room;

import java.util.Locale;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * R-02: 6자리 영문 대문자 + 숫자 코드. 혼동되는 0 O 1 I 는 제외한다 (32자 알파벳, 32^6 ≈ 10억 조합).
 * DB 에 이미 있는 코드가 나오면 다시 뽑는다.
 */
@Component
@RequiredArgsConstructor
public class RoomCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_ATTEMPTS = 10;

    private final RoomRepository roomRepository;
    private final RandomGenerator random;

    public String generateUnique() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = generate();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate a unique room code after " + MAX_ATTEMPTS + " attempts");
    }

    String generate() {
        StringBuilder sb = new StringBuilder(Room.CODE_LENGTH);
        for (int i = 0; i < Room.CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 사용자 입력 정규화: 앞뒤 공백 제거 + 대문자. 소문자로 입력해도 통과시키기 위함. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
