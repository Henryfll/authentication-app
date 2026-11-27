package com.henryfll.auth.apps.application.port.out;

import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.Permission;
import com.henryfll.auth.apps.domain.PermissionId;
import java.util.Collection;
import java.util.List;

public interface PermissionRepository {

  Permission save(Permission permission);

  List<Permission> findByApplication(ApplicationId applicationId);

  List<Permission> findAllById(Collection<PermissionId> ids);

  boolean existsByApplicationAndCode(ApplicationId applicationId, String code);
}
