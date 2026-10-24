package workeffort.domain;

/** P6-T2: the WorkEffort status machine is forward-only; skips are refused. */
public final class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException(String workEffortId, WorkEffortStatus from,
            WorkEffortStatus to) {
        super("work effort %s cannot move %s -> %s".formatted(workEffortId, from, to));
    }
}
