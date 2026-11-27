package com.henryfll.auth.auth.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

  List<SessionJpaEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

  List<SessionJpaEntity> findByUserIdAndApplicationIdAndRevokedAtIsNull(
      UUID userId, UUID applicationId);
}
