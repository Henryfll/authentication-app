package com.henryfll.auth.access.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessGrantJpaRepository extends JpaRepository<AccessGrantJpaEntity, UUID> {

  Optional<AccessGrantJpaEntity> findByUserIdAndApplicationId(UUID userId, UUID applicationId);

  Page<AccessGrantJpaEntity> findByApplicationId(UUID applicationId, Pageable pageable);

  Page<AccessGrantJpaEntity> findByApplicationIdAndStatus(
      UUID applicationId, String status, Pageable pageable);

  List<AccessGrantJpaEntity> findByUserId(UUID userId);
}
