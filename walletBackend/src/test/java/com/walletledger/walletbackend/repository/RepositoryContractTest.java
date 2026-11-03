package com.walletledger.walletbackend.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

class RepositoryContractTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            UserRepository.class,
            WalletRepository.class,
            TransactionRepository.class,
            LedgerEntryRepository.class,
            FraudAlertRepository.class,
            NotificationRepository.class,
            FlexCreditRepository.class,
            DailySummaryRepository.class,
            SettlementBatchRepository.class,
            LoginAttemptRepository.class,
            ScheduledJobLogRepository.class,
            AuditLogRepository.class
    );

    @ParameterizedTest
    @MethodSource("repositoryClasses")
    void allRepositoriesAreInterfacesExtendingJpaRepository(Class<?> repoClass) {
        assertTrue(repoClass.isInterface(), repoClass.getSimpleName() + " must be an interface");
        assertTrue(JpaRepository.class.isAssignableFrom(repoClass),
                repoClass.getSimpleName() + " must extend JpaRepository");
    }

    static Stream<Arguments> repositoryClasses() {
        return REPOSITORIES.stream().map(Arguments::of);
    }

    @Test
    void queryAnnotationsContainValidJpqlString() {
        for (Class<?> repoClass : REPOSITORIES) {
            for (Method method : repoClass.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null) {
                    assertFalse(query.value().isBlank(),
                            "Query on " + repoClass.getSimpleName() + "." + method.getName() + " cannot be blank");
                    String sql = query.value().toLowerCase().trim();
                    assertTrue(sql.startsWith("select") || sql.startsWith("update") || sql.startsWith("delete"),
                            "Query on " + method.getName() + " should start with SELECT/UPDATE/DELETE");
                }
            }
        }
    }

    @Test
    void modifyingQueriesAreConfiguredCorrectly() {
        for (Class<?> repoClass : REPOSITORIES) {
            for (Method method : repoClass.getDeclaredMethods()) {
                Modifying modifying = method.getAnnotation(Modifying.class);
                if (modifying != null) {
                    Query query = method.getAnnotation(Query.class);
                    assertNotNull(query, "Modifying method " + method.getName() + " must have @Query");
                }
            }
        }
    }

    @Test
    void pessimisticLocksAreConfiguredCorrectly() {
        for (Class<?> repoClass : REPOSITORIES) {
            for (Method method : repoClass.getDeclaredMethods()) {
                Lock lock = method.getAnnotation(Lock.class);
                if (lock != null) {
                    assertNotNull(lock.value(), "Lock mode on " + method.getName() + " must not be null");
                }
            }
        }
    }
}
