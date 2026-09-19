package com.kosscchthon.Icelink.room.dto;

import com.kosscchthon.Icelink.room.Room;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** PUT /host/rooms/{code}/final-questions. 목록 전체 덮어쓰기. 빈 배열이면 모두 삭제. */
public record UpdateFinalQuestionsRequest(
        @NotNull
        @Size(max = Room.FINAL_QUESTIONS_MAX, message = "마무리 질문은 5개까지 가능합니다")
        List<@NotBlank @Size(max = Room.FINAL_QUESTION_MAX_LENGTH, message = "마무리 질문은 200자 이하여야 합니다") String> finalQuestions
) {
}
