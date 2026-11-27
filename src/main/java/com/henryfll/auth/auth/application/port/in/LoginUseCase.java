package com.henryfll.auth.auth.application.port.in;

import java.time.Instant;

/** Login con email y contraseña desde un cliente registrado de una aplicación. */
public interface LoginUseCase {

  /**
   * @throws com.henryfll.auth.shared.error.DomainException con código {@code AUTH_INVALID_CLIENT},
   *     {@code AUTH_INVALID_CREDENTIALS}, {@code AUTH_ACCOUNT_INACTIVE} o {@code
   *     ACCESS_NOT_GRANTED}.
   */
  LoginResult login(LoginCommand command);

  record LoginCommand(String clientId, String email, String password) {

    @Override
    public String toString() {
      return "LoginCommand[clientId=" + clientId + ", email=" + email + ", password=***]";
    }
  }

  record LoginResult(
      String accessToken,
      Instant expiresAt,
      long expiresInSeconds,
      String applicationCode,
      String platform) {

    @Override
    public String toString() {
      return "LoginResult[applicationCode=" + applicationCode + ", accessToken=***]";
    }
  }
}
