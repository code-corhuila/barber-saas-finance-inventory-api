package co.edu.corhuila.barbersaas.financeinventory.application.usecase;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Created;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.AppointmentLookup;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Clock;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository.Totals;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Idempotency.KeyTaken;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceSummary;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** The finance use cases (HU-FIN-001). They orchestrate; the record decides its own rules. */
public class ManageFinance implements FinanceUseCases {

    static final String RECORD_OPERATION = "POST /api/v1/finance/records";

    private final FinanceRepository records;
    private final AppointmentLookup appointments;
    private final Clock clock;
    private final IdGenerator ids;

    public ManageFinance(FinanceRepository records, AppointmentLookup appointments, Clock clock, IdGenerator ids) {
        this.records = records;
        this.appointments = appointments;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Created<FinanceRecord> record(Caller caller, RecordCommand c, String idempotencyKey) {
        UUID tenant = owner(caller);
        String hash = RequestHash.of(tenant, c.type(), c.category(), c.amountCents(), c.description(),
                c.recordDate(), c.relatedAppointmentId());
        Optional<Created<FinanceRecord>> retried = retry(tenant, idempotencyKey, hash);
        if (retried.isPresent()) {
            return retried.get();
        }
        FinanceRecord record = FinanceRecord.record(ids.next(), tenant, c.type(), c.category(), c.amountCents(),
                c.description(), c.recordDate(), c.relatedAppointmentId(), clock.now());
        if (c.relatedAppointmentId() != null && !appointments.exists(caller, c.relatedAppointmentId())) {
            throw new NotFound("Appointment");
        }
        try {
            records.saveNew(record, new Idempotency.Key(idempotencyKey, RECORD_OPERATION, hash));
        } catch (KeyTaken race) {
            return retry(tenant, idempotencyKey, hash).orElseThrow(IdempotencyKeyReused::new);
        }
        return new Created<>(record, true);
    }

    /** The same key and request return what was created; the same key with another request is refused. */
    private Optional<Created<FinanceRecord>> retry(UUID tenant, String key, String hash) {
        return records.findKey(key, RECORD_OPERATION).map(stored -> {
            if (!stored.requestHash().equals(hash)) {
                throw new IdempotencyKeyReused();
            }
            return new Created<>(records.findById(tenant, stored.resourceId())
                    .orElseThrow(IdempotencyKeyReused::new), false);
        });
    }

    @Override
    public Page<FinanceRecord> list(Caller caller, Filter filter, Page.Request page) {
        UUID tenant = owner(caller);
        FinanceSummary.checkPeriod(filter.from(), filter.to());
        return records.page(tenant, filter, page);
    }

    @Override
    public FinanceRecord get(Caller caller, UUID id) {
        return records.findById(owner(caller), id).orElseThrow(() -> new NotFound("Finance record"));
    }

    @Override
    public FinanceSummary summary(Caller caller, LocalDate from, LocalDate to) {
        UUID tenant = owner(caller);
        FinanceSummary.checkPeriod(from, to);
        Totals totals = records.totals(tenant, from, to);
        return new FinanceSummary(from, to, totals.incomeCents(), totals.expensesCents());
    }

    /** Every operation is ADMIN_BARBERSHOP only, scoped to the token's barbershop. */
    static UUID owner(Caller caller) {
        caller.require(Role.ADMIN_BARBERSHOP);
        return caller.tenant();
    }
}
