package com.henryfll.auth.access.infrastructure.persistence;

import com.henryfll.auth.access.application.port.out.AccessGrantRepository;
import com.henryfll.auth.access.domain.AccessGrant;
import com.henryfll.auth.access.domain.AccessGrantId;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class AccessGrantRepositoryAdapter implements AccessGrantRepository {

  private final AccessGrantJpaRepository jpa;

  AccessGrantRepositoryAdapter(AccessGrantJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AccessGrant save(AccessGrant grant) {
    AccessGrantJpaEntity e =
        jpa.findById(grant.id().value())
            .orElseGet(() -> new AccessGrantJpaEntity(grant.id().value()));
    e.userId = grant.userId();
    e.applicationId = grant.applicationId();
    e.status = grant.status().name();
    e.validFrom = grant.validFrom();
    e.validUntil = grant.validUntil();
    e.grantedBy = grant.grantedBy();
    e.roleIds.clear();
    e.roleIds.addAll(grant.roleIds());
    e.createdAt = grant.createdAt();
    e.updatedAt = grant.updatedAt();
    return toDomain(jpa.saveAndFlush(e));
  }

  @Override
  public Optional<AccessGrant> find(UUID userId, UUID applicationId) {
    return jpa.findByUserIdAndApplicationId(userId, applicationId)
        .map(AccessGrantRepositoryAdapter::toDomain);
  }

  @Override
  public PageResult<AccessGrant> findByApplication(
      UUID applicationId, AccessStatus status, PageQuery page) {
    PageRequest request =
        PageRequest.of(page.page(), page.size(), Sort.by("createdAt").descending());
    Page<AccessGrantJpaEntity> result =
        status == null
            ? jpa.findByApplicationId(applicationId, request)
            : jpa.findByApplicationIdAndStatus(applicationId, status.name(), request);
    return new PageResult<>(
        result.getContent().stream().map(AccessGrantRepositoryAdapter::toDomain).toList(),
        page.page(),
        page.size(),
        result.getTotalElements());
  }

  @Override
  public List<AccessGrant> findByUser(UUID userId) {
    return jpa.findByUserId(userId).stream().map(AccessGrantRepositoryAdapter::toDomain).toList();
  }

  private static AccessGrant toDomain(AccessGrantJpaEntity e) {
    return AccessGrant.restore(
        new AccessGrantId(e.id),
        e.userId,
        e.applicationId,
        AccessStatus.valueOf(e.status),
        e.validFrom,
        e.validUntil,
        new HashSet<>(e.roleIds),
        e.grantedBy,
        e.createdAt,
        e.updatedAt);
  }
}
