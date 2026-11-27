package com.henryfll.auth;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de los tests de integración: contexto completo + PostgreSQL real (Testcontainers). El
 * contexto y el contenedor se comparten entre clases, así que cada test usa datos únicos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

  @Autowired protected MockMvc mockMvc;

  protected static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  protected static String read(ResultActions result, String path) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path).toString();
  }
}
