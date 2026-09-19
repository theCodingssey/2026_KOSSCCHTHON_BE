package com.kosscchthon.Icelink.room;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.dto.CreateRoomRequest;
import com.kosscchthon.Icelink.room.dto.FinalQuestionsResponse;
import com.kosscchthon.Icelink.room.dto.FinishRoomResponse;
import com.kosscchthon.Icelink.room.dto.RoomDetailResponse;
import com.kosscchthon.Icelink.room.dto.RoomPublicResponse;
import com.kosscchthon.Icelink.room.dto.RoomResponse;
import com.kosscchthon.Icelink.room.dto.UpdateFinalQuestionsRequest;
import com.kosscchthon.Icelink.room.dto.UpdateRoomRequest;
import com.kosscchthon.Icelink.room.port.RoomParticipantQuery;
import com.kosscchthon.Icelink.room.port.RoomTeamQuery;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomCodeGenerator codeGenerator;
    private final RoomAccessChecker accessChecker;
    private final RoomParticipantQuery participantQuery;
    private final RoomTeamQuery teamQuery;
    private final RoomEventPublisher eventPublisher;
    private final RoomProperties properties;
    private final Clock clock;

    /** POST /rooms — 방 생성. 요청 유저가 주최자. 진행 중인 방을 이미 주최 중이면 409. */
    @Transactional
    public RoomResponse create(User host, CreateRoomRequest request) {
        if (roomRepository.existsByHost_UserKeyAndStatusIn(host.getUserKey(), RoomStatus.ACTIVE)) {
            throw new IcelinkException(ErrorCode.ALREADY_IN_ANOTHER_ROOM,
                    "이미 진행 중인 방을 주최하고 있습니다. 먼저 그 방을 종료해 주세요.");
        }

        Instant now = Instant.now(clock);
        Room room = Room.create(
                codeGenerator.generateUnique(),
                userRepository.getReferenceById(host.getUserKey()),
                request.title().trim(),
                request.situation().trim(),
                request.teamSize(),
                normalizeFinalQuestions(request.finalQuestions()),
                now,
                properties.ttl());
        roomRepository.save(room);
        return RoomResponse.from(room, host.getName(), properties);
    }

    /** GET /rooms/{code} — 공개 정보. 인증 없음. */
    @Transactional(readOnly = true)
    public RoomPublicResponse getPublic(String code) {
        Room room = accessChecker.getByCode(code);
        return RoomPublicResponse.from(room, participantQuery.countActive(room.getId()));
    }

    /** GET /host/rooms/{code} — 주최자 대시보드. */
    @Transactional(readOnly = true)
    public RoomDetailResponse getHostDetail(String code, User user) {
        Room room = accessChecker.requireHost(code, user.getUserKey());
        return RoomDetailResponse.from(
                room,
                properties,
                participantQuery.counts(room.getId()),
                participantQuery.listActive(room.getId()),
                teamQuery.listByRoom(room.getId()));
    }

    /** PATCH /host/rooms/{code} — 제목·상황·팀 인원 수정. WAITING 에서만. */
    @Transactional
    public RoomResponse updateSettings(String code, User user, UpdateRoomRequest request) {
        Room room = accessChecker.requireHost(code, user.getUserKey());
        if (!request.isEmpty()) {
            room.updateSettings(
                    request.title() == null ? null : request.title().trim(),
                    request.situation() == null ? null : request.situation().trim(),
                    request.teamSize(),
                    Instant.now(clock));
            publishRoomUpdated(room);
        }
        return RoomResponse.from(room, user.getName(), properties);
    }

    /** PUT /host/rooms/{code}/final-questions — 종료 전까지 언제든. */
    @Transactional
    public FinalQuestionsResponse updateFinalQuestions(String code, User user, UpdateFinalQuestionsRequest request) {
        Room room = accessChecker.requireHost(code, user.getUserKey());
        room.replaceFinalQuestions(normalizeFinalQuestions(request.finalQuestions()), Instant.now(clock));
        publishRoomUpdated(room);
        return new FinalQuestionsResponse(room.getFinalQuestions(), room.getUpdatedAt());
    }

    /**
     * POST /host/rooms/{code}/finish — 아이스브레이킹 종료 (R-05). 멱등.
     * 방과 모든 팀을 FINISHED 로 만들고, 마무리 질문 목록을 ROOM_FINISHED 이벤트로 전파한다.
     */
    @Transactional
    public FinishRoomResponse finish(String code, User user) {
        Room room = accessChecker.requireHost(code, user.getUserKey());
        Instant now = Instant.now(clock);

        int finishedTeams = 0;
        if (room.finish(now)) {
            finishedTeams = teamQuery.finishAll(room.getId(), now);
            eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.ROOM_FINISHED, Map.of(
                    "finishedAt", room.getFinishedAt(),
                    "finalQuestions", room.getFinalQuestions()
            ), now));
        }
        return new FinishRoomResponse(room.getStatus(), room.getFinishedAt(), finishedTeams, room.getFinalQuestions());
    }

    // ---- 내부 ----

    private void publishRoomUpdated(Room room) {
        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.ROOM_UPDATED, Map.of(
                "title", room.getTitle(),
                "teamSize", room.getTeamSize(),
                "finalQuestionCount", room.getFinalQuestions().size()
        ), room.getUpdatedAt()));
    }

    /** trim 후 빈 문자열 제거. 길이는 Bean Validation 이 trim 전 값으로 검사하므로 trim 후 한 번 더 확인. */
    private static List<String> normalizeFinalQuestions(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> normalized = raw.stream()
                .map(q -> q == null ? "" : q.trim())
                .filter(q -> !q.isEmpty())
                .toList();
        if (normalized.size() > Room.FINAL_QUESTIONS_MAX
                || normalized.stream().anyMatch(q -> q.length() > Room.FINAL_QUESTION_MAX_LENGTH)) {
            throw new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                    Map.of("errors", List.of(Map.of("field", "finalQuestions",
                            "message", "마무리 질문은 최대 5개, 각 200자 이하여야 합니다"))));
        }
        return normalized;
    }
}
