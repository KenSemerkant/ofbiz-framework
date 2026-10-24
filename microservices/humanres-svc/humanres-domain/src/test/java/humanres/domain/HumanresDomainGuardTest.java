package humanres.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * P6-T3 guard tests: blank identifiers and inverted employment dates are
 * refused at construction — an org chart with dangling position ids or an
 * employment that ends before it starts is wrong by definition.
 */
class HumanresDomainGuardTest {

    @Test
    void positionCarriesLiveSliceFields() {
        EmplPosition position = new EmplPosition("POS-1", "ENGINEER", "POS-0", "ACTIVE");
        assertEquals("POS-1", position.emplPositionId());
        assertEquals("ENGINEER", position.emplPositionTypeId());
        assertEquals("POS-0", position.reportingToEmplPositionId());
        assertEquals("ACTIVE", position.statusId());
    }

    @Test
    void positionAllowsNoReportingParent() {
        assertNull(new EmplPosition("POS-1", "ENGINEER", null, "ACTIVE")
            .reportingToEmplPositionId());
    }

    @Test
    void positionConstructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new EmplPosition(" ", "T", null, "ACTIVE"));
        assertThrows(IllegalArgumentException.class,
            () -> new EmplPosition("POS-1", " ", null, "ACTIVE"));
        assertThrows(IllegalArgumentException.class,
            () -> new EmplPosition("POS-1", "T", null, " "));
    }

    @Test
    void employmentCarriesLiveSliceFields() {
        LocalDate from = LocalDate.parse("2026-10-01");
        Employment employment = new Employment("EMP-1", "P-100", "POS-1", from,
            LocalDate.parse("2026-12-31"));
        assertEquals("EMP-1", employment.employmentId());
        assertEquals("P-100", employment.partyId());
        assertEquals("POS-1", employment.emplPositionId());
        assertEquals(from, employment.fromDate());
        assertEquals(LocalDate.parse("2026-12-31"), employment.thruDate());
    }

    @Test
    void employmentAllowsOpenEndedThruDate() {
        assertNull(new Employment("EMP-1", "P-100", "POS-1",
            LocalDate.parse("2026-10-01"), null).thruDate());
    }

    @Test
    void employmentConstructionGuards() {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate before = LocalDate.parse("2026-09-30");
        assertThrows(IllegalArgumentException.class,
            () -> new Employment(" ", "P", "POS", from, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Employment("EMP", " ", "POS", from, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Employment("EMP", "P", " ", from, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Employment("EMP", "P", "POS", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Employment("EMP", "P", "POS", from, before));
    }
}
