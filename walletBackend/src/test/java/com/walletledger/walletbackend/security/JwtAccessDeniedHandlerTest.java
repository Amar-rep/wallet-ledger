package com.walletledger.walletbackend.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.access.AccessDeniedException;

class JwtAccessDeniedHandlerTest {

    private JwtAccessDeniedHandler accessDeniedHandler;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() throws IOException {
        accessDeniedHandler = new JwtAccessDeniedHandler();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        stringWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    }

    @Test
    void handleSetsStatus403AndJsonContentType() throws IOException {
        when(request.getRequestURI()).thenReturn("/api/admin/dashboard");

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Forbidden action"));

        verify(response).setStatus(403);
        verify(response).setContentType("application/json");
        String body = stringWriter.toString();
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("\"error\":\"Forbidden\""));
        assertTrue(body.contains("Forbidden action"));
        assertTrue(body.contains("/api/admin/dashboard"));
    }

    @ParameterizedTest
    @MethodSource("accessDeniedCases")
    void handleHandlesVariousExceptions(AccessDeniedException ex, String expectedMessage) throws IOException {
        when(request.getRequestURI()).thenReturn(null);

        accessDeniedHandler.handle(request, response, ex);

        String body = stringWriter.toString();
        assertTrue(body.contains(expectedMessage));
    }

    static Stream<Arguments> accessDeniedCases() {
        return Stream.of(
                Arguments.of(null, "Access denied: insufficient permissions"),
                Arguments.of(new AccessDeniedException(null), "Access denied: insufficient permissions"),
                Arguments.of(new AccessDeniedException("Admin \"role\" required"), "Admin \\\"role\\\" required"));
    }
}
