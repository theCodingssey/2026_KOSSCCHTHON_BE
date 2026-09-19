package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.survey.PersonalitySurvey;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방 참가자. (room, user) 당 한 행이며, 나간 뒤 재입장하면 같은 행을 JOINED 로 재활성화한다.
 * 닉네임은 방 안에서 대소문자 무시 유일 (LEFT 제외) — 애플리케이션 레벨에서 검사.
 */
@Entity
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(name = "ux_participants_room_user", columnNames = {"room_id", "user_id"}),
        indexes = {
                @Index(name = "ix_participants_room_status", columnList = "room_id, status"),
                @Index(name = "ix_participants_user_status", columnList = "user_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {

    public static final int NICKNAME_MAX_LENGTH = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "nickname", length = NICKNAME_MAX_LENGTH, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ParticipantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_category", length = 10)
    private InterestCategory interestCategory;

    /** 성격 6문항 점수 합 (6~30). 성격 제출 전 null. */
    @Column(name = "extroversion_score")
    private Integer extroversionScore;

    /** 성격 문항별 점수 (question_no → score). 재제출 시 전체 교체. survey_answers 테이블. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "survey_answers", joinColumns = @JoinColumn(name = "participant_id"))
    @MapKeyColumn(name = "question_no")
    @Column(name = "score", nullable = false)
    private Map<Integer, Integer> personalityAnswers = new HashMap<>();

    /** 배정된 팀. 팀 빌딩 전 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Participant(Room room, User user, String nickname, Instant now) {
        this.room = room;
        this.user = user;
        this.nickname = nickname;
        this.status = ParticipantStatus.JOINED;
        this.joinedAt = now;
    }

    public static Participant join(Room room, User user, String nickname, Instant now) {
        return new Participant(room, user, nickname, now);
    }

    // ---- 조회 ----

    public Long getRoomId() {
        return room.getId();
    }

    public String getUserKey() {
        return user.getUserKey();
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean isPersonalityDone() {
        return extroversionScore != null;
    }

    public boolean isCategoryDone() {
        return interestCategory != null;
    }

    public boolean isSurveyDone() {
        return isPersonalityDone() && isCategoryDone();
    }

    /** 문항별 답 (읽기 전용 복사). 성격 미제출이면 빈 맵. */
    public Map<Integer, Integer> getPersonalityAnswers() {
        return Map.copyOf(personalityAnswers);
    }

    // ---- 변경 ----

    /** 나간 뒤 다시 들어올 때. 설문은 초기화한다. */
    public void rejoin(String nickname, Instant now) {
        if (isActive()) {
            throw new IllegalStateException("Participant is already active");
        }
        this.nickname = nickname;
        this.status = ParticipantStatus.JOINED;
        this.interestCategory = null;
        this.extroversionScore = null;
        this.personalityAnswers.clear();
        this.joinedAt = now;
        this.leftAt = null;
    }

    /** P-04 / 강퇴. 팀 빌딩 전(JOINED, SURVEY_DONE)에만 가능. */
    public void leave(Instant now) {
        if (!status.canLeave()) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "팀 배정 이후에는 방을 나갈 수 없습니다. (현재 상태: " + status + ")");
        }
        this.status = ParticipantStatus.LEFT;
        this.leftAt = now;
    }

    /**
     * 3.3 설문: 성격 6문항 답을 저장하고 합을 외향 점수로 기록한다. 재제출 시 전체 교체.
     * 둘 다 완료되면 SURVEY_DONE.
     * @throws IllegalArgumentException 문항 수·번호·점수 범위 위반
     */
    public void submitPersonality(Map<Integer, Integer> answers) {
        requireSurveyEditable();
        int sum = PersonalitySurvey.validateAndSum(answers);
        this.personalityAnswers.clear();
        this.personalityAnswers.putAll(answers);
        this.extroversionScore = sum;
        refreshSurveyStatus();
    }

    /** 3.3 설문: 관심사 카테고리 저장. 둘 다 완료되면 SURVEY_DONE. */
    public void selectCategory(InterestCategory category) {
        requireSurveyEditable();
        this.interestCategory = category;
        refreshSurveyStatus();
    }

    /** 3.4 팀 빌딩: 팀에 배정. SURVEY_DONE(기본) 또는 JOINED/LATE(미완료 포함 옵션) 에서 ASSIGNED 로. */
    public void assignTo(Team team) {
        if (status != ParticipantStatus.SURVEY_DONE && status != ParticipantStatus.LATE && status != ParticipantStatus.JOINED) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "배정할 수 없는 참가자 상태입니다: " + status);
        }
        this.team = team;
        this.status = ParticipantStatus.ASSIGNED;
    }

    /** 3.4 팀 빌딩: 설문 미완료로 마감에서 제외. */
    public void markLate() {
        if (status != ParticipantStatus.JOINED) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "LATE 로 바꿀 수 없는 참가자 상태입니다: " + status);
        }
        this.status = ParticipantStatus.LATE;
    }

    private void requireSurveyEditable() {
        if (status != ParticipantStatus.JOINED && status != ParticipantStatus.SURVEY_DONE) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "설문은 팀 배정 전에만 제출할 수 있습니다. (현재 상태: " + status + ")");
        }
    }

    private void refreshSurveyStatus() {
        this.status = isSurveyDone() ? ParticipantStatus.SURVEY_DONE : ParticipantStatus.JOINED;
    }
}
