package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoomRequest(
        @Schema(example = "KOSSCCHTHON 팀빌딩")
        @NotBlank @Size(max = Room.TITLE_MAX_LENGTH, message = "제목은 50자 이하여야 합니다")
        String title,

        @Schema(description = "AI 질문 생성 컨텍스트로 쓰이는 상황 설명", example = "대학생 해커톤 참가자 30명, 서로 처음 봄.")
        @NotBlank @Size(max = Room.SITUATION_MAX_LENGTH, message = "상황 설명은 500자 이하여야 합니다")
        String situation,

        @Schema(description = "팀당 인원수 (2~10)", example = "4")
        @NotNull @Min(Room.TEAM_SIZE_MIN) @Max(Room.TEAM_SIZE_MAX)
        Integer teamSize,

        @Schema(description = "마무리 질문 목록 (0~5개, 각 200자). 종료 시 참가자 화면에 표시", nullable = true)
        @Size(max = Room.FINAL_QUESTIONS_MAX, message = "마무리 질문은 5개까지 가능합니다")
        List<@NotBlank @Size(max = Room.FINAL_QUESTION_MAX_LENGTH, message = "마무리 질문은 200자 이하여야 합니다") String> finalQuestions
) {
}
