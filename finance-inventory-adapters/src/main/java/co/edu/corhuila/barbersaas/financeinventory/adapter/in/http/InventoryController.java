package co.edu.corhuila.barbersaas.financeinventory.adapter.in.http;

import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.InventoryViews.MovementResultView;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.InventoryViews.MovementView;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.InventoryViews.ProductView;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Created;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.CreateCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.EditCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.MoveCommand;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases.MovementResult;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.InventoryProduct;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.MovementType;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Inventory: shapes are checked here, rules in the core. */
@RestController
@RequestMapping("/api/v1/inventory/products")
public class InventoryController {

    private static final Set<String> CREATE_FIELDS = Set.of("name", "description", "unit", "currentStock",
            "minStockAlert");
    /** No currentStock: an edit that carries it is a 400 (DEC-INV-01). */
    private static final Set<String> EDIT_FIELDS = Set.of("name", "description", "unit", "minStockAlert");
    private static final Set<String> MOVE_FIELDS = Set.of("movementType", "quantity", "reason");

    private final InventoryUseCases inventory;

    public InventoryController(InventoryUseCases inventory) {
        this.inventory = inventory;
    }

    /** createProduct: 201 with Location, or 200 when the same Idempotency-Key is retried. */
    @PostMapping
    public ResponseEntity<ProductView> create(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                              @RequestBody(required = false) JsonNode body) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody b = JsonBody.of(body, CREATE_FIELDS);
        CreateCommand command = new CreateCommand(b.text("name", 120), b.optionalText("description", 255),
                b.text("unit", 20), b.quantity("currentStock"), b.quantity("minStockAlert"));
        b.validate();
        Created<InventoryProduct> result = inventory.create(caller, command, idempotencyKey);
        ProductView view = ProductView.of(result.value());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/inventory/products/" + view.id())).body(view)
                : ResponseEntity.ok(view);
    }

    @GetMapping
    public PageView<ProductView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) Boolean lowStock) {
        return PageView.of(inventory.list(caller, lowStock, Requests.page(page, limit)), ProductView::of);
    }

    @GetMapping("/{id}")
    public ProductView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return ProductView.of(inventory.get(caller, id));
    }

    @PutMapping("/{id}")
    public ProductView edit(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id,
                            @RequestBody(required = false) JsonNode body) {
        JsonBody b = JsonBody.of(body, EDIT_FIELDS);
        EditCommand command = new EditCommand(b.text("name", 120), b.optionalText("description", 255),
                b.text("unit", 20), b.quantity("minStockAlert"));
        b.validate();
        return ProductView.of(inventory.edit(caller, id, command));
    }

    /** registerStockMovement: 201 with Location, or 200 when retried; the stock does not move twice. */
    @PostMapping("/{id}/movements")
    public ResponseEntity<MovementResultView> move(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                   @PathVariable UUID id,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                   @RequestBody(required = false) JsonNode body) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody b = JsonBody.of(body, MOVE_FIELDS);
        MoveCommand command = new MoveCommand(b.enumValue("movementType", MovementType.class), b.quantity("quantity"),
                b.optionalText("reason", 255));
        b.validate();
        Created<MovementResult> result = inventory.move(caller, id, command, idempotencyKey);
        MovementResultView view = MovementResultView.of(result.value());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/inventory/products/" + id + "/movements/"
                        + view.movement().id())).body(view)
                : ResponseEntity.ok(view);
    }

    @GetMapping("/{id}/movements")
    public PageView<MovementView> movements(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                            @PathVariable UUID id,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) String movementType) {
        MovementType type = JsonBody.parseEnum("movementType", movementType, MovementType.class);
        return PageView.of(inventory.movements(caller, id, type, Requests.page(page, limit)), MovementView::of);
    }
}
