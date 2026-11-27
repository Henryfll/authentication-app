package com.henryfll.auth.auth.domain;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cliente desde el que se intenta el login o el refresh, con lo que el módulo auth necesita de él.
 *
 * @param id identificador interno del cliente.
 * @param clientId identificador público ({@code client_id}).
 * @param active {@code true} solo si el cliente y su aplicación están activos.
 * @param allowedOrigins orígenes permitidos (solo clientes {@code WEB}).
 */
public record LoginClient(
    UUID id,
    String clientId,
    UUID applicationId,
    String applicationCode,
    String platform,
    boolean confidential,
    boolean active,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    List<String> allowedOrigins) {

  public static final String WEB = "WEB";

  public LoginClient {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  /** Los clientes web reciben el refresh token en una cookie HttpOnly, no en el body. */
  public boolean usesRefreshCookie() {
    return WEB.equals(platform);
  }
}
