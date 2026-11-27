package com.henryfll.auth.apps.domain;

/** Forma de consumir una aplicación. Determina cómo se entregan los tokens (§6.3). */
public enum ClientPlatform {
  /** Frontend web: refresh token en cookie HttpOnly. Cliente público. */
  WEB,
  /** App móvil: refresh token en el body. Cliente público. */
  MOBILE,
  /** Backend a backend: cliente confidencial con secreto. */
  SERVICE;

  public boolean isConfidential() {
    return this == SERVICE;
  }
}
