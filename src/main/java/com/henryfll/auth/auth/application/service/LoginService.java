package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.LoginUseCase;
import com.henryfll.auth.auth.application.port.out.AuthorizationResolver;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.application.port.out.CredentialsVerifier;
import com.henryfll.auth.auth.application.port.out.TokenIssuer;
import com.henryfll.auth.auth.domain.AccessTokenClaims;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.auth.domain.Authorization;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Login (docs/ARCHITECTURE.md §6.4): valida el cliente, las credenciales y el acceso a la
 * aplicación, y emite un access token con los roles y permisos de esa aplicación.
 */
@Service
class LoginService implements LoginUseCase {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final ClientDirectory clients;
  private final CredentialsVerifier credentials;
  private final AuthorizationResolver authorizations;
  private final TokenIssuer tokenIssuer;
  private final Clock clock;

  LoginService(
      ClientDirectory clients,
      CredentialsVerifier credentials,
      AuthorizationResolver authorizations,
      TokenIssuer tokenIssuer,
      Clock clock) {
    this.clients = clients;
    this.credentials = credentials;
    this.authorizations = authorizations;
    this.tokenIssuer = tokenIssuer;
    this.clock = clock;
  }

  @Override
  public LoginResult login(LoginCommand command) {
    LoginClient client =
        clients
            .findByClientId(command.clientId())
            .filter(LoginClient::active)
            .orElseThrow(() -> fail(ErrorCode.AUTH_INVALID_CLIENT, "Unknown or inactive client"));
    if (client.confidential()) {
      throw fail(ErrorCode.AUTH_INVALID_CLIENT, "This client cannot use password login");
    }

    AuthenticatedUser user =
        credentials
            .verify(command.email(), command.password())
            .orElseThrow(() -> {
              log.info("Login failed for client {}: invalid credentials", client.clientId());
              return fail(ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid email or password");
            });
    if (!user.active()) {
      throw fail(ErrorCode.AUTH_ACCOUNT_INACTIVE, "The account is locked or disabled");
    }

    Authorization authorization =
        authorizations
            .resolve(user.id(), client.applicationId())
            .orElseThrow(() ->
                fail(
                    ErrorCode.ACCESS_NOT_GRANTED,
                    "The user has no access to application " + client.applicationCode()));

    Instant now = clock.instant();
    Instant expiresAt = now.plus(client.accessTokenTtl());
    String token =
        tokenIssuer.issue(
            new AccessTokenClaims(
                user.id(),
                user.email(),
                authorization.applicationCode(),
                client.clientId(),
                authorization.roles(),
                authorization.permissions(),
                now,
                expiresAt,
                UUID.randomUUID()));
    log.info(
        "User {} logged in to {} via {}", user.id(), client.applicationCode(), client.clientId());
    return new LoginResult(
        token,
        expiresAt,
        client.accessTokenTtl().toSeconds(),
        authorization.applicationCode(),
        client.platform());
  }

  private static DomainException fail(ErrorCode code, String message) {
    return new DomainException(code, message);
  }
}
