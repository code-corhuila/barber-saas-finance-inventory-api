package co.edu.corhuila.barbersaas.financeinventory.application.usecase;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.EventUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Clock;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.ProcessedEvents;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.ProcessedEvents.AlreadyProcessed;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * The automatic income (DEC-FIN-03): AppointmentCompleted, delivered by the worker at least once, records
 * exactly one INCOME per appointment, so the owner does not type what the agenda already knows.
 */
public class HandleEvents implements EventUseCases {

    static final String WORKER = "barber-saas-worker";
    static final String APPOINTMENT_COMPLETED = "AppointmentCompleted";
    /** The category every automatic income carries, so the owner can filter it (FR-016). */
    static final String CATEGORY = "Citas";

    private final ProcessedEvents processed;
    private final Clock clock;
    private final IdGenerator ids;

    public HandleEvents(ProcessedEvents processed, Clock clock, IdGenerator ids) {
        this.processed = processed;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Receipt receive(Caller caller, IncomingEvent e) {
        caller.requireService(WORKER);
        if (!APPOINTMENT_COMPLETED.equals(e.type())) {
            throw new BusinessRuleViolation("finance does not handle " + e.type());
        }
        if (processed.isProcessed(e.id())) {
            return new Receipt(e.id(), Outcome.DUPLICATE);
        }
        UUID appointmentId = uuid(e, "appointmentId");
        long price = price(e);
        if (price == 0 || processed.hasIncomeFor(e.barbershopId(), appointmentId)) {     // a coupon, or typed by hand
            return ignored(e);
        }
        FinanceRecord income = FinanceRecord.record(ids.next(), e.barbershopId(), FinanceRecordType.INCOME, CATEGORY,
                price, description(e), date(e), appointmentId, clock.now());
        try {
            processed.recordIncome(income, e.id(), e.type());
            return new Receipt(e.id(), Outcome.PROCESSED);
        } catch (AlreadyProcessed race) {
            return new Receipt(e.id(), Outcome.DUPLICATE);
        }
    }

    private Receipt ignored(IncomingEvent e) {
        try {
            processed.markIgnored(e.id(), e.type());
            return new Receipt(e.id(), Outcome.IGNORED);
        } catch (AlreadyProcessed race) {
            return new Receipt(e.id(), Outcome.DUPLICATE);
        }
    }

    /** "Cita completada (10:00 – 10:45)": the time tells it apart from another appointment of the day. */
    private static String description(IncomingEvent e) {
        Object start = e.payload().get("startTime");
        Object end = e.payload().get("endTime");
        return start != null && end != null ? "Cita completada (" + start + " – " + end + ")" : "Cita completada";
    }

    private static UUID uuid(IncomingEvent e, String field) {
        try {
            return UUID.fromString(String.valueOf(e.payload().get(field)));
        } catch (IllegalArgumentException bad) {
            throw new BusinessRuleViolation(e.type() + " without a valid " + field);
        }
    }

    private static long price(IncomingEvent e) {
        if (!(e.payload().get("priceAtBookingCents") instanceof Number n) || n.longValue() < 0) {
            throw new BusinessRuleViolation(e.type() + " without a valid priceAtBookingCents");
        }
        return n.longValue();
    }

    private static LocalDate date(IncomingEvent e) {
        try {
            return LocalDate.parse(String.valueOf(e.payload().get("date")));
        } catch (DateTimeParseException bad) {
            throw new BusinessRuleViolation(e.type() + " without a valid date");
        }
    }
}
