package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import java.time.Instant;

/** The current instant; the tests fix it, so createdAt is repeatable. */
public interface Clock {

    Instant now();
}
