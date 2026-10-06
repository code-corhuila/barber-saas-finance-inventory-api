package co.edu.corhuila.barbersaas.financeinventory.adapter.in.http;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Page;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceRecord;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.FinanceSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** The response schemas of finance-inventory-service.yaml, never the entity itself (norm 5.3.4). */
final class Views {

    private Views() {
    }

    record Meta(int page, int limit, long total, long totalPages) { }

    record PageView<T>(List<T> data, Meta meta) {
        static <D, T> PageView<T> of(Page<D> page, Function<D, T> view) {
            return new PageView<>(page.items().stream().map(view).toList(),
                    new Meta(page.page(), page.limit(), page.total(), page.totalPages()));
        }
    }

    /** FinanceRecord: no barbershopId, which is the token's. */
    record FinanceRecordView(UUID id, String type, String category, long amountCents, String description,
                             LocalDate recordDate, UUID relatedAppointmentId, Instant createdAt) {
        static FinanceRecordView of(FinanceRecord r) {
            return new FinanceRecordView(r.id(), r.type().name(), r.category(), r.amountCents(), r.description(),
                    r.recordDate(), r.relatedAppointmentId(), r.createdAt());
        }
    }

    record FinanceSummaryView(LocalDate from, LocalDate to, long totalIncomeCents, long totalExpensesCents,
                              long netProfitCents) {
        static FinanceSummaryView of(FinanceSummary s) {
            return new FinanceSummaryView(s.from(), s.to(), s.totalIncomeCents(), s.totalExpensesCents(),
                    s.netProfitCents());
        }
    }
}
