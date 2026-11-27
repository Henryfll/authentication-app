package com.henryfll.auth.apps.domain;

import java.util.Objects;
import java.util.UUID;

public record ApplicationClientId(UUID value) {

  public ApplicationClientId {
    Objects.requireNonNull(value, "value");
  }

  public static ApplicationClientId newId() {
    return new ApplicationClientId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
