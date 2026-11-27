package com.henryfll.auth.apps.application.service;

import com.henryfll.auth.apps.application.port.in.ManagePermissionsUseCase;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase;
import com.henryfll.auth.apps.application.port.out.ApplicationRepository;
import com.henryfll.auth.apps.application.port.out.PermissionRepository;
import com.henryfll.auth.apps.application.port.out.RoleRepository;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import com.henryfll.auth.apps.domain.Role;
import com.henryfll.auth.apps.domain.RoleId;
import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permisos y roles de cada aplicación. */
@Service
@Transactional
class AuthorizationCatalogService implements ManagePermissionsUseCase, ManageRolesUseCase {

  private final ApplicationRepository applications;
  private final PermissionRepository permissions;
  private final RoleRepository roles;
  private final AuditLog audit;
  private final Clock clock;

  AuthorizationCatalogService(
      ApplicationRepository applications,
      PermissionRepository permissions,
      RoleRepository roles,
      AuditLog audit,
      Clock clock) {
    this.applications = applications;
    this.permissions = permissions;
    this.roles = roles;
    this.audit = audit;
    this.clock = clock;
  }

  // --- Permisos ---

  @Override
  public Permission createPermission(ApplicationId applicationId, CreatePermissionCommand command) {
    requireApplication(applicationId);
    Permission permission =
        Permission.create(applicationId, command.code(), command.description(), clock.instant());
    if (permissions.existsByApplicationAndCode(applicationId, permission.code())) {
      throw new ConflictException(
          ErrorCode.PERMISSION_ALREADY_EXISTS,
          "Permission '" + permission.code() + "' already exists in this application");
    }
    Permission saved = permissions.save(permission);
    audit.record(
        AuditEvent.of(AuditEventType.PERMISSION_CREATED, null, applicationId.value())
            .with("code", saved.code()));
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Permission> listPermissions(ApplicationId applicationId) {
    requireApplication(applicationId);
    return permissions.findByApplication(applicationId).stream()
        .sorted(Comparator.comparing(Permission::code))
        .toList();
  }

  // --- Roles ---

  @Override
  public RoleView createRole(ApplicationId applicationId, CreateRoleCommand command) {
    requireApplication(applicationId);
    Instant now = clock.instant();
    Role role =
        Role.create(applicationId, command.code(), command.name(), command.description(), now);
    if (roles.existsByApplicationAndCode(applicationId, role.code())) {
      throw new ConflictException(
          ErrorCode.ROLE_ALREADY_EXISTS,
          "Role '" + role.code() + "' already exists in this application");
    }
    Set<PermissionId> permissionIds =
        command.permissionIds() == null ? Set.of() : command.permissionIds();
    List<Permission> resolved = resolvePermissions(permissionIds);
    role.replacePermissions(resolved, now);
    RoleView created = view(roles.save(role), resolved);
    audit.record(roleEvent(AuditEventType.ROLE_CREATED, created));
    return created;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleView> listRoles(ApplicationId applicationId) {
    requireApplication(applicationId);
    Map<PermissionId, Permission> catalog =
        permissions.findByApplication(applicationId).stream()
            .collect(Collectors.toMap(Permission::id, Function.identity()));
    return roles.findByApplication(applicationId).stream()
        .sorted(Comparator.comparing(Role::code))
        .map(role ->
            view(
                role,
                role.permissionIds().stream()
                    .map(catalog::get)
                    .filter(java.util.Objects::nonNull)
                    .toList()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public RoleView getRole(ApplicationId applicationId, RoleId roleId) {
    Role role = requireRole(applicationId, roleId);
    return view(role, permissions.findAllById(role.permissionIds()));
  }

  @Override
  public RoleView replacePermissions(
      ApplicationId applicationId, RoleId roleId, Set<PermissionId> permissionIds) {
    Role role = requireRole(applicationId, roleId);
    List<Permission> resolved = resolvePermissions(permissionIds);
    role.replacePermissions(resolved, clock.instant());
    RoleView updated = view(roles.save(role), resolved);
    audit.record(roleEvent(AuditEventType.ROLE_PERMISSIONS_CHANGED, updated));
    return updated;
  }

  private static AuditEvent roleEvent(AuditEventType type, RoleView view) {
    return AuditEvent.of(type, null, view.role().applicationId().value())
        .with("role", view.role().code())
        .with("permissions", view.permissions().stream().map(Permission::code).toList());
  }

  /** Carga los permisos pedidos; falla si alguno no existe. */
  private List<Permission> resolvePermissions(Set<PermissionId> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    List<Permission> found = permissions.findAllById(ids);
    if (found.size() != ids.size()) {
      Set<PermissionId> missing = new HashSet<>(ids);
      found.forEach(p -> missing.remove(p.id()));
      throw new NotFoundException(ErrorCode.PERMISSION_NOT_FOUND, missing.iterator().next());
    }
    return found;
  }

  private static RoleView view(Role role, List<Permission> rolePermissions) {
    return new RoleView(
        role, rolePermissions.stream().sorted(Comparator.comparing(Permission::code)).toList());
  }

  private void requireApplication(ApplicationId id) {
    if (applications.findById(id).isEmpty()) {
      throw new NotFoundException(ErrorCode.APPLICATION_NOT_FOUND, id);
    }
  }

  private Role requireRole(ApplicationId applicationId, RoleId roleId) {
    requireApplication(applicationId);
    return roles
        .findById(roleId)
        .filter(r -> r.applicationId().equals(applicationId))
        .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, roleId));
  }
}
