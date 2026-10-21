package party.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import ofbiz.arch.OfbizArchitectureRules;
import org.junit.jupiter.api.Test;

/** ADR-007 layering checked on the full party-svc tree, adapters included. */
class ServiceArchitectureTest {

    @Test
    void fullServiceTreeHonoursLayering() {
        JavaClasses classes = new ClassFileImporter().importPackages("party");

        assertTrue(classes.stream().anyMatch(c -> c.getPackageName().contains(".adapters.")),
            "adapter classes must be on the checked classpath, otherwise the gate is vacuous");

        OfbizArchitectureRules.layeredArchitecture().check(classes);
        OfbizArchitectureRules.noFrameworkDependenciesInDomain().check(classes);
    }
}
