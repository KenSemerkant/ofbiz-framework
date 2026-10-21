package party.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PersonGroupTest {

    @Test
    void personRequiresBothNames() {
        assertThrows(IllegalArgumentException.class, () -> new Person(null, "B"));
        assertThrows(IllegalArgumentException.class, () -> new Person("A", " "));
        assertEquals("A B", new Person("A", "B").displayName());
    }

    @Test
    void groupRequiresName() {
        assertThrows(IllegalArgumentException.class, () -> new PartyGroup(""));
        assertEquals("Acme", new PartyGroup("Acme").groupName());
    }
}
