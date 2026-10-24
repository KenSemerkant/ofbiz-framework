package marketing.adapters.rest.api;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import marketing.application.AttributionService;
import marketing.application.MarketingService;
import marketing.domain.CampaignStatus;
import marketing.domain.MarketingCampaign;
import marketing.domain.SalesOpportunity;
import marketing.domain.TrackingCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P6-T5 REST edge: /api/v1 — campaigns, tracking codes, opportunities and
 * the attribution report. Guard violations map to typed 409s; missing
 * aggregates map to 404s; mutating endpoints run in one adapter-layer tx so
 * the aggregate + outbox row commit together.
 */
@RestController
@RequestMapping("/api/v1")
public class MarketingController {

    private final MarketingService marketing;
    private final AttributionService attribution;

    public MarketingController(MarketingService marketing, AttributionService attribution) {
        this.marketing = marketing;
        this.attribution = attribution;
    }

    public record CreateCampaignRequest(String marketingCampaignId, String campaignName,
            String statusId, String fromDate, String thruDate) {
    }

    public record UpdateCampaignRequest(String campaignName, String statusId, String fromDate,
            String thruDate) {
    }

    public record CampaignResponse(String marketingCampaignId, String campaignName,
            String statusId, String fromDate, String thruDate) {
    }

    public record CreateTrackingCodeRequest(String trackingCodeId, String marketingCampaignId,
            String description, String fromDate, String thruDate) {
    }

    public record UpdateTrackingCodeRequest(String marketingCampaignId, String description,
            String fromDate, String thruDate) {
    }

    public record TrackingCodeResponse(String trackingCodeId, String marketingCampaignId,
            String description, String fromDate, String thruDate) {
    }

    public record CreateOpportunityRequest(String salesOpportunityId, String description,
            String opportunityStageId, Integer closeProbability) {
    }

    public record UpdateOpportunityRequest(String description, String opportunityStageId,
            Integer closeProbability) {
    }

    public record OpportunityResponse(String salesOpportunityId, String description,
            String opportunityStageId, int closeProbability) {
    }

    @PostMapping("/campaigns")
    @Transactional
    ResponseEntity<CampaignResponse> createCampaign(@RequestBody CreateCampaignRequest request) {
        return guarded(() -> ResponseEntity.status(HttpStatus.CREATED).body(toCampaign(
            marketing.createCampaign(request.marketingCampaignId(), request.campaignName(),
                CampaignStatus.valueOf(request.statusId()),
                LocalDate.parse(request.fromDate()), LocalDate.parse(request.thruDate())))));
    }

    @GetMapping("/campaigns")
    List<CampaignResponse> campaigns() {
        return marketing.campaigns().stream().map(this::toCampaign).toList();
    }

    @GetMapping("/campaigns/{marketingCampaignId}")
    ResponseEntity<CampaignResponse> getCampaign(@PathVariable String marketingCampaignId) {
        return marketing.getCampaign(marketingCampaignId).map(this::toCampaign)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/campaigns/{marketingCampaignId}")
    @Transactional
    ResponseEntity<CampaignResponse> updateCampaign(@PathVariable String marketingCampaignId,
            @RequestBody UpdateCampaignRequest request) {
        return guarded(() -> ResponseEntity.ok(toCampaign(marketing.updateCampaign(
            marketingCampaignId, request.campaignName(), statusOrNull(request.statusId()),
            dateOrNull(request.fromDate()), dateOrNull(request.thruDate())))));
    }

    @PostMapping("/tracking-codes")
    @Transactional
    ResponseEntity<TrackingCodeResponse> createTrackingCode(
            @RequestBody CreateTrackingCodeRequest request) {
        return guarded(() -> ResponseEntity.status(HttpStatus.CREATED).body(toTrackingCode(
            marketing.createTrackingCode(request.trackingCodeId(),
                request.marketingCampaignId(), request.description(),
                LocalDate.parse(request.fromDate()), LocalDate.parse(request.thruDate())))));
    }

    @GetMapping("/tracking-codes")
    List<TrackingCodeResponse> trackingCodes() {
        return marketing.trackingCodes().stream().map(this::toTrackingCode).toList();
    }

    @GetMapping("/tracking-codes/{trackingCodeId}")
    ResponseEntity<TrackingCodeResponse> getTrackingCode(@PathVariable String trackingCodeId) {
        return marketing.getTrackingCode(trackingCodeId).map(this::toTrackingCode)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/tracking-codes/{trackingCodeId}")
    @Transactional
    ResponseEntity<TrackingCodeResponse> updateTrackingCode(@PathVariable String trackingCodeId,
            @RequestBody UpdateTrackingCodeRequest request) {
        return guarded(() -> ResponseEntity.ok(toTrackingCode(marketing.updateTrackingCode(
            trackingCodeId, request.marketingCampaignId(), request.description(),
            dateOrNull(request.fromDate()), dateOrNull(request.thruDate())))));
    }

    @PostMapping("/opportunities")
    @Transactional
    ResponseEntity<OpportunityResponse> createOpportunity(
            @RequestBody CreateOpportunityRequest request) {
        return guarded(() -> ResponseEntity.status(HttpStatus.CREATED).body(toOpportunity(
            marketing.createOpportunity(request.salesOpportunityId(), request.description(),
                request.opportunityStageId(), request.closeProbability()))));
    }

    @GetMapping("/opportunities")
    List<OpportunityResponse> opportunities() {
        return marketing.opportunities().stream().map(this::toOpportunity).toList();
    }

    @GetMapping("/opportunities/{salesOpportunityId}")
    ResponseEntity<OpportunityResponse> getOpportunity(
            @PathVariable String salesOpportunityId) {
        return marketing.getOpportunity(salesOpportunityId).map(this::toOpportunity)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/opportunities/{salesOpportunityId}")
    @Transactional
    ResponseEntity<OpportunityResponse> updateOpportunity(
            @PathVariable String salesOpportunityId,
            @RequestBody UpdateOpportunityRequest request) {
        return guarded(() -> ResponseEntity.ok(toOpportunity(marketing.updateOpportunity(
            salesOpportunityId, request.description(), request.opportunityStageId(),
            request.closeProbability()))));
    }

    /** P6-T5 CG report: per-tracking-code order count and revenue. */
    @GetMapping("/attribution")
    List<AttributionService.AttributionReportRow> attributionReport() {
        return attribution.report();
    }

    @GetMapping("/attribution/{trackingCode}")
    AttributionService.AttributionDetail attributionDetail(
            @PathVariable String trackingCode) {
        return attribution.detail(trackingCode);
    }

    private CampaignResponse toCampaign(MarketingCampaign campaign) {
        return new CampaignResponse(campaign.marketingCampaignId(), campaign.campaignName(),
            campaign.statusId().name(), campaign.fromDate().toString(),
            campaign.thruDate().toString());
    }

    private TrackingCodeResponse toTrackingCode(TrackingCode trackingCode) {
        return new TrackingCodeResponse(trackingCode.trackingCodeId(),
            trackingCode.marketingCampaignId(), trackingCode.description(),
            trackingCode.fromDate().toString(), trackingCode.thruDate().toString());
    }

    private OpportunityResponse toOpportunity(SalesOpportunity opportunity) {
        return new OpportunityResponse(opportunity.salesOpportunityId(),
            opportunity.description(), opportunity.opportunityStageId(),
            opportunity.closeProbability());
    }

    private static CampaignStatus statusOrNull(String statusId) {
        return statusId != null ? CampaignStatus.valueOf(statusId) : null;
    }

    private static LocalDate dateOrNull(String date) {
        return date != null ? LocalDate.parse(date) : null;
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<ResponseEntity<T>> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
