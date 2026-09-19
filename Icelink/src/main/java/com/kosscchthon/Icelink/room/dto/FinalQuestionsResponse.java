package com.kosscchthon.Icelink.room.dto;

import java.time.Instant;
import java.util.List;

public record FinalQuestionsResponse(List<String> finalQuestions, Instant updatedAt) {
}
