package co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence;

import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs against a real PostgreSQL migrated by barber-saas-finance-inventory-db, connected as
 * finance_inventory_app (never the administrator). It does not create the schema: set TEST_DATABASE_URL,
 * TEST_DATABASE_USER and TEST_DATABASE_PASSWORD to run it; without them it is skipped.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcFinanceRepositoryTest extends FinanceRepositoryContract {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
    private final JdbcFinanceRepository repository = new JdbcFinanceRepository(jdbc,
            new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));

    @Override
    FinanceRepository repository() {
        return repository;
    }
}
