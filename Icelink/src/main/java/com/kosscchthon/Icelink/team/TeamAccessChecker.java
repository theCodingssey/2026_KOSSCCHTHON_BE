package com.kosscchthon.Icelink.team;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 팀 단위 역할 판정: 팀원(해당 팀에 속한 활성 참가자) 또는 그 방의 호스트 (0.1절). */
@Component
@RequiredArgsConstructor
public class TeamAccessChecker {

    private final TeamRepository teamRepository;
    private final ParticipantRepository participantRepository;

    public Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IcelinkException(ErrorCode.TEAM_NOT_FOUND, "존재하지 않는 팀입니다."));
    }

    /** 팀원이면 해당 참가자, 호스트면 empty. 둘 다 아니면 403. */
    public Optional<Participant> requireMemberOrHost(Team team, String userKey) {
        Optional<Participant> member = findMember(team, userKey);
        if (member.isPresent()) {
            return member;
        }
        if (team.getRoom().isHostedBy(userKey)) {
            return Optional.empty();
        }
        throw new IcelinkException(ErrorCode.FORBIDDEN, "이 팀의 팀원 또는 방 주최자만 접근할 수 있습니다.");
    }

    /** 팀원만. 호스트도 403. */
    public Participant requireMember(Team team, String userKey) {
        return findMember(team, userKey)
                .orElseThrow(() -> new IcelinkException(ErrorCode.FORBIDDEN, "이 팀의 팀원만 할 수 있는 요청입니다."));
    }

    private Optional<Participant> findMember(Team team, String userKey) {
        return participantRepository.findByRoom_IdAndUser_UserKey(team.getRoomId(), userKey)
                .filter(Participant::isActive)
                .filter(p -> p.getTeam() != null && p.getTeam().getId().equals(team.getId()));
    }
}
