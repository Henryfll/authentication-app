package com.henryfll.auth.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token persistido. Solo se conoce su hash; el valor en claro se entrega una vez al
 * cliente. Cada uso lo marca como usado y emite uno nuevo en la misma sesión (rotación).
 */
public final class RefreshToken {

  private final UUID id;
  private final UUID sessionId;
  private final String tokenHash;
  private final Instant expiresAt;
  private final Instant usedAt;
  private final UUID replacedBy;
  private final Instant createdAt;

  private RefreshToken(
      UUID id,
      UUID sessionId,
      String tokenHash,
      Instant expiresAt,
      Instant usedAt,
      UUID replacedBy,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    this.usedAt = usedAt;
    this.replacedBy = replacedBy;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static RefreshToken issue(
      UUID sessionId, String tokenHash, Instant expiresAt, Instant now) {
    return new RefreshToken(UUID.randomUUID(), sessionId, tokenHash, expiresAt, null, null, now);
  }

  public static RefreshToken restore(
      UUID id,
      UUID sessionId,
      String tokenHash,
      Instant expiresAt,
      Instant usedAt,
      UUID replacedBy,
      Instant createdAt) {
    return new RefreshToken(id, sessionId, tokenHash, expiresAt, usedAt, replacedBy, createdAt);
  }

  public boolean isExpiredAt(Instant instant) {
    return !instant.isBefore(expiresAt);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public UUID id() {
    return id;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant usedAt() {
    return usedAt;
  }

  public UUID replacedBy() {
    return replacedBy;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
