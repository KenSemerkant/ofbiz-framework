package notification.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import ofbiz.arch.OfbizArchitectureRules;
import org.junit.jupiter.api.Test;

/**
 * P1-F4-S1: shared layering rule is green on this module and provably RED
 * on a violation (same evidence pattern as demo-svc).
 */
class NotificationConformanceTest {

    @Test
    void cleanModulePasses() {
        JavaClasses classes = new ClassFileImporter().importPackages("notification");
        OfbizArchitectureRules.layeredArchitecture().check(classes);
        OfbizArchitectureRules.noFrameworkDependenciesInDomain().check(classes);
    }

    @Test
    void violationIsRejected() {
        // Fixture lives in ..application.. but depends on ..adapters.. — inward-only rule must fire.
        JavaClasses bad = new ClassFileImporter()
            .importPackages("violation.application", "violation.adapters");
        assertThrows(AssertionError.class,
            () -> OfbizArchitectureRules.layeredArchitecture().check(bad));
    }
}
