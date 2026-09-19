package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.participant.Participant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 질문에 대한 팀의 답변 (프론트 STT 텍스트). 질문과 1:1, PK 공유. */
@Entity
@Table(name = "team_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamAnswer {

    public static final int TEXT_MAX_LENGTH = 3000;
    public static final int SHORT_TEXT_THRESHOLD = 10;

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "question_id")
    private TeamQuestion question;

    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answerText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false, updatable = false)
    private Participant submittedBy;

    @Column(name = "speech_duration_sec")
    private Integer speechDurationSec;

    /** LLM 추출 키워드 (3~7개). 처리 전 빈 배열. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> keywords = new ArrayList<>();

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private TeamAnswer(TeamQuestion question, String answerText, Participant submittedBy, Integer speechDurationSec, Instant now) {
        this.question = question;
        this.answerText = answerText;
        this.submittedBy = submittedBy;
        this.speechDurationSec = speechDurationSec;
        this.submittedAt = now;
    }

    public static TeamAnswer submit(TeamQuestion question, String answerText, Participant submittedBy,
                                    Integer speechDurationSec, Instant now) {
        return new TeamAnswer(question, answerText, submittedBy, speechDurationSec, now);
    }

    /** @MapsId 로 영속화 시점에 채워진다. 그 전(단위 테스트 등)에는 질문 ID 를 그대로 쓴다. */
    public Long getQuestionId() {
        return questionId != null ? questionId : (question == null ? null : question.getId());
    }

    public List<String> getKeywords() {
        return List.copyOf(keywords);
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    /** 답변 텍스트가 너무 짧아 꼬리질문 근거로 쓰기 어려운 경우 (Q-16) */
    public boolean isTooShort() {
        return answerText.trim().length() < SHORT_TEXT_THRESHOLD;
    }

    public void applyKeywords(List<String> keywords, Instant now) {
        this.keywords = new ArrayList<>(keywords == null ? List.of() : keywords);
        this.processedAt = now;
    }
}
