package com.henryfll.auth.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockoutPolicyTest {

  private static final LockoutPolicy POLICY =
      new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24));
  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void lockDurationDoublesAfterTheThresholdUpToTheMaximum() {
    assertThat(POLICY.lockDurationAfter(4)).isZero();
    assertThat(POLICY.lockDurationAfter(5)).isEqualTo(Duration.ofMinutes(15));
    assertThat(POLICY.lockDurationAfter(6)).isEqualTo(Duration.ofMinutes(30));
    assertThat(POLICY.lockDurationAfter(8)).isEqualTo(Duration.ofHours(2));
    assertThat(POLICY.lockDurationAfter(12)).isEqualTo(Duration.ofHours(24));
    assertThat(POLICY.lockDurationAfter(1_000)).isEqualTo(Duration.ofHours(24));
    assertThatThrownBy(() -> new LockoutPolicy(0, Duration.ZERO, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void userIsLockedAfterTooManyFailuresAndUnlockedOnSuccessOrActivation() {
    User user = User.register(new Email("ana@example.com"), "{argon2}h", null, null, NOW);
    for (int i = 0; i < 4; i++) {
      assertThat(user.recordFailedLogin(POLICY, NOW)).isFalse();
    }
    assertThat(user.isTemporarilyLockedAt(NOW)).isFalse();

    assertThat(user.recordFailedLogin(POLICY, NOW)).isTrue();
    assertThat(user.failedLoginAttempts()).isEqualTo(5);
    assertThat(user.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(user.isTemporarilyLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
    assertThat(user.isTemporarilyLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();

    user.changeStatus(UserStatus.ACTIVE, NOW);
    assertThat(user.failedLoginAttempts()).isZero();
    assertThat(user.lockedUntil()).isNull();

    user.recordFailedLogin(POLICY, NOW);
    user.recordSuccessfulLogin(NOW);
    assertThat(user.failedLoginAttempts()).isZero();
  }

  @Test
  void adminResetClearsTheLockout() {
    User user = User.register(new Email("ana@example.com"), "{argon2}h", null, null, NOW);
    for (int i = 0; i < 5; i++) {
      user.recordFailedLogin(POLICY, NOW);
    }
    user.resetPasswordHash("{argon2}new", NOW.plusSeconds(1));
    assertThat(user.isTemporarilyLockedAt(NOW.plusSeconds(2))).isFalse();
    assertThat(user.passwordHash()).isEqualTo("{argon2}new");
    assertThat(user.passwordChangedAt()).isEqualTo(NOW.plusSeconds(1));
  }
}
