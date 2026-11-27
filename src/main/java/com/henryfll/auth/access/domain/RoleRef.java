package com.henryfll.auth.access.domain;

import java.util.List;
import java.util.UUID;

/** Vista mínima de un rol del módulo apps, con los códigos de sus permisos. */
public record RoleRef(UUID id, String code, List<String> permissions) {

  public RoleRef {
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }
}
