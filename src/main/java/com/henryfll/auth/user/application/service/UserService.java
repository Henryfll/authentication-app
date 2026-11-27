package com.henryfll.auth.user.application.service;

import com.henryfll.auth.shared.error.ConflictException;
import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.error.NotFoundException;
import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.application.port.in.ChangeUserStatusUseCase;
import com.henryfll.auth.user.application.port.in.CreateUserUseCase;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase;
import com.henryfll.auth.user.application.port.out.PasswordHasher;
import com.henryfll.auth.user.application.port.out.UserRepository;
import com.henryfll.auth.user.domain.Email;
import com.henryfll.auth.user.domain.PasswordPolicy;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UserService implements CreateUserUseCase, FindUsersUseCase, ChangeUserStatusUseCase {

  private final UserRepository users;
  private final PasswordHasher passwordHasher;
  private final Clock clock;

  UserService(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
    this.users = users;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
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
  public PageResult<User> search(UserSearchCriteria criteria, PageQuery page) {
    return users.search(criteria == null ? UserSearchCriteria.none() : criteria, page);
  }

  @Override
  public User changeStatus(UserId id, UserStatus status) {
    User user = get(id);
    user.changeStatus(status, clock.instant());
    return users.save(user);
  }
}
