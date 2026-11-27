package com.henryfll.auth.auth.infrastructure.security;

import static com.henryfll.auth.auth.infrastructure.security.AdminAuthoritiesConverter.permission;

import com.henryfll.auth.apps.application.port.in.ManageClientsUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * API stateless protegida con bearer tokens (JWT RS256).
 *
 * <p>La administración ({@code /api/v1/admin/**}) exige un token emitido para {@code auth-admin}
 * con el permiso correspondiente (docs/ARCHITECTURE.md §7.3). El resto de endpoints de usuario
 * aceptan un token de cualquier aplicación.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  private static final String ADMIN = "/api/v1/admin";
  private static final String[] PUBLIC = {
    "/.well-known/jwks.json",
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/error"
  };

  @Bean
  ClientOriginsCorsConfigurationSource corsConfigurationSource(
      ManageClientsUseCase clients,
      Clock clock,
      @Value("${app.security.cors.origins-cache-ttl:30s}") Duration cacheTtl) {
    return new ClientOriginsCorsConfigurationSource(clients, cacheTtl, clock);
  }

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, ClientOriginsCorsConfigurationSource cors)
      throws Exception {
    var entryPoint = SecurityProblemHandlers.entryPoint();
    var accessDenied = SecurityProblemHandlers.accessDeniedHandler();

    http.csrf(AbstractHttpConfigurer::disable)
        .cors(c -> c.configurationSource(cors))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .anonymous(Customizer.withDefaults())
        .headers(h ->
            h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                .permitAll()
                .requestMatchers(PUBLIC)
                .permitAll()
                // Accesos (más específico que /applications/**: va primero).
                .requestMatchers(
                    HttpMethod.GET,
                    ADMIN + "/applications/*/access",
                    ADMIN + "/applications/*/access/**",
                    ADMIN + "/users/*/access")
                .hasAuthority(permission("access:read"))
                .requestMatchers(
                    ADMIN + "/applications/*/access", ADMIN + "/applications/*/access/**")
                .hasAuthority(permission("access:write"))
                // Aplicaciones, clientes, roles y permisos.
                .requestMatchers(
                    HttpMethod.GET, ADMIN + "/applications", ADMIN + "/applications/**")
                .hasAuthority(permission("applications:read"))
                .requestMatchers(ADMIN + "/applications", ADMIN + "/applications/**")
                .hasAuthority(permission("applications:write"))
                // Usuarios.
                .requestMatchers(HttpMethod.GET, ADMIN + "/users", ADMIN + "/users/**")
                .hasAuthority(permission("users:read"))
                .requestMatchers(ADMIN + "/users", ADMIN + "/users/**")
                .hasAuthority(permission("users:write"))
                // Cualquier otra ruta de administración: denegada por defecto.
                .requestMatchers(ADMIN + "/**")
                .denyAll()
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth ->
            oauth
                .jwt(jwt ->
                    jwt.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new AdminAuthoritiesConverter()))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDenied))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
    return http.build();
  }
}
