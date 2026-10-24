package manufacturing.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * P6-T1 bill of materials (monolith ProductAssoc PRODUCT_MANUFACTURER
 * slice): productId → component lines. Components are inline JSON on the
 * row (finance-svc gl_transactions precedent); quantities are per-parent
 * unit, scrap is a percentage.
 */
public final class Bom {

    public static final String CREATED = "manufacturing.bom.created.v1";
    public static final String UPDATED = "manufacturing.bom.updated.v1";
    public static final String DELETED = "manufacturing.bom.deleted.v1";

    private final String productId;
    private final List<Component> components;

    public Bom(String productId, List<Component> components) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("a bom needs at least one component");
        }
        this.productId = productId;
        this.components = List.copyOf(components);
    }

    public String productId() {
        return productId;
    }

    public List<Component> components() {
        return components;
    }

    /** One component line: quantity per parent unit plus scrap percentage. */
    public record Component(String productId, BigDecimal quantity, BigDecimal scrapPct) {

        public Component {
            if (productId == null || productId.isBlank()) {
                throw new IllegalArgumentException("component productId must not be blank");
            }
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("component quantity must be positive");
            }
            if (scrapPct == null || scrapPct.signum() < 0
                    || scrapPct.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("scrapPct must be between 0 and 100");
            }
        }
    }
}
