package manufacturing.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import manufacturing.application.BomStore;
import manufacturing.domain.Bom;

/** JPA adapter for the BomStore port. */
@Repository
public class JpaBomStore implements BomStore {

    private final BomRepository boms;

    public JpaBomStore(BomRepository boms) {
        this.boms = boms;
    }

    @Override
    public Optional<Bom> find(String productId) {
        return boms.findById(productId).map(this::toDomain);
    }

    @Override
    public void save(Bom bom) {
        boms.save(new BomEntity(bom.productId(), BomComponentJson.serialize(bom.components())));
    }

    @Override
    public void delete(String productId) {
        boms.deleteById(productId);
    }

    private Bom toDomain(BomEntity entity) {
        return new Bom(entity.getProductId(), BomComponentJson.deserialize(entity.getComponents()));
    }
}
