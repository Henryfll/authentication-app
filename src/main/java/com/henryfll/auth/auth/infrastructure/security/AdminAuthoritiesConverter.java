package com.henryfll.auth.auth.infrastructure.security;

import com.henryfll.auth.apps.domain.AuthAdminApplication;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte un JWT en autenticación de Spring Security.
 *
 * <p>Solo los tokens emitidos para {@code auth-admin} ({@code aud}) dan autoridades: sus permisos
 * pasan a {@code PERM_<permiso>} y sus roles a {@code ROLE_<rol>}. Un token de otra aplicación
 * autentica al usuario (p. ej. para {@code /users/me}) pero nunca da acceso a la administración,
 * aunque esa aplicación defina roles con el mismo nombre.
 */
class AdminAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String PERMISSION_PREFIX = "PERM_";
  static final String ROLE_PREFIX = "ROLE_";

  static String permission(String code) {
    return PERMISSION_PREFIX + code;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    List<String> audience = jwt.getAudience();
    if (audience != null && audience.contains(AuthAdminApplication.CODE.value())) {
      for (String permission : claimList(jwt, JwtTokenIssuer.CLAIM_PERMISSIONS)) {
        authorities.add(new SimpleGrantedAuthority(permission(permission)));
      }
      for (String role : claimList(jwt, JwtTokenIssuer.CLAIM_ROLES)) {
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
      }
    }
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private static List<String> claimList(Jwt jwt, String claim) {
    List<String> values = jwt.getClaimAsStringList(claim);
    return values == null ? List.of() : values;
  }
}
