package com.henryfll.auth.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de firma de los access tokens.
 *
 * @param issuer valor del claim {@code iss} ({@code JWT_ISSUER}).
 * @param keyId {@code kid} de la clave, publicado en el JWKS ({@code JWT_KEY_ID}).
 * @param privateKey clave privada RSA en PEM PKCS#8 ({@code JWT_PRIVATE_KEY}).
 * @param allowEphemeralKey si no hay clave configurada, generar una en memoria al arrancar. Solo
 *     para desarrollo y tests: los tokens dejan de ser válidos al reiniciar.
 */
@ConfigurationProperties("app.security.jwt")
record JwtProperties(String issuer, String keyId, String privateKey, boolean allowEphemeralKey) {

  /**
   * Spring Boot no falla al enlazar un {@code ${JWT_ISSUER}} sin resolver: deja el texto literal.
   * Sin esta comprobación, un despliegue sin la variable firmaría tokens con ese literal como
   * emisor.
   */
  JwtProperties {
    if (issuer == null || issuer.isBlank() || issuer.contains("${")) {
      throw new IllegalStateException(
          "JWT_ISSUER is required: the public URL of this service, e.g. https://auth.example.com");
    }
  }

  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + ", keyId=" + keyId + ", privateKey=***]";
  }
}
