package com.henryfll.auth.access.application.port.out;

import java.util.UUID;

/** Consulta de usuarios del módulo user. */
public interface UserDirectory {

  boolean exists(UUID userId);
}
