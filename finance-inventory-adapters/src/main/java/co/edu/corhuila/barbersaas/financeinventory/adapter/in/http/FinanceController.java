package co.edu.corhuila.barbersaas.financeinventory.adapter.in.http;

import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.Views.FinanceRecordView;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.Views.FinanceSummaryView;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Created;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.Filter;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases.RecordCommand;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecordType;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Finance records: shapes are checked here, rules in the core. */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private static final Set<String> RECORD_FIELDS = Set.of("type", "category", "amountCents", "description",
            "recordDate", "relatedAppointmentId");

    private final FinanceUseCases finance;

    public FinanceController(FinanceUseCases finance) {
        this.finance = finance;
    }

    /** createFinanceRecord: 201 with Location, or 200 when the same Idempotency-Key is retried. */
    @PostMapping("/records")
    public ResponseEntity<FinanceRecordView> record(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                    @RequestBody(required = false) JsonNode body) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody b = JsonBody.of(body, RECORD_FIELDS);
        RecordCommand command = new RecordCommand(b.enumValue("type", FinanceRecordType.class), b.text("category", 80),
                b.cents("amountCents"), b.optionalText("description", 255), b.date("recordDate"),
                b.optionalUuid("relatedAppointmentId"));
        b.validate();
        Created<FinanceRecord> result = finance.record(caller, command, idempotencyKey);
        FinanceRecordView view = FinanceRecordView.of(result.value());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/finance/records/" + view.id())).body(view)
                : ResponseEntity.ok(view);
    }

    @GetMapping("/records")
    public PageView<FinanceRecordView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String category) {
        Filter filter = new Filter(JsonBody.parseDate("from", from), JsonBody.parseDate("to", to),
                JsonBody.parseEnum("type", type, FinanceRecordType.class), category);
        return PageView.of(finance.list(caller, filter, Requests.page(page, limit)), FinanceRecordView::of);
    }

    @GetMapping("/records/{id}")
    public FinanceRecordView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return FinanceRecordView.of(finance.get(caller, id));
    }

    @GetMapping("/summary")
    public FinanceSummaryView summary(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return FinanceSummaryView.of(finance.summary(caller, JsonBody.requiredDate("from", from),
                JsonBody.requiredDate("to", to)));
    }
}
