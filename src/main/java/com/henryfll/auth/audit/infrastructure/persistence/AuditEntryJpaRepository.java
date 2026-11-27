package com.henryfll.auth.audit.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AuditEntryJpaRepository
    extends JpaRepository<AuditEntryJpaEntity, UUID>,
        JpaSpecificationExecutor<AuditEntryJpaEntity> {}
