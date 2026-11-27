package com.henryfll.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.henryfll.auth.auth.application.port.out.TokenIssuer;
import com.henryfll.auth.auth.domain.AccessTokenClaims;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase.CreateUserCommand;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de los tests de integración: contexto completo + PostgreSQL real (Testcontainers). El
 * contexto y el contenedor se comparten entre clases, así que cada test usa datos únicos.
 *
 * <p>{@link #mockMvc} envía por defecto un access token de {@code auth-admin} con todos sus
 * permisos (firmado con la clave real de la aplicación). {@link #anonymous} no envía ninguno.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

  public static final String AUTH_ADMIN = "auth-admin";
  public static final List<String> ALL_ADMIN_PERMISSIONS =
      List.of(
          "access:read",
          "access:write",
          "applications:read",
          "applications:write",
          "audit:read",
          "users:read",
          "users:write");

  private static final String TEST_ADMIN_EMAIL = "it-admin@example.com";
  private static volatile UUID testAdminId;

  /** Peticiones autenticadas como administrador (SUPER_ADMIN de auth-admin). */
  protected MockMvc mockMvc;

  /** Peticiones sin token. */
  @Autowired protected MockMvc anonymous;

  @Autowired private WebApplicationContext context;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  @Autowired protected TokenIssuer tokenIssuer;
  @Autowired private FindUsersUseCase findUsers;
  @Autowired private CreateUserUseCase createUser;

  @BeforeEach
  void setUpAdminMockMvc() {
    String token = token(testAdminId(), AUTH_ADMIN, List.of("SUPER_ADMIN"), ALL_ADMIN_PERMISSIONS);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(springSecurityFilterChain)
            .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .build();
  }

  /** Usuario real (existe en la base de datos) usado como sujeto del token de administrador. */
  protected UUID testAdminId() {
    if (testAdminId == null) {
      testAdminId =
          findUsers
              .findByEmail(TEST_ADMIN_EMAIL)
              .orElseGet(() ->
                  createUser.create(
                      new CreateUserCommand(
                          TEST_ADMIN_EMAIL, "integration test admin passphrase", null, null)))
              .id()
              .value();
    }
    return testAdminId;
  }

  /** Firma un access token arbitrario con la clave de la aplicación. */
  protected String token(
      UUID subject, String audience, List<String> roles, List<String> permissions) {
    Instant now = Instant.now();
    return tokenIssuer.issue(
        new AccessTokenClaims(
            subject,
            null,
            audience,
            audience + "-test",
            roles,
            permissions,
            now,
            now.plus(Duration.ofHours(1)),
            UUID.randomUUID(),
            null));
  }

  protected static String bearer(String token) {
    return "Bearer " + token;
  }

  protected static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  protected static String read(ResultActions result, String path) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path).toString();
  }
}
