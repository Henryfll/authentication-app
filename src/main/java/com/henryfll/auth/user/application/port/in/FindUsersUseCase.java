package com.henryfll.auth.user.application.port.in;

import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import com.henryfll.auth.user.domain.UserStatus;

/** Consultas sobre usuarios. */
public interface FindUsersUseCase {

  /**
   * @throws com.henryfll.auth.shared.error.NotFoundException si no existe.
   */
  User get(UserId id);

  PageResult<User> search(UserSearchCriteria criteria, PageQuery page);

  /**
   * @param emailContains fragmento del email, sin distinguir mayúsculas; {@code null} para no
   *     filtrar.
   * @param status estado exacto; {@code null} para no filtrar.
   */
  record UserSearchCriteria(String emailContains, UserStatus status) {

    public static UserSearchCriteria none() {
      return new UserSearchCriteria(null, null);
    }
  }
}
