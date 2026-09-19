package com.kosscchthon.Icelink.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.dto.ActiveRoomResponse;
import com.kosscchthon.Icelink.user.dto.MeResponse;
import com.kosscchthon.Icelink.user.dto.RegisterUserRequest;
import com.kosscchthon.Icelink.user.dto.UserResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String NAME = "민수";
    private static final String KEY = UserKeys.derive(NAME, 42);

    @Mock UserRepository userRepository;
    @Mock ActiveRoomProvider activeRoomProvider;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository,
                new UserProperties(Duration.ofSeconds(60)),
                activeRoomProvider,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new Random(7));
    }

    private static Set<String> allCandidateKeys(String name) {
        return IntStream.rangeClosed(UserKeys.NONCE_MIN, UserKeys.NONCE_MAX)
                .mapToObj(n -> UserKeys.derive(name, n))
                .collect(Collectors.toSet());
    }

    @Nested
    class Register {

        @Test
        void generatesKeyFromTrimmedNameAndOneOfHundredNonces() {
            when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = service.register(new RegisterUserRequest("  " + NAME + " "));

            assertThat(res.name()).isEqualTo(NAME);
            assertThat(res.createdAt()).isEqualTo(NOW);
            assertThat(UserKeys.isValidFormat(res.userKey())).isTrue();
            assertThat(allCandidateKeys(NAME)).contains(res.userKey());
        }

        @Test
        void checksAllHundredCandidatesInOneQuery() {
            when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.register(new RegisterUserRequest(NAME));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Iterable<String>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(userRepository).findAllById(captor.capture());
            Set<String> queried = StreamSupport.stream(captor.getValue().spliterator(), false)
                    .collect(Collectors.toSet());
            assertThat(queried).isEqualTo(allCandidateKeys(NAME));
        }

        @Test
        void skipsKeysAlreadyInUse() {
            // 42번 이외 후보를 모두 사용 중으로 만들면 42번 키만 남는다
            List<User> used = allCandidateKeys(NAME).stream()
                    .filter(k -> !k.equals(KEY))
                    .map(k -> User.register(k, NAME, NOW))
                    .toList();
            when(userRepository.findAllById(anyIterable())).thenReturn(used);
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = service.register(new RegisterUserRequest(NAME));

            assertThat(res.userKey()).isEqualTo(KEY);
        }

        @Test
        void conflictsWhenAllHundredKeysAreTaken() {
            List<User> used = allCandidateKeys(NAME).stream().map(k -> User.register(k, NAME, NOW)).toList();
            when(userRepository.findAllById(anyIterable())).thenReturn(used);

            assertThatThrownBy(() -> service.register(new RegisterUserRequest(NAME)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_KEY_CONFLICT));
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void conflictsWhenInsertRacesWithAnotherRequest() {
            when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
            when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> service.register(new RegisterUserRequest(NAME)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_KEY_CONFLICT));
        }

        @Test
        void rejectsBlankNameAfterTrim() {
            assertThatThrownBy(() -> service.register(new RegisterUserRequest("   ")))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
            verify(userRepository, never()).findAllById(anyIterable());
        }
    }

    @Nested
    class Authenticate {

        @Test
        void returnsUserAndTouchesLastSeenWithThrottleThreshold() {
            User user = User.register(KEY, NAME, NOW.minusSeconds(3600));
            when(userRepository.findById(KEY)).thenReturn(Optional.of(user));

            Optional<User> result = service.authenticate(KEY);

            assertThat(result).contains(user);
            verify(userRepository).touchLastSeenIfStale(eq(KEY), eq(NOW), eq(NOW.minusSeconds(60)));
        }

        @Test
        void returnsEmptyForUnknownKeyWithoutTouching() {
            when(userRepository.findById(KEY)).thenReturn(Optional.empty());

            assertThat(service.authenticate(KEY)).isEmpty();
            verify(userRepository, never()).touchLastSeenIfStale(anyString(), any(), any());
        }
    }

    @Nested
    class MeAndRename {

        @Test
        void getMe_includesActiveRoomFromProvider() {
            User user = User.register(KEY, NAME, NOW);
            ActiveRoomResponse room = new ActiveRoomResponse("HOST", 12L, "K7M3PQ", "t", "WAITING", null, null, null);
            when(userRepository.findById(KEY)).thenReturn(Optional.of(user));
            when(activeRoomProvider.findActiveRoom(KEY)).thenReturn(Optional.of(room));

            MeResponse me = service.getMe(KEY);

            assertThat(me.name()).isEqualTo(NAME);
            assertThat(me.activeRoom()).isEqualTo(room);
        }

        @Test
        void getMe_activeRoomIsNullWhenNone() {
            when(userRepository.findById(KEY)).thenReturn(Optional.of(User.register(KEY, NAME, NOW)));
            when(activeRoomProvider.findActiveRoom(KEY)).thenReturn(Optional.empty());

            assertThat(service.getMe(KEY).activeRoom()).isNull();
        }

        @Test
        void rename_trimsAndKeepsKey() {
            User user = User.register(KEY, NAME, NOW);
            when(userRepository.findById(KEY)).thenReturn(Optional.of(user));

            UserResponse res = service.rename(KEY, " 민수짱 ");

            assertThat(res.userKey()).isEqualTo(KEY);
            assertThat(res.name()).isEqualTo("민수짱");
            assertThat(user.getName()).isEqualTo("민수짱");
        }

        @Test
        void rename_rejectsTooLongName() {
            when(userRepository.findById(KEY)).thenReturn(Optional.of(User.register(KEY, NAME, NOW)));

            assertThatThrownBy(() -> service.rename(KEY, "가".repeat(13)))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @Test
        void getMe_unknownUserIsNotFound() {
            when(userRepository.findById(KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMe(KEY))
                    .isInstanceOfSatisfying(IcelinkException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }
}
