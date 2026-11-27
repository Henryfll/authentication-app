package com.henryfll.auth.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void domainExceptionIsMappedToItsStatusAndCode() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value("Email already registered"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.instance").value("/test/conflict"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void bodyValidationErrorsListEachField() throws Exception {
    mockMvc
        .perform(
            post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
        .andExpect(jsonPath("$.instance").value("/test/users"));
  }

  @Test
  void parameterValidationErrorsAreReported() throws Exception {
    mockMvc
        .perform(get("/test/items/ab"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  void malformedJsonIsReportedAsMalformedRequest() throws Exception {
    mockMvc
        .perform(post("/test/users").contentType(MediaType.APPLICATION_JSON).content("{invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void unsupportedMethodIsReported() throws Exception {
    mockMvc
        .perform(post("/test/conflict"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unsupportedMediaTypeIsReported() throws Exception {
    mockMvc
        .perform(post("/test/users").contentType(MediaType.TEXT_PLAIN).content("hello"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void unexpectedErrorsDoNotLeakInternalDetails() throws Exception {
    mockMvc
        .perform(get("/test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }

  record CreateUserRequest(
      @NotBlank @Email String email, @NotBlank @Size(min = 12) String password) {}

  @RestController
  static class TestController {

    @GetMapping("/test/conflict")
    void conflict() {
      throw new DomainException(
          ErrorCode.CONFLICT, "Email already registered", Map.of("field", "email"));
    }

    @PostMapping("/test/users")
    void create(@Valid @RequestBody CreateUserRequest request) {}

    @GetMapping("/test/items/{code}")
    void item(@PathVariable @Size(min = 3) String code) {}

    @GetMapping("/test/boom")
    void boom() {
      throw new IllegalStateException("secret internal state");
    }
  }
}
