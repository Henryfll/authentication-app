package com.henryfll.auth.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de la documentación OpenAPI expuesta en {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  @Bean
  OpenAPI authOpenApi(@Value("${app.version:dev}") String version) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Authentication App API")
                .description(
                    "Servicio centralizado de autenticación y control de acceso a aplicaciones.")
                .version(version)
                .license(new License().name("Proprietary")));
  }
}
