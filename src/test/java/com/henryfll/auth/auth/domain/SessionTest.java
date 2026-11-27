package com.henryfll.auth.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  private static Session session(String userAgent) {
    return Session.start(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "10.0.0.1", userAgent, " ", NOW);
  }

  @Test
  void startsActiveAndTruncatesInformationalFields() {
    Session session = session("x".repeat(600));
    assertThat(session.isActive()).isTrue();
    assertThat(session.userAgent()).hasSize(Session.MAX_USER_AGENT_LENGTH);
    assertThat(session.deviceName()).isNull();
    assertThat(session.lastUsedAt()).isEqualTo(NOW);
  }

  @Test
  void revocationIsIdempotentAndKeepsTheFirstReason() {
    Session session = session("Firefox");
    session.revoke(SessionRevocationReason.REUSE_DETECTED, NOW.plusSeconds(1));
    session.revoke(SessionRevocationReason.LOGOUT, NOW.plusSeconds(2));

    assertThat(session.isActive()).isFalse();
    assertThat(session.revokedReason()).isEqualTo(SessionRevocationReason.REUSE_DETECTED);
    assertThat(session.revokedAt()).isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void touchUpdatesLastUse() {
    Session session = session("Firefox");
    session.touch(NOW.plusSeconds(30));
    assertThat(session.lastUsedAt()).isEqualTo(NOW.plusSeconds(30));
  }

  @Test
  void refreshTokenValuesAreRandomAndHashedWithSha256() {
    String a = RefreshTokenValues.generate();
    String b = RefreshTokenValues.generate();
    assertThat(a).hasSize(43).isNotEqualTo(b);
    assertThat(RefreshTokenValues.hash(a)).hasSize(64).isEqualTo(RefreshTokenValues.hash(a));
    assertThat(RefreshTokenValues.hash(a)).isNotEqualTo(RefreshTokenValues.hash(b));
  }

  @Test
  void refreshTokensExpireAtTheirDeadline() {
    RefreshToken token = RefreshToken.issue(UUID.randomUUID(), "h", NOW.plusSeconds(60), NOW);
    assertThat(token.isUsed()).isFalse();
    assertThat(token.isExpiredAt(NOW.plusSeconds(59))).isFalse();
    assertThat(token.isExpiredAt(NOW.plusSeconds(60))).isTrue();
  }
}
