package com.henryfll.auth.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
class SessionJpaEntity {

  @Id UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  UUID userId;

  @Column(name = "application_id", nullable = false, updatable = false)
  UUID applicationId;

  @Column(name = "client_id", nullable = false, updatable = false)
  UUID clientId;

  @Column(name = "ip_address", length = 45)
  String ipAddress;

  @Column(name = "user_agent", length = 512)
  String userAgent;

  @Column(name = "device_name", length = 100)
  String deviceName;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  Instant lastUsedAt;

  @Column(name = "revoked_at")
  Instant revokedAt;

  @Column(name = "revoked_reason", length = 30)
  String revokedReason;

  @Version long version;

  protected SessionJpaEntity() {}

  SessionJpaEntity(UUID id) {
    this.id = id;
  }
}
