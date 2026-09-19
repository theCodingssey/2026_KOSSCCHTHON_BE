package com.kosscchthon.Icelink.user;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kosscchthon.Icelink.common.auth.CurrentUserArgumentResolver;
import com.kosscchthon.Icelink.common.auth.UserKeyInterceptor;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.dto.MeResponse;
import com.kosscchthon.Icelink.user.dto.RegisterUserRequest;
import com.kosscchthon.Icelink.user.dto.UserResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 + 인터셉터 + 예외 핸들러 슬라이스 테스트. DB 없이 동작한다.
 */
@WebMvcTest(UserController.class)
@Import({WebMvcConfig.class, UserKeyInterceptor.class, CurrentUserArgumentResolver.class})
class UserControllerTest {

    private static final String KEY = UserKeys.derive("민수", 42);
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void register_returns201WithoutUserKeyHeader() throws Exception {
        when(userService.register(any(RegisterUserRequest.class)))
                .thenReturn(new UserResponse(KEY, "민수", NOW));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"민수"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userKey").value(KEY))
                .andExpect(jsonPath("$.name").value("민수"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-19T10:00:00Z"));
    }

    @Test
    void register_beanValidationFailure_isProblemDetailWithErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.type").value("https://icelink.app/errors/validation-error"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService, never()).register(any());
    }

    @Test
    void register_conflict_isMappedTo409() throws Exception {
        when(userService.register(any(RegisterUserRequest.class)))
                .thenThrow(new IcelinkException(ErrorCode.USER_KEY_CONFLICT, "dup"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"민수"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USER_KEY_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("dup"));
    }

    @Test
    void me_withoutHeader_is401UserKeyRequired() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USER_KEY_REQUIRED"));

        verify(userService, never()).getMe(any());
    }

    @Test
    void me_withMalformedHeader_is400InvalidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(UserKeyInterceptor.HEADER, "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_KEY_INVALID_FORMAT"));
    }

    @Test
    void me_withUnknownKey_is401UserNotFound() throws Exception {
        when(userService.authenticate(KEY)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/me").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void me_withValidHeader_returnsMeResponse() throws Exception {
        User user = User.register(KEY, "민수", NOW);
        when(userService.authenticate(KEY)).thenReturn(Optional.of(user));
        when(userService.getMe(KEY)).thenReturn(MeResponse.of(user, null));

        mockMvc.perform(get("/api/v1/users/me").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userKey").value(KEY))
                .andExpect(jsonPath("$.name").value("민수"))
                .andExpect(jsonPath("$.activeRoom").value((Object) null));
    }

    @Test
    void me_acceptsUserKeyQueryParamAsSseFallback() throws Exception {
        User user = User.register(KEY, "민수", NOW);
        when(userService.authenticate(KEY)).thenReturn(Optional.of(user));
        when(userService.getMe(KEY)).thenReturn(MeResponse.of(user, null));

        mockMvc.perform(get("/api/v1/users/me").param(UserKeyInterceptor.QUERY_PARAM, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userKey").value(KEY));
    }

    @Test
    void rename_returnsUpdatedUser() throws Exception {
        User user = User.register(KEY, "민수", NOW);
        when(userService.authenticate(KEY)).thenReturn(Optional.of(user));
        when(userService.rename(KEY, "민수짱")).thenReturn(new UserResponse(KEY, "민수짱", NOW));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(UserKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"민수짱"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("민수짱"));
    }

    @Test
    void unknownPath_is404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/nope").header(UserKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }
}
