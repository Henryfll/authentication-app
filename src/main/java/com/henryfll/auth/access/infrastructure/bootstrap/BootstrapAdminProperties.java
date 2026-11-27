package com.henryfll.auth.access.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales del primer administrador ({@code AUTH_BOOTSTRAP_ADMIN_EMAIL} y {@code
 * AUTH_BOOTSTRAP_ADMIN_PASSWORD}). Si el email está vacío no se crea ningún usuario.
 */
@ConfigurationProperties("app.bootstrap.admin")
record BootstrapAdminProperties(String email, String password) {

  boolean enabled() {
    return email != null && !email.isBlank();
  }

  @Override
  public String toString() {
    return "BootstrapAdminProperties[email=" + email + ", password=***]";
  }
}
