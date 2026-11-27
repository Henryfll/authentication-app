package com.henryfll.auth.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

  Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

  /** Update condicional: solo uno de dos refresh concurrentes con el mismo token lo consigue. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update RefreshTokenJpaEntity t set t.usedAt = :usedAt"
          + " where t.id = :id and t.usedAt is null")
  int markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshTokenJpaEntity t set t.replacedBy = :replacedBy where t.id = :id")
  int markReplaced(@Param("id") UUID id, @Param("replacedBy") UUID replacedBy);
}
