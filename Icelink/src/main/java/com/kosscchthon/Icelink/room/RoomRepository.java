package com.kosscchthon.Icelink.room;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    boolean existsByCode(String code);

    /** 유저가 주최 중인(종료되지 않은) 방 중 가장 최근 것. R-01 중복 주최 방지와 activeRoom 판정에 사용. */
    Optional<Room> findFirstByHost_UserKeyAndStatusInOrderByCreatedAtDesc(String hostUserKey, Collection<RoomStatus> statuses);

    boolean existsByHost_UserKeyAndStatusIn(String hostUserKey, Collection<RoomStatus> statuses);
}
