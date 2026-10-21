package party.adapters.rest.api;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import party.application.PartyService;
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
            String lastName, String groupName, Set<String> roles) {
    }

    public record PartyResponse(String partyId, String kind, String displayName,
            Set<String> roles) {
    }

    @PostMapping
    public ResponseEntity<PartyResponse> upsert(@RequestBody UpsertPartyRequest request) {
        PartyKind kind = PartyKind.valueOf(request.kind());
        Party party = kind == PartyKind.PERSON
            ? Party.person(request.partyId(), new Person(request.firstName(), request.lastName()))
            : Party.group(request.partyId(), new PartyGroup(request.groupName()));
        if (request.roles() != null) {
            request.roles().forEach(party::grantRole);
        }
        partyService.upsert(party);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(party));
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
            party.roles());
    }
}
