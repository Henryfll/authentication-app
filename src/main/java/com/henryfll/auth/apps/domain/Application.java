package com.henryfll.auth.apps.domain;

import com.henryfll.auth.shared.error.BusinessRuleException;
import java.time.Instant;
import java.util.Objects;

/** Sistema protegido por la plataforma. Es la unidad de autorización: tiene sus propios roles. */
public final class Application {

  public static final int MAX_NAME_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 1000;

  private final ApplicationId id;
  private final ApplicationCode code;
  private String name;
  private String description;
  private ActivationStatus status;
  private boolean selfRegistration;
  private RoleId defaultRoleId;
  private final Instant createdAt;
  private Instant updatedAt;

  private Application(
      ApplicationId id,
      ApplicationCode code,
      String name,
      String description,
      ActivationStatus status,
      boolean selfRegistration,
      RoleId defaultRoleId,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.code = Objects.requireNonNull(code, "code");
    this.name = DomainText.required("name", name, MAX_NAME_LENGTH);
    this.description = DomainText.optional("description", description, MAX_DESCRIPTION_LENGTH);
    this.status = Objects.requireNonNull(status, "status");
    this.selfRegistration = selfRegistration;
    this.defaultRoleId = defaultRoleId;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Application create(
      ApplicationCode code, String name, String description, Instant now) {
    return new Application(
        ApplicationId.newId(),
        code,
        name,
        description,
        ActivationStatus.ACTIVE,
        false,
        null,
        now,
        now);
  }

  public static Application restore(
      ApplicationId id,
      ApplicationCode code,
      String name,
      String description,
      ActivationStatus status,
      boolean selfRegistration,
      RoleId defaultRoleId,
      Instant createdAt,
      Instant updatedAt) {
    return new Application(
        id, code, name, description, status, selfRegistration, defaultRoleId, createdAt, updatedAt);
  }

  public void rename(String newName, Instant now) {
    this.name = DomainText.required("name", newName, MAX_NAME_LENGTH);
    this.updatedAt = now;
  }

  public void describe(String newDescription, Instant now) {
    this.description = DomainText.optional("description", newDescription, MAX_DESCRIPTION_LENGTH);
    this.updatedAt = now;
  }

  public void changeStatus(ActivationStatus newStatus, Instant now) {
    this.status = Objects.requireNonNull(newStatus, "status");
    this.updatedAt = now;
  }

  /** El rol por defecto debe pertenecer a esta aplicación. */
  public void assignDefaultRole(Role role, Instant now) {
    if (!role.applicationId().equals(id)) {
      throw new BusinessRuleException("The default role must belong to the same application");
    }
    this.defaultRoleId = role.id();
    this.updatedAt = now;
  }

  /** El auto-registro necesita un rol por defecto que asignar a los nuevos usuarios. */
  public void changeSelfRegistration(boolean enabled, Instant now) {
    if (enabled && defaultRoleId == null) {
      throw new BusinessRuleException("Self-registration requires a default role");
    }
    this.selfRegistration = enabled;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return status == ActivationStatus.ACTIVE;
  }

  public ApplicationId id() {
    return id;
  }

  public ApplicationCode code() {
    return code;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public ActivationStatus status() {
    return status;
  }

  public boolean selfRegistration() {
    return selfRegistration;
  }

  public RoleId defaultRoleId() {
    return defaultRoleId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
