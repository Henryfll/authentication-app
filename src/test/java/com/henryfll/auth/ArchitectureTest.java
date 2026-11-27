package com.henryfll.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de la arquitectura hexagonal descritas en docs/ARCHITECTURE.md (sección 4). */
@AnalyzeClasses(
    packages = "com.henryfll.auth",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String[] MODULES = {"user", "application", "access", "auth"};

  @ArchTest
  static final ArchRule domainIsFrameworkIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
          .because("el dominio debe ser Java puro");

  @ArchTest
  static final ArchRule applicationLayerDoesNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("com.henryfll.auth.*.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..")
          .because("los casos de uso solo conocen puertos, no adaptadores");

  @ArchTest
  static final ArchRule domainDoesNotDependOnOuterLayers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.henryfll.auth.*.application..", "..infrastructure..");

  @ArchTest
  static final ArchRule controllersOnlyUseInboundPorts =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..application.service..",
              "..application.port.out..",
              "..infrastructure.persistence..")
          .because("los controllers solo invocan casos de uso (port.in)");

  @ArchTest
  static final ArchRule modulesOnlyTalkThroughInboundPorts =
      com.tngtech.archunit.lang.CompositeArchRule.of(moduleIsolationRule(MODULES[0]))
          .and(moduleIsolationRule(MODULES[1]))
          .and(moduleIsolationRule(MODULES[2]))
          .and(moduleIsolationRule(MODULES[3]));

  private static ArchRule moduleIsolationRule(String module) {
    String base = "com.henryfll.auth." + module;
    return noClasses()
        .that()
        .resideOutsideOfPackage(base + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            base + ".application.service..",
            base + ".application.port.out..",
            base + ".infrastructure..")
        .because("otros módulos solo pueden usar los puertos de entrada de '" + module + "'");
  }
}
