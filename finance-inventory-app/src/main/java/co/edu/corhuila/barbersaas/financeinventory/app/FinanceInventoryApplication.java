package co.edu.corhuila.barbersaas.financeinventory.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "co.edu.corhuila.barbersaas.financeinventory")
public class FinanceInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceInventoryApplication.class, args);
    }
}
