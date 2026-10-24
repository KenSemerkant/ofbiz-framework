package marketing.adapters.rest;

import org.apache.kafka.common.TopicPartition;
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
 * Retry-then-DLQ, same policy as the other services (P1-F4): 3 attempts,
 * 100ms apart, then {@code <topic>.dlq}. Uncoded orders never get here —
 * the attribution use case skips them by design (P6-T5).
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler marketingErrorHandler(ProducerFactory<Object, Object> producerFactory) {
        KafkaTemplate<Object, Object> dlqTemplate = new KafkaTemplate<>(producerFactory);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dlqTemplate,
            KafkaConfig::dlqDestination);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(100L, 2L));
    }

    /** {@code <topic>.dlq}, partition chosen by the recoverer (-1 = any). */
    static TopicPartition dlqDestination(
            org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception failure) {
        return new TopicPartition(record.topic() + ".dlq", -1);
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
