package com.henryfll.auth.access.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Autorización efectiva de un usuario en una aplicación: lo que se incluye en su access token. */
public interface ResolveAuthorizationUseCase {

  /**
   * @return vacío si el usuario no tiene un acceso efectivo ahora (inexistente, suspendido,
   *     revocado, fuera de vigencia) o si la aplicación está inactiva.
   */
  Optional<EffectiveAuthorization> resolve(UUID userId, UUID applicationId);

  /**
   * @param roles códigos de los roles asignados, ordenados.
   * @param permissions unión de los permisos de esos roles, ordenados y sin duplicados.
   */
  record EffectiveAuthorization(
      UUID applicationId, String applicationCode, List<String> roles, List<String> permissions) {}
}
