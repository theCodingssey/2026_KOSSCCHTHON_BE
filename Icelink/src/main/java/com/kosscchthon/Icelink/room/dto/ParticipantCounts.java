package com.kosscchthon.Icelink.room.dto;

/** 주최자 상세의 counts 블록. total 은 LEFT 를 제외한 합. */
public record ParticipantCounts(int joined, int surveyDone, int assigned, int late, int total) {

    public static ParticipantCounts empty() {
        return new ParticipantCounts(0, 0, 0, 0, 0);
    }
}
