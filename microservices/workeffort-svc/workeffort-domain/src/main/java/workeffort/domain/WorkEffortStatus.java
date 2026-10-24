package workeffort.domain;

/**
 * P6-T2 status machine (WorkEffort.currentStatusId live slice):
 * forward-only CREATED → IN_PROGRESS → COMPLETED, plus CANCELLED from
 * any live state. COMPLETED and CANCELLED are terminal.
 */
public enum WorkEffortStatus {
    CREATED, IN_PROGRESS, COMPLETED, CANCELLED;

    /** @return true when CANCELLED is still reachable from this state. */
    public boolean isLive() {
        return this == CREATED || this == IN_PROGRESS;
    }

    public boolean canTransitionTo(WorkEffortStatus target) {
        return switch (this) {
            case CREATED -> target == WorkEffortStatus.IN_PROGRESS
                || target == WorkEffortStatus.CANCELLED;
            case IN_PROGRESS -> target == WorkEffortStatus.COMPLETED
                || target == WorkEffortStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
