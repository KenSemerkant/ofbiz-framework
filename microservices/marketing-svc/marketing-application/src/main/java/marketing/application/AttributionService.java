package marketing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import marketing.domain.TrackingCode;

/**
 * P6-T5 attribution use case: consumes order.order.placed.v1 rows and owns
 * the per-tracking-code report the CG checks against the monolith. Orders
 * without a tracking code are skipped; unknown codes are still recorded
 * (the report shows what arrived, campaignId stays null until the code is
 * defined).
 */
public class AttributionService {

    /** One report row per tracking code, ordered by code (CG report shape). */
    public record AttributionReportRow(String trackingCode, String campaignId, long orderCount,
            BigDecimal revenueTotal) {
    }

    public record AttributionDetail(String trackingCode, String campaignId, long orderCount,
            BigDecimal revenueTotal, List<AttributionStore.Attribution> orders) {
    }

    private final AttributionStore attributions;
    private final TrackingCodeStore trackingCodes;

    public AttributionService(AttributionStore attributions, TrackingCodeStore trackingCodes) {
        this.attributions = attributions;
        this.trackingCodes = trackingCodes;
    }

    /**
     * Idempotent via the store's unique order_id — the processed_events
     * check in the consumer is the first line of defense, this is the second.
     */
    public void onOrderPlaced(String orderId, String trackingCode, BigDecimal orderTotal,
            Instant occurredAt) {
        if (trackingCode == null || trackingCode.isBlank()) {
            return;
        }
        attributions.record(new AttributionStore.Attribution(orderId, trackingCode, orderTotal,
            occurredAt));
    }

    public List<AttributionReportRow> report() {
        var byCode = new TreeMap<String, List<AttributionStore.Attribution>>();
        for (AttributionStore.Attribution attribution : attributions.findAll()) {
            byCode.computeIfAbsent(attribution.trackingCode(), code -> new ArrayList<>())
                .add(attribution);
        }
        List<AttributionReportRow> rows = new ArrayList<>();
        byCode.forEach((code, orders) -> rows.add(new AttributionReportRow(code,
            resolveCampaignId(code), orders.size(),
            orders.stream().map(AttributionStore.Attribution::orderTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add))));
        return rows;
    }

    public AttributionDetail detail(String trackingCode) {
        List<AttributionStore.Attribution> orders = attributions.findByTrackingCode(trackingCode);
        if (orders.isEmpty()) {
            throw new NoSuchElementException("no attributions for " + trackingCode);
        }
        return new AttributionDetail(trackingCode, resolveCampaignId(trackingCode), orders.size(),
            orders.stream().map(AttributionStore.Attribution::orderTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            orders);
    }

    /** Campaign resolved FK-by-id at read time; unknown codes report null. */
    private String resolveCampaignId(String trackingCode) {
        return trackingCodes.find(trackingCode).map(TrackingCode::marketingCampaignId)
            .orElse(null);
    }
}
