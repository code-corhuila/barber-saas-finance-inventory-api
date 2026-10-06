package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository.Totals;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What every FinanceRepository must do, in memory or in PostgreSQL. */
abstract class FinanceRepositoryContract {

    final UUID shop = UUID.randomUUID();
    final LocalDate day = LocalDate.of(2026, 9, 1);
    final Page.Request first = new Page.Request(1, 10);

    abstract FinanceRepository repository();

    FinanceRecord saved(UUID tenant, FinanceRecordType type, long cents, LocalDate date) {
        FinanceRecord r = FinanceRecord.record(UUID.randomUUID(), tenant, type, "Cortes", cents, "Note", date,
                UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        repository().saveNew(r, key());
        return r;
    }

    Idempotency.Key key() {
        return new Idempotency.Key("it-" + UUID.randomUUID(), "POST /api/v1/finance/records", "hash");
    }

    @Test
    void aRecordIsStoredWithItsKeyAndReadBackOnlyInItsBarbershop() {
        Idempotency.Key key = key();
        FinanceRecord r = FinanceRecord.record(UUID.randomUUID(), shop, FinanceRecordType.EXPENSE, "Supplies",
                8_500_000, "Blades", day, null, Instant.now().truncatedTo(ChronoUnit.MICROS));

        repository().saveNew(r, key);

        assertEquals(r, repository().findById(shop, r.id()).orElseThrow());
        assertEquals(r.id(), repository().findKey(key.key(), key.operation()).orElseThrow().resourceId());
        assertTrue(repository().findById(UUID.randomUUID(), r.id()).isEmpty(), "another barbershop sees nothing");
    }

    @Test
    void aKeyIsStoredOnlyOnce() {
        Idempotency.Key key = key();
        repository().saveNew(FinanceRecord.record(UUID.randomUUID(), shop, FinanceRecordType.INCOME, "Cortes", 1, null,
                day, null, Instant.now()), key);

        assertThrows(KeyTaken.class, () -> repository().saveNew(FinanceRecord.record(UUID.randomUUID(), shop,
                FinanceRecordType.INCOME, "Cortes", 2, null, day, null, Instant.now()), key));
    }

    @Test
    void aPageFiltersByPeriodAndTypeMostRecentFirst() {
        saved(shop, FinanceRecordType.INCOME, 1, day);
        saved(shop, FinanceRecordType.INCOME, 2, day.plusDays(2));
        saved(shop, FinanceRecordType.EXPENSE, 3, day.plusDays(1));
        saved(shop, FinanceRecordType.INCOME, 4, day.plusDays(10));

        Page<FinanceRecord> incomes = repository().page(shop,
                new Filter(day, day.plusDays(5), FinanceRecordType.INCOME, "Cortes"), first);

        assertEquals(List.of(2L, 1L), incomes.items().stream().map(FinanceRecord::amountCents).toList());
        assertEquals(4, repository().page(shop, Filter.none(), first).total());
        assertEquals(0, repository().page(UUID.randomUUID(), Filter.none(), first).total());
    }

    @Test
    void theTotalsOfAPeriodIncludeBothEndsAndOnlyTheBarbershop() {
        saved(shop, FinanceRecordType.INCOME, 450_000, day);
        saved(shop, FinanceRecordType.EXPENSE, 120_000, day.plusDays(29));
        saved(shop, FinanceRecordType.INCOME, 999, day.plusDays(30));
        saved(UUID.randomUUID(), FinanceRecordType.INCOME, 777, day);

        assertEquals(new Totals(450_000, 120_000), repository().totals(shop, day, day.plusDays(29)));
        assertEquals(new Totals(0, 0), repository().totals(UUID.randomUUID(), day, day));
    }
}
