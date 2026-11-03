package com.walletledger.walletbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterSetsAuthenticationWhenValidAccessTokenProvided() throws ServletException, IOException {
        String token = "valid-access-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(token)).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("user@example.com");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isAccountNonLocked()).thenReturn(true);
        when(userDetails.isEnabled()).thenReturn(true);
        org.mockito.Mockito.doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .when(userDetails).getAuthorities();
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(userDetails, auth.getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @ParameterizedTest
    @MethodSource("invalidHeaderCases")
    void doFilterSkipsWhenHeaderIsMissingOrInvalid(String header) throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(header);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    static Stream<Arguments> invalidHeaderCases() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("Basic user:pass"),
                Arguments.of("Bearer "),
                Arguments.of("InvalidPrefix token"));
    }

    @Test
    void doFilterSkipsWhenTokenIsInvalid() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterSkipsWhenTokenIsNotAccessToken() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer refresh-token");
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("refresh-token")).thenReturn(JwtTokenProvider.TOKEN_TYPE_REFRESH);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterSkipsWhenUserIsLockedOrDisabled() throws ServletException, IOException {
        String token = "access-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(token)).thenReturn(JwtTokenProvider.TOKEN_TYPE_ACCESS);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("user@example.com");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isAccountNonLocked()).thenReturn(false); // locked
        when(userDetails.isEnabled()).thenReturn(true);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
