package com.apchavez.products.infrastructure.web.exception;

public class InvalidImportFileException extends RuntimeException {
    public InvalidImportFileException(String message) {
        super(message);
    }
}
