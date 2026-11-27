package com.henryfll.auth.apps.domain;

import java.util.Objects;
import java.util.UUID;

public record PermissionId(UUID value) {

  public PermissionId {
    Objects.requireNonNull(value, "value");
  }

  public static PermissionId newId() {
    return new PermissionId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
