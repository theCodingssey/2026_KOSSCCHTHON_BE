package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.dto.MeResponse;
import com.kosscchthon.Icelink.user.dto.RegisterUserRequest;
import com.kosscchthon.Icelink.user.dto.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProperties properties;
    private final ActiveRoomProvider activeRoomProvider;
    private final Clock clock;
    private final RandomGenerator random;

    /**
     * POST /users — 유저 등록.
     * 서버가 난수(1~100)를 뽑아 sha256(name + nonce) 키를 만들고, 아직 쓰이지 않은 키를 골라 저장한다 (U-01~U-03).
     * 같은 이름의 후보 100개가 모두 사용 중이면 409 를 돌려준다.
     */
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String name = normalizeName(request.name());

        String key = pickUnusedKey(name)
                .orElseThrow(() -> new IcelinkException(ErrorCode.USER_KEY_CONFLICT,
                        "같은 이름으로 만들 수 있는 키가 모두 사용 중입니다. 다른 이름을 입력해 주세요."));
        try {
            User saved = userRepository.saveAndFlush(User.register(key, name, Instant.now(clock)));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 후보 조회와 저장 사이에 다른 요청이 같은 키를 넣은 경우. 클라이언트가 재시도하면 다른 난수가 뽑힌다.
            throw new IcelinkException(ErrorCode.USER_KEY_CONFLICT, "키 생성이 충돌했습니다. 다시 시도해 주세요.");
        }
    }

    /** 후보 키 100개를 한 번의 조회로 확인하고, 비어 있는 것 중 하나를 무작위로 고른다. */
    private Optional<String> pickUnusedKey(String name) {
        List<String> candidates = IntStream.rangeClosed(UserKeys.NONCE_MIN, UserKeys.NONCE_MAX)
                .mapToObj(nonce -> UserKeys.derive(name, nonce))
                .collect(Collectors.toCollection(ArrayList::new));
        Set<String> used = userRepository.findAllById(candidates).stream()
                .map(User::getUserKey)
                .collect(Collectors.toSet());
        candidates.removeAll(used);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(candidates, Random.from(random));
        return Optional.of(candidates.getFirst());
    }

    /**
     * 인터셉터용. 키로 유저를 찾고 last_seen_at 을 스로틀링해서 갱신한다.
     * 형식 검증은 호출 측(인터셉터)에서 이미 끝났다고 가정한다.
     */
    @Transactional
    public Optional<User> authenticate(String userKey) {
        Optional<User> user = userRepository.findById(userKey);
        user.ifPresent(u -> {
            Instant now = Instant.now(clock);
            userRepository.touchLastSeenIfStale(u.getUserKey(), now, now.minus(properties.lastSeenThrottle()));
        });
        return user;
    }

    /** GET /users/me */
    @Transactional(readOnly = true)
    public MeResponse getMe(String userKey) {
        User user = load(userKey);
        return MeResponse.of(user, activeRoomProvider.findActiveRoom(userKey).orElse(null));
    }

    /** PATCH /users/me — 이름 변경. 키는 유지, 진행 중 방의 닉네임은 바뀌지 않는다 (U-06). */
    @Transactional
    public UserResponse rename(String userKey, String newName) {
        User user = load(userKey);
        user.rename(normalizeName(newName));
        return UserResponse.from(user);
    }

    private User load(String userKey) {
        return userRepository.findById(userKey)
                .orElseThrow(() -> new IcelinkException(ErrorCode.USER_NOT_FOUND, "등록되지 않은 유저 키입니다."));
    }

    /** trim 후 1~12자. Bean Validation 은 trim 전 값을 보므로 여기서 한 번 더 확인한다. */
    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > User.NAME_MAX_LENGTH) {
            throw new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                    Map.of("errors", List.of(Map.of("field", "name", "message", "이름은 공백 제외 1~12자여야 합니다"))));
        }
        return name;
    }
}
