package com.henryfll.auth.apps.domain;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

  public RoleId {
    Objects.requireNonNull(value, "value");
  }

  public static RoleId newId() {
    return new RoleId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
