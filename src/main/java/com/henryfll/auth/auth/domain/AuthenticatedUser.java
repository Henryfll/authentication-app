package com.henryfll.auth.auth.domain;

import java.util.UUID;

/** Usuario cuyas credenciales se han verificado. {@code active} refleja el estado de la cuenta. */
public record AuthenticatedUser(UUID id, String email, boolean active) {}
