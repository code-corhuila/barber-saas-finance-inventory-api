package co.edu.corhuila.barbersaas.financeinventory.application.port.in;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.Quantity;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.util.UUID;

/** The inventory operations of finance-inventory-service.yaml; every one is ADMIN_BARBERSHOP only. */
public interface InventoryUseCases {

    record CreateCommand(String name, String description, String unit, Quantity currentStock,
                         Quantity minStockAlert) { }

    /** No stock: it changes only through movements (DEC-INV-01). */
    record EditCommand(String name, String description, String unit, Quantity minStockAlert) { }

    record MoveCommand(MovementType type, Quantity quantity, String reason) { }

    /** The movement and the product with its stock and lowStock already recomputed. */
    record MovementResult(StockMovement movement, InventoryProduct product) { }

    /** FR-018: the stock given is the initial stock. Idempotent like every creation. */
    Created<InventoryProduct> create(Caller caller, CreateCommand command, String idempotencyKey);

    /** Most recent first; {@code lowStock} null lists every product, true or false only those (FR-019). */
    Page<InventoryProduct> list(Caller caller, Boolean lowStock, Page.Request page);

    InventoryProduct get(Caller caller, UUID id);

    InventoryProduct edit(Caller caller, UUID id, EditCommand command);

    /**
     * Applies an entry or an exit in one transaction with its movement. An exit larger than the stock
     * is refused (422) and records nothing. A retry with the same key does not move the stock again.
     */
    Created<MovementResult> move(Caller caller, UUID productId, MoveCommand command, String idempotencyKey);

    /** The product's movements, most recent first; {@code type} null lists both directions. */
    Page<StockMovement> movements(Caller caller, UUID productId, MovementType type, Page.Request page);
}
