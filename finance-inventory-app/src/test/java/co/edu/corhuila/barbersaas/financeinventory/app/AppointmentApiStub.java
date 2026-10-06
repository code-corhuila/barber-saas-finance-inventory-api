package co.edu.corhuila.barbersaas.financeinventory.app;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** appointment-api on a local port: an appointment answers 200 only to a token that carries its barbershop. */
final class AppointmentApiStub {

    /** Appointment → its barbershop. */
    final Map<UUID, UUID> appointments = new ConcurrentHashMap<>();
    private static final Pattern CLAIM = Pattern.compile("\"barbershopId\":\"([0-9a-f-]{36})\"");
    private final HttpServer server;

    AppointmentApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/api/v1/appointments/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
            UUID shop = barbershopOf(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            int status = shop != null && shop.equals(appointments.get(id)) ? 200 : 404;
            byte[] body = ("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    /** The barbershopId claim of the bearer token, as appointment-api takes it after validating the token. */
    private static UUID barbershopOf(String authorization) {
        String[] parts = authorization.replace("Bearer ", "").split("\\.");
        if (parts.length < 2) {
            return null;
        }
        Matcher m = CLAIM.matcher(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        return m.find() ? UUID.fromString(m.group(1)) : null;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
