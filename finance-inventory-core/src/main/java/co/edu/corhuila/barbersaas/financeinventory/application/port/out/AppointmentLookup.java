package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import java.util.UUID;

/**
 * What finance needs from the appointment domain, asked through appointment-api and never its
 * database (golden rule 8, Annex J J.3.3). appointment-api applies the caller's tenant itself.
 */
public interface AppointmentLookup {

    /** False when the appointment does not exist or belongs to another barbershop (the record answers 404). */
    boolean exists(Caller caller, UUID appointmentId);
}
