package com.wallet.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private final AuthenticationFilter filter = new AuthenticationFilter(new JwtUtil(SECRET));

    @Test
    void rejectsMissingToken() throws Exception {
        assertThat(run(null).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTamperedOrExpiredTokens() throws Exception {
        assertThat(run("Bearer abc.def.ghi").statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(run("Bearer " + token(SECRET, -1000)).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String otherKey = "another-secret-another-secret-another-1234";
        assertThat(run("Bearer " + token(otherKey, 60_000)).statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forwardsValidToken() throws Exception {
        assertThat(run("Bearer " + token(SECRET, 60_000)).statusCode()).isEqualTo(HttpStatus.OK);
    }

    private ServerResponse run(String authorization) throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/accounts");
        if (authorization != null) {
            servletRequest.addHeader("Authorization", authorization);
        }
        List<HttpMessageConverter<?>> converters = List.of();
        return filter.filter(ServerRequest.create(servletRequest, converters), request -> ServerResponse.ok().build());
    }

    private static String token(String secret, long ttlMillis) {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 1L)
                .claim("userType", "CUSTOMER")
                .expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
