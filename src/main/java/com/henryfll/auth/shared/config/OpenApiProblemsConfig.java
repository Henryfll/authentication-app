package com.henryfll.auth.shared.config;

import com.henryfll.auth.shared.error.ErrorCode;
import com.henryfll.auth.shared.web.ErrorResponses;
import com.henryfll.auth.shared.web.ProblemResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Documenta los errores en el contrato OpenAPI: registra el esquema {@code Problem} y añade a cada
 * operación sus respuestas de error.
 *
 * <p>Sin esto, un cliente generado desde el contrato no tiene tipo para los errores. Los códigos
 * comunes se deducen de la propia operación; los específicos se indican con {@link ErrorResponses}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiProblemsConfig {

  static final String PROBLEM_SCHEMA = "Problem";
  static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
  private static final String ADMIN_PREFIX = "/api/v1/admin";

  /** Registra {@code Problem} (y {@code ProblemFieldError}) en los componentes del contrato. */
  @Bean
  OpenApiCustomizer problemSchemaCustomizer() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      Map<String, Schema> schemas =
          ModelConverters.getInstance(true).readAll(ProblemResponse.class);
      schemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    };
  }

  /** Añade a cada operación las respuestas de error que puede devolver. */
  @Bean
  OperationCustomizer problemResponsesCustomizer() {
    return (operation, handlerMethod) -> {
      Set<ErrorCode> codes = new LinkedHashSet<>();
      if (isSecured(operation)) {
        codes.add(ErrorCode.AUTHENTICATION_REQUIRED);
        codes.add(ErrorCode.INVALID_TOKEN);
      }
      if (isAdmin(handlerMethod)) {
        codes.add(ErrorCode.INSUFFICIENT_PERMISSIONS);
      }
      if (hasInput(operation)) {
        codes.add(ErrorCode.VALIDATION_ERROR);
        codes.add(ErrorCode.MALFORMED_REQUEST);
      }
      codes.add(ErrorCode.INTERNAL_ERROR);
      ErrorResponses declared = handlerMethod.getMethodAnnotation(ErrorResponses.class);
      if (declared != null) {
        codes.addAll(Arrays.asList(declared.value()));
      }
      addProblemResponses(operation, codes);
      return operation;
    };
  }

  /** Una operación sin {@code security} propio hereda el {@code bearer} global. */
  private static boolean isSecured(Operation operation) {
    return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
  }

  private static boolean isAdmin(HandlerMethod handlerMethod) {
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(
            ClassUtils.getUserClass(handlerMethod.getBeanType()), RequestMapping.class);
    return mapping != null
        && Arrays.stream(mapping.path()).anyMatch(path -> path.startsWith(ADMIN_PREFIX));
  }

  private static boolean hasInput(Operation operation) {
    return operation.getRequestBody() != null
        || (operation.getParameters() != null && !operation.getParameters().isEmpty());
  }

  private static void addProblemResponses(Operation operation, Set<ErrorCode> codes) {
    if (operation.getResponses() == null) {
      operation.setResponses(new ApiResponses());
    }
    Map<Integer, List<ErrorCode>> byStatus = new TreeMap<>();
    for (ErrorCode code : codes) {
      byStatus.computeIfAbsent(code.type().httpStatus(), s -> new ArrayList<>()).add(code);
    }
    byStatus.forEach((status, statusCodes) -> {
      String key = String.valueOf(status);
      if (operation.getResponses().containsKey(key)) {
        return;
      }
      String list = statusCodes.stream().map(ErrorCode::name).collect(Collectors.joining(", "));
      operation
          .getResponses()
          .addApiResponse(
              key,
              new ApiResponse()
                  .description("Error. Códigos posibles: " + list)
                  .content(
                      new Content()
                          .addMediaType(
                              PROBLEM_MEDIA_TYPE,
                              new MediaType()
                                  .schema(
                                      new Schema<>()
                                          .$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
    });
  }
}
