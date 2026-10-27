package co.edu.corhuila.barbersaas.financeinventory.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** receiveEvent (DEC-FIN-03): a completed appointment becomes an income of the barbershop, once. */
class InternalEventsHttpTest extends HttpTest {

    private static final String WORKER = serviceBearer("barber-saas-worker");
    private final UUID shop = UUID.randomUUID();
    private final String owner = bearer("ADMIN_BARBERSHOP", shop);

    private String completed(UUID eventId, UUID appointmentId, long priceCents) {
        return "{\"id\":\"" + eventId + "\",\"type\":\"AppointmentCompleted\",\"version\":2,\"barbershopId\":\"" + shop
                + "\",\"aggregateId\":\"" + appointmentId + "\",\"payload\":{\"appointmentId\":\"" + appointmentId
                + "\",\"barbershopId\":\"" + shop + "\",\"clientId\":null,\"date\":\"2026-10-19\",\"startTime\":\"10:00\","
                + "\"endTime\":\"10:45\",\"status\":\"COMPLETED\",\"priceAtBookingCents\":" + priceCents
                + ",\"completedBy\":\"" + UUID.randomUUID() + "\"}}";
    }

    private ResultActions deliver(String token, String body) throws Exception {
        return http.perform(post("/internal/v1/events").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions incomes() throws Exception {
        return http.perform(get("/api/v1/finance/records?from=2026-10-19&to=2026-10-19&type=INCOME")
                .header("Authorization", owner));
    }

    @Test
    void aCompletedAppointmentIsRecordedOnceAsAnIncomeOfItsBarbershop() throws Exception {
        UUID event = UUID.randomUUID();
        UUID appointment = UUID.randomUUID();

        deliver(WORKER, completed(event, appointment, 3_500_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.toString()))
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));
        deliver(WORKER, completed(event, appointment, 3_500_000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        incomes().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("INCOME"))
                .andExpect(jsonPath("$.data[0].category").value("Citas"))
                .andExpect(jsonPath("$.data[0].amountCents").value(3_500_000))
                .andExpect(jsonPath("$.data[0].description").value("Cita completada (10:00 – 10:45)"))
                .andExpect(jsonPath("$.data[0].recordDate").value("2026-10-19"))
                .andExpect(jsonPath("$.data[0].relatedAppointmentId").value(appointment.toString()));
        http.perform(get("/api/v1/finance/records").header("Authorization", bearer("ADMIN_BARBERSHOP", UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aFreeAppointmentOrOneAlreadyTypedByHandIsIgnored() throws Exception {
        UUID typed = UUID.randomUUID();
        APPOINTMENT_API.appointments.put(typed, shop);
        http.perform(post("/api/v1/finance/records").header("Authorization", owner).header("Idempotency-Key", "key-typed-01")
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"INCOME\",\"category\":\"Cortes\","
                        + "\"amountCents\":2500000,\"recordDate\":\"2026-10-19\",\"relatedAppointmentId\":\"" + typed + "\"}"))
                .andExpect(status().isCreated());

        deliver(WORKER, completed(UUID.randomUUID(), UUID.randomUUID(), 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("IGNORED"));
        deliver(WORKER, completed(UUID.randomUUID(), typed, 2_500_000))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("IGNORED"));
    }

    @Test
    void onlyTheWorkerDeliversAndOnlyAppointmentCompleted() throws Exception {
        String body = completed(UUID.randomUUID(), UUID.randomUUID(), 1_000_000);
        deliver(owner, body).andExpect(status().isForbidden());
        deliver(serviceBearer("barber-saas-workflow"), body).andExpect(status().isForbidden());
        http.perform(post("/internal/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        deliver(WORKER, body.replace("AppointmentCompleted", "AppointmentCreated"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
        deliver(WORKER, "{\"type\":\"AppointmentCompleted\"}").andExpect(status().isBadRequest());
    }
}
