package finance.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import finance.application.GlTransactionStore;
import finance.domain.GlTransaction;

/** JPA adapter for the GlTransactionStore port. */
@Repository
public class JpaGlTransactionStore implements GlTransactionStore {

    private final GlTransactionRepository transactions;

    public JpaGlTransactionStore(GlTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public Optional<GlTransaction> find(String acctgTransId) {
        return transactions.findById(acctgTransId).map(this::toDomain);
    }

    @Override
    public void save(GlTransaction transaction) {
        transactions.save(new GlTransactionEntity(transaction.acctgTransId(),
            transaction.transType(), transaction.description(), transaction.periodId(),
            GlEntryJson.serialize(transaction.entries()), transaction.isPosted(),
            transaction.postedAt()));
    }

    @Override
    public List<GlTransaction> findAllPosted() {
        return transactions.findByPostedTrue().stream().map(this::toDomain).toList();
    }

    private GlTransaction toDomain(GlTransactionEntity entity) {
        return GlTransaction.rehydrate(entity.getAcctgTransId(), entity.getTransType(),
            entity.getDescription(), entity.getPeriodId(),
            GlEntryJson.deserialize(entity.getEntries()), entity.isPosted(),
            entity.getPostedAt());
    }
}
