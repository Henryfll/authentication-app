package com.henryfll.auth.access.domain;

public enum AccessStatus {
  /** El usuario puede entrar a la aplicación (dentro de su vigencia). */
  ACTIVE,
  /** Acceso pausado temporalmente; se puede reactivar. */
  SUSPENDED,
  /** Acceso retirado. Se conserva el registro para auditoría; se puede volver a conceder. */
  REVOKED
}
