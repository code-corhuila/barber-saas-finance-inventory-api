package co.edu.corhuila.barbersaas.financeinventory.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Created;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.CreateCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.EditCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.MoveCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.MovementResult;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.Quantity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-INV-001 #11 and HU-TENANT-001 #13, with doubles of every outbound port. */
class ManageInventoryTest {

    static final UUID SHOP = UUID.randomUUID();
    static final Page.Request FIRST = new Page.Request(1, 20);

    final UUID ownerId = UUID.randomUUID();
    final Caller owner = new Caller(ownerId.toString(), Role.ADMIN_BARBERSHOP, SHOP, "token");
    final Caller otherOwner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID(), "t");
    final Fakes.Inventory repository = new Fakes.Inventory();
    final ManageInventory inventory = new ManageInventory(repository, new Fakes.FixedClock(), new Fakes.RandomIds());

    InventoryProduct shampoo(String stock, String min) {
        return inventory.create(owner, new CreateCommand("Menthol shampoo", null, "ml", Quantity.of(stock),
                Quantity.of(min)), "key-" + UUID.randomUUID()).value();
    }

    MoveCommand out(String quantity) {
        return new MoveCommand(MovementType.OUT, Quantity.of(quantity), "Weekly use");
    }

    @Test
    void anExitRecomputesTheStockAndTheAlertAndRecordsWhoDidIt() {
        InventoryProduct p = shampoo("600", "500");

        Created<MovementResult> moved = inventory.move(owner, p.id(), out("150"), "key-00000001");

        assertTrue(moved.created());
        assertEquals(Quantity.of("450"), moved.value().product().currentStock());
        assertTrue(moved.value().product().lowStock());
        assertEquals(ownerId, moved.value().movement().createdByUserId());
        assertEquals(List.of(p.id()), inventory.list(owner, true, FIRST).items().stream()
                .map(InventoryProduct::id).toList());
    }

    @Test
    void anExitLargerThanTheStockIsRefusedAndRecordsNothing() {
        InventoryProduct p = shampoo("100", "0");

        assertThrows(BusinessRuleViolation.class, () -> inventory.move(owner, p.id(), out("250"), "key-00000002"));

        assertTrue(repository.movements.isEmpty());
        assertEquals(Quantity.of("100"), inventory.get(owner, p.id()).currentStock());
    }

    @Test
    void losingTheRaceToAConcurrentExitIsABusinessRuleViolation() {
        InventoryProduct p = shampoo("100", "0");
        repository.loseTheRace = true;

        assertThrows(BusinessRuleViolation.class, () -> inventory.move(owner, p.id(), out("80"), "key-00000003"));
    }

    @Test
    void aRetriedMovementDoesNotMoveTheStockAgain() {
        InventoryProduct p = shampoo("100", "0");
        MovementResult first = inventory.move(owner, p.id(), out("30"), "key-00000004").value();

        Created<MovementResult> again = inventory.move(owner, p.id(), out("30"), "key-00000004");

        assertFalse(again.created());
        assertEquals(first.movement().id(), again.value().movement().id());
        assertEquals(Quantity.of("70"), inventory.get(owner, p.id()).currentStock());
        assertThrows(IdempotencyKeyReused.class, () -> inventory.move(owner, p.id(), out("31"), "key-00000004"));
    }

    @Test
    void editingKeepsTheStock() {
        InventoryProduct p = shampoo("100", "0");

        InventoryProduct edited = inventory.edit(owner, p.id(), new EditCommand("Shampoo", "Mint", "ml",
                Quantity.of("150")));

        assertEquals(Quantity.of("100"), edited.currentStock());
        assertTrue(inventory.get(owner, p.id()).lowStock());
    }

    @Test
    void anotherBarbershopSeesAndMovesNothing() {
        InventoryProduct p = shampoo("100", "0");
        inventory.move(owner, p.id(), out("10"), "key-00000005");

        assertThrows(NotFound.class, () -> inventory.get(otherOwner, p.id()));
        assertThrows(NotFound.class, () -> inventory.edit(otherOwner, p.id(), new EditCommand("X", null, "ml",
                Quantity.ZERO)));
        assertThrows(NotFound.class, () -> inventory.move(otherOwner, p.id(), out("1"), "key-00000006"));
        assertThrows(NotFound.class, () -> inventory.movements(otherOwner, p.id(), null, FIRST));
        assertEquals(0, inventory.list(otherOwner, null, FIRST).total());
        assertEquals(Quantity.of("90"), inventory.get(owner, p.id()).currentStock());
    }

    @Test
    void theMovementsOfAProductAreMostRecentFirstAndFilterByDirection() {
        InventoryProduct p = shampoo("100", "0");
        inventory.move(owner, p.id(), out("10"), "key-00000007");
        inventory.move(owner, p.id(), new MoveCommand(MovementType.IN, Quantity.of("5"), null), "key-00000008");

        assertEquals(List.of(MovementType.IN, MovementType.OUT), inventory.movements(owner, p.id(), null, FIRST)
                .items().stream().map(m -> m.type()).toList());
        assertEquals(1, inventory.movements(owner, p.id(), MovementType.OUT, FIRST).total());
    }

    @Test
    void onlyTheOwnerOfABarbershopMayUseTheInventory() {
        Caller barber = new Caller(UUID.randomUUID().toString(), Role.BARBER, SHOP, "token");

        assertThrows(Forbidden.class, () -> inventory.list(barber, null, FIRST));
        assertTrue(repository.rows.isEmpty());
    }
}
