package order.domain;

import java.util.List;

/**
 * Customer return against a completed order (P4-F1-S3): creation triggers
 * the inventory-adjust saga and a credit memo event (application layer).
 */
public final class ReturnOrder {

    public enum Status { RECEIVED, COMPLETED }

    private final String returnId;
    private final String orderId;
    private final List<ReturnLine> lines;
    private Status status = Status.RECEIVED;

    public ReturnOrder(String returnId, String orderId, List<ReturnLine> lines) {
        if (returnId == null || returnId.isBlank()) {
            throw new IllegalArgumentException("returnId must not be blank");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a return needs at least one line");
        }
        this.returnId = returnId;
        this.orderId = orderId;
        this.lines = List.copyOf(lines);
    }

    /** Stock is back and the credit memo issued. */
    public void complete() {
        if (status != Status.RECEIVED) {
            throw new IllegalStateException("return " + returnId + " is already " + status);
        }
        status = Status.COMPLETED;
    }

    public static ReturnOrder rehydrate(String returnId, String orderId,
            List<ReturnLine> lines, Status status) {
        ReturnOrder returns = new ReturnOrder(returnId, orderId, lines);
        returns.status = status;
        return returns;
    }

    public String returnId() {
        return returnId;
    }

    public String orderId() {
        return orderId;
    }

    public List<ReturnLine> lines() {
        return lines;
    }

    public Status status() {
        return status;
    }
}
