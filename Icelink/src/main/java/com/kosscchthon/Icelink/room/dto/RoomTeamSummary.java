package com.kosscchthon.Icelink.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 주최자 상세의 teams 원소. */
public record RoomTeamSummary(
        Long teamId,
        int teamNo,
        String name,
        @Schema(description = "NOT_STARTED | NAMING | QUESTIONING | FINISHED") String status,
        int memberCount,
        @Schema(description = "MOVIE | GAME | FOOD | TRAVEL | SPORTS") String category,
        boolean mixed,
        @Schema(nullable = true) Double extroversionAvg,
        int questionCount
) {
}
