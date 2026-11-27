package com.henryfll.auth.apps.application.port.out;

import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Role;
import com.henryfll.auth.apps.domain.RoleId;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

  Role save(Role role);

  Optional<Role> findById(RoleId id);

  List<Role> findByApplication(ApplicationId applicationId);

  boolean existsByApplicationAndCode(ApplicationId applicationId, String code);
}
