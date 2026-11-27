package com.henryfll.auth.apps.domain;

import java.util.UUID;

/**
 * Identificadores fijos de la aplicación {@code auth-admin}, creada por la migración {@code
 * V4__seed_auth_admin.sql}. La propia plataforma se administra como una aplicación más.
 */
public final class AuthAdminApplication {

  public static final ApplicationId ID =
      new ApplicationId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  public static final ApplicationCode CODE = new ApplicationCode("auth-admin");
  public static final RoleId SUPER_ADMIN_ROLE =
      new RoleId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

  private AuthAdminApplication() {}
}
