package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.Quantity;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.StockMovement;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * finance_inventory.inventory_product and inventory_movement (06-data/models.md §8) as
 * finance_inventory_app. A movement reaches the barbershop through its product. The stock changes by a
 * delta in the same statement that checks it: chk_inventory_product_stock is the final guarantee that
 * two concurrent exits cannot take the same stock.
 */
public class JdbcInventoryRepository implements InventoryRepository {

    private static final String PRODUCT = "id, barbershop_id, name, description, unit, current_stock, min_stock_alert, "
            + "created_at";
    private static final String MOVEMENT = "m.id, m.product_id, m.movement_type, m.quantity, m.reason, "
            + "m.created_by_user_id, m.created_at";
    private static final String OF_TENANT = "FROM finance_inventory.inventory_movement m "
            + "JOIN finance_inventory.inventory_product p ON p.id = m.product_id WHERE p.barbershop_id = ?";
    private static final String STOCK_CHECK = "chk_inventory_product_stock";
    private static final String KEY_PRIMARY_KEY = "pk_idempotency_key";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcInventoryRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<InventoryProduct> findById(UUID tenant, UUID id) {
        return jdbc.query("SELECT " + PRODUCT + " FROM finance_inventory.inventory_product "
                + "WHERE barbershop_id = ? AND id = ?", (rs, n) -> product(rs), tenant, id).stream().findFirst();
    }

    @Override
    public Page<InventoryProduct> page(UUID tenant, Boolean lowStock, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM finance_inventory.inventory_product WHERE barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenant));
        if (lowStock != null) {
            from.append(" AND (current_stock <= min_stock_alert) = ?");
            args.add(lowStock);
        }
        return JdbcPages.page(jdbc, PRODUCT, new JdbcPages.Query(from.toString(), args, "ORDER BY created_at DESC, id"),
                (rs, n) -> product(rs), page);
    }

    @Override
    public Page<StockMovement> movements(UUID tenant, UUID productId, MovementType type, Page.Request page) {
        StringBuilder from = new StringBuilder(OF_TENANT + " AND m.product_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenant, productId));
        if (type != null) {
            from.append(" AND m.movement_type = ?");
            args.add(type.name());
        }
        return JdbcPages.page(jdbc, MOVEMENT, new JdbcPages.Query(from.toString(), args,
                "ORDER BY m.created_at DESC, m.id"), (rs, n) -> movement(rs), page);
    }

    @Override
    public Optional<StockMovement> findMovement(UUID tenant, UUID movementId) {
        return jdbc.query("SELECT " + MOVEMENT + " " + OF_TENANT + " AND m.id = ?", (rs, n) -> movement(rs),
                tenant, movementId).stream().findFirst();
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return JdbcIdempotency.find(jdbc, key, operation);
    }

    @Override
    public void saveNew(InventoryProduct p, Idempotency.Key key) {
        write(() -> {
            jdbc.update("INSERT INTO finance_inventory.inventory_product (" + PRODUCT + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    p.id(), p.barbershopId(), p.name(), p.description(), p.unit(), p.currentStock().value(),
                    p.minStockAlert().value(), Timestamp.from(p.createdAt()));
            JdbcIdempotency.insert(jdbc, key, p.id());
        });
    }

    @Override
    public void update(InventoryProduct p) {
        jdbc.update("UPDATE finance_inventory.inventory_product SET name = ?, description = ?, unit = ?, "
                        + "min_stock_alert = ? WHERE barbershop_id = ? AND id = ?",
                p.name(), p.description(), p.unit(), p.minStockAlert().value(), p.barbershopId(), p.id());
    }

    @Override
    public InventoryProduct applyMovement(UUID tenant, StockMovement m, Idempotency.Key key) {
        BigDecimal delta = m.type() == MovementType.IN ? m.quantity().value() : m.quantity().value().negate();
        write(() -> {
            int changed = jdbc.update("UPDATE finance_inventory.inventory_product SET current_stock = current_stock + ? "
                    + "WHERE barbershop_id = ? AND id = ?", delta, tenant, m.productId());
            if (changed != 1) {
                throw new IllegalStateException("The product " + m.productId() + " is not in the barbershop");
            }
            jdbc.update("INSERT INTO finance_inventory.inventory_movement (id, product_id, movement_type, quantity, "
                            + "reason, created_by_user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    m.id(), m.productId(), m.type().name(), m.quantity().value(), m.reason(), m.createdByUserId(),
                    Timestamp.from(m.createdAt()));
            JdbcIdempotency.insert(jdbc, key, m.id());
        });
        return findById(tenant, m.productId()).orElseThrow();
    }

    /** One transaction; the constraint and key violations become what the use case answers with 422. */
    private void write(Runnable statements) {
        try {
            tx.executeWithoutResult(status -> statements.run());
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains(STOCK_CHECK)) {
                throw new StockWouldGoNegative();
            }
            if (message.contains(KEY_PRIMARY_KEY)) {
                throw new KeyTaken();
            }
            throw e;
        }
    }

    private static InventoryProduct product(ResultSet rs) throws SQLException {
        return InventoryProduct.restore(rs.getObject("id", UUID.class), rs.getObject("barbershop_id", UUID.class),
                rs.getString("name"), rs.getString("description"), rs.getString("unit"),
                new Quantity(rs.getBigDecimal("current_stock")), new Quantity(rs.getBigDecimal("min_stock_alert")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static StockMovement movement(ResultSet rs) throws SQLException {
        return new StockMovement(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                MovementType.valueOf(rs.getString("movement_type")), new Quantity(rs.getBigDecimal("quantity")),
                rs.getString("reason"), rs.getObject("created_by_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }
}
