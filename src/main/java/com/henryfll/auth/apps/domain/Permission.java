package com.henryfll.auth.apps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Acción atómica dentro de una aplicación, p. ej. {@code invoice:approve}. */
public final class Permission {

  public static final int MAX_DESCRIPTION_LENGTH = 1000;
  private static final Pattern CODE =
      Pattern.compile("^(?=.{3,100}$)[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$");

  private final PermissionId id;
  private final ApplicationId applicationId;
  private final String code;
  private final String description;
  private final Instant createdAt;

  private Permission(
      PermissionId id,
      ApplicationId applicationId,
      String code,
      String description,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId");
    this.code =
        DomainText.matching(
            "code",
            code,
            CODE,
            "must be like 'resource:action' (lowercase letters, digits, hyphens), max 100");
    this.description = DomainText.optional("description", description, MAX_DESCRIPTION_LENGTH);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static Permission create(
      ApplicationId applicationId, String code, String description, Instant now) {
    return new Permission(PermissionId.newId(), applicationId, code, description, now);
  }

  public static Permission restore(
      PermissionId id,
      ApplicationId applicationId,
      String code,
      String description,
      Instant createdAt) {
    return new Permission(id, applicationId, code, description, createdAt);
  }

  public PermissionId id() {
    return id;
  }

  public ApplicationId applicationId() {
    return applicationId;
  }

  public String code() {
    return code;
  }

  public String description() {
    return description;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
