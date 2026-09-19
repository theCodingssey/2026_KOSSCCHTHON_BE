package com.kosscchthon.Icelink.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. docs/02-api-spec.md 0.2절과 1:1 대응한다.
 * name() 이 Problem Details 의 "code" 필드로 내려간다.
 */
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    USER_KEY_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Invalid user key format"),

    // 401
    USER_KEY_REQUIRED(HttpStatus.UNAUTHORIZED, "User key required"),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "User not found"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),

    // 404
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Room not found"),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "Team not found"),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Question not found"),
    PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Participation not found"),

    // 409
    USER_KEY_CONFLICT(HttpStatus.CONFLICT, "User key already exists"),
    ROOM_NOT_WAITING(HttpStatus.CONFLICT, "Room is not accepting participants"),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "Nickname already taken in this room"),
    HOST_CANNOT_JOIN(HttpStatus.CONFLICT, "Host cannot join own room as participant"),
    ALREADY_IN_ANOTHER_ROOM(HttpStatus.CONFLICT, "Already in another active room"),
    ROOM_FULL(HttpStatus.CONFLICT, "Room is full"),
    SURVEY_INCOMPLETE(HttpStatus.CONFLICT, "Survey is not completed"),
    NOT_ENOUGH_PARTICIPANTS(HttpStatus.CONFLICT, "Not enough participants"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    QUESTION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Question limit exceeded"),
    ANSWER_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Answer already submitted"),
    TEAM_NAME_DUPLICATED(HttpStatus.CONFLICT, "Team name already taken in this room"),

    // 429
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 5xx
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI provider unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem Details "type" URI 의 마지막 세그먼트. USER_KEY_CONFLICT → user-key-conflict */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }
}
