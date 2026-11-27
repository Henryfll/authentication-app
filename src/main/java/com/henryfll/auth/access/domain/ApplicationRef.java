package com.henryfll.auth.access.domain;

import java.util.UUID;

/** Vista mínima de una aplicación del módulo apps, la que necesita el módulo access. */
public record ApplicationRef(UUID id, String code, String name, boolean active) {}
