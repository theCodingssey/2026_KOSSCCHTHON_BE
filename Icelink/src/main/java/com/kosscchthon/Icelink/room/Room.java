package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.user.User;
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
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 방. 생성자가 주최자(host)이며 역할 판정의 기준이 된다.
 * 상태 전이는 이 클래스 안에서만 일어나고, 허용되지 않은 전이는 INVALID_STATE_TRANSITION 을 던진다.
 */
@Entity
@Table(name = "rooms", indexes = {
        @Index(name = "ux_rooms_code", columnList = "code", unique = true),
        @Index(name = "ix_rooms_host_status", columnList = "host_user_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    public static final int CODE_LENGTH = 6;
    public static final int TITLE_MAX_LENGTH = 50;
    public static final int SITUATION_MAX_LENGTH = 500;
    public static final int TEAM_SIZE_MIN = 2;
    public static final int TEAM_SIZE_MAX = 10;
    public static final int FINAL_QUESTIONS_MAX = 5;
    public static final int FINAL_QUESTION_MAX_LENGTH = 200;
    public static final int PARTICIPANT_LIMIT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = CODE_LENGTH, nullable = false, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false, updatable = false)
    private User host;

    @Column(name = "title", length = TITLE_MAX_LENGTH, nullable = false)
    private String title;

    @Column(name = "situation", nullable = false, columnDefinition = "text")
    private String situation;

    @Column(name = "team_size", nullable = false)
    private int teamSize;

    /** 주최자 마무리 질문 목록. 종료 시 모든 참가자 화면에 표시된다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_questions", nullable = false, columnDefinition = "jsonb")
    private List<String> finalQuestions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Room(String code, User host, String title, String situation, int teamSize,
                 List<String> finalQuestions, Instant now, Duration ttl) {
        this.code = code;
        this.host = host;
        this.title = title;
        this.situation = situation;
        this.teamSize = teamSize;
        this.finalQuestions = new ArrayList<>(finalQuestions);
        this.status = RoomStatus.WAITING;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = now.plus(ttl);
    }

    public static Room create(String code, User host, String title, String situation, int teamSize,
                              List<String> finalQuestions, Instant now, Duration ttl) {
        return new Room(code, host, title, situation, teamSize, finalQuestions, now, ttl);
    }

    // ---- 조회 ----

    /** 주최자 키. LAZY 프록시의 식별자만 읽으므로 추가 쿼리가 나가지 않는다. */
    public String getHostUserKey() {
        return host.getUserKey();
    }

    public boolean isHostedBy(String userKey) {
        return getHostUserKey().equals(userKey);
    }

    public boolean isActive() {
        return status.isActive();
    }

    public List<String> getFinalQuestions() {
        return List.copyOf(finalQuestions);
    }

    // ---- 변경 ----

    /** R-04: 제목·상황·팀 인원은 WAITING 에서만. null 인 필드는 유지. */
    public void updateSettings(String title, String situation, Integer teamSize, Instant now) {
        requireStatus(RoomStatus.WAITING, "방 설정은 대기 중(WAITING)일 때만 수정할 수 있습니다.");
        if (title != null) {
            this.title = title;
        }
        if (situation != null) {
            this.situation = situation;
        }
        if (teamSize != null) {
            this.teamSize = teamSize;
        }
        this.updatedAt = now;
    }

    /** R-04: 마무리 질문은 종료 전까지 언제든 수정 가능. 목록 전체 덮어쓰기. */
    public void replaceFinalQuestions(List<String> questions, Instant now) {
        if (!isActive()) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION, "이미 종료된 방입니다.");
        }
        this.finalQuestions = new ArrayList<>(questions);
        this.updatedAt = now;
    }

    public void startTeamBuilding(Instant now) {
        transitionTo(RoomStatus.TEAM_BUILDING, now);
    }

    public void completeTeamBuilding(Instant now) {
        transitionTo(RoomStatus.IN_PROGRESS, now);
    }

    /** 팀 빌딩 실패 시 WAITING 으로 되돌린다. */
    public void rollbackTeamBuilding(Instant now) {
        transitionTo(RoomStatus.WAITING, now);
    }

    /**
     * R-05: 아이스브레이킹 종료. 어떤 진행 상태에서도 가능하며 멱등.
     * @return 이번 호출로 실제 전이가 일어났으면 true
     */
    public boolean finish(Instant now) {
        if (status == RoomStatus.FINISHED) {
            return false;
        }
        this.status = RoomStatus.FINISHED;
        this.finishedAt = now;
        this.updatedAt = now;
        return true;
    }

    private void transitionTo(RoomStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "방 상태를 " + status + " 에서 " + next + " 로 바꿀 수 없습니다.");
        }
        this.status = next;
        this.updatedAt = now;
    }

    private void requireStatus(RoomStatus expected, String message) {
        if (status != expected) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    message + " (현재 상태: " + status + ")");
        }
    }
}
