package inventory.adapters.rest.api;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Bounded optimistic-lock retry (P3-F2-S2 AC): 3 attempts, then the last
 * conflict propagates (controller maps it to 409). Immediate retries cannot
 * drain a thundering herd (measured: 11-12 of 50 wins under 100 parallel
 * reserves) — every collision round crowns exactly one winner — so attempts
 * back off with jitter to spread the herd over the time the winners need to
 * commit. The sleeping thread holds no transaction/connection between
 * attempts. Retry must wrap the WHOLE transaction (a failed tx is
 * rollback-only), hence the helper lives in the controller layer, not inside
 * the @Transactional operation beans.
 */
final class OptimisticRetry {

    static final int MAX_ATTEMPTS = 3;

    private OptimisticRetry() {
    }

    static <T> T run(Supplier<T> op) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return op.get();
            } catch (OptimisticLockingFailureException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw last;
    }

    private static void backoff(int attempt) {
        long base = switch (attempt) {
            case 1 -> 5; // 5-10ms
            case 2 -> 20; // 20-40ms
            default -> 0;
        };
        try {
            Thread.sleep(base + java.util.concurrent.ThreadLocalRandom.current().nextLong(base));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
