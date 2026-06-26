package com.apchavez.customers.infrastructure.persistence;

import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.model.CustomerState;
import com.apchavez.customers.infrastructure.mapper.CustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import(CustomerMapper.class)
class CustomerPersistenceAdapterTest {

    @Autowired
    private CustomerR2dbcRepository r2dbcRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private CustomerMapper mapper;

    private CustomerPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerPersistenceAdapter(r2dbcRepository, mapper);

        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS customer (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nombre VARCHAR(255) NOT NULL,
                    apellido VARCHAR(255) NOT NULL,
                    estado VARCHAR(50) NOT NULL,
                    edad INT NOT NULL
                )
                """)
                .then()
                .then(r2dbcRepository.deleteAll())
                .block();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_shouldPersistCustomerAndReturnWithGeneratedId() {
        Customer customer = new Customer(null, "Alex", "Prieto", CustomerState.ACTIVE, 30);

        StepVerifier.create(adapter.save(customer))
                .assertNext(saved -> {
                    assertThat(saved.id()).isNotNull();
                    assertThat(saved.nombre()).isEqualTo("Alex");
                    assertThat(saved.apellido()).isEqualTo("Prieto");
                    assertThat(saved.estado()).isEqualTo(CustomerState.ACTIVE);
                    assertThat(saved.edad()).isEqualTo(30);
                })
                .verifyComplete();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturnCustomer_whenExists() {
        CustomerEntity entity = r2dbcRepository
                .save(new CustomerEntity(null, "Carlos", "Lopez", "ACTIVE", 22))
                .block();

        StepVerifier.create(adapter.findById(entity.getId()))
                .assertNext(found -> {
                    assertThat(found.id()).isEqualTo(entity.getId());
                    assertThat(found.nombre()).isEqualTo("Carlos");
                    assertThat(found.estado()).isEqualTo(CustomerState.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        StepVerifier.create(adapter.findById(9999))
                .verifyComplete();
    }

    // ── findAllActive ─────────────────────────────────────────────────────────

    @Test
    void findAllActive_shouldReturnOnlyActiveCustomers() {
        r2dbcRepository.save(new CustomerEntity(null, "Carlos", "Lopez", "ACTIVE", 22)).block();
        r2dbcRepository.save(new CustomerEntity(null, "Maria", "Gomez", "INACTIVE", 20)).block();
        r2dbcRepository.save(new CustomerEntity(null, "Ana", "Diaz", "ACTIVE", 30)).block();

        StepVerifier.create(adapter.findAllActive())
                .assertNext(c -> assertThat(c.estado()).isEqualTo(CustomerState.ACTIVE))
                .assertNext(c -> assertThat(c.estado()).isEqualTo(CustomerState.ACTIVE))
                .verifyComplete();
    }

    @Test
    void findAllActive_shouldReturnEmpty_whenNoActiveCustomers() {
        r2dbcRepository.save(new CustomerEntity(null, "Maria", "Gomez", "INACTIVE", 20)).block();

        StepVerifier.create(adapter.findAllActive())
                .verifyComplete();
    }
}
