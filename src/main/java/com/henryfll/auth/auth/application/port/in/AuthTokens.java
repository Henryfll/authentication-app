package com.henryfll.auth.auth.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Tokens emitidos por un login o un refresh.
 *
 * @param refreshToken valor en claro del refresh token. Solo se conoce en este momento: en base de
 *     datos se guarda su hash.
 * @param platform plataforma del cliente; decide si el refresh token viaja en cookie (WEB) o en el
 *     body (MOBILE).
 */
public record AuthTokens(
    String accessToken,
    Instant accessTokenExpiresAt,
    long expiresInSeconds,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    String applicationCode,
    String platform,
    UUID sessionId) {

  public boolean usesRefreshCookie() {
    return "WEB".equals(platform);
  }

  @Override
  public String toString() {
    return "AuthTokens[application="
        + applicationCode
        + ", session="
        + sessionId
        + ", accessToken=***, refreshToken=***]";
  }
}
