package com.henryfll.auth.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Contenido de un access token (docs/ARCHITECTURE.md §6.1). El emisor ({@code iss}) lo añade el
 * adaptador que firma el token.
 *
 * @param audience código de la aplicación ({@code aud}).
 * @param authorizedParty {@code client_id} que pidió el token ({@code azp}).
 * @param sessionId sesión a la que pertenece el token ({@code sid}); {@code null} si no tiene.
 */
public record AccessTokenClaims(
    UUID subject,
    String email,
    String audience,
    String authorizedParty,
    List<String> roles,
    List<String> permissions,
    Instant issuedAt,
    Instant expiresAt,
    UUID tokenId,
    UUID sessionId) {

  public AccessTokenClaims {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(issuedAt, "issuedAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(tokenId, "tokenId");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    roles = List.copyOf(roles);
    permissions = List.copyOf(permissions);
  }
}
