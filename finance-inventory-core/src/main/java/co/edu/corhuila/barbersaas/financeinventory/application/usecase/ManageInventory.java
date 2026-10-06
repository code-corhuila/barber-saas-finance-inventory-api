package co.edu.corhuila.barbersaas.financeinventory.application.usecase;

import static co.edu.corhuila.barbersaas.financeinventory.application.usecase.ManageFinance.owner;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Created;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Clock;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository.StockWouldGoNegative;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** The inventory use cases (HU-INV-001). The product decides its stock; the database guarantees it. */
public class ManageInventory implements InventoryUseCases {

    static final String CREATE_OPERATION = "POST /api/v1/inventory/products";
    static final String MOVE_OPERATION = "POST /api/v1/inventory/products/{id}/movements";

    private final InventoryRepository products;
    private final Clock clock;
    private final IdGenerator ids;

    public ManageInventory(InventoryRepository products, Clock clock, IdGenerator ids) {
        this.products = products;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Created<InventoryProduct> create(Caller caller, CreateCommand c, String idempotencyKey) {
        UUID tenant = owner(caller);
        String hash = RequestHash.of(tenant, c.name(), c.description(), c.unit(), c.currentStock(),
                c.minStockAlert());
        Function<UUID, Optional<InventoryProduct>> find = id -> products.findById(tenant, id);
        Optional<Created<InventoryProduct>> retried = retry(idempotencyKey, CREATE_OPERATION, hash, find);
        if (retried.isPresent()) {
            return retried.get();
        }
        InventoryProduct product = InventoryProduct.create(ids.next(), tenant, c.name(), c.description(), c.unit(),
                c.currentStock(), c.minStockAlert(), clock.now());
        try {
            products.saveNew(product, new Idempotency.Key(idempotencyKey, CREATE_OPERATION, hash));
        } catch (KeyTaken race) {
            return retry(idempotencyKey, CREATE_OPERATION, hash, find).orElseThrow(IdempotencyKeyReused::new);
        }
        return new Created<>(product, true);
    }

    @Override
    public Page<InventoryProduct> list(Caller caller, Boolean lowStock, Page.Request page) {
        return products.page(owner(caller), lowStock, page);
    }

    @Override
    public InventoryProduct get(Caller caller, UUID id) {
        return product(owner(caller), id);
    }

    @Override
    public InventoryProduct edit(Caller caller, UUID id, EditCommand c) {
        InventoryProduct product = product(owner(caller), id);
        product.edit(c.name(), c.description(), c.unit(), c.minStockAlert());
        products.update(product);
        return product;
    }

    @Override
    public Created<MovementResult> move(Caller caller, UUID productId, MoveCommand c, String idempotencyKey) {
        UUID tenant = owner(caller);
        UUID userId = caller.userId();
        String hash = RequestHash.of(tenant, productId, c.type(), c.quantity(), c.reason());
        Function<UUID, Optional<MovementResult>> find = movementId -> products.findMovement(tenant, movementId)
                .map(m -> new MovementResult(m, product(tenant, m.productId())));
        Optional<Created<MovementResult>> retried = retry(idempotencyKey, MOVE_OPERATION, hash, find);
        if (retried.isPresent()) {
            return retried.get();
        }
        InventoryProduct product = product(tenant, productId);
        StockMovement movement = product.move(ids.next(), c.type(), c.quantity(), c.reason(), userId, clock.now());
        try {
            InventoryProduct stored = products.applyMovement(tenant, movement,
                    new Idempotency.Key(idempotencyKey, MOVE_OPERATION, hash));
            return new Created<>(new MovementResult(movement, stored), true);
        } catch (StockWouldGoNegative race) {
            throw new BusinessRuleViolation("Insufficient stock: another exit took it first");
        } catch (KeyTaken race) {
            return retry(idempotencyKey, MOVE_OPERATION, hash, find).orElseThrow(IdempotencyKeyReused::new);
        }
    }

    @Override
    public Page<StockMovement> movements(Caller caller, UUID productId, MovementType type, Page.Request page) {
        UUID tenant = owner(caller);
        product(tenant, productId);
        return products.movements(tenant, productId, type, page);
    }

    private InventoryProduct product(UUID tenant, UUID id) {
        return products.findById(tenant, id).orElseThrow(() -> new NotFound("Product"));
    }

    /** The same key and request return what was created; the same key with another request is refused. */
    private <T> Optional<Created<T>> retry(String key, String operation, String hash,
                                           Function<UUID, Optional<T>> find) {
        return products.findKey(key, operation).map(stored -> {
            if (!stored.requestHash().equals(hash)) {
                throw new IdempotencyKeyReused();
            }
            return new Created<>(find.apply(stored.resourceId()).orElseThrow(IdempotencyKeyReused::new), false);
        });
    }
}
