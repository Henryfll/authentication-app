package com.henryfll.auth.apps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.BusinessRuleException;
import com.henryfll.auth.shared.error.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationClientTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final ApplicationId APP = ApplicationId.newId();

  private static ApplicationClient web(List<String> origins) {
    return ApplicationClient.create(
        APP, "erp-web", "ERP Web", ClientPlatform.WEB, null, origins, null, null, NOW);
  }

  @Test
  void appliesPlatformDefaults() {
    ApplicationClient webClient = web(List.of());
    ApplicationClient mobile =
        ApplicationClient.create(
            APP, "erp-android", "ERP Android", ClientPlatform.MOBILE, null, null, null, null, NOW);

    assertThat(webClient.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(webClient.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    assertThat(mobile.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
    assertThat(mobile.isConfidential()).isFalse();
    assertThat(mobile.isActive()).isTrue();
  }

  @Test
  void normalizesAndDeduplicatesOrigins() {
    ApplicationClient client =
        web(List.of("https://App.Example.com", "https://app.example.com", "http://localhost:4200"));
    assertThat(client.allowedOrigins())
        .containsExactly("https://app.example.com", "http://localhost:4200");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "app.example.com",
        "ftp://app.example.com",
        "https://app.example.com/path",
        "https://app.example.com?x=1",
        "https://user@app.example.com",
        "https://"
      })
  void rejectsInvalidOrigins(String origin) {
    assertThatThrownBy(() -> web(List.of(origin))).isInstanceOf(ValidationException.class);
  }

  @Test
  void onlyWebClientsHaveOrigins() {
    assertThatThrownBy(() ->
            ApplicationClient.create(
                APP,
                "erp-ios",
                "ERP iOS",
                ClientPlatform.MOBILE,
                null,
                List.of("https://app.example.com"),
                null,
                null,
                NOW))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void onlyServiceClientsAreConfidential() {
    assertThatThrownBy(() ->
            ApplicationClient.create(
                APP, "erp-web", "ERP", ClientPlatform.WEB, "{argon2}x", null, null, null, NOW))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() ->
            ApplicationClient.create(
                APP, "erp-svc", "ERP", ClientPlatform.SERVICE, null, null, null, null, NOW))
        .isInstanceOf(BusinessRuleException.class);

    ApplicationClient service =
        ApplicationClient.create(
            APP, "erp-svc", "ERP", ClientPlatform.SERVICE, "{argon2}x", null, null, null, NOW);
    service.rotateSecret("{argon2}y", NOW);
    assertThat(service.secretHash()).isEqualTo("{argon2}y");
    assertThatThrownBy(() -> web(List.of()).rotateSecret("{argon2}z", NOW))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void tokenTtlsMustBeWithinBounds() {
    ApplicationClient client = web(List.of());
    assertThatThrownBy(() -> client.changeTokenTtls(Duration.ofSeconds(30), null, NOW))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> client.changeTokenTtls(null, Duration.ofDays(91), NOW))
        .isInstanceOf(ValidationException.class);

    client.changeTokenTtls(Duration.ofMinutes(5), Duration.ofDays(1), NOW);
    assertThat(client.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(client.refreshTokenTtl()).isEqualTo(Duration.ofDays(1));
  }

  @Test
  void clientIdMustBeASlug() {
    assertThatThrownBy(() ->
            ApplicationClient.create(
                APP, "ERP Web", "ERP", ClientPlatform.WEB, null, null, null, null, NOW))
        .isInstanceOf(ValidationException.class);
  }
}
