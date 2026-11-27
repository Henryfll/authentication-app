package com.henryfll.auth.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessGrantTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID APP = UUID.randomUUID();
  private static final UUID ROLE = UUID.randomUUID();

  private static AccessGrant grant(Instant from, Instant until) {
    return AccessGrant.grant(USER, APP, null, Set.of(ROLE), from, until, null, NOW);
  }

  @Test
  void defaultsToActiveStartingNowWithoutExpiry() {
    AccessGrant grant = grant(null, null);
    assertThat(grant.status()).isEqualTo(AccessStatus.ACTIVE);
    assertThat(grant.validFrom()).isEqualTo(NOW);
    assertThat(grant.validUntil()).isNull();
    assertThat(grant.roleIds()).containsExactly(ROLE);
    assertThat(grant.isEffectiveAt(NOW)).isTrue();
    assertThat(grant.isEffectiveAt(NOW.minusSeconds(1))).isFalse();
  }

  @Test
  void isEffectiveOnlyWithinItsValidityWindow() {
    Instant from = NOW.plus(Duration.ofDays(1));
    Instant until = from.plus(Duration.ofDays(7));
    AccessGrant grant = grant(from, until);

    assertThat(grant.isEffectiveAt(NOW)).isFalse();
    assertThat(grant.isEffectiveAt(from)).isTrue();
    assertThat(grant.isEffectiveAt(until.minusMillis(1))).isTrue();
    assertThat(grant.isEffectiveAt(until)).isFalse();
  }

  @Test
  void validUntilMustBeAfterValidFrom() {
    assertThatThrownBy(() -> grant(NOW, NOW)).isInstanceOf(ValidationException.class);
    AccessGrant grant = grant(null, null);
    assertThatThrownBy(() -> grant.replace(null, Set.of(), null, NOW.minusSeconds(1), null, NOW))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void suspendedAndRevokedAccessIsNotEffective() {
    AccessGrant grant = grant(null, null);
    grant.replace(AccessStatus.SUSPENDED, Set.of(ROLE), null, null, null, NOW);
    assertThat(grant.isEffectiveAt(NOW)).isFalse();

    grant.revoke(NOW.plusSeconds(5));
    assertThat(grant.status()).isEqualTo(AccessStatus.REVOKED);
    assertThat(grant.updatedAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(grant.isEffectiveAt(NOW.plusSeconds(10))).isFalse();
  }

  @Test
  void replaceKeepsTheStartDateWhenNotGivenAndReactivates() {
    Instant from = NOW.minus(Duration.ofDays(10));
    AccessGrant grant = grant(from, null);
    grant.revoke(NOW);

    UUID other = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    grant.replace(null, Set.of(other), null, NOW.plus(Duration.ofDays(30)), admin, NOW);

    assertThat(grant.status()).isEqualTo(AccessStatus.ACTIVE);
    assertThat(grant.validFrom()).isEqualTo(from);
    assertThat(grant.roleIds()).containsExactly(other);
    assertThat(grant.grantedBy()).isEqualTo(admin);
    assertThat(grant.isEffectiveAt(NOW)).isTrue();
  }
}
