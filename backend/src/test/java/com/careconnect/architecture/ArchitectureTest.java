package com.careconnect.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.careconnect.common.security.PublicEndpoint;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the layering and module rules from docs/ARCHITECTURE.md section 3.
 * Package layout: com.careconnect.&lt;module&gt;.{api, service, domain, repository}.
 * Most rules select nothing yet because the feature modules do not exist; they
 * are marked allowEmptyShould so they start protecting each module the moment
 * its first class appears. The canary rule at the top fails if the import
 * finds no classes at all, which would make every other rule pass silently.
 */
@AnalyzeClasses(packages = "com.careconnect", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] FEATURE_MODULES = {
        "com.careconnect.auth..",
        "com.careconnect.patient..",
        "com.careconnect.scheduling..",
        "com.careconnect.clinical..",
        "com.careconnect.orders..",
        "com.careconnect.pharmacy..",
        "com.careconnect.lab..",
        "com.careconnect.portal..",
        "com.careconnect.messaging..",
        "com.careconnect.audit..",
        "com.careconnect.reporting.."
    };

    // ---- canary -----------------------------------------------------------

    @ArchTest
    static final ArchRule applicationClassIsInTheRootPackage =
        classes().that().areAnnotatedWith(SpringBootApplication.class)
            .should().resideInAPackage("com.careconnect");

    // ---- layering inside a module ----------------------------------------

    @ArchTest
    static final ArchRule restControllersLiveInApiPackages =
        classes().that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(Controller.class)
            .should().resideInAPackage("..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entitiesLiveInDomainPackages =
        classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repositoriesAreOnlyUsedByServices =
        classes().that().resideInAPackage("..repository..")
            .should().onlyHaveDependentClassesThat().resideInAnyPackage("..service..", "..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule apiNeverExposesEntities =
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesNeverDependOnApi =
        noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainNeverDependsOnOtherLayers =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..service..", "..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactionsAreDeclaredOnlyInServices =
        noClasses().that().resideOutsideOfPackages("..service..")
            .should().beAnnotatedWith(Transactional.class)
            .allowEmptyShould(true);

    // ---- authorization -----------------------------------------------------

    /**
     * Every endpoint says who may call it: a role annotation ({@code @StaffOnly}, {@code @AdminOnly},
     * {@code @PreAuthorize} and so on, on the method or the class) or {@code @PublicEndpoint}. An endpoint
     * with no rule would be open to every signed-in user, so forgetting one fails the build.
     */
    @ArchTest
    static final ArchRule everyEndpointDeclaresWhoMayCallIt =
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should(declareAnAuthorizationRule())
            .allowEmptyShould(true);

    private static ArchCondition<JavaMethod> declareAnAuthorizationRule() {
        return new ArchCondition<>("declare an authorization rule (a role annotation or @PublicEndpoint)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean declared = method.isMetaAnnotatedWith(PreAuthorize.class)
                    || method.getOwner().isMetaAnnotatedWith(PreAuthorize.class)
                    || method.isAnnotatedWith(PublicEndpoint.class);
                if (!declared) {
                    events.add(SimpleConditionEvent.violated(method,
                        method.getFullName() + " declares no authorization rule"));
                }
            }
        };
    }

    // ---- boundaries between modules --------------------------------------

    @ArchTest
    static final ArchRule commonNeverDependsOnFeatureModules =
        noClasses().that().resideInAPackage("com.careconnect.common..")
            .should().dependOnClassesThat().resideInAnyPackage(FEATURE_MODULES)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
        slices().matching("com.careconnect.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    // ---- general coding rules --------------------------------------------

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
