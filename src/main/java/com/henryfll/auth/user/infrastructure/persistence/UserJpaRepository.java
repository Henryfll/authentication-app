package com.henryfll.auth.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface UserJpaRepository
    extends JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {

  Optional<UserJpaEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
