package content.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * P6-T4 guard tests: forward-only publishing (no re-publish, no publish
 * without upload, DRAFT-only mutations, PUBLISHED-only data fetch) is the
 * invariant content-svc exists to enforce — content that can be mutated
 * after publishing or fetched while private is wrong by definition.
 */
class ContentGuardsTest {

    private static Content document() {
        return new Content("C-1", ContentType.DOCUMENT, "spec.pdf", "C-0");
    }

    @Test
    void constructionGuardsAndDefaults() {
        Content content = document();
        assertEquals("C-1", content.contentId());
        assertEquals(ContentType.DOCUMENT, content.contentTypeId());
        assertEquals("spec.pdf", content.contentName());
        assertEquals("C-0", content.parentContentId());
        assertEquals(ContentStatus.DRAFT, content.statusId());
        assertNull(content.s3Key());
        assertNull(content.contentType());
        assertNull(content.sizeBytes());
        assertThrows(IllegalArgumentException.class,
            () -> new Content(" ", ContentType.DOCUMENT, "n", null));
        assertThrows(IllegalArgumentException.class,
            () -> new Content("C", null, "n", null));
        assertThrows(IllegalArgumentException.class,
            () -> new Content("C", ContentType.FOLDER, " ", null));
    }

    @Test
    void uploadSetsObjectFieldsOnDraftDocument() {
        Content content = document();
        content.upload("contents/C-1", "application/pdf", 42);
        assertEquals("contents/C-1", content.s3Key());
        assertEquals("application/pdf", content.contentType());
        assertEquals(42, content.sizeBytes());
        assertThrows(IllegalArgumentException.class,
            () -> content.upload(" ", "application/pdf", 1));
        assertThrows(IllegalArgumentException.class,
            () -> content.upload("k", " ", 1));
        assertThrows(IllegalArgumentException.class,
            () -> content.upload("k", "application/pdf", -1));
    }

    @Test
    void folderCannotHoldBytes() {
        Content folder = new Content("C-F", ContentType.FOLDER, "docs", null);
        assertThrows(FolderUploadException.class,
            () -> folder.upload("contents/C-F", "application/pdf", 1));
    }

    @Test
    void publishIsForwardOnlyAndRequiresUpload() {
        Content content = document();
        assertThrows(MissingUploadException.class, content::publish);
        content.upload("contents/C-1", "application/pdf", 42);
        content.publish();
        assertEquals(ContentStatus.PUBLISHED, content.statusId());
        assertThrows(AlreadyPublishedException.class, content::publish);
        assertThrows(AlreadyPublishedException.class,
            () -> content.upload("contents/C-1", "application/pdf", 43));
    }

    @Test
    void publishedContentIsFrozen() {
        Content content = document();
        content.upload("contents/C-1", "application/pdf", 42);
        content.publish();
        assertThrows(AlreadyPublishedException.class,
            () -> content.update("renamed", null));
        assertThrows(AlreadyPublishedException.class, content::ensureDeletable);
        assertEquals("contents/C-1", content.publishedS3Key());
    }

    @Test
    void dataFetchIsPublishedOnly() {
        Content content = document();
        content.upload("contents/C-1", "application/pdf", 42);
        assertThrows(NotPublishedException.class, content::publishedS3Key);
    }

    @Test
    void draftUpdateRenamesAndReparents() {
        Content content = document();
        content.update("renamed.pdf", "C-9");
        assertEquals("renamed.pdf", content.contentName());
        assertEquals("C-9", content.parentContentId());
        assertThrows(IllegalArgumentException.class, () -> content.update(" ", null));
        content.ensureDeletable();
    }

    @Test
    void rehydrateRestoresPersistedState() {
        Content content = Content.rehydrate("C-R", ContentType.DOCUMENT, "n", "C-P",
            ContentStatus.PUBLISHED, "contents/C-R", "text/plain", 7L);
        assertEquals(ContentStatus.PUBLISHED, content.statusId());
        assertEquals("contents/C-R", content.s3Key());
        assertEquals("text/plain", content.contentType());
        assertEquals(7, content.sizeBytes());
        assertTrue(content.contentId().equals("C-R"));
    }
}
