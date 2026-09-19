package com.kosscchthon.Icelink.realtime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEventRepository extends JpaRepository<RoomEventEntity, Long> {

    /** 재연결 재전송: 해당 방에서 lastEventId 이후 발생한 이벤트 (id 순) */
    List<RoomEventEntity> findAllByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long lastEventId);

    /** 접속 시 현재 seq 를 알려주기 위한 마지막 id */
    RoomEventEntity findFirstByRoomIdOrderByIdDesc(Long roomId);
}
