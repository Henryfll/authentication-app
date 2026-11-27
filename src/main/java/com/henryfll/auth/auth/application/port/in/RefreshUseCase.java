package com.henryfll.auth.auth.application.port.in;

/** Renovación de tokens con un refresh token (rotación con detección de reutilización, §6.2). */
public interface RefreshUseCase {

  /**
   * @throws com.henryfll.auth.shared.error.DomainException con código {@code AUTH_INVALID_CLIENT},
   *     {@code INVALID_REFRESH_TOKEN}, {@code REFRESH_TOKEN_REUSED} (la sesión queda revocada),
   *     {@code AUTH_ACCOUNT_INACTIVE} o {@code ACCESS_NOT_GRANTED}.
   */
  AuthTokens refresh(RefreshCommand command);

  /**
   * @param fromCookie si el token llegó en la cookie (clientes web) o en el body (móvil).
   * @param origin cabecera {@code Origin} de la petición, si la hay.
   */
  record RefreshCommand(
      String clientId,
      String refreshToken,
      boolean fromCookie,
      String origin,
      String ipAddress,
      String userAgent) {

    @Override
    public String toString() {
      return "RefreshCommand[clientId=" + clientId + ", refreshToken=***]";
    }
  }
}
