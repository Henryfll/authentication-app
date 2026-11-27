package com.henryfll.auth.auth.infrastructure.persistence;

import com.henryfll.auth.auth.application.port.out.RefreshTokenRepository;
import com.henryfll.auth.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

  private final RefreshTokenJpaRepository jpa;

  RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public RefreshToken save(RefreshToken token) {
    RefreshTokenJpaEntity e = new RefreshTokenJpaEntity(token.id());
    e.sessionId = token.sessionId();
    e.tokenHash = token.tokenHash();
    e.expiresAt = token.expiresAt();
    e.usedAt = token.usedAt();
    e.replacedBy = token.replacedBy();
    e.createdAt = token.createdAt();
    return toDomain(jpa.saveAndFlush(e));
  }

  @Override
  public Optional<RefreshToken> findByHash(String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(RefreshTokenRepositoryAdapter::toDomain);
  }

  @Override
  public boolean markUsed(UUID tokenId, Instant usedAt) {
    return jpa.markUsed(tokenId, usedAt) == 1;
  }

  @Override
  public void markReplaced(UUID tokenId, UUID replacedBy) {
    jpa.markReplaced(tokenId, replacedBy);
  }

  private static RefreshToken toDomain(RefreshTokenJpaEntity e) {
    return RefreshToken.restore(
        e.id, e.sessionId, e.tokenHash, e.expiresAt, e.usedAt, e.replacedBy, e.createdAt);
  }
}
