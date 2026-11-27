package com.henryfll.auth.apps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.shared.error.BusinessRuleException;
import com.henryfll.auth.shared.error.ValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoleTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final ApplicationId APP = ApplicationId.newId();

  @Test
  void permissionsMustBelongToTheSameApplication() {
    Role role = Role.create(APP, "ACCOUNTANT", "Contable", null, NOW);
    Permission own = Permission.create(APP, "invoice:read", null, NOW);
    Permission foreign = Permission.create(ApplicationId.newId(), "invoice:read", null, NOW);

    role.replacePermissions(List.of(own), NOW);
    assertThat(role.permissionIds()).containsExactly(own.id());

    assertThatThrownBy(() -> role.replacePermissions(List.of(own, foreign), NOW))
        .isInstanceOf(BusinessRuleException.class);
    assertThat(role.permissionIds()).containsExactly(own.id());
  }

  @Test
  void canBeRenamed() {
    Role role = Role.create(APP, "ACCOUNTANT", "Contable", null, NOW);
    role.rename("Contador", "Gestiona facturas", NOW);
    assertThat(role.name()).isEqualTo("Contador");
    assertThat(role.description()).isEqualTo("Gestiona facturas");
  }

  @ParameterizedTest
  @ValueSource(strings = {"accountant", "A", "1ADMIN", "SUPER-ADMIN"})
  void rejectsInvalidRoleCodes(String code) {
    assertThatThrownBy(() -> Role.create(APP, code, "Name", null, NOW))
        .isInstanceOf(ValidationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invoice", "Invoice:read", "invoice:", ":read", "invoice read"})
  void rejectsInvalidPermissionCodes(String code) {
    assertThatThrownBy(() -> Permission.create(APP, code, null, NOW))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void acceptsNestedPermissionCodes() {
    assertThat(Permission.create(APP, "reports:sales:export", null, NOW).code())
        .isEqualTo("reports:sales:export");
  }
}
