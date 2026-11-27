package com.henryfll.auth.auth.infrastructure.security;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** Carga y generación de claves RSA. */
final class RsaKeys {

  static final int KEY_SIZE = 2048;

  private RsaKeys() {}

  /** Lee una clave privada PEM PKCS#8 ({@code BEGIN PRIVATE KEY}) y deriva la pública. */
  static KeyPair fromPkcs8Pem(String pem) {
    String base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replaceAll("\\s", "");
    if (pem.contains("BEGIN RSA PRIVATE KEY")) {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY must be PKCS#8 (BEGIN PRIVATE KEY). Convert it with:"
              + " openssl pkcs8 -topk8 -nocrypt -in key.pem");
    }
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      RSAPrivateCrtKey privateKey =
          (RSAPrivateCrtKey)
              factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
      if (privateKey.getModulus().bitLength() < KEY_SIZE) {
        throw new IllegalStateException("JWT_PRIVATE_KEY must be at least " + KEY_SIZE + " bits");
      }
      BigInteger exponent = privateKey.getPublicExponent();
      RSAPublicKey publicKey =
          (RSAPublicKey)
              factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), exponent));
      return new KeyPair(publicKey, privateKey);
    } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
      throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid RSA PKCS#8 private key", e);
    }
  }

  static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(KEY_SIZE);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot generate RSA key", e);
    }
  }
}
