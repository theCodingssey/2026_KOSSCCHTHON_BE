package com.kosscchthon.Icelink.realtime;

/**
 * 도메인 서비스가 이벤트를 발행하는 단일 진입점.
 * 3.6 실시간 구현에서 SSE 브로드캐스트 + room_events 저장 구현체로 교체된다.
 * 서비스 계층은 이 인터페이스만 의존한다.
 */
public interface RoomEventPublisher {

    void publish(RoomEvent event);
}
