package manufacturing.application;

import java.util.NoSuchElementException;
import java.util.Optional;
import manufacturing.domain.Bom;

/**
 * P6-T1 BOM use case: create/update/delete with one outbox event per
 * mutation (ADR-009 — same transaction as the row write, via the caller's
 * adapter-layer tx). Duplicate creates are typed conflicts; updates and
 * deletes of missing products are typed not-founds.
 */
public class BomService {

    private final BomStore boms;
    private final OutboxWriter outbox;
    private final ManufacturingSerializer serializer;

    public BomService(BomStore boms, OutboxWriter outbox, ManufacturingSerializer serializer) {
        this.boms = boms;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Bom create(String productId, java.util.List<Bom.Component> components) {
        if (boms.find(productId).isPresent()) {
            throw new IllegalStateException("bom already exists for " + productId);
        }
        Bom bom = new Bom(productId, components);
        boms.save(bom);
        outbox.append(Bom.CREATED, productId, serializer.serializeBom(bom));
        return bom;
    }

    public Bom update(String productId, java.util.List<Bom.Component> components) {
        if (boms.find(productId).isEmpty()) {
            throw new NoSuchElementException("no bom for " + productId);
        }
        Bom bom = new Bom(productId, components);
        boms.save(bom);
        outbox.append(Bom.UPDATED, productId, serializer.serializeBom(bom));
        return bom;
    }

    public void delete(String productId) {
        Optional<Bom> existing = boms.find(productId);
        if (existing.isEmpty()) {
            throw new NoSuchElementException("no bom for " + productId);
        }
        boms.delete(productId);
        outbox.append(Bom.DELETED, productId, serializer.serializeBomDeleted(productId));
    }

    public Optional<Bom> get(String productId) {
        return boms.find(productId);
    }
}
