package com.walletledger.walletbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;

class CustomUserDetailsTest {

    @Test
    void wrapsUserPropertiesCorrectly() {
        User user = new User();
        user.setId(42L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(UserRole.USER);

        CustomUserDetails userDetails = new CustomUserDetails(user);

        assertEquals(user, userDetails.getUser());
        assertEquals(42L, userDetails.getId());
        assertEquals("user@example.com", userDetails.getUsername());
        assertEquals("hashed-password", userDetails.getPassword());
        assertTrue(userDetails.isAccountNonExpired());
        assertTrue(userDetails.isCredentialsNonExpired());
        assertTrue(userDetails.isEnabled());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void mapsAuthoritiesForRoles(UserRole role) {
        User user = new User();
        user.setRole(role);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();

        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertEquals("ROLE_" + role.name(), authorities.iterator().next().getAuthority());
    }

    @Test
    void defaultsRoleUserWhenRoleIsNull() {
        User user = new User();
        user.setRole(null);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        assertEquals("ROLE_USER", userDetails.getAuthorities().iterator().next().getAuthority());
    }

    @ParameterizedTest
    @MethodSource("lockedStatusCases")
    void isAccountNonLockedEvaluatesCorrectly(OffsetDateTime lockedAt, boolean expectedNonLocked) {
        User user = new User();
        user.setLockedAt(lockedAt);

        CustomUserDetails userDetails = new CustomUserDetails(user);

        assertEquals(expectedNonLocked, userDetails.isAccountNonLocked());
    }

    static Stream<Arguments> lockedStatusCases() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of(OffsetDateTime.now().minusMinutes(5), false),
                Arguments.of(OffsetDateTime.now().minusMinutes(29), false),
                Arguments.of(OffsetDateTime.now().minusMinutes(31), true));
    }
}
