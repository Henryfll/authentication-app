package com.henryfll.auth.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class SessionApiTest extends IntegrationTest {

  private static final String ADMIN = "/api/v1/admin";
  private static final String AUTH = "/api/v1/auth";
  private static final String PASSWORD = "correct horse battery staple";
  private static final String COOKIE = "refresh_token";

  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JdbcTemplate jdbc;

  private String appId;
  private String webClient;
  private String mobileClient;
  private String origin;
  private String userId;
  private String email;

  private ResultActions admin(MockHttpServletRequestBuilder request, String json) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  @BeforeEach
  void setUp() throws Exception {
    String code = unique("crm");
    origin = "https://" + code + ".example.com";
    appId =
        read(
            admin(
                post(ADMIN + "/applications"),
                "{\"code\": \"%s\", \"name\": \"CRM\"}".formatted(code)),
            "$.id");
    webClient =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/clients"),
                "{\"name\": \"Web\", \"platform\": \"WEB\", \"allowedOrigins\": [\"%s\"]}"
                    .formatted(origin)),
            "$.clientId");
    mobileClient =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/clients"),
                "{\"name\": \"Android\", \"platform\": \"MOBILE\"}"),
            "$.clientId");
    String role =
        read(
            admin(
                post(ADMIN + "/applications/" + appId + "/roles"),
                "{\"code\": \"USER\", \"name\": \"U\"}"),
            "$.id");
    email = unique("session") + "@example.com";
    userId =
        read(
            admin(
                post(ADMIN + "/users"),
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)),
            "$.id");
    admin(
            put(ADMIN + "/applications/" + appId + "/access/" + userId),
            "{\"roleIds\": [\"%s\"]}".formatted(role))
        .andExpect(status().isCreated());
  }

  private MvcResult login(String client, String device) throws Exception {
    return anonymous
        .perform(
            post(AUTH + "/login")
                .header("X-Client-Id", client)
                .header(HttpHeaders.USER_AGENT, "JUnit/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\": \"%s\", \"password\": \"%s\", \"deviceName\": \"%s\"}"
                        .formatted(email, PASSWORD, device)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private ResultActions refreshWeb(String cookieValue) throws Exception {
    return anonymous.perform(
        post(AUTH + "/refresh")
            .header("X-Client-Id", webClient)
            .header(HttpHeaders.ORIGIN, origin)
            .cookie(new Cookie(COOKIE, cookieValue)));
  }

  private ResultActions refreshMobile(String token) throws Exception {
    return anonymous.perform(
        post(AUTH + "/refresh")
            .header("X-Client-Id", mobileClient)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\": \"%s\"}".formatted(token)));
  }

  private static String json(MvcResult result, String path) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path)
        .toString();
  }

  private static String cookieValue(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie(COOKIE);
    assertThat(cookie).as("refresh cookie").isNotNull();
    return cookie.getValue();
  }

  private String sessionStatus(String sessionId) {
    return jdbc.queryForObject(
        "SELECT coalesce(revoked_reason, 'ACTIVE') FROM sessions WHERE id = ?::uuid",
        String.class,
        sessionId);
  }

  @Test
  void webClientsGetTheRefreshTokenInASecureHttpOnlyCookie() throws Exception {
    MvcResult login = login(webClient, "Laptop");

    String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=");
    assertThat(login.getResponse().getContentAsString()).doesNotContain("\"refreshToken\"");

    String sid = json(login, "$.sessionId");
    assertThat(jwtDecoder.decode(json(login, "$.accessToken")).getClaimAsString("sid"))
        .isEqualTo(sid);

    String stored =
        jdbc.queryForObject(
            "SELECT token_hash FROM refresh_tokens WHERE session_id = ?::uuid", String.class, sid);
    assertThat(stored).hasSize(64).isNotEqualTo(cookieValue(login));
  }

  @Test
  void webRefreshRotatesTheTokenAndDetectsReuse() throws Exception {
    MvcResult login = login(webClient, "Laptop");
    String sid = json(login, "$.sessionId");
    String first = cookieValue(login);

    MvcResult refreshed =
        refreshWeb(first)
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly(COOKIE, true))
            .andExpect(jsonPath("$.sessionId").value(sid))
            .andExpect(jsonPath("$.accessToken").exists())
            .andReturn();
    String second = cookieValue(refreshed);
    assertThat(second).isNotEqualTo(first);

    // Reutilizar el primer token: se revoca la sesión entera.
    refreshWeb(first)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    assertThat(sessionStatus(sid)).isEqualTo("REUSE_DETECTED");

    // El token legítimo (el segundo) también deja de servir.
    refreshWeb(second)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void webRefreshRequiresTheCookieAndAnAllowedOrigin() throws Exception {
    String token = cookieValue(login(webClient, "Laptop"));

    anonymous
        .perform(
            post(AUTH + "/refresh")
                .header("X-Client-Id", webClient)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    anonymous
        .perform(
            post(AUTH + "/refresh")
                .header("X-Client-Id", webClient)
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .cookie(new Cookie(COOKIE, token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CLIENT"));

    // El token no se consumió con los intentos rechazados.
    refreshWeb(token).andExpect(status().isOk());
  }

  @Test
  void mobileClientsUseTheBodyAndCannotUseOtherClientsTokens() throws Exception {
    MvcResult login = login(mobileClient, "Pixel 9");
    String token = json(login, "$.refreshToken");
    assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    assertThat(token).hasSize(43);

    String next =
        read(
            refreshMobile(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists()),
            "$.refreshToken");
    assertThat(next).isNotEqualTo(token);

    // Un token del cliente móvil presentado por el cliente web no es válido.
    refreshWeb(next)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    refreshMobile("unknown-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    anonymous
        .perform(post(AUTH + "/refresh").header("X-Client-Id", mobileClient))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredRefreshTokensAreRejected() throws Exception {
    MvcResult login = login(mobileClient, "Pixel 9");
    jdbc.update(
        "UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE session_id = ?::uuid",
        UUID.fromString(json(login, "$.sessionId")));

    refreshMobile(json(login, "$.refreshToken"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logoutRevokesTheCurrentSessionAndLogoutAllEveryOtherOne() throws Exception {
    MvcResult laptop = login(webClient, "Laptop");
    MvcResult phone = login(mobileClient, "Pixel 9");
    MvcResult tablet = login(mobileClient, "Tablet");
    String laptopToken = json(laptop, "$.accessToken");

    anonymous
        .perform(
            get("/api/v1/users/me/sessions").header(HttpHeaders.AUTHORIZATION, bearer(laptopToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.current == true)].deviceName").value("Laptop"))
        .andExpect(jsonPath("$[?(@.deviceName == 'Pixel 9')].platform").value("MOBILE"))
        .andExpect(jsonPath("$[0].userAgent").value("JUnit/5"));

    anonymous
        .perform(post(AUTH + "/logout").header(HttpHeaders.AUTHORIZATION, bearer(laptopToken)))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge(COOKIE, 0));
    assertThat(sessionStatus(json(laptop, "$.sessionId"))).isEqualTo("LOGOUT");
    refreshWeb(cookieValue(laptop)).andExpect(status().isUnauthorized());

    anonymous
        .perform(
            post(AUTH + "/logout-all")
                .header(HttpHeaders.AUTHORIZATION, bearer(json(phone, "$.accessToken"))))
        .andExpect(status().isNoContent());
    assertThat(sessionStatus(json(phone, "$.sessionId"))).isEqualTo("LOGOUT_ALL");
    assertThat(sessionStatus(json(tablet, "$.sessionId"))).isEqualTo("LOGOUT_ALL");
    refreshMobile(json(tablet, "$.refreshToken")).andExpect(status().isUnauthorized());

    anonymous.perform(post(AUTH + "/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  void usersCanCloseASpecificSessionButNotOthersSessions() throws Exception {
    MvcResult laptop = login(webClient, "Laptop");
    MvcResult phone = login(mobileClient, "Pixel 9");
    String laptopToken = json(laptop, "$.accessToken");
    String phoneSid = json(phone, "$.sessionId");

    anonymous
        .perform(
            delete("/api/v1/users/me/sessions/" + phoneSid)
                .header(HttpHeaders.AUTHORIZATION, bearer(laptopToken)))
        .andExpect(status().isNoContent());
    assertThat(sessionStatus(phoneSid)).isEqualTo("LOGOUT");

    anonymous
        .perform(
            delete("/api/v1/users/me/sessions/" + phoneSid)
                .header(HttpHeaders.AUTHORIZATION, bearer(laptopToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

    // El administrador de los tests no puede cerrar la sesión de otro usuario por esta vía.
    mockMvc
        .perform(delete("/api/v1/users/me/sessions/" + json(laptop, "$.sessionId")))
        .andExpect(status().isNotFound());
  }

  @Test
  void withdrawingAccessOrLockingTheUserRevokesTheSessions() throws Exception {
    MvcResult web = login(webClient, "Laptop");
    MvcResult mobile = login(mobileClient, "Pixel 9");

    admin(
            put(ADMIN + "/applications/" + appId + "/access/" + userId),
            "{\"roleIds\": [], \"status\": \"SUSPENDED\"}")
        .andExpect(status().isOk());
    assertThat(sessionStatus(json(web, "$.sessionId"))).isEqualTo("ACCESS_REVOKED");
    assertThat(sessionStatus(json(mobile, "$.sessionId"))).isEqualTo("ACCESS_REVOKED");
    refreshMobile(json(mobile, "$.refreshToken")).andExpect(status().isUnauthorized());

    admin(put(ADMIN + "/applications/" + appId + "/access/" + userId), "{\"roleIds\": []}")
        .andExpect(status().isOk());
    MvcResult again = login(mobileClient, "Pixel 9");
    admin(patch(ADMIN + "/users/" + userId + "/status"), "{\"status\": \"LOCKED\"}")
        .andExpect(status().isOk());
    assertThat(sessionStatus(json(again, "$.sessionId"))).isEqualTo("USER_INACTIVE");

    admin(patch(ADMIN + "/users/" + userId + "/status"), "{\"status\": \"ACTIVE\"}");
    MvcResult last = login(mobileClient, "Pixel 9");
    mockMvc
        .perform(delete(ADMIN + "/applications/" + appId + "/access/" + userId))
        .andExpect(status().isNoContent());
    assertThat(sessionStatus(json(last, "$.sessionId"))).isEqualTo("ACCESS_REVOKED");
  }

  @Test
  void refreshFailsAndRevokesTheSessionWhenAccessIsNoLongerEffective() throws Exception {
    MvcResult mobile = login(mobileClient, "Pixel 9");
    String sid = json(mobile, "$.sessionId");
    // Acceso caducado sin pasar por la API (no hay evento): lo detecta el propio refresh.
    jdbc.update(
        "UPDATE user_application_access SET valid_until = now() - interval '1 second',"
            + " valid_from = now() - interval '1 day' WHERE user_id = ?::uuid",
        UUID.fromString(userId));

    refreshMobile(json(mobile, "$.refreshToken"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_NOT_GRANTED"));
    assertThat(sessionStatus(sid)).isEqualTo("ACCESS_REVOKED");
  }
}
