package com.henryfll.auth.auth.domain;

import java.util.List;

/** Roles y permisos efectivos del usuario en la aplicación del login. */
public record Authorization(String applicationCode, List<String> roles, List<String> permissions) {

  public Authorization {
    roles = List.copyOf(roles);
    permissions = List.copyOf(permissions);
  }
}
