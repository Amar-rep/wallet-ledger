package com.walletledger.walletbackend.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.walletledger.walletbackend.security.JwtAccessDeniedHandler;
import com.walletledger.walletbackend.security.JwtAuthenticationEntryPoint;
import com.walletledger.walletbackend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    private SecurityConfig securityConfig;
    private JwtAuthenticationFilter filter;
    private JwtAuthenticationEntryPoint entryPoint;
    private JwtAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        filter = mock(JwtAuthenticationFilter.class);
        entryPoint = mock(JwtAuthenticationEntryPoint.class);
        accessDeniedHandler = mock(JwtAccessDeniedHandler.class);
        securityConfig = new SecurityConfig(filter, entryPoint, accessDeniedHandler);
    }

    @Test
    void passwordEncoderReturnsBCryptPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);
    }

    @Test
    void authenticationManagerDelegatesToConfiguration() throws Exception {
        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        AuthenticationManager expectedManager = mock(AuthenticationManager.class);
        org.mockito.Mockito.when(configuration.getAuthenticationManager()).thenReturn(expectedManager);

        AuthenticationManager actual = securityConfig.authenticationManager(configuration);
        assertNotNull(actual);
        org.junit.jupiter.api.Assertions.assertEquals(expectedManager, actual);
    }
}
