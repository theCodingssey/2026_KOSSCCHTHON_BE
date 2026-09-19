package com.kosscchthon.Icelink.user;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.dto.MeResponse;
import com.kosscchthon.Icelink.user.dto.RegisterUserRequest;
import com.kosscchthon.Icelink.user.dto.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
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
     * 서버가 256-bit 난수 키를 만들어 저장한다 (U-01~U-03). 이름은 표시용이며 유일하지 않아도 된다.
     * 키 충돌은 확률적으로 일어나지 않지만, 만약 PK 충돌이 나면 409 로 재시도를 안내한다.
     */
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String name = normalizeName(request.name());
        String key = UserKeys.generate(random);
        try {
            User saved = userRepository.saveAndFlush(User.register(key, name, Instant.now(clock)));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IcelinkException(ErrorCode.USER_KEY_CONFLICT, "키 생성이 충돌했습니다. 다시 시도해 주세요.");
        }
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
