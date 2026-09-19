package com.kosscchthon.Icelink.participant.dto;

import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /rooms/{code}/me — 이 방에서의 내 상태. 프론트 세부 화면 복원 기준.
 * team 은 팀 빌딩 전 null. room.finalQuestions 는 방이 FINISHED 일 때만 채워진다.
 */
public record ParticipantMeResponse(
        Long participantId,
        String userKey,
        String nickname,
        ParticipantStatus status,
        RoomView room,
        SurveyView survey,
        @Schema(nullable = true) TeamView team
) {

    public record RoomView(
            Long roomId,
            String code,
            String title,
            RoomStatus status,
            int teamSize,
            int participantCount,
            @Schema(nullable = true, description = "FINISHED 일 때만. 그 전엔 null") List<String> finalQuestions
    ) {
        public static RoomView from(Room room, int participantCount) {
            List<String> finalQuestions = room.getStatus() == RoomStatus.FINISHED ? room.getFinalQuestions() : null;
            return new RoomView(room.getId(), room.getCode(), room.getTitle(), room.getStatus(), room.getTeamSize(),
                    participantCount, finalQuestions);
        }
    }

    public record SurveyView(
            boolean personalityDone,
            boolean categoryDone,
            @Schema(nullable = true) Integer extroversionScore,
            @Schema(nullable = true) InterestCategory interestCategory
    ) {
        public static SurveyView from(Participant p) {
            return new SurveyView(p.isPersonalityDone(), p.isCategoryDone(), p.getExtroversionScore(), p.getInterestCategory());
        }
    }

    /** 팀 배정 후 채워진다 (3.4). 구조는 GET /teams/{teamId} 의 요약과 같다. */
    public record TeamView(
            Long teamId,
            int teamNo,
            String name,
            String status,
            InterestCategory category,
            List<Member> members
    ) {
        public record Member(Long participantId, String nickname, boolean isMe) {
        }
    }

    public static ParticipantMeResponse of(Participant p, Room room, int participantCount, TeamView team) {
        return new ParticipantMeResponse(
                p.getId(),
                p.getUserKey(),
                p.getNickname(),
                p.getStatus(),
                RoomView.from(room, participantCount),
                SurveyView.from(p),
                team);
    }
}
