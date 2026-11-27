package com.henryfll.auth.access.infrastructure.persistence;

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
@Table(name = "user_application_access")
class AccessGrantJpaEntity {

  @Id UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  UUID userId;

  @Column(name = "application_id", nullable = false, updatable = false)
  UUID applicationId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "valid_from", nullable = false)
  Instant validFrom;

  @Column(name = "valid_until")
  Instant validUntil;

  @Column(name = "granted_by")
  UUID grantedBy;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "access_roles", joinColumns = @JoinColumn(name = "access_id"))
  @Column(name = "role_id", nullable = false)
  Set<UUID> roleIds = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;

  protected AccessGrantJpaEntity() {}

  AccessGrantJpaEntity(UUID id) {
    this.id = id;
  }
}
