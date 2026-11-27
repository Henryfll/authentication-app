package com.henryfll.auth.user.domain;

import com.henryfll.auth.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;

/** Agregado usuario. Los usuarios son globales: una cuenta puede acceder a varias aplicaciones. */
public final class User {

  public static final int MAX_NAME_LENGTH = 100;

  private final UserId id;
  private final Email email;
  private String passwordHash;
  private String firstName;
  private String lastName;
  private UserStatus status;
  private final Instant emailVerifiedAt;
  private int failedLoginAttempts;
  private Instant lockedUntil;
  private Instant passwordChangedAt;
  private final Instant createdAt;
  private Instant updatedAt;

  private User(
      UserId id,
      Email email,
      String passwordHash,
      String firstName,
      String lastName,
      UserStatus status,
      Instant emailVerifiedAt,
      int failedLoginAttempts,
      Instant lockedUntil,
      Instant passwordChangedAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.email = Objects.requireNonNull(email, "email");
    this.passwordHash = requireHash(passwordHash);
    this.firstName = normalizeName("firstName", firstName);
    this.lastName = normalizeName("lastName", lastName);
    this.status = Objects.requireNonNull(status, "status");
    this.emailVerifiedAt = emailVerifiedAt;
    this.failedLoginAttempts = failedLoginAttempts;
    this.lockedUntil = lockedUntil;
    this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** Alta de un usuario nuevo, activo. La contraseña ya debe venir validada y hasheada. */
  public static User register(
      Email email, String passwordHash, String firstName, String lastName, Instant now) {
    return new User(
        UserId.newId(),
        email,
        passwordHash,
        firstName,
        lastName,
        UserStatus.ACTIVE,
        null,
        0,
        null,
        now,
        now,
        now);
  }

  /** Reconstruye un usuario desde la persistencia. */
  public static User restore(
      UserId id,
      Email email,
      String passwordHash,
      String firstName,
      String lastName,
      UserStatus status,
      Instant emailVerifiedAt,
      int failedLoginAttempts,
      Instant lockedUntil,
      Instant passwordChangedAt,
      Instant createdAt,
      Instant updatedAt) {
    return new User(
        id,
        email,
        passwordHash,
        firstName,
        lastName,
        status,
        emailVerifiedAt,
        failedLoginAttempts,
        lockedUntil,
        passwordChangedAt,
        createdAt,
        updatedAt);
  }

  /** Activar una cuenta también levanta un bloqueo temporal por intentos fallidos. */
  public void changeStatus(UserStatus newStatus, Instant now) {
    Objects.requireNonNull(newStatus, "status");
    if (newStatus != status) {
      status = newStatus;
      updatedAt = now;
    }
    if (newStatus == UserStatus.ACTIVE) {
      clearLockout(now);
    }
  }

  /** {@code true} si la cuenta está bloqueada temporalmente por intentos fallidos. */
  public boolean isTemporarilyLockedAt(Instant instant) {
    return lockedUntil != null && instant.isBefore(lockedUntil);
  }

  /**
   * Registra un intento fallido y, si se alcanza el umbral de la política, bloquea la cuenta.
   *
   * @return {@code true} si este fallo ha provocado un bloqueo.
   */
  public boolean recordFailedLogin(LockoutPolicy policy, Instant now) {
    failedLoginAttempts++;
    updatedAt = now;
    var lock = policy.lockDurationAfter(failedLoginAttempts);
    if (lock.isZero()) {
      return false;
    }
    lockedUntil = now.plus(lock);
    return true;
  }

  public void recordSuccessfulLogin(Instant now) {
    if (failedLoginAttempts != 0 || lockedUntil != null) {
      clearLockout(now);
    }
  }

  private void clearLockout(Instant now) {
    if (failedLoginAttempts != 0 || lockedUntil != null) {
      failedLoginAttempts = 0;
      lockedUntil = null;
      updatedAt = now;
    }
  }

  public void updateProfile(String firstName, String lastName, Instant now) {
    this.firstName = normalizeName("firstName", firstName);
    this.lastName = normalizeName("lastName", lastName);
    this.updatedAt = now;
  }

  public void changePasswordHash(String newPasswordHash, Instant now) {
    this.passwordHash = requireHash(newPasswordHash);
    this.passwordChangedAt = now;
    this.updatedAt = now;
  }

  /** Restablecimiento por un administrador: nueva contraseña y bloqueo temporal levantado. */
  public void resetPasswordHash(String newPasswordHash, Instant now) {
    changePasswordHash(newPasswordHash, now);
    clearLockout(now);
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  private static String requireHash(String hash) {
    if (hash == null || hash.isBlank()) {
      throw new IllegalArgumentException("passwordHash must not be blank");
    }
    return hash;
  }

  private static String normalizeName(String field, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new ValidationException(
          field, "must be at most " + MAX_NAME_LENGTH + " characters long");
    }
    return trimmed;
  }

  public UserId id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String firstName() {
    return firstName;
  }

  public String lastName() {
    return lastName;
  }

  public UserStatus status() {
    return status;
  }

  public Instant emailVerifiedAt() {
    return emailVerifiedAt;
  }

  public int failedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant lockedUntil() {
    return lockedUntil;
  }

  public Instant passwordChangedAt() {
    return passwordChangedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
