package com.henryfll.auth.user.domain;

import java.util.UUID;

/**
 * Se publica cuando cambia la contraseña de un usuario. El módulo auth cierra sus sesiones: todas
 * si la restableció un administrador, o todas menos {@code currentSessionId} si la cambió el propio
 * usuario.
 *
 * @param currentSessionId sesión desde la que se hizo el cambio; {@code null} para cerrarlas todas.
 */
public record PasswordChangedEvent(UUID userId, UUID currentSessionId, boolean resetByAdmin) {}
