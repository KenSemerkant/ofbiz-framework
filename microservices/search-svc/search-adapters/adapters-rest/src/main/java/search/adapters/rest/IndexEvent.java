package search.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Payload of {@code search.doc.indexed.v1}: the docId is the index id
 * (last-write-wins keeps consumption idempotent), fields become the source.
 */
public record IndexEvent(String docId, String index, Map<String, String> fields) {

    public static IndexEvent parse(String payload, ObjectMapper mapper) {
        try {
            return mapper.readValue(payload, IndexEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad search.doc.indexed payload: " + payload, e);
        }
    }
}
