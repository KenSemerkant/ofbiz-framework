package finance.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlTransactionRepository
        extends JpaRepository<GlTransactionEntity, String> {

    List<GlTransactionEntity> findByPostedTrue();
}
