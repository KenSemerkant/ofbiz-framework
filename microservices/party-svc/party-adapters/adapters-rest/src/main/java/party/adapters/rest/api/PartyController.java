package party.adapters.rest.api;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import party.adapters.rest.persistence.ContactMechJson;
import party.application.PartyService;
import party.domain.ContactMech;
import party.domain.ContactMechPurpose;
import party.domain.ContactMechType;
import party.domain.Party;
import party.domain.PartyGroup;
import party.domain.PartyKind;
import party.domain.Person;

/** North-south REST edge (ADR-010): only the gateway routes here. */
@RestController
@RequestMapping("/api/v1/parties")
public class PartyController {

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    public record UpsertPartyRequest(String partyId, String kind, String firstName,
            String lastName, String groupName, Set<String> roles,
            List<ContactMechJson.MechDto> contactMechs) {
    }

    // ponytail: contactMechs rides as the same JSON ContactMechJson emits for
    // the entity column and the event payload — one shape, one mapper
    public record PartyResponse(String partyId, String kind, String displayName,
            Set<String> roles, tools.jackson.databind.JsonNode contactMechs) {
    }

    @PostMapping
    public ResponseEntity<PartyResponse> upsert(@RequestBody UpsertPartyRequest request) {
        Party party;
        try {
            PartyKind kind = PartyKind.valueOf(request.kind());
            party = kind == PartyKind.PERSON
                ? Party.person(request.partyId(),
                    new Person(request.firstName(), request.lastName()))
                : Party.group(request.partyId(), new PartyGroup(request.groupName()));
            if (request.roles() != null) {
                request.roles().forEach(party::grantRole);
            }
            if (request.contactMechs() != null) {
                for (var mech : request.contactMechs) {
                    party.linkContactMech(new ContactMech(mech.contactMechId(),
                        ContactMechType.valueOf(mech.type()), mech.value()),
                        mech.purposes() == null ? Set.of()
                            : mech.purposes().stream().map(ContactMechPurpose::valueOf)
                                .collect(java.util.stream.Collectors.toSet()));
                }
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        partyService.upsert(party);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(party));
    }

    /** P2-F1-S3 paged role lookup; P9-F4: role optional = list all parties. */
    @GetMapping
    public ResponseEntity<?> findByRole(@RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            return ResponseEntity.badRequest().body("size must be between 1 and 100");
        }
        party.application.PartyPage result = role == null || role.isBlank()
            ? partyService.allPaged(page, size)
            : partyService.findByRole(role, page, size);
        return ResponseEntity.ok(java.util.Map.of(
            "items", result.items(),
            "page", result.page(),
            "size", result.size(),
            "totalElements", result.totalElements(),
            "totalPages", result.totalPages()));
    }

    @GetMapping("/{partyId}")
    public ResponseEntity<PartyResponse> get(@PathVariable String partyId) {
        return partyService.get(partyId)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    private PartyResponse toResponse(Party party) {
        String displayName = party.kind() == PartyKind.PERSON
            ? party.person().displayName()
            : party.group().groupName();
        return new PartyResponse(party.partyId(), party.kind().name(), displayName,
            party.roles(), ContactMechJson.serializeNode(party.contactMechs()));
    }
}
