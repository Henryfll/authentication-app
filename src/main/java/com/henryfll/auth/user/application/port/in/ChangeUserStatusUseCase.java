package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;

/** Bloquear, activar o deshabilitar una cuenta. */
public interface ChangeUserStatusUseCase {

  User changeStatus(UserId id, UserStatus status);
}
