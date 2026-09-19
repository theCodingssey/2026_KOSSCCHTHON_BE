package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.InterestCategory;
import com.kosscchthon.Icelink.room.Room;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀. 팀 빌딩 시 생성되며 팀명은 "{teamNo}팀" 으로 초기화된다 (T-08).
 * 소속 참가자는 participants.team_id 로 연결된다.
 */
@Entity
@Table(name = "teams",
        uniqueConstraints = @UniqueConstraint(name = "ux_teams_room_no", columnNames = {"room_id", "team_no"}),
        indexes = @Index(name = "ix_teams_room", columnList = "room_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    public static final int NAME_MAX_LENGTH = 20;
    public static final int QUESTION_LIMIT = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @Column(name = "team_no", nullable = false, updatable = false)
    private int teamNo;

    @Column(name = "name", length = NAME_MAX_LENGTH, nullable = false)
    private String name;

    /** 팀 구성 기준 카테고리. AI 질문 생성 기준. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 10, nullable = false)
    private InterestCategory category;

    /** 다른 카테고리의 잔여 인원이 섞였는지 */
    @Column(name = "mixed", nullable = false)
    private boolean mixed;

    /** 빌딩 시점 팀 외향 점수 평균 (주최자 확인용) */
    @Column(name = "extroversion_avg")
    private Double extroversionAvg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TeamStatus status;

    /** 생성된 질문 수 (INTRO 포함) */
    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Team(Room room, int teamNo, InterestCategory category, boolean mixed, double extroversionAvg) {
        this.room = room;
        this.teamNo = teamNo;
        this.name = defaultName(teamNo);
        this.category = category;
        this.mixed = mixed;
        this.extroversionAvg = Math.round(extroversionAvg * 10) / 10.0;
        this.status = TeamStatus.NOT_STARTED;
        this.questionCount = 0;
    }

    public static Team create(Room room, int teamNo, InterestCategory category, boolean mixed, double extroversionAvg) {
        return new Team(room, teamNo, category, mixed, extroversionAvg);
    }

    public static String defaultName(int teamNo) {
        return teamNo + "팀";
    }

    // ---- 조회 ----

    public Long getRoomId() {
        return room.getId();
    }

    public boolean isDefaultName() {
        return defaultName(teamNo).equals(name);
    }

    public boolean isFinished() {
        return status.isFinished();
    }

    // ---- 변경 ----

    /** Q-03: 팀명 수정. null/빈 문자열이면 기본값 복원. FINISHED 에서는 불가. */
    public void rename(String newName) {
        if (isFinished()) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "종료된 팀의 이름은 바꿀 수 없습니다.");
        }
        this.name = (newName == null || newName.isBlank()) ? defaultName(teamNo) : newName.trim();
    }

    /** 3.4/T-06: 잔여 인원이 합류했을 때 */
    public void markMixed() {
        this.mixed = true;
    }

    /** Q-01: "모두 모였어요". 이미 시작됐으면 false (멱등). */
    public boolean start(Instant now) {
        if (status != TeamStatus.NOT_STARTED) {
            return false;
        }
        transitionTo(TeamStatus.NAMING);
        this.startedAt = now;
        return true;
    }

    /** NAMING → QUESTIONING */
    public void beginQuestioning() {
        transitionTo(TeamStatus.QUESTIONING);
    }

    public void incrementQuestionCount() {
        this.questionCount++;
    }

    public boolean hasReachedQuestionLimit() {
        return questionCount >= QUESTION_LIMIT;
    }

    /** 방 종료에 따른 팀 종료. 멱등. @return 이번 호출로 전이했으면 true */
    public boolean finish(Instant now) {
        if (isFinished()) {
            return false;
        }
        this.status = TeamStatus.FINISHED;
        this.finishedAt = now;
        return true;
    }

    private void transitionTo(TeamStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "팀 상태를 " + status + " 에서 " + next + " 로 바꿀 수 없습니다.");
        }
        this.status = next;
    }
}
