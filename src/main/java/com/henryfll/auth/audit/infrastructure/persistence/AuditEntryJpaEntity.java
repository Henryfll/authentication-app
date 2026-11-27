package com.henryfll.auth.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
class AuditEntryJpaEntity {

  @Id UUID id;

  @Column(nullable = false, updatable = false, length = 50)
  String type;

  @Column(name = "actor_user_id", updatable = false)
  UUID actorUserId;

  @Column(name = "target_user_id", updatable = false)
  UUID targetUserId;

  @Column(name = "application_id", updatable = false)
  UUID applicationId;

  @Column(name = "ip_address", updatable = false, length = 45)
  String ipAddress;

  @Column(updatable = false, columnDefinition = "text")
  String details;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  protected AuditEntryJpaEntity() {}

  AuditEntryJpaEntity(UUID id) {
    this.id = id;
  }
}
