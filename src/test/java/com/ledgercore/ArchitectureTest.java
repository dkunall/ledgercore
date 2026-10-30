package com.ledgercore;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(packages = "com.ledgercore", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
            "com.fasterxml..", "..application..", "..adapter..");

    @ArchTest
    static final ArchRule applicationDoesNotSeeAdapters = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule webDoesNotTouchPersistence = noClasses().that().resideInAPackage("..adapter.web..")
        .should().dependOnClassesThat().resideInAnyPackage("..adapter.persistence..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule onlyPersistenceAdaptersUseJdbc = noClasses().that().resideOutsideOfPackages(
            "..adapter.persistence..", "..adapter.messaging..", "..adapter.scheduling..", "com.ledgercore.config..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    @ArchTest
    static final ArchRule modulesAreAcyclic = slices().matching("com.ledgercore.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule constructorInjectionOnly = fields().should().notBeAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
