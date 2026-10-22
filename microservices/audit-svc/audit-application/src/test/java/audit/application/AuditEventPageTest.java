package audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditEventPageTest {

    @Test
    void carriesItemsAndTotal() {
        var page = new AuditEventPage(List.of(), 42L);
        assertEquals(List.of(), page.items());
        assertEquals(42L, page.total());
    }
}
