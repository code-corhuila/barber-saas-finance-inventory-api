package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FinanceRecord: FR-016, FR-017, DEC-FIN-01 (HU-FIN-001 #10). */
class FinanceRecordTest {

    static final LocalDate DAY = LocalDate.of(2026, 9, 28);

    FinanceRecord record(FinanceRecordType type, String category, long cents, String description) {
        return FinanceRecord.record(UUID.randomUUID(), UUID.randomUUID(), type, category, cents, description, DAY,
                null, Instant.now());
    }

    @Test
    void anIncomeAddsAndAnExpenseSubtractsAlwaysWithAPositiveAmount() {
        assertEquals(8_500_000, record(FinanceRecordType.INCOME, "Cortes", 8_500_000, null).signedCents());
        assertEquals(-8_500_000, record(FinanceRecordType.EXPENSE, "Supplies", 8_500_000, null).signedCents());
    }

    @Test
    void aZeroOrNegativeAmountIsRejected() {
        assertThrows(InvalidValue.class, () -> record(FinanceRecordType.INCOME, "Cortes", 0, null));
        assertThrows(InvalidValue.class, () -> record(FinanceRecordType.EXPENSE, "Cortes", -1, null));
    }

    @Test
    void theCategoryIsRequiredAndTrimmedAndABlankDescriptionIsNull() {
        FinanceRecord r = record(FinanceRecordType.INCOME, "  Cortes ", 1, "   ");

        assertEquals("Cortes", r.category());
        assertNull(r.description());
        assertThrows(InvalidValue.class, () -> record(FinanceRecordType.INCOME, " ", 1, null));
        assertThrows(InvalidValue.class, () -> record(FinanceRecordType.INCOME, "x".repeat(81), 1, null));
        assertThrows(InvalidValue.class, () -> record(FinanceRecordType.INCOME, "Cortes", 1, "x".repeat(256)));
        assertThrows(InvalidValue.class, () -> record(null, "Cortes", 1, null));
    }

    @Test
    void theSummaryProfitMayBeALossAndThePeriodCannotRunBackwards() {
        assertEquals(-50, new FinanceSummary(DAY, DAY, 100, 150).netProfitCents());
        assertThrows(InvalidValue.class, () -> new FinanceSummary(DAY, DAY.minusDays(1), 0, 0));
    }
}
