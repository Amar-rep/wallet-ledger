package com.walletledger.walletbackend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @MethodSource("mappedExceptions")
    void mapsSecurityExceptionsToTheExpectedHttpStatus(Exception exception, HttpStatus expected) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        var response = switch (exception) {
            case BadCredentialsException ex -> handler.handleBadCredentials(ex, request);
            case LockedException ex -> handler.handleLocked(ex, request);
            case UsernameNotFoundException ex -> handler.handleUsernameNotFound(ex, request);
            default -> throw new IllegalArgumentException("Unexpected exception");
        };

        assertEquals(expected, response.getStatusCode());
        assertEquals("/api/test", response.getBody().path());
    }

    static Stream<Arguments> mappedExceptions() {
        return Stream.of(
                Arguments.of(new BadCredentialsException("secret"), HttpStatus.UNAUTHORIZED),
                Arguments.of(new LockedException("locked"), HttpStatus.FORBIDDEN),
                Arguments.of(new UsernameNotFoundException("missing"), HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("applicationExceptions")
    void mapsApplicationExceptionsWithoutLeakingImplementationDetails(Exception exception, HttpStatus expected) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");

        var response = switch (exception) {
            case BusinessRuleException ex -> handler.handleBusinessRule(ex, request);
            case DuplicateResourceException ex -> handler.handleDuplicateResource(ex, request);
            case InvalidTokenException ex -> handler.handleInvalidToken(ex, request);
            default -> throw new IllegalArgumentException("Unexpected exception");
        };

        ErrorResponse body = response.getBody();
        assertEquals(expected, response.getStatusCode());
        assertEquals(expected.value(), body.status());
    }

    static Stream<Arguments> applicationExceptions() {
        return Stream.of(
                Arguments.of(new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid"), HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(new DuplicateResourceException("duplicate"), HttpStatus.CONFLICT),
                Arguments.of(new InvalidTokenException("expired"), HttpStatus.UNAUTHORIZED));
    }

    @org.junit.jupiter.api.Test
    void mapsValidationErrorsToBadRequestWithFieldErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/register");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(
                new org.springframework.validation.FieldError("registerRequest", "email", "Email is required"),
                new org.springframework.validation.FieldError("registerRequest", "phone", "Phone is invalid")
        ));
        org.springframework.web.bind.MethodArgumentNotValidException ex =
                new org.springframework.web.bind.MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidationErrors(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation failed", response.getBody().message());
        assertEquals(2, response.getBody().fieldErrors().size());
        assertEquals("Email is required", response.getBody().fieldErrors().get("email"));
    }
}
