package com.henryfll.auth.apps.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {
  List<RoleJpaEntity> findByApplicationId(UUID applicationId);

  boolean existsByApplicationIdAndCode(UUID applicationId, String code);
}
