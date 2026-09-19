package com.kosscchthon.Icelink.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /users/me 의 activeRoom. 프론트 첫 화면 라우팅 기준.
 * role 이 HOST 면 participantId / participantStatus / teamId 는 null.
 */
@Schema(description = "유저가 현재 주최 또는 참가 중인 방 (FINISHED 아닌 것 중 최근 1개)")
public record ActiveRoomResponse(
        @Schema(description = "HOST | PARTICIPANT", example = "PARTICIPANT") String role,
        @Schema(example = "12") Long roomId,
        @Schema(example = "K7M3PQ") String code,
        @Schema(example = "KOSSCCHTHON 팀빌딩") String title,
        @Schema(description = "WAITING | TEAM_BUILDING | IN_PROGRESS", example = "IN_PROGRESS") String status,
        @Schema(nullable = true, example = "101") Long participantId,
        @Schema(nullable = true, description = "JOINED | SURVEY_DONE | ASSIGNED | LATE", example = "ASSIGNED") String participantStatus,
        @Schema(nullable = true, example = "501") Long teamId
) {
}
