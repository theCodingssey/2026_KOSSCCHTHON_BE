package com.kosscchthon.Icelink.room;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * icelink.room.* 설정.
 * @param inviteBaseUrl  초대 링크 prefix. 뒤에 코드가 붙는다. 예: https://icelink.app/join
 * @param deepLinkPrefix 딥링크 prefix. 뒤에 코드가 붙는다. 예: icelink://join?code=
 * @param ttl            생성 후 만료까지 기간 (R-06)
 */
@ConfigurationProperties(prefix = "icelink.room")
public record RoomProperties(String inviteBaseUrl, String deepLinkPrefix, Duration ttl) {

    public RoomProperties {
        if (inviteBaseUrl == null || inviteBaseUrl.isBlank()) {
            inviteBaseUrl = "https://icelink.app/join";
        }
        if (deepLinkPrefix == null || deepLinkPrefix.isBlank()) {
            deepLinkPrefix = "icelink://join?code=";
        }
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
        inviteBaseUrl = inviteBaseUrl.endsWith("/") ? inviteBaseUrl.substring(0, inviteBaseUrl.length() - 1) : inviteBaseUrl;
    }

    public String inviteUrl(String code) {
        return inviteBaseUrl + "/" + code;
    }

    public String deepLink(String code) {
        return deepLinkPrefix + code;
    }
}
