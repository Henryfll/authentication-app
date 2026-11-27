package com.henryfll.auth.access.application.port.out;

import com.henryfll.auth.access.domain.ApplicationRef;
import com.henryfll.auth.access.domain.RoleRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consulta de aplicaciones y roles del módulo apps. */
public interface ApplicationCatalog {

  Optional<ApplicationRef> findApplication(UUID applicationId);

  List<RoleRef> rolesOf(UUID applicationId);
}
