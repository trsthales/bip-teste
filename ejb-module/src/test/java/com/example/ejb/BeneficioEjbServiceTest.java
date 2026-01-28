package com.example.ejb;

import com.example.ejb.exception.BeneficioNotFoundException;
import com.example.ejb.exception.SaldoInsuficienteException;
import com.example.ejb.exception.TransferenciaInvalidaException;
import com.example.ejb.model.Beneficio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para BeneficioEjbService.
 * Valida todas as correções implementadas no bug de transferência.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BeneficioEjbService - Testes de Transferência")
class BeneficioEjbServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private BeneficioEjbService service;

    private Beneficio beneficioOrigem;
    private Beneficio beneficioDestino;

    @BeforeEach
    void setUp() {
        beneficioOrigem = new Beneficio("Beneficio A", "Descrição A", new BigDecimal("1000.00"));
        beneficioOrigem.setId(1L);
        beneficioOrigem.setAtivo(true);

        beneficioDestino = new Beneficio("Beneficio B", "Descrição B", new BigDecimal("500.00"));
        beneficioDestino.setId(2L);
        beneficioDestino.setAtivo(true);
    }

    @Test
    @DisplayName("Deve realizar transferência com sucesso quando todos os parâmetros são válidos")
    void deveRealizarTransferenciaComSucesso() {
        // Arrange
        BigDecimal valorTransferencia = new BigDecimal("300.00");
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act
        service.transfer(1L, 2L, valorTransferencia);

        // Assert
        assertEquals(new BigDecimal("700.00"), beneficioOrigem.getValor());
        assertEquals(new BigDecimal("800.00"), beneficioDestino.getValor());
        verify(entityManager, times(2)).merge(any(Beneficio.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando ID de origem é nulo")
    void deveLancarExcecaoQuandoIdOrigemNulo() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(null, 2L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("origem não pode ser nulo"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando ID de destino é nulo")
    void deveLancarExcecaoQuandoIdDestinoNulo() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, null, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("destino não pode ser nulo"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando valor é nulo")
    void deveLancarExcecaoQuandoValorNulo() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 2L, null)
        );
        
        assertTrue(exception.getMessage().contains("Valor da transferência não pode ser nulo"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando valor é zero")
    void deveLancarExcecaoQuandoValorZero() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 2L, BigDecimal.ZERO)
        );
        
        assertTrue(exception.getMessage().contains("maior que zero"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando valor é negativo")
    void deveLancarExcecaoQuandoValorNegativo() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 2L, new BigDecimal("-100.00"))
        );
        
        assertTrue(exception.getMessage().contains("maior que zero"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando IDs são iguais")
    void deveLancarExcecaoQuandoIdsIguais() {
        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 1L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("mesmo benefício"));
        verify(entityManager, never()).find(any(Class.class), any(), any(LockModeType.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando benefício de origem não existe")
    void deveLancarExcecaoQuandoBeneficioOrigemNaoExiste() {
        // Arrange
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(null);

        // Act & Assert
        BeneficioNotFoundException exception = assertThrows(
            BeneficioNotFoundException.class,
            () -> service.transfer(1L, 2L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("ID: 1"));
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando benefício de destino não existe")
    void deveLancarExcecaoQuandoBeneficioDestinoNaoExiste() {
        // Arrange
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(null);

        // Act & Assert
        BeneficioNotFoundException exception = assertThrows(
            BeneficioNotFoundException.class,
            () -> service.transfer(1L, 2L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("ID: 2"));
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando benefício de origem está inativo")
    void deveLancarExcecaoQuandoBeneficioOrigemInativo() {
        // Arrange
        beneficioOrigem.setAtivo(false);
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 2L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("origem está inativo"));
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando benefício de destino está inativo")
    void deveLancarExcecaoQuandoBeneficioDestinoInativo() {
        // Arrange
        beneficioDestino.setAtivo(false);
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act & Assert
        TransferenciaInvalidaException exception = assertThrows(
            TransferenciaInvalidaException.class,
            () -> service.transfer(1L, 2L, new BigDecimal("100.00"))
        );
        
        assertTrue(exception.getMessage().contains("destino está inativo"));
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando saldo é insuficiente - BUG PRINCIPAL CORRIGIDO")
    void deveLancarExcecaoQuandoSaldoInsuficiente() {
        // Arrange
        BigDecimal valorTransferencia = new BigDecimal("1500.00"); // Maior que saldo de 1000
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act & Assert
        SaldoInsuficienteException exception = assertThrows(
            SaldoInsuficienteException.class,
            () -> service.transfer(1L, 2L, valorTransferencia)
        );
        
        assertTrue(exception.getMessage().contains("Saldo insuficiente"));
        // Aceitar tanto formato brasileiro (1.000,00) quanto americano (1000.00)
        assertTrue(exception.getMessage().contains("1") && exception.getMessage().contains("000"));
        assertTrue(exception.getMessage().contains("1") && exception.getMessage().contains("500"));
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Deve usar Pessimistic Locking para prevenir race conditions")
    void deveUsarPessimisticLocking() {
        // Arrange
        BigDecimal valorTransferencia = new BigDecimal("100.00");
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act
        service.transfer(1L, 2L, valorTransferencia);

        // Assert - Verifica que o lock foi aplicado
        verify(entityManager).find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE));
        verify(entityManager).find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE));
    }

    @Test
    @DisplayName("Deve transferir todo o saldo quando valor é igual ao saldo")
    void deveTransferirTodoSaldoQuandoValorIgualAoSaldo() {
        // Arrange
        BigDecimal valorTransferencia = new BigDecimal("1000.00"); // Exatamente o saldo
        when(entityManager.find(eq(Beneficio.class), eq(1L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioOrigem);
        when(entityManager.find(eq(Beneficio.class), eq(2L), eq(LockModeType.PESSIMISTIC_WRITE)))
            .thenReturn(beneficioDestino);

        // Act
        service.transfer(1L, 2L, valorTransferencia);

        // Assert
        assertEquals(BigDecimal.ZERO.setScale(2), beneficioOrigem.getValor().setScale(2));
        assertEquals(new BigDecimal("1500.00"), beneficioDestino.getValor());
        verify(entityManager, times(2)).merge(any(Beneficio.class));
    }
}
