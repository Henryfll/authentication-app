package com.henryfll.auth.access.domain;

import java.util.UUID;

/**
 * Se publica cuando un acceso deja de estar activo (suspendido o revocado). El módulo auth lo
 * escucha para cerrar las sesiones del usuario en esa aplicación (docs/ARCHITECTURE.md §5.4).
 */
public record AccessWithdrawnEvent(UUID userId, UUID applicationId, AccessStatus status) {}
