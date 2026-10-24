package marketing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * P6-T5 guard tests: the probability bounds and the date-window guards are
 * the invariants that make the report trustworthy — an out-of-range
 * probability or an inverted date window is wrong by definition.
 */
class MarketingDomainGuardTest {

    private static final LocalDate FROM = LocalDate.parse("2026-01-01");
    private static final LocalDate THRU = LocalDate.parse("2026-12-31");

    @Test
    void campaignConstructionAndGetters() {
        MarketingCampaign campaign = new MarketingCampaign("MC-1", "Spring Promo",
            CampaignStatus.PLANNED, FROM, THRU);
        assertEquals("MC-1", campaign.marketingCampaignId());
        assertEquals("Spring Promo", campaign.campaignName());
        assertEquals(CampaignStatus.PLANNED, campaign.statusId());
        assertEquals(FROM, campaign.fromDate());
        assertEquals(THRU, campaign.thruDate());
    }

    @Test
    void campaignGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign(" ", "n", CampaignStatus.PLANNED, FROM, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign("MC", "", CampaignStatus.PLANNED, FROM, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign("MC", "n", null, FROM, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign("MC", "n", CampaignStatus.ACTIVE, THRU, FROM));
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign("MC", "n", CampaignStatus.ACTIVE, null, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new MarketingCampaign("MC", "n", CampaignStatus.ACTIVE, FROM, null));
        assertEquals(3, CampaignStatus.values().length);
    }

    @Test
    void trackingCodeConstructionAndGetters() {
        TrackingCode code = new TrackingCode("PROMO-42", "MC-1", "spring mail", FROM, THRU);
        assertEquals("PROMO-42", code.trackingCodeId());
        assertEquals("MC-1", code.marketingCampaignId());
        assertEquals("spring mail", code.description());
        assertEquals(FROM, code.fromDate());
        assertEquals(THRU, code.thruDate());
        TrackingCode uncoded = new TrackingCode("AFF-1", null, null, FROM, THRU);
        assertNull(uncoded.marketingCampaignId());
        assertNull(uncoded.description());
    }

    @Test
    void trackingCodeGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new TrackingCode("", null, null, FROM, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new TrackingCode("TC", null, null, THRU, FROM));
        assertThrows(IllegalArgumentException.class,
            () -> new TrackingCode("TC", null, null, null, THRU));
        assertThrows(IllegalArgumentException.class,
            () -> new TrackingCode("TC", null, null, FROM, null));
    }

    @Test
    void opportunityConstructionAndGetters() {
        SalesOpportunity opportunity = new SalesOpportunity("SO-1", "big deal",
            "QUALIFIED", 65);
        assertEquals("SO-1", opportunity.salesOpportunityId());
        assertEquals("big deal", opportunity.description());
        assertEquals("QUALIFIED", opportunity.opportunityStageId());
        assertEquals(65, opportunity.closeProbability());
    }

    @Test
    void opportunityProbabilityBounds() {
        new SalesOpportunity("SO-0", null, "QUALIFIED", 0);
        new SalesOpportunity("SO-100", null, "QUALIFIED", 100);
        assertThrows(IllegalArgumentException.class,
            () -> new SalesOpportunity("SO-1", null, "QUALIFIED", -1));
        assertThrows(IllegalArgumentException.class,
            () -> new SalesOpportunity("SO-1", null, "QUALIFIED", 101));
        assertThrows(IllegalArgumentException.class,
            () -> new SalesOpportunity(" ", null, "QUALIFIED", 50));
        assertThrows(IllegalArgumentException.class,
            () -> new SalesOpportunity("SO-1", null, " ", 50));
    }
}
