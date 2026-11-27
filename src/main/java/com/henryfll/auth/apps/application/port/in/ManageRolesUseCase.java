package com.henryfll.auth.apps.application.port.in;

import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import com.henryfll.auth.apps.domain.Role;
import com.henryfll.auth.apps.domain.RoleId;
import java.util.List;
import java.util.Set;

/** Roles de una aplicación y sus permisos. */
public interface ManageRolesUseCase {

  RoleView createRole(ApplicationId applicationId, CreateRoleCommand command);

  List<RoleView> listRoles(ApplicationId applicationId);

  RoleView getRole(ApplicationId applicationId, RoleId roleId);

  /** Reemplaza el conjunto completo de permisos del rol. */
  RoleView replacePermissions(
      ApplicationId applicationId, RoleId roleId, Set<PermissionId> permissionIds);

  record CreateRoleCommand(
      String code, String name, String description, Set<PermissionId> permissionIds) {}

  /** Rol con sus permisos resueltos. */
  record RoleView(Role role, List<Permission> permissions) {}
}
