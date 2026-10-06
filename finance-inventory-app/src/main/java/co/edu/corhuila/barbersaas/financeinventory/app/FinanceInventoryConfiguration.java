package co.edu.corhuila.barbersaas.financeinventory.app;

import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.AuthFilter;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.barbersaas.financeinventory.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.http.AppointmentApiClient;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.InMemoryFinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.InMemoryInventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.JdbcFinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.JdbcInventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.SystemClock;
import co.edu.corhuila.barbersaas.financeinventory.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.FinanceUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.in.InventoryUseCases;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.AppointmentLookup;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.FinanceRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.port.out.InventoryRepository;
import co.edu.corhuila.barbersaas.financeinventory.application.usecase.ManageFinance;
import co.edu.corhuila.barbersaas.financeinventory.application.usecase.ManageInventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composition root: the only place that knows every concrete type. The pool and its limits are
 * built here explicitly (norm 5.3.10); the limits of the calls to appointment-api live in its client.
 */
@Configuration
public class FinanceInventoryConfiguration {

    /** One pool as finance_inventory_app for both repositories, or none when DATABASE_URL is empty (in memory). */
    record Database(Optional<DataSource> pool) {

        JdbcTemplate jdbc() {
            return new JdbcTemplate(pool.orElseThrow());
        }

        TransactionTemplate tx() {
            return new TransactionTemplate(new DataSourceTransactionManager(pool.orElseThrow()));
        }
    }

    @Bean
    Database database(@Value("${finance-inventory.database.url:}") String url,
                      @Value("${finance-inventory.database.user:}") String user,
                      @Value("${finance-inventory.database.password:}") String password,
                      @Value("${finance-inventory.database.pool-max:10}") int poolMax,
                      @Value("${finance-inventory.database.statement-timeout-ms:5000}") int statementTimeoutMs) {
        if (url.isBlank()) {
            return new Database(Optional.empty());
        }
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setUsername(user);                                   // finance_inventory_app, never the administrator
        pool.setPassword(password);
        pool.setMaximumPoolSize(poolMax);
        pool.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
        pool.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        pool.setConnectionInitSql("SET statement_timeout = " + statementTimeoutMs);
        return new Database(Optional.of(new HikariDataSource(pool)));
    }

    @Bean
    FinanceRepository financeRepository(Database db) {
        return db.pool().isEmpty() ? new InMemoryFinanceRepository() : new JdbcFinanceRepository(db.jdbc(), db.tx());
    }

    @Bean
    InventoryRepository inventoryRepository(Database db) {
        return db.pool().isEmpty() ? new InMemoryInventoryRepository()
                : new JdbcInventoryRepository(db.jdbc(), db.tx());
    }

    @Bean
    AppointmentLookup appointmentLookup(@Value("${finance-inventory.appointment-api-url}") String url) {
        return new AppointmentApiClient(url);
    }

    @Bean
    FinanceUseCases financeUseCases(FinanceRepository records, AppointmentLookup appointments) {
        return new ManageFinance(records, appointments, new SystemClock(), new UuidGenerator());
    }

    @Bean
    InventoryUseCases inventoryUseCases(InventoryRepository products) {
        return new ManageInventory(products, new SystemClock(), new UuidGenerator());
    }

    /** JWT_PUBLIC_KEY: the PEM itself; a one-line value with literal \n escapes, as an env file holds it, is accepted. */
    @Bean
    Rs256Verifier tokenVerifier(@Value("${JWT_PUBLIC_KEY:}") String pem) {
        return new Rs256Verifier(pem.replace("\\n", "\n"));
    }

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilter() {
        FilterRegistrationBean<CorrelationFilter> bean = new FilterRegistrationBean<>(new CorrelationFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(Rs256Verifier verifier, ObjectMapper json) {
        FilterRegistrationBean<AuthFilter> bean = new FilterRegistrationBean<>(new AuthFilter(verifier, json));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
