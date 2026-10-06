package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** finance_inventory.finance_record (06-data/models.md §8) as finance_inventory_app. Every read filters by the barbershop. */
public class JdbcFinanceRepository implements FinanceRepository {

    private static final String COLUMNS = "id, barbershop_id, type, category, amount_cents, description, record_date, "
            + "related_appointment_id, created_at";
    private static final String KEY_PRIMARY_KEY = "pk_idempotency_key";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcFinanceRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<FinanceRecord> findById(UUID tenant, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM finance_inventory.finance_record WHERE barbershop_id = ? AND id = ?",
                (rs, n) -> map(rs), tenant, id).stream().findFirst();
    }

    @Override
    public Page<FinanceRecord> page(UUID tenant, Filter f, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM finance_inventory.finance_record WHERE barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenant));
        period(from, args, f.from(), f.to());
        if (f.type() != null) {
            from.append(" AND type = ?");
            args.add(f.type().name());
        }
        if (f.category() != null) {
            from.append(" AND category = ?");
            args.add(f.category());
        }
        return JdbcPages.page(jdbc, COLUMNS, new JdbcPages.Query(from.toString(), args,
                "ORDER BY record_date DESC, created_at DESC, id"), (rs, n) -> map(rs), page);
    }

    @Override
    public Totals totals(UUID tenant, LocalDate from, LocalDate to) {
        StringBuilder where = new StringBuilder("FROM finance_inventory.finance_record WHERE barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenant));
        period(where, args, from, to);
        return jdbc.queryForObject("SELECT coalesce(sum(amount_cents) FILTER (WHERE type = 'INCOME'), 0) AS income, "
                        + "coalesce(sum(amount_cents) FILTER (WHERE type = 'EXPENSE'), 0) AS expenses " + where,
                (rs, n) -> new Totals(rs.getLong("income"), rs.getLong("expenses")), args.toArray());
    }

    private static void period(StringBuilder where, List<Object> args, LocalDate from, LocalDate to) {
        if (from != null) {
            where.append(" AND record_date >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND record_date <= ?");
            args.add(to);
        }
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return JdbcIdempotency.find(jdbc, key, operation);
    }

    @Override
    public void saveNew(FinanceRecord r, Idempotency.Key key) {
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO finance_inventory.finance_record (" + COLUMNS + ") "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        r.id(), r.barbershopId(), r.type().name(), r.category(), r.amountCents(), r.description(),
                        r.recordDate(), r.relatedAppointmentId(), Timestamp.from(r.createdAt()));
                JdbcIdempotency.insert(jdbc, key, r.id());
            });
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains(KEY_PRIMARY_KEY)) {
                throw new KeyTaken();
            }
            throw e;
        }
    }

    private static FinanceRecord map(ResultSet rs) throws SQLException {
        return new FinanceRecord(rs.getObject("id", UUID.class), rs.getObject("barbershop_id", UUID.class),
                FinanceRecordType.valueOf(rs.getString("type")), rs.getString("category"), rs.getLong("amount_cents"),
                rs.getString("description"), rs.getObject("record_date", LocalDate.class),
                rs.getObject("related_appointment_id", UUID.class), rs.getTimestamp("created_at").toInstant());
    }
}
