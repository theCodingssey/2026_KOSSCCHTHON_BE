package com.kosscchthon.Icelink.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * - @EnableAsync: AI 호출(6~10초)을 요청 스레드에서 분리. 풀 크기는 spring.task.execution.pool.*
 * - @EnableScheduling: SSE keep-alive(15초) 스케줄러
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
