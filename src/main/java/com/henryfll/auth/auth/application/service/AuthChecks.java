package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.out.AttemptLimiter;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.RateLimitedException;

/** Validaciones comunes al login y al refresh. */
final class AuthChecks {

  private AuthChecks() {}

  /** Cliente activo, de una aplicación activa y público (los SERVICE no usan contraseña). */
  static LoginClient requirePublicClient(ClientDirectory clients, String clientId) {
    LoginClient client =
        clients
            .findByClientId(clientId)
            .filter(LoginClient::active)
            .orElseThrow(() ->
                new DomainException(ErrorCode.AUTH_INVALID_CLIENT, "Unknown or inactive client"));
    if (client.confidential()) {
      throw new DomainException(
          ErrorCode.AUTH_INVALID_CLIENT, "This client cannot use password login");
    }
    return client;
  }

  static DomainException accessNotGranted(LoginClient client) {
    return new DomainException(
        ErrorCode.ACCESS_NOT_GRANTED,
        "The user has no access to application " + client.applicationCode());
  }

  /** Consume un intento del límite indicado; lanza 429 si se ha superado. */
  static void throttle(AttemptLimiter limiter, AttemptLimiter.Limit limit, String key) {
    limiter
        .tryAcquire(limit, key)
        .ifPresent(retryAfter -> {
          throw new RateLimitedException(retryAfter);
        });
  }

  static DomainException invalidRefreshToken() {
    return new DomainException(
        ErrorCode.INVALID_REFRESH_TOKEN, "The refresh token is invalid, expired or revoked");
  }
}
