package com.wallet.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Rejects requests without a valid bearer token before they reach a protected service.
 * Fine-grained authorization (ownership, admin-only operations) is enforced by each service.
 */
@Component
public class AuthenticationFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        if (HttpMethod.OPTIONS.equals(request.method())) {
            return next.handle(request);
        }

        String authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized("Falta el token o la cabecera Authorization es inválida");
        }
        if (jwtUtil.validateToken(authHeader.substring(BEARER_PREFIX.length())).isEmpty()) {
            return unauthorized("Token inválido o expirado");
        }
        return next.handle(request);
    }

    private static ServerResponse unauthorized(String detail) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail));
    }
}
