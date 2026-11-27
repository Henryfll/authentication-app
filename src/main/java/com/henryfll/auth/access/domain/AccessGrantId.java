package com.henryfll.auth.access.domain;

import java.util.Objects;
import java.util.UUID;

public record AccessGrantId(UUID value) {

  public AccessGrantId {
    Objects.requireNonNull(value, "value");
  }

  public static AccessGrantId newId() {
    return new AccessGrantId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
