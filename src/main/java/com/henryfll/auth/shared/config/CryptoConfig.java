package com.henryfll.auth.shared.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Codificador de contraseñas y secretos. Usa Argon2id para los hashes nuevos; el prefijo {@code
 * {id}} de cada hash permite migrar de algoritmo sin invalidar los existentes.
 */
@Configuration(proxyBeanMethods = false)
class CryptoConfig {

  static final String DEFAULT_ENCODER = "argon2";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        DEFAULT_ENCODER,
        Map.of(
            DEFAULT_ENCODER,
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "bcrypt",
            new BCryptPasswordEncoder()));
  }
}
