package com.henryfll.auth.auth.application.port.out;

import com.henryfll.auth.auth.domain.AccessTokenClaims;

/** Firma access tokens. */
public interface TokenIssuer {

  /** Devuelve el token firmado (JWT compacto). */
  String issue(AccessTokenClaims claims);
}
