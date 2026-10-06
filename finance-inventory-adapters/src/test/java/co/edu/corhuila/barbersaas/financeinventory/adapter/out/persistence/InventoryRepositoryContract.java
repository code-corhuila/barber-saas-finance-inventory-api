package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository.StockWouldGoNegative;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.Quantity;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What every InventoryRepository must do, in memory or in PostgreSQL. */
abstract class InventoryRepositoryContract {

    final UUID shop = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final Page.Request first = new Page.Request(1, 10);

    abstract InventoryRepository repository();

    InventoryProduct saved(String stock, String min) {
        InventoryProduct p = InventoryProduct.create(UUID.randomUUID(), shop, "Shampoo", null, "ml", Quantity.of(stock),
                Quantity.of(min), Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository().saveNew(p, key("POST /api/v1/inventory/products"));
        return p;
    }

    Idempotency.Key key(String operation) {
        return new Idempotency.Key("it-" + UUID.randomUUID(), operation, "hash");
    }

    StockMovement movement(InventoryProduct p, MovementType type, String quantity) {
        return new StockMovement(UUID.randomUUID(), p.id(), type, Quantity.of(quantity), "Use", user,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    InventoryProduct apply(StockMovement m) {
        return repository().applyMovement(shop, m, key("POST /api/v1/inventory/products/{id}/movements"));
    }

    @Test
    void aProductIsReadBackOnlyInItsBarbershop() {
        InventoryProduct p = saved("2000.5", "500");

        InventoryProduct read = repository().findById(shop, p.id()).orElseThrow();

        assertEquals(Quantity.of("2000.50"), read.currentStock());
        assertEquals("ml", read.unit());
        assertTrue(repository().findById(UUID.randomUUID(), p.id()).isEmpty(), "another barbershop sees nothing");
    }

    @Test
    void aMovementChangesTheStockByItsDeltaAndIsStoredWithIt() {
        InventoryProduct p = saved("100", "0");
        StockMovement out = movement(p, MovementType.OUT, "30.25");

        InventoryProduct after = apply(out);
        apply(movement(p, MovementType.IN, "5"));

        assertEquals(Quantity.of("69.75"), after.currentStock());
        assertEquals(Quantity.of("74.75"), repository().findById(shop, p.id()).orElseThrow().currentStock());
        assertEquals(out, repository().findMovement(shop, out.id()).orElseThrow());
        assertTrue(repository().findMovement(UUID.randomUUID(), out.id()).isEmpty());
        assertEquals(2, repository().movements(shop, p.id(), null, first).total());
        assertEquals(1, repository().movements(shop, p.id(), MovementType.OUT, first).total());
        assertEquals(0, repository().movements(UUID.randomUUID(), p.id(), null, first).total());
    }

    @Test
    void anExitTheStockCannotCoverIsRefusedAndStoresNothing() {
        InventoryProduct p = saved("10", "0");

        assertThrows(StockWouldGoNegative.class, () -> apply(movement(p, MovementType.OUT, "10.01")));

        assertEquals(Quantity.of("10"), repository().findById(shop, p.id()).orElseThrow().currentStock());
        assertEquals(0, repository().movements(shop, p.id(), null, first).total());
    }

    @Test
    void aKeyIsStoredOnlyOnce() {
        InventoryProduct p = saved("10", "0");
        Idempotency.Key key = key("POST /api/v1/inventory/products/{id}/movements");
        repository().applyMovement(shop, movement(p, MovementType.IN, "1"), key);

        assertThrows(KeyTaken.class, () -> repository().applyMovement(shop, movement(p, MovementType.IN, "1"), key));
        assertEquals(Quantity.of("11"), repository().findById(shop, p.id()).orElseThrow().currentStock());
    }

    @Test
    void editingNeverTouchesTheStockAndTheAlertFilterIsComputed() {
        InventoryProduct low = saved("100", "0");
        saved("100", "0");
        low.edit("Mint shampoo", "New", "ml", Quantity.of("150"));
        repository().update(low);

        assertEquals(Quantity.of("100"), repository().findById(shop, low.id()).orElseThrow().currentStock());
        assertEquals(List.of(low.id()), repository().page(shop, true, first).items().stream()
                .map(InventoryProduct::id).toList());
        assertEquals(1, repository().page(shop, false, first).total());
        assertEquals(2, repository().page(shop, null, first).total());
        assertEquals(0, repository().page(UUID.randomUUID(), null, first).total());
    }
}
