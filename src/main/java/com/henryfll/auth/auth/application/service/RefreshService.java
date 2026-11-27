package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.AuthTokens;
import com.henryfll.auth.auth.application.port.in.RefreshUseCase;
import com.henryfll.auth.auth.application.port.out.AttemptLimiter;
import com.henryfll.auth.auth.application.port.out.AuthorizationResolver;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.application.port.out.RefreshTokenRepository;
import com.henryfll.auth.auth.application.port.out.SessionRepository;
import com.henryfll.auth.auth.application.port.out.UserAccounts;
import com.henryfll.auth.auth.domain.AuthenticatedUser;
import com.henryfll.auth.auth.domain.Authorization;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.auth.domain.RefreshToken;
import com.henryfll.auth.auth.domain.RefreshTokenValues;
import com.henryfll.auth.auth.domain.Session;
import com.henryfll.auth.auth.domain.SessionRevocationReason;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.DomainException;
import com.henryfll.auth.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh con rotación y detección de reutilización (docs/ARCHITECTURE.md §6.2).
 *
 * <p>Cada refresh token sirve una sola vez. Si se presenta uno ya usado, se asume que fue robado y
 * se revoca la sesión completa, invalidando también el token que tenga el usuario legítimo.
 *
 * <p>Las revocaciones deben persistir aunque la petición termine en error, de ahí {@code
 * noRollbackFor}: todos los {@link DomainException} de este método se lanzan en puntos donde
 * confirmar la transacción es lo correcto.
 */
@Service
class RefreshService implements RefreshUseCase {

  private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

  private final ClientDirectory clients;
  private final RefreshTokenRepository refreshTokens;
  private final SessionRepository sessions;
  private final UserAccounts users;
  private final AuthorizationResolver authorizations;
  private final TokenFactory tokenFactory;
  private final AttemptLimiter limiter;
  private final AuditLog audit;
  private final Clock clock;

  RefreshService(
      ClientDirectory clients,
      RefreshTokenRepository refreshTokens,
      SessionRepository sessions,
      UserAccounts users,
      AuthorizationResolver authorizations,
      TokenFactory tokenFactory,
      AttemptLimiter limiter,
      AuditLog audit,
      Clock clock) {
    this.clients = clients;
    this.refreshTokens = refreshTokens;
    this.sessions = sessions;
    this.users = users;
    this.authorizations = authorizations;
    this.tokenFactory = tokenFactory;
    this.limiter = limiter;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(noRollbackFor = DomainException.class)
  public AuthTokens refresh(RefreshCommand command) {
    AuthChecks.throttle(limiter, AttemptLimiter.Limit.REFRESH_BY_IP, command.ipAddress());
    LoginClient client = AuthChecks.requirePublicClient(clients, command.clientId());
    checkTransport(client, command);

    String value = command.refreshToken();
    if (value == null || value.isBlank()) {
      throw AuthChecks.invalidRefreshToken();
    }
    RefreshToken token =
        refreshTokens
            .findByHash(RefreshTokenValues.hash(value.strip()))
            .orElseThrow(AuthChecks::invalidRefreshToken);
    Session session =
        sessions.findById(token.sessionId()).orElseThrow(AuthChecks::invalidRefreshToken);
    if (!session.clientId().equals(client.id()) || !session.isActive()) {
      throw AuthChecks.invalidRefreshToken();
    }

    Instant now = clock.instant();
    if (token.isUsed() || !refreshTokens.markUsed(token.id(), now)) {
      revoke(session, SessionRevocationReason.REUSE_DETECTED, now);
      audit.record(
          AuditEvent.of(
                  AuditEventType.REFRESH_TOKEN_REUSED, session.userId(), session.applicationId())
              .with("sessionId", session.id().toString())
              .with("clientId", client.clientId()));
      log.warn(
          "Refresh token reuse detected for session {} of user {}: session revoked",
          session.id(),
          session.userId());
      throw new DomainException(
          ErrorCode.REFRESH_TOKEN_REUSED,
          "This refresh token was already used. The session has been revoked; log in again");
    }
    if (token.isExpiredAt(now)) {
      throw AuthChecks.invalidRefreshToken();
    }

    Optional<AuthenticatedUser> user = users.findById(session.userId());
    if (user.isEmpty() || !user.get().active()) {
      revoke(session, SessionRevocationReason.USER_INACTIVE, now);
      throw new DomainException(
          ErrorCode.AUTH_ACCOUNT_INACTIVE, "The account is locked or disabled");
    }
    Optional<Authorization> authorization =
        authorizations.resolve(session.userId(), session.applicationId());
    if (authorization.isEmpty()) {
      revoke(session, SessionRevocationReason.ACCESS_REVOKED, now);
      throw AuthChecks.accessNotGranted(client);
    }

    session.touch(now);
    sessions.save(session);
    TokenFactory.Issued issued =
        tokenFactory.issue(session, client, user.get().email(), authorization.get(), now);
    refreshTokens.markReplaced(token.id(), issued.refreshTokenId());
    return issued.tokens();
  }

  /**
   * Los clientes web deben enviar el token en la cookie HttpOnly (nunca en el body, donde sería
   * accesible a JavaScript) y desde un origen permitido; los móviles, en el body.
   */
  private static void checkTransport(LoginClient client, RefreshCommand command) {
    if (client.usesRefreshCookie() != command.fromCookie()) {
      throw new DomainException(
          ErrorCode.INVALID_REFRESH_TOKEN,
          client.usesRefreshCookie()
              ? "Web clients must send the refresh token cookie"
              : "Mobile clients must send the refresh token in the request body");
    }
    if (client.usesRefreshCookie()
        && command.origin() != null
        && !client.allowedOrigins().contains(command.origin().toLowerCase(Locale.ROOT))) {
      throw new DomainException(
          ErrorCode.AUTH_INVALID_CLIENT, "Origin not allowed for this client");
    }
  }

  private void revoke(Session session, SessionRevocationReason reason, Instant now) {
    session.revoke(reason, now);
    sessions.save(session);
  }
}
