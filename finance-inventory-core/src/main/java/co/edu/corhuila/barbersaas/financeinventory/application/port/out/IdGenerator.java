package co.edu.corhuila.barbersaas.financeinventory.application.port.out;

import java.util.UUID;

public interface IdGenerator {

    UUID next();
}
