package com.kosscchthon.Icelink.realtime;

import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.team.Team;
import com.kosscchthon.Icelink.team.session.TeamAnswer;
import com.kosscchthon.Icelink.team.session.TeamAnswerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참가자 스트림용 부가 정보 (docs 3.3):
 * - 방 참가자 → 배정 팀 매핑 (팀 이벤트 필터링)
 * - TEAM_BUILDING_COMPLETED / ROOM_FINISHED 의 myTeam 채우기
 */
@Component
@RequiredArgsConstructor
public class SseParticipantView {

    private final ParticipantRepository participantRepository;
    private final TeamAnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public Map<Long, Long> participantTeams(Long roomId) {
        Map<Long, Long> map = new HashMap<>();
        for (Participant p : participantRepository.findAllByRoom_IdAndTeamIsNotNullOrderByIdAsc(roomId)) {
            map.put(p.getId(), p.getTeam().getId());
        }
        return map;
    }

    /** 참가자 구독에 맞게 payload 를 바꾼다. 해당 없는 이벤트는 원본 그대로. */
    @Transactional(readOnly = true)
    public Map<String, Object> customizeForParticipant(Long participantId, RoomEventEntity event) {
        Map<String, Object> base = event.getPayload() == null ? Map.of() : event.getPayload();
        return switch (event.getType()) {
            case TEAM_BUILDING_COMPLETED -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("teamCount", base.get("teamCount"));
                out.put("myTeam", myTeamForBuilding(participantId));
                yield out;
            }
            case ROOM_FINISHED -> {
                Map<String, Object> out = new LinkedHashMap<>(base);
                out.put("myTeam", myTeamForFinish(participantId));
                yield out;
            }
            default -> base;
        };
    }

    private Map<String, Object> myTeamForBuilding(Long participantId) {
        Team team = teamOf(participantId);
        if (team == null) {
            return null;
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (Participant p : participantRepository.findAllByTeam_IdOrderByIdAsc(team.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("participantId", p.getId());
            m.put("nickname", p.getNickname());
            m.put("isMe", p.getId().equals(participantId));
            members.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("teamId", team.getId());
        out.put("teamNo", team.getTeamNo());
        out.put("name", team.getName());
        out.put("category", team.getCategory().name());
        out.put("members", members);
        return out;
    }

    private Map<String, Object> myTeamForFinish(Long participantId) {
        Team team = teamOf(participantId);
        if (team == null) {
            return null;
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (TeamAnswer a : answerRepository.findAllByQuestion_Team_IdOrderByQuestion_OrderNoAsc(team.getId())) {
            keywords.addAll(a.getKeywords());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("teamId", team.getId());
        out.put("name", team.getName());
        out.put("keywords", new ArrayList<>(keywords));
        return out;
    }

    private Team teamOf(Long participantId) {
        return participantRepository.findById(participantId).map(Participant::getTeam).orElse(null);
    }
}
