package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.util.Optional;
import java.util.UUID;

/**
 * finance_inventory.inventory_product and inventory_movement. Every read is scoped by the tenant; a
 * movement reaches it through its product.
 */
public interface InventoryRepository {

    /** A concurrent exit took the stock first: chk_inventory_product_stock refused the change. */
    class StockWouldGoNegative extends RuntimeException {
        public StockWouldGoNegative() {
            super("The stock would go negative");
        }
    }

    Optional<InventoryProduct> findById(UUID tenant, UUID id);

    /** Most recent first; {@code lowStock} null does not filter. */
    Page<InventoryProduct> page(UUID tenant, Boolean lowStock, Page.Request page);

    /** The movements of a product of the tenant, most recent first; {@code type} null does not filter. */
    Page<StockMovement> movements(UUID tenant, UUID productId, MovementType type, Page.Request page);

    /** A movement of a product of the tenant; what a retried key answers with. */
    Optional<StockMovement> findMovement(UUID tenant, UUID movementId);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** The product and its key in ONE transaction (norm 5.3.8). */
    void saveNew(InventoryProduct product, Idempotency.Key key);

    /** Name, description, unit and minimum only: never the stock (DEC-INV-01). */
    void update(InventoryProduct product);

    /**
     * Adds the movement to the stock as a delta (never by overwriting it), stores the movement and its
     * key, all in ONE transaction, and returns the product as stored. Two concurrent exits cannot
     * both take the same stock: the second one throws {@link StockWouldGoNegative} and stores nothing.
     */
    InventoryProduct applyMovement(UUID tenant, StockMovement movement, Idempotency.Key key);
}
