package com.example.ejb;

import com.example.ejb.exception.BeneficioNotFoundException;
import com.example.ejb.exception.SaldoInsuficienteException;
import com.example.ejb.exception.TransferenciaInvalidaException;
import com.example.ejb.model.Beneficio;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço EJB para operações de transferência entre benefícios.
 * Implementa controle de concorrência via Pessimistic Locking.
 */
@Stateless
public class BeneficioEjbService {

    private static final Logger LOGGER = Logger.getLogger(BeneficioEjbService.class.getName());

    @PersistenceContext
    private EntityManager em;

    /**
     * Realiza transferência de valor entre dois benefícios.
     * 
     * CORREÇÕES IMPLEMENTADAS:
     * - Validação de parâmetros (nulls, valores negativos, IDs iguais)
     * - Pessimistic Write Lock para prevenir race conditions
     * - Validação de existência dos benefícios
     * - Validação de saldo suficiente
     * - Logging de operações
     * - Exceções customizadas com rollback automático
     * 
     * @param fromId ID do benefício de origem
     * @param toId ID do benefício de destino
     * @param amount Valor a ser transferido
     * @throws TransferenciaInvalidaException se parâmetros inválidos
     * @throws BeneficioNotFoundException se benefício não encontrado
     * @throws SaldoInsuficienteException se saldo insuficiente
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        LOGGER.log(Level.INFO, "Iniciando transferência: FROM={0}, TO={1}, AMOUNT={2}", 
                   new Object[]{fromId, toId, amount});

        // VALIDAÇÃO 1: Parâmetros não podem ser nulos
        validateTransferParameters(fromId, toId, amount);

        // VALIDAÇÃO 2: Valor deve ser positivo
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferenciaInvalidaException(
                "Valor da transferência deve ser maior que zero. Valor informado: " + amount
            );
        }

        // VALIDAÇÃO 3: IDs não podem ser iguais
        if (fromId.equals(toId)) {
            throw new TransferenciaInvalidaException(
                "Não é permitido transferir para o mesmo benefício. ID: " + fromId
            );
        }

        // PESSIMISTIC LOCKING: Previne race conditions e lost updates
        // O lock é mantido até o fim da transação
        Beneficio from = em.find(Beneficio.class, fromId, LockModeType.PESSIMISTIC_WRITE);
        Beneficio to = em.find(Beneficio.class, toId, LockModeType.PESSIMISTIC_WRITE);

        // VALIDAÇÃO 4: Benefícios devem existir
        if (from == null) {
            throw new BeneficioNotFoundException(fromId);
        }
        if (to == null) {
            throw new BeneficioNotFoundException(toId);
        }

        // VALIDAÇÃO 5: Benefícios devem estar ativos
        if (!Boolean.TRUE.equals(from.getAtivo())) {
            throw new TransferenciaInvalidaException(
                "Benefício de origem está inativo. ID: " + fromId
            );
        }
        if (!Boolean.TRUE.equals(to.getAtivo())) {
            throw new TransferenciaInvalidaException(
                "Benefício de destino está inativo. ID: " + toId
            );
        }

        // VALIDAÇÃO 6: Saldo suficiente (CORREÇÃO DO BUG PRINCIPAL)
        if (from.getValor().compareTo(amount) < 0) {
            throw new SaldoInsuficienteException(fromId, from.getValor(), amount);
        }

        // Executar transferência
        BigDecimal novoSaldoFrom = from.getValor().subtract(amount);
        BigDecimal novoSaldoTo = to.getValor().add(amount);

        from.setValor(novoSaldoFrom);
        to.setValor(novoSaldoTo);

        // Merge atualiza as entidades no banco
        em.merge(from);
        em.merge(to);

        LOGGER.log(Level.INFO, 
            "Transferência concluída com sucesso: FROM={0} (novo saldo: {1}), TO={2} (novo saldo: {3})", 
            new Object[]{fromId, novoSaldoFrom, toId, novoSaldoTo}
        );
    }

    /**
     * Valida parâmetros básicos da transferência.
     */
    private void validateTransferParameters(Long fromId, Long toId, BigDecimal amount) {
        if (fromId == null) {
            throw new TransferenciaInvalidaException("ID do benefício de origem não pode ser nulo");
        }
        if (toId == null) {
            throw new TransferenciaInvalidaException("ID do benefício de destino não pode ser nulo");
        }
        if (amount == null) {
            throw new TransferenciaInvalidaException("Valor da transferência não pode ser nulo");
        }
    }
}
