package com.henryfll.auth.access.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import com.henryfll.auth.access.application.port.in.ListMyApplicationsUseCase;
import com.henryfll.auth.access.application.port.in.ListMyApplicationsUseCase.MyApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminAccessApiTest extends IntegrationTest {

  private static final String ADMIN = "/api/v1/admin";
  private static final String UNKNOWN = "00000000-0000-0000-0000-00000000dead";

  @Autowired private ListMyApplicationsUseCase myApplications;

  private String userId;
  private String appId;
  private String appCode;
  private String viewerRole;
  private String editorRole;

  private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
    return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private String createApp(String code) throws Exception {
    return read(
        send(
            post(ADMIN + "/applications"),
            "{\"code\": \"%s\", \"name\": \"%s\"}".formatted(code, code)),
        "$.id");
  }

  private String createRole(String app, String code) throws Exception {
    return read(
        send(
            post(ADMIN + "/applications/" + app + "/roles"),
            "{\"code\": \"%s\", \"name\": \"%s\"}".formatted(code, code)),
        "$.id");
  }

  private String accessUrl(String app, String user) {
    return ADMIN + "/applications/" + app + "/access/" + user;
  }

  private ResultActions grant(String app, String user, String json) throws Exception {
    return send(put(accessUrl(app, user)), json);
  }

  @BeforeEach
  void setUp() throws Exception {
    userId =
        read(
            send(
                post(ADMIN + "/users"),
                "{\"email\": \"%s@example.com\", \"password\": \"correct horse battery staple\"}"
                    .formatted(unique("access"))),
            "$.id");
    appCode = unique("erp");
    appId = createApp(appCode);
    viewerRole = createRole(appId, "VIEWER");
    editorRole = createRole(appId, "EDITOR");
  }

  @Test
  void grantsUpdatesRevokesAndRegrantsAccess() throws Exception {
    grant(appId, userId, "{\"roleIds\": [\"%s\"]}".formatted(viewerRole))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", accessUrl(appId, userId)))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.effective").value(true))
        .andExpect(jsonPath("$.application.code").value(appCode))
        .andExpect(jsonPath("$.roles[*].code").value(contains("VIEWER")))
        .andExpect(jsonPath("$.validUntil").doesNotExist());

    grant(appId, userId, "{\"roleIds\": [\"%s\", \"%s\"]}".formatted(viewerRole, editorRole))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[*].code").value(contains("EDITOR", "VIEWER")));

    mockMvc.perform(delete(accessUrl(appId, userId))).andExpect(status().isNoContent());
    mockMvc
        .perform(get(accessUrl(appId, userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"))
        .andExpect(jsonPath("$.effective").value(false));

    grant(appId, userId, "{\"roleIds\": []}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.roles").isEmpty());
  }

  @Test
  void accessOutsideItsValidityWindowOrSuspendedIsNotEffective() throws Exception {
    Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
    grant(
            appId,
            userId,
            "{\"roleIds\": [], \"validFrom\": \"%s\", \"validUntil\": \"%s\"}"
                .formatted(tomorrow, tomorrow.plus(7, ChronoUnit.DAYS)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.effective").value(false));

    grant(appId, userId, "{\"roleIds\": [], \"status\": \"SUSPENDED\"}")
        .andExpect(jsonPath("$.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.effective").value(false));

    grant(
            appId,
            userId,
            "{\"roleIds\": [], \"validFrom\": \"%s\", \"validUntil\": \"%s\"}"
                .formatted(tomorrow, tomorrow.minus(2, ChronoUnit.DAYS)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("validUntil"));
  }

  @Test
  void rejectsRolesOfOtherApplicationsAndUnknownReferences() throws Exception {
    String otherApp = createApp(unique("crm"));
    String foreignRole = createRole(otherApp, "VIEWER");

    grant(appId, userId, "{\"roleIds\": [\"%s\"]}".formatted(foreignRole))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
    grant(UNKNOWN, userId, "{\"roleIds\": []}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
    grant(appId, UNKNOWN, "{\"roleIds\": []}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    grant(appId, userId, "{}").andExpect(status().isBadRequest());

    mockMvc
        .perform(get(accessUrl(appId, userId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCESS_NOT_FOUND"));
    mockMvc.perform(delete(accessUrl(appId, userId))).andExpect(status().isNotFound());
    mockMvc.perform(get(ADMIN + "/users/" + UNKNOWN + "/access")).andExpect(status().isNotFound());
  }

  @Test
  void listsAccessByApplicationAndByUser() throws Exception {
    String otherUser =
        read(
            send(
                post(ADMIN + "/users"),
                "{\"email\": \"%s@example.com\", \"password\": \"correct horse battery staple\"}"
                    .formatted(unique("other"))),
            "$.id");
    String otherApp = createApp(unique("crm"));

    grant(appId, userId, "{\"roleIds\": [\"%s\"]}".formatted(viewerRole));
    grant(appId, otherUser, "{\"roleIds\": [], \"status\": \"SUSPENDED\"}");
    grant(otherApp, userId, "{\"roleIds\": []}");

    mockMvc
        .perform(get(ADMIN + "/applications/" + appId + "/access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mockMvc
        .perform(get(ADMIN + "/applications/" + appId + "/access").param("status", "SUSPENDED"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].userId").value(otherUser));

    mockMvc
        .perform(get(ADMIN + "/users/" + userId + "/access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].application.id").value(containsInAnyOrder(appId, otherApp)));
  }

  @Test
  void myApplicationsOnlyIncludesEffectiveAccessToActiveApplications() throws Exception {
    String inactiveApp = createApp(unique("old"));
    String futureApp = createApp(unique("next"));

    grant(appId, userId, "{\"roleIds\": [\"%s\", \"%s\"]}".formatted(editorRole, viewerRole));
    grant(inactiveApp, userId, "{\"roleIds\": []}");
    send(patch(ADMIN + "/applications/" + inactiveApp), "{\"status\": \"INACTIVE\"}")
        .andExpect(status().isOk());
    grant(
        futureApp,
        userId,
        "{\"roleIds\": [], \"validFrom\": \"%s\"}"
            .formatted(Instant.now().plus(1, ChronoUnit.DAYS)));

    List<MyApplication> apps = myApplications.listEffective(UUID.fromString(userId));

    assertThat(apps).hasSize(1);
    assertThat(apps.getFirst().application().code()).isEqualTo(appCode);
    assertThat(apps.getFirst().roles()).containsExactly("EDITOR", "VIEWER");
  }
}
