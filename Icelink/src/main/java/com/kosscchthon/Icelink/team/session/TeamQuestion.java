package com.kosscchthon.Icelink.team.session;

import com.kosscchthon.Icelink.ai.AiFailureReason;
import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 팀에 제시된 질문 한 개. 답변은 {@link TeamAnswer} 1:1. */
@Entity
@Table(name = "team_questions",
        uniqueConstraints = @UniqueConstraint(name = "ux_team_questions_team_order", columnNames = {"team_id", "order_no"}),
        indexes = @Index(name = "ix_team_questions_team", columnList = "team_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamQuestion {

    public static final int CONTENT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "order_no", nullable = false, updatable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false, updatable = false)
    private QuestionType type;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QuestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private AiFailureReason failureReason;

    /** 프롬프트에 넣은 값·토큰 사용량 (디버깅·크레딧 추적) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "generation_context", columnDefinition = "jsonb")
    private Map<String, Object> generationContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private TeamQuestion(Team team, int orderNo, QuestionType type, String content, Map<String, Object> generationContext, Instant now) {
        this.team = team;
        this.orderNo = orderNo;
        this.type = type;
        this.content = content;
        this.status = QuestionStatus.ANSWERING;
        this.generationContext = generationContext;
        this.createdAt = now;
    }

    public static TeamQuestion create(Team team, int orderNo, QuestionType type, String content,
                                      Map<String, Object> generationContext, Instant now) {
        return new TeamQuestion(team, orderNo, type, content, generationContext, now);
    }

    public Long getTeamId() {
        return team.getId();
    }

    /** 답변 제출: ANSWERING → PROCESSING. 이미 PROCESSING 이면 ANSWER_ALREADY_SUBMITTED. */
    public void startProcessing() {
        if (status == QuestionStatus.PROCESSING) {
            throw new IcelinkException(ErrorCode.ANSWER_ALREADY_SUBMITTED, "다른 팀원이 먼저 답변을 제출했습니다. 처리 중입니다.");
        }
        if (status != QuestionStatus.ANSWERING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "답변을 받을 수 없는 질문입니다. (현재 상태: " + status + ")");
        }
        this.status = QuestionStatus.PROCESSING;
        this.failureReason = null;
    }

    /** 재시도: FAILED → PROCESSING */
    public void retryProcessing() {
        if (status != QuestionStatus.FAILED) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "재시도할 수 없는 질문입니다. (현재 상태: " + status + ")");
        }
        this.status = QuestionStatus.PROCESSING;
        this.failureReason = null;
    }

    public void markDone() {
        this.status = QuestionStatus.DONE;
        this.failureReason = null;
    }

    public void markFailed(AiFailureReason reason) {
        this.status = QuestionStatus.FAILED;
        this.failureReason = reason;
    }

    /** 건너뛰기 또는 INTRO 완료: ANSWERING → SKIPPED/DONE */
    public void skip() {
        if (status != QuestionStatus.ANSWERING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "건너뛸 수 없는 질문입니다. (현재 상태: " + status + ")");
        }
        this.status = QuestionStatus.SKIPPED;
    }

    public void completeIntro() {
        if (type != QuestionType.INTRO || status != QuestionStatus.ANSWERING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "자기소개 단계가 아닙니다.");
        }
        this.status = QuestionStatus.DONE;
    }

    public void attachGenerationContext(Map<String, Object> context) {
        this.generationContext = context;
    }
}
