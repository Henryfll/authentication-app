package com.henryfll.auth.auth.application.service;

import com.henryfll.auth.auth.application.port.in.ManageSessionsUseCase;
import com.henryfll.auth.auth.application.port.in.RevokeSessionsUseCase;
import com.henryfll.auth.auth.application.port.out.ClientDirectory;
import com.henryfll.auth.auth.application.port.out.SessionRepository;
import com.henryfll.auth.auth.domain.LoginClient;
import com.henryfll.auth.auth.domain.Session;
import com.henryfll.auth.auth.domain.SessionRevocationReason;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SessionService implements ManageSessionsUseCase, RevokeSessionsUseCase {

  private final SessionRepository sessions;
  private final ClientDirectory clients;
  private final Clock clock;

  SessionService(SessionRepository sessions, ClientDirectory clients, Clock clock) {
    this.sessions = sessions;
    this.clients = clients;
    this.clock = clock;
  }

  @Override
  public void logout(UUID userId, UUID sessionId) {
    sessions
        .findById(sessionId)
        .filter(s -> s.userId().equals(userId))
        .ifPresent(s -> revoke(s, SessionRevocationReason.LOGOUT, clock.instant()));
  }

  @Override
  public int logoutAll(UUID userId) {
    return revokeAll(sessions.findActiveByUser(userId), SessionRevocationReason.LOGOUT_ALL);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SessionView> listActive(UUID userId) {
    Map<UUID, Optional<LoginClient>> clientCache = new HashMap<>();
    return sessions.findActiveByUser(userId).stream()
        .sorted(Comparator.comparing(Session::lastUsedAt).reversed())
        .map(session -> {
          Optional<LoginClient> client =
              clientCache.computeIfAbsent(session.clientId(), clients::findById);
          return new SessionView(
              session,
              client.map(LoginClient::applicationCode).orElse(null),
              client.map(LoginClient::clientId).orElse(null),
              client.map(LoginClient::platform).orElse(null));
        })
        .toList();
  }

  @Override
  public void revokeOwn(UUID userId, UUID sessionId) {
    Session session =
        sessions
            .findById(sessionId)
            .filter(s -> s.userId().equals(userId) && s.isActive())
            .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND, sessionId));
    revoke(session, SessionRevocationReason.LOGOUT, clock.instant());
  }

  @Override
  public int revokeForApplication(UUID userId, UUID applicationId, SessionRevocationReason reason) {
    return revokeAll(sessions.findActiveByUserAndApplication(userId, applicationId), reason);
  }

  @Override
  public int revokeAllForUser(UUID userId, SessionRevocationReason reason) {
    return revokeAll(sessions.findActiveByUser(userId), reason);
  }

  private int revokeAll(List<Session> active, SessionRevocationReason reason) {
    Instant now = clock.instant();
    active.forEach(s -> revoke(s, reason, now));
    return active.size();
  }

  private void revoke(Session session, SessionRevocationReason reason, Instant now) {
    session.revoke(reason, now);
    sessions.save(session);
  }
}
