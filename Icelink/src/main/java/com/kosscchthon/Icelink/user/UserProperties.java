package com.kosscchthon.Icelink.user;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * icelink.user.* 설정.
 * @param lastSeenThrottle last_seen_at 갱신 최소 간격
 */
@ConfigurationProperties(prefix = "icelink.user")
public record UserProperties(Duration lastSeenThrottle) {

    public UserProperties {
        if (lastSeenThrottle == null) {
            lastSeenThrottle = Duration.ofSeconds(60);
        }
    }
}
