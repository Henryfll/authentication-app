package com.henryfll.auth.user.domain;

import java.util.UUID;

/**
 * Se publica cuando una cuenta pasa a {@code LOCKED} o {@code DISABLED}. El módulo auth lo escucha
 * para cerrar todas las sesiones del usuario.
 */
public record UserDeactivatedEvent(UUID userId, UserStatus status) {}
