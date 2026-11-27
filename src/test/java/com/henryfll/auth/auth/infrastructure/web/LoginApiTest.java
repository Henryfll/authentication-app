package com.henryfll.auth.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class LoginApiTest extends IntegrationTest {

  private static final String ADMIN = "/api/v1/admin";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private JwtDecoder jwtDecoder;

  private String appId;
  private String appCode;
  private String clientId;
  private String clientUuid;
  private String userId;
  private String email;

  private ResultActions admin(MockHttpServletRequestBuilder request, String json) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions login(String client, String userEmail, String password) throws Exception {
    MockHttpServletRequestBuilder request =
        post(LOGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(userEmail, password));
    if (client != null) {
      request.header("X-Client-Id", client);
    }
    return anonymous.perform(request);
  }

  @BeforeEach
  void setUp() throws Exception {
    appCode = unique("erp");
    appId =
        read(
            admin(
                post(ADMIN + "/applications"),
                "{\"code\": \"%s\", \"name\": \"ERP\"}".formatted(appCode)),
            "$.id");
    ResultActions client =
        admin(
            post(ADMIN + "/applications/" + appId + "/clients"),
            "{\"name\": \"ERP Web\", \"platform\": \"WEB\", \"accessTokenTtlSeconds\": 600}");
    clientId = read(client, "$.clientId");
    clientUuid = read(client, "$.id");

    String read =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/permissions"),
                "{\"code\": \"invoice:read\"}"),
            "$.id");
    String approve =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/permissions"),
                "{\"code\": \"invoice:approve\"}"),
            "$.id");
    String viewer =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/roles"),
                "{\"code\": \"VIEWER\", \"name\": \"V\", \"permissionIds\": [\"%s\"]}"
                    .formatted(read)),
            "$.id");
    String accountant =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/roles"),
                "{\"code\": \"ACCOUNTANT\", \"name\": \"A\", \"permissionIds\": [\"%s\", \"%s\"]}"
                    .formatted(read, approve)),
            "$.id");

    email = unique("login") + "@example.com";
    userId =
        read(
            admin(
                post(ADMIN + "/users"),
                "{\"email\": \"%s\", \"password\": \"%s\", \"firstName\": \"Lucía\"}"
                    .formatted(email, PASSWORD)),
            "$.id");
    admin(
            put(ADMIN + "/applications/" + appId + "/access/" + userId),
            "{\"roleIds\": [\"%s\", \"%s\"]}".formatted(viewer, accountant))
        .andExpect(status().isCreated());
  }

  @Test
  void issuesAnAccessTokenWithTheRolesAndPermissionsOfTheApplication() throws Exception {
    ResultActions result =
        login(clientId, email.toUpperCase(), PASSWORD)
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(600))
            .andExpect(jsonPath("$.application").value(appCode));

    Jwt jwt = jwtDecoder.decode(read(result, "$.accessToken"));
    assertThat(jwt.getSubject()).isEqualTo(userId);
    assertThat(jwt.getIssuer().toString()).isEqualTo("http://auth.test");
    assertThat(jwt.getAudience()).containsExactly(appCode);
    assertThat(jwt.getClaimAsString("azp")).isEqualTo(clientId);
    assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ACCOUNTANT", "VIEWER");
    assertThat(jwt.getClaimAsStringList("permissions"))
        .containsExactly("invoice:approve", "invoice:read");
    assertThat(jwt.getId()).isNotBlank();
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofSeconds(600));
    assertThat(jwt.getHeaders()).containsEntry("kid", "auth-key-1").containsEntry("alg", "RS256");
  }

  @Test
  void theTokenGivesAccessToTheUserProfileAndApplications() throws Exception {
    String token = read(login(clientId, email, PASSWORD), "$.accessToken");

    anonymous
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.firstName").value("Lucía"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    anonymous
        .perform(
            get("/api/v1/users/me/applications").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value(appCode))
        .andExpect(jsonPath("$[0].roles[0]").value("ACCOUNTANT"));

    // Un token de otra aplicación no sirve para la administración.
    anonymous
        .perform(get(ADMIN + "/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
  }

  @Test
  void invalidCredentialsGetTheSameGenericAnswer() throws Exception {
    login(clientId, email, "wrong passphrase value")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid email or password, or the account is temporarily locked"));
    login(clientId, unique("nobody") + "@example.com", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid email or password, or the account is temporarily locked"));
    login(clientId, "not-an-email", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void rejectsUnknownInactiveAndConfidentialClients() throws Exception {
    login("unknown-client", email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CLIENT"));
    login(null, email, PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    String serviceClient =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/clients"),
                "{\"name\": \"Batch\", \"platform\": \"SERVICE\"}"),
            "$.clientId");
    login(serviceClient, email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CLIENT"));

    admin(
            patch(ADMIN + "/applications/" + appId + "/clients/" + clientUuid),
            "{\"status\": \"INACTIVE\"}")
        .andExpect(status().isOk());
    login(clientId, email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CLIENT"));
  }

  @Test
  void rejectsInactiveApplications() throws Exception {
    admin(patch(ADMIN + "/applications/" + appId), "{\"status\": \"INACTIVE\"}")
        .andExpect(status().isOk());
    login(clientId, email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CLIENT"));
  }

  @Test
  void rejectsLockedUsersAndUsersWithoutEffectiveAccess() throws Exception {
    admin(
            put(ADMIN + "/applications/" + appId + "/access/" + userId),
            "{\"roleIds\": [], \"status\": \"SUSPENDED\"}")
        .andExpect(status().isOk());
    login(clientId, email, PASSWORD)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_NOT_GRANTED"));

    String outsider = unique("outsider") + "@example.com";
    admin(
            post(ADMIN + "/users"),
            "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(outsider, PASSWORD))
        .andExpect(status().isCreated());
    login(clientId, outsider, PASSWORD)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_NOT_GRANTED"));

    admin(patch(ADMIN + "/users/" + userId + "/status"), "{\"status\": \"LOCKED\"}")
        .andExpect(status().isOk());
    login(clientId, email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_INACTIVE"));
  }

  @Test
  void validatesTheRequestBody() throws Exception {
    anonymous
        .perform(
            post(LOGIN)
                .header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
