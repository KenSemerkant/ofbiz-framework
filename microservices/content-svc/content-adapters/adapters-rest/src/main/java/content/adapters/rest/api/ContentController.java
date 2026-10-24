package content.adapters.rest.api;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import content.application.ContentService;
import content.domain.AlreadyPublishedException;
import content.domain.Content;
import content.domain.ContentType;
import content.domain.FolderUploadException;
import content.domain.MissingUploadException;
import content.domain.NotPublishedException;

/**
 * P6-T4 REST edge: /api/v1/contents — tree CRUD, binary upload, one-way
 * publish, published-only data fetch. Guard violations map to typed 409s;
 * mutating endpoints run in one adapter-layer tx so the aggregate + outbox
 * row commit together.
 */
@RestController
@RequestMapping("/api/v1/contents")
public class ContentController {

    private final ContentService contents;

    public ContentController(ContentService contents) {
        this.contents = contents;
    }

    public record CreateContentRequest(String contentId, String contentTypeId,
            String contentName, String parentContentId) {
    }

    public record UpdateContentRequest(String contentName, String parentContentId) {
    }

    public record ContentResponse(String contentId, String contentTypeId, String contentName,
            String parentContentId, String statusId, String s3Key, String contentType,
            Long sizeBytes) {
    }

    @PostMapping
    @Transactional
    ResponseEntity<ContentResponse> create(@RequestBody CreateContentRequest request) {
        Content content = contents.create(request.contentId(),
            ContentType.valueOf(request.contentTypeId()), request.contentName(),
            request.parentContentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(content));
    }

    @GetMapping
    List<ContentResponse> tree(@RequestParam(required = false) String parent) {
        return contents.tree(parent).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{contentId}")
    ResponseEntity<ContentResponse> get(@PathVariable String contentId) {
        return contents.find(contentId).map(this::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{contentId}")
    @Transactional
    ResponseEntity<ContentResponse> update(@PathVariable String contentId,
            @RequestBody UpdateContentRequest request) {
        return guarded(() -> toResponse(
            contents.update(contentId, request.contentName(), request.parentContentId())));
    }

    @DeleteMapping("/{contentId}")
    @Transactional
    ResponseEntity<Void> delete(@PathVariable String contentId) {
        try {
            contents.delete(contentId);
            return ResponseEntity.noContent().build();
        } catch (FolderUploadException | MissingUploadException
                | AlreadyPublishedException | NotPublishedException e) {
            return conflict(e);
        }
    }

    /**
     * P6-T4 AC: binary body, any content type — stored via the ObjectStore
     * under contents/{id} (DRAFT DOCUMENTs only).
     */
    @PostMapping("/{contentId}/upload")
    @Transactional
    ResponseEntity<ContentResponse> upload(@PathVariable String contentId,
            @RequestBody byte[] bytes,
            @RequestHeader(value = "Content-Type",
                defaultValue = MediaType.APPLICATION_OCTET_STREAM_VALUE) String contentType) {
        return guarded(() -> toResponse(contents.upload(contentId, bytes, contentType)));
    }

    /** DRAFT→PUBLISHED, forward-only; 409 when no upload yet or re-publish. */
    @PostMapping("/{contentId}/publish")
    @Transactional
    ResponseEntity<ContentResponse> publish(@PathVariable String contentId) {
        return guarded(() -> toResponse(contents.publish(contentId)));
    }

    /** Streams the object bytes with the stored content type (PUBLISHED only). */
    @GetMapping("/{contentId}/data")
    ResponseEntity<byte[]> data(@PathVariable String contentId) {
        try {
            ContentService.ContentData data = contents.data(contentId);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.contentType()))
                .contentLength(data.bytes().length).body(data.bytes());
        } catch (NotPublishedException e) {
            return conflict(e);
        }
    }

    private ContentResponse toResponse(Content content) {
        return new ContentResponse(content.contentId(), content.contentTypeId().name(),
            content.contentName(), content.parentContentId(), content.statusId().name(),
            content.s3Key(), content.contentType(), content.sizeBytes());
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<T> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (FolderUploadException | MissingUploadException
                | AlreadyPublishedException | NotPublishedException e) {
            return conflict(e);
        }
    }

    private <T> ResponseEntity<T> conflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .header("X-Error-Reason", e.getClass().getSimpleName()).build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
