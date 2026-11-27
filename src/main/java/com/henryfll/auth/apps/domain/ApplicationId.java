package com.henryfll.auth.apps.domain;

import java.util.Objects;
import java.util.UUID;

public record ApplicationId(UUID value) {

  public ApplicationId {
    Objects.requireNonNull(value, "value");
  }

  public static ApplicationId newId() {
    return new ApplicationId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
