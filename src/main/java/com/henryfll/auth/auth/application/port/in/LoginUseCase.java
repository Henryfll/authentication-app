package com.henryfll.auth.auth.application.port.in;

/** Login con email y contraseña desde un cliente registrado de una aplicación. */
public interface LoginUseCase {

  /**
   * Abre una sesión y emite un access token y un refresh token.
   *
   * @throws com.henryfll.auth.shared.error.DomainException con código {@code AUTH_INVALID_CLIENT},
   *     {@code AUTH_INVALID_CREDENTIALS}, {@code AUTH_ACCOUNT_INACTIVE} o {@code
   *     ACCESS_NOT_GRANTED}.
   */
  AuthTokens login(LoginCommand command);

  /**
   * @param ipAddress, userAgent, deviceName datos informativos de la sesión (opcionales).
   */
  record LoginCommand(
      String clientId,
      String email,
      String password,
      String ipAddress,
      String userAgent,
      String deviceName) {

    @Override
    public String toString() {
      return "LoginCommand[clientId=" + clientId + ", email=" + email + ", password=***]";
    }
  }
}
