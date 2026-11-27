package com.henryfll.auth.apps.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationClientJpaRepository extends JpaRepository<ApplicationClientJpaEntity, UUID> {
  List<ApplicationClientJpaEntity> findByApplicationIdOrderByCreatedAt(UUID applicationId);

  Optional<ApplicationClientJpaEntity> findByClientId(String clientId);

  List<ApplicationClientJpaEntity> findByPlatformAndStatus(String platform, String status);

  boolean existsByClientId(String clientId);
}
