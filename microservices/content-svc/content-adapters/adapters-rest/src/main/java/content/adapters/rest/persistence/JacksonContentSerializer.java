package content.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import content.application.ContentSerializer;
import content.domain.Content;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the content.* topics (Jackson 3). */
@Component
public class JacksonContentSerializer implements ContentSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serialize(Content content) {
        ObjectNode node = json.createObjectNode();
        node.put("contentId", content.contentId());
        node.put("contentTypeId", content.contentTypeId().name());
        node.put("contentName", content.contentName());
        node.put("statusId", content.statusId().name());
        if (content.s3Key() != null) {
            node.put("s3Key", content.s3Key());
        }
        if (content.contentType() != null) {
            node.put("contentType", content.contentType());
        }
        if (content.sizeBytes() != null) {
            node.put("sizeBytes", content.sizeBytes());
        }
        return node.toString();
    }
}
