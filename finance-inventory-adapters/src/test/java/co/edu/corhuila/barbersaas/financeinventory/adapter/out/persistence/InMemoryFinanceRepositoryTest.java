package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;

class InMemoryFinanceRepositoryTest extends FinanceRepositoryContract {

    private final InMemoryFinanceRepository repository = new InMemoryFinanceRepository();

    @Override
    FinanceRepository repository() {
        return repository;
    }
}
