package com.henryfll.auth.access.domain;

import java.util.UUID;

/** Vista mínima de un rol del módulo apps. */
public record RoleRef(UUID id, String code) {}
