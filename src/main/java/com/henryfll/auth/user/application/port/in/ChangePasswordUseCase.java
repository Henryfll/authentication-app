package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.user.domain.UserId;
import java.util.UUID;

/** Cambio de contraseña por el propio usuario y restablecimiento por un administrador. */
public interface ChangePasswordUseCase {

  /**
   * Cambia la contraseña del usuario y cierra el resto de sus sesiones.
   *
   * @param currentSessionId sesión desde la que se hace el cambio, que se mantiene abierta; {@code
   *     null} para cerrarlas todas.
   * @throws com.henryfll.auth.shared.error.ValidationException si la contraseña actual no coincide
   *     ({@code INVALID_CURRENT_PASSWORD}) o la nueva no cumple la política.
   */
  void changeOwnPassword(
      UserId userId, String currentPassword, String newPassword, UUID currentSessionId);

  /**
   * Restablece la contraseña (administrador), levanta el bloqueo temporal y cierra todas las
   * sesiones.
   */
  void resetPassword(UserId userId, String newPassword);
}
