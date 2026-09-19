package com.kosscchthon.Icelink.realtime;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantAccessChecker;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.TeamAccessChecker;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독 엔드포인트 3개 (docs 3.1절).
 * 접속 직후 CONNECTED 이벤트(현재 seq)를 보내고, Last-Event-ID 가 있으면 그 이후 이벤트를 재전송한다.
 * 유저 키는 X-User-Key 헤더 또는 ?userKey= 쿼리 (인터셉터가 처리).
 */
@RestController
@RequestMapping(WebMvcConfig.API_BASE)
@RequiredArgsConstructor
@Tag(name = "Realtime", description = "SSE 스트림. Accept: text/event-stream")
public class SseController {

    static final String CONNECTED_EVENT = "CONNECTED";

    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantAccessChecker participantAccessChecker;
    private final TeamAccessChecker teamAccessChecker;
    private final SseEmitterRegistry registry;
    private final RoomEventRepository eventRepository;
    private final SseParticipantView participantView;
    private final SseEventSerializer serializer;

    @GetMapping(value = "/host/rooms/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "주최자 스트림", description = "방 전체 + 모든 팀 이벤트")
    @Transactional(readOnly = true)
    public SseEmitter hostEvents(@PathVariable String code, @CurrentUser User user,
                                 @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Room room = roomAccessChecker.requireHost(code, user.getUserKey());
        SseSubscription sub = registry.subscribe(SseScope.HOST, room.getId(), null, null, user.getUserKey());
        return open(sub, lastEventId);
    }

    @GetMapping(value = "/rooms/{code}/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "참가자 스트림", description = "방 이벤트 + 자기 팀 이벤트. TEAM_BUILDING_COMPLETED / ROOM_FINISHED 에 myTeam 포함")
    @Transactional(readOnly = true)
    public SseEmitter participantEvents(@PathVariable String code, @CurrentUser User user,
                                        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Participant me = participantAccessChecker.requireParticipant(code, user.getUserKey());
        SseSubscription sub = registry.subscribe(SseScope.PARTICIPANT, me.getRoomId(), null, me.getId(), user.getUserKey());
        return open(sub, lastEventId);
    }

    @GetMapping(value = "/teams/{teamId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "팀 스트림", description = "해당 팀 이벤트 + ROOM_FINISHED. 팀원 또는 주최자")
    @Transactional(readOnly = true)
    public SseEmitter teamEvents(@PathVariable Long teamId, @CurrentUser User user,
                                 @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Team team = teamAccessChecker.getTeam(teamId);
        Optional<Participant> member = teamAccessChecker.requireMemberOrHost(team, user.getUserKey());
        SseSubscription sub = registry.subscribe(SseScope.TEAM, team.getRoomId(), teamId,
                member.map(Participant::getId).orElse(null), user.getUserKey());
        return open(sub, lastEventId);
    }

    // ---- 내부 ----

    private SseEmitter open(SseSubscription sub, String lastEventIdHeader) {
        Long lastEventId = parseLastEventId(lastEventIdHeader);
        RoomEventEntity latest = eventRepository.findFirstByRoomIdOrderByIdDesc(sub.roomId());
        long currentSeq = latest == null ? 0 : latest.getId();

        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("scope", sub.scope().name());
        hello.put("roomId", sub.roomId());
        hello.put("teamId", sub.teamId());
        hello.put("participantId", sub.participantId());
        hello.put("lastEventId", currentSeq);
        hello.put("replayFrom", lastEventId);
        if (!registry.send(sub, null, CONNECTED_EVENT, serializer.json(hello))) {
            return sub.emitter();
        }

        if (lastEventId != null && lastEventId < currentSeq) {
            replay(sub, lastEventId);
        }
        return sub.emitter();
    }

    private void replay(SseSubscription sub, long lastEventId) {
        List<RoomEventEntity> missed = eventRepository.findAllByRoomIdAndIdGreaterThanOrderByIdAsc(sub.roomId(), lastEventId);
        if (missed.isEmpty()) {
            return;
        }
        Map<Long, Long> participantTeams = sub.scope() == SseScope.PARTICIPANT
                ? participantView.participantTeams(sub.roomId())
                : Map.of();
        for (RoomEventEntity event : missed) {
            if (!sub.accepts(event, participantTeams)) {
                continue;
            }
            Map<String, Object> payload = sub.scope() == SseScope.PARTICIPANT
                    ? participantView.customizeForParticipant(sub.participantId(), event)
                    : event.getPayload();
            if (!registry.send(sub, event.getId(), event.getType().name(), serializer.data(event, payload))) {
                return;
            }
        }
    }

    private static Long parseLastEventId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
