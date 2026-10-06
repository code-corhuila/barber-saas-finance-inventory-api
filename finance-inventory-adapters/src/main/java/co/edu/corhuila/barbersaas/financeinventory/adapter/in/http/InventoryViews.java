package co.edu.corhuila.barbersaas.financeinventory.adapter.in.http;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.MovementResult;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The inventory response schemas: quantities as numbers with two decimals, lowStock computed (FR-019). */
final class InventoryViews {

    private InventoryViews() {
    }

    record ProductView(UUID id, String name, String description, String unit, BigDecimal currentStock,
                       BigDecimal minStockAlert, boolean lowStock, Instant createdAt) {
        static ProductView of(InventoryProduct p) {
            return new ProductView(p.id(), p.name(), p.description(), p.unit(), p.currentStock().value(),
                    p.minStockAlert().value(), p.lowStock(), p.createdAt());
        }
    }

    record MovementView(UUID id, UUID productId, String movementType, BigDecimal quantity, String reason,
                        UUID createdByUserId, Instant createdAt) {
        static MovementView of(StockMovement m) {
            return new MovementView(m.id(), m.productId(), m.type().name(), m.quantity().value(), m.reason(),
                    m.createdByUserId(), m.createdAt());
        }
    }

    record MovementResultView(MovementView movement, ProductView product) {
        static MovementResultView of(MovementResult r) {
            return new MovementResultView(MovementView.of(r.movement()), ProductView.of(r.product()));
        }
    }
}
