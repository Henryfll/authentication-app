package com.henryfll.auth.apps.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_clients")
class ApplicationClientJpaEntity {

  @Id UUID id;

  @Column(name = "application_id", nullable = false, updatable = false)
  UUID applicationId;

  @Column(name = "client_id", nullable = false, updatable = false, length = 100)
  String clientId;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, updatable = false, length = 20)
  String platform;

  @Column(nullable = false)
  boolean confidential;

  @Column(name = "client_secret_hash")
  String clientSecretHash;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "allowed_origins", nullable = false, columnDefinition = "text[]")
  List<String> allowedOrigins;

  @Column(name = "access_token_ttl_seconds", nullable = false)
  int accessTokenTtlSeconds;

  @Column(name = "refresh_token_ttl_seconds", nullable = false)
  int refreshTokenTtlSeconds;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version long version;

  protected ApplicationClientJpaEntity() {}

  ApplicationClientJpaEntity(UUID id) {
    this.id = id;
  }
}
