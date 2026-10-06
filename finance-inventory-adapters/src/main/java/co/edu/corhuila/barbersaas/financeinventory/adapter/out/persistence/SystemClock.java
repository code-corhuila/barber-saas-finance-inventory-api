package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.Clock;
import java.time.Instant;

/** The machine's clock. */
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
