package finance.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** gl_transactions row: entries inline as JSON (no cross-entry query in P5). */
@Entity
@Table(name = "gl_transactions")
public class GlTransactionEntity {

    @Id
    @Column(name = "acctg_trans_id")
    private String acctgTransId;

    @Column(name = "trans_type", nullable = false)
    private String transType;

    @Column(nullable = false)
    private String description;

    @Column(name = "period_id", nullable = false)
    private String periodId;

    @Column(nullable = false)
    private String entries;

    @Column(nullable = false)
    private boolean posted;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public GlTransactionEntity() {
    }

    public GlTransactionEntity(String acctgTransId, String transType, String description,
            String periodId, String entries, boolean posted, Instant postedAt) {
        this.acctgTransId = acctgTransId;
        this.transType = transType;
        this.description = description;
        this.periodId = periodId;
        this.entries = entries;
        this.posted = posted;
        this.postedAt = postedAt;
    }

    public String getAcctgTransId() {
        return acctgTransId;
    }

    public String getTransType() {
        return transType;
    }

    public String getDescription() {
        return description;
    }

    public String getPeriodId() {
        return periodId;
    }

    public String getEntries() {
        return entries;
    }

    public boolean isPosted() {
        return posted;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
