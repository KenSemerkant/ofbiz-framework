package party.application;

import ofbiz.projection.IdempotentProjector;

/**
 * Persistence contract for the monolith CDC projection (P2-F2-S2). The
 * implementation parses the Debezium envelope and writes the projection
 * rows plus the processed-event marker in one transaction.
 */
public interface MonolithProjectionSink extends IdempotentProjector.Sink {
}
