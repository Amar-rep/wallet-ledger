package com.walletledger.walletbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint entryPoint;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() throws IOException {
        entryPoint = new JwtAuthenticationEntryPoint();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    }

    @Test
    void commenceSetsStatus401AndJsonContentType() throws IOException {
        when(request.getRequestURI()).thenReturn("/api/test");

        entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));

        org.mockito.Mockito.verify(response).setStatus(401);
        org.mockito.Mockito.verify(response).setContentType("application/json");
        String body = stringWriter.toString();
        assertTrue(body.contains("\"status\":401"));
        assertTrue(body.contains("\"error\":\"Unauthorized\""));
        assertTrue(body.contains("Invalid token"));
        assertTrue(body.contains("/api/test"));
    }

    @ParameterizedTest
    @MethodSource("exceptionCases")
    void commenceHandlesVariousMessagesAndNullPaths(AuthenticationException authException, String expectedMessage) throws IOException {
        when(request.getRequestURI()).thenReturn(null);

        entryPoint.commence(request, response, authException);

        String body = stringWriter.toString();
        assertTrue(body.contains(expectedMessage));
    }

    static Stream<Arguments> exceptionCases() {
        return Stream.of(
                Arguments.of(null, "Full authentication is required to access this resource"),
                Arguments.of(new BadCredentialsException(null), "Full authentication is required to access this resource"),
                Arguments.of(new BadCredentialsException("Custom \"quoted\" reason"), "Custom \\\"quoted\\\" reason"));
    }
}
