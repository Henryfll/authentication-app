package com.henryfll.auth.apps.application.port.in;

import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import java.util.List;

/** Catálogo de permisos de una aplicación. */
public interface ManagePermissionsUseCase {

  Permission createPermission(ApplicationId applicationId, CreatePermissionCommand command);

  List<Permission> listPermissions(ApplicationId applicationId);

  record CreatePermissionCommand(String code, String description) {}
}
