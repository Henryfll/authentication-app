package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.AuthTokens;
import com.henryfll.auth.auth.application.port.in.LoginUseCase;
import com.henryfll.auth.auth.application.port.out.AuthorizationResolver;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.application.port.out.CredentialsVerifier;
import com.henryfll.auth.auth.application.port.out.SessionRepository;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.auth.domain.Authorization;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.auth.domain.Session;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (docs/ARCHITECTURE.md §6.4): valida el cliente, las credenciales y el acceso a la
 * aplicación, abre una sesión y emite un access token y un refresh token.
 */
@Service
class LoginService implements LoginUseCase {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final ClientDirectory clients;
  private final CredentialsVerifier credentials;
  private final AuthorizationResolver authorizations;
  private final SessionRepository sessions;
  private final TokenFactory tokenFactory;
  private final Clock clock;

  LoginService(
      ClientDirectory clients,
      CredentialsVerifier credentials,
      AuthorizationResolver authorizations,
      SessionRepository sessions,
      TokenFactory tokenFactory,
      Clock clock) {
    this.clients = clients;
    this.credentials = credentials;
    this.authorizations = authorizations;
    this.sessions = sessions;
    this.tokenFactory = tokenFactory;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AuthTokens login(LoginCommand command) {
    LoginClient client = AuthChecks.requirePublicClient(clients, command.clientId());

    AuthenticatedUser user =
        credentials
            .verify(command.email(), command.password())
            .orElseThrow(() -> {
              log.info("Login failed for client {}: invalid credentials", client.clientId());
              return new DomainException(
                  ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid email or password");
            });
    if (!user.active()) {
      throw new DomainException(
          ErrorCode.AUTH_ACCOUNT_INACTIVE, "The account is locked or disabled");
    }

    Authorization authorization =
        authorizations
            .resolve(user.id(), client.applicationId())
            .orElseThrow(() -> AuthChecks.accessNotGranted(client));

    Instant now = clock.instant();
    Session session =
        sessions.save(
            Session.start(
                user.id(),
                client.applicationId(),
                client.id(),
                command.ipAddress(),
                command.userAgent(),
                command.deviceName(),
                now));
    log.info(
        "User {} logged in to {} via {} (session {})",
        user.id(),
        client.applicationCode(),
        client.clientId(),
        session.id());
    return tokenFactory.issue(session, client, user.email(), authorization, now).tokens();
  }
}
