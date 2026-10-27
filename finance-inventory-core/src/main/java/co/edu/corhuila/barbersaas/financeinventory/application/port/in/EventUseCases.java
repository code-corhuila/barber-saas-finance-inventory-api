package co.edu.corhuila.barbersaas.financeinventory.application.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * POST /internal/v1/events (ADR-016, DEC-FIN-03): the worker delivers one event at a time, at least
 * once. Only the service token of barber-saas-worker.
 */
public interface EventUseCases {

    /** The fields of the EventEnvelope that finance reads; the tenant is the envelope's barbershopId. */
    record IncomingEvent(UUID id, String type, int version, UUID barbershopId, Map<String, Object> payload) { }

    /** EventReceipt of _shared.yaml: every outcome answers 200, so the worker confirms the delivery. */
    enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    record Receipt(UUID eventId, Outcome outcome) { }

    /**
     * AppointmentCompleted records one INCOME of its priceAtBookingCents, category "Citas", dated the day
     * of the appointment and related to it. IGNORED when the price is 0 (a reward coupon) or the owner
     * already recorded an income for that appointment by hand; DUPLICATE when already processed. Any
     * other type, or a payload without its fields, is a BusinessRuleViolation (422) and the worker marks
     * it failed.
     */
    Receipt receive(Caller caller, IncomingEvent event);
}
