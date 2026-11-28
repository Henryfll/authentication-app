package com.henryfll.auth.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Claves públicas para que las aplicaciones validen los access tokens sin llamar al servicio. */
@RestController
@Tag(name = "Auth")
class JwksController {

  private final Map<String, Object> jwks;

  JwksController(RSAKey jwtSigningKey) {
    this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
  }

  @GetMapping("/.well-known/jwks.json")
  @SecurityRequirements
  @Operation(
      operationId = "getJwks",
      summary = "Claves públicas (JWKS) para validar los access tokens")
  ResponseEntity<Map<String, Object>> jwks() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic())
        .body(jwks);
  }
}
