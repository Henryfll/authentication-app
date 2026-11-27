package com.henryfll.auth.apps.infrastructure.persistence;

import com.henryfll.auth.apps.application.port.out.RoleRepository;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.PermissionId;
import com.henryfll.auth.apps.domain.Role;
import com.henryfll.auth.apps.domain.RoleId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class RoleRepositoryAdapter implements RoleRepository {

  private final RoleJpaRepository roles;

  RoleRepositoryAdapter(RoleJpaRepository roles) {
    this.roles = roles;
  }

  @Override
  public Role save(Role role) {
    RoleJpaEntity e =
        roles.findById(role.id().value()).orElseGet(() -> new RoleJpaEntity(role.id().value()));
    e.applicationId = role.applicationId().value();
    e.code = role.code();
    e.name = role.name();
    e.description = role.description();
    e.permissionIds.clear();
    e.permissionIds.addAll(
        role.permissionIds().stream().map(PermissionId::value).collect(Collectors.toSet()));
    e.createdAt = role.createdAt();
    e.updatedAt = role.updatedAt();
    return toDomain(roles.saveAndFlush(e));
  }

  @Override
  public Optional<Role> findById(RoleId id) {
    return roles.findById(id.value()).map(RoleRepositoryAdapter::toDomain);
  }

  @Override
  public List<Role> findByApplication(ApplicationId applicationId) {
    return roles.findByApplicationId(applicationId.value()).stream()
        .map(RoleRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByApplicationAndCode(ApplicationId applicationId, String code) {
    return roles.existsByApplicationIdAndCode(applicationId.value(), code);
  }

  private static Role toDomain(RoleJpaEntity e) {
    return Role.restore(
        new RoleId(e.id),
        new ApplicationId(e.applicationId),
        e.code,
        e.name,
        e.description,
        new HashSet<>(e.permissionIds).stream().map(PermissionId::new).collect(Collectors.toSet()),
        e.createdAt,
        e.updatedAt);
  }
}
