package com.henryfll.auth.auth.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Clave de firma RS256, codificador y decodificador de JWT. */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

  @Bean
  RSAKey jwtSigningKey(JwtProperties properties) {
    KeyPair keyPair;
    if (properties.privateKey() != null && !properties.privateKey().isBlank()) {
      keyPair = RsaKeys.fromPkcs8Pem(properties.privateKey());
    } else if (properties.allowEphemeralKey()) {
      log.warn(
          "JWT_PRIVATE_KEY is not set: using an ephemeral RSA key. Tokens will not survive a"
              + " restart. Never use this in production.");
      keyPair = RsaKeys.generate();
    } else {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY is required (RSA private key in PEM PKCS#8 format)");
    }
    return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey((RSAPrivateKey) keyPair.getPrivate())
        .keyID(properties.keyId())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .build();
  }

  @Bean
  JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws Exception {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    return decoder;
  }
}
