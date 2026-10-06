package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.time.LocalDate;

/** Income, expenses and profit of a period, both dates inclusive; the profit may be negative (a loss). */
public record FinanceSummary(LocalDate from, LocalDate to, long totalIncomeCents, long totalExpensesCents) {

    public FinanceSummary {
        checkPeriod(from, to);
    }

    public long netProfitCents() {
        return totalIncomeCents - totalExpensesCents;
    }

    /** {@code to} cannot be before {@code from} (the contract's ToDate). */
    public static void checkPeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidValue("to cannot be before from");
        }
    }
}
