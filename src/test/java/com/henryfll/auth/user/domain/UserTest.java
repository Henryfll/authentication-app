package com.henryfll.auth.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant LATER = NOW.plusSeconds(60);

  private static User newUser() {
    return User.register(new Email("ana@example.com"), "{argon2}hash", " Ana ", "  ", NOW);
  }

  @Test
  void registeredUsersAreActiveAndUnverified() {
    User user = newUser();
    assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(user.firstName()).isEqualTo("Ana");
    assertThat(user.lastName()).isNull();
    assertThat(user.createdAt()).isEqualTo(NOW);
    assertThat(user.passwordChangedAt()).isEqualTo(NOW);
    assertThat(user.failedLoginAttempts()).isZero();
  }

  @Test
  void changingStatusUpdatesTimestampOnlyWhenItChanges() {
    User user = newUser();
    user.changeStatus(UserStatus.ACTIVE, LATER);
    assertThat(user.updatedAt()).isEqualTo(NOW);

    user.changeStatus(UserStatus.LOCKED, LATER);
    assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
    assertThat(user.updatedAt()).isEqualTo(LATER);
  }

  @Test
  void profileAndPasswordCanBeUpdated() {
    User user = newUser();
    user.updateProfile("Ana María", "García", LATER);
    user.changePasswordHash("{argon2}other", LATER);
    assertThat(user.firstName()).isEqualTo("Ana María");
    assertThat(user.lastName()).isEqualTo("García");
    assertThat(user.passwordHash()).isEqualTo("{argon2}other");
    assertThat(user.passwordChangedAt()).isEqualTo(LATER);
  }

  @Test
  void rejectsNamesThatAreTooLongAndBlankHashes() {
    User user = newUser();
    assertThatThrownBy(() -> user.updateProfile("x".repeat(101), null, LATER))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> user.changePasswordHash(" ", LATER))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
