package co.edu.corhuila.barbersaas.financeinventory.application.port.in;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceSummary;
import java.time.LocalDate;
import java.util.UUID;

/** The finance operations of finance-inventory-service.yaml; every one is ADMIN_BARBERSHOP only. */
public interface FinanceUseCases {

    /** What the request carries; the barbershop is the token's, never a field (HU-TENANT-001). */
    record RecordCommand(FinanceRecordType type, String category, long amountCents, String description,
                         LocalDate recordDate, UUID relatedAppointmentId) { }

    /** Optional filters of the list; a period may be open at either end. */
    record Filter(LocalDate from, LocalDate to, FinanceRecordType type, String category) {
        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    /**
     * FR-016. A related appointment must exist in the caller's barbershop (asked to appointment-api).
     * The same Idempotency-Key returns the record already created; with a different request it is refused.
     */
    Created<FinanceRecord> record(Caller caller, RecordCommand command, String idempotencyKey);

    /** Most recent recordDate first. */
    Page<FinanceRecord> list(Caller caller, Filter filter, Page.Request page);

    /** A record of another barbershop is not found (404), the same as one that does not exist. */
    FinanceRecord get(Caller caller, UUID id);

    /** Income, expenses and profit of the period, both dates inclusive. */
    FinanceSummary summary(Caller caller, LocalDate from, LocalDate to);
}
