package co.edu.corhuila.barbersaas.financeinventory.adapter.out.http;

import co.edu.corhuila.barbersaas.financeinventory.application.port.in.Caller;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.AppointmentLookup;
import java.util.UUID;

/** AppointmentLookup over appointment-service.yaml; appointment-api applies the token's tenant itself. */
public class AppointmentApiClient implements AppointmentLookup {

    private final JsonApi api;

    public AppointmentApiClient(String baseUrl) {
        this.api = new JsonApi("appointment-api", baseUrl);
    }

    /** getAppointmentById with the owner's token: 404 for one that does not exist or is of another barbershop. */
    @Override
    public boolean exists(Caller caller, UUID appointmentId) {
        return api.get(caller, "/api/v1/appointments/" + appointmentId).isPresent();
    }
}
