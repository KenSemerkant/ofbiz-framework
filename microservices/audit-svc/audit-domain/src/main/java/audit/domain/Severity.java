package audit.domain;

/** Audit event severity. HIGH events are alert-worthy (ADR-011 audit pipeline). */
public enum Severity {
    INFO,
    WARN,
    HIGH
}
