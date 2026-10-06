package co.edu.corhuila.barbersaas.financeinventory.app;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** finance-inventory-service.yaml, tag Finance records, over HTTP (annex C, HU-FIN-001 #10, HU-TENANT-001 #13). */
class FinanceHttpTest extends HttpTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private final ObjectMapper json = new ObjectMapper();
    private final UUID shop = UUID.randomUUID();
    private final String owner = bearer("ADMIN_BARBERSHOP", shop);

    private String body(String type, String amount, String day) {
        return "{\"type\":\"" + type + "\",\"category\":\"Cortes\",\"amountCents\":" + amount
                + ",\"description\":\"Día\",\"recordDate\":\"" + day + "\"}";
    }

    private ResultActions record(String token, String key, String body) throws Exception {
        return http.perform(post("/api/v1/finance/records").header("Authorization", token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String recordedId(String token, String body) throws Exception {
        String response = record(token, "key-" + UUID.randomUUID(), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    @Test
    void theFirstSendIs201WithLocationAndARetryIs200WithTheSameId() throws Exception {
        ResultActions first = record(owner, "key-00000001", body("INCOME", "450000", "2026-09-01"));
        String created = first
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.amountCents").value(450_000))
                .andExpect(jsonPath("$.recordDate").value("2026-09-01"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.relatedAppointmentId").isEmpty())
                .andExpect(jsonPath("$.barbershopId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();
        first.andExpect(header().string("Location", endsWith("/api/v1/finance/records/" + id)));

        record(owner, "key-00000001", body("INCOME", "450000", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        record(owner, "key-00000001", body("INCOME", "1", "2026-09-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
        http.perform(get("/api/v1/finance/records/" + id).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("Cortes"));
    }

    @Test
    void zeroNegativeOrDecimalMoneyIs400() throws Exception {
        record(owner, "key-00000002", body("INCOME", "0", "2026-09-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        record(owner, "key-00000003", body("EXPENSE", "-5", "2026-09-01")).andExpect(status().isBadRequest());
        record(owner, "key-00000004", body("INCOME", "100.5", "2026-09-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("amountCents"));
    }

    @Test
    void everyInvalidFieldIsNamedIncludingTheIdempotencyKey() throws Exception {
        http.perform(post("/api/v1/finance/records").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body("INCOME", "1", "2026-09-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
        record(owner, "key-00000005", "{\"type\":\"GIFT\",\"recordDate\":\"yesterday\",\"barbershopId\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(5));
        record(owner, "key-00000006", "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void aRelatedAppointmentMustBeOfTheOwnersBarbershop() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        APPOINTMENT_API.appointments.put(mine, shop);
        APPOINTMENT_API.appointments.put(theirs, UUID.randomUUID());
        String withMine = body("INCOME", "25000", "2026-09-01").replace("}", ",\"relatedAppointmentId\":\"" + mine + "\"}");

        record(owner, "key-00000007", withMine).andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedAppointmentId").value(mine.toString()));
        record(owner, "key-00000008", withMine.replace(mine.toString(), theirs.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void theSummaryAndTheListShowOnlyThePeriodOfTheBarbershop() throws Exception {
        recordedId(owner, body("INCOME", "450000", "2026-09-01"));
        recordedId(owner, body("EXPENSE", "120000", "2026-09-30"));
        recordedId(owner, body("INCOME", "999", "2026-10-01"));

        http.perform(get("/api/v1/finance/summary").param("from", "2026-09-01").param("to", "2026-09-30")
                        .header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncomeCents").value(450_000))
                .andExpect(jsonPath("$.totalExpensesCents").value(120_000))
                .andExpect(jsonPath("$.netProfitCents").value(330_000));
        http.perform(get("/api/v1/finance/records").param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("type", "INCOME").header("Authorization", owner))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].amountCents").value(450_000));
        http.perform(get("/api/v1/finance/summary").param("from", "2026-09-30").param("to", "2026-09-01")
                .header("Authorization", owner)).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/finance/summary").header("Authorization", owner)).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/finance/records").param("type", "GIFT").header("Authorization", owner))
                .andExpect(status().isBadRequest());
    }

    /** HU-TENANT-001: another barbershop's token never sees this record nor its totals. */
    @Test
    void anotherBarbershopGets404EvenWithAValidId() throws Exception {
        String id = recordedId(owner, body("INCOME", "450000", "2026-09-01"));
        String intruder = bearer("ADMIN_BARBERSHOP", UUID.randomUUID());

        http.perform(get("/api/v1/finance/records/" + id).header("Authorization", intruder))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
        http.perform(get("/api/v1/finance/records").header("Authorization", intruder))
                .andExpect(jsonPath("$.meta.total").value(0));
        http.perform(get("/api/v1/finance/summary").param("from", "2026-09-01").param("to", "2026-09-01")
                .header("Authorization", intruder)).andExpect(jsonPath("$.totalIncomeCents").value(0));
    }

    @Test
    void onlyTheOwnerGetsIn() throws Exception {
        for (String role : new String[] {"BARBER", "CLIENT"}) {
            http.perform(get("/api/v1/finance/records").header("Authorization", bearer(role, shop)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
        http.perform(get("/api/v1/finance/records").header("Authorization", bearer("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMalformedIdIs400AnUnknownOne404AndAnUnknownRoute404WithTheEnvelope() throws Exception {
        http.perform(get("/api/v1/finance/records/not-a-uuid").header("Authorization", owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        http.perform(get("/api/v1/finance/records/" + UUID.randomUUID()).header("Authorization", owner))
                .andExpect(status().isNotFound());
        http.perform(get("/api/v1/finance/nothing").header("Authorization", owner))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(header().string("X-Correlation-Id", not(emptyOrNullString())));
    }
}
