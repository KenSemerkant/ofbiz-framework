package party.adapters.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import party.application.PartyService;
import party.application.PartyStore;
import party.domain.Party;
import party.v1.PartyApiGrpc;
import party.v1.PartyApiGrpc.PartyApiBlockingStub;
import party.v1.PartyOuterClass.GetPartyRequest;
import party.v1.PartyOuterClass.PartyResponse;
import party.v1.PartyOuterClass.UpsertPartyRequest;

/** AC (P2-F1-S1): upsert/fetch via gRPC against an in-process server. */
class PartyGrpcServiceTest {

    private final Map<String, Party> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private io.grpc.Server server;
    private ManagedChannel channel;
    private PartyApiBlockingStub stub;

    @BeforeEach
    void startServer() throws Exception {
        PartyService service = new PartyService(
            new PartyStore() {
                @Override
                public Optional<Party> find(String partyId) {
                    return Optional.ofNullable(storeData.get(partyId));
                }

                @Override
                public void save(Party party) {
                    storeData.put(party.partyId(), party);
                }

                @Override
                public party.application.PartyPage findByRole(String roleTypeId, int page,
                        int size) {
                    return new party.application.PartyPage(java.util.List.of(), page, size, 0);
                }
            },
            (eventType, aggregateKey, payload) -> outboxEvents.add(eventType),
            party -> "{}");
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new PartyGrpcService(service))
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = PartyApiGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void upsertThenFetchRoundtrip() {
        PartyResponse upserted = stub.upsertParty(UpsertPartyRequest.newBuilder()
            .setPartyId("P1")
            .setKind("PERSON")
            .setFirstName("Ada")
            .setLastName("Lovelace")
            .addRoles("CUSTOMER")
            .build());
        assertEquals("Ada Lovelace", upserted.getDisplayName());

        PartyResponse fetched = stub.getParty(
            GetPartyRequest.newBuilder().setPartyId("P1").build());
        assertEquals("P1", fetched.getPartyId());
        assertEquals(List.of("CUSTOMER"), fetched.getRolesList());
        // created + role.granted for the CUSTOMER role on first upsert
        assertEquals(
            List.of("party.party.created.v1", "party.role.granted.v1"), outboxEvents);
    }

    @Test
    void missingPartyMapsToNotFound() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
            () -> stub.getParty(GetPartyRequest.newBuilder().setPartyId("nope").build()));
        assertEquals(io.grpc.Status.Code.NOT_FOUND, e.getStatus().getCode());
    }

    @Test
    void invalidKindMapsToInvalidArgument() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
            () -> stub.upsertParty(UpsertPartyRequest.newBuilder()
                .setPartyId("P1").setKind("ALIEN").build()));
        assertEquals(io.grpc.Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }
}
