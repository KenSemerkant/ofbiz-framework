package content.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import content.application.ContentSerializer;
import content.application.ContentService;
import content.application.ContentStore;
import content.application.ObjectStore;
import content.application.OutboxWriter;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class ContentBeans {

    @Bean
    ContentService contentService(ContentStore contents, ObjectStore objects,
            OutboxWriter outbox, ContentSerializer serializer) {
        return new ContentService(contents, objects, outbox, serializer);
    }
}
