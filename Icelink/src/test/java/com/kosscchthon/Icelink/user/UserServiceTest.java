package com.kosscchthon.Icelink.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Optional;
import java.util.Random;
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
    private static final String KEY = "3a7f0d2e9b6c4f18a5d7e2c1b0f9a8d7c6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1";

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

    @Nested
    class Register {

        @Test
        void generatesRandom64HexKeyAndTrimsName() {
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse res = service.register(new RegisterUserRequest("  " + NAME + " "));

            assertThat(res.name()).isEqualTo(NAME);
            assertThat(res.createdAt()).isEqualTo(NOW);
            assertThat(UserKeys.isValidFormat(res.userKey())).isTrue();
        }

        @Test
        void keyDoesNotDependOnNameAndDiffersPerRegistration() {
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse first = service.register(new RegisterUserRequest(NAME));
            UserResponse second = service.register(new RegisterUserRequest(NAME));

            // 같은 이름으로 몇 번 등록해도 서로 다른 키가 나온다 (이름당 100개 제한 없음)
            assertThat(first.userKey()).isNotEqualTo(second.userKey());
        }

        @Test
        void savesExactlyOnceWithoutLookingUpCandidates() {
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.register(new RegisterUserRequest(NAME));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(captor.capture());
            verify(userRepository, never()).findAllById(any());
            assertThat(captor.getValue().getName()).isEqualTo(NAME);
            assertThat(UserKeys.isValidFormat(captor.getValue().getUserKey())).isTrue();
        }

        @Test
        void conflictsWhenInsertCollidesOnPrimaryKey() {
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
            verify(userRepository, never()).saveAndFlush(any());
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
