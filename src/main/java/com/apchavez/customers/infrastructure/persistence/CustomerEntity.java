package com.apchavez.customers.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("customer")
@AllArgsConstructor
@NoArgsConstructor
public class CustomerEntity {
    @Id
    private Integer id;
    private String nombre;
    private String apellido;
    private String estado;
    private Integer edad;
}
