package com.kosscchthon.Icelink.common.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간·난수 의존 로직을 테스트에서 고정할 수 있도록 빈으로 둔다. 서버·DB 모두 UTC. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
