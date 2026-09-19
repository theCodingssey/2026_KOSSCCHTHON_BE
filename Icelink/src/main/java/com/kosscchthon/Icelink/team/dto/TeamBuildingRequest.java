package com.kosscchthon.Icelink.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** POST /host/rooms/{code}/team-building. 본문 생략 가능. */
public record TeamBuildingRequest(
        @Schema(description = "true 면 설문 미완료자도 배정 (카테고리 없으면 잔여, 점수 없으면 18). 기본 false → LATE",
                defaultValue = "false")
        Boolean includeIncompleteSurvey
) {

    public boolean includeIncomplete() {
        return Boolean.TRUE.equals(includeIncompleteSurvey);
    }

    public static TeamBuildingRequest defaults() {
        return new TeamBuildingRequest(false);
    }
}
