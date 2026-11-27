package com.henryfll.auth.auth.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.henryfll.auth.auth.application.port.out.AttemptLimiter.Limit;
import com.henryfll.auth.auth.infrastructure.ratelimit.RateLimitProperties.Policy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Bucket4jAttemptLimiterTest {

  private final Bucket4jAttemptLimiter limiter =
      new Bucket4jAttemptLimiter(
          new RateLimitProperties(
              new Policy(3, Duration.ofMinutes(1)), new Policy(0, Duration.ofMinutes(1)), null));

  @Test
  void allowsUpToTheCapacityPerKeyAndThenReportsTheWait() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire(Limit.LOGIN_BY_IP, "10.0.0.1")).isEmpty();
    }
    assertThat(limiter.tryAcquire(Limit.LOGIN_BY_IP, "10.0.0.1"))
        .hasValueSatisfying(
            wait -> assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1)));

    // Otra clave tiene su propio bucket.
    assertThat(limiter.tryAcquire(Limit.LOGIN_BY_IP, "10.0.0.2")).isEmpty();
  }

  @Test
  void keysAreCaseInsensitive() {
    for (int i = 0; i < 3; i++) {
      limiter.tryAcquire(Limit.LOGIN_BY_IP, "Host-A");
    }
    assertThat(limiter.tryAcquire(Limit.LOGIN_BY_IP, " host-a ")).isPresent();
  }

  @Test
  void disabledOrMissingPoliciesAndBlankKeysAreNotLimited() {
    for (int i = 0; i < 10; i++) {
      assertThat(limiter.tryAcquire(Limit.LOGIN_BY_EMAIL, "a@b.com")).isEmpty();
      assertThat(limiter.tryAcquire(Limit.REFRESH_BY_IP, "10.0.0.1")).isEmpty();
      assertThat(limiter.tryAcquire(Limit.LOGIN_BY_IP, null)).isEmpty();
    }
  }
}
