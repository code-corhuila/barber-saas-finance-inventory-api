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
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.RecordCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-FIN-001 #10 and HU-TENANT-001 #13, with doubles of every outbound port. */
class ManageFinanceTest {

    static final UUID SHOP = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 9, 28);
    static final Page.Request FIRST = new Page.Request(1, 20);

    final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, SHOP, "token");
    final Caller otherOwner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID(), "t");
    final Fakes.Finance repository = new Fakes.Finance();
    final Fakes.Appointments appointments = new Fakes.Appointments();
    final ManageFinance finance = new ManageFinance(repository, appointments, new Fakes.FixedClock(),
            new Fakes.RandomIds());

    RecordCommand command(FinanceRecordType type, long cents, LocalDate day) {
        return new RecordCommand(type, "Cortes", cents, null, day, null);
    }

    FinanceRecord recorded(Caller caller, FinanceRecordType type, long cents, LocalDate day) {
        return finance.record(caller, command(type, cents, day), "key-" + UUID.randomUUID()).value();
    }

    @Test
    void theOwnerRecordsAnEntryOfTheirBarbershopAndItIsInTheSummary() {
        Created<FinanceRecord> created = finance.record(owner, command(FinanceRecordType.INCOME, 450_000, DAY),
                "key-00000001");
        recorded(owner, FinanceRecordType.EXPENSE, 120_000, DAY.plusDays(1));
        recorded(owner, FinanceRecordType.INCOME, 999, DAY.plusMonths(1));

        FinanceSummary summary = finance.summary(owner, DAY, DAY.plusDays(1));

        assertTrue(created.created());
        assertEquals(SHOP, created.value().barbershopId());
        assertEquals(450_000, summary.totalIncomeCents());
        assertEquals(120_000, summary.totalExpensesCents());
        assertEquals(330_000, summary.netProfitCents());
    }

    @Test
    void theSameKeyAndRequestReturnTheRecordAlreadyCreated() {
        FinanceRecord first = finance.record(owner, command(FinanceRecordType.INCOME, 1, DAY), "key-00000002").value();

        Created<FinanceRecord> again = finance.record(owner, command(FinanceRecordType.INCOME, 1, DAY), "key-00000002");

        assertFalse(again.created());
        assertEquals(first.id(), again.value().id());
        assertEquals(1, repository.rows.size());
        assertThrows(IdempotencyKeyReused.class,
                () -> finance.record(owner, command(FinanceRecordType.INCOME, 2, DAY), "key-00000002"));
    }

    @Test
    void aZeroAmountIsRejectedAndNothingIsStored() {
        assertThrows(InvalidValue.class, () -> recorded(owner, FinanceRecordType.INCOME, 0, DAY));
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void aRelatedAppointmentIsCheckedWithTheCallersToken() {
        UUID known = UUID.randomUUID();
        appointments.known.add(known);

        finance.record(owner, new RecordCommand(FinanceRecordType.INCOME, "Cortes", 1, null, DAY, known), "key-00000003");

        assertEquals(List.of(owner), appointments.askedBy);
        assertThrows(NotFound.class, () -> finance.record(owner,
                new RecordCommand(FinanceRecordType.INCOME, "Cortes", 1, null, DAY, UUID.randomUUID()), "key-00000004"));
        assertEquals(1, repository.rows.size());
    }

    @Test
    void anotherBarbershopSeesNothing() {
        FinanceRecord mine = recorded(owner, FinanceRecordType.INCOME, 100, DAY);

        assertThrows(NotFound.class, () -> finance.get(otherOwner, mine.id()));
        assertEquals(0, finance.list(otherOwner, Filter.none(), FIRST).total());
        assertEquals(0, finance.summary(otherOwner, DAY, DAY).totalIncomeCents());
        assertEquals(mine, finance.get(owner, mine.id()));
    }

    @Test
    void theListFiltersByPeriodTypeAndCategoryMostRecentFirst() {
        recorded(owner, FinanceRecordType.INCOME, 1, DAY);
        recorded(owner, FinanceRecordType.INCOME, 2, DAY.plusDays(2));
        recorded(owner, FinanceRecordType.EXPENSE, 3, DAY.plusDays(1));

        Page<FinanceRecord> incomes = finance.list(owner, new Filter(DAY, DAY.plusDays(5), FinanceRecordType.INCOME,
                "Cortes"), FIRST);

        assertEquals(List.of(2L, 1L), incomes.items().stream().map(FinanceRecord::amountCents).toList());
        assertThrows(InvalidValue.class, () -> finance.list(owner, new Filter(DAY, DAY.minusDays(1), null, null), FIRST));
    }

    @Test
    void onlyTheOwnerOfABarbershopMayUseFinance() {
        for (Role role : List.of(Role.BARBER, Role.CLIENT, Role.SUPER_ADMIN, Role.SERVICE)) {
            Caller caller = new Caller(UUID.randomUUID().toString(), role, SHOP, "token");
            assertThrows(Forbidden.class, () -> finance.list(caller, Filter.none(), FIRST));
        }
        Caller withoutShop = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, null, "token");
        assertThrows(Forbidden.class, () -> finance.summary(withoutShop, DAY, DAY));
    }
}
