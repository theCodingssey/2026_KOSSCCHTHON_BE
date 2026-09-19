package com.kosscchthon.Icelink.room.port;

import com.kosscchthon.Icelink.room.dto.RoomTeamSummary;
import java.time.Instant;
import java.util.List;

/**
 * room 모듈이 팀 데이터를 읽고 종료시키는 포트. team 모듈(3.4/3.5)이 구현한다.
 */
public interface RoomTeamQuery {

    /** 주최자 상세용 팀 요약 목록 (팀 번호 순) */
    List<RoomTeamSummary> listByRoom(Long roomId);

    /**
     * 방 종료 시 모든 팀을 FINISHED 로 전이한다.
     * @return 이번 호출로 종료된 팀 수
     */
    int finishAll(Long roomId, Instant now);
}
