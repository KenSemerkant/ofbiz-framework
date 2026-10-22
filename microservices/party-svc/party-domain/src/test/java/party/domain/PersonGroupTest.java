package party.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersonGroupTest {

    @Test
    void personRequiresBothNames() {
        assertThrows(IllegalArgumentException.class, () -> new Person(null, "B"));
        assertThrows(IllegalArgumentException.class, () -> new Person("A", " "));
        assertEquals("A B", new Person("A", "B").displayName());
    }

    @Test
    void contactMechPurposeTypeAccessor() {
        assertEquals(party.domain.ContactMechType.POSTAL_ADDRESS,
            party.domain.ContactMechPurpose.SHIPPING_ADDRESS.mechType());
    }

    @Test
    void contactMechWithoutPurposesIsNullSafe() {
        var linked = new party.domain.PartyContactMech(
            new party.domain.ContactMech("C9", party.domain.ContactMechType.POSTAL_ADDRESS,
                "1 Main St"), null);
        assertTrue(linked.purposes().isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> new party.domain.PartyContactMech(null, null));
    }

    @Test
    void groupRequiresName() {
        assertThrows(IllegalArgumentException.class, () -> new PartyGroup(""));
        assertEquals("Acme", new PartyGroup("Acme").groupName());
    }
}
