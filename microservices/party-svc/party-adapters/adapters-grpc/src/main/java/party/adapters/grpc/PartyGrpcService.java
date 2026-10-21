package party.adapters.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import party.application.PartyService;
import party.domain.Party;
import party.domain.PartyGroup;
import party.domain.PartyKind;
import party.domain.Person;
import party.v1.PartyApiGrpc.PartyApiImplBase;
import party.v1.PartyOuterClass.GetPartyRequest;
import party.v1.PartyOuterClass.PartyResponse;
import party.v1.PartyOuterClass.UpsertPartyRequest;

/** gRPC adapter: translates proto messages to/from the application layer. */
public class PartyGrpcService extends PartyApiImplBase {

    private final PartyService partyService;

    public PartyGrpcService(PartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public void getParty(GetPartyRequest request, StreamObserver<PartyResponse> responseObserver) {
        Optional<Party> found = partyService.get(request.getPartyId());
        if (found.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("no party " + request.getPartyId()).asException());
            return;
        }
        responseObserver.onNext(toResponse(found.get()));
        responseObserver.onCompleted();
    }

    @Override
    public void upsertParty(UpsertPartyRequest request,
            StreamObserver<PartyResponse> responseObserver) {
        try {
            Party party = toDomain(request);
            request.getRolesList().forEach(party::grantRole);
            partyService.upsert(party);
            responseObserver.onNext(toResponse(party));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage()).asException());
        }
    }

    private Party toDomain(UpsertPartyRequest request) {
        PartyKind kind = PartyKind.valueOf(request.getKind());
        if (kind == PartyKind.PERSON) {
            return Party.person(request.getPartyId(),
                new Person(request.getFirstName(), request.getLastName()));
        }
        return Party.group(request.getPartyId(), new PartyGroup(request.getGroupName()));
    }

    private PartyResponse toResponse(Party party) {
        String displayName = party.kind() == PartyKind.PERSON
            ? party.person().displayName()
            : party.group().groupName();
        return PartyResponse.newBuilder()
            .setPartyId(party.partyId())
            .setKind(party.kind().name())
            .setDisplayName(displayName)
            .addAllRoles(party.roles())
            .build();
    }
}
