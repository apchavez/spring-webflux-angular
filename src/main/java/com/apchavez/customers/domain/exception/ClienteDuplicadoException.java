package com.apchavez.customers.domain.exception;

public class ClienteDuplicadoException extends ClienteDominioException {
    public ClienteDuplicadoException(Integer id) {
        super("Ya existe un cliente con el ID: " + id);
    }
}
