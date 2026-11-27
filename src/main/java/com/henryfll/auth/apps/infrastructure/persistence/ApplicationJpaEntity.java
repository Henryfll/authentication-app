package com.henryfll.auth.apps.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
class ApplicationJpaEntity {

  @Id UUID id;

  @Column(nullable = false, updatable = false, length = 50)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(columnDefinition = "text")
  String description;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "self_registration", nullable = false)
  boolean selfRegistration;

  @Column(name = "default_role_id")
  UUID defaultRoleId;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;

  protected ApplicationJpaEntity() {}

  ApplicationJpaEntity(UUID id) {
    this.id = id;
  }
}
