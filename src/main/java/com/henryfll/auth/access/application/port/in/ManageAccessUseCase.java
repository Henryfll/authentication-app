package com.henryfll.auth.access.application.port.in;

import com.henryfll.auth.access.domain.AccessGrant;
import com.henryfll.auth.access.domain.AccessStatus;
import com.henryfll.auth.access.domain.ApplicationRef;
import com.henryfll.auth.access.domain.RoleRef;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Concesión, consulta y revocación de accesos de usuarios a aplicaciones. */
public interface ManageAccessUseCase {

  /**
   * Concede el acceso o reemplaza su configuración si ya existe (semántica PUT).
   *
   * @throws com.henryfll.auth.shared.error.NotFoundException si la aplicación, el usuario o alguno
   *     de los roles no existen; los roles deben ser de la aplicación.
   */
  GrantResult grant(UUID applicationId, UUID userId, GrantAccessCommand command);

  /** Revoca el acceso. Las sesiones abiertas se revocarán a partir de la fase 4. */
  void revoke(UUID applicationId, UUID userId);

  AccessView get(UUID applicationId, UUID userId);

  /**
   * @param status {@code null} para no filtrar.
   */
  PageResult<AccessView> listByApplication(UUID applicationId, AccessStatus status, PageQuery page);

  List<AccessView> listByUser(UUID userId);

  /**
   * @param status {@code null} para {@code ACTIVE}.
   * @param validFrom {@code null} para "ahora" (o conservar el actual si el acceso ya existe).
   * @param validUntil {@code null} para que no caduque.
   * @param grantedBy usuario que concede el acceso; {@code null} si no hay usuario autenticado.
   */
  record GrantAccessCommand(
      AccessStatus status,
      Set<UUID> roleIds,
      Instant validFrom,
      Instant validUntil,
      UUID grantedBy) {}

  record GrantResult(AccessView access, boolean created) {}

  /**
   * Acceso con la aplicación y los roles resueltos.
   *
   * @param effective si en este momento permite entrar a la aplicación.
   */
  record AccessView(
      AccessGrant grant, ApplicationRef application, List<RoleRef> roles, boolean effective) {}
}
