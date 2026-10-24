package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import product.adapters.rest.events.OutboxRelay;
import product.adapters.rest.persistence.CategoryEntity;
import product.adapters.rest.persistence.JacksonCategorySerializer;
import product.adapters.rest.persistence.JacksonProductSerializer;
import product.adapters.rest.persistence.OutboxEntryEntity;
import product.adapters.rest.persistence.OutboxRepository;
import product.adapters.rest.persistence.ProductEntity;
import product.adapters.rest.persistence.ProductJson;
import product.domain.Product;
import product.domain.ProductAssoc;

/**
 * Unit coverage for adapter paths the container tests don't reach: the outbox
 * relay (publish-then-mark), the event payload shape and the JPA entity
 * accessors.
 */
class ProductAdaptersUnitTest {

    @Test
    void outboxRelayPublishesThenMarks() {
        boolean[] drained = {false};
        OutboxRepository outbox = (OutboxRepository) Proxy.newProxyInstance(
            OutboxRepository.class.getClassLoader(), new Class<?>[] {OutboxRepository.class},
            (p, method, args) -> switch (method.getName()) {
                case "findUnpublished" -> drained[0] ? List.of()
                    : List.of(new OutboxEntryEntity("product.product.created.v1", "WIDGET", "{}"));
                case "findById" -> {
                    // the relay marks the fetched row published inside the tx
                    OutboxEntryEntity e = new OutboxEntryEntity("t", "k", "{}");
                    e.markPublished(Instant.now());
                    drained[0] = true;
                    yield java.util.Optional.of(e);
                }
                default -> null;
            });
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = new KafkaTemplate<String, String>(
                new ProducerFactory<String, String>() {
                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String>
                            createProducer() {
                        throw new IllegalStateException("send() is overridden");
                    }

                    @Override
                    public org.apache.kafka.clients.producer.Producer<String, String>
                            createProducer(String txIdPrefix) {
                        throw new IllegalStateException("send() is overridden");
                    }
                }) {
            @Override
            public CompletableFuture<SendResult<String, String>> send(String topic, String key,
                    String value) {
                return CompletableFuture.completedFuture(null);
            }
        };
        TransactionTemplate transactions = new TransactionTemplate(noopTransactionManager());
        new OutboxRelay(outbox, kafka, transactions).publishPending();
        assertTrue(drained[0], "relay should have drained the pending row");
    }

    private static PlatformTransactionManager noopTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    @Test
    void serializerPayloadShape() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "Widget 1.0", "Widget",
            java.util.Set.of("TOOLS"), List.of());
        product.associateWith(
            new ProductAssoc("GIZMO", "PRODUCT_VARIANT", Instant.parse("2026-01-01T00:00:00Z"),
                null));
        String json = new JacksonProductSerializer().serialize(product);
        assertTrue(json.contains("\"productId\":\"WIDGET\""));
        assertTrue(json.contains("\"productTypeId\":\"FINISHED_GOOD\""));
        assertTrue(json.contains("\"categories\":[\"TOOLS\"]"));
        assertTrue(json.contains("\"productIdTo\":\"GIZMO\""));
    }

    @Test
    void serializerPayloadShapeIncludesPrices() {
        Product product = new Product("WIDGET", "FINISHED_GOOD", "Widget", null, null, null);
        product.addPrice(new product.domain.ProductPrice("DEFAULT_PRICE", "USD",
            Instant.parse("2026-01-01T00:00:00Z"), null, new java.math.BigDecimal("10.00")));
        String json = new JacksonProductSerializer().serialize(product);
        assertTrue(json.contains("\"productPriceTypeId\":\"DEFAULT_PRICE\""));
        assertTrue(json.contains("\"currencyUomId\":\"USD\""));
        assertTrue(json.contains("\"price\":10.00"));
    }

    @Test
    void serializerPayloadsEscapeHostileFields() {
        JacksonProductSerializer serializer = new JacksonProductSerializer();
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        // every payload must parse and round-trip the hostile value
        assertEquals("W\"Q\\T", mapper.readTree(serializer.serializeDeleted("W\"Q\\T"))
            .path("productId").asText());
        var priceChange = mapper.readTree(serializer.serializePriceChange("W\"Q\\T",
            new product.domain.ProductPrice("TY\"PE", "US\\D",
                Instant.parse("2026-01-01T00:00:00Z"), null, new java.math.BigDecimal("1.00")),
            true));
        assertEquals("TY\"PE", priceChange.path("productPriceTypeId").asText());
        assertEquals("US\\D", priceChange.path("currencyUomId").asText());
        assertTrue(priceChange.path("removed").asBoolean());

        assertEquals("Sharp \"Tools\" \\ Best",
            mapper.readTree(new JacksonCategorySerializer().serialize(
                new product.domain.Category("CAT\"Q", null, "Sharp \"Tools\" \\ Best")))
                .path("categoryName").asText());
    }

    @Test
    void productJsonRoundtrip() {
        List<ProductAssoc> assocs = List.of(
            new ProductAssoc("GIZMO", "PRODUCT_VARIANT",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z")));
        assertEquals(List.of("TOOLS", "FASTENERS"),
            ProductJson.deserializeCategories(ProductJson.serializeCategories(
                List.of("TOOLS", "FASTENERS"))));
        assertEquals(assocs, ProductJson.deserializeAssocs(ProductJson.serializeAssocs(assocs)));
        List<product.domain.ProductPrice> prices = List.of(
            new product.domain.ProductPrice("DEFAULT_PRICE", "USD",
                Instant.parse("2026-01-01T00:00:00Z"), null, new java.math.BigDecimal("10.00")));
        assertEquals(prices, ProductJson.deserializePrices(ProductJson.serializePrices(prices)));
        assertEquals(List.of(), ProductJson.deserializeCategories(
            ProductJson.serializeCategories(null)));
    }

    @Test
    void entityAccessors() {
        ProductEntity product = new ProductEntity("WIDGET", "FINISHED_GOOD", "Widget", "W",
            "[]", "[]", "[]");
        assertEquals("WIDGET", product.getProductId());
        assertEquals("FINISHED_GOOD", product.getProductTypeId());
        assertEquals("Widget", product.getInternalName());
        assertEquals("W", product.getProductName());
        assertEquals("[]", product.getCategories());
        assertEquals("[]", product.getAssocs());
        assertEquals("[]", product.getPrices());
        assertTrue(product.getUpdatedAt() != null);

        CategoryEntity category = new CategoryEntity("TOOLS", "ROOT", "Tools");
        assertEquals("TOOLS", category.getProductCategoryId());
        assertEquals("ROOT", category.getPrimaryParentCategoryId());
        assertEquals("Tools", category.getCategoryName());
        assertTrue(category.getUpdatedAt() != null);

        OutboxEntryEntity entry = new OutboxEntryEntity("t", "k", "{}");
        assertEquals("t", entry.getEventType());
        assertEquals("k", entry.getAggregateKey());
        assertEquals("{}", entry.getPayload());
        assertTrue(entry.getOccurredAt() != null);
        assertTrue(entry.getPublishedAt() == null);
        entry.markPublished(Instant.now());
        assertTrue(entry.getPublishedAt() != null);
        assertTrue(entry.getId() == null);
        assertTrue(new ProductEntity().getProductId() == null);
        assertTrue(new CategoryEntity().getProductCategoryId() == null);
        assertTrue(new OutboxEntryEntity().getEventType() == null);
    }
}
