package party.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** P2-F1-S2 AC: link/unlink email/phone/address with purpose validation. */
class ContactMechTest {

    @Test
    void valueFormatValidatedPerType() {
        assertDoesNotThrow(() -> new ContactMech("C1", ContactMechType.EMAIL_ADDRESS, "a@b.co"));
        assertDoesNotThrow(() -> new ContactMech("C2", ContactMechType.TELECOM_NUMBER, "+1 (555) 123-4567"));
        assertDoesNotThrow(() -> new ContactMech("C3", ContactMechType.POSTAL_ADDRESS, "1 Main St"));
        assertThrows(IllegalArgumentException.class,
            () -> new ContactMech("C1", ContactMechType.EMAIL_ADDRESS, "not-an-email"));
        assertThrows(IllegalArgumentException.class,
            () -> new ContactMech("C2", ContactMechType.TELECOM_NUMBER, "555-CALL-NOW"));
        assertThrows(IllegalArgumentException.class,
            () -> new ContactMech("C1", null, "x"));
        assertThrows(IllegalArgumentException.class,
            () -> new ContactMech(" ", ContactMechType.EMAIL_ADDRESS, "a@b.co"));
    }

    @Test
    void purposeMustMatchMechType() {
        assertDoesNotThrow(() -> new PartyContactMech(
            new ContactMech("C1", ContactMechType.EMAIL_ADDRESS, "a@b.co"),
            Set.of(ContactMechPurpose.PRIMARY_EMAIL)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> new PartyContactMech(
                new ContactMech("C1", ContactMechType.EMAIL_ADDRESS, "a@b.co"),
                Set.of(ContactMechPurpose.SHIPPING_ADDRESS)));
        assertTrue(e.getMessage().contains("SHIPPING_ADDRESS"));
    }

    @Test
    void partyLinkAndUnlinkContactMech() {
        Party party = Party.person("P1", new Person("Ada", "Lovelace"));
        ContactMech email = new ContactMech("C1", ContactMechType.EMAIL_ADDRESS, "ada@lovelace.dev");

        assertTrue(party.linkContactMech(email, Set.of(ContactMechPurpose.PRIMARY_EMAIL)));
        assertFalse(party.linkContactMech(email, Set.of(ContactMechPurpose.PRIMARY_EMAIL)));
        assertEquals(1, party.contactMechs().size());

        assertTrue(party.unlinkContactMech("C1"));
        assertFalse(party.unlinkContactMech("C1"));
        assertTrue(party.contactMechs().isEmpty());
    }
}
