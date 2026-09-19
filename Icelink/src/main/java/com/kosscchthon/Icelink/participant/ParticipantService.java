package com.kosscchthon.Icelink.participant;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.dto.HostParticipantResponse;
import com.kosscchthon.Icelink.participant.dto.JoinRoomRequest;
import com.kosscchthon.Icelink.participant.dto.JoinRoomResponse;
import com.kosscchthon.Icelink.participant.dto.ParticipantMeResponse;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.user.User;
import com.kosscchthon.Icelink.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantAccessChecker participantAccessChecker;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    /** join 결과. created=false 면 이미 참가 중이던 행을 그대로 돌려준 것 (200). */
    public record JoinResult(JoinRoomResponse response, boolean created) {
    }

    /**
     * POST /rooms/{code}/participants — 방 참가 (P-01~P-03, P-06, R-08, R-09).
     * 검사 순서: 이미 참가 중(멱등) → 주최자 → 방 상태 → 다른 방 참가 중 → 정원. 닉네임 중복은 거절 대신 자동 변경.
     */
    @Transactional
    public JoinResult join(String code, User user, JoinRoomRequest request) {
        Room room = roomAccessChecker.getByCode(code);
        Optional<Participant> existing = participantRepository.findByRoom_IdAndUser_UserKey(room.getId(), user.getUserKey());

        if (existing.isPresent() && existing.get().isActive()) {
            return new JoinResult(JoinRoomResponse.from(existing.get(), room), false);
        }
        if (room.isHostedBy(user.getUserKey())) {
            throw new IcelinkException(ErrorCode.HOST_CANNOT_JOIN, "주최자는 자기 방에 참가자로 들어갈 수 없습니다.");
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IcelinkException(ErrorCode.ROOM_NOT_WAITING,
                    "이 방은 더 이상 참가를 받지 않습니다. (현재 상태: " + room.getStatus() + ")");
        }
        participantRepository.findActiveParticipation(user.getUserKey(), RoomStatus.ACTIVE)
                .filter(p -> !p.getRoomId().equals(room.getId()))
                .ifPresent(p -> {
                    throw new IcelinkException(ErrorCode.ALREADY_IN_ANOTHER_ROOM,
                            "이미 다른 방(" + p.getRoom().getCode() + ")에 참가 중입니다. 먼저 그 방에서 나가 주세요.",
                            Map.of("activeRoomCode", p.getRoom().getCode()));
                });

        int count = participantRepository.countByRoom_IdAndStatusNot(room.getId(), ParticipantStatus.LEFT);
        if (count >= Room.PARTICIPANT_LIMIT) {
            throw new IcelinkException(ErrorCode.ROOM_FULL, "방 인원이 가득 찼습니다. (최대 " + Room.PARTICIPANT_LIMIT + "명)");
        }

        String requested = resolveNickname(request, user);
        String nickname = resolveUniqueNickname(room, requested);

        Instant now = Instant.now(clock);
        Participant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            participant.rejoin(nickname, now);
        } else {
            try {
                participant = participantRepository.saveAndFlush(
                        Participant.join(room, userRepository.getReferenceById(user.getUserKey()), nickname, now));
            } catch (DataIntegrityViolationException e) {
                // 같은 유저의 동시 참가 요청. 먼저 들어간 행을 그대로 돌려준다.
                Participant winner = participantRepository.findByRoom_IdAndUser_UserKey(room.getId(), user.getUserKey())
                        .orElseThrow(() -> e);
                return new JoinResult(JoinRoomResponse.from(winner, room), false);
            }
        }

        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.PARTICIPANT_JOINED, Map.of(
                "participantId", participant.getId(),
                "nickname", participant.getNickname(),
                "participantCount", count + 1
        ), now));
        return new JoinResult(JoinRoomResponse.from(participant, room), true);
    }

    /** GET /rooms/{code}/me */
    @Transactional(readOnly = true)
    public ParticipantMeResponse getMe(String code, User user) {
        Room room = roomAccessChecker.getByCode(code);
        Participant me = participantAccessChecker.requireParticipant(room, user.getUserKey());
        int count = participantRepository.countByRoom_IdAndStatusNot(room.getId(), ParticipantStatus.LEFT);
        return ParticipantMeResponse.of(me, room, count, teamViewOf(me));
    }

    /** DELETE /rooms/{code}/me — 나가기 (P-04). WAITING 에서만. */
    @Transactional
    public void leave(String code, User user) {
        Room room = roomAccessChecker.getByCode(code);
        Participant me = participantAccessChecker.requireParticipant(room, user.getUserKey());
        requireWaiting(room, "팀 빌딩이 시작된 뒤에는 방을 나갈 수 없습니다.");
        markLeft(room, me);
    }

    /** GET /host/rooms/{code}/participants */
    @Transactional(readOnly = true)
    public List<HostParticipantResponse> listForHost(String code, User host) {
        Room room = roomAccessChecker.requireHost(code, host.getUserKey());
        return participantRepository.findAllByRoom_IdAndStatusNotOrderByJoinedAtAsc(room.getId(), ParticipantStatus.LEFT)
                .stream()
                .map(p -> HostParticipantResponse.from(p,
                        p.getTeam() == null ? null : p.getTeam().getId(),
                        p.getTeam() == null ? null : p.getTeam().getTeamNo()))
                .toList();
    }

    /** DELETE /host/rooms/{code}/participants/{participantId} — 강퇴. WAITING 에서만. */
    @Transactional
    public void kick(String code, User host, Long participantId) {
        Room room = roomAccessChecker.requireHost(code, host.getUserKey());
        Participant target = participantRepository.findByIdAndRoom_Id(participantId, room.getId())
                .filter(Participant::isActive)
                .orElseThrow(() -> new IcelinkException(ErrorCode.PARTICIPATION_NOT_FOUND, "이 방에 없는 참가자입니다."));
        requireWaiting(room, "팀 빌딩이 시작된 뒤에는 강퇴할 수 없습니다.");
        markLeft(room, target);
    }

    // ---- 내부 ----

    /** 배정된 팀이 있으면 팀 요약 + 팀원 목록(isMe 표시), 없으면 null. */
    private ParticipantMeResponse.TeamView teamViewOf(Participant me) {
        Team team = me.getTeam();
        if (team == null) {
            return null;
        }
        List<ParticipantMeResponse.TeamView.Member> members = participantRepository.findAllByTeam_IdOrderByIdAsc(team.getId())
                .stream()
                .map(p -> new ParticipantMeResponse.TeamView.Member(p.getId(), p.getNickname(), p.getId().equals(me.getId())))
                .toList();
        return new ParticipantMeResponse.TeamView(team.getId(), team.getTeamNo(), team.getName(),
                team.getStatus().name(), team.getCategory(), members);
    }

    private void markLeft(Room room, Participant participant) {
        Instant now = Instant.now(clock);
        // leave() 뒤에 count 쿼리를 날리면 auto-flush 로 이미 LEFT 가 반영되어 한 번 더 빼게 되므로, 먼저 읽는다.
        int before = participantRepository.countByRoom_IdAndStatusNot(room.getId(), ParticipantStatus.LEFT);
        participant.leave(now);
        int remaining = before - 1;
        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.PARTICIPANT_LEFT, Map.of(
                "participantId", participant.getId(),
                "participantCount", Math.max(remaining, 0)
        ), now));
    }

    private static void requireWaiting(Room room, String message) {
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    message + " (현재 상태: " + room.getStatus() + ")");
        }
    }

    /**
     * P-02: 방 안에서 닉네임이 겹치면 거절하지 않고 서버가 "민수2", "민수3" … 으로 바꿔 저장한다.
     * 후보(2~99)가 모두 쓰인 극단적 경우에만 409.
     */
    private String resolveUniqueNickname(Room room, String requested) {
        List<String> taken = participantRepository.findActiveNicknamesLower(room.getId());
        if (!taken.contains(requested.toLowerCase(Locale.ROOT))) {
            return requested;
        }
        String suggested = NicknameSuggester.suggest(requested, taken);
        if (suggested == null) {
            throw new IcelinkException(ErrorCode.NICKNAME_DUPLICATED,
                    "같은 닉네임이 너무 많아 자동으로 바꿀 수 없습니다. 다른 닉네임을 입력해 주세요.");
        }
        return suggested;
    }

    /** 닉네임 생략 시 유저 이름. trim 후 1~12자. */
    private static String resolveNickname(JoinRoomRequest request, User user) {
        String raw = request == null || request.nickname() == null || request.nickname().isBlank()
                ? user.getName()
                : request.nickname();
        String nickname = raw.trim();
        if (nickname.isEmpty() || nickname.length() > Participant.NICKNAME_MAX_LENGTH) {
            throw new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                    Map.of("errors", List.of(Map.of("field", "nickname", "message", "닉네임은 공백 제외 1~12자여야 합니다"))));
        }
        return nickname;
    }
}
