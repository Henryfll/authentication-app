package com.henryfll.auth.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI expuesta en {@code /swagger-ui.html} y {@code
 * /v3/api-docs}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  private static final String GENERATED_SERVER_DESCRIPTION = "Generated server url";
  static final String SERVER_DESCRIPTION = "Servidor desde el que se ha obtenido este contrato";

  @Bean
  OpenAPI authOpenApi(@Value("${app.version:dev}") String version) {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"))
        .info(
            new Info()
                .title("Authentication App API")
                .description(
                    "Servicio centralizado de autenticación y control de acceso a aplicaciones.")
                .version(version)
                .license(new License().name("Proprietary")));
  }

  /**
   * springdoc genera el servidor a partir de la petición (se adapta a la dirección con la que se
   * consulta el contrato) y lo describe como «Generated server url». Se mantiene dinámico, pero con
   * una descripción que diga qué es.
   */
  @Bean
  OpenApiCustomizer serverDescriptionCustomizer() {
    return openApi -> {
      if (openApi.getServers() != null) {
        openApi.getServers().stream()
            .filter(server -> GENERATED_SERVER_DESCRIPTION.equals(server.getDescription()))
            .forEach(server -> server.setDescription(SERVER_DESCRIPTION));
      }
    };
  }
}
