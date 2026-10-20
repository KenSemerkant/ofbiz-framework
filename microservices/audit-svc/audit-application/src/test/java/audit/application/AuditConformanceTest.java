package audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audit.domain.AlertPolicy;
import audit.domain.AuditEvent;
import audit.domain.Severity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.Instant;
import java.util.List;
import ofbiz.arch.OfbizArchitectureRules;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * P1-F3 conformance evidence (pattern from P0-F4-S4 demo): the shared
 * layering rule is green on this module and provably RED on a violation.
 */
class AuditConformanceTest {

    @Nested
    class Layering {
        @Test
        void cleanModulePasses() {
            JavaClasses classes = new ClassFileImporter().importPackages("audit");
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

    @Nested
    class Behavior {
        private final AuditEventStore inMemoryStore = new AuditEventStore() {
            private final java.util.Set<String> seen = new java.util.HashSet<>();

            @Override
            public boolean append(AuditEvent event) {
                return seen.add(event.eventId());
            }

            @Override
            public AuditEventPage search(String topic, Instant from, Instant to, int page, int size) {
                return new AuditEventPage(List.of(), 0);
            }
        };

        @Test
        void duplicateEventNotStoredTwice() {
            RecordingPublisher alerts = new RecordingPublisher();
            RecordAuditEventUseCase useCase =
                new RecordAuditEventUseCase(inMemoryStore, alerts, new AlertPolicy());
            AuditEvent event = new AuditEvent("e1", "audit.testa.v1", "THING_DONE",
                Severity.INFO, "{}", Instant.now());

            assertTrue(useCase.record(event));
            assertFalse(useCase.record(event), "duplicate must be skipped");
        }

        @Test
        void highSeverityStoresAndAlerts() {
            RecordingPublisher alerts = new RecordingPublisher();
            RecordAuditEventUseCase useCase =
                new RecordAuditEventUseCase(inMemoryStore, alerts, new AlertPolicy());

            useCase.record(new AuditEvent("s1", "audit.secrets.v1", "SECRET_ACCESSED",
                Severity.HIGH, "{}", Instant.now()));

            assertEquals(1, alerts.count, "HIGH event must raise exactly one alert");
        }

        private static final class RecordingPublisher implements AlertPublisher {
            int count;

            @Override
            public void publish(Alert alert) {
                count++;
            }
        }
    }
}
