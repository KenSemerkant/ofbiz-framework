package order.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "returns")
public class ReturnEntity {

    @Id
    @Column(name = "return_id")
    private String returnId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String lines;

    public ReturnEntity() {
    }

    public ReturnEntity(String returnId, String orderId, String status, String lines) {
        this.returnId = returnId;
        this.orderId = orderId;
        this.status = status;
        this.lines = lines;
    }

    public String getReturnId() {
        return returnId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getLines() {
        return lines;
    }
}
