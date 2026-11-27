package com.henryfll.auth.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable: los servicios no llaman a {@code Instant.now()} directamente (testeable). */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
