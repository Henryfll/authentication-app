package com.henryfll.auth.user.application.service;

import com.henryfll.auth.shared.audit.AuditEvent;
import com.henryfll.auth.shared.audit.AuditEventType;
import com.henryfll.auth.shared.audit.AuditLog;
import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.shared.error.ValidationException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.application.port.in.ChangePasswordUseCase;
import com.henryfll.auth.user.application.port.in.ChangeUserStatusUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.application.port.in.VerifyCredentialsUseCase;
import com.henryfll.auth.user.application.port.out.PasswordHasher;
import com.henryfll.auth.user.application.port.out.UserRepository;
import com.henryfll.auth.user.domain.Email;
import com.henryfll.auth.user.domain.LockoutPolicy;
import com.henryfll.auth.user.domain.PasswordChangedEvent;
import com.henryfll.auth.user.domain.PasswordPolicy;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserDeactivatedEvent;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UserService
    implements CreateUserUseCase,
        FindUsersUseCase,
        ChangeUserStatusUseCase,
        VerifyCredentialsUseCase,
        ChangePasswordUseCase {

  /**
   * Contraseña ficticia cuyo hash se compara cuando no se puede comprobar la real (tiempo
   * homogéneo).
   */
  private static final String TIMING_DUMMY_PASSWORD = "timing-equalization-dummy-password";

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final LockoutPolicy lockoutPolicy;
  private final AuditLog audit;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private volatile String dummyHash;

  UserService(
      UserRepository users,
      PasswordHasher passwordHasher,
      LockoutPolicy lockoutPolicy,
      AuditLog audit,
      Clock clock,
      ApplicationEventPublisher events) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.lockoutPolicy = lockoutPolicy;
    this.audit = audit;
    this.clock = clock;
    this.events = events;
  }

  @Override
  public User create(CreateUserCommand command) {
    Email email = new Email(command.email());
    PasswordPolicy.validate(command.password(), email);
    if (users.existsByEmail(email)) {
      throw new ConflictException(
          ErrorCode.USER_EMAIL_ALREADY_EXISTS, "A user with this email already exists");
    }
    User user =
        users.save(
            User.register(
                email,
                passwordHasher.hash(command.password()),
                command.firstName(),
                command.lastName(),
                clock.instant()));
    audit.record(
        AuditEvent.of(AuditEventType.USER_CREATED, user.id().value(), null)
            .with("email", user.email().value()));
    return user;
  }

  @Override
  @Transactional(readOnly = true)
  public User get(UserId id) {
    return users
        .findById(id)
        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, id));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return users.findByEmail(new Email(email));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<User> search(UserSearchCriteria criteria, PageQuery page) {
    return users.search(criteria == null ? UserSearchCriteria.none() : criteria, page);
  }

  @Override
  public CredentialsCheck verify(String email, String password) {
    Optional<User> found;
    try {
      found = users.findByEmail(new Email(email));
    } catch (ValidationException e) {
      found = Optional.empty();
    }
    if (found.isEmpty()) {
      passwordHasher.matches(password, dummyHash());
      return new CredentialsCheck(Outcome.UNKNOWN_USER, Optional.empty());
    }
    User user = found.get();
    Instant now = clock.instant();
    if (user.isTemporarilyLockedAt(now)) {
      passwordHasher.matches(password, dummyHash());
      return new CredentialsCheck(Outcome.TEMPORARILY_LOCKED, found);
    }
    if (!passwordHasher.matches(password, user.passwordHash())) {
      boolean locked = user.recordFailedLogin(lockoutPolicy, now);
      users.save(user);
      if (locked) {
        audit.record(
            AuditEvent.of(AuditEventType.ACCOUNT_TEMPORARILY_LOCKED, user.id().value(), null)
                .with("failedAttempts", user.failedLoginAttempts())
                .with("lockedUntil", user.lockedUntil().toString()));
      }
      return new CredentialsCheck(Outcome.INVALID_PASSWORD, found);
    }
    user.recordSuccessfulLogin(now);
    return new CredentialsCheck(Outcome.VALID, Optional.of(users.save(user)));
  }

  private String dummyHash() {
    String hash = dummyHash;
    if (hash == null) {
      hash = passwordHasher.hash(TIMING_DUMMY_PASSWORD);
      dummyHash = hash;
    }
    return hash;
  }

  @Override
  public User changeStatus(UserId id, UserStatus status) {
    User user = get(id);
    UserStatus previous = user.status();
    Instant now = clock.instant();
    boolean wasTemporarilyLocked = user.isTemporarilyLockedAt(now);
    user.changeStatus(status, now);
    User saved = users.save(user);
    if (wasTemporarilyLocked && !saved.isTemporarilyLockedAt(now)) {
      audit.record(AuditEvent.of(AuditEventType.ACCOUNT_UNLOCKED, saved.id().value(), null));
    }
    if (previous != saved.status()) {
      audit.record(
          AuditEvent.of(AuditEventType.USER_STATUS_CHANGED, saved.id().value(), null)
              .with("from", previous)
              .with("to", saved.status()));
    }
    if (saved.status() != UserStatus.ACTIVE) {
      events.publishEvent(new UserDeactivatedEvent(saved.id().value(), saved.status()));
    }
    return saved;
  }

  @Override
  public void changeOwnPassword(
      UserId userId, String currentPassword, String newPassword, UUID currentSessionId) {
    User user = get(userId);
    if (currentPassword == null || !passwordHasher.matches(currentPassword, user.passwordHash())) {
      throw new ValidationException(
          ErrorCode.INVALID_CURRENT_PASSWORD, "currentPassword", "is incorrect");
    }
    PasswordPolicy.validate(newPassword, user.email());
    if (passwordHasher.matches(newPassword, user.passwordHash())) {
      throw new ValidationException(
          ErrorCode.PASSWORD_POLICY_VIOLATION,
          "newPassword",
          "must be different from the current password");
    }
    user.changePasswordHash(passwordHasher.hash(newPassword), clock.instant());
    users.save(user);
    audit.record(AuditEvent.of(AuditEventType.PASSWORD_CHANGED, user.id().value(), null));
    events.publishEvent(new PasswordChangedEvent(user.id().value(), currentSessionId, false));
  }

  @Override
  public void resetPassword(UserId userId, String newPassword) {
    User user = get(userId);
    PasswordPolicy.validate(newPassword, user.email());
    user.resetPasswordHash(passwordHasher.hash(newPassword), clock.instant());
    users.save(user);
    audit.record(AuditEvent.of(AuditEventType.PASSWORD_RESET, user.id().value(), null));
    events.publishEvent(new PasswordChangedEvent(user.id().value(), null, true));
  }
}
