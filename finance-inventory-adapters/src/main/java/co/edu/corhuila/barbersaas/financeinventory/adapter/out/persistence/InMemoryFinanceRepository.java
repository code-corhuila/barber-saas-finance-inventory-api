package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryFinanceRepository implements FinanceRepository {

    private final Map<UUID, FinanceRecord> rows = new ConcurrentHashMap<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();

    @Override
    public Optional<FinanceRecord> findById(UUID tenant, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(r -> r.barbershopId().equals(tenant));
    }

    @Override
    public Page<FinanceRecord> page(UUID tenant, Filter f, Page.Request page) {
        return Page.of(inPeriod(tenant, f.from(), f.to())
                .filter(r -> f.type() == null || f.type() == r.type())
                .filter(r -> f.category() == null || f.category().equals(r.category()))
                .sorted(Comparator.comparing(FinanceRecord::recordDate)
                        .thenComparing(FinanceRecord::createdAt).reversed().thenComparing(FinanceRecord::id))
                .toList(), page);
    }

    @Override
    public Totals totals(UUID tenant, LocalDate from, LocalDate to) {
        return new Totals(sum(tenant, from, to, FinanceRecordType.INCOME), sum(tenant, from, to, FinanceRecordType.EXPENSE));
    }

    private long sum(UUID tenant, LocalDate from, LocalDate to, FinanceRecordType type) {
        return inPeriod(tenant, from, to).filter(r -> r.type() == type).mapToLong(FinanceRecord::amountCents).sum();
    }

    private Stream<FinanceRecord> inPeriod(UUID tenant, LocalDate from, LocalDate to) {
        return rows.values().stream().filter(r -> r.barbershopId().equals(tenant))
                .filter(r -> from == null || !r.recordDate().isBefore(from))
                .filter(r -> to == null || !r.recordDate().isAfter(to));
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(key + " " + operation));
    }

    @Override
    public synchronized void saveNew(FinanceRecord record, Idempotency.Key key) {
        String id = key.key() + " " + key.operation();
        if (keys.containsKey(id)) {
            throw new KeyTaken();
        }
        rows.put(record.id(), record);
        keys.put(id, new Idempotency.Stored(record.id(), key.requestHash()));
    }
}
