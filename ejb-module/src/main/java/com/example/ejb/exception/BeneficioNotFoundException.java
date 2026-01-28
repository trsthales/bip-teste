package com.example.ejb.exception;

import jakarta.ejb.ApplicationException;

/**
 * Exceção lançada quando um benefício não é encontrado.
 * ApplicationException com rollback=true garante rollback da transação.
 */
@ApplicationException(rollback = true)
public class BeneficioNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BeneficioNotFoundException(String message) {
        super(message);
    }

    public BeneficioNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeneficioNotFoundException(Long id) {
        super("Benefício não encontrado com ID: " + id);
    }
}
