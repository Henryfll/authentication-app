package com.henryfll.auth.access.infrastructure.integration;

import com.henryfll.auth.access.application.port.out.ApplicationCatalog;
import com.henryfll.auth.access.domain.ApplicationRef;
import com.henryfll.auth.access.domain.RoleRef;
import com.henryfll.auth.apps.application.port.in.ManageApplicationsUseCase;
import com.henryfll.auth.apps.application.port.in.ManageRolesUseCase;
import com.henryfll.auth.apps.domain.Application;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.shared.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Traduce el puerto {@link ApplicationCatalog} a los casos de uso públicos del módulo apps. */
@Component
class ApplicationCatalogAdapter implements ApplicationCatalog {

  private final ManageApplicationsUseCase applications;
  private final ManageRolesUseCase roles;

  ApplicationCatalogAdapter(ManageApplicationsUseCase applications, ManageRolesUseCase roles) {
    this.applications = applications;
    this.roles = roles;
  }

  @Override
  public Optional<ApplicationRef> findApplication(UUID applicationId) {
    try {
      Application app = applications.get(new ApplicationId(applicationId));
      return Optional.of(
          new ApplicationRef(app.id().value(), app.code().value(), app.name(), app.isActive()));
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<RoleRef> rolesOf(UUID applicationId) {
    return roles.listRoles(new ApplicationId(applicationId)).stream()
        .map(view -> new RoleRef(view.role().id().value(), view.role().code()))
        .toList();
  }
}
