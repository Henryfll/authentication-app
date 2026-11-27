package com.henryfll.auth.user.application.port.out;

import com.henryfll.auth.shared.pagination.PageQuery;
import com.henryfll.auth.shared.pagination.PageResult;
import com.henryfll.auth.user.application.port.in.FindUsersUseCase.UserSearchCriteria;
import com.henryfll.auth.user.domain.Email;
import com.henryfll.auth.user.domain.User;
import com.henryfll.auth.user.domain.UserId;
import java.util.Optional;

public interface UserRepository {

  User save(User user);

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(Email email);

  boolean existsByEmail(Email email);

  PageResult<User> search(UserSearchCriteria criteria, PageQuery page);
}
