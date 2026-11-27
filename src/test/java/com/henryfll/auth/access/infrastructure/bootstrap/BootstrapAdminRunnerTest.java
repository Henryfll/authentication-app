package com.henryfll.auth.access.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henryfll.auth.IntegrationTest;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase;
import com.henryfll.auth.access.application.port.in.ManageAccessUseCase.AccessView;
import com.henryfll.auth.access.domain.RoleRef;
import com.henryfll.auth.apps.domain.AuthAdminApplication;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BootstrapAdminRunnerTest extends IntegrationTest {

  private static final String PASSWORD = "bootstrap admin passphrase";

  @Autowired private FindUsersUseCase findUsers;
  @Autowired private CreateUserUseCase createUser;
  @Autowired private ManageAccessUseCase access;

  private BootstrapAdminRunner runner(String email) {
    return new BootstrapAdminRunner(
        new BootstrapAdminProperties(email, PASSWORD), findUsers, createUser, access);
  }

  @Test
  void createsTheSuperAdminOnceAndIsIdempotent() {
    String email = unique("root") + "@example.com";

    runner(email).run(null);
    runner(email).run(null);

    User admin = findUsers.findByEmail(email).orElseThrow();
    List<AccessView> grants = access.listByUser(admin.id().value());
    assertThat(grants).hasSize(1);
    AccessView grant = grants.getFirst();
    assertThat(grant.application().id()).isEqualTo(AuthAdminApplication.ID.value());
    assertThat(grant.effective()).isTrue();
    assertThat(grant.roles()).extracting(RoleRef::code).containsExactly("SUPER_ADMIN");
  }

  @Test
  void grantsAccessToAnExistingUserWithoutChangingThePassword() {
    String email = unique("existing") + "@example.com";
    User existing =
        createUser.create(
            new CreateUserUseCase.CreateUserCommand(email, "existing user passphrase", null, null));

    runner(email).run(null);

    User after = findUsers.findByEmail(email).orElseThrow();
    assertThat(after.passwordHash()).isEqualTo(existing.passwordHash());
    assertThat(access.listByUser(after.id().value())).hasSize(1);
  }

  @Test
  void failsFastWithAClearMessageWhenThePasswordIsInvalid() {
    BootstrapAdminRunner runner =
        new BootstrapAdminRunner(
            new BootstrapAdminProperties(unique("weak") + "@example.com", "short"),
            findUsers,
            createUser,
            access);
    assertThatThrownBy(() -> runner.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUTH_BOOTSTRAP_ADMIN_PASSWORD")
        .hasMessageContaining("at least 12 characters");
  }

  @Test
  void doesNothingWhenNotConfigured() {
    long before =
        findUsers.search(FindUsersUseCase.UserSearchCriteria.none(), page()).totalElements();
    runner(" ").run(null);
    runner(null).run(null);
    long after =
        findUsers.search(FindUsersUseCase.UserSearchCriteria.none(), page()).totalElements();
    assertThat(after).isEqualTo(before);
    assertThat(new BootstrapAdminProperties("a@b.com", "secret").toString())
        .doesNotContain("secret");
  }

  private static com.henryfll.auth.shared.pagination.PageQuery page() {
    return new com.henryfll.auth.shared.pagination.PageQuery(0, 1);
  }
}
