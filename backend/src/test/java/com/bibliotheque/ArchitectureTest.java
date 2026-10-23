package com.bibliotheque;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Règles d'ADR-001 : dépendances entre modules, couches internes, injection par constructeur. */
@AnalyzeClasses(packages = "com.bibliotheque", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String COMMON = "com.bibliotheque.common..";
    private static final String AUTH = "com.bibliotheque.auth..";
    private static final String CATALOG = "com.bibliotheque.catalog..";
    private static final String MEMBER = "com.bibliotheque.member..";
    private static final String CIRCULATION = "com.bibliotheque.circulation..";
    private static final String REPORTING = "com.bibliotheque.reporting..";

    @ArchTest
    static final ArchRule commonDependsOnNoModule = noClasses().that().resideInAPackage(COMMON)
            .should().dependOnClassesThat().resideInAnyPackage(AUTH, CATALOG, MEMBER, CIRCULATION, REPORTING);

    @ArchTest
    static final ArchRule authDependsOnlyOnCommon = noClasses().that().resideInAPackage(AUTH)
            .should().dependOnClassesThat().resideInAnyPackage(CATALOG, MEMBER, CIRCULATION, REPORTING);

    @ArchTest
    static final ArchRule catalogDependsOnlyOnCommon = noClasses().that().resideInAPackage(CATALOG)
            .should().dependOnClassesThat().resideInAnyPackage(AUTH, MEMBER, CIRCULATION, REPORTING)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule memberDependsOnCommonAndAuth = noClasses().that().resideInAPackage(MEMBER)
            .should().dependOnClassesThat().resideInAnyPackage(CATALOG, CIRCULATION, REPORTING);

    @ArchTest
    static final ArchRule circulationDependsOnCommonCatalogMember = noClasses().that().resideInAPackage(CIRCULATION)
            .should().dependOnClassesThat().resideInAnyPackage(AUTH, REPORTING)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = noFields().should().beAnnotatedWith(Autowired.class)
            .because("l'injection se fait par constructeur (ADR-001 §5)");

    /** Dans chaque module : web → service → repository/domain ; un job n'appelle pas le web. */
    @ArchTest
    static void layersAreRespectedInEachModule(JavaClasses classes) {
        for (String module : List.of("auth", "catalog", "member", "circulation", "reporting")) {
            String base = "com.bibliotheque." + module;
            noClasses().that().resideInAnyPackage(base + ".domain..", base + ".repository..")
                    .should().dependOnClassesThat().resideInAnyPackage(base + ".service..", base + ".web..",
                            base + ".job..")
                    .allowEmptyShould(true).check(classes);
            noClasses().that().resideInAnyPackage(base + ".service..", base + ".job..")
                    .should().dependOnClassesThat().resideInAPackage(base + ".web..")
                    .allowEmptyShould(true).check(classes);
            noClasses().that().resideInAPackage(base + ".web..")
                    .should().dependOnClassesThat().resideInAPackage(base + ".repository..")
                    .because("un contrôleur passe toujours par un service")
                    .allowEmptyShould(true).check(classes);
        }
    }
}
