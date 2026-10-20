package demo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class GreetingTest {

    @Test
    void valueSemantics() {
        Greeting greeting = new Greeting("hello kadir");
        assertEquals("hello kadir", greeting.message());
        assertEquals(greeting, new Greeting("hello kadir"));
        assertEquals(greeting.hashCode(), new Greeting("hello kadir").hashCode());
        assertNotEquals(greeting, new Greeting("hello other"));
        assertEquals("Greeting[message=hello kadir]", greeting.toString());
    }
}
