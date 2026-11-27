package com.henryfll.auth.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "citext")
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatusColumn status;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected UserJpaEntity() {}

  UserJpaEntity(UUID id) {
    this.id = id;
  }

  /** Mismos valores que {@code UserStatus}: la persistencia no depende del enum del dominio. */
  enum UserStatusColumn {
    ACTIVE,
    LOCKED,
    DISABLED
  }

  UUID getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  void setEmail(String email) {
    this.email = email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  String getFirstName() {
    return firstName;
  }

  void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  String getLastName() {
    return lastName;
  }

  void setLastName(String lastName) {
    this.lastName = lastName;
  }

  UserStatusColumn getStatus() {
    return status;
  }

  void setStatus(UserStatusColumn status) {
    this.status = status;
  }

  Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  void setEmailVerifiedAt(Instant emailVerifiedAt) {
    this.emailVerifiedAt = emailVerifiedAt;
  }

  int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  void setFailedLoginAttempts(int failedLoginAttempts) {
    this.failedLoginAttempts = failedLoginAttempts;
  }

  Instant getLockedUntil() {
    return lockedUntil;
  }

  void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  void setPasswordChangedAt(Instant passwordChangedAt) {
    this.passwordChangedAt = passwordChangedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
