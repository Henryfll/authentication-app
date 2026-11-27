package com.henryfll.auth.user.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Bloqueo progresivo por intentos fallidos (docs/ARCHITECTURE.md §8): al alcanzar {@code
 * maxFailedAttempts} la cuenta se bloquea {@code baseLockDuration}, y cada fallo adicional duplica
 * el bloqueo hasta {@code maxLockDuration}.
 */
public record LockoutPolicy(
    int maxFailedAttempts, Duration baseLockDuration, Duration maxLockDuration) {

  public LockoutPolicy {
    if (maxFailedAttempts < 1) {
      throw new IllegalArgumentException("maxFailedAttempts must be at least 1");
    }
    Objects.requireNonNull(baseLockDuration, "baseLockDuration");
    Objects.requireNonNull(maxLockDuration, "maxLockDuration");
  }

  /** Duración del bloqueo tras {@code failures} fallos; cero si aún no corresponde bloquear. */
  public Duration lockDurationAfter(int failures) {
    if (failures < maxFailedAttempts) {
      return Duration.ZERO;
    }
    int doublings = Math.min(failures - maxFailedAttempts, 30);
    Duration duration = baseLockDuration.multipliedBy(1L << doublings);
    return duration.compareTo(maxLockDuration) > 0 ? maxLockDuration : duration;
  }
}
