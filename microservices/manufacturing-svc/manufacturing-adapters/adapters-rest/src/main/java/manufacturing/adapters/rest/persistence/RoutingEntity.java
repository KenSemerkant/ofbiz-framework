package manufacturing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** routings row: tasks inline as JSON (no cross-task query in P6). */
@Entity
@Table(name = "routings")
public class RoutingEntity {

    @Id
    @Column(name = "routing_id")
    private String routingId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private String tasks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public RoutingEntity() {
    }

    public RoutingEntity(String routingId, String productId, String tasks) {
        this.routingId = routingId;
        this.productId = productId;
        this.tasks = tasks;
    }

    public String getRoutingId() {
        return routingId;
    }

    public String getProductId() {
        return productId;
    }

    public String getTasks() {
        return tasks;
    }
}
