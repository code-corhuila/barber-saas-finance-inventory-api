package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** finance_inventory.finance_record. Every read is scoped by the tenant: another barbershop's id is empty (404). */
public interface FinanceRepository {

    /** Income and expense totals of a period, both dates inclusive. */
    record Totals(long incomeCents, long expensesCents) { }

    Optional<FinanceRecord> findById(UUID tenant, UUID id);

    /** Most recent recordDate first, then most recent createdAt. */
    Page<FinanceRecord> page(UUID tenant, Filter filter, Page.Request page);

    Totals totals(UUID tenant, LocalDate from, LocalDate to);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** The record and its key in ONE transaction (norm 5.3.8). */
    void saveNew(FinanceRecord record, Idempotency.Key key);
}
