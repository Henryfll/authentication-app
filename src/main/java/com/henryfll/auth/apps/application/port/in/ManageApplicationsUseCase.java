package com.henryfll.auth.apps.application.port.in;

import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.RoleId;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;

/** Alta, consulta y configuración de aplicaciones. */
public interface ManageApplicationsUseCase {

  Application create(CreateApplicationCommand command);

  /** Los campos {@code null} del comando no se modifican. */
  Application update(ApplicationId id, UpdateApplicationCommand command);

  Application get(ApplicationId id);

  PageResult<Application> list(PageQuery page);

  record CreateApplicationCommand(String code, String name, String description) {}

  record UpdateApplicationCommand(
      String name,
      String description,
      ActivationStatus status,
      Boolean selfRegistration,
      RoleId defaultRoleId) {}
}
