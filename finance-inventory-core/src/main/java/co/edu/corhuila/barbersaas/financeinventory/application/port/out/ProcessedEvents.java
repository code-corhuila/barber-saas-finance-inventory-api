package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import java.util.UUID;

/**
 * finance_inventory.processed_event (06-data/models.md §8): the ids of the events already handled. The
 * row is written in the same transaction as the income it records, so a redelivery finds it.
 */
public interface ProcessedEvents {

    /** Another delivery of the same event was processed first: pk_processed_event. */
    class AlreadyProcessed extends RuntimeException {
        public AlreadyProcessed() {
            super("The event was already processed");
        }
    }

    boolean isProcessed(UUID eventId);

    /** Whether the barbershop already has an INCOME related to that appointment, e.g. one recorded by hand. */
    boolean hasIncomeFor(UUID tenant, UUID appointmentId);

    /**
     * An event with nothing to record: only its processed_event row (outcome IGNORED).
     *
     * @throws AlreadyProcessed when another delivery stored the event first
     */
    void markIgnored(UUID eventId, String eventType);

    /**
     * The income and the processed_event row (outcome PROCESSED) in ONE transaction.
     *
     * @throws AlreadyProcessed when another delivery stored the event first
     */
    void recordIncome(FinanceRecord income, UUID eventId, String eventType);
}
