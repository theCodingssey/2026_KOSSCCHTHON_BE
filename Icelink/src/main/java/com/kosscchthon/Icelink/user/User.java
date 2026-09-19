package com.kosscchthon.Icelink.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저. 회원가입 없이 클라이언트가 만든 sha256(name + nonce) 키가 PK 다.
 * 역할(호스트/참가자)은 여기 없고 방 단위로 판정한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final int KEY_LENGTH = 64;
    public static final int NAME_MAX_LENGTH = 12;

    @Id
    @Column(name = "user_key", length = KEY_LENGTH, nullable = false, updatable = false)
    private String userKey;

    @Column(name = "name", length = NAME_MAX_LENGTH, nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    private User(String userKey, String name, Instant now) {
        this.userKey = userKey;
        this.name = name;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public static User register(String userKey, String name, Instant now) {
        return new User(userKey, name, now);
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
