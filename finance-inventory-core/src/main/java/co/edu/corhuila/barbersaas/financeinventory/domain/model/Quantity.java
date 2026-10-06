package co.edu.corhuila.barbersaas.financeinventory.domain.model;

import co.edu.corhuila.barbersaas.financeinventory.domain.model.DomainException.InvalidValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A stock quantity in the product's unit, with two decimals as numeric(12,2) (DEC-INV-01). It is not
 * money, so it is a decimal and not cents; it is never a floating point number and never negative.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    static final BigDecimal MAX = new BigDecimal("9999999999.99");
    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value);
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidValue("A quantity has at most two decimals");
        }
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new InvalidValue("A quantity is between 0 and " + MAX.toPlainString());
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    Quantity plus(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    Quantity minus(Quantity other) {
        return new Quantity(value.subtract(other.value));
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
