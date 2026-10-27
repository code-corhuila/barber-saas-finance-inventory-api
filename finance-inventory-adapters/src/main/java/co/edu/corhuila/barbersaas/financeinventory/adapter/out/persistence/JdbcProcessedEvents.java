package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.ProcessedEvents;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** finance_inventory.processed_event and the automatic income, as finance_inventory_app (DEC-FIN-03). */
public class JdbcProcessedEvents implements ProcessedEvents {

    private static final String PRIMARY_KEY = "pk_processed_event";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcProcessedEvents(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM finance_inventory.processed_event WHERE event_id = ?)", Boolean.class,
                eventId));
    }

    @Override
    public boolean hasIncomeFor(UUID tenant, UUID appointmentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM finance_inventory.finance_record "
                + "WHERE barbershop_id = ? AND related_appointment_id = ? AND type = 'INCOME')", Boolean.class,
                tenant, appointmentId));
    }

    @Override
    public void markIgnored(UUID eventId, String eventType) {
        insert(() -> mark(eventId, eventType, "IGNORED"));
    }

    @Override
    public void recordIncome(FinanceRecord r, UUID eventId, String eventType) {
        insert(() -> tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO finance_inventory.finance_record (id, barbershop_id, type, category, amount_cents, "
                            + "description, record_date, related_appointment_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.id(), r.barbershopId(), r.type().name(), r.category(), r.amountCents(), r.description(),
                    r.recordDate(), r.relatedAppointmentId(), Timestamp.from(r.createdAt()));
            mark(eventId, eventType, "PROCESSED");
        }));
    }

    private void mark(UUID eventId, String eventType, String outcome) {
        jdbc.update("INSERT INTO finance_inventory.processed_event (event_id, event_type, outcome) VALUES (?, ?, ?)",
                eventId, eventType, outcome);
    }

    private static void insert(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(PRIMARY_KEY)) {
                throw new AlreadyProcessed();
            }
            throw e;
        }
    }
}
