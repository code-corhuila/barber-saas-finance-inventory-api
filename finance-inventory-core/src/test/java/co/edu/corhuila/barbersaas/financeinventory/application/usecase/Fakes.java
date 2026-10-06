package co.edu.corhuila.barbersaas.financeinventory.application.usecase;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.AppointmentLookup;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Clock;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doubles of every outbound port, in memory. */
final class Fakes {

    private Fakes() {
    }

    static final class Keys {
        final Map<String, Idempotency.Stored> rows = new HashMap<>();

        Optional<Idempotency.Stored> find(String key, String operation) {
            return Optional.ofNullable(rows.get(key + " " + operation));
        }

        void put(Idempotency.Key key, UUID resourceId) {
            rows.put(key.key() + " " + key.operation(), new Idempotency.Stored(resourceId, key.requestHash()));
        }
    }

    static final class Finance implements FinanceRepository {
        final Map<UUID, FinanceRecord> rows = new LinkedHashMap<>();
        final Keys keys = new Keys();

        @Override
        public Optional<FinanceRecord> findById(UUID tenant, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(r -> r.barbershopId().equals(tenant));
        }

        @Override
        public Page<FinanceRecord> page(UUID tenant, Filter f, Page.Request page) {
            return Page.of(inPeriod(tenant, f.from(), f.to())
                    .filter(r -> f.type() == null || f.type() == r.type())
                    .filter(r -> f.category() == null || f.category().equals(r.category()))
                    .sorted(Comparator.comparing(FinanceRecord::recordDate).reversed()).toList(), page);
        }

        @Override
        public Totals totals(UUID tenant, LocalDate from, LocalDate to) {
            List<FinanceRecord> all = inPeriod(tenant, from, to).toList();
            return new Totals(sum(all, FinanceRecordType.INCOME), sum(all, FinanceRecordType.EXPENSE));
        }

        private java.util.stream.Stream<FinanceRecord> inPeriod(UUID tenant, LocalDate from, LocalDate to) {
            return rows.values().stream().filter(r -> r.barbershopId().equals(tenant))
                    .filter(r -> from == null || !r.recordDate().isBefore(from))
                    .filter(r -> to == null || !r.recordDate().isAfter(to));
        }

        private static long sum(List<FinanceRecord> all, FinanceRecordType type) {
            return all.stream().filter(r -> r.type() == type).mapToLong(FinanceRecord::amountCents).sum();
        }

        @Override
        public Optional<Idempotency.Stored> findKey(String key, String operation) {
            return keys.find(key, operation);
        }

        @Override
        public void saveNew(FinanceRecord record, Idempotency.Key key) {
            rows.put(record.id(), record);
            keys.put(key, record.id());
        }
    }

    static final class Inventory implements InventoryRepository {
        final Map<UUID, InventoryProduct> rows = new LinkedHashMap<>();
        final List<StockMovement> movements = new ArrayList<>();
        final Keys keys = new Keys();
        /** Simulates a concurrent exit that took the stock after the product was read. */
        boolean loseTheRace;

        @Override
        public Optional<InventoryProduct> findById(UUID tenant, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(p -> p.barbershopId().equals(tenant))
                    .map(p -> InventoryProduct.restore(p.id(), p.barbershopId(), p.name(), p.description(), p.unit(),
                            p.currentStock(), p.minStockAlert(), p.createdAt()));
        }

        @Override
        public Page<InventoryProduct> page(UUID tenant, Boolean lowStock, Page.Request page) {
            return Page.of(rows.values().stream().filter(p -> p.barbershopId().equals(tenant))
                    .filter(p -> lowStock == null || lowStock == p.lowStock()).toList(), page);
        }

        @Override
        public Page<StockMovement> movements(UUID tenant, UUID productId, MovementType type, Page.Request page) {
            return Page.of(movements.stream().filter(m -> m.productId().equals(productId))
                    .filter(m -> findById(tenant, m.productId()).isPresent())
                    .filter(m -> type == null || type == m.type()).toList().reversed(), page);
        }

        @Override
        public Optional<StockMovement> findMovement(UUID tenant, UUID movementId) {
            return movements.stream().filter(m -> m.id().equals(movementId))
                    .filter(m -> findById(tenant, m.productId()).isPresent()).findFirst();
        }

        @Override
        public Optional<Idempotency.Stored> findKey(String key, String operation) {
            return keys.find(key, operation);
        }

        @Override
        public void saveNew(InventoryProduct product, Idempotency.Key key) {
            rows.put(product.id(), product);
            keys.put(key, product.id());
        }

        @Override
        public void update(InventoryProduct product) {
            InventoryProduct stored = rows.get(product.id());
            stored.edit(product.name(), product.description(), product.unit(), product.minStockAlert());
        }

        @Override
        public InventoryProduct applyMovement(UUID tenant, StockMovement m, Idempotency.Key key) {
            if (loseTheRace) {
                throw new StockWouldGoNegative();
            }
            InventoryProduct stored = rows.get(m.productId());
            stored.move(UUID.randomUUID(), m.type(), m.quantity(), m.reason(), m.createdByUserId(), m.createdAt());
            movements.add(m);
            keys.put(key, m.id());
            return findById(tenant, m.productId()).orElseThrow();
        }
    }

    static final class Appointments implements AppointmentLookup {
        final Set<UUID> known = new HashSet<>();
        final List<Caller> askedBy = new ArrayList<>();

        @Override
        public boolean exists(Caller caller, UUID appointmentId) {
            askedBy.add(caller);
            return known.contains(appointmentId);
        }
    }

    static final class FixedClock implements Clock {
        @Override
        public Instant now() {
            return Instant.parse("2026-10-06T14:00:00Z");
        }
    }

    static final class RandomIds implements IdGenerator {
        @Override
        public UUID next() {
            return UUID.randomUUID();
        }
    }
}
