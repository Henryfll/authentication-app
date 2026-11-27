package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.user.domain.User;

/** Alta de un usuario por un administrador. */
public interface CreateUserUseCase {

  User create(CreateUserCommand command);

  record CreateUserCommand(String email, String password, String firstName, String lastName) {}
}
