package com.henryfll.auth.user.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henryfll.auth.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class AdminUserApiTest extends IntegrationTest {

  private static final String USERS = "/api/v1/admin/users";
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private JdbcTemplate jdbc;

  private ResultActions createUser(String email, String password) throws Exception {
    return mockMvc.perform(
        post(USERS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "password": "%s", "firstName": "Ana", "lastName": "García"}
                """
                    .formatted(email, password)));
  }

  @Test
  void createsAUserAndStoresAnArgon2Hash() throws Exception {
    String email = unique("ana") + "@Example.com";
    ResultActions result =
        createUser(email, PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString(USERS + "/")))
            .andExpect(jsonPath("$.email").value(email.toLowerCase()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

    String id = read(result, "$.id");
    String hash =
        jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?::uuid", String.class, id);
    assertThat(hash).startsWith("{argon2}").doesNotContain(PASSWORD);

    mockMvc
        .perform(get(USERS + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ana"));
  }

  @Test
  void emailsAreUniqueIgnoringCase() throws Exception {
    String email = unique("dup") + "@example.com";
    createUser(email, PASSWORD).andExpect(status().isCreated());
    createUser(email.toUpperCase(), PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void rejectsWeakPasswordsAndInvalidEmails() throws Exception {
    createUser(unique("weak") + "@example.com", "password1234")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    createUser("not-an-email", PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void changesTheStatusOfAUser() throws Exception {
    String id = read(createUser(unique("lock") + "@example.com", PASSWORD), "$.id");

    mockMvc
        .perform(
            patch(USERS + "/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"LOCKED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"));

    mockMvc
        .perform(get(USERS).param("status", "LOCKED").param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].status", everyItem(org.hamcrest.Matchers.is("LOCKED"))));
  }

  @Test
  void searchesUsersByEmailFragmentWithPagination() throws Exception {
    String marker = unique("search");
    for (int i = 0; i < 3; i++) {
      createUser(marker + "-" + i + "@example.com", PASSWORD).andExpect(status().isCreated());
    }

    mockMvc
        .perform(get(USERS).param("email", marker.toUpperCase()).param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.page").value(0));

    mockMvc.perform(get(USERS)).andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
  }

  @Test
  void reportsUnknownUsersAndInvalidPagination() throws Exception {
    mockMvc
        .perform(get(USERS + "/00000000-0000-0000-0000-00000000dead"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

    mockMvc
        .perform(get(USERS + "/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    mockMvc
        .perform(get(USERS).param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }
}
