package com.henryfll.auth.apps.application.port.in;

import com.henryfll.auth.apps.domain.ActivationStatus;
import com.henryfll.auth.apps.domain.ApplicationClient;
import com.henryfll.auth.apps.domain.ApplicationClientId;
import com.henryfll.auth.apps.domain.ApplicationId;
import com.henryfll.auth.apps.domain.ClientPlatform;
import java.time.Duration;
import java.util.List;

/** Gestión de los clientes (web, móvil, servicio) de una aplicación. */
public interface ManageClientsUseCase {

  ClientWithSecret create(ApplicationId applicationId, CreateClientCommand command);

  /** Los campos {@code null} del comando no se modifican. */
  ApplicationClient update(
      ApplicationId applicationId, ApplicationClientId clientId, UpdateClientCommand command);

  List<ApplicationClient> list(ApplicationId applicationId);

  /** Genera un secreto nuevo para un cliente {@code SERVICE}; el anterior deja de ser válido. */
  ClientWithSecret rotateSecret(ApplicationId applicationId, ApplicationClientId clientId);

  /**
   * @param clientId identificador público; {@code null} para generarlo a partir del código de la
   *     aplicación y la plataforma.
   */
  record CreateClientCommand(
      String clientId,
      String name,
      ClientPlatform platform,
      List<String> allowedOrigins,
      Duration accessTokenTtl,
      Duration refreshTokenTtl) {}

  record UpdateClientCommand(
      String name,
      List<String> allowedOrigins,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      ActivationStatus status) {}

  /**
   * Cliente junto con su secreto en claro. El secreto solo se conoce en este momento: se guarda
   * únicamente su hash. Es {@code null} para clientes públicos.
   */
  record ClientWithSecret(ApplicationClient client, String plainSecret) {

    @Override
    public String toString() {
      return "ClientWithSecret[client=" + client.clientId() + ", plainSecret=***]";
    }
  }
}
