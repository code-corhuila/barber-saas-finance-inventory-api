package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A product of a barbershop's inventory (02-domain/entities-and-rules.md, InventoryProduct): stock is
 * never negative and the alert is on when it reaches the minimum (FR-019). The stock is set when the
 * product is created and afterwards changes only through a movement (DEC-INV-01).
 */
public final class InventoryProduct {

    static final int NAME_MAX = 120;
    static final int DESCRIPTION_MAX = 255;
    static final int UNIT_MAX = 20;

    private final UUID id;
    private final UUID barbershopId;
    private final Instant createdAt;
    private String name;
    private String description;
    private String unit;
    private Quantity currentStock;
    private Quantity minStockAlert;

    private InventoryProduct(UUID id, UUID barbershopId, String name, String description, String unit,
                             Quantity currentStock, Quantity minStockAlert, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.barbershopId = Objects.requireNonNull(barbershopId);
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.currentStock = Objects.requireNonNull(currentStock);
        this.minStockAlert = Objects.requireNonNull(minStockAlert);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** A new product; {@code initialStock} is its stock until the first movement. */
    public static InventoryProduct create(UUID id, UUID barbershopId, String name, String description, String unit,
                                          Quantity initialStock, Quantity minStockAlert, Instant now) {
        return new InventoryProduct(id, barbershopId, Text.required("name", name, NAME_MAX),
                Text.optional("description", description, DESCRIPTION_MAX), Text.required("unit", unit, UNIT_MAX),
                initialStock, minStockAlert, now);
    }

    /** Rebuilds a stored product; no rule is checked again. */
    public static InventoryProduct restore(UUID id, UUID barbershopId, String name, String description, String unit,
                                           Quantity currentStock, Quantity minStockAlert, Instant createdAt) {
        return new InventoryProduct(id, barbershopId, name, description, unit, currentStock, minStockAlert, createdAt);
    }

    /** Edits everything but the stock (DEC-INV-01). */
    public void edit(String name, String description, String unit, Quantity minStockAlert) {
        this.name = Text.required("name", name, NAME_MAX);
        this.description = Text.optional("description", description, DESCRIPTION_MAX);
        this.unit = Text.required("unit", unit, UNIT_MAX);
        this.minStockAlert = Objects.requireNonNull(minStockAlert);
    }

    /**
     * Applies a movement to the stock and returns it. An exit larger than the stock is refused and
     * changes nothing (HU-INV-001 scenario 2; the database enforces it too, chk_inventory_product_stock).
     */
    public StockMovement move(UUID movementId, MovementType type, Quantity quantity, String reason, UUID userId,
                              Instant now) {
        StockMovement movement = StockMovement.of(movementId, id, type, quantity, reason, userId, now);
        if (type == MovementType.OUT && quantity.compareTo(currentStock) > 0) {
            throw new BusinessRuleViolation("Insufficient stock: " + currentStock + " " + unit + " available, "
                    + quantity + " " + unit + " requested");
        }
        currentStock = type == MovementType.IN ? currentStock.plus(quantity) : currentStock.minus(quantity);
        return movement;
    }

    /** FR-019: computed on read, never stored. With a minimum of 0 it is on only when the stock is 0. */
    public boolean lowStock() {
        return currentStock.compareTo(minStockAlert) <= 0;
    }

    public UUID id() { return id; }
    public UUID barbershopId() { return barbershopId; }
    public String name() { return name; }
    public String description() { return description; }
    public String unit() { return unit; }
    public Quantity currentStock() { return currentStock; }
    public Quantity minStockAlert() { return minStockAlert; }
    public Instant createdAt() { return createdAt; }
}
