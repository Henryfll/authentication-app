package com.henryfll.auth.user.application.service;

import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.shared.error.ValidationException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.application.port.in.ChangeUserStatusUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.application.port.in.VerifyCredentialsUseCase;
import com.henryfll.auth.user.application.port.out.PasswordHasher;
import com.henryfll.auth.user.application.port.out.UserRepository;
import com.henryfll.auth.user.domain.Email;
import com.henryfll.auth.user.domain.PasswordPolicy;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserDeactivatedEvent;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UserService
    implements CreateUserUseCase,
        FindUsersUseCase,
        ChangeUserStatusUseCase,
        VerifyCredentialsUseCase {

  /** Contraseña ficticia cuyo hash se compara cuando el email no existe (tiempo homogéneo). */
  private static final String TIMING_DUMMY_PASSWORD = "timing-equalization-dummy-password";

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Clock clock;
  private volatile String dummyHash;

  private final ApplicationEventPublisher events;

  UserService(
      UserRepository users,
      PasswordHasher passwordHasher,
      Clock clock,
      ApplicationEventPublisher events) {
    this.users = users;
    this.passwordHasher = passwordHasher;
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
        User.register(
            email,
            passwordHasher.hash(command.password()),
            command.firstName(),
            command.lastName(),
            clock.instant());
    return users.save(user);
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
  @Transactional(readOnly = true)
  public Optional<User> verify(String email, String password) {
    Optional<User> user;
    try {
      user = users.findByEmail(new Email(email));
    } catch (ValidationException e) {
      user = Optional.empty();
    }
    if (user.isEmpty()) {
      passwordHasher.matches(password, dummyHash());
      return Optional.empty();
    }
    return passwordHasher.matches(password, user.get().passwordHash()) ? user : Optional.empty();
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
    user.changeStatus(status, clock.instant());
    User saved = users.save(user);
    if (saved.status() != UserStatus.ACTIVE) {
      events.publishEvent(new UserDeactivatedEvent(saved.id().value(), saved.status()));
    }
    return saved;
  }
}
