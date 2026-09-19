package com.kosscchthon.Icelink.team.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.room.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** POST /host/rooms/{code}/team-building 응답. */
public record TeamBuildingResponse(
        RoomStatus roomStatus,
        int teamCount,
        int assignedCount,
        int lateCount,
        List<CategoryGroupView> categoryGroups,
        List<BuiltTeamView> teams
) {

    public record CategoryGroupView(
            InterestCategory category,
            int participantCount,
            int teamCount,
            @Schema(description = "인원이 적어 팀을 만들지 못하고 잔여로 배치됨") boolean leftover
    ) {
    }

    public record BuiltTeamView(
            Long teamId,
            int teamNo,
            String name,
            InterestCategory category,
            boolean mixed,
            Double extroversionAvg,
            List<TeamMemberView> members
    ) {
    }
}
