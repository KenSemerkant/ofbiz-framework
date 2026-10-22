package identity.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import identity.adapters.rest.events.OutboxRelay;
import identity.adapters.rest.persistence.OutboxEntryEntity;
import identity.adapters.rest.persistence.OutboxRepository;
import identity.adapters.rest.persistence.PartyProjectionEntity;
import identity.adapters.rest.persistence.ProcessedEventEntity;
import identity.adapters.rest.persistence.UserLoginEntity;
import identity.adapters.rest.security.JpaUserDetailsService;
import identity.adapters.rest.security.OfbizPasswordHasher;
import identity.domain.PasswordHasher;

/**
 * Unit coverage for identity adapter paths the container tests don't reach:
 * the outbox relay, the monolith HashCrypt hasher's rejection branches, the
 * UserDetailsService lookup failure and the JPA entity accessors.
 */
class IdentityAdaptersUnitTest {

    @Test
    void outboxRelayPublishesThenMarks() {
        boolean[] drained = {false};
        List<String> sent = new ArrayList<>();
        OutboxRepository repo = proxy(OutboxRepository.class, (p, method) -> {
            switch (method.getName()) {
                case "findUnpublished" -> {
                    return drained[0] ? List.of()
                        : List.of(new OutboxEntryEntity("audit.auditevent.recorded.v1", "{}"));
                }
                case "findById" -> {
                    OutboxEntryEntity e = new OutboxEntryEntity("t", "{}");
                    e.markPublished(Instant.now());
                    drained[0] = true;
                    return Optional.of(e);
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
                sent.add(topic);
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
        relay.publishPending();
        assertEquals(List.of("audit.auditevent.recorded.v1"), sent);
    }

    @Test
    void hasherMatchesOnlyCorrectCredentialsAndKnownFormats() {
        PasswordHasher hasher = new OfbizPasswordHasher();
        String hash = hasher.hash("secret");
        assertTrue(hasher.matches("secret", hash));
        org.junit.jupiter.api.Assertions.assertFalse(hasher.matches("wrong", hash));
        // unknown / malformed stored formats verify to false, never throw
        org.junit.jupiter.api.Assertions.assertFalse(hasher.matches("secret", "not-a-format"));
        org.junit.jupiter.api.Assertions.assertFalse(
            hasher.matches("secret", "{PBKDF2-SHA256}bad"));
        org.junit.jupiter.api.Assertions.assertFalse(
            hasher.matches("secret", "$BOGUS$s$notb64"));
        org.junit.jupiter.api.Assertions.assertFalse(hasher.matches("secret", null));
    }

    @Test
    void userDetailsServiceFailsForUnknownUser() {
        var service = new JpaUserDetailsService(proxy(
            identity.adapters.rest.persistence.UserLoginRepository.class, (p, method) -> {
                if ("findById".equals(method.getName())) {
                    return Optional.empty();
                }
                return null;
            }));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("ghost"));
    }

    @Test
    void entityAccessors() {
        UserLoginEntity user = new UserLoginEntity(
            "ada", "P1", "h", true, java.util.Set.of("USER", "ADMIN"));
        assertEquals("ada", user.getUserLoginId());
        assertEquals("P1", user.getPartyId());
        assertEquals("h", user.getCurrentPassword());
        assertTrue(user.isEnabled());
        assertEquals(java.util.Set.of("USER", "ADMIN"), user.getRoles());

        PartyProjectionEntity projection = new PartyProjectionEntity("P1", "{}", Instant.now());
        assertEquals("P1", projection.getPartyId());
        assertEquals("{}", projection.getPayload());
        assertNotNull(projection.getUpdatedAt());

    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface,
            java.util.function.BiFunction<Object, java.lang.reflect.Method, Object> handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface},
            (p, method, args) -> handler.apply(p, method));
    }
}
