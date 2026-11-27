package com.henryfll.auth.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sesión abierta por un login en un dispositivo o navegador, desde un cliente concreto. Es la
 * "familia" de rotación de sus refresh tokens: revocarla invalida todos.
 */
public final class Session {

  public static final int MAX_USER_AGENT_LENGTH = 512;
  public static final int MAX_DEVICE_NAME_LENGTH = 100;
  public static final int MAX_IP_LENGTH = 45;

  private final UUID id;
  private final UUID userId;
  private final UUID applicationId;
  private final UUID clientId;
  private final String ipAddress;
  private final String userAgent;
  private final String deviceName;
  private final Instant createdAt;
  private Instant lastUsedAt;
  private Instant revokedAt;
  private SessionRevocationReason revokedReason;

  private Session(
      UUID id,
      UUID userId,
      UUID applicationId,
      UUID clientId,
      String ipAddress,
      String userAgent,
      String deviceName,
      Instant createdAt,
      Instant lastUsedAt,
      Instant revokedAt,
      SessionRevocationReason revokedReason) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
    this.clientId = Objects.requireNonNull(clientId, "clientId");
    this.ipAddress = truncate(ipAddress, MAX_IP_LENGTH);
    this.userAgent = truncate(userAgent, MAX_USER_AGENT_LENGTH);
    this.deviceName = truncate(deviceName, MAX_DEVICE_NAME_LENGTH);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.lastUsedAt = Objects.requireNonNull(lastUsedAt, "lastUsedAt");
    this.revokedAt = revokedAt;
    this.revokedReason = revokedReason;
  }

  public static Session start(
      UUID userId,
      UUID applicationId,
      UUID clientId,
      String ipAddress,
      String userAgent,
      String deviceName,
      Instant now) {
    return new Session(
        UUID.randomUUID(),
        userId,
        applicationId,
        clientId,
        ipAddress,
        userAgent,
        deviceName,
        now,
        now,
        null,
        null);
  }

  public static Session restore(
      UUID id,
      UUID userId,
      UUID applicationId,
      UUID clientId,
      String ipAddress,
      String userAgent,
      String deviceName,
      Instant createdAt,
      Instant lastUsedAt,
      Instant revokedAt,
      SessionRevocationReason revokedReason) {
    return new Session(
        id,
        userId,
        applicationId,
        clientId,
        ipAddress,
        userAgent,
        deviceName,
        createdAt,
        lastUsedAt,
        revokedAt,
        revokedReason);
  }

  public void touch(Instant now) {
    this.lastUsedAt = now;
  }

  /** Idempotente: una sesión ya revocada conserva el motivo original. */
  public void revoke(SessionRevocationReason reason, Instant now) {
    if (revokedAt == null) {
      this.revokedAt = now;
      this.revokedReason = Objects.requireNonNull(reason, "reason");
    }
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  private static String truncate(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public UUID applicationId() {
    return applicationId;
  }

  public UUID clientId() {
    return clientId;
  }

  public String ipAddress() {
    return ipAddress;
  }

  public String userAgent() {
    return userAgent;
  }

  public String deviceName() {
    return deviceName;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant lastUsedAt() {
    return lastUsedAt;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public SessionRevocationReason revokedReason() {
    return revokedReason;
  }
}
