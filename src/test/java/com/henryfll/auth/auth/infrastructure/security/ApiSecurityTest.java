package com.henryfll.auth.auth.infrastructure.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ApiSecurityTest extends IntegrationTest {

  private static final String USERS = "/api/v1/admin/users";
  private static final String APPS = "/api/v1/admin/applications";

  private ResultActions as(
      String token,
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    return anonymous.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
  }

  private String adminToken(String role, List<String> permissions) {
    return token(testAdminId(), AUTH_ADMIN, List.of(role), permissions);
  }

  @Test
  void requestsWithoutTokenGetA401ProblemDetail() throws Exception {
    anonymous
        .perform(get(USERS))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value(USERS));
    anonymous.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidOrTamperedTokensAreRejected() throws Exception {
    anonymous
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

    String valid = adminToken("SUPER_ADMIN", ALL_ADMIN_PERMISSIONS);
    String[] parts = valid.split("\\.");
    String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
    as(tampered, get(USERS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void adminEndpointsRequireTheMatchingPermission() throws Exception {
    String auditor = adminToken("AUDITOR", List.of("audit:read"));
    as(auditor, get(USERS))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    as(auditor, get(APPS)).andExpect(status().isForbidden());

    String accessManager =
        adminToken(
            "ACCESS_MANAGER",
            List.of(
                "applications:read", "users:read", "users:write", "access:read", "access:write"));
    as(accessManager, get(USERS)).andExpect(status().isOk());
    as(accessManager, get(APPS)).andExpect(status().isOk());
    as(
            accessManager,
            post(APPS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"name\": \"X\"}".formatted(unique("deny"))))
        .andExpect(status().isForbidden());
    as(accessManager, get(USERS + "/" + testAdminId() + "/access")).andExpect(status().isOk());

    String appManager =
        adminToken("APP_MANAGER", List.of("applications:read", "applications:write"));
    as(appManager, get(USERS)).andExpect(status().isForbidden());
    as(appManager, get(USERS + "/" + testAdminId() + "/access")).andExpect(status().isForbidden());
    as(
            appManager,
            post(APPS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"name\": \"X\"}".formatted(unique("allow"))))
        .andExpect(status().isCreated());

    as(adminToken("SUPER_ADMIN", ALL_ADMIN_PERMISSIONS), get("/api/v1/admin/unknown"))
        .andExpect(status().isForbidden());
  }

  @Test
  void permissionsOnlyCountInTokensIssuedForAuthAdmin() throws Exception {
    // Otra aplicación con un rol SUPER_ADMIN y los mismos permisos: no da acceso.
    String foreign = token(testAdminId(), "erp", List.of("SUPER_ADMIN"), ALL_ADMIN_PERMISSIONS);
    as(foreign, get(USERS))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    as(foreign, get("/api/v1/users/me")).andExpect(status().isOk());
  }

  @Test
  void expiredTokensAreRejected() throws Exception {
    Instant past = Instant.now().minusSeconds(3600);
    String expired =
        tokenIssuer.issue(
            new com.henryfll.auth.auth.domain.AccessTokenClaims(
                testAdminId(),
                null,
                AUTH_ADMIN,
                "test",
                List.of(),
                ALL_ADMIN_PERMISSIONS,
                past,
                past.plusSeconds(60),
                UUID.randomUUID()));
    as(expired, get(USERS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void publishesThePublicKeyAsJwks() throws Exception {
    anonymous
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys.length()").value(1))
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").value("auth-key-1"))
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].use").value("sig"))
        .andExpect(jsonPath("$.keys[0].n").exists())
        .andExpect(jsonPath("$.keys[0].d").doesNotExist())
        .andExpect(jsonPath("$.keys[0].p").doesNotExist());
  }

  @Test
  void corsAllowsOnlyOriginsOfActiveWebClients() throws Exception {
    String code = unique("cors");
    String appId =
        read(
            mockMvc.perform(
                post(APPS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\": \"%s\", \"name\": \"CORS\"}".formatted(code))),
            "$.id");
    String origin = "https://" + code + ".example.com";
    mockMvc
        .perform(
            post(APPS + "/" + appId + "/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Web\", \"platform\": \"WEB\", \"allowedOrigins\": [\"%s\"]}"
                        .formatted(origin)))
        .andExpect(status().isCreated());

    anonymous
        .perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-client-id"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

    anonymous
        .perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void publicEndpointsDoNotNeedAToken() throws Exception {
    anonymous.perform(get("/actuator/health")).andExpect(status().isOk());
    anonymous.perform(get("/v3/api-docs")).andExpect(status().isOk());
    anonymous
        .perform(get("/v3/api-docs"))
        .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
  }
}
