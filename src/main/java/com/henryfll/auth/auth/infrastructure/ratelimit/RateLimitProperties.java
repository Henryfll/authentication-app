package com.henryfll.auth.auth.infrastructure.ratelimit;

import com.henryfll.auth.auth.application.port.out.AttemptLimiter.Limit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de intentos ({@code app.security.rate-limit.*}). Cada límite permite {@code capacity}
 * intentos por {@code period}; {@code capacity <= 0} lo desactiva.
 */
@ConfigurationProperties("app.security.rate-limit")
record RateLimitProperties(Policy loginByIp, Policy loginByEmail, Policy refreshByIp) {

  record Policy(int capacity, Duration period) {}

  Policy policy(Limit limit) {
    Policy policy =
        switch (limit) {
          case LOGIN_BY_IP -> loginByIp;
          case LOGIN_BY_EMAIL -> loginByEmail;
          case REFRESH_BY_IP -> refreshByIp;
        };
    return policy == null ? new Policy(0, Duration.ofMinutes(1)) : policy;
  }
}
