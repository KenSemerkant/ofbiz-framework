package product.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ofbiz.projection.IdempotentProjector;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

/**
 * P3-GATE: the CDC application services delegate correctly — the projector
 * skips replays, the reverse-sync writer receives the snapshot as parsed.
 */
class MonolithProjectionApplicationTest {

    @Test
    void projectionAppliesOnceAndSkipsReplays() {
        var applied = new StringBuilder();
        var service = new MonolithProjectionService(new IdempotentProjector.Sink() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return applied.indexOf(eventId) >= 0;
            }

            @Override
            public void apply(String eventId, String payload) {
                applied.append(eventId);
            }
        });
        assertTrue(service.apply("ofbiz.PRODUCT:0:1", "{}"));
        assertFalse(service.apply("ofbiz.PRODUCT:0:1", "{}"));
    }

    @Test
    void reverseSyncForwardsSnapshotToMonolithWriter() {
        var received = new StringBuilder();
        MonolithSyncWriter writer = new MonolithSyncWriter() {
            @Override
            public void syncProduct(String productId, String productTypeId,
                    String internalName, String productName, Set<String> categoryIds,
                    List<ProductPrice> prices, List<ProductAssoc> assocs) {
                received.append(productId).append('|').append(productTypeId);
            }

            @Override
            public void syncCategory(String productCategoryId, String categoryName) {
                received.append(productCategoryId).append('=').append(categoryName);
            }

            @Override
            public void deleteProduct(String productId) {
                received.append('-').append(productId);
            }
        };
        var reverseSync = new ReverseSyncService(writer);
        reverseSync.onProductEvent(new ReverseSyncService.ProductSnapshot(
            "PRD1", "FINISHED_GOOD", "Widget", null, Set.of(), List.of(), List.of()));
        reverseSync.onCategoryEvent("CAT1", "Widgets");
        reverseSync.onProductDeleted("PRD1");
        assertTrue(received.toString().contains("PRD1|FINISHED_GOOD"));
        assertTrue(received.toString().contains("CAT1=Widgets"));
        assertTrue(received.toString().contains("-PRD1"));
    }
}
