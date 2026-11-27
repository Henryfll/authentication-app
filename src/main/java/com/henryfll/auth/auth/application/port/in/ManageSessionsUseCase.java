package com.henryfll.auth.auth.application.port.in;

import com.henryfll.auth.auth.domain.Session;
import java.util.List;
import java.util.UUID;

/** Sesiones del usuario autenticado: logout, logout-all y gestión de dispositivos. */
public interface ManageSessionsUseCase {

  /** Cierra una sesión del usuario. No falla si ya estaba cerrada. */
  void logout(UUID userId, UUID sessionId);

  /** Cierra todas las sesiones activas del usuario y devuelve cuántas cerró. */
  int logoutAll(UUID userId);

  List<SessionView> listActive(UUID userId);

  /**
   * Cierra una sesión concreta del usuario.
   *
   * @throws com.henryfll.auth.shared.error.NotFoundException si no existe, no es del usuario o ya
   *     estaba cerrada.
   */
  void revokeOwn(UUID userId, UUID sessionId);

  /**
   * @param applicationCode, clientId, platform datos del cliente desde el que se abrió la sesión.
   */
  record SessionView(Session session, String applicationCode, String clientId, String platform) {}
}
