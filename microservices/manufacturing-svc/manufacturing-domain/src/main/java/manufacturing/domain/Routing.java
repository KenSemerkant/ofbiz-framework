package manufacturing.domain;

import java.util.List;

/**
 * P6-T1 production routing (monolith WorkEffort routing slice): ordered
 * task list for one product. Tasks are inline JSON on the row (inline-JSON
 * precedent); seq is the execution order, estimatedDuration is carried
 * as-is from the monolith WorkEffort.estimatedDuration.
 */
public final class Routing {

    public static final String CREATED = "manufacturing.routing.created.v1";
    public static final String UPDATED = "manufacturing.routing.updated.v1";

    private final String routingId;
    private final String productId;
    private final List<Task> tasks;

    public Routing(String routingId, String productId, List<Task> tasks) {
        if (routingId == null || routingId.isBlank()) {
            throw new IllegalArgumentException("routingId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("a routing needs at least one task");
        }
        this.routingId = routingId;
        this.productId = productId;
        this.tasks = List.copyOf(tasks);
    }

    public String routingId() {
        return routingId;
    }

    public String productId() {
        return productId;
    }

    public List<Task> tasks() {
        return tasks;
    }

    /** One routing step: execution seq, name, estimated duration. */
    public record Task(int seq, String name, long estimatedDuration) {

        public Task {
            if (seq <= 0) {
                throw new IllegalArgumentException("seq must be positive");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("task name must not be blank");
            }
            if (estimatedDuration <= 0) {
                throw new IllegalArgumentException("estimatedDuration must be positive");
            }
        }
    }
}
