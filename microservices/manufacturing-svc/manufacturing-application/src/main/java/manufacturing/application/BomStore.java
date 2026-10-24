package manufacturing.application;

import java.util.Optional;
import manufacturing.domain.Bom;

/** Persistence port for bills of materials. */
public interface BomStore {

    Optional<Bom> find(String productId);

    void save(Bom bom);

    void delete(String productId);
}
