package com.henryfll.auth.auth.infrastructure.persistence;

import com.henryfll.auth.auth.application.port.out.SessionRepository;
import com.henryfll.auth.auth.domain.Session;
import com.henryfll.auth.auth.domain.SessionRevocationReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class SessionRepositoryAdapter implements SessionRepository {

  private final SessionJpaRepository jpa;

  SessionRepositoryAdapter(SessionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Session save(Session session) {
    SessionJpaEntity e =
        jpa.findById(session.id()).orElseGet(() -> new SessionJpaEntity(session.id()));
    e.userId = session.userId();
    e.applicationId = session.applicationId();
    e.clientId = session.clientId();
    e.ipAddress = session.ipAddress();
    e.userAgent = session.userAgent();
    e.deviceName = session.deviceName();
    e.createdAt = session.createdAt();
    e.lastUsedAt = session.lastUsedAt();
    e.revokedAt = session.revokedAt();
    e.revokedReason = session.revokedReason() == null ? null : session.revokedReason().name();
    return toDomain(jpa.saveAndFlush(e));
  }

  @Override
  public Optional<Session> findById(UUID id) {
    return jpa.findById(id).map(SessionRepositoryAdapter::toDomain);
  }

  @Override
  public List<Session> findActiveByUser(UUID userId) {
    return jpa.findByUserIdAndRevokedAtIsNull(userId).stream()
        .map(SessionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Session> findActiveByUserAndApplication(UUID userId, UUID applicationId) {
    return jpa.findByUserIdAndApplicationIdAndRevokedAtIsNull(userId, applicationId).stream()
        .map(SessionRepositoryAdapter::toDomain)
        .toList();
  }

  private static Session toDomain(SessionJpaEntity e) {
    return Session.restore(
        e.id,
        e.userId,
        e.applicationId,
        e.clientId,
        e.ipAddress,
        e.userAgent,
        e.deviceName,
        e.createdAt,
        e.lastUsedAt,
        e.revokedAt,
        e.revokedReason == null ? null : SessionRevocationReason.valueOf(e.revokedReason));
  }
}
