package com.henryfll.auth.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity {

  @Id UUID id;

  @Column(name = "session_id", nullable = false, updatable = false)
  UUID sessionId;

  @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
  String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  Instant expiresAt;

  @Column(name = "used_at")
  Instant usedAt;

  @Column(name = "replaced_by")
  UUID replacedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  protected RefreshTokenJpaEntity() {}

  RefreshTokenJpaEntity(UUID id) {
    this.id = id;
  }
}
