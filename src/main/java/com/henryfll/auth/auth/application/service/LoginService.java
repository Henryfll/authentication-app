package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.AuthTokens;
import com.henryfll.auth.auth.application.port.in.LoginUseCase;
import com.henryfll.auth.auth.application.port.out.AttemptLimiter;
import com.henryfll.auth.auth.application.port.out.AuthorizationResolver;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.application.port.out.CredentialsVerifier;
import com.henryfll.auth.auth.application.port.out.SessionRepository;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.auth.domain.Authorization;
import com.henryfll.auth.auth.domain.CredentialsVerification;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.auth.domain.Session;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (docs/ARCHITECTURE.md §6.4): límite de intentos, cliente, credenciales (con bloqueo
 * progresivo) y acceso a la aplicación; abre una sesión y emite access token y refresh token.
 *
 * <p>{@code noRollbackFor}: un login fallido debe conservar el contador de intentos fallidos y el
 * bloqueo que haya provocado.
 */
@Service
class LoginService implements LoginUseCase {

  /**
   * Mismo mensaje para email inexistente, contraseña incorrecta y cuenta bloqueada temporalmente.
   */
  static final String INVALID_CREDENTIALS_MESSAGE =
      "Invalid email or password, or the account is temporarily locked";

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final ClientDirectory clients;
  private final CredentialsVerifier credentials;
  private final AuthorizationResolver authorizations;
  private final SessionRepository sessions;
  private final TokenFactory tokenFactory;
  private final AttemptLimiter limiter;
  private final AuditLog audit;
  private final Clock clock;

  LoginService(
      ClientDirectory clients,
      CredentialsVerifier credentials,
      AuthorizationResolver authorizations,
      SessionRepository sessions,
      TokenFactory tokenFactory,
      AttemptLimiter limiter,
      AuditLog audit,
      Clock clock) {
    this.clients = clients;
    this.credentials = credentials;
    this.authorizations = authorizations;
    this.sessions = sessions;
    this.tokenFactory = tokenFactory;
    this.limiter = limiter;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(noRollbackFor = DomainException.class)
  public AuthTokens login(LoginCommand command) {
    AuthChecks.throttle(limiter, AttemptLimiter.Limit.LOGIN_BY_IP, command.ipAddress());
    AuthChecks.throttle(limiter, AttemptLimiter.Limit.LOGIN_BY_EMAIL, command.email());

    LoginClient client;
    try {
      client = AuthChecks.requirePublicClient(clients, command.clientId());
    } catch (DomainException e) {
      failed(null, null, command, "INVALID_CLIENT");
      throw e;
    }

    CredentialsVerification verification = credentials.verify(command.email(), command.password());
    if (!verification.isValid()) {
      log.info("Login failed for client {}: {}", client.clientId(), verification.failure());
      failed(verification.userId(), client, command, verification.failure());
      throw new DomainException(ErrorCode.AUTH_INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }
    AuthenticatedUser user = verification.user();
    if (!user.active()) {
      failed(user.id(), client, command, "ACCOUNT_INACTIVE");
      throw new DomainException(
          ErrorCode.AUTH_ACCOUNT_INACTIVE, "The account is locked or disabled");
    }

    Authorization authorization =
        authorizations.resolve(user.id(), client.applicationId()).orElse(null);
    if (authorization == null) {
      failed(user.id(), client, command, "ACCESS_NOT_GRANTED");
      throw AuthChecks.accessNotGranted(client);
    }

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
    audit.record(
        AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, user.id(), client.applicationId())
            .with("clientId", client.clientId())
            .with("sessionId", session.id().toString()));
    log.info(
        "User {} logged in to {} via {} (session {})",
        user.id(),
        client.applicationCode(),
        client.clientId(),
        session.id());
    return tokenFactory.issue(session, client, user.email(), authorization, now).tokens();
  }

  private void failed(UUID userId, LoginClient client, LoginCommand command, String reason) {
    audit.record(
        AuditEvent.of(
                AuditEventType.LOGIN_FAILED, userId, client == null ? null : client.applicationId())
            .with("reason", reason)
            .with("clientId", command.clientId())
            .with("email", command.email()));
  }
}
