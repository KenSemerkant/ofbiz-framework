package billing.application;

/**
 * P5-F1-S2: the gateway is unreachable after the adapter's bounded retry.
 * Callers let the Kafka error handler DLQ the work; the reconciliation
 * report later surfaces anything stranded.
 */
public final class GatewayUnavailableException extends RuntimeException {

    public GatewayUnavailableException(String operation, int attempts, Throwable cause) {
        super("gateway unavailable during %s after %d attempts: %s"
            .formatted(operation, attempts, cause.getMessage()), cause);
    }
}
