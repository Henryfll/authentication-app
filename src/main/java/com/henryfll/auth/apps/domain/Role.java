package com.henryfll.auth.apps.domain;

import com.henryfll.auth.shared.error.BusinessRuleException;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Agrupación de permisos dentro de una aplicación, p. ej. {@code ACCOUNTANT}. */
public final class Role {

  public static final int MAX_NAME_LENGTH = 100;
  public static final int MAX_DESCRIPTION_LENGTH = 1000;
  private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

  private final RoleId id;
  private final ApplicationId applicationId;
  private final String code;
  private String name;
  private String description;
  private Set<PermissionId> permissionIds;
  private final Instant createdAt;
  private Instant updatedAt;

  private Role(
      RoleId id,
      ApplicationId applicationId,
      String code,
      String name,
      String description,
      Set<PermissionId> permissionIds,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
    this.code =
        DomainText.matching(
            "code",
            code,
            CODE,
            "must be 2-50 characters: uppercase letters, digits and underscores, starting with a"
                + " letter");
    this.name = DomainText.required("name", name, MAX_NAME_LENGTH);
    this.description = DomainText.optional("description", description, MAX_DESCRIPTION_LENGTH);
    this.permissionIds = Set.copyOf(permissionIds);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Role create(
      ApplicationId applicationId, String code, String name, String description, Instant now) {
    return new Role(RoleId.newId(), applicationId, code, name, description, Set.of(), now, now);
  }

  public static Role restore(
      RoleId id,
      ApplicationId applicationId,
      String code,
      String name,
      String description,
      Set<PermissionId> permissionIds,
      Instant createdAt,
      Instant updatedAt) {
    return new Role(
        id, applicationId, code, name, description, permissionIds, createdAt, updatedAt);
  }

  /** Reemplaza los permisos del rol. Todos deben pertenecer a la misma aplicación. */
  public void replacePermissions(Collection<Permission> permissions, Instant now) {
    for (Permission permission : permissions) {
      if (!permission.applicationId().equals(applicationId)) {
        throw new BusinessRuleException(
            "Permission " + permission.code() + " belongs to a different application");
      }
    }
    this.permissionIds =
        permissions.stream().map(Permission::id).collect(Collectors.toUnmodifiableSet());
    this.updatedAt = now;
  }

  public void rename(String newName, String newDescription, Instant now) {
    this.name = DomainText.required("name", newName, MAX_NAME_LENGTH);
    this.description = DomainText.optional("description", newDescription, MAX_DESCRIPTION_LENGTH);
    this.updatedAt = now;
  }

  public RoleId id() {
    return id;
  }

  public ApplicationId applicationId() {
    return applicationId;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public Set<PermissionId> permissionIds() {
    return permissionIds;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
