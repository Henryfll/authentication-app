package com.henryfll.auth.auth.infrastructure.web;

import com.henryfll.auth.auth.application.port.in.AuthTokens;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshCookies {

  private final RefreshCookieProperties properties;

  RefreshCookies(RefreshCookieProperties properties) {
    this.properties = properties;
  }

  String name() {
    return properties.name();
  }

  ResponseCookie issue(AuthTokens tokens) {
    Duration maxAge = Duration.between(Instant.now(), tokens.refreshTokenExpiresAt());
    return base(tokens.refreshToken()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
  }

  ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(properties.name(), value)
        .httpOnly(true)
        .secure(properties.secure())
        .sameSite("Strict")
        .path(RefreshCookieProperties.PATH);
  }
}
