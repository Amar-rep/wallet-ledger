package com.walletledger.walletbackend.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

class FlexCreditMappingTest {

    @Test
    void tableNameAndStatusEnumArePersistedAsStrings() throws NoSuchFieldException {
        Table table = FlexCredit.class.getAnnotation(Table.class);
        assertEquals("flex_credits", table.name());

        Field status = FlexCredit.class.getDeclaredField("status");
        Enumerated mapping = status.getAnnotation(Enumerated.class);
        assertEquals(FlexCreditStatus.class, status.getType());
        assertNotNull(mapping);
        assertEquals(EnumType.STRING, mapping.value());
    }

    @Test
    void transactionJoinIsRequiredAndUnique() throws NoSuchFieldException {
        JoinColumn join = FlexCredit.class.getDeclaredField("transaction").getAnnotation(JoinColumn.class);
        assertEquals("transaction_id", join.name());
        assertFalse(join.nullable());
        assertTrue(join.unique());
    }

    @Test
    void moneyColumnsAreRequired() throws NoSuchFieldException {
        assertFalse(FlexCredit.class.getDeclaredField("amount").getAnnotation(Column.class).nullable());
        assertFalse(FlexCredit.class.getDeclaredField("amountRecovered").getAnnotation(Column.class).nullable());
        assertFalse(FlexCredit.class.getDeclaredField("amountOutstanding").getAnnotation(Column.class).nullable());
        assertEquals(500, FlexCredit.class.getDeclaredField("writeOffReason").getAnnotation(Column.class).length());
    }
}
