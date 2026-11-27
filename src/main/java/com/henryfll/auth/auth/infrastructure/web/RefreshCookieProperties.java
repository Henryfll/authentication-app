package com.henryfll.auth.auth.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cookie del refresh token para clientes web (docs/ARCHITECTURE.md §6.3): {@code HttpOnly}, {@code
 * SameSite=Strict} y limitada a {@code /api/v1/auth}. {@code secure} solo se desactiva en local
 * (HTTP sin TLS).
 */
@ConfigurationProperties("app.security.refresh-cookie")
record RefreshCookieProperties(String name, boolean secure) {

  static final String PATH = "/api/v1/auth";

  RefreshCookieProperties {
    if (name == null || name.isBlank()) {
      name = "refresh_token";
    }
  }
}
