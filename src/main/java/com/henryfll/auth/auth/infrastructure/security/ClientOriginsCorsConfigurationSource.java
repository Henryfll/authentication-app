package com.henryfll.auth.auth.infrastructure.security;

import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * CORS dinámico: se aceptan los orígenes configurados en los clientes {@code WEB} activos de
 * aplicaciones activas (docs/ARCHITECTURE.md §8). La lista se cachea durante {@code cacheTtl}.
 */
class ClientOriginsCorsConfigurationSource implements CorsConfigurationSource {

  private final ManageClientsUseCase clients;
  private final Duration cacheTtl;
  private final Clock clock;
  private volatile Set<String> cachedOrigins = Set.of();
  private volatile Instant cachedAt = Instant.EPOCH;

  ClientOriginsCorsConfigurationSource(
      ManageClientsUseCase clients, Duration cacheTtl, Clock clock) {
    this.clients = clients;
    this.cacheTtl = cacheTtl;
    this.clock = clock;
  }

  @Override
  public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin == null || !allowedOrigins().contains(origin.toLowerCase(Locale.ROOT))) {
      return null;
    }
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(origin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Client-Id"));
    config.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.WWW_AUTHENTICATE));
    // Necesario para la cookie HttpOnly del refresh token (fase 4).
    config.setAllowCredentials(true);
    config.setMaxAge(Duration.ofHours(1));
    return config;
  }

  private Set<String> allowedOrigins() {
    Instant now = clock.instant();
    if (cacheTtl.isZero() || now.isAfter(cachedAt.plus(cacheTtl))) {
      cachedOrigins = clients.activeWebOrigins();
      cachedAt = now;
    }
    return cachedOrigins;
  }
}
