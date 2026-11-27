package com.henryfll.auth.apps.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminApplicationApiTest extends IntegrationTest {

  private static final String APPS = "/api/v1/admin/applications";
  private static final String AUTH_ADMIN_ID = "00000000-0000-0000-0000-000000000001";

  @Autowired private JdbcTemplate jdbc;

  private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private String createApp(String code) throws Exception {
    return read(
        send(post(APPS), "{\"code\": \"%s\", \"name\": \"App %s\"}".formatted(code, code))
            .andExpect(status().isCreated()),
        "$.id");
  }

  @Test
  void seedsTheAuthAdminApplication() throws Exception {
    mockMvc
        .perform(get(APPS + "/" + AUTH_ADMIN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("auth-admin"));

    mockMvc
        .perform(get(APPS + "/" + AUTH_ADMIN_ID + "/roles"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].code")
                .value(
                    containsInAnyOrder("ACCESS_MANAGER", "APP_MANAGER", "AUDITOR", "SUPER_ADMIN")))
        .andExpect(jsonPath("$[?(@.code == 'SUPER_ADMIN')].permissions.length()").value(7));

    mockMvc
        .perform(get(APPS + "/" + AUTH_ADMIN_ID + "/clients"))
        .andExpect(jsonPath("$[0].clientId").value("auth-admin-web"))
        .andExpect(jsonPath("$[0].platform").value("WEB"));
  }

  @Test
  void createsListsAndUpdatesApplications() throws Exception {
    String code = unique("erp");
    String id = createApp(code.toUpperCase());

    mockMvc
        .perform(get(APPS + "/" + id))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.selfRegistration").value(false));

    send(post(APPS), "{\"code\": \"%s\", \"name\": \"Otra\"}".formatted(code))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICATION_CODE_ALREADY_EXISTS"));

    send(patch(APPS + "/" + id), "{\"name\": \"ERP Corporativo\", \"status\": \"INACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ERP Corporativo"))
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.code").value(code));

    mockMvc
        .perform(get(APPS).param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].code", hasItem(code)));
  }

  @Test
  void selfRegistrationNeedsADefaultRoleFromTheSameApplication() throws Exception {
    String appId = createApp(unique("crm"));
    String otherAppId = createApp(unique("crm"));

    send(patch(APPS + "/" + appId), "{\"selfRegistration\": true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    String foreignRole =
        read(
            send(post(APPS + "/" + otherAppId + "/roles"), "{\"code\": \"USER\", \"name\": \"U\"}"),
            "$.id");
    send(patch(APPS + "/" + appId), "{\"defaultRoleId\": \"%s\"}".formatted(foreignRole))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

    String role =
        read(
            send(post(APPS + "/" + appId + "/roles"), "{\"code\": \"USER\", \"name\": \"U\"}"),
            "$.id");
    send(
            patch(APPS + "/" + appId),
            "{\"defaultRoleId\": \"%s\", \"selfRegistration\": true}".formatted(role))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selfRegistration").value(true))
        .andExpect(jsonPath("$.defaultRoleId").value(role));
  }

  @Test
  void managesWebMobileAndServiceClients() throws Exception {
    String code = unique("portal");
    String appId = createApp(code);
    String clients = APPS + "/" + appId + "/clients";

    send(
            post(clients),
            """
            {"name": "Portal Web", "platform": "WEB",
             "allowedOrigins": ["https://Portal.Example.com", "http://localhost:4200"]}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clientId").value(code + "-web"))
        .andExpect(jsonPath("$.confidential").value(false))
        .andExpect(jsonPath("$.allowedOrigins[0]").value("https://portal.example.com"))
        .andExpect(jsonPath("$.accessTokenTtlSeconds").value(900))
        .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(604800))
        .andExpect(jsonPath("$.clientSecret").doesNotExist());

    String mobileId =
        read(
            send(
                    post(clients),
                    "{\"clientId\": \"%s-android\", \"name\": \"Portal Android\", \"platform\": \"MOBILE\"}"
                        .formatted(code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(2592000)),
            "$.id");

    send(
            post(clients),
            "{\"clientId\": \"%s-android\", \"name\": \"Dup\", \"platform\": \"MOBILE\"}"
                .formatted(code))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CLIENT_ID_ALREADY_EXISTS"));

    send(
            post(clients),
            "{\"name\": \"App\", \"platform\": \"MOBILE\", \"allowedOrigins\": [\"https://x.com\"]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_CLIENT_CONFIGURATION"));

    ResultActions service =
        send(post(clients), "{\"name\": \"Portal Batch\", \"platform\": \"SERVICE\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.confidential").value(true));
    String serviceId = read(service, "$.id");
    String secret = read(service, "$.clientSecret");
    assertThat(secret).hasSizeGreaterThanOrEqualTo(43);
    String storedHash =
        jdbc.queryForObject(
            "SELECT client_secret_hash FROM application_clients WHERE id = ?::uuid",
            String.class,
            serviceId);
    assertThat(storedHash).startsWith("{argon2}").doesNotContain(secret);

    String rotated =
        read(
            mockMvc.perform(post(clients + "/" + serviceId + "/secret")).andExpect(status().isOk()),
            "$.clientSecret");
    assertThat(rotated).isNotEqualTo(secret);

    mockMvc
        .perform(post(clients + "/" + mobileId + "/secret"))
        .andExpect(status().isUnprocessableContent());

    send(
            patch(clients + "/" + mobileId),
            "{\"name\": \"Portal Android v2\", \"refreshTokenTtlSeconds\": 86400, \"status\": \"INACTIVE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Portal Android v2"))
        .andExpect(jsonPath("$.refreshTokenTtlSeconds").value(86400))
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    send(patch(clients + "/" + mobileId), "{\"accessTokenTtlSeconds\": 10}")
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get(clients))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].clientSecret").isEmpty());

    send(patch(APPS + "/" + AUTH_ADMIN_ID + "/clients/" + mobileId), "{\"name\": \"x\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
  }

  @Test
  void managesPermissionsAndRoles() throws Exception {
    String appId = createApp(unique("billing"));
    String base = APPS + "/" + appId;

    String read =
        read(
            send(post(base + "/permissions"), "{\"code\": \"invoice:read\"}")
                .andExpect(status().isCreated()),
            "$.id");
    String approve =
        read(
            send(
                post(base + "/permissions"),
                "{\"code\": \"invoice:approve\", \"description\": \"Aprobar facturas\"}"),
            "$.id");

    send(post(base + "/permissions"), "{\"code\": \"invoice:read\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERMISSION_ALREADY_EXISTS"));
    send(post(base + "/permissions"), "{\"code\": \"Invoice Read\"}")
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get(base + "/permissions"))
        .andExpect(
            jsonPath("$[*].code")
                .value(org.hamcrest.Matchers.contains("invoice:approve", "invoice:read")));

    String roleId =
        read(
            send(
                    post(base + "/roles"),
                    "{\"code\": \"ACCOUNTANT\", \"name\": \"Contable\", \"permissionIds\": [\"%s\"]}"
                        .formatted(read))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions[0].code").value("invoice:read")),
            "$.id");

    send(post(base + "/roles"), "{\"code\": \"ACCOUNTANT\", \"name\": \"Otro\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROLE_ALREADY_EXISTS"));

    send(
            put(base + "/roles/" + roleId + "/permissions"),
            "{\"permissionIds\": [\"%s\", \"%s\"]}".formatted(read, approve))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));

    mockMvc
        .perform(get(base + "/roles/" + roleId))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.permissions[*].code")
                .value(containsInAnyOrder("invoice:read", "invoice:approve")));

    // Un permiso de otra aplicación no se puede asignar.
    String foreignPermission = "00000000-0000-0000-0000-000000000201";
    send(
            put(base + "/roles/" + roleId + "/permissions"),
            "{\"permissionIds\": [\"%s\"]}".formatted(foreignPermission))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    send(
            put(base + "/roles/" + roleId + "/permissions"),
            "{\"permissionIds\": [\"00000000-0000-0000-0000-00000000beef\"]}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));

    send(put(base + "/roles/" + roleId + "/permissions"), "{\"permissionIds\": []}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty());

    mockMvc
        .perform(get(APPS + "/" + AUTH_ADMIN_ID + "/roles/" + roleId))
        .andExpect(status().isNotFound());
  }

  @Test
  void reportsUnknownApplications() throws Exception {
    String unknown = APPS + "/00000000-0000-0000-0000-00000000dead";
    mockMvc
        .perform(get(unknown))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    mockMvc.perform(get(unknown + "/clients")).andExpect(status().isNotFound());
    mockMvc.perform(get(unknown + "/roles")).andExpect(status().isNotFound());
    mockMvc.perform(get(unknown + "/permissions")).andExpect(status().isNotFound());
  }
}
