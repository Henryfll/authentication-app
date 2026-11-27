package com.henryfll.auth.apps.infrastructure.persistence;

import com.henryfll.auth.apps.application.port.out.PermissionRepository;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PermissionRepositoryAdapter implements PermissionRepository {

  private final PermissionJpaRepository permissions;

  PermissionRepositoryAdapter(PermissionJpaRepository permissions) {
    this.permissions = permissions;
  }

  @Override
  public Permission save(Permission permission) {
    PermissionJpaEntity e = new PermissionJpaEntity(permission.id().value());
    e.applicationId = permission.applicationId().value();
    e.code = permission.code();
    e.description = permission.description();
    e.createdAt = permission.createdAt();
    return toDomain(permissions.saveAndFlush(e));
  }

  @Override
  public List<Permission> findByApplication(ApplicationId applicationId) {
    return permissions.findByApplicationId(applicationId.value()).stream()
        .map(PermissionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Permission> findAllById(Collection<PermissionId> ids) {
    List<UUID> raw = ids.stream().map(PermissionId::value).toList();
    return permissions.findAllById(raw).stream()
        .map(PermissionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByApplicationAndCode(ApplicationId applicationId, String code) {
    return permissions.existsByApplicationIdAndCode(applicationId.value(), code);
  }

  private static Permission toDomain(PermissionJpaEntity e) {
    return Permission.restore(
        new PermissionId(e.id),
        new ApplicationId(e.applicationId),
        e.code,
        e.description,
        e.createdAt);
  }
}
