package manufacturing.adapters.rest.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import manufacturing.application.BomService;
import manufacturing.application.MrpService;
import manufacturing.application.RoutingService;
import manufacturing.domain.Bom;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpProposal;
import manufacturing.domain.Routing;

/**
 * P6-T1 REST edge: /api/v1/boms, /api/v1/routings, /api/v1/mrp — BOM and
 * routing CRUD, the MRP ledger views, and the deterministic MRP run.
 * Duplicate creates map to typed 409s, missing aggregates to 404s;
 * mutating endpoints run in one adapter-layer tx so the aggregate and its
 * outbox row commit together.
 */
@RestController
public class ManufacturingController {

    private final BomService boms;
    private final RoutingService routings;
    private final MrpService mrp;

    public ManufacturingController(BomService boms, RoutingService routings, MrpService mrp) {
        this.boms = boms;
        this.routings = routings;
        this.mrp = mrp;
    }

    public record ComponentRequest(String productId, BigDecimal quantity, BigDecimal scrapPct) {
    }

    public record SaveBomRequest(String productId, List<ComponentRequest> components) {
    }

    public record TaskRequest(int seq, String name, long estimatedDuration) {
    }

    public record SaveRoutingRequest(String routingId, String productId,
            List<TaskRequest> tasks) {
    }

    public record ComponentResponse(String productId, BigDecimal quantity,
            BigDecimal scrapPct) {
    }

    public record BomResponse(String productId, List<ComponentResponse> components) {
    }

    public record TaskResponse(int seq, String name, long estimatedDuration) {
    }

    public record RoutingResponse(String routingId, String productId,
            List<TaskResponse> tasks) {
    }

    public record MrpEventResponse(String eventId, String productId, String eventType,
            BigDecimal quantity, String occurredAt) {
    }

    public record MrpProposalResponse(String productId, BigDecimal quantity) {
    }

    public record MrpRunResponse(List<MrpProposalResponse> proposals) {
    }

    @PostMapping("/api/v1/boms")
    @Transactional
    ResponseEntity<BomResponse> createBom(@RequestBody SaveBomRequest request) {
        return guarded(HttpStatus.CREATED, () -> toBomBody(
            boms.create(request.productId(), components(request))));
    }

    @GetMapping("/api/v1/boms/{productId}")
    ResponseEntity<BomResponse> getBom(@PathVariable String productId) {
        return boms.get(productId).map(this::toBomBody)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/boms/{productId}")
    @Transactional
    ResponseEntity<BomResponse> updateBom(@PathVariable String productId,
            @RequestBody SaveBomRequest request) {
        return guarded(HttpStatus.OK, () -> toBomBody(
            boms.update(productId, components(request))));
    }

    @DeleteMapping("/api/v1/boms/{productId}")
    @Transactional
    ResponseEntity<Void> deleteBom(@PathVariable String productId) {
        boms.delete(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/routings")
    @Transactional
    ResponseEntity<RoutingResponse> createRouting(@RequestBody SaveRoutingRequest request) {
        return guarded(HttpStatus.CREATED, () -> toRoutingBody(
            routings.create(request.routingId(), request.productId(), tasks(request))));
    }

    @GetMapping("/api/v1/routings/{routingId}")
    ResponseEntity<RoutingResponse> getRouting(@PathVariable String routingId) {
        return routings.get(routingId).map(this::toRoutingBody)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/routings/{routingId}")
    @Transactional
    ResponseEntity<RoutingResponse> updateRouting(@PathVariable String routingId,
            @RequestBody SaveRoutingRequest request) {
        return guarded(HttpStatus.OK, () -> toRoutingBody(
            routings.update(routingId, request.productId(), tasks(request))));
    }

    /** P6-T1 AC: MRP over the ledger produces one proposal per short product. */
    @PostMapping("/api/v1/mrp/run")
    @Transactional
    MrpRunResponse runMrp() {
        List<MrpProposalResponse> proposals = mrp.run().stream()
            .map(p -> new MrpProposalResponse(p.productId(), p.quantity()))
            .toList();
        return new MrpRunResponse(proposals);
    }

    @GetMapping("/api/v1/mrp/events")
    List<MrpEventResponse> mrpEvents() {
        return mrp.events().stream().map(this::toEventResponse).toList();
    }

    @GetMapping("/api/v1/mrp/proposals")
    List<MrpProposalResponse> mrpProposals() {
        return mrp.proposals().stream()
            .map(p -> new MrpProposalResponse(p.productId(), p.quantity()))
            .toList();
    }

    private List<Bom.Component> components(SaveBomRequest request) {
        return request.components().stream()
            .map(c -> new Bom.Component(c.productId(), c.quantity(), c.scrapPct()))
            .toList();
    }

    private List<Routing.Task> tasks(SaveRoutingRequest request) {
        return request.tasks().stream()
            .map(t -> new Routing.Task(t.seq(), t.name(), t.estimatedDuration()))
            .toList();
    }

    private BomResponse toBomBody(Bom bom) {
        return new BomResponse(bom.productId(), bom.components().stream()
            .map(c -> new ComponentResponse(c.productId(), c.quantity(), c.scrapPct()))
            .toList());
    }

    private RoutingResponse toRoutingBody(Routing routing) {
        return new RoutingResponse(routing.routingId(), routing.productId(),
            routing.tasks().stream()
                .map(t -> new TaskResponse(t.seq(), t.name(), t.estimatedDuration()))
                .toList());
    }

    private MrpEventResponse toEventResponse(MrpEvent event) {
        return new MrpEventResponse(event.eventId(), event.productId(),
            event.eventType().name(), event.quantity(), event.occurredAt().toString());
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(HttpStatus success, Supplier<T> body) {
        try {
            return ResponseEntity.status(success).body(body.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
