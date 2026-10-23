package order.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotes")
public class QuoteEntity {

    @Id
    @Column(name = "quote_id")
    private String quoteId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String lines;

    public QuoteEntity() {
    }

    public QuoteEntity(String quoteId, String customerId, String status, String lines) {
        this.quoteId = quoteId;
        this.customerId = customerId;
        this.status = status;
        this.lines = lines;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public String getLines() {
        return lines;
    }
}
