package com.kosscchthon.Icelink.realtime;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** SSE data 필드 JSON: {"roomId","teamId","occurredAt","payload"} (docs 3.2절). */
@Component
public class SseEventSerializer {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String data(RoomEventEntity event, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", event.getRoomId());
        data.put("teamId", event.getTeamId());
        data.put("occurredAt", event.getCreatedAt());
        data.put("payload", payload == null ? Map.of() : payload);
        return mapper.writeValueAsString(data);
    }

    public String json(Object value) {
        return mapper.writeValueAsString(value);
    }
}
