package com.henryfll.auth.apps.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationClientJpaRepository extends JpaRepository<ApplicationClientJpaEntity, UUID> {
  List<ApplicationClientJpaEntity> findByApplicationIdOrderByCreatedAt(UUID applicationId);

  boolean existsByClientId(String clientId);
}
