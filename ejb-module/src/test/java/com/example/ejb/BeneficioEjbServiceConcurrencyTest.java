package com.example.ejb;

import com.example.ejb.exception.SaldoInsuficienteException;
import com.example.ejb.model.Beneficio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de concorrência para validar que o Pessimistic Locking
 * previne race conditions e lost updates durante transferências simultâneas.
 * 
 * IMPORTANTE: Este teste requer um banco de dados real (não mock)
 * para validar o comportamento de locking.
 */
@DisplayName("BeneficioEjbService - Testes de Concorrência")
class BeneficioEjbServiceConcurrencyTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private BeneficioEjbService service;

    @BeforeAll
    static void setUpClass() {
        // Configurar EntityManagerFactory para testes
        // Em ambiente real, usar persistence.xml configurado
        try {
            emf = Persistence.createEntityManagerFactory("test-pu");
        } catch (Exception e) {
            System.err.println("AVISO: EntityManagerFactory não disponível. Testes de concorrência serão ignorados.");
        }
    }

    @AfterAll
    static void tearDownClass() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @BeforeEach
    void setUp() {
        if (emf == null) {
            return; // Skip se EMF não disponível
        }
        
        em = emf.createEntityManager();
        service = new BeneficioEjbService();
        // Injetar EntityManager via reflection ou setter para testes
        injectEntityManager(service, em);
        
        // Preparar dados de teste
        em.getTransaction().begin();
        Beneficio b1 = new Beneficio("Beneficio Concorrente A", "Teste", new BigDecimal("10000.00"));
        Beneficio b2 = new Beneficio("Beneficio Concorrente B", "Teste", new BigDecimal("5000.00"));
        em.persist(b1);
        em.persist(b2);
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    @Test
    @DisplayName("Deve prevenir lost updates com múltiplas transferências simultâneas")
    void devePrevenirlostUpdatesComTransferenciasConcorrentes() throws Exception {
        Assumptions.assumeTrue(emf != null, "EntityManagerFactory não disponível");

        // Arrange
        int numThreads = 10;
        BigDecimal valorPorTransferencia = new BigDecimal("100.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhas = new AtomicInteger(0);

        // Act - Executar transferências simultâneas
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            Future<?> future = executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                BeneficioEjbService threadService = new BeneficioEjbService();
                injectEntityManager(threadService, threadEm);

                try {
                    threadEm.getTransaction().begin();
                    threadService.transfer(1L, 2L, valorPorTransferencia);
                    threadEm.getTransaction().commit();
                    sucessos.incrementAndGet();
                } catch (Exception e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                    falhas.incrementAndGet();
                } finally {
                    threadEm.close();
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // Aguardar conclusão
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - Verificar consistência dos dados
        em.clear();
        Beneficio b1Final = em.find(Beneficio.class, 1L);
        Beneficio b2Final = em.find(Beneficio.class, 2L);

        BigDecimal totalEsperado = new BigDecimal("15000.00"); // 10000 + 5000
        BigDecimal totalAtual = b1Final.getValor().add(b2Final.getValor());

        // O total deve ser preservado (conservação de valor)
        assertEquals(0, totalEsperado.compareTo(totalAtual), 
            "Total de valores deve ser preservado (sem lost updates)");

        // Verificar que houve transferências bem-sucedidas
        assertTrue(sucessos.get() > 0, "Deve haver pelo menos uma transferência bem-sucedida");

        System.out.println("Sucessos: " + sucessos.get() + ", Falhas: " + falhas.get());
        System.out.println("Saldo final B1: " + b1Final.getValor());
        System.out.println("Saldo final B2: " + b2Final.getValor());
    }

    @Test
    @DisplayName("Deve prevenir saldo negativo com transferências concorrentes")
    void devePrevenirsaldoNegativoComConcorrencia() throws Exception {
        Assumptions.assumeTrue(emf != null, "EntityManagerFactory não disponível");

        // Arrange - Benefício com saldo limitado
        em.getTransaction().begin();
        Beneficio b3 = new Beneficio("Beneficio Limitado", "Teste", new BigDecimal("500.00"));
        em.persist(b3);
        em.getTransaction().commit();
        Long b3Id = b3.getId();
        em.clear();

        int numThreads = 20;
        BigDecimal valorPorTransferencia = new BigDecimal("100.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger saldoInsuficiente = new AtomicInteger(0);

        // Act - Tentar transferir mais do que o saldo disponível
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                BeneficioEjbService threadService = new BeneficioEjbService();
                injectEntityManager(threadService, threadEm);

                try {
                    threadEm.getTransaction().begin();
                    threadService.transfer(b3Id, 2L, valorPorTransferencia);
                    threadEm.getTransaction().commit();
                    sucessos.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                    saldoInsuficiente.incrementAndGet();
                } catch (Exception e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                } finally {
                    threadEm.close();
                    latch.countDown();
                }
            });
        }

        // Aguardar conclusão
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        em.clear();
        Beneficio b3Final = em.find(Beneficio.class, b3Id);

        // Saldo nunca deve ser negativo
        assertTrue(b3Final.getValor().compareTo(BigDecimal.ZERO) >= 0, 
            "Saldo não deve ser negativo mesmo com concorrência");

        // Deve ter exatamente 5 sucessos (500 / 100)
        assertEquals(5, sucessos.get(), 
            "Deve ter exatamente 5 transferências bem-sucedidas");

        // Deve ter 15 falhas por saldo insuficiente
        assertEquals(15, saldoInsuficiente.get(), 
            "Deve ter 15 tentativas rejeitadas por saldo insuficiente");

        // Saldo final deve ser zero
        assertEquals(0, BigDecimal.ZERO.compareTo(b3Final.getValor()), 
            "Saldo final deve ser zero");

        System.out.println("Sucessos: " + sucessos.get());
        System.out.println("Saldo Insuficiente: " + saldoInsuficiente.get());
        System.out.println("Saldo final: " + b3Final.getValor());
    }

    @Test
    @DisplayName("Deve serializar transferências bidirecionais simultâneas")
    void deveSerializarTransferenciasBidirecionais() throws Exception {
        Assumptions.assumeTrue(emf != null, "EntityManagerFactory não disponível");

        // Arrange
        int numPares = 5;
        BigDecimal valor = new BigDecimal("50.00");
        ExecutorService executor = Executors.newFixedThreadPool(numPares * 2);
        CountDownLatch latch = new CountDownLatch(numPares * 2);

        // Act - Transferências bidirecionais simultâneas (A->B e B->A)
        for (int i = 0; i < numPares; i++) {
            // Thread 1: A -> B
            executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                BeneficioEjbService threadService = new BeneficioEjbService();
                injectEntityManager(threadService, threadEm);

                try {
                    threadEm.getTransaction().begin();
                    threadService.transfer(1L, 2L, valor);
                    threadEm.getTransaction().commit();
                } catch (Exception e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                } finally {
                    threadEm.close();
                    latch.countDown();
                }
            });

            // Thread 2: B -> A
            executor.submit(() -> {
                EntityManager threadEm = emf.createEntityManager();
                BeneficioEjbService threadService = new BeneficioEjbService();
                injectEntityManager(threadService, threadEm);

                try {
                    threadEm.getTransaction().begin();
                    threadService.transfer(2L, 1L, valor);
                    threadEm.getTransaction().commit();
                } catch (Exception e) {
                    if (threadEm.getTransaction().isActive()) {
                        threadEm.getTransaction().rollback();
                    }
                } finally {
                    threadEm.close();
                    latch.countDown();
                }
            });
        }

        // Aguardar conclusão
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert - Total deve ser preservado
        em.clear();
        Beneficio b1Final = em.find(Beneficio.class, 1L);
        Beneficio b2Final = em.find(Beneficio.class, 2L);

        BigDecimal totalEsperado = new BigDecimal("15000.00");
        BigDecimal totalAtual = b1Final.getValor().add(b2Final.getValor());

        assertEquals(0, totalEsperado.compareTo(totalAtual), 
            "Total deve ser preservado mesmo com transferências bidirecionais");

        System.out.println("Saldo final B1: " + b1Final.getValor());
        System.out.println("Saldo final B2: " + b2Final.getValor());
    }

    /**
     * Helper para injetar EntityManager no serviço (simulando @PersistenceContext)
     */
    private void injectEntityManager(BeneficioEjbService service, EntityManager em) {
        try {
            java.lang.reflect.Field field = BeneficioEjbService.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(service, em);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao injetar EntityManager", e);
        }
    }
}
