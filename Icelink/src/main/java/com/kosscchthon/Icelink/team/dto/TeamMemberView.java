package com.kosscchthon.Icelink.team.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import io.swagger.v3.oas.annotations.media.Schema;

/** 팀원 표시. 외향 점수·카테고리는 주최자용 응답에서만 채운다 (T-09). */
public record TeamMemberView(
        Long participantId,
        String nickname,
        @Schema(nullable = true, description = "요청자 본인이면 true (참가자 응답에서만)") Boolean isMe,
        @Schema(nullable = true, description = "주최자 응답에서만") Integer extroversionScore,
        @Schema(nullable = true, description = "주최자 응답에서만") InterestCategory interestCategory
) {

    public static TeamMemberView forParticipant(Participant p, String viewerUserKey) {
        return new TeamMemberView(p.getId(), p.getNickname(), p.getUserKey().equals(viewerUserKey), null, null);
    }

    public static TeamMemberView forHost(Participant p) {
        return new TeamMemberView(p.getId(), p.getNickname(), null, p.getExtroversionScore(), p.getInterestCategory());
    }

    public static TeamMemberView minimal(Participant p) {
        return new TeamMemberView(p.getId(), p.getNickname(), null, null, null);
    }
}
