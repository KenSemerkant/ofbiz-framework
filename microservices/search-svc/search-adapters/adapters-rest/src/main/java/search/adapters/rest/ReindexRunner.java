package search.adapters.rest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.stereotype.Component;
import search.application.SearchService;
import search.domain.SearchDocument;

/**
 * P1-F5-S1 AC c: reindex-from-topic. Drops the index, then replays the
 * whole topic with a one-shot consumer (own group, earliest offsets) through
 * the same IndexEvent mapping as the live listener.
 */
@Component
public class ReindexRunner {

    private final SearchService searchService;
    private final KafkaProperties kafkaProperties;
    private final String topic;

    public ReindexRunner(SearchService searchService, KafkaProperties kafkaProperties,
            @Value("${search.topic}") String topic) {
        this.searchService = searchService;
        this.kafkaProperties = kafkaProperties;
        this.topic = topic;
    }

    public synchronized int reindex() {
        searchService.recreate(SearchDocument.DEFAULT_INDEX);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        int indexed = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
            if (partitions.isEmpty()) {
                return 0;
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            while (!partitions.stream().allMatch(p -> consumer.position(p) >= endOffsets.get(p))) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    try {
                        IndexEvent event = IndexEvent.parse(record.value(), mapper);
                        searchService.index(
                            new SearchDocument(event.docId(), event.index(), event.fields(), null));
                        indexed++;
                    } catch (IllegalArgumentException e) {
                        // malformed events were dead-lettered by the live
                        // listener; reindex skips them and moves on
                    }
                }
            }
        }
        return indexed;
    }

    private Properties consumerProps() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        props.put("group.id", "search-reindex-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        Properties properties = new Properties();
        properties.putAll(props);
        return properties;
    }
}
