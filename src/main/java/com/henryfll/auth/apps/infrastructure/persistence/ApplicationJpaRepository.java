package com.henryfll.auth.apps.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationJpaRepository extends JpaRepository<ApplicationJpaEntity, UUID> {
  boolean existsByCode(String code);
}
