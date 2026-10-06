package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;

class InMemoryInventoryRepositoryTest extends InventoryRepositoryContract {

    private final InMemoryInventoryRepository repository = new InMemoryInventoryRepository();

    @Override
    InventoryRepository repository() {
        return repository;
    }
}
