package com.kosscchthon.Icelink.user;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

    /**
     * last_seen_at 이 threshold 보다 오래된 경우에만 now 로 갱신한다.
     * 매 요청마다 엔티티를 dirty-check 하지 않고 단일 UPDATE 로 처리하기 위한 쿼리.
     * @return 갱신된 행 수 (0 또는 1)
     */
    @Modifying
    @Query("update User u set u.lastSeenAt = :now where u.userKey = :userKey and u.lastSeenAt < :threshold")
    int touchLastSeenIfStale(@Param("userKey") String userKey,
                             @Param("now") Instant now,
                             @Param("threshold") Instant threshold);
}
