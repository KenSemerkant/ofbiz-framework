package party.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import party.adapters.rest.config.MonolithSyncConfig;
import party.adapters.rest.events.OutboxRelay;
import party.adapters.rest.events.ReverseSyncConsumer;
import party.adapters.rest.persistence.ContactMechJson;
import party.adapters.rest.persistence.JacksonPartySerializer;
import party.adapters.rest.persistence.MonolithPartyEntity;
import party.adapters.rest.persistence.OutboxEntryEntity;
import party.adapters.rest.persistence.OutboxRepository;
import party.adapters.rest.persistence.PartyEntity;
import party.application.ReverseSyncService;
import party.domain.Party;
import party.domain.PartyGroup;
import party.domain.Person;

/**
 * Unit coverage for adapter paths the container tests don't reach: the outbox
 * relay (publish-then-mark), the reverse-sync envelope parsing, the
 * conditional monolith datasource config, the event serializer's group shape
 * and the JPA entity accessors.
 */
class PartyAdaptersUnitTest {

    @Test
    void outboxRelayPublishesThenMarks() {
        boolean[] drained = {false};
        List<String> sent = new ArrayList<>();
        OutboxRepository repo = proxy(OutboxRepository.class, (p, method) -> {
            switch (method.getName()) {
                case "findUnpublished" -> {
                    return drained[0] ? List.of()
                        : List.of(new OutboxEntryEntity("party.party.created.v1", "P1", "{}"));
                }
                case "findById" -> {
                    // the relay marks the fetched row published inside the tx
                    OutboxEntryEntity e = new OutboxEntryEntity("t", "k", "{}");
                    e.markPublished(Instant.now());
                    drained[0] = true;
                    return java.util.Optional.of(e);
                }
                default -> {
                    return null;
                }
            }
        });
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = new KafkaTemplate<String, String>(
                new org.springframework.kafka.core.ProducerFactory<String, String>() {
                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String> createProducer() {
                        throw new IllegalStateException("send() is overridden");
                    }

                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String> createProducer(
                            String txIdPrefix) {
                        throw new IllegalStateException("send() is overridden");
                    }
                }) {
            @Override
            public CompletableFuture<SendResult<String, String>> send(
                    String topic, String key, String value) {
                sent.add(topic + "|" + key);
                return CompletableFuture.completedFuture(null);
            }
        };
        TransactionTemplate tx = new TransactionTemplate() {
            @Override
            public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) {
                action.accept(null);
            }
        };
        OutboxRelay relay = new OutboxRelay(repo, kafka, tx);
        relay.publishPending();
        relay.publishPending(); // nothing pending anymore
        assertEquals(List.of("party.party.created.v1|P1"), sent);
    }

    @Test
    void reverseSyncConsumerParsesEnvelope() {
        List<ReverseSyncService.PartySnapshot> seen = new ArrayList<>();
        ReverseSyncConsumer consumer = new ReverseSyncConsumer(
            new ReverseSyncService((partyId, kind, first, last, group, roles) ->
                seen.add(new ReverseSyncService.PartySnapshot(partyId, kind, first, last, group, roles))));
        consumer.onPartyEvent("""
            {"partyId":"P1","kind":"PERSON","firstName":"Ada","lastName":"King",
             "roles":["CUSTOMER","SUPPLIER"]}""");
        assertEquals("P1", seen.get(0).partyId());
        assertEquals(Set.of("CUSTOMER", "SUPPLIER"), seen.get(0).roles());
        consumer.onPartyEvent("{\"partyId\":\"G1\",\"kind\":\"PARTY_GROUP\",\"groupName\":\"Acme\"}");
        assertEquals("Acme", seen.get(1).groupName());
    }

    @Test
    void monolithSyncConfigBuildsBeans() {
        MonolithSyncConfig config = new MonolithSyncConfig();
        DataSource ds = config.partyDataSource("jdbc:postgresql://localhost/x", "u", "p");
        assertNotNull(ds);
        JdbcTemplate jdbc = config.monolithJdbc(ds);
        assertNotNull(jdbc);
        assertNotNull(config.monolithSyncWriter(jdbc));
        assertNotNull(config.reverseSyncService((partyId, kind, first, last, group, roles) -> { }));
    }

    @Test
    void serializerCoversBothKindsAndEntityAccessors() {
        var serializer = new JacksonPartySerializer();
        assertTrue(serializer.serialize(Party.person("P1", new Person("Ada", "Lovelace")))
            .contains("\"firstName\":\"Ada\""));
        assertTrue(serializer.serialize(Party.group("G1", new PartyGroup("Acme")))
            .contains("\"groupName\":\"Acme\""));

        PartyEntity party = new PartyEntity("P1", "PERSON", "A", "B", null, Set.of("R"), "[]");
        assertEquals("P1", party.getPartyId());
        assertEquals("PERSON", party.getKind());
        assertEquals("A", party.getFirstName());
        assertEquals("B", party.getLastName());
        assertTrue(party.getRoles().contains("R"));
        assertNotNull(party.getUpdatedAt());

        MonolithPartyEntity mono = new MonolithPartyEntity("M1");
        mono.setPartyTypeId("PERSON");
        mono.setFirstName("F");
        mono.setLastName("L");
        mono.setGroupName("G");
        assertEquals("M1", mono.getPartyId());
        assertEquals("PERSON", mono.getPartyTypeId());
        assertEquals("F", mono.getFirstName());
        assertEquals("L", mono.getLastName());
        assertEquals("G", mono.getGroupName());

        OutboxEntryEntity outbox = new OutboxEntryEntity("t", "k", "{}");
        outbox.markPublished(Instant.now());
        assertEquals("t", outbox.getEventType());
        assertEquals("k", outbox.getAggregateKey());
        assertEquals("{}", outbox.getPayload());
        assertNotNull(outbox.getPublishedAt());
        assertEquals("[]", ContactMechJson.serialize(List.of()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface,
            java.util.function.BiFunction<Object, java.lang.reflect.Method, Object> handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface},
            (p, method, args) -> handler.apply(p, method));
    }
}
