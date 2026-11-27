package com.henryfll.auth.user.infrastructure.config;

import com.henryfll.auth.user.domain.LockoutPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bloqueo progresivo por intentos fallidos ({@code app.security.lockout.*}). */
@ConfigurationProperties("app.security.lockout")
record LockoutProperties(
    Integer maxFailedAttempts, Duration baseLockDuration, Duration maxLockDuration) {

  LockoutPolicy toPolicy() {
    return new LockoutPolicy(
        maxFailedAttempts == null ? 5 : maxFailedAttempts,
        baseLockDuration == null ? Duration.ofMinutes(15) : baseLockDuration,
        maxLockDuration == null ? Duration.ofHours(24) : maxLockDuration);
  }

  @Configuration(proxyBeanMethods = false)
  static class LockoutConfig {

    @Bean
    LockoutPolicy lockoutPolicy(LockoutProperties properties) {
      return properties.toPolicy();
    }
  }
}
