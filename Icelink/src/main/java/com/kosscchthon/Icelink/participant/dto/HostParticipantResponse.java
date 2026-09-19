package com.kosscchthon.Icelink.participant.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** GET /host/rooms/{code}/participants 원소. 주최자만 보므로 외향 점수·카테고리 포함. */
public record HostParticipantResponse(
        Long participantId,
        String nickname,
        ParticipantStatus status,
        @Schema(nullable = true) Long teamId,
        @Schema(nullable = true) Integer teamNo,
        @Schema(nullable = true) Integer extroversionScore,
        @Schema(nullable = true) InterestCategory interestCategory,
        Instant joinedAt
) {

    public static HostParticipantResponse from(Participant p, Long teamId, Integer teamNo) {
        return new HostParticipantResponse(p.getId(), p.getNickname(), p.getStatus(), teamId, teamNo,
                p.getExtroversionScore(), p.getInterestCategory(), p.getJoinedAt());
    }
}
