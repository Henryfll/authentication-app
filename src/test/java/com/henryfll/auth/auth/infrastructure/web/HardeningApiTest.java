package com.henryfll.auth.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class HardeningApiTest extends IntegrationTest {

  private static final String ADMIN = "/api/v1/admin";
  private static final String PASSWORD = "correct horse battery staple";
  private static final String NEW_PASSWORD = "a brand new passphrase";

  @Autowired private JdbcTemplate jdbc;

  private String appId;
  private String clientId;
  private String userId;
  private String email;

  private ResultActions admin(MockHttpServletRequestBuilder request, String json) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions login(String userEmail, String password) throws Exception {
    return anonymous.perform(
        post("/api/v1/auth/login")
            .header("X-Client-Id", clientId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(userEmail, password)));
  }

  private static String json(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
  }

  private String sessionStatus(String sessionId) {
    return jdbc.queryForObject(
        "SELECT coalesce(revoked_reason, 'ACTIVE') FROM sessions WHERE id = ?::uuid",
        String.class,
        sessionId);
  }

  @BeforeEach
  void setUp() throws Exception {
    String code = unique("hr");
    appId =
        read(
            admin(
                post(ADMIN + "/applications"),
                "{\"code\": \"%s\", \"name\": \"HR\"}".formatted(code)),
            "$.id");
    clientId =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/clients"),
                "{\"name\": \"Android\", \"platform\": \"MOBILE\"}"),
            "$.clientId");
    email = unique("hardening") + "@example.com";
    userId =
        read(
            admin(
                post(ADMIN + "/users"),
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)),
            "$.id");
    admin(put(ADMIN + "/applications/" + appId + "/access/" + userId), "{\"roleIds\": []}")
        .andExpect(status().isCreated());
  }

  @Test
  void tooManyLoginAttemptsForTheSameEmailGet429WithRetryAfter() throws Exception {
    String target = unique("ghost") + "@example.com";
    for (int i = 0; i < 10; i++) {
      login(target, "whatever passphrase").andExpect(status().isUnauthorized());
    }
    login(target, "whatever passphrase")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

    // Otros emails no se ven afectados.
    login(email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void theAccountIsTemporarilyLockedAfterRepeatedFailures() throws Exception {
    for (int i = 0; i < 5; i++) {
      login(email, "wrong passphrase " + i)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT locked_until > now() FROM users WHERE id = ?::uuid", Boolean.class, userId))
        .isTrue();

    // Bloqueada: ni siquiera la contraseña correcta funciona, y la respuesta es la misma.
    login(email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

    mockMvc
        .perform(
            get(ADMIN + "/audit-events")
                .param("userId", userId)
                .param("type", "ACCOUNT_TEMPORARILY_LOCKED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].details.failedAttempts").value(5));

    // Un administrador desbloquea la cuenta activándola.
    admin(patch(ADMIN + "/users/" + userId + "/status"), "{\"status\": \"ACTIVE\"}")
        .andExpect(status().isOk());
    login(email, PASSWORD).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE id = ?::uuid",
                Integer.class,
                userId))
        .isZero();
    mockMvc
        .perform(
            get(ADMIN + "/audit-events").param("userId", userId).param("type", "ACCOUNT_UNLOCKED"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].actorUserId").value(testAdminId().toString()));
  }

  @Test
  void usersChangeTheirPasswordAndOtherSessionsAreClosed() throws Exception {
    MvcResult current = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
    MvcResult other = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
    String token = json(current, "$.accessToken");

    anonymous
        .perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\": \"not the password\", \"newPassword\": \"%s\"}"
                        .formatted(NEW_PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    anonymous
        .perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                        .formatted(PASSWORD, PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    anonymous
        .perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\": \"%s\", \"newPassword\": \"short\"}"
                        .formatted(PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));

    anonymous
        .perform(
            put("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                        .formatted(PASSWORD, NEW_PASSWORD)))
        .andExpect(status().isNoContent());

    assertThat(sessionStatus(json(current, "$.sessionId"))).isEqualTo("ACTIVE");
    assertThat(sessionStatus(json(other, "$.sessionId"))).isEqualTo("PASSWORD_CHANGED");
    login(email, PASSWORD).andExpect(status().isUnauthorized());
    login(email, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void adminsResetPasswordsClosingAllSessionsAndLiftingTheLock() throws Exception {
    String session = json(login(email, PASSWORD).andReturn(), "$.sessionId");
    for (int i = 0; i < 5; i++) {
      login(email, "wrong passphrase " + i);
    }

    admin(
            put(ADMIN + "/users/" + userId + "/password"),
            "{\"newPassword\": \"%s\"}".formatted(NEW_PASSWORD))
        .andExpect(status().isNoContent());

    assertThat(sessionStatus(session)).isEqualTo("PASSWORD_CHANGED");
    login(email, NEW_PASSWORD).andExpect(status().isOk());

    admin(put(ADMIN + "/users/" + userId + "/password"), "{\"newPassword\": \"weak\"}")
        .andExpect(status().isBadRequest());
    admin(
            put(ADMIN + "/users/00000000-0000-0000-0000-00000000dead/password"),
            "{\"newPassword\": \"%s\"}".formatted(NEW_PASSWORD))
        .andExpect(status().isNotFound());
  }

  @Test
  void securityEventsAreAuditedWithActorAndDetails() throws Exception {
    login(email, "wrong passphrase").andExpect(status().isUnauthorized());
    login(email, PASSWORD).andExpect(status().isOk());

    ResultActions events =
        mockMvc
            .perform(get(ADMIN + "/audit-events").param("userId", userId).param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].type").value(hasItem("USER_CREATED")))
            .andExpect(jsonPath("$.content[*].type").value(hasItem("ACCESS_GRANTED")))
            .andExpect(jsonPath("$.content[*].type").value(hasItem("LOGIN_FAILED")))
            .andExpect(jsonPath("$.content[*].type").value(hasItem("LOGIN_SUCCEEDED")))
            .andExpect(jsonPath("$.content[0].type").value("LOGIN_SUCCEEDED"));

    String body = events.andReturn().getResponse().getContentAsString();
    List<String> failedReasons =
        JsonPath.read(body, "$.content[?(@.type == 'LOGIN_FAILED')].details.reason");
    assertThat(failedReasons).containsExactly("INVALID_PASSWORD");
    List<String> grantActors =
        JsonPath.read(body, "$.content[?(@.type == 'ACCESS_GRANTED')].actorUserId");
    assertThat(grantActors).containsExactly(testAdminId().toString());
    assertThat(body).doesNotContain(PASSWORD).doesNotContain("wrong passphrase");

    mockMvc
        .perform(
            get(ADMIN + "/audit-events")
                .param("applicationId", appId)
                .param("type", "CLIENT_CREATED"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].details.platform").value("MOBILE"));
  }

  @Test
  void auditQueriesRequireAuditReadAndValidateTheRange() throws Exception {
    String auditor =
        token(UUID.randomUUID(), AUTH_ADMIN, List.of("AUDITOR"), List.of("audit:read"));
    String manager =
        token(UUID.randomUUID(), AUTH_ADMIN, List.of("ACCESS_MANAGER"), List.of("users:read"));

    anonymous
        .perform(get(ADMIN + "/audit-events").header(HttpHeaders.AUTHORIZATION, bearer(auditor)))
        .andExpect(status().isOk());
    anonymous
        .perform(get(ADMIN + "/audit-events").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get(ADMIN + "/audit-events")
                .param("from", "2026-02-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("to"));
    mockMvc
        .perform(get(ADMIN + "/audit-events").param("from", "2999-01-01T00:00:00Z"))
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(get(ADMIN + "/audit-events").param("type", "LOGIN_SUCCEEDED").param("size", "100"))
        .andExpect(jsonPath("$.content[*].type").value(not(hasItem("LOGIN_FAILED"))));
  }
}
