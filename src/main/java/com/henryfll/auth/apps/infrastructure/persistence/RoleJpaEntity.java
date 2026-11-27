package com.henryfll.auth.apps.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
class RoleJpaEntity {

  @Id UUID id;

  @Column(name = "application_id", nullable = false, updatable = false)
  UUID applicationId;

  @Column(nullable = false, updatable = false, length = 50)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(columnDefinition = "text")
  String description;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_id", nullable = false)
  Set<UUID> permissionIds = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;

  protected RoleJpaEntity() {}

  RoleJpaEntity(UUID id) {
    this.id = id;
  }
}
