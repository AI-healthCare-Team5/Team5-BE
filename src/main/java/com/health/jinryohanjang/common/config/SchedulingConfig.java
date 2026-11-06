package com.health.jinryohanjang.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 작업(병원 동기화 등)을 켠다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
