package content.domain;

/**
 * Content lifecycle (mirrors monolith statusItemId on Content): DRAFT is
 * mutable and private, PUBLISHED is immutable and publicly fetchable.
 * Forward-only — publishing is one-way (P6-T4).
 */
public enum ContentStatus {
    DRAFT, PUBLISHED
}
