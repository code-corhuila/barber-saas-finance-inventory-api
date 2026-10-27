package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.ProcessedEvents;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the automatic income goes to the in-memory records. */
public class InMemoryProcessedEvents implements ProcessedEvents {

    private final InMemoryFinanceRepository records;
    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    public InMemoryProcessedEvents(InMemoryFinanceRepository records) {
        this.records = records;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        return processed.contains(eventId);
    }

    @Override
    public boolean hasIncomeFor(UUID tenant, UUID appointmentId) {
        return records.hasIncomeFor(tenant, appointmentId);
    }

    @Override
    public void markIgnored(UUID eventId, String eventType) {
        if (!processed.add(eventId)) {
            throw new AlreadyProcessed();
        }
    }

    @Override
    public synchronized void recordIncome(FinanceRecord income, UUID eventId, String eventType) {
        if (!processed.add(eventId)) {
            throw new AlreadyProcessed();
        }
        records.add(income);
    }
}
