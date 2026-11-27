package com.henryfll.auth.auth.domain;

import java.time.Duration;
import java.util.UUID;

/**
 * Cliente desde el que se intenta el login, con lo que el módulo auth necesita de él.
 *
 * @param active {@code true} solo si el cliente y su aplicación están activos.
 */
public record LoginClient(
    String clientId,
    UUID applicationId,
    String applicationCode,
    String platform,
    boolean confidential,
    boolean active,
    Duration accessTokenTtl) {}
