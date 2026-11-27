package com.henryfll.auth.user.domain;

public enum UserStatus {
  /** Puede autenticarse. */
  ACTIVE,
  /** Bloqueado (por un administrador o por intentos fallidos). */
  LOCKED,
  /** Deshabilitado de forma indefinida. */
  DISABLED
}
