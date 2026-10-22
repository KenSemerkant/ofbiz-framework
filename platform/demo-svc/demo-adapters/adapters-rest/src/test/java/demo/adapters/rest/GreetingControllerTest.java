package demo.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import demo.application.GreetingService;
import demo.domain.Greeting;
import org.junit.jupiter.api.Test;

/** Direct controller test — the demo service has no container test. */
class GreetingControllerTest {

    private final GreetingController controller =
        new GreetingController(new GreetingService(name -> new Greeting("hi " + name)));

    @Test
    void greetsByName() {
        assertEquals("hi Ada", controller.hello(" Ada ").message());
        assertThrows(IllegalArgumentException.class, () -> controller.hello(" "));
    }
}
