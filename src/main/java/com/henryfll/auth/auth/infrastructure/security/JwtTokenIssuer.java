package com.henryfll.auth.auth.infrastructure.security;

import com.henryfll.auth.auth.application.port.out.TokenIssuer;
import com.henryfll.auth.auth.domain.AccessTokenClaims;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Firma los access tokens con RS256 (docs/ARCHITECTURE.md §6.1). */
@Component
class JwtTokenIssuer implements TokenIssuer {

  static final String CLAIM_AUTHORIZED_PARTY = "azp";
  static final String CLAIM_EMAIL = "email";
  static final String CLAIM_ROLES = "roles";
  static final String CLAIM_PERMISSIONS = "permissions";

  private final JwtEncoder encoder;
  private final JwtProperties properties;

  JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  @Override
  public String issue(AccessTokenClaims claims) {
    JwtClaimsSet.Builder set =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(claims.subject().toString())
            .audience(List.of(claims.audience()))
            .id(claims.tokenId().toString())
            .issuedAt(claims.issuedAt())
            .expiresAt(claims.expiresAt())
            .claim(CLAIM_ROLES, claims.roles())
            .claim(CLAIM_PERMISSIONS, claims.permissions());
    if (claims.authorizedParty() != null) {
      set.claim(CLAIM_AUTHORIZED_PARTY, claims.authorizedParty());
    }
    if (claims.email() != null) {
      set.claim(CLAIM_EMAIL, claims.email());
    }
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).type("JWT").build();
    return encoder.encode(JwtEncoderParameters.from(header, set.build())).getTokenValue();
  }
}
