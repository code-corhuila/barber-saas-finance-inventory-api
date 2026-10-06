package co.edu.corhuila.barbersaas.financeinventory.app;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** finance-inventory-service.yaml, tag Inventory, over HTTP (annex C, HU-INV-001 #11, HU-TENANT-001 #13). */
class InventoryHttpTest extends HttpTest {

    private static final String PRODUCTS = "/api/v1/inventory/products";
    private final ObjectMapper json = new ObjectMapper();
    private final UUID shop = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final String owner = bearer(ownerId, "ADMIN_BARBERSHOP", shop);

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, String key, String body) throws Exception {
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String product(String stock, String min) throws Exception {
        String body = "{\"name\":\"Menthol shampoo\",\"unit\":\"ml\",\"currentStock\":" + stock + ",\"minStockAlert\":"
                + min + "}";
        String response = send(post(PRODUCTS), owner, "key-" + UUID.randomUUID(), body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private ResultActions move(String token, String id, String key, String type, String quantity) throws Exception {
        return send(post(PRODUCTS + "/" + id + "/movements"), token, key,
                "{\"movementType\":\"" + type + "\",\"quantity\":" + quantity + ",\"reason\":\"Weekly use\"}");
    }

    @Test
    void aProductIsCreatedWithItsInitialStockAndATwoDecimalQuantity() throws Exception {
        String body = "{\"name\":\"Menthol shampoo\",\"unit\":\"ml\",\"currentStock\":2000,\"minStockAlert\":500.5}";
        ResultActions created = send(post(PRODUCTS), owner, "key-00000001", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStock").value(2000.00))
                .andExpect(jsonPath("$.minStockAlert").value(500.50))
                .andExpect(jsonPath("$.lowStock").value(false))
                .andExpect(jsonPath("$.barbershopId").doesNotExist());
        String id = json.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText();

        created.andExpect(header().string("Location", endsWith(PRODUCTS + "/" + id)));
        send(post(PRODUCTS), owner, "key-00000001", body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        send(post(PRODUCTS), owner, "key-00000002", body.replace("2000", "-1")).andExpect(status().isBadRequest());
        send(post(PRODUCTS), owner, "key-00000003", body.replace("2000", "1.005")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("currentStock"));
    }

    @Test
    void anExitRecomputesTheStockAndTheAlertAndARetryDoesNotMoveItAgain() throws Exception {
        String id = product("600", "500");

        move(owner, id, "key-00000004", "OUT", "150.5")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.movement.movementType").value("OUT"))
                .andExpect(jsonPath("$.movement.createdByUserId").value(ownerId.toString()))
                .andExpect(jsonPath("$.product.currentStock").value(449.5))
                .andExpect(jsonPath("$.product.lowStock").value(true));
        move(owner, id, "key-00000004", "OUT", "150.5").andExpect(status().isOk());

        http.perform(get(PRODUCTS + "/" + id).header("Authorization", owner))
                .andExpect(jsonPath("$.currentStock").value(449.5));
        http.perform(get(PRODUCTS).param("lowStock", "true").header("Authorization", owner))
                .andExpect(jsonPath("$.meta.total").value(1));
        http.perform(get(PRODUCTS + "/" + id + "/movements").param("movementType", "OUT").header("Authorization", owner))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void anExitLargerThanTheStockIs422AndRecordsNothing() throws Exception {
        String id = product("100", "0");

        move(owner, id, "key-00000005", "OUT", "250")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Insufficient stock: 100.00 ml available, 250.00 ml requested"));
        http.perform(get(PRODUCTS + "/" + id + "/movements").header("Authorization", owner))
                .andExpect(jsonPath("$.meta.total").value(0));
        move(owner, id, "key-00000006", "OUT", "0").andExpect(status().isBadRequest());
        move(owner, id, "key-00000007", "SIDEWAYS", "1").andExpect(status().isBadRequest());
    }

    @Test
    void anEditNeverTouchesTheStockAndRefusesCurrentStock() throws Exception {
        String id = product("100", "0");

        send(put(PRODUCTS + "/" + id), owner, null, "{\"name\":\"Shampoo\",\"unit\":\"ml\",\"minStockAlert\":150}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shampoo"))
                .andExpect(jsonPath("$.currentStock").value(100.0))
                .andExpect(jsonPath("$.lowStock").value(true));
        send(put(PRODUCTS + "/" + id), owner, null,
                "{\"name\":\"Shampoo\",\"unit\":\"ml\",\"minStockAlert\":1,\"currentStock\":9}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("currentStock"));
    }

    /** HU-TENANT-001: another barbershop's token never sees, edits or moves this product. */
    @Test
    void anotherBarbershopGets404EvenWithAValidId() throws Exception {
        String id = product("100", "0");
        String intruder = bearer("ADMIN_BARBERSHOP", UUID.randomUUID());

        http.perform(get(PRODUCTS + "/" + id).header("Authorization", intruder)).andExpect(status().isNotFound());
        send(put(PRODUCTS + "/" + id), intruder, null, "{\"name\":\"X\",\"unit\":\"ml\",\"minStockAlert\":0}")
                .andExpect(status().isNotFound());
        move(intruder, id, "key-00000008", "OUT", "100").andExpect(status().isNotFound());
        http.perform(get(PRODUCTS + "/" + id + "/movements").header("Authorization", intruder))
                .andExpect(status().isNotFound());
        http.perform(get(PRODUCTS).header("Authorization", intruder)).andExpect(jsonPath("$.meta.total").value(0));
        http.perform(get(PRODUCTS + "/" + id).header("Authorization", owner)).andExpect(jsonPath("$.currentStock").value(100.0));
    }

    @Test
    void onlyTheOwnerGetsInAndQueryValuesAreChecked() throws Exception {
        http.perform(get(PRODUCTS).header("Authorization", bearer("BARBER", shop))).andExpect(status().isForbidden());
        http.perform(get(PRODUCTS).param("lowStock", "maybe").header("Authorization", owner))
                .andExpect(status().isBadRequest());
        http.perform(get(PRODUCTS).param("limit", "101").header("Authorization", owner)).andExpect(status().isBadRequest());
    }
}
