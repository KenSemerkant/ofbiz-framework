package inventory.application;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import inventory.domain.InventoryItem;

/**
 * P3-F2-S1 use case: item creation and the stock ops receive/adjust/transfer.
 * Each op mutates the aggregate through its guards and appends the matching
 * `inventory.stock.*` outbox event in the same logical transaction.
 */
public class InventoryService {

    public static final String ITEM_CREATED = "inventory.item.created.v1";
    public static final String STOCK_RECEIVED = "inventory.stock.received.v1";
    public static final String STOCK_ADJUSTED = "inventory.stock.adjusted.v1";
    public static final String STOCK_TRANSFERRED = "inventory.stock.transferred.v1";

    private final InventoryItemStore items;
    private final OutboxWriter outbox;
    private final InventorySerializer serializer;

    public InventoryService(InventoryItemStore items, OutboxWriter outbox,
            InventorySerializer serializer) {
        this.items = items;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<InventoryItem> get(String inventoryItemId) {
        return items.find(inventoryItemId);
    }

    public InventoryItem create(InventoryItem item) {
        if (items.find(item.inventoryItemId()).isPresent()) {
            throw new DuplicateException("inventory item already exists: "
                + item.inventoryItemId());
        }
        items.save(item);
        outbox.append(ITEM_CREATED, item.inventoryItemId(), serializer.serializeItem(item));
        return item;
    }

    public InventoryItem receive(String inventoryItemId, BigDecimal quantity) {
        InventoryItem item = load(inventoryItemId);
        item.receive(quantity);
        items.save(item);
        outbox.append(STOCK_RECEIVED, item.inventoryItemId(),
            serializer.serializeStockChange(item, quantity));
        return item;
    }

    public InventoryItem adjust(String inventoryItemId, BigDecimal quantity) {
        InventoryItem item = load(inventoryItemId);
        item.adjust(quantity);
        items.save(item);
        outbox.append(STOCK_ADJUSTED, item.inventoryItemId(),
            serializer.serializeStockChange(item, quantity));
        return item;
    }

    public InventoryItem transfer(String fromItemId, String toItemId, BigDecimal quantity) {
        InventoryItem from = load(fromItemId);
        InventoryItem to = load(toItemId);
        from.transferTo(to, quantity);
        items.save(from);
        items.save(to);
        outbox.append(STOCK_TRANSFERRED, from.inventoryItemId(),
            serializer.serializeTransfer(from, to, quantity));
        return from;
    }

    private InventoryItem load(String inventoryItemId) {
        return items.find(inventoryItemId).orElseThrow(
            () -> new NoSuchElementException("no inventory item " + inventoryItemId));
    }
}
