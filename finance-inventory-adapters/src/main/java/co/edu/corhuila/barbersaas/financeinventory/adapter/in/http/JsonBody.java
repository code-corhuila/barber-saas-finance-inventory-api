package co.edu.corhuila.barbersaas.financeinventory.adapter.in.http;

import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import co.edu.corhuila.barbersaas.financeinventory.domain.model.Quantity;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reads a request body against its contract schema: every schema here is additionalProperties: false,
 * so an unknown field answers 400 instead of being ignored — which is how a product edit that carries
 * currentStock is refused (DEC-INV-01). Business rules stay in the domain.
 */
final class JsonBody {

    private final JsonNode node;
    private final List<FieldError> errors = new ArrayList<>();

    private JsonBody(JsonNode node) {
        this.node = node;
    }

    static JsonBody of(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw new ValidationException("the body must be a JSON object", List.of());
        }
        JsonBody body = new JsonBody(node);
        node.fieldNames().forEachRemaining(f -> {
            if (!allowed.contains(f)) {
                body.errors.add(new FieldError(f, "not allowed"));
            }
        });
        return body;
    }

    /** A required string of at most {@code max} characters; blank is left to the domain, which knows the rule. */
    String text(String field, int max) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().length() > max) {
            errors.add(new FieldError(field, v == null ? "required" : "a string of at most " + max + " characters"));
            return null;
        }
        return v.asText();
    }

    String optionalText(String field, int max) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : text(field, max);
    }

    LocalDate date(String field) {
        JsonNode v = node.get(field);
        try {
            return LocalDate.parse(v.asText());
        } catch (RuntimeException e) {
            errors.add(new FieldError(field, v == null ? "required" : "must be a date YYYY-MM-DD"));
            return null;
        }
    }

    UUID optionalUuid(String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(v.asText());
        } catch (RuntimeException e) {
            errors.add(new FieldError(field, "must be a UUID"));
            return null;
        }
    }

    <E extends Enum<E>> E enumValue(String field, Class<E> type) {
        JsonNode v = node.get(field);
        try {
            return Enum.valueOf(type, v.asText());
        } catch (RuntimeException e) {
            errors.add(new FieldError(field, v == null ? "required" : "not an accepted value"));
            return null;
        }
    }

    /** Money in cents (ADR-010): an integer, never a decimal; whether it is positive is the domain's rule. */
    long cents(String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) {
            errors.add(new FieldError(field, v == null ? "required" : "must be an integer amount in cents"));
            return 0;
        }
        return v.asLong();
    }

    /** Quantity of the contract: a number with at most two decimals, never negative. */
    Quantity quantity(String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            errors.add(new FieldError(field, v == null ? "required" : "must be a number"));
            return null;
        }
        try {
            return new Quantity(v.decimalValue());
        } catch (InvalidValue e) {
            errors.add(new FieldError(field, e.getMessage()));
            return null;
        }
    }

    /** Throws every collected error at once, as one 400. */
    void validate() {
        if (!errors.isEmpty()) {
            throw new ValidationException("the request is not valid", errors);
        }
    }

    static LocalDate parseDate(String field, String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(field, "must be a date YYYY-MM-DD");
        }
    }

    static LocalDate requiredDate(String field, String value) {
        if (value == null) {
            throw invalid(field, "required");
        }
        return parseDate(field, value);
    }

    static <E extends Enum<E>> E parseEnum(String field, String value, Class<E> type) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw invalid(field, "not an accepted value");
        }
    }

    private static ValidationException invalid(String field, String message) {
        return new ValidationException("the request is not valid", List.of(new FieldError(field, message)));
    }
}
