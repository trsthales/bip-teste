package com.example.ejb.exception;

import jakarta.ejb.ApplicationException;
import java.math.BigDecimal;

/**
 * Exceção lançada quando há tentativa de transferência com saldo insuficiente.
 * ApplicationException com rollback=true garante rollback da transação.
 */
@ApplicationException(rollback = true)
public class SaldoInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SaldoInsuficienteException(String message) {
        super(message);
    }

    public SaldoInsuficienteException(Long beneficioId, BigDecimal saldoAtual, BigDecimal valorSolicitado) {
        super(String.format(
            "Saldo insuficiente no benefício ID %d. Saldo atual: R$ %.2f, Valor solicitado: R$ %.2f",
            beneficioId, saldoAtual, valorSolicitado
        ));
    }
}
