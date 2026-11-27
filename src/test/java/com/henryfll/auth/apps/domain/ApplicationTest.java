package com.henryfll.auth.apps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.BusinessRuleException;
import com.henryfll.auth.shared.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void codeIsNormalizedToLowercase() {
    assertThat(new ApplicationCode(" ERP ").value()).isEqualTo("erp");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab", "1erp", "erp-", "erp_app", "e rp", ""})
  void rejectsInvalidCodes(String code) {
    assertThatThrownBy(() -> new ApplicationCode(code)).isInstanceOf(ValidationException.class);
  }

  @Test
  void newApplicationsAreActiveWithoutSelfRegistration() {
    Application app = Application.create(new ApplicationCode("erp"), "ERP", null, NOW);
    assertThat(app.isActive()).isTrue();
    assertThat(app.selfRegistration()).isFalse();
    assertThat(app.defaultRoleId()).isNull();
  }

  @Test
  void selfRegistrationRequiresADefaultRoleOfTheSameApplication() {
    Application app = Application.create(new ApplicationCode("erp"), "ERP", null, NOW);
    assertThatThrownBy(() -> app.changeSelfRegistration(true, NOW))
        .isInstanceOf(BusinessRuleException.class);

    Role foreignRole = Role.create(ApplicationId.newId(), "USER", "User", null, NOW);
    assertThatThrownBy(() -> app.assignDefaultRole(foreignRole, NOW))
        .isInstanceOf(BusinessRuleException.class);

    Role role = Role.create(app.id(), "USER", "User", null, NOW);
    app.assignDefaultRole(role, NOW);
    app.changeSelfRegistration(true, NOW);
    assertThat(app.selfRegistration()).isTrue();
    assertThat(app.defaultRoleId()).isEqualTo(role.id());
  }

  @Test
  void nameIsRequired() {
    Application app = Application.create(new ApplicationCode("erp"), "ERP", null, NOW);
    assertThatThrownBy(() -> app.rename(" ", NOW)).isInstanceOf(ValidationException.class);
  }
}
