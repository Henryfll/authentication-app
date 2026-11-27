package com.henryfll.auth.apps.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
class PermissionJpaEntity {

  @Id UUID id;

  @Column(name = "application_id", nullable = false, updatable = false)
  UUID applicationId;

  @Column(nullable = false, updatable = false, length = 100)
  String code;

  @Column(columnDefinition = "text")
  String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  protected PermissionJpaEntity() {}

  PermissionJpaEntity(UUID id) {
    this.id = id;
  }
}
