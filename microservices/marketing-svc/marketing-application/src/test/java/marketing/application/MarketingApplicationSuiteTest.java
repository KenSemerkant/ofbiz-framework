package marketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import marketing.domain.CampaignStatus;
import marketing.domain.MarketingCampaign;
import marketing.domain.SalesOpportunity;
import marketing.domain.TrackingCode;
import org.junit.jupiter.api.Test;

/**
 * P6-T5 use-case suite: aggregate CRUD with outbox events, the FK-by-id
 * campaign guard, attribution skip-uncoded/idempotency, and the report the
 * CG checker compares against the monolith TrackingCodeOrder slice.
 */
class MarketingApplicationSuiteTest {

    private final FakeStores stores = new FakeStores();
    private final List<String> outbox = new ArrayList<>();
    private final MarketingService marketing;
    private final AttributionService attribution;

    MarketingApplicationSuiteTest() {
        MarketingSerializer serializer = new MarketingSerializer() {
            @Override
            public String serializeCampaign(MarketingCampaign c) {
                return "campaign:" + c.marketingCampaignId();
            }

            @Override
            public String serializeTrackingCode(TrackingCode t) {
                return "trackingcode:" + t.trackingCodeId();
            }

            @Override
            public String serializeOpportunity(SalesOpportunity o) {
                return "opportunity:" + o.salesOpportunityId();
            }
        };
        marketing = new MarketingService(stores.campaigns, stores.trackingCodes,
            stores.opportunities, (type, key, payload) -> outbox.add(type + ":" + key),
            serializer);
        attribution = new AttributionService(stores.attributions, stores.trackingCodes);
    }

    @Test
    void campaignCrudEmitsCreatedAndUpdated() {
        MarketingCampaign created = marketing.createCampaign("MC-1", "Spring Promo",
            CampaignStatus.PLANNED, LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"));
        assertEquals(CampaignStatus.PLANNED, created.statusId());
        assertTrue(outbox.contains("marketing.campaign.created.v1:MC-1"));

        MarketingCampaign updated = marketing.updateCampaign("MC-1", null, CampaignStatus.ACTIVE,
            null, null);
        assertEquals(CampaignStatus.ACTIVE, updated.statusId());
        assertEquals("Spring Promo", updated.campaignName());
        assertTrue(outbox.contains("marketing.campaign.updated.v1:MC-1"));

        assertEquals(1, marketing.campaigns().size());
        assertEquals("MC-1", marketing.getCampaign("MC-1").orElseThrow().marketingCampaignId());
        assertThrows(NoSuchElementException.class,
            () -> marketing.updateCampaign("MC-404", null, CampaignStatus.ACTIVE, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> marketing.updateCampaign("MC-1", null, CampaignStatus.ACTIVE,
                LocalDate.parse("2026-12-31"), LocalDate.parse("2026-01-01")));
    }

    @Test
    void trackingCodeCrudValidatesCampaignById() {
        marketing.createCampaign("MC-1", "Spring Promo", CampaignStatus.ACTIVE,
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        TrackingCode created = marketing.createTrackingCode("PROMO-42", "MC-1", "spring mail",
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        assertEquals("MC-1", created.marketingCampaignId());
        assertTrue(outbox.contains("marketing.trackingcode.created.v1:PROMO-42"));

        assertThrows(NoSuchElementException.class,
            () -> marketing.createTrackingCode("TC-X", "MC-404", null,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")));

        TrackingCode updated = marketing.updateTrackingCode("PROMO-42", null, "renamed", null,
            null);
        assertEquals("renamed", updated.description());
        assertTrue(outbox.contains("marketing.trackingcode.updated.v1:PROMO-42"));

        // clearing the link (empty body keeps current) — unlink via new id null
        // is not possible through update (null = keep); the guard still checks
        assertThrows(NoSuchElementException.class,
            () -> marketing.updateTrackingCode("PROMO-42", "MC-404", null, null, null));
        assertThrows(NoSuchElementException.class,
            () -> marketing.updateTrackingCode("TC-404", null, null, null, null));
        assertEquals(1, marketing.trackingCodes().size());
        assertEquals("PROMO-42",
            marketing.getTrackingCode("PROMO-42").orElseThrow().trackingCodeId());
    }

    @Test
    void opportunityCrudEmitsCreatedAndUpdated() {
        SalesOpportunity created = marketing.createOpportunity("SO-1", "big deal", "QUALIFIED",
            65);
        assertEquals(65, created.closeProbability());
        assertTrue(outbox.contains("marketing.opportunity.created.v1:SO-1"));

        SalesOpportunity updated = marketing.updateOpportunity("SO-1", null, "WON", 100);
        assertEquals("WON", updated.opportunityStageId());
        assertEquals(100, updated.closeProbability());
        assertEquals("big deal", updated.description());
        assertTrue(outbox.contains("marketing.opportunity.updated.v1:SO-1"));

        assertThrows(NoSuchElementException.class,
            () -> marketing.updateOpportunity("SO-404", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> marketing.updateOpportunity("SO-1", null, null, 101));
        assertEquals(1, marketing.opportunities().size());
        assertEquals("SO-1",
            marketing.getOpportunity("SO-1").orElseThrow().salesOpportunityId());
    }

    @Test
    void attributionSkipsUncodedOrdersAndIsIdempotent() {
        attribution.onOrderPlaced("ORDER-A1", "PROMO-42", new BigDecimal("22.00"),
            Instant.parse("2026-02-01T10:00:00Z"));
        attribution.onOrderPlaced("ORDER-A2", null, new BigDecimal("10.00"), Instant.now());
        attribution.onOrderPlaced("ORDER-A3", "  ", new BigDecimal("10.00"), Instant.now());
        // replay of the same order is a no-op (unique order_id in the store)
        attribution.onOrderPlaced("ORDER-A1", "PROMO-42", new BigDecimal("22.00"),
            Instant.parse("2026-02-01T10:00:00Z"));
        assertEquals(1, stores.attributions.rows.size());
        assertEquals("ORDER-A1", stores.attributions.rows.get(0).orderId());
    }

    @Test
    void reportGroupsByTrackingCodeAndResolvesCampaign() {
        marketing.createCampaign("MC-1", "Spring Promo", CampaignStatus.ACTIVE,
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        marketing.createTrackingCode("PROMO-42", "MC-1", null,
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        attribution.onOrderPlaced("ORDER-B2", "UNKNOWN-9", new BigDecimal("5.00"),
            Instant.now());
        attribution.onOrderPlaced("ORDER-A1", "PROMO-42", new BigDecimal("22.00"),
            Instant.now());
        attribution.onOrderPlaced("ORDER-A2", "PROMO-42", new BigDecimal("10.00"),
            Instant.now());

        List<AttributionService.AttributionReportRow> report = attribution.report();
        assertEquals(2, report.size());
        // ordered by trackingCode: PROMO-42 sorts before UNKNOWN-9
        AttributionService.AttributionReportRow promo = report.get(0);
        assertEquals("PROMO-42", promo.trackingCode());
        assertEquals("MC-1", promo.campaignId());
        assertEquals(2, promo.orderCount());
        assertEquals(0, promo.revenueTotal().compareTo(new BigDecimal("32.00")));
        AttributionService.AttributionReportRow unknown = report.get(1);
        assertEquals("UNKNOWN-9", unknown.trackingCode());
        assertEquals(null, unknown.campaignId(), "unknown codes report a null campaignId");
        assertEquals(1, unknown.orderCount());
    }

    @Test
    void detailIncludesOrderIdsAndThrowsForUnknownCode() {
        attribution.onOrderPlaced("ORDER-A1", "PROMO-42", new BigDecimal("22.00"),
            Instant.parse("2026-02-01T10:00:00Z"));
        AttributionService.AttributionDetail detail = attribution.detail("PROMO-42");
        assertEquals("PROMO-42", detail.trackingCode());
        assertEquals(1, detail.orderCount());
        assertEquals(List.of("ORDER-A1"),
            detail.orders().stream().map(AttributionStore.Attribution::orderId).toList());
        assertThrows(NoSuchElementException.class, () -> attribution.detail("TC-404"));
    }

    /** In-memory fakes for every store port. */
    private static final class FakeStores {
        final FakeCampaignStore campaigns = new FakeCampaignStore();
        final FakeTrackingCodeStore trackingCodes = new FakeTrackingCodeStore();
        final FakeOpportunityStore opportunities = new FakeOpportunityStore();
        final FakeAttributionStore attributions = new FakeAttributionStore();
    }

    static final class FakeCampaignStore implements MarketingCampaignStore {
        final List<MarketingCampaign> rows = new ArrayList<>();

        @Override
        public Optional<MarketingCampaign> find(String marketingCampaignId) {
            return rows.stream().filter(c -> c.marketingCampaignId().equals(marketingCampaignId))
                .findFirst();
        }

        @Override
        public void save(MarketingCampaign campaign) {
            rows.removeIf(c -> c.marketingCampaignId().equals(campaign.marketingCampaignId()));
            rows.add(campaign);
        }

        @Override
        public List<MarketingCampaign> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeTrackingCodeStore implements TrackingCodeStore {
        final List<TrackingCode> rows = new ArrayList<>();

        @Override
        public Optional<TrackingCode> find(String trackingCodeId) {
            return rows.stream().filter(t -> t.trackingCodeId().equals(trackingCodeId))
                .findFirst();
        }

        @Override
        public void save(TrackingCode trackingCode) {
            rows.removeIf(t -> t.trackingCodeId().equals(trackingCode.trackingCodeId()));
            rows.add(trackingCode);
        }

        @Override
        public List<TrackingCode> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeOpportunityStore implements SalesOpportunityStore {
        final List<SalesOpportunity> rows = new ArrayList<>();

        @Override
        public Optional<SalesOpportunity> find(String salesOpportunityId) {
            return rows.stream().filter(o -> o.salesOpportunityId().equals(salesOpportunityId))
                .findFirst();
        }

        @Override
        public void save(SalesOpportunity opportunity) {
            rows.removeIf(o -> o.salesOpportunityId().equals(opportunity.salesOpportunityId()));
            rows.add(opportunity);
        }

        @Override
        public List<SalesOpportunity> all() {
            return List.copyOf(rows);
        }
    }

    /** Mimics the unique-order_id insert: duplicates are silently skipped. */
    static final class FakeAttributionStore implements AttributionStore {
        final List<Attribution> rows = new ArrayList<>();

        @Override
        public void record(Attribution attribution) {
            if (rows.stream().noneMatch(a -> a.orderId().equals(attribution.orderId()))) {
                rows.add(attribution);
            }
        }

        @Override
        public List<Attribution> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<Attribution> findByTrackingCode(String trackingCode) {
            return rows.stream().filter(a -> a.trackingCode().equals(trackingCode)).toList();
        }
    }
}
