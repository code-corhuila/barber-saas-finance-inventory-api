package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.ProcessedEvents.AlreadyProcessed;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** processed_event and the automatic income (DEC-FIN-03) against a real PostgreSQL, as JdbcFinanceRepositoryTest. */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcProcessedEventsTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
    private final JdbcProcessedEvents events = new JdbcProcessedEvents(jdbc,
            new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
    private final UUID shop = UUID.randomUUID();

    private FinanceRecord income(UUID appointmentId) {
        return FinanceRecord.record(UUID.randomUUID(), shop, FinanceRecordType.INCOME, "Citas", 3_500_000,
                "Cita completada (10:00 – 10:45)", LocalDate.of(2026, 10, 19), appointmentId, Instant.now());
    }

    @Test
    void theIncomeAndItsEventAreStoredTogetherAndOnlyOnce() {
        UUID event = UUID.randomUUID();
        UUID appointment = UUID.randomUUID();
        assertFalse(events.isProcessed(event));
        assertFalse(events.hasIncomeFor(shop, appointment));

        events.recordIncome(income(appointment), event, "AppointmentCompleted");

        assertTrue(events.isProcessed(event));
        assertTrue(events.hasIncomeFor(shop, appointment));
        assertFalse(events.hasIncomeFor(UUID.randomUUID(), appointment));
        assertThrows(AlreadyProcessed.class, () -> events.recordIncome(income(appointment), event, "AppointmentCompleted"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM finance_inventory.finance_record "
                + "WHERE related_appointment_id = ?", Integer.class, appointment));
    }

    @Test
    void anIgnoredEventIsRememberedWithoutARecord() {
        UUID event = UUID.randomUUID();
        events.markIgnored(event, "AppointmentCompleted");

        assertTrue(events.isProcessed(event));
        assertThrows(AlreadyProcessed.class, () -> events.markIgnored(event, "AppointmentCompleted"));
    }
}
