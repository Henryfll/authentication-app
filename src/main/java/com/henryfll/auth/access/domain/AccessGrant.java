package com.henryfll.auth.access.domain;

import com.henryfll.auth.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Concesión de acceso de un usuario a una aplicación, con su estado, su vigencia y los roles de esa
 * aplicación que tiene asignados.
 *
 * <p>Los usuarios, aplicaciones y roles se referencian por su identificador: pertenecen a otros
 * módulos. Que existan y que los roles sean de la aplicación lo valida la capa de aplicación.
 */
public final class AccessGrant {

  private final AccessGrantId id;
  private final UUID userId;
  private final UUID applicationId;
  private AccessStatus status;
  private Instant validFrom;
  private Instant validUntil;
  private Set<UUID> roleIds;
  private UUID grantedBy;
  private final Instant createdAt;
  private Instant updatedAt;

  private AccessGrant(
      AccessGrantId id,
      UUID userId,
      UUID applicationId,
      AccessStatus status,
      Instant validFrom,
      Instant validUntil,
      Set<UUID> roleIds,
      UUID grantedBy,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
    this.status = Objects.requireNonNull(status, "status");
    this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
    this.validUntil = checkValidity(validFrom, validUntil);
    this.roleIds = Set.copyOf(roleIds);
    this.grantedBy = grantedBy;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * Concede un acceso nuevo.
   *
   * @param validFrom inicio de la vigencia; {@code null} para empezar ahora.
   * @param validUntil fin de la vigencia (exclusivo); {@code null} para un acceso sin caducidad.
   */
  public static AccessGrant grant(
      UUID userId,
      UUID applicationId,
      AccessStatus status,
      Set<UUID> roleIds,
      Instant validFrom,
      Instant validUntil,
      UUID grantedBy,
      Instant now) {
    return new AccessGrant(
        AccessGrantId.newId(),
        userId,
        applicationId,
        status == null ? AccessStatus.ACTIVE : status,
        validFrom == null ? now : validFrom,
        validUntil,
        roleIds,
        grantedBy,
        now,
        now);
  }

  public static AccessGrant restore(
      AccessGrantId id,
      UUID userId,
      UUID applicationId,
      AccessStatus status,
      Instant validFrom,
      Instant validUntil,
      Set<UUID> roleIds,
      UUID grantedBy,
      Instant createdAt,
      Instant updatedAt) {
    return new AccessGrant(
        id,
        userId,
        applicationId,
        status,
        validFrom,
        validUntil,
        roleIds,
        grantedBy,
        createdAt,
        updatedAt);
  }

  /**
   * Reemplaza la configuración del acceso (semántica PUT).
   *
   * @param status {@code null} para dejarlo {@code ACTIVE}.
   * @param validFrom {@code null} para conservar el inicio actual.
   * @param validUntil {@code null} para que no caduque.
   */
  public void replace(
      AccessStatus status,
      Set<UUID> roleIds,
      Instant validFrom,
      Instant validUntil,
      UUID grantedBy,
      Instant now) {
    Instant newFrom = validFrom == null ? this.validFrom : validFrom;
    this.validUntil = checkValidity(newFrom, validUntil);
    this.validFrom = newFrom;
    this.status = status == null ? AccessStatus.ACTIVE : status;
    this.roleIds = Set.copyOf(roleIds);
    this.grantedBy = grantedBy;
    this.updatedAt = now;
  }

  public void revoke(Instant now) {
    if (status != AccessStatus.REVOKED) {
      status = AccessStatus.REVOKED;
      updatedAt = now;
    }
  }

  /** {@code true} si el acceso permite entrar a la aplicación en el instante dado. */
  public boolean isEffectiveAt(Instant instant) {
    return status == AccessStatus.ACTIVE
        && !instant.isBefore(validFrom)
        && (validUntil == null || instant.isBefore(validUntil));
  }

  private static Instant checkValidity(Instant from, Instant until) {
    if (until != null && !until.isAfter(from)) {
      throw new ValidationException("validUntil", "must be after validFrom");
    }
    return until;
  }

  public AccessGrantId id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public UUID applicationId() {
    return applicationId;
  }

  public AccessStatus status() {
    return status;
  }

  public Instant validFrom() {
    return validFrom;
  }

  public Instant validUntil() {
    return validUntil;
  }

  public Set<UUID> roleIds() {
    return roleIds;
  }

  public UUID grantedBy() {
    return grantedBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
