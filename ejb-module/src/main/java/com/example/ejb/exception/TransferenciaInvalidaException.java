package com.example.ejb.exception;

import jakarta.ejb.ApplicationException;

/**
 * Exceção lançada quando os parâmetros de transferência são inválidos.
 * ApplicationException com rollback=true garante rollback da transação.
 */
@ApplicationException(rollback = true)
public class TransferenciaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransferenciaInvalidaException(String message) {
        super(message);
    }

    public TransferenciaInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
