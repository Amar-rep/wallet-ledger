package com.walletledger.walletbackend.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

class JpaMappingConstraintTest {

    private static final List<Class<?>> ENTITIES = List.of(
            User.class, LoginAttempt.class, Wallet.class, SettlementBatch.class, Transaction.class,
            LedgerEntry.class, FraudAlert.class, Notification.class, AuditLog.class,
            ScheduledJobLog.class, DailySummary.class
    );

    @Test
    void entitiesDoNotDefineDatabaseConstraintOrIndexRules() {
        for (Class<?> entity : ENTITIES) {
            Table table = entity.getAnnotation(Table.class);
            assertEquals(0, table.uniqueConstraints().length, entity.getSimpleName());
            assertEquals(0, table.indexes().length, entity.getSimpleName());

            for (Field field : entity.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    assertTrue(column.nullable(), field.getName());
                    assertFalse(column.unique(), field.getName());
                    assertEquals(255, column.length(), field.getName());
                    assertEquals(0, column.precision(), field.getName());
                    assertEquals(0, column.scale(), field.getName());
                    assertEquals("", column.columnDefinition(), field.getName());
                }

                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn != null) {
                    assertTrue(joinColumn.nullable(), field.getName());
                    assertFalse(joinColumn.unique(), field.getName());
                    assertEquals("", joinColumn.columnDefinition(), field.getName());
                }
            }
        }
    }
}
