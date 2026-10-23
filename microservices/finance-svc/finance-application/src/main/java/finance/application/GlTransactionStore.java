package finance.application;

import java.util.List;
import java.util.Optional;
import finance.domain.GlTransaction;

/** Persistence port for journal transactions. */
public interface GlTransactionStore {

    Optional<GlTransaction> find(String acctgTransId);

    void save(GlTransaction transaction);

    /** Posted transactions only — the trial-balance input (P5-F2-S3). */
    List<GlTransaction> findAllPosted();
}
