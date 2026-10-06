package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An income or an expense of a barbershop (02-domain/entities-and-rules.md, FinanceRecord). The amount
 * is always positive and {@code type} gives the sign (DEC-FIN-01, FR-017). It is never edited nor
 * deleted: a mistake is corrected with a compensating record of the opposite type (DEC-FIN-02).
 */
public record FinanceRecord(UUID id, UUID barbershopId, FinanceRecordType type, String category, long amountCents,
                            String description, LocalDate recordDate, UUID relatedAppointmentId, Instant createdAt) {

    static final int CATEGORY_MAX = 80;
    static final int DESCRIPTION_MAX = 255;

    public FinanceRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(barbershopId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(recordDate);
        Objects.requireNonNull(createdAt);
    }

    /** A new record; every rule of the contract is checked here, not only by the database. */
    public static FinanceRecord record(UUID id, UUID barbershopId, FinanceRecordType type, String category,
                                       long amountCents, String description, LocalDate recordDate,
                                       UUID relatedAppointmentId, Instant now) {
        if (type == null) {
            throw new InvalidValue("type is required");
        }
        if (amountCents < 1) {
            throw new InvalidValue("amountCents must be greater than 0");
        }
        if (recordDate == null) {
            throw new InvalidValue("recordDate is required");
        }
        return new FinanceRecord(id, barbershopId, type, Text.required("category", category, CATEGORY_MAX),
                amountCents, Text.optional("description", description, DESCRIPTION_MAX), recordDate,
                relatedAppointmentId, now);
    }

    /** What the record adds to the period's profit: positive for an income, negative for an expense. */
    public long signedCents() {
        return type == FinanceRecordType.INCOME ? amountCents : -amountCents;
    }
}
