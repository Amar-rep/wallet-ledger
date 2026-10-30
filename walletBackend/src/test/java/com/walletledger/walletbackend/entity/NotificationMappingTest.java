package com.walletledger.walletbackend.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;

import com.walletledger.walletbackend.dto.enums.NotificationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

class NotificationMappingTest {

    @Test
    void notificationTypeIsStoredAsAStringEnum() throws NoSuchFieldException {
        Field type = Notification.class.getDeclaredField("type");
        Enumerated mapping = type.getAnnotation(Enumerated.class);

        assertEquals(NotificationType.class, type.getType());
        assertNotNull(mapping);
        assertEquals(EnumType.STRING, mapping.value());
    }
}
