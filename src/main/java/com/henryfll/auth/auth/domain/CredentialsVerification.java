package com.henryfll.auth.auth.domain;

import java.util.UUID;

/**
 * Resultado de verificar email y contraseña.
 *
 * @param user presente solo si las credenciales son válidas.
 * @param userId usuario afectado, si existe (para auditoría), aunque las credenciales no sean
 *     válidas.
 * @param failure motivo del fallo (solo para auditoría: la API responde igual en todos los casos).
 */
public record CredentialsVerification(AuthenticatedUser user, UUID userId, String failure) {

  public static CredentialsVerification valid(AuthenticatedUser user) {
    return new CredentialsVerification(user, user.id(), null);
  }

  public static CredentialsVerification failed(UUID userId, String failure) {
    return new CredentialsVerification(null, userId, failure);
  }

  public boolean isValid() {
    return user != null;
  }
}
