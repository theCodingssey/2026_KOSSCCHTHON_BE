package com.kosscchthon.Icelink.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SSE 구현 전까지 쓰는 기본 발행기. 로그만 남긴다.
 * realtime 모듈이 SseRoomEventPublisher 를 제공하면 이 클래스를 삭제한다.
 */
@Slf4j
@Component
class LoggingRoomEventPublisher implements RoomEventPublisher {

    @Override
    public void publish(RoomEvent event) {
        log.info("[event] room={} team={} type={} payload={}",
                event.roomId(), event.teamId(), event.type(), event.payload());
    }
}
