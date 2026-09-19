package com.kosscchthon.Icelink.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 주최자 상세의 participants 원소. */
public record RoomParticipantSummary(
        Long participantId,
        String nickname,
        @Schema(description = "JOINED | SURVEY_DONE | ASSIGNED | LATE") String status,
        @Schema(nullable = true) Integer teamNo,
        Instant joinedAt
) {
}
