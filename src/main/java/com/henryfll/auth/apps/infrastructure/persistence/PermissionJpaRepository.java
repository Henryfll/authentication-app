package com.henryfll.auth.apps.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionJpaRepository extends JpaRepository<PermissionJpaEntity, UUID> {
  List<PermissionJpaEntity> findByApplicationId(UUID applicationId);

  boolean existsByApplicationIdAndCode(UUID applicationId, String code);
}
