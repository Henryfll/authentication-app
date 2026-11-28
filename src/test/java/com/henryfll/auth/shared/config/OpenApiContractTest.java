package com.henryfll.auth.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.henryfll.auth.IntegrationTest;
import com.henryfll.auth.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato OpenAPI es lo que usa el frontend para generar su cliente: estos tests fijan que los
 * estados, los errores, los campos obligatorios y la seguridad que declara coinciden con lo que la
 * API hace de verdad.
 */
class OpenApiContractTest extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final String PROBLEM_REF = "#/components/schemas/Problem";

  /** Operaciones cuyo éxito NO es un único {@code 200}. El resto declara solo {@code 200}. */
  private static final Map<String, Set<String>> SUCCESS_STATUSES =
      Map.ofEntries(
          Map.entry("POST /api/v1/admin/users", Set.of("201")),
          Map.entry("POST /api/v1/admin/applications", Set.of("201")),
          Map.entry("POST /api/v1/admin/applications/{appId}/clients", Set.of("201")),
          Map.entry("POST /api/v1/admin/applications/{appId}/permissions", Set.of("201")),
          Map.entry("POST /api/v1/admin/applications/{appId}/roles", Set.of("201")),
          Map.entry("PUT /api/v1/admin/applications/{appId}/access/{userId}", Set.of("200", "201")),
          Map.entry("POST /api/v1/auth/logout", Set.of("204")),
          Map.entry("POST /api/v1/auth/logout-all", Set.of("204")),
          Map.entry("DELETE /api/v1/admin/applications/{appId}/access/{userId}", Set.of("204")),
          Map.entry("PUT /api/v1/users/me/password", Set.of("204")),
          Map.entry("PUT /api/v1/admin/users/{userId}/password", Set.of("204")),
          Map.entry("DELETE /api/v1/users/me/sessions/{sessionId}", Set.of("204")));

  /**
   * Identificadores de operación. Son parte del contrato: el cliente generado nombra sus funciones
   * con ellos, así que cambiar uno rompe al frontend y debe ser una decisión consciente.
   */
  private static final Map<String, String> OPERATION_IDS =
      Map.ofEntries(
          Map.entry("POST /api/v1/auth/login", "login"),
          Map.entry("POST /api/v1/auth/refresh", "refreshTokens"),
          Map.entry("POST /api/v1/auth/logout", "logout"),
          Map.entry("POST /api/v1/auth/logout-all", "logoutAll"),
          Map.entry("GET /.well-known/jwks.json", "getJwks"),
          Map.entry("GET /api/v1/users/me", "getMe"),
          Map.entry("PUT /api/v1/users/me/password", "changeMyPassword"),
          Map.entry("GET /api/v1/users/me/applications", "listMyApplications"),
          Map.entry("GET /api/v1/users/me/sessions", "listMySessions"),
          Map.entry("DELETE /api/v1/users/me/sessions/{sessionId}", "revokeMySession"),
          Map.entry("GET /api/v1/admin/users", "listUsers"),
          Map.entry("POST /api/v1/admin/users", "createUser"),
          Map.entry("GET /api/v1/admin/users/{userId}", "getUser"),
          Map.entry("PATCH /api/v1/admin/users/{userId}/status", "changeUserStatus"),
          Map.entry("PUT /api/v1/admin/users/{userId}/password", "resetUserPassword"),
          Map.entry("GET /api/v1/admin/users/{userId}/access", "listUserAccess"),
          Map.entry("GET /api/v1/admin/applications", "listApplications"),
          Map.entry("POST /api/v1/admin/applications", "createApplication"),
          Map.entry("GET /api/v1/admin/applications/{appId}", "getApplication"),
          Map.entry("PATCH /api/v1/admin/applications/{appId}", "updateApplication"),
          Map.entry("GET /api/v1/admin/applications/{appId}/clients", "listClients"),
          Map.entry("POST /api/v1/admin/applications/{appId}/clients", "createClient"),
          Map.entry("PATCH /api/v1/admin/applications/{appId}/clients/{clientId}", "updateClient"),
          Map.entry(
              "POST /api/v1/admin/applications/{appId}/clients/{clientId}/secret",
              "rotateClientSecret"),
          Map.entry("GET /api/v1/admin/applications/{appId}/permissions", "listPermissions"),
          Map.entry("POST /api/v1/admin/applications/{appId}/permissions", "createPermission"),
          Map.entry("GET /api/v1/admin/applications/{appId}/roles", "listRoles"),
          Map.entry("POST /api/v1/admin/applications/{appId}/roles", "createRole"),
          Map.entry("GET /api/v1/admin/applications/{appId}/roles/{roleId}", "getRole"),
          Map.entry(
              "PUT /api/v1/admin/applications/{appId}/roles/{roleId}/permissions",
              "replaceRolePermissions"),
          Map.entry("PUT /api/v1/admin/applications/{appId}/access/{userId}", "grantAccess"),
          Map.entry("GET /api/v1/admin/applications/{appId}/access/{userId}", "getAccess"),
          Map.entry("DELETE /api/v1/admin/applications/{appId}/access/{userId}", "revokeAccess"),
          Map.entry("GET /api/v1/admin/applications/{appId}/access", "listApplicationAccess"),
          Map.entry("GET /api/v1/admin/audit-events", "searchAuditEvents"));

  /** Operaciones públicas: no exigen token. */
  private static final Set<String> PUBLIC_OPERATIONS =
      Set.of("POST /api/v1/auth/login", "POST /api/v1/auth/refresh", "GET /.well-known/jwks.json");

  /** Campos que el servidor omite cuando no tienen valor (el resto se envía, aunque sea null). */
  private static final Map<String, Set<String>> OMITTED_WHEN_EMPTY =
      Map.of("TokenResponse", Set.of("refreshToken"), "ClientResponse", Set.of("clientSecret"));

  private static Map<String, Object> spec;

  @BeforeEach
  void loadSpec() throws Exception {
    if (spec == null) {
      MvcResult result = anonymous.perform(get("/v3/api-docs")).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      spec = JSON.readValue(result.getResponse().getContentAsString(), MAP);
    }
  }

  // --- Estados de éxito ---------------------------------------------------------------------

  @Test
  void everyOperationDeclaresTheSuccessStatusTheApiReallyReturns() {
    operations()
        .forEach((key, op) -> {
          Set<String> expected = SUCCESS_STATUSES.getOrDefault(key, Set.of("200"));
          assertThat(successStatuses(op)).as("estados de éxito de %s", key).isEqualTo(expected);
        });
  }

  @Test
  void noContentResponsesHaveNoBodyAndTheRestHaveOne() {
    operations()
        .forEach((key, op) -> {
          Map<String, Object> responses = map(op.get("responses"));
          for (String status : successStatuses(op)) {
            Map<String, Object> response = map(responses.get(status));
            if (status.equals("204")) {
              assertThat(response).as("%s %s", key, status).doesNotContainKey("content");
            } else {
              assertThat(response).as("%s %s", key, status).containsKey("content");
            }
          }
        });
  }

  @Test
  void createdResponsesExposeTheLocationHeader() {
    SUCCESS_STATUSES.forEach((key, statuses) -> {
      if (statuses.contains("201")) {
        Map<String, Object> created = map(map(operations().get(key).get("responses")).get("201"));
        assertThat(map(created.get("headers"))).as(key).containsKey("Location");
      }
    });
  }

  // --- Seguridad ------------------------------------------------------------------------------

  @Test
  void onlyLoginRefreshAndJwksAreDeclaredPublic() {
    Set<String> publicOps =
        operations().entrySet().stream()
            .filter(e -> e.getValue().get("security") instanceof List<?> l && l.isEmpty())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    assertThat(publicOps).isEqualTo(PUBLIC_OPERATIONS);
    assertThat(list(spec.get("security"))).as("seguridad global").isNotEmpty();
  }

  // --- Errores --------------------------------------------------------------------------------

  @Test
  void theProblemSchemaMatchesTheErrorCodes() {
    Map<String, Object> problem = schemas().get("Problem") instanceof Map<?, ?> m ? cast(m) : null;
    assertThat(problem).as("esquema Problem").isNotNull();
    assertThat(list(problem.get("required")))
        .containsExactlyInAnyOrder("code", "status", "title", "timestamp");
    Map<String, Object> code = map(map(problem.get("properties")).get("code"));
    assertThat(list(code.get("enum")))
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
    assertThat(schemas()).containsKey("ProblemFieldError");
  }

  @Test
  void everyOperationDocumentsItsCommonErrors() {
    operations()
        .forEach((key, op) -> {
          Set<String> statuses = map(op.get("responses")).keySet();
          assertThat(statuses).as("%s: error 500", key).contains("500");
          if (!PUBLIC_OPERATIONS.contains(key)) {
            assertThat(statuses).as("%s: error 401", key).contains("401");
          }
          if (key.contains(" /api/v1/admin/")) {
            assertThat(statuses).as("%s: error 403", key).contains("403");
          }
          boolean hasInput = op.containsKey("requestBody") || !list(op.get("parameters")).isEmpty();
          if (hasInput) {
            assertThat(statuses).as("%s: error 400", key).contains("400");
          }
        });
  }

  @Test
  void errorResponsesUseProblemJsonAndListOnlyRealCodesOfThatStatus() {
    operations()
        .forEach((key, op) ->
            map(op.get("responses"))
                .forEach((status, response) -> {
                  if (status.startsWith("2")) {
                    return;
                  }
                  Map<String, Object> r = map(response);
                  Map<String, Object> content = map(r.get("content"));
                  assertThat(content.keySet())
                      .as("%s %s", key, status)
                      .containsExactly("application/problem+json");
                  assertThat(
                          map(map(content.get("application/problem+json")).get("schema"))
                              .get("$ref"))
                      .isEqualTo(PROBLEM_REF);
                  for (String code : codesIn((String) r.get("description"))) {
                    assertThat(ErrorCode.valueOf(code).type().httpStatus())
                        .as("%s %s: %s", key, status, code)
                        .isEqualTo(Integer.parseInt(status));
                  }
                }));
  }

  @Test
  void specificErrorsAreDocumentedWhereTheyHappen() {
    assertThat(codes("POST /api/v1/auth/login", "401"))
        .contains("AUTH_INVALID_CLIENT", "AUTH_INVALID_CREDENTIALS", "AUTH_ACCOUNT_INACTIVE");
    assertThat(codes("POST /api/v1/auth/login", "403")).contains("ACCESS_NOT_GRANTED");
    assertThat(codes("POST /api/v1/auth/login", "429")).containsExactly("TOO_MANY_REQUESTS");
    assertThat(codes("POST /api/v1/auth/refresh", "401"))
        .contains("INVALID_REFRESH_TOKEN", "REFRESH_TOKEN_REUSED");
    assertThat(codes("POST /api/v1/admin/users", "409")).contains("USER_EMAIL_ALREADY_EXISTS");
    assertThat(codes("PUT /api/v1/users/me/password", "400"))
        .contains("INVALID_CURRENT_PASSWORD", "PASSWORD_POLICY_VIOLATION");
    assertThat(codes("DELETE /api/v1/users/me/sessions/{sessionId}", "404"))
        .containsExactly("SESSION_NOT_FOUND");
    assertThat(codes("PATCH /api/v1/admin/applications/{appId}", "422"))
        .containsExactly("BUSINESS_RULE_VIOLATION");
  }

  // --- Campos obligatorios ------------------------------------------------------------------

  @Test
  void responseFieldsAreRequiredUnlessTheServerOmitsThem() {
    schemas()
        .forEach((name, schema) -> {
          if (name.endsWith("Request") || name.startsWith("Problem")) {
            return;
          }
          Map<String, Object> s = map(schema);
          Set<String> all = map(s.get("properties")).keySet();
          Set<String> expected = new HashSet<>(all);
          expected.removeAll(OMITTED_WHEN_EMPTY.getOrDefault(name, Set.of()));
          assertThat(new HashSet<>(list(s.get("required"))))
              .as("campos obligatorios de %s", name)
              .isEqualTo(expected);
        });
  }

  @Test
  void nullableFieldsAreDeclaredNullable() {
    assertThat(propertyTypes("UserResponse", "firstName")).contains("null");
    assertThat(propertyTypes("UserResponse", "id")).doesNotContain("null");
    assertThat(propertyTypes("AccessResponse", "validUntil")).contains("null");
    assertThat(propertyTypes("AuditEventResponse", "actorUserId")).contains("null");
    assertThat(propertyTypes("ApplicationResponse", "description")).contains("null");
  }

  // --- El contrato frente a la realidad -----------------------------------------------------

  @Test
  void realErrorBodiesFitTheProblemSchema() throws Exception {
    Map<String, Object> problem = map(schemas().get("Problem"));
    Set<String> declared = map(problem.get("properties")).keySet();
    Set<String> required = new HashSet<>(list(problem.get("required")));

    List<MvcResult> errors = new ArrayList<>();
    // 404 de negocio
    errors.add(
        mockMvc
            .perform(get("/api/v1/admin/users/00000000-0000-0000-0000-00000000dead"))
            .andReturn());
    // 400 de validación del cuerpo
    errors.add(
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"no-es-un-correo\", \"password\": \"x\"}"))
            .andReturn());
    // 400 por identificador mal formado
    errors.add(mockMvc.perform(get("/api/v1/admin/users/no-es-un-uuid")).andReturn());
    // 401 sin token (lo genera el filtro de seguridad)
    errors.add(anonymous.perform(get("/api/v1/admin/users")).andReturn());
    // 403 con un token sin permisos
    String sinPermisos = token(testAdminId(), AUTH_ADMIN, List.of(), List.of());
    errors.add(
        anonymous
            .perform(
                get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(sinPermisos)))
            .andReturn());
    // 409 por duplicado
    String email = unique("dup") + "@example.com";
    String body =
        "{\"email\": \"%s\", \"password\": \"correct horse battery staple\"}".formatted(email);
    mockMvc.perform(
        post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content(body));
    errors.add(
        mockMvc
            .perform(
                post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn());

    Set<Integer> seen = new TreeSet<>();
    for (MvcResult result : errors) {
      int status = result.getResponse().getStatus();
      seen.add(status);
      Map<String, Object> error = JSON.readValue(result.getResponse().getContentAsString(), MAP);
      assertThat(error.keySet()).as("campos del error %s", status).isSubsetOf(declared);
      assertThat(error.keySet()).as("obligatorios del error %s", status).containsAll(required);
      assertThat(error.get("status")).isEqualTo(status);
      assertThat(ErrorCode.valueOf((String) error.get("code")).type().httpStatus())
          .isEqualTo(status);
    }
    assertThat(seen).containsExactly(400, 401, 403, 404, 409);
  }

  @Test
  void realResponsesMatchTheDeclaredRequiredAndNullableFields() throws Exception {
    // Usuario sin nombre: los campos nulos se envían como null y todos los obligatorios están.
    String email = unique("sinnombre") + "@example.com";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\": \"%s\", \"password\": \"correct horse battery staple\"}"
                            .formatted(email)))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    Map<String, Object> user = JSON.readValue(created.getResponse().getContentAsString(), MAP);
    assertThat(user.keySet()).containsAll(list(map(schemas().get("UserResponse")).get("required")));
    assertThat(user).containsEntry("firstName", null).containsEntry("lastName", null);

    // Login de un cliente web: sin refreshToken en el cuerpo, con todo lo demás.
    String code = unique("ct");
    String appId =
        read(
            mockMvc.perform(
                post("/api/v1/admin/applications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\": \"%s\", \"name\": \"Contrato\"}".formatted(code))),
            "$.id");
    String clientId =
        read(
            mockMvc.perform(
                post("/api/v1/admin/applications/" + appId + "/clients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Web\", \"platform\": \"WEB\"}")),
            "$.clientId");
    mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/admin/applications/" + appId + "/access/" + user.get("id"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roleIds\": []}"));
    MvcResult login =
        anonymous
            .perform(
                post("/api/v1/auth/login")
                    .header("X-Client-Id", clientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\": \"%s\", \"password\": \"correct horse battery staple\"}"
                            .formatted(email)))
            .andReturn();
    assertThat(login.getResponse().getStatus()).isEqualTo(200);
    Map<String, Object> tokens = JSON.readValue(login.getResponse().getContentAsString(), MAP);
    Set<String> requiredTokenFields =
        new HashSet<>(list(map(schemas().get("TokenResponse")).get("required")));
    assertThat(tokens.keySet()).containsAll(requiredTokenFields).doesNotContain("refreshToken");
  }

  // --- Identificadores, tipos de contenido y metadatos ----------------------------------------

  @Test
  void operationIdsAreExplicitUniqueAndStable() {
    Map<String, Map<String, Object>> operations = operations();
    List<String> ids = new ArrayList<>();
    operations.forEach((key, op) -> {
      String id = (String) op.get("operationId");
      ids.add(id);
      assertThat(id).as("operationId de %s", key).matches("[a-z][A-Za-z]+");
      if (OPERATION_IDS.containsKey(key)) {
        assertThat(id).as("operationId de %s", key).isEqualTo(OPERATION_IDS.get(key));
      }
    });
    assertThat(ids).as("identificadores repetidos").doesNotHaveDuplicates();
    assertThat(operations.keySet())
        .as("operaciones sin identificador fijado en la tabla de este test")
        .containsAll(OPERATION_IDS.keySet());
  }

  @Test
  void responsesAreDeclaredAsJson() {
    operations()
        .forEach((key, op) ->
            map(op.get("responses"))
                .forEach((status, response) -> {
                  Set<String> types = map(map(response).get("content")).keySet();
                  if (status.startsWith("2") && !types.isEmpty()) {
                    assertThat(types)
                        .as("tipos de contenido de %s %s", key, status)
                        .containsExactly("application/json");
                  }
                }));
  }

  @Test
  void theContractCarriesTheBuildVersionAndDescribesTheServer() {
    Map<String, Object> info = map(spec.get("info"));
    assertThat((String) info.get("version")).matches("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?");
    for (Object server : (List<?>) spec.get("servers")) {
      assertThat(map(server).get("description")).isEqualTo(OpenApiConfig.SERVER_DESCRIPTION);
    }
  }

  @Test
  void theYamlVariantIsPublicLikeTheJson() throws Exception {
    MvcResult result = anonymous.perform(get("/v3/api-docs.yaml")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).startsWith("openapi: 3.1");
  }

  // --- Utilidades ---------------------------------------------------------------------------

  /** "MÉTODO ruta" → operación. */
  private static Map<String, Map<String, Object>> operations() {
    Map<String, Map<String, Object>> operations = new java.util.LinkedHashMap<>();
    map(spec.get("paths"))
        .forEach((path, item) ->
            map(item)
                .forEach((method, op) -> {
                  if (Set.of("get", "post", "put", "patch", "delete").contains(method)) {
                    operations.put(method.toUpperCase() + " " + path, map(op));
                  }
                }));
    return operations;
  }

  private static Set<String> successStatuses(Map<String, Object> op) {
    return map(op.get("responses")).keySet().stream()
        .filter(s -> s.startsWith("2"))
        .collect(Collectors.toSet());
  }

  private static Map<String, Object> schemas() {
    return map(map(spec.get("components")).get("schemas"));
  }

  private static Set<String> codes(String operation, String status) {
    Map<String, Object> response =
        map(map(operations().get(operation).get("responses")).get(status));
    assertThat(response).as("%s %s", operation, status).isNotEmpty();
    return codesIn((String) response.get("description"));
  }

  private static Set<String> codesIn(String description) {
    String list = description.substring(description.indexOf(':') + 1);
    return Arrays.stream(list.split(","))
        .map(String::strip)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static List<String> propertyTypes(String schema, String property) {
    Object type = map(map(map(schemas().get(schema)).get("properties")).get(property)).get("type");
    return type instanceof List<?> l
        ? l.stream().map(String::valueOf).toList()
        : List.of(String.valueOf(type));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> value) {
    return (Map<String, Object>) value;
  }

  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? cast(m) : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static List<String> list(Object value) {
    return value instanceof List<?> l ? (List<String>) l : List.of();
  }
}
