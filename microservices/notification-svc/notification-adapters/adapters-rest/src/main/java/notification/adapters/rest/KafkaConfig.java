package notification.adapters.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry-then-DLQ (P1-F4-S1 AC b): 3 attempts total, 100ms apart, then the
 * DeadLetterPublishingRecoverer republishes to notification.requested.v1.dlq
 * (default destination = source topic + ".dlq") and the offset is committed.
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler notificationErrorHandler(ProducerFactory<Object, Object> producerFactory) {
        KafkaTemplate<Object, Object> dlqTemplate = new KafkaTemplate<>(producerFactory);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlqTemplate,
            (record, failure) -> new org.apache.kafka.common.TopicPartition(
                record.topic() + ".dlq", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(100L, 2L));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
