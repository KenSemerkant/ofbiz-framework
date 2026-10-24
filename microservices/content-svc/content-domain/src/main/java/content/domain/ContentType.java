package content.domain;

/**
 * Content node kind: FOLDER (tree structure only, never holds bytes) or
 * DOCUMENT (holds one object in S3). Collapses the monolith ContentType /
 * DataResourceType vocabulary into the two kinds the live slice needs.
 */
public enum ContentType {
    FOLDER, DOCUMENT
}
