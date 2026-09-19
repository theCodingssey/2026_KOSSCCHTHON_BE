package com.kosscchthon.Icelink.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 호출(6~10초)을 요청 스레드에서 분리한다. 풀 크기는 spring.task.execution.pool.* 로 조정.
 * @Async 메서드는 Boot 가 만드는 applicationTaskExecutor 에서 실행된다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
