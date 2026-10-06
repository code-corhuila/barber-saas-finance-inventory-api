package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Used when DATABASE_URL is empty. It stores copies, so a product changes only through these methods;
 * writes are synchronized so the stock rule holds as it does in PostgreSQL.
 */
public class InMemoryInventoryRepository implements InventoryRepository {

    private final Map<UUID, InventoryProduct> rows = new ConcurrentHashMap<>();
    private final List<StockMovement> movements = new CopyOnWriteArrayList<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();

    @Override
    public Optional<InventoryProduct> findById(UUID tenant, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(p -> p.barbershopId().equals(tenant)).map(this::copy);
    }

    @Override
    public Page<InventoryProduct> page(UUID tenant, Boolean lowStock, Page.Request page) {
        return Page.of(rows.values().stream().filter(p -> p.barbershopId().equals(tenant))
                .filter(p -> lowStock == null || lowStock == p.lowStock())
                .sorted(Comparator.comparing(InventoryProduct::createdAt).reversed().thenComparing(InventoryProduct::id))
                .map(this::copy).toList(), page);
    }

    @Override
    public Page<StockMovement> movements(UUID tenant, UUID productId, MovementType type, Page.Request page) {
        return Page.of(movements.stream().filter(m -> m.productId().equals(productId) && ofTenant(tenant, m))
                .filter(m -> type == null || type == m.type())
                .sorted(Comparator.comparing(StockMovement::createdAt).reversed().thenComparing(StockMovement::id))
                .toList(), page);
    }

    @Override
    public Optional<StockMovement> findMovement(UUID tenant, UUID movementId) {
        return movements.stream().filter(m -> m.id().equals(movementId) && ofTenant(tenant, m)).findFirst();
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(key + " " + operation));
    }

    @Override
    public synchronized void saveNew(InventoryProduct product, Idempotency.Key key) {
        store(key, product.id());
        rows.put(product.id(), copy(product));
    }

    @Override
    public synchronized void update(InventoryProduct product) {
        rows.get(product.id()).edit(product.name(), product.description(), product.unit(), product.minStockAlert());
    }

    @Override
    public synchronized InventoryProduct applyMovement(UUID tenant, StockMovement m, Idempotency.Key key) {
        if (keys.containsKey(key.key() + " " + key.operation())) {
            throw new KeyTaken();
        }
        InventoryProduct stored = rows.get(m.productId());
        try {
            stored.move(m.id(), m.type(), m.quantity(), m.reason(), m.createdByUserId(), m.createdAt());
        } catch (BusinessRuleViolation e) {
            throw new StockWouldGoNegative();
        }
        movements.add(m);
        store(key, m.id());
        return copy(stored);
    }

    private void store(Idempotency.Key key, UUID resourceId) {
        if (keys.putIfAbsent(key.key() + " " + key.operation(), new Idempotency.Stored(resourceId, key.requestHash()))
                != null) {
            throw new KeyTaken();
        }
    }

    private boolean ofTenant(UUID tenant, StockMovement m) {
        InventoryProduct p = rows.get(m.productId());
        return p != null && p.barbershopId().equals(tenant);
    }

    private InventoryProduct copy(InventoryProduct p) {
        return InventoryProduct.restore(p.id(), p.barbershopId(), p.name(), p.description(), p.unit(), p.currentStock(),
                p.minStockAlert(), p.createdAt());
    }
}
