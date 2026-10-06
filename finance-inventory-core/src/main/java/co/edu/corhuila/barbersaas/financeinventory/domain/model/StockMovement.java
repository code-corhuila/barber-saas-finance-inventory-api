package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry or exit of a product's stock, append-only. The quantity is always positive and the type
 * gives the direction (data-dictionary.md). Created only by {@link InventoryProduct#move}.
 */
public record StockMovement(UUID id, UUID productId, MovementType type, Quantity quantity, String reason,
                            UUID createdByUserId, Instant createdAt) {

    static final int REASON_MAX = 255;

    public StockMovement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(productId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(createdByUserId);
        Objects.requireNonNull(createdAt);
    }

    static StockMovement of(UUID id, UUID productId, MovementType type, Quantity quantity, String reason,
                            UUID userId, Instant now) {
        if (type == null) {
            throw new InvalidValue("movementType is required");
        }
        if (quantity == null || quantity.isZero()) {
            throw new InvalidValue("quantity must be greater than 0");
        }
        return new StockMovement(id, productId, type, quantity, Text.optional("reason", reason, REASON_MAX), userId,
                now);
    }
}
