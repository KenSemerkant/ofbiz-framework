package content.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import content.domain.AlreadyPublishedException;
import content.domain.Content;
import content.domain.ContentType;
import content.domain.FolderUploadException;
import content.domain.MissingUploadException;
import content.domain.NotPublishedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * P6-T4 use-case suite: tree CRUD with parent checks, the
 * upload→publish→fetch lifecycle with every guard (folder upload, publish
 * without upload, re-publish, DRAFT fetch), and one outbox event per state
 * change landing in the same operation.
 */
class ContentApplicationSuiteTest {

    private final FakeContentStore contents = new FakeContentStore();
    private final FakeObjectStore objects = new FakeObjectStore();
    private final List<String> outbox = new ArrayList<>();
    private final ContentService service;

    ContentApplicationSuiteTest() {
        service = new ContentService(contents, objects,
            (type, key, payload) -> outbox.add(type + ":" + key), Content::contentId);
    }

    @Test
    void createLandsInTreeAndEmitsEvent() {
        Content folder = service.create("C-F", ContentType.FOLDER, "docs", null);
        Content doc = service.create("C-1", ContentType.DOCUMENT, "spec.pdf", "C-F");
        assertEquals("C-F", doc.parentContentId());
        assertTrue(outbox.contains("content.content.created.v1:C-F"));
        assertTrue(outbox.contains("content.content.created.v1:C-1"));
        assertEquals(doc.contentId(), service.find("C-1").orElseThrow().contentId());
        assertEquals(1, service.tree("C-F").size());
        assertThrows(NoSuchElementException.class,
            () -> service.create("C-2", ContentType.DOCUMENT, "orphan", "C-NONE"));
    }

    @Test
    void updateRenamesDraftAndEmitsEvent() {
        service.create("C-1", ContentType.DOCUMENT, "spec.pdf", null);
        Content updated = service.update("C-1", "renamed.pdf", null);
        assertEquals("renamed.pdf", updated.contentName());
        assertTrue(outbox.contains("content.content.updated.v1:C-1"));
        assertThrows(NoSuchElementException.class,
            () -> service.update("C-404", "n", null));
        assertThrows(NoSuchElementException.class,
            () -> service.update("C-1", "n", "C-NONE"));
    }

    @Test
    void uploadPublishFetchRoundtrip() {
        service.create("C-1", ContentType.DOCUMENT, "spec.pdf", null);
        byte[] bytes = {1, 2, 3};
        Content uploaded = service.upload("C-1", bytes, "application/pdf");
        assertEquals("contents/C-1", uploaded.s3Key());
        assertEquals(3, uploaded.sizeBytes());
        assertEquals("application/pdf", uploaded.contentType());
        assertArrayEquals(bytes, objects.stored.get("contents/C-1"));

        assertThrows(NotPublishedException.class, () -> service.data("C-1"));
        service.publish("C-1");
        ContentService.ContentData data = service.data("C-1");
        assertEquals("application/pdf", data.contentType());
        assertArrayEquals(bytes, data.bytes());
        assertTrue(outbox.contains("content.content.published.v1:C-1"));
    }

    @Test
    void uploadOnFolderOrMissingContentRefused() {
        service.create("C-F", ContentType.FOLDER, "docs", null);
        assertThrows(FolderUploadException.class,
            () -> service.upload("C-F", new byte[] {1}, "application/pdf"));
        assertThrows(NoSuchElementException.class,
            () -> service.upload("C-404", new byte[] {1}, "application/pdf"));
    }

    @Test
    void publishGuardsAreTyped() {
        service.create("C-1", ContentType.DOCUMENT, "spec.pdf", null);
        assertThrows(MissingUploadException.class, () -> service.publish("C-1"));
        service.upload("C-1", new byte[] {1}, "text/plain");
        service.publish("C-1");
        assertThrows(AlreadyPublishedException.class, () -> service.publish("C-1"));
        assertThrows(AlreadyPublishedException.class,
            () -> service.upload("C-1", new byte[] {2}, "text/plain"));
    }

    @Test
    void deleteDraftOnly() {
        service.create("C-1", ContentType.DOCUMENT, "spec.pdf", null);
        service.delete("C-1");
        assertTrue(service.find("C-1").isEmpty());
        service.create("C-2", ContentType.DOCUMENT, "spec.pdf", null);
        service.upload("C-2", new byte[] {1}, "text/plain");
        service.publish("C-2");
        assertThrows(AlreadyPublishedException.class, () -> service.delete("C-2"));
        assertThrows(NoSuchElementException.class, () -> service.delete("C-404"));
    }

    @Test
    void treeListsChildrenOrEverything() {
        service.create("C-F", ContentType.FOLDER, "docs", null);
        service.create("C-1", ContentType.DOCUMENT, "a", "C-F");
        service.create("C-2", ContentType.DOCUMENT, "b", "C-F");
        service.create("C-R", ContentType.FOLDER, "root", null);
        assertEquals(2, service.tree("C-F").size());
        assertEquals(4, service.tree(null).size());
    }

    /** In-memory fake for the content tree port. */
    static final class FakeContentStore implements ContentStore {
        final Map<String, Content> rows = new HashMap<>();

        @Override
        public Optional<Content> find(String contentId) {
            return Optional.ofNullable(rows.get(contentId));
        }

        @Override
        public void save(Content content) {
            rows.put(content.contentId(), content);
        }

        @Override
        public void remove(String contentId) {
            rows.remove(contentId);
        }

        @Override
        public List<Content> findByParentContentId(String parentContentId) {
            return rows.values().stream()
                .filter(c -> parentContentId.equals(c.parentContentId())).toList();
        }

        @Override
        public List<Content> all() {
            return rows.values().stream()
                .sorted(java.util.Comparator.comparing(Content::contentId)).toList();
        }
    }

    /** In-memory fake for the S3 port. */
    static final class FakeObjectStore implements ObjectStore {
        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            stored.put(key, bytes.clone());
        }

        @Override
        public byte[] get(String key) {
            byte[] bytes = stored.get(key);
            if (bytes == null) {
                throw new NoSuchElementException("no object " + key);
            }
            return bytes.clone();
        }

        @Override
        public boolean exists(String key) {
            return stored.containsKey(key);
        }
    }
}
