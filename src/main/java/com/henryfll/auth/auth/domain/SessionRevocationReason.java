package com.henryfll.auth.auth.domain;

public enum SessionRevocationReason {
  /** El usuario cerró esta sesión. */
  LOGOUT,
  /** El usuario cerró todas sus sesiones. */
  LOGOUT_ALL,
  /** Se presentó un refresh token ya usado: posible robo del token. */
  REUSE_DETECTED,
  /** Cerrada por un administrador. */
  ADMIN,
  /** El acceso a la aplicación se suspendió, revocó o dejó de estar vigente. */
  ACCESS_REVOKED,
  /** La cuenta se bloqueó o deshabilitó. */
  USER_INACTIVE
}
