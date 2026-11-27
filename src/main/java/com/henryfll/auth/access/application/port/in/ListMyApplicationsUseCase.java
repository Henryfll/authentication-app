package com.henryfll.auth.access.application.port.in;

import com.henryfll.auth.access.domain.ApplicationRef;
import java.util.List;
import java.util.UUID;

/**
 * Aplicaciones a las que un usuario puede entrar ahora mismo, para el lanzador de aplicaciones
 * ({@code GET /users/me/applications}, que se expone cuando haya autenticación en la fase 3).
 */
public interface ListMyApplicationsUseCase {

  List<MyApplication> listEffective(UUID userId);

  record MyApplication(ApplicationRef application, List<String> roles) {}
}
