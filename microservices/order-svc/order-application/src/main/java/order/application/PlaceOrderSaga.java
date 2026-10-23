package order.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import order.domain.Order;
import order.domain.OrderLine;

/**
 * P4-F1-S2 / P4-F2-S1 placeOrder orchestrator: validate party, snapshot
 * prices, persist the order + saga row, emit `order.order.placed.v1` —
 * the ECA steps (inventory reserve, invoice trigger, attribution) hang off
 * the placed event and are driven by {@link OrderEcaService}.
 */
public class PlaceOrderSaga {

    public static final String ORDER_PLACED = "order.order.placed.v1";

    /** Requested line before pricing (P4-F1-S2 cart shape). */
    public record CartLine(String productId, String facilityId, java.math.BigDecimal quantity) {
    }

    private final OrderStore orders;
    private final SagaStore sagas;
    private final OutboxWriter outbox;
    private final OrderSerializer serializer;
    private final PartyValidator partyValidator;
    private final PriceSnapshotProvider prices;

    public PlaceOrderSaga(OrderStore orders, SagaStore sagas, OutboxWriter outbox,
            OrderSerializer serializer, PartyValidator partyValidator,
            PriceSnapshotProvider prices) {
        this.orders = orders;
        this.sagas = sagas;
        this.outbox = outbox;
        this.serializer = serializer;
        this.partyValidator = partyValidator;
        this.prices = prices;
    }

    /**
     * @throws PartyValidationException  unknown party (no order written)
     * @throws PriceUnavailableException a line has no applicable price
     */
    public Order place(String customerId, String currencyUomId, List<CartLine> cart,
            String trackingCode) {
        if (!partyValidator.isValid(customerId)) {
            throw new PartyValidationException(customerId);
        }
        return placeWithLines(customerId, snapshotPrices(cart, currencyUomId), trackingCode);
    }

    /**
     * Placement with pre-snapshotted lines (P4-F1-S4 quote conversion): the
     * lines move over verbatim — no re-pricing — but the saga machinery
     * (party validation is the caller's duty, saga row, placed event) is
     * identical to a fresh placement.
     */
    public Order placeWithLines(String customerId, List<OrderLine> lines, String trackingCode) {
        Order order = new Order("ORDER-" + UUID.randomUUID(), customerId, trackingCode, lines,
            Instant.now());
        orders.save(order);
        sagas.create(order.orderId());
        outbox.append(ORDER_PLACED, order.orderId(), serializer.serializePlaced(order));
        return order;
    }

    /** Immutable snapshot (P4-F1-S2 AC): every line priced at placement. */
    public List<OrderLine> snapshotPrices(List<CartLine> cart, String currencyUomId) {
        List<OrderLine> lines = new ArrayList<>();
        int seq = 1;
        for (CartLine line : cart) {
            PriceSnapshotProvider.ResolvedPrice price = prices.resolve(line.productId(),
                currencyUomId).orElseThrow(() -> new PriceUnavailableException(line.productId()));
            lines.add(new OrderLine("0000" + seq++, line.productId(), line.facilityId(),
                line.quantity(), price.price(), price.currencyUomId()));
        }
        return List.copyOf(lines);
    }

    public PartyValidator partyValidator() {
        return partyValidator;
    }

    public PriceSnapshotProvider prices() {
        return prices;
    }
}
