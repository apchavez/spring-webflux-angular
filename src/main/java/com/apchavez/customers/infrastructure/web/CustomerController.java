package com.apchavez.customers.infrastructure.web;

import com.apchavez.customers.application.CustomerApplicationService;
import com.apchavez.customers.infrastructure.mapper.CustomerMapper;
import com.apchavez.customers.infrastructure.web.dto.CustomerRequestDTO;
import com.apchavez.customers.infrastructure.web.dto.CustomerResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Operaciones de gestión de clientes")
public class CustomerController {

    private final CustomerApplicationService applicationService;
    private final CustomerMapper mapper;

    public CustomerController(CustomerApplicationService applicationService, CustomerMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Crear cliente", description = "Crea un nuevo cliente. El campo id es opcional.")
    public Mono<ResponseEntity<CustomerResponseDTO>> createCustomer(@Valid @RequestBody CustomerRequestDTO dto) {
        return applicationService.createCustomer(mapper.toDomain(dto))
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(saved)));
    }

    @GetMapping("/active")
    @Operation(summary = "Listar clientes activos", description = "Retorna todos los clientes con estado ACTIVE.")
    public Flux<CustomerResponseDTO> listActiveCustomers() {
        return applicationService.listActiveCustomers()
                .map(mapper::toResponseDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID", description = "Retorna el cliente con el ID indicado o 404 si no existe.")
    public Mono<ResponseEntity<CustomerResponseDTO>> findById(@PathVariable Integer id) {
        return applicationService.findById(id)
                .map(customer -> ResponseEntity.ok(mapper.toResponseDTO(customer)));
    }
}
