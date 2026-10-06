package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** InventoryProduct: FR-018, FR-019, DEC-INV-01 (HU-INV-001 #11). */
class InventoryProductTest {

    final UUID user = UUID.randomUUID();

    InventoryProduct shampoo(String stock, String min) {
        return InventoryProduct.create(UUID.randomUUID(), UUID.randomUUID(), "Menthol shampoo", null, "ml",
                Quantity.of(stock), Quantity.of(min), Instant.now());
    }

    StockMovement move(InventoryProduct p, MovementType type, String quantity) {
        return p.move(UUID.randomUUID(), type, Quantity.of(quantity), "Weekly use", user, Instant.now());
    }

    @Test
    void anEntryAddsAndAnExitSubtracts() {
        InventoryProduct p = shampoo("2000.00", "500.00");

        move(p, MovementType.OUT, "250.5");
        StockMovement in = move(p, MovementType.IN, "100");

        assertEquals(Quantity.of("1849.50"), p.currentStock());
        assertEquals(p.id(), in.productId());
        assertEquals(user, in.createdByUserId());
    }

    @Test
    void anExitLargerThanTheStockIsRefusedAndChangesNothing() {
        InventoryProduct p = shampoo("100.00", "0");

        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class, () -> move(p, MovementType.OUT, "250"));

        assertEquals("Insufficient stock: 100.00 ml available, 250.00 ml requested", e.getMessage());
        assertEquals(Quantity.of("100"), p.currentStock());
        move(p, MovementType.OUT, "100");
        assertTrue(p.currentStock().isZero(), "the whole stock can go out");
    }

    @Test
    void theAlertIsOnWhenTheStockReachesTheMinimum() {
        InventoryProduct p = shampoo("600", "500");
        assertFalse(p.lowStock());

        move(p, MovementType.OUT, "100");

        assertTrue(p.lowStock());
    }

    @Test
    void editingNeverTouchesTheStock() {
        InventoryProduct p = shampoo("600", "500");

        p.edit("Shampoo", "Mint", "ml", Quantity.of("50"));

        assertEquals(Quantity.of("600"), p.currentStock());
        assertEquals("Shampoo", p.name());
        assertFalse(p.lowStock());
    }

    @Test
    void aMovementNeedsAPositiveQuantityAndAType() {
        InventoryProduct p = shampoo("10", "0");

        assertThrows(InvalidValue.class, () -> move(p, MovementType.IN, "0"));
        assertThrows(InvalidValue.class, () -> move(p, null, "1"));
        assertThrows(InvalidValue.class, () -> p.move(UUID.randomUUID(), MovementType.IN, Quantity.of("1"),
                "x".repeat(256), user, Instant.now()));
    }

    @Test
    void aQuantityHasTwoDecimalsAndIsNeverNegative() {
        assertEquals(new BigDecimal("2000.00"), Quantity.of("2000").value());
        assertThrows(InvalidValue.class, () -> Quantity.of("1.005"));
        assertThrows(InvalidValue.class, () -> Quantity.of("-1"));
        assertThrows(InvalidValue.class, () -> Quantity.of("10000000000"));
    }

    @Test
    void nameAndUnitAreRequired() {
        assertThrows(InvalidValue.class, () -> InventoryProduct.create(UUID.randomUUID(), UUID.randomUUID(), " ",
                null, "ml", Quantity.ZERO, Quantity.ZERO, Instant.now()));
        assertThrows(InvalidValue.class, () -> InventoryProduct.create(UUID.randomUUID(), UUID.randomUUID(), "A",
                null, "x".repeat(21), Quantity.ZERO, Quantity.ZERO, Instant.now()));
    }
}
